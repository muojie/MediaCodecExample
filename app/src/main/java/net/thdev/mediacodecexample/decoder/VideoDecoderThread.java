package net.thdev.mediacodecexample.decoder;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import android.graphics.Rect;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;
import android.view.Surface;

import net.thdev.mediacodecexample.utils.JpegEncoder;

public class VideoDecoderThread extends Thread {
	private static final String VIDEO = "video/";
	private static final String TAG = "VideoDecoder";
	private static final boolean VERBOSE = true;           // lots of logging
	private MediaExtractor mExtractor;
	private MediaCodec mDecoder;
	private int mWidth = 0;
	private int mHeight = 0;
	
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
					
					mDecoder.start();
					break;
				}
			}
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return true;
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

					int sampleSize = mExtractor.readSampleData(inputBuffer, 0);
					
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
				// As soon as we call releaseOutputBuffer, the buffer will be forwarded
				// to SurfaceTexture to convert to a texture.  The API doesn't guarantee
				// that the texture will be available before the call returns, so we
				// need to wait for the onFrameAvailable callback to fire.
				if (doRender) {
					if (VERBOSE) Log.d(TAG, "awaiting decode of frame " + decodeCount);
					MediaFormat bufferFormat = mDecoder.getOutputFormat(outIndex);
					System.err.println(bufferFormat);

					//ByteBuffer outputBuffer = codec.getOutputBuffer(outIndex);
					Image outImage = mDecoder.getOutputImage(outIndex);
					System.err.println(outImage.getPlanes()[1].getBuffer().remaining());
					System.err.println(outImage.getPlanes()[1].getPixelStride());
					System.err.println(outImage.getPlanes()[1].getRowStride());

/*
                            System.err.println(outputBuffer.limit());
                            System.err.println(outputBuffer.capacity());
                            System.err.println(outputBuffer.mark());
                            System.err.println(outputBuffer.position());
                            System.err.println(outputBuffer.remaining());
*/
/*
					FileOutputStream outStream;
					try {
						outStream = new FileOutputStream(String.format("./output-%03d.jpg", decodeCount));
						//outStream = new FileOutputStream("/dev/null");
					} catch (IOException ioe) {
						throw new RuntimeException("Unable to create output file ", ioe);
					}

					ByteArrayOutputStream stream = new ByteArrayOutputStream();
					//System.err.println(outputBuffer.remaining());
					Rect rect = new Rect(0, 0, mWidth, mHeight);

					long before = System.currentTimeMillis();
					byte[] arr = JpegEncoder.getDataFromImage(outImage, JpegEncoder.COLOR_FormatNV21);
					byte[] result = JpegEncoder.encode(arr, mWidth, mHeight);
					System.err.println(result.length);
					System.err.println("time");
					System.err.println(System.currentTimeMillis()-before);
					//YuvImage image = new YuvImage(arr, ImageFormat.NV21, width, height, null);
					//System.err.println(System.currentTimeMillis()-before);
					//image.compressToJpeg(rect, 100, outStream);
					try {
						outStream.write(result);
						System.err.println(System.currentTimeMillis()-before);
						outStream.close();
					} catch (Exception ignore) {
					}
					//outputSurface.awaitNewImage();
					//outputSurface.drawImage(true);
					decodeCount++;*/
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
	
	public void close() {
		eosReceived = true;
	}
}
