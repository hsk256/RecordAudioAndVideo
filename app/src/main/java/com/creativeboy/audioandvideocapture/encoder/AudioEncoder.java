package com.creativeboy.audioandvideocapture.encoder;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by heshaokang on 2015/5/9.
 *  对音频数据进行编码
 */
public class AudioEncoder {
    private static final String TAG = "AudioEncoder";
    //编码
    private MediaCodec mAudioCodec;     //音频编解码器
    private MediaFormat mAudioFormat;
    private static final String AUDIO_MIME_TYPE = "audio/mp4a-latm"; //音频类型
    private static final int SAMPLE_RATE = 44100; //采样率(CD音质)
    private TrackIndex mAudioTrackIndex = new TrackIndex();
    private MediaMuxer mMediaMuxer;     //混合器
    private boolean mMuxerStart = false; //混合器启动的标志
    private MediaCodec.BufferInfo mAudioBufferInfo;
    private static long audioBytesReceived = 0;        //接收到的音频数据 用来设置录音起始时间的
    private long audioStartTime;
    private String recordFile ;
    private boolean eosReceived = false;  //终止录音的标志
    private ExecutorService encodingService = Executors.newSingleThreadExecutor(); //序列化线程任务
    private MediaMuxerWrapper mediaMuxerWrapper;
    //枚举值 一个用来标志编码 一个标志编码完成
    enum EncoderTaskType {ENCODE_FRAME,FINALIZE_ENCODER};

    public AudioEncoder() {
        recordFile = AudioRecorder.recordFile.getAbsolutePath();
        prepareEncoder();
    }

    class TrackIndex {
        int index = 0;
    }
    public void prepareEncoder() {
        eosReceived = false;
        audioBytesReceived = 0;
        mAudioBufferInfo = new MediaCodec.BufferInfo();
        mAudioFormat = new MediaFormat();
        mAudioFormat.setString(MediaFormat.KEY_MIME, AUDIO_MIME_TYPE);
        mAudioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        mAudioFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE,SAMPLE_RATE);
        mAudioFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
        mAudioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
        mAudioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,16384);
        try {
            mAudioCodec = MediaCodec.createEncoderByType(AUDIO_MIME_TYPE);
            mAudioCodec.configure(mAudioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mAudioCodec.start();
           // mMediaMuxer = new MediaMuxer(recordFile,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            //if(!MediaMuxerWrapper.isInstance) {
                mediaMuxerWrapper = new MediaMuxerWrapper(recordFile);
                Log.d(TAG,"MediaMuxerWrapper is instance");
            //}
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    //此方法 由AudioRecorder任务调用 开启编码任务
    public void offerAudioEncoder(byte[] input,long presentationTimeStampNs) {
       if(!encodingService.isShutdown()) {
//           Log.d(TAG,"encodingService--submit");
           encodingService.submit(new AudioEncodeTask(this,input,presentationTimeStampNs));
       }

    }

    //发送音频数据和时间进行编码
    public void _offerAudioEncoder(byte[] input,long pts) {
        if(audioBytesReceived==0) {
            audioStartTime = pts;
        }
        audioBytesReceived+=input.length;
        drainEncoder(mAudioCodec,mAudioBufferInfo,mAudioTrackIndex,false);
        try {
            ByteBuffer[] inputBuffers = mAudioCodec.getInputBuffers();
            int inputBufferIndex = mAudioCodec.dequeueInputBuffer(-1);
//        Log.d(TAG,"inputBufferIndex--"+inputBufferIndex);
            if(inputBufferIndex>=0) {
                ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                inputBuffer.clear();
                inputBuffer.put(input);

                //录音时长
                long presentationTimeUs = (pts - audioStartTime)/1000;
                Log.d("hsk","presentationTimeUs--"+presentationTimeUs);
                if(eosReceived) {
                    mAudioCodec.queueInputBuffer(inputBufferIndex, 0, input.length, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    closeEncoder(mAudioCodec, mAudioBufferInfo, mAudioTrackIndex);
                    ///closeMuxer();
                    mediaMuxerWrapper.release();
                    encodingService.shutdown();

                }else {
                    mAudioCodec.queueInputBuffer(inputBufferIndex,0,input.length,presentationTimeUs,0);
                }
            }

        }catch (Throwable t) {
            Log.e(TAG, "_offerAudioEncoder exception");
        }

    }

    public void drainEncoder(MediaCodec encoder,MediaCodec.BufferInfo bufferInfo,TrackIndex trackIndex ,boolean endOfStream) {
        final int TIMEOUT_USEC = 100;
        ByteBuffer[] encoderOutputBuffers = encoder.getOutputBuffers();
        while(true) {
            int encoderIndex = encoder.dequeueOutputBuffer(bufferInfo,TIMEOUT_USEC);
            Log.d("hsk","encoderIndex---"+encoderIndex);
            if(encoderIndex==MediaCodec.INFO_TRY_AGAIN_LATER) {
                //没有可进行混合的输出流数据 但还没有结束录音 此时退出循环
                Log.d(TAG,"info_try_again_later");
                if(!endOfStream)
                    break;
                else
                    Log.d(TAG, "no output available, spinning to await EOS");
            }else if(encoderIndex== MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                //只会在第一次接收数据前 调用一次
                if(mMuxerStart)
                    throw new RuntimeException("format 在muxer启动后发生了改变");
                MediaFormat newFormat = encoder.getOutputFormat();
                //trackIndex.index = mMediaMuxer.addTrack(newFormat);
                trackIndex.index = mediaMuxerWrapper.addTrack(newFormat);
                //mMediaMuxer.start();
                if(!mediaMuxerWrapper.isStarted()) {
                    mediaMuxerWrapper.start();
                }
                mMuxerStart = true;
            }else if(encoderIndex<0) {
                Log.w(TAG,"encoderIndex 非法"+encoderIndex);
            }else {
                ByteBuffer encodeData = encoderOutputBuffers[encoderIndex];
                if (encodeData==null) {
                    throw new RuntimeException("编码数据为空");
                }
                if(bufferInfo.size!=0) {
                    if(!mMuxerStart) {
                        throw new RuntimeException("混合器未开启");
                    }
                    encodeData.position(bufferInfo.offset);
                    encodeData.limit(bufferInfo.offset + bufferInfo.size);
                    //mMediaMuxer.writeSampleData(trackIndex.index,encodeData,bufferInfo);
                    mediaMuxerWrapper.writeSampleData(trackIndex.index,encodeData,bufferInfo);
                }

                encoder.releaseOutputBuffer(encoderIndex,false);
                //退出循环
                if((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0) {
                    break;
                }


            }
        }

    }



    /**
     * 关闭编码
     * @param encoder
     * @param bufferInfo
     *
     */
    public void closeEncoder(MediaCodec encoder,MediaCodec.BufferInfo bufferInfo,TrackIndex trackIndex) {
        drainEncoder(encoder,bufferInfo,trackIndex,true);
        encoder.stop();
        encoder.release();
        encoder = null;

    }

    /**
     * 关闭混合器
     */
    public void closeMuxer() {
        mMediaMuxer.stop();
        mMediaMuxer.release();
        mMediaMuxer = null;
        mMuxerStart = false;
    }

    //发送终止编码信息
    public void stop() {
        if(!encodingService.isShutdown()) {
            encodingService.submit(new AudioEncodeTask(this,EncoderTaskType.FINALIZE_ENCODER));
        }
    }

    //终止编码
    public void _stop() {
        eosReceived = true;
        Log.d(TAG,"停止编码");
    }


    /**
     * 音频编码任务
     */
    class AudioEncodeTask implements Runnable {
        private static final String TAG = "AudioEncoderTask";
        private boolean is_initialized = false;
        private AudioEncoder encoder;
        private byte[] audio_data;
        long pts;
        private EncoderTaskType type;

        //进行编码任务时 调用此构造方法
        public AudioEncodeTask(AudioEncoder encoder,byte[] audio_data,long pts) {
            this.encoder = encoder;
            this.audio_data = audio_data;
            this.pts = pts;
            is_initialized = true;
            this.type = EncoderTaskType.ENCODE_FRAME;
            //这里是有数据的
//            Log.d(TAG,"AudioData--"+audio_data);
//            Log.d(TAG,"pts--"+pts);
        }
        //当要停止编码任务时 调用此构造方法
        public AudioEncodeTask(AudioEncoder encoder,EncoderTaskType type) {
            this.type = type;

            if(type==EncoderTaskType.FINALIZE_ENCODER) {
                this.encoder = encoder;
                is_initialized = true;
            }
            Log.d(TAG,"完成...");

        }
        ////编码
        private void encodeFrame() {
            Log.d(TAG,"audio_data---encoder--"+audio_data+" "+encoder);
            if(audio_data!=null && encoder!=null) {
                encoder._offerAudioEncoder(audio_data,pts);
                audio_data = null;
            }

        }

        //终止编码
        private void finalizeEncoder() {
            encoder._stop();
        }

        @Override
        public void run() {
            Log.d(TAG,"is_initialized--"+is_initialized);
            if(is_initialized) {
                switch(type) {
                    case ENCODE_FRAME:
                        //进行编码
                        encodeFrame();
                        break;
                    case FINALIZE_ENCODER:
                        //完成编码
                        finalizeEncoder();
                        break;
                }
                is_initialized = false;
            }else {
                //打印错误日志
                Log.e(TAG,"AudioEncoderTask is not initiallized");
            }
        }
    }



}
