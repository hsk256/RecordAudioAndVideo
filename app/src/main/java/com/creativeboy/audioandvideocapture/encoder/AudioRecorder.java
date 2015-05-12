package com.creativeboy.audioandvideocapture.encoder;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by heshaokang on 2015/5/6.
 */
public class AudioRecorder {
    private static final String TAG = "AudioRecorder";
    private static final int SAMPLE_RATE = 44100; //采样率(CD音质)
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO; //音频通道(单声道)
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT; //音频格式
    private static final int AUDIO_SOURCE = MediaRecorder.AudioSource.MIC;  //音频源（麦克风）
    private static boolean is_recording = false;
    public static File recordFile ;
    private AudioEncoder audioEncoder;
    private static AudioRecorder instance;
    private RecorderTask recorderTask = new RecorderTask();
    private AudioRecorder(File file){
       recordFile = file;
    }
    public static AudioRecorder getInstance(File file) {
           return new AudioRecorder(file);

    }

    public void setAudioEncoder(AudioEncoder audioEncoder) {
        this.audioEncoder = audioEncoder;
    }

    /*
        开始录音
     */
    public void startAudioRecording() {

        new Thread(recorderTask).start();
    }

    /*
        停止录音
     */
    public void stopAudioRecording() {
        is_recording = false;
    }
    class RecorderTask implements Runnable {
        int bufferReadResult = 0;
        public int samples_per_frame = 2048;
        @Override
        public void run() {
                long audioPresentationTimeNs; //音频时间戳 pts
                int bufferSizeInBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE,CHANNEL_CONFIG,AUDIO_FORMAT);
                AudioRecord audioRecord = new AudioRecord(
                        AUDIO_SOURCE,
                        SAMPLE_RATE,
                        CHANNEL_CONFIG,
                        AUDIO_FORMAT,
                        bufferSizeInBytes
                );
                audioRecord.startRecording();
                is_recording = true;

            Log.v(TAG, "recordFile.getAbsolutepath---" + recordFile.getAbsolutePath());

            while(is_recording) {
                  byte[] buffer = new byte[samples_per_frame];
                  audioPresentationTimeNs = System.nanoTime();
                    Log.d("hsk","System.nanotime--"+audioPresentationTimeNs);
                    bufferReadResult = audioRecord.read(buffer,0,samples_per_frame);
                    //判断是否读取成功
                    if(bufferReadResult == AudioRecord.ERROR_BAD_VALUE || bufferReadResult == AudioRecord.ERROR_INVALID_OPERATION)
                        Log.e(TAG, "Read error");
                    if(audioRecord!=null) {
                        audioEncoder.offerAudioEncoder(buffer,audioPresentationTimeNs);
                    }

                }
                if(audioRecord!=null) {
                    audioRecord.setRecordPositionUpdateListener(null);
                    audioRecord.stop();
                    audioRecord.release();
                    audioRecord = null;
                }


        }
    }




}
