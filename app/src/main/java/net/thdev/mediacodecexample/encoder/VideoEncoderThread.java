package net.thdev.mediacodecexample.encoder;

import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;

import net.thdev.mediacodecexample.utils.JpegEncoder;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class VideoEncoderThread extends Thread {
	private static final String VIDEO = "video/";
	private static final String TAG = "VideoEncoder";
	private static final boolean VERBOSE = true;           // lots of logging
	private MediaExtractor mExtractor;
	private MediaCodec mEncoder;
	private int m_width;
	private int m_height;
	private int m_framerate;
	private boolean mToSeek = false;
	private int mToSeekPTS = 0;
	private long lastSeekedTo = 0;
	private long lastOffset = 0;
	private int mWidth = 0;
	private int mHeight = 0;
	private int TIMEOUT_USEC = 12000;
	private FileInputStream fs;
	private int mOneFrameLen = 0;
	private int mFPS = 30;

	byte[] m_info = null;

	public byte[] configbyte;

	public boolean init(int width, int height, int framerate, int bitrate, String yuvPath) {

		m_width  = width;
		m_height = height;
		m_framerate = framerate;
		mOneFrameLen = m_width*m_height*3/2;

		try {
			fs = new FileInputStream(yuvPath);
		} catch (Exception e) {
			Log.e(TAG, e.toString());
		}

		MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", width, height);
		mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
		mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
		mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
		mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 3);
		try {
			mEncoder = MediaCodec.createEncoderByType("video/avc");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		mEncoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
		mEncoder.start();
		showSupportedColorFormat(mEncoder.getCodecInfo().getCapabilitiesForType("video/avc"));
		createfile();

		return true;
	}

	private static String path = Environment.getExternalStorageDirectory() + "/test1.h264";
	private BufferedOutputStream outputStream;
	FileOutputStream outStream;
	private void createfile(){
		File file = new File(path);
		if(file.exists()){
			file.delete();
		}
		try {
			outputStream = new BufferedOutputStream(new FileOutputStream(file));
		} catch (Exception e){
			e.printStackTrace();
		}
	}

	private void StopEncoder() {
		try {
			mEncoder.stop();
			mEncoder.release();
		} catch (Exception e){
			e.printStackTrace();
		}
	}

	ByteBuffer[] inputBuffers;
	ByteBuffer[] outputBuffers;

	public boolean isRuning = false;

	public void StopThread(){
		isRuning = false;
		try {
			StopEncoder();
			outputStream.flush();
			outputStream.close();
			Log.e(TAG, "Stop thread");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	int count = 0;

	@Override
	public void run() {
		isRuning = true;
		byte[] input = null;
		long pts =  0;
		long generateIndex = 0;

		while (isRuning) {
//			if (MainActivity.YUVQueue.size() >0){
//				input = MainActivity.YUVQueue.poll();
//				byte[] yuv420sp = new byte[m_width*m_height*3/2];
//				NV21ToNV12(input,yuv420sp,m_width,m_height);
//				input = yuv420sp;
//			}
			if (mEncoder != null) {
				try {
					byte[] bufferSrc = new byte[mOneFrameLen];
					byte[] bufferDest = new byte[mOneFrameLen];
					int len = fs.read(bufferSrc, 0, mOneFrameLen);
					swapYV12toNV21(bufferSrc, bufferDest, m_width, m_height);
					if (-1 != len) {
						long startMs = System.currentTimeMillis();
						ByteBuffer[] inputBuffers = mEncoder.getInputBuffers();
						ByteBuffer[] outputBuffers = mEncoder.getOutputBuffers();
						int inputBufferIndex = mEncoder.dequeueInputBuffer(-1);
						if (inputBufferIndex >= 0) {
							pts = computePresentationTime(generateIndex);
							ByteBuffer inputBuffer = mEncoder.getInputBuffer(inputBufferIndex);
							inputBuffer.clear();
							inputBuffer.put(bufferDest);
							mEncoder.queueInputBuffer(inputBufferIndex, 0, bufferDest.length, pts, 0);
							generateIndex += 1;
						} else {
							Log.e(TAG, "dequeue input buffer failed");
						}
					}

					BufferInfo bufferInfo = new BufferInfo();
					int outputBufferIndex = mEncoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
					while (outputBufferIndex >= 0) {
						Log.i(TAG, "Get H264 Buffer Success! flag = "+bufferInfo.flags+",pts = "+bufferInfo.presentationTimeUs+"");
						ByteBuffer outputBuffer = mEncoder.getOutputBuffer(outputBufferIndex);
						byte[] outData = new byte[bufferInfo.size];
						outputBuffer.get(outData);
						if(bufferInfo.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG){
							configbyte = new byte[bufferInfo.size];
							configbyte = outData;
						}else if(bufferInfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME){
							byte[] keyframe = new byte[bufferInfo.size + configbyte.length];
							System.arraycopy(configbyte, 0, keyframe, 0, configbyte.length);
							System.arraycopy(outData, 0, keyframe, configbyte.length, outData.length);

							outputStream.write(keyframe, 0, keyframe.length);
						}else{
							outputStream.write(outData, 0, outData.length);
						}
						outputStream.flush();
						mEncoder.releaseOutputBuffer(outputBufferIndex, false);
						outputBufferIndex = mEncoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
					}

				} catch (Throwable t) {
					t.printStackTrace();
				}
			} else {
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private void NV21ToNV12(byte[] nv21,byte[] nv12,int width,int height){
		if(nv21 == null || nv12 == null)return;
		int framesize = width*height;
		int i = 0,j = 0;
		System.arraycopy(nv21, 0, nv12, 0, framesize);
		for(i = 0; i < framesize; i++){
			nv12[i] = nv21[i];
		}
		for (j = 0; j < framesize/2; j+=2)
		{
			nv12[framesize + j-1] = nv21[j+framesize];
		}
		for (j = 0; j < framesize/2; j+=2)
		{
			nv12[framesize + j] = nv21[j+framesize-1];
		}
	}

	private void swapYV12toNV12(byte[]yv12bytes, byte[] nv12bytes, int width, int height)
	{
		int	nLenY = width * height;
		int	nLenU = nLenY /	4;
		System.arraycopy(yv12bytes,	0, nv12bytes, 0, width * height);
		for(int i = 0; i < nLenU; i++) {
			nv12bytes[nLenY + 2	* i + 1] = yv12bytes[nLenY + i];
			nv12bytes[nLenY + 2	* i] = yv12bytes[nLenY + nLenU + i];
		}
	}

	private void swapYV12toNV21(byte[]yv12bytes, byte[] nv21bytes, int width, int height)
	{
		int	nLenY = width * height;
		int	nLenU = nLenY /	4;
		System.arraycopy(yv12bytes,	0, nv21bytes, 0, width * height);
		for(int i = 0; i < nLenU; i++) {
			nv21bytes[nLenY + 2	* i] = yv12bytes[nLenY + i];
			nv21bytes[nLenY + 2	* i + 1] = yv12bytes[nLenY + nLenU + i];
		}
	}
	/**
	 * Generates the presentation time for frame N, in microseconds.
	 */
	private long computePresentationTime(long frameIndex) {
		return 132 + frameIndex * 1000000 / m_framerate;
	}

	private static void dumpFile(String fileName, byte[] data) {
		FileOutputStream outStream;
		try {
			outStream = new FileOutputStream(fileName);
		} catch (IOException ioe) {
			throw new RuntimeException("Unable to create output file " + fileName, ioe);
		}
		try {
			outStream.write(data);
			outStream.close();
		} catch (IOException ioe) {
			throw new RuntimeException("failed writing data to file " + fileName, ioe);
		}
	}

	public void encodeAndOutput(byte[] arr, String file) {
		try {
			long before = System.currentTimeMillis();
			Log.d(TAG, "out file: " + file);
			FileOutputStream outStream;
			outStream = new FileOutputStream(file);

			byte[] result = JpegEncoder.encode(arr, mWidth, mHeight);
			outStream.write(result);

//			Rect rect = outImage.getCropRect();
//			YuvImage yuvImage = new YuvImage(arr, ImageFormat.NV21, rect.width(), rect.height(), null);
//			Log.d(TAG, "time used(yuv image): " + (System.currentTimeMillis()-before));
//			yuvImage.compressToJpeg(rect, 100, outStream);

			Log.d(TAG, "time used(compress to jpeg): " + (System.currentTimeMillis()-before) + ", len: " + result.length);
			outStream.close();
		} catch (IOException ioe) {
			Log.d(TAG, ioe.toString());
//			throw new RuntimeException("Unable to create output file ", ioe);
		}
	}

	private void showSupportedColorFormat(MediaCodecInfo.CodecCapabilities caps) {
		System.out.print("supported color format: ");
		for (int c : caps.colorFormats) {
			System.out.print(c + "\t");
		}
		System.out.println();
	}
}
