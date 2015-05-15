package com.creativeboy.audioandvideocapture.encoder;

import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by heshaokang on 2015/5/15.
 * 录制屏幕及编码输出
 */
public class VideoRecorder extends Thread{
    private static final String TAG = "VideoRecorder";
    private int mWidth;    //视频宽度
    private int mHeight;    //视频高度
    private static final int BITRATE = 6000000; //比特率
    private static final int mDpi=1;  //密度
    private String filePath;   //输出文件路径
    private DisplayManager displayManager;
    private VirtualDisplay mVirtualDisplay;
    private MediaCodec mEncoder;  //编码器
    private Surface mSurface;
    private AtomicBoolean endEncode = new AtomicBoolean(false);
    private static final String MIME_TYPE = "video/avc";
    private static final int FRAME_RATE = 24; //帧数
    private static final int IFRAME_INTERVAL = 10;
    private MediaMuxer mediaMuxer;
    private MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
    private static final int TIMEOUT_US = 10000;
    private int videoTrackIndex = -1;
    private boolean mediaMuxerStarted = false;
    public VideoRecorder(int width,int height,String filePath,DisplayManager dm) {
        this.mWidth = width;
        this.mHeight = height;
        this.filePath = filePath;
        this.displayManager = dm;
    }

    public void prepareEncode() {
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE,mWidth,mHeight);
        //描述视频格式的信息
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE,BITRATE);       //比特率
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE,FRAME_RATE);  //帧数
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL); //关键帧间隔时间
        try {
            mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
            mEncoder.configure(mediaFormat,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
            mSurface = mEncoder.createInputSurface();
            mEncoder.start();
            mediaMuxer = new MediaMuxer(filePath,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

//    public void start() {
//        new Thread(new VideoRecordTask()).start();
//    }

    public void mstop() {
        endEncode.set(true);

    }

    @Override
    public void run() {
        try
        {
            try
            {
                prepareEncode();
                mediaMuxer = new MediaMuxer(filePath,
                        MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            } catch (IOException e)
            {
                throw new RuntimeException(e);
            }
            mVirtualDisplay = displayManager.createVirtualDisplay(TAG, mWidth,
                    mHeight, mDpi, mSurface,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC);
            // Log.d(TAG, "created virtual display: " + mVirtualDisplay);
            recordVideo();
        } finally
        {
            try
            {
                release();
            } catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }

//    class VideoRecordTask implements Runnable {
//
//        @Override
//        public void run() {
//                try {
//                    prepareEncode();
//                    mVirtualDisplay = displayManager.createVirtualDisplay(
//                            TAG,mWidth,mHeight,mDpi,mSurface,DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC|DisplayManager.VIRTUAL_DISPLAY_FLAG_SECURE
//                    );
//                    Log.d(TAG,"created virtual display "+mVirtualDisplay);
//                    recordVideo();
//                }catch(Exception e) {
//                    e.printStackTrace();
//                }finally {
//                    try
//                    {
//                        release();
//                    } catch (Exception e)
//                    {
//                        e.printStackTrace();
//                    }
//                }
//
//        }
//    }

    private void recordVideo() {
        ByteBuffer[] encodeOutputBuffers = mEncoder.getOutputBuffers();
        while(!endEncode.get()) {
            int encoderIndex = mEncoder.dequeueOutputBuffer(mBufferInfo,TIMEOUT_US);
            if(encoderIndex==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if(mediaMuxerStarted) {
                    throw new IllegalStateException("output format already changed");
                }
                MediaFormat newFormat = mEncoder.getOutputFormat();
                videoTrackIndex = mediaMuxer.addTrack(newFormat);
                mediaMuxer.start();
                mediaMuxerStarted = true;
            }else if(encoderIndex==MediaCodec.INFO_TRY_AGAIN_LATER) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }else if(encoderIndex<0) {
                Log.w(TAG, "encoderIndex 非法" + encoderIndex);
            }else if(encoderIndex>=0){
                ByteBuffer encodeData = encodeOutputBuffers[encoderIndex];
                if(encodeData==null) {
                    throw new RuntimeException("编码数据为空");
                }
                if(mBufferInfo.size!=0) {
                    if(!mediaMuxerStarted)
                        throw new RuntimeException("混合器未开启");
                    encodeData.position(mBufferInfo.offset);
                    encodeData.limit(mBufferInfo.offset + mBufferInfo.size);

                    mediaMuxer.writeSampleData(videoTrackIndex,encodeData,mBufferInfo);
                    mEncoder.releaseOutputBuffer(encoderIndex,false);
                }
            }
        }
    }

    private void release() {

        if(mEncoder!=null) {
            mEncoder.stop();
            mEncoder.release();
            mEncoder = null;
        }

        if(mVirtualDisplay!=null) {
            mVirtualDisplay.release();
        }

        if(displayManager!=null) {
            displayManager = null;
        }
        if(mediaMuxer!=null) {
            mediaMuxer.stop();
            mediaMuxer.release();
            mediaMuxer = null;
        }
    }
}
