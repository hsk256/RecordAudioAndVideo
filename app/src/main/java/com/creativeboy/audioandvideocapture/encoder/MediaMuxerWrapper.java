package com.creativeboy.audioandvideocapture.encoder;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by heshaokang on 2015/5/16.
 */
public class MediaMuxerWrapper {
    private static final String TAG = "MediaMuxerWrapper";
    private ScreenRecorder videoEncoder;
    private AudioRecorder audioEncoder;
    private MediaMuxer mMediaMuxer;
    private boolean muxerStarted = false;
    public static boolean isInstance  = false;
    public  MediaMuxerWrapper(String filepath) {
        setMuxer(filepath);
    }
    synchronized void setMuxer(String filepath) {
        try {
            //File file = new File(Environment.getExternalStorageDirectory()+"/VideoAndAudioRecord/"+System.currentTimeMillis()+".mp4");
            mMediaMuxer = new MediaMuxer(filepath,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            Log.d(TAG,"MediaMuxerWrapper--"+mMediaMuxer);
            isInstance = true;

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    synchronized boolean isStarted() {
        Log.d(TAG,"isStarted");
        return muxerStarted;
    }

    public void addAudioEncoder(AudioRecorder encoder) {

        if(audioEncoder!=null) {
            throw new IllegalStateException("Audio encoder already added");
        }
        this.audioEncoder = encoder;
    }
    public void addVideoEncoder(ScreenRecorder encoder) {
        if(videoEncoder!=null) {
            throw new IllegalStateException("Video encoder already added");
        }
        this.videoEncoder = encoder;
    }

    synchronized boolean start() {
        mMediaMuxer.start();
        muxerStarted = true;
        return muxerStarted;
    }

    synchronized void stop() {
        mMediaMuxer.stop();
        mMediaMuxer.release();
        muxerStarted = false;

    }

    synchronized int addTrack(MediaFormat format) {
        if(muxerStarted) {
            throw new IllegalStateException("muxer already started");
        }
        int trackIndex = mMediaMuxer.addTrack(format);
        return trackIndex;
    }

    synchronized void writeSampleData(int trackIndex,ByteBuffer byteBuffer,MediaCodec.BufferInfo bufferInfo) {
        mMediaMuxer.writeSampleData(trackIndex,byteBuffer,bufferInfo);
    }

    synchronized void release() {
        if(muxerStarted) {
            if(mMediaMuxer!=null) {
                mMediaMuxer.stop();
                mMediaMuxer.release();
            }
        }
    }

}
