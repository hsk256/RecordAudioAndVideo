/*
 * Copyright (c) 2014 Yrom Wang <http://www.yrom.net>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.creativeboy.audioandvideocapture.encoder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Surface;
import android.widget.Toast;

/**
 * @author David
 */
public class ScreenRecorder extends Thread
{
	private static final String TAG = "ScreenRecorder";

	private int mWidth;
	private int mHeight;
	private String mDstPath;
	private DisplayManager mDisplayManager;
	private static final int BITRATE = 6000000; //比特率
	private VirtualDisplay mVirtualDisplay;
	private MediaCodec mEncoder; // 编码器
	private Surface mSurface;
	private MediaMuxer mMuxer; // 混合
	private AtomicBoolean encoderDone = new AtomicBoolean(false); // 原子操作
	private MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
	private int mVideoTrackIndex = -1;
	private boolean mMuxerStarted = false;
	// parameters for the encoder
	private static final int FRAME_RATE = 24; // 24fps
	private static final int IFRAME_INTERVAL = 10; // 10 seconds between
													// I-frames
	private static final int TIMEOUT_US = 10000;
	private static final String MIME_TYPE = "video/avc"; // H.264 Advanced Video
															// Coding
	private  int mDpi = 1;
	private MediaMuxerWrapper mediaMuxerWrapper;
	public ScreenRecorder(int width, int height,
			 String dstPath,DisplayManager dm)
	{
		super(TAG);
		mWidth = width;
		mHeight = height;
		mDstPath = dstPath;
		mDisplayManager = dm;
	}

	/**
	 * make thread quit
	 */
	public final void quit()
	{
		encoderDone.set(true);
	}

	private void prepareEncoder() throws IOException
	{
		MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, mWidth,
				mHeight);
		// 封装描述媒体数据格式的信息，可以是音频或视频。
		format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
				MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
		format.setInteger(MediaFormat.KEY_BIT_RATE, BITRATE);
		format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
		format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);// 关键帧间隔时间
																				// 10
		mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);// 实例化一个编码器支持的MIME类型的数据输出。
		mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
		mSurface = mEncoder.createInputSurface();
		mEncoder.start();
//		mMuxer = new MediaMuxer(mDstPath,
//				MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

		Log.d(TAG,"mEncoder--"+mEncoder);

		//if(!MediaMuxerWrapper.isInstance) {
			Log.d(TAG,"mediamuxer is instance?--"+MediaMuxerWrapper.isInstance);
			mediaMuxerWrapper = new MediaMuxerWrapper(mDstPath);
		//}

	}

	@Override
	public void run()
	{
		try
		{
			try
			{
				prepareEncoder();

			} catch (IOException e)
			{
				throw new RuntimeException(e);
			}
			mVirtualDisplay = mDisplayManager.createVirtualDisplay(TAG, mWidth,
					mHeight, mDpi, mSurface,
					DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
							);
			recordVirtualDisplay();
		} finally
		{
			try
			{
				release();
				mediaMuxerWrapper.release();
			} catch (Exception e)
			{
				e.printStackTrace();
			}
		}
	}

	private void recordVirtualDisplay()
	{
		ByteBuffer[] encoderOutputBuffers = mEncoder.getOutputBuffers();
		while (!encoderDone.get())
		{
			int encoderIndex = mEncoder.dequeueOutputBuffer(mBufferInfo,
					TIMEOUT_US);
			// Log.i(TAG, "dequeue output buffer index=" + encoderIndex);
			if (encoderIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED)
			{
				// not expected for an encoder
				if (mMuxerStarted)
				{
					throw new IllegalStateException("output format already changed!");
				}
				MediaFormat newFormat = mEncoder.getOutputFormat();

				// Log.i(TAG,
				// "output format changed.\n new format: " + newFormat.toString());
				//mVideoTrackIndex = mMuxer.addTrack(newFormat);
				Log.d(TAG,"mVideotrackIndex---"+mVideoTrackIndex);
				//mMuxer.start();
				mVideoTrackIndex = mediaMuxerWrapper.addTrack(newFormat);
				if(!mediaMuxerWrapper.isStarted()) {
					Log.d(TAG,"mediaMuxerWrapper is stared");
					mediaMuxerWrapper.start();
				}
				mMuxerStarted = true;
			} else if (encoderIndex == MediaCodec.INFO_TRY_AGAIN_LATER)
			{
				// Log.d(TAG, "retrieving buffers time out!");
				try
				{
					// wait 10ms
					Thread.sleep(10);
				} catch (InterruptedException e)
				{
					Log.e("@@", e.getMessage());
				}
			} else if (encoderIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED)
			{
				encoderOutputBuffers = mEncoder.getOutputBuffers();
			} else if (encoderIndex >= 0)
			{
				if (!mMuxerStarted)
				{
					throw new IllegalStateException(
							"MediaMuxer dose not call addTrack(format) ");
				}
				encodeToVideoTrack(encoderOutputBuffers[encoderIndex]);

				mEncoder.releaseOutputBuffer(encoderIndex, false);
			}
		}
	}

	private void encodeToVideoTrack(ByteBuffer encodedData)
	{
		if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0)
		{
			// // The codec config data was pulled out and fed to the muxer when
			// we got
			// // the INFO_OUTPUT_FORMAT_CHANGED status.
			// // Ignore it.
			// Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
			mBufferInfo.size = 0;
		}
		if (mBufferInfo.size == 0)
		{
			Log.d(TAG, "info.size == 0, drop it.");
			encodedData = null;
		} else
		{
			// Log.d(TAG, "got buffer, info: size=" + mBufferInfo.size
			// + ", presentationTimeUs=" + mBufferInfo.presentationTimeUs
			// + ", offset=" + mBufferInfo.offset);
		}

		if (encodedData != null)
		{
			encodedData.position(mBufferInfo.offset);
			encodedData.limit(mBufferInfo.offset + mBufferInfo.size);
			//mMuxer.writeSampleData(mVideoTrackIndex, encodedData, mBufferInfo);
			mediaMuxerWrapper.writeSampleData(mVideoTrackIndex,encodedData,mBufferInfo);
			 Log.i(TAG, "sent " + mBufferInfo.size + " bytes to muxer...");
		}
	}


	private void release()
	{
		if (mEncoder != null)
		{
			mEncoder.stop();
			mEncoder.release();
			mEncoder = null;
		}
		if (mVirtualDisplay != null)
		{
			mVirtualDisplay.release();
		}
		if (mDisplayManager != null)
		{
			mDisplayManager = null;
		}
//		if (mMuxer != null)
//		{
//			mMuxer.stop();
//			mMuxer.release();
//			mMuxer = null;
//		}
	}
}
