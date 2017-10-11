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

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class VideoEncoderThread extends Thread {
	private static final String VIDEO = "video/";
	private static final String TAG = "VideoDecoder";
	private static final boolean VERBOSE = true;           // lots of logging
	private MediaExtractor mExtractor;
	private MediaCodec mDecoder;
	private boolean mSeeked = false;
	private boolean mToSeek = false;
	private int mToSeekPTS = 0;
	private long lastSeekedTo = 0;
	private long lastOffset = 0;
	private int mWidth = 0;
	private int mHeight = 0;
	private static final int SAMPLING_PERIOD	= 3000;				//3s
	private int mFPS = 30;
	
	private boolean eosReceived;
	
	public boolean init(Surface surface, String filePath) {
		eosReceived = false;
		try {
			mExtractor = new MediaExtractor();
			mExtractor.setDataSource(filePath);
			
			for (int i = 0; i < mExtractor.getTrackCount(); i++) {
				MediaFormat format = mExtractor.getTrackFormat(i);
				
				String mime = format.getString(MediaFormat.KEY_MIME);
				if (mime.startsWith(VIDEO)) {
					mExtractor.selectTrack(i);
					mDecoder = MediaCodec.createDecoderByType(mime);
					mWidth = format.getInteger(MediaFormat.KEY_WIDTH);
					mHeight = format.getInteger(MediaFormat.KEY_HEIGHT);
					try {
						Log.d(TAG, "format : " + format);
						mDecoder.configure(format, null, null, 0 /* Decoder */);

					} catch (IllegalStateException e) {
						Log.e(TAG, "codec '" + mime + "' failed configuration. " + e);
						return false;
					}
					showSupportedColorFormat(mDecoder.getCodecInfo().getCapabilitiesForType(mime));
					mDecoder.start();
					break;
				}
			}
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return true;
	}

	public void seekTo(int i) {
		mSeeked = true;
		Log.d(TAG, "SeekTo Requested to : " + i);
		long beforeSeek = mExtractor.getSampleTime()/1000;
		Log.d(TAG, "SampleTime Before SeekTo : " + mExtractor.getSampleTime() / 1000);
		mExtractor.seekTo(i * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
		long afterSeek = mExtractor.getSampleTime()/1000;
		Log.d(TAG, "SampleTime After SeekTo : " + mExtractor.getSampleTime() / 1000);
		mToSeek = false;

//		if(afterSeek < beforeSeek) {
//			mExtractor.seekTo(beforeSeek*1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
//		}

//		lastOffset = mExtractor.getSampleTime() / 1000;

//		startMs = System.currentTimeMillis();
//		diff = (lastOffset - lastPresentationTimeUs / 1000);
//
//		Log.d(TAG, "SeekTo with diff : " + diff);
	}

	@Override
	public void run() {
		BufferInfo info = new BufferInfo();

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
			ByteBuffer[] inputBuffers = mDecoder.getInputBuffers();
			mDecoder.getOutputBuffers();
		}
		
		boolean isInput = true;
		boolean first = false;
		long startWhen = 0;
		int decodeCount = 0;
		
		while (!eosReceived) {
			if (isInput) {
				int inputIndex = mDecoder.dequeueInputBuffer(10000);
				if (inputIndex >= 0) {
					ByteBuffer inputBuffer;
					// SDK_INT > LOLLIPOP
					inputBuffer = mDecoder.getInputBuffer(inputIndex);
					if (mToSeek) {
						seekTo(mToSeekPTS);
					}
					int sampleSize = mExtractor.readSampleData(inputBuffer, 0);
					lastOffset = mExtractor.getSampleTime() / 1000;
					
					if (mExtractor.advance() && sampleSize > 0) {
						mDecoder.queueInputBuffer(inputIndex, 0, sampleSize, mExtractor.getSampleTime(), 0);
						
					} else {
						Log.d(TAG, "InputBuffer BUFFER_FLAG_END_OF_STREAM");
						mDecoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
						isInput = false;
					}
				}
			}
			
			int outIndex = mDecoder.dequeueOutputBuffer(info, 10000);
			switch (outIndex) {
			case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
				Log.d(TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
				mDecoder.getOutputBuffers();
				break;

			case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
				Log.d(TAG, "INFO_OUTPUT_FORMAT_CHANGED format : " + mDecoder.getOutputFormat());
				break;
				
			case MediaCodec.INFO_TRY_AGAIN_LATER:
//				Log.d(TAG, "INFO_TRY_AGAIN_LATER");
				break;
				
			default:
				if (!first) {
					startWhen = System.currentTimeMillis();
					first = true;
				}
				try {
					long sleepTime = (info.presentationTimeUs / 1000) - (System.currentTimeMillis() - startWhen);
					Log.d(TAG, "info.presentationTimeUs : " + (info.presentationTimeUs / 1000) + " playTime: " + (System.currentTimeMillis() - startWhen) + " sleepTime : " + sleepTime + ", status: " + outIndex);
					
					if (sleepTime > 0)
						Thread.sleep(sleepTime);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				boolean doRender = (info.size != 0);
				doRender = Math.abs(info.presentationTimeUs/1000 - decodeCount*SAMPLING_PERIOD) < 100;
				// As soon as we call releaseOutputBuffer, the buffer will be forwarded
				// to SurfaceTexture to convert to a texture.  The API doesn't guarantee
				// that the texture will be available before the call returns, so we
				// need to wait for the onFrameAvailable callback to fire.
				if (doRender) {
					if (VERBOSE) Log.d(TAG, "awaiting decode of frame " + decodeCount + ", pts: " + info.presentationTimeUs/1000);
					MediaFormat bufferFormat = mDecoder.getOutputFormat(outIndex);
					Log.d(TAG, "buffer format: " + bufferFormat);
					long before = System.currentTimeMillis();

					ByteBuffer outputBuffer = mDecoder.getOutputBuffer(outIndex);
					byte[] arr = new byte[outputBuffer.remaining()];
					outputBuffer.get(arr, 0, arr.length);
					Log.d(TAG, "buffer info: " + outputBuffer.limit() + ", " + outputBuffer.capacity() + ", " +
						outputBuffer.mark() + ", " + outputBuffer.position() + ", " + outputBuffer.remaining() );

//					Image outImage = mDecoder.getOutputImage(outIndex);
//					Log.d(TAG, "image info: " + outImage.getFormat() + ", " +
//							outImage.getPlanes()[1].getBuffer().remaining() + ", " +
//							outImage.getPlanes()[1].getPixelStride() + ", " +
//							outImage.getPlanes()[1].getRowStride());
//					byte[] arr = JpegEncoder.getDataFromImage(outImage, JpegEncoder.COLOR_FormatNV21);

					Log.d(TAG, "time used(format change): " + (System.currentTimeMillis()-before) + ", len: " + arr.length);

					if (arr != null) {
						String filePath = Environment.getExternalStorageDirectory() + "/Pictures/";
						String file = String.format(filePath + "output-%03d.jpg", decodeCount);
//						dumpFile(file, arr);
						encodeAndOutput(arr, file);
					}
					//outputSurface.awaitNewImage();
					//outputSurface.drawImage(true);

					decodeCount++;
					mToSeekPTS = decodeCount * SAMPLING_PERIOD;
					mToSeek = true;
				}
				
				mDecoder.releaseOutputBuffer(outIndex, true /* Surface init */);
				break;
			}
			
			// All decoded frames have been rendered, we can stop playing now
			if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
				Log.d(TAG, "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
				break;
			}
		}
		
		mDecoder.stop();
		mDecoder.release();
		mExtractor.release();
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

	public void close() {
		eosReceived = true;
	}

	private void showSupportedColorFormat(MediaCodecInfo.CodecCapabilities caps) {
		System.out.print("supported color format: ");
		for (int c : caps.colorFormats) {
			System.out.print(c + "\t");
		}
		System.out.println();
	}

	/**
	 * Selects the video track, if any.
	 *
	 * @return the track index, or -1 if no video track is found.
	 */
	private static int selectTrack(MediaExtractor extractor) {
		// Select the first video track we find, ignore the rest.
		int numTracks = extractor.getTrackCount();
		for (int i = 0; i < numTracks; i++) {
			MediaFormat format = extractor.getTrackFormat(i);
			String mime = format.getString(MediaFormat.KEY_MIME);
			if (mime.startsWith("video/")) {
				if (VERBOSE) {
					Log.d(TAG, "Extractor selected track " + i + " (" + mime + "): " + format);
				}
				return i;
			}
		}

		return -1;
	}
}
