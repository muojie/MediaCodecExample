package net.thdev.mediacodecexample;

/**
 * Created by lenovo on 2017/5/24.
 */

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Environment;
import android.util.Log;

import net.thdev.mediacodecexample.utils.JpegEncoder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by zhantong on 16/5/12.
 */
public class VideoToFrames implements Runnable {
    private static final String TAG = "VideoToFrames";
    private static final boolean VERBOSE = true;
    private static final long DEFAULT_TIMEOUT_US = 10000;

    private static final int COLOR_FormatI420 = 1;
    private static final int COLOR_FormatNV21 = 2;


    private final int decodeColorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;

    private LinkedBlockingQueue<byte[]> mQueue;
    private OutputImageFormat outputImageFormat;
    private String OUTPUT_DIR;
    private boolean stopDecode = false;

    private String videoFilePath;
    private Throwable throwable;
    private Thread childThread;

    private static final String VIDEO = "video/";
    private MediaExtractor mExtractor;
    private MediaCodec mDecoder;

    private boolean mToSeek = true;
    private long mToSeekPTS = 0;
    private long lastSeekedTo = 0;
    private long lastOffset = 0;
    private int mWidth = 0;
    private int mHeight = 0;
    private int mVideoMaxInputSize = 0;
    private long mVideoDuration;
    private static final int SAMPLING_PERIOD	= 3000;				//3s
    private int mFPS = 30;
    private boolean eosReceived;
    private long mFirstPTS = 0;

    private long mSeekUsedTime = 0;
    private long mJpegEncTime = 0;
    private long mTotalUsedTime = 0;
    private long mDecodeTimeStart = 0;

    private List<Long> mIFramePTS = new ArrayList<>();  //结果 List

    private Callback callback;

    public interface Callback {
        void onFinishDecode();

        void onDecodeFrame(int index);
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }


    public long getTotalUsedTime() {
        return mTotalUsedTime;
    }

    public long getJpegEncTime() {
        return mJpegEncTime;
    }

    public long getVideoDuration() {
        return mVideoDuration;
    }

    public void setEnqueue(LinkedBlockingQueue<byte[]> queue) {
        mQueue = queue;
    }

    public void setSaveFrames(String dir, OutputImageFormat imageFormat) throws IOException {
        outputImageFormat = imageFormat;
        File theDir = new File(dir);
        if (!theDir.exists()) {
            theDir.mkdirs();
        } else if (!theDir.isDirectory()) {
            throw new IOException("Not a directory");
        }
        OUTPUT_DIR = theDir.getAbsolutePath() + "/";
    }

    public void stopDecode() {
        stopDecode = true;
    }

    public void decode(String videoFilePath) throws Throwable {
        this.videoFilePath = videoFilePath;
        if (childThread == null) {
            childThread = new Thread(this, "decode");
            childThread.start();
            if (throwable != null) {
                throw throwable;
            }
        }
    }

    public void run() {
        try {
            videoDecode(videoFilePath);
        } catch (Throwable t) {
            throwable = t;
        }
    }

    public void videoDecode(String videoFilePath) throws IOException {
        try {
            mExtractor = new MediaExtractor();
            mExtractor.setDataSource(videoFilePath);

            //init
            mSeekUsedTime = 0;
            mDecodeTimeStart = System.currentTimeMillis();

            for (int i = 0; i < mExtractor.getTrackCount(); i++) {
                MediaFormat format = mExtractor.getTrackFormat(i);

                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith(VIDEO)) {
                    mExtractor.selectTrack(i);
                    mDecoder = MediaCodec.createDecoderByType(mime);
                    mWidth = format.getInteger(MediaFormat.KEY_WIDTH);
                    mHeight = format.getInteger(MediaFormat.KEY_HEIGHT);
                    mVideoDuration = format.getLong(MediaFormat.KEY_DURATION)/1000;
//                    mVideoMaxInputSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
                    showSupportedColorFormat(mDecoder.getCodecInfo().getCapabilitiesForType(mime));
//                    if (isColorFormatSupported(decodeColorFormat, mDecoder.getCodecInfo().getCapabilitiesForType(mime))) {
//                        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, decodeColorFormat);
//                        Log.i(TAG, "set decode color format to type " + decodeColorFormat);
//                    } else {
//                        Log.i(TAG, "unable to set decode color format, color format type " + decodeColorFormat + " not supported");
//                    }
                    Log.d(TAG, "format : " + format);
//                    GotIFramePTS();
                    decodeFramesToImage(mDecoder, mExtractor, format);
                    mDecoder.stop();
                    break;
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (mDecoder != null) {
                mDecoder.stop();
                mDecoder.release();
                mDecoder = null;
            }
            if (mExtractor != null) {
                mExtractor.release();
                mExtractor = null;
            }
        }
    }

    private void showSupportedColorFormat(MediaCodecInfo.CodecCapabilities caps) {
        System.out.print("supported color format: ");
        for (int c : caps.colorFormats) {
            System.out.print(c + "\t");
        }
        System.out.println();
    }

    private boolean isColorFormatSupported(int colorFormat, MediaCodecInfo.CodecCapabilities caps) {
        for (int c : caps.colorFormats) {
            if (c == colorFormat) {
                return true;
            }
        }
        return false;
    }

    private void decodeFramesToImage(MediaCodec decoder, MediaExtractor extractor, MediaFormat mediaFormat) {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean sawInputEOS = false;
        boolean sawOutputEOS = false;
        decoder.configure(mediaFormat, null, null, 0);
        decoder.start();
        final int width = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
        final int height = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
        int InputFrameCount = 0, outputFrameCount = 0, JpegFrameCount = 0;
        while (!sawOutputEOS && !stopDecode) {
            if (!sawInputEOS) {
                int inputBufferId = decoder.dequeueInputBuffer(DEFAULT_TIMEOUT_US);
                if (inputBufferId >= 0) {
                    ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufferId);
                    if(!sawInputEOS) {
                        int sampleSize = extractor.readSampleData(inputBuffer, 0);
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inputBufferId, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            sawInputEOS = true;
                        } else {
                            long presentationTimeUs = extractor.getSampleTime();
                            int sampleFlag = extractor.getSampleFlags();
                            if (VERBOSE)
                                Log.d(TAG, "queue frame, pts:  " + presentationTimeUs / 1000 +
                                        ", flag: " + sampleFlag + ", size: " + sampleSize);
                            decoder.queueInputBuffer(inputBufferId, 0, sampleSize, presentationTimeUs, 0);
                            InputFrameCount++;
                            extractor.advance();
                        }
                    }else {
                        decoder.queueInputBuffer(inputBufferId, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        sawInputEOS = true;
                    }
                }
            }
            int outputBufferId = decoder.dequeueOutputBuffer(info, DEFAULT_TIMEOUT_US);
            if (outputBufferId >= 0) {
                outputFrameCount++;
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    sawOutputEOS = true;
                }
                boolean doRender = (info.size != 0);
//                doRender = (JpegFrameCount == 0) || ((info.presentationTimeUs-mFirstPTS)/(1000 * JpegFrameCount) > SAMPLING_PERIOD);
                if (VERBOSE) Log.d(TAG, "awaiting decode of frame " + JpegFrameCount + ", pts: " + info.presentationTimeUs/1000);
                if (doRender) {
                    if (JpegFrameCount == 0)
                        mFirstPTS = info.presentationTimeUs;
                    JpegFrameCount++;
                    if (callback != null) {
                        callback.onDecodeFrame(JpegFrameCount);
                    }
                    long before = System.currentTimeMillis();
                    MediaFormat bufferFormat = mDecoder.getOutputFormat(outputBufferId);
                    Log.d(TAG, "buffer format: " + bufferFormat);

//                    ByteBuffer outputBuffer = mDecoder.getOutputBuffer(outputBufferId);
//                    byte[] arr = new byte[outputBuffer.remaining()];
//                    outputBuffer.get(arr, 0, arr.length);
//                    Log.d(TAG, "buffer info: " + outputBuffer.limit() + ", " + outputBuffer.capacity() + ", " +
//                            outputBuffer.mark() + ", " + outputBuffer.position() + ", " + outputBuffer.remaining() );
//
//                    Log.d(TAG, "time used(format change): " + (System.currentTimeMillis()-before) + ", len: " + arr.length);

                    Image image = null;
                    image = decoder.getOutputImage(outputBufferId);
                    Log.d(TAG, "image info: " + image.getFormat() + ", " +
                            image.getPlanes()[1].getBuffer().remaining() + ", " +
                            image.getPlanes()[1].getPixelStride() + ", " +
                            image.getPlanes()[1].getRowStride());
					byte[] arr = JpegEncoder.getDataFromImage(image, JpegEncoder.COLOR_FormatNV21);  //jpeg软编用COLOR_FormatNV21， rk硬编用COLOR_FormatI420
                    Log.d(TAG, "time used(format change): " + (System.currentTimeMillis()-before) + ", len: " + arr.length);

//                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
//                    byte[] arr = new byte[buffer.remaining()];
//                    buffer.get(arr);
//                    if (mQueue != null) {
//                        try {
//                            mQueue.put(arr);
//                        } catch (InterruptedException e) {
//                            e.printStackTrace();
//                        }
//                    }

                    if (outputImageFormat != null) {
                        String fileName;
                        switch (outputImageFormat) {
                            case I420:
                                fileName = OUTPUT_DIR + String.format("frame_%05d_I420_%dx%d.yuv", JpegFrameCount, width, height);
                                dumpFile(fileName, getDataFromImage(image, COLOR_FormatI420));
                                break;
                            case NV21:
                                fileName = OUTPUT_DIR + String.format("frame_%05d_NV21_%dx%d.yuv", JpegFrameCount, width, height);
                                dumpFile(fileName, getDataFromImage(image, COLOR_FormatNV21));
                                break;
                            case JPEG:
                                fileName = OUTPUT_DIR + String.format("%5d.jpeg", outputFrameCount);
//                                compressToJpeg(fileName, image);
                                if (arr != null && (outputFrameCount % 5 == 0)) {
                                    encodeAndOutput(arr, fileName);
                                }
                                break;
                        }
                    }
                    if (image != null)
                        image.close();
                    mToSeekPTS = JpegFrameCount * SAMPLING_PERIOD;
//                    mToSeek = true;
                    mJpegEncTime += (System.currentTimeMillis() - before);
                    Log.d(TAG, "time used(jpeg encode): " + (System.currentTimeMillis()-before));
                    //outputSurface.awaitNewImage();
                    //outputSurface.drawImage(true);
                }
                decoder.releaseOutputBuffer(outputBufferId, true);
            }
        }
        mTotalUsedTime = System.currentTimeMillis() - mDecodeTimeStart;
        Log.e(TAG, "seek used time: " + mSeekUsedTime + "ms");
        Log.e(TAG, "jpeg encdoe time: " + mJpegEncTime + "ms, count: " + JpegFrameCount);
        Log.e(TAG, "total used time: " + mTotalUsedTime + "ms, Frame in: " + InputFrameCount + ", out: " + outputFrameCount);
        if (callback != null) {
            callback.onFinishDecode();
        }
    }

    public void readTo(Long pts)
    {
        long beforeSeek = mExtractor.getSampleTime()/1000;
        long pts_now = beforeSeek;
//        if (VERBOSE) Log.d(TAG, "SampleSize -> readTo: " + pts + ", Before: " + beforeSeek);

        //分配缓冲
        ByteBuffer inputBuffer = ByteBuffer.allocate(mVideoMaxInputSize);
        while(pts > pts_now) {
            int sampleSize = mExtractor.readSampleData(inputBuffer, 0);
            if (sampleSize < 0) {
                Log.e(TAG, "SampleSize -> readTo: " + pts + ", error");
                return;
            } else {
                mExtractor.advance();
                pts_now = mExtractor.getSampleTime()/1000;
                int sampleFlag = mExtractor.getSampleFlags();
//                if (VERBOSE) Log.d(TAG, "SampleSize -> readTo: " + pts_now);
            }
        }
//        Log.d(TAG, "SampleSize -> readTo: " + pts + ", Before: " + beforeSeek + ", After: " + pts_now);
    }

    public void GotIFramePTS() {
        long beforePTS = 0;
        mIFramePTS.add(beforePTS);
        while(true) {
            mExtractor.seekTo(beforePTS+1, MediaExtractor.SEEK_TO_NEXT_SYNC);
            Long afterSeek = mExtractor.getSampleTime();
            if(afterSeek > beforePTS) {
                Log.e(TAG, "I Frame PTS: " + afterSeek);
                mIFramePTS.add(afterSeek);
                beforePTS = afterSeek;
            } else {
                Log.e(TAG, "I Frame PTS: " + afterSeek + " last: " + beforePTS + " break");
                break;
            }
        }
        mExtractor.seekTo(beforePTS+1, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
    }

    public boolean seekTo(int i) {
        if (mToSeek) {
            if (i < mIFramePTS.size()) {
                mExtractor.seekTo(mIFramePTS.get(i), MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            } else {
                return false;
            }
        }
        return true;
    }

    public void seekTo(long i) {
        long before = System.currentTimeMillis();
        long beforeSeek = mExtractor.getSampleTime()/1000;
        if (VERBOSE)
            Log.d(TAG, "SampleSize -> SeekTo: " + i + ", Before: " + beforeSeek);

        if (i <= beforeSeek) {
            Log.e(TAG, "SampleSize -> SeekTo(" + i + " < " + beforeSeek + ") ===> can't seekTo");
        } else {
            mExtractor.seekTo(i * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            long afterSeek = mExtractor.getSampleTime() / 1000;

            if(afterSeek < i) {
                readTo(beforeSeek);
            }
//          if(afterSeek < beforeSeek) {
//			    readTo(beforeSeek);
//		    }
        }

        if (VERBOSE)
            Log.d(TAG, "SampleSize -> SeekTo After : " + mExtractor.getSampleTime() / 1000);
        mToSeek = false;
        mSeekUsedTime += (System.currentTimeMillis() - before);
//		lastOffset = mExtractor.getSampleTime() / 1000;

//		startMs = System.currentTimeMillis();
//		diff = (lastOffset - lastPresentationTimeUs / 1000);
//
//		Log.d(TAG, "SeekTo with diff : " + diff);
    }

    public void encodeAndOutput(byte[] arr, String file) {
        try {
            long before = System.currentTimeMillis();
            Log.d(TAG, "out file: " + file);
            FileOutputStream outStream;
            outStream = new FileOutputStream(file);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
			YuvImage yuvImage = new YuvImage(arr, ImageFormat.NV21, mWidth, mHeight, null);
			Log.d(TAG, "time used(yuv image): " + (System.currentTimeMillis()-before));
			yuvImage.compressToJpeg(new Rect(0, 0, mWidth, mHeight), 100, out);
            byte[] imageBytes = out.toByteArray();

			//硬编
//            byte[] imageBytes = JpegEncoder.encode(arr, mWidth, mHeight);   //硬编，不支持1080p

            outStream.write(imageBytes);

            //resize

//            Bitmap image = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
//            Bitmap resized = Bitmap.createScaledBitmap(image, newWidth, newHieght, true);
//            resized.compress(Bitmap.CompressFormat.JPEG, 100, outStream);


            Log.d(TAG, "time used(compress to jpeg): " + (System.currentTimeMillis()-before));
            outStream.close();
        } catch (IOException ioe) {
            Log.d(TAG, ioe.toString());
//			throw new RuntimeException("Unable to create output file ", ioe);
        }
    }

    private int selectTrack(MediaExtractor extractor) {
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

    private boolean isImageFormatSupported(Image image) {
        int format = image.getFormat();
        switch (format) {
            case ImageFormat.YUV_420_888:
            case ImageFormat.NV21:
            case ImageFormat.YV12:
                return true;
        }
        return false;
    }

    private byte[] getDataFromImage(Image image, int colorFormat) {
        if (colorFormat != COLOR_FormatI420 && colorFormat != COLOR_FormatNV21) {
            throw new IllegalArgumentException("only support COLOR_FormatI420 " + "and COLOR_FormatNV21");
        }
        if (!isImageFormatSupported(image)) {
            throw new RuntimeException("can't convert Image to byte array, format " + image.getFormat());
        }
        Rect crop = image.getCropRect();
        int format = image.getFormat();
        int width = crop.width();
        int height = crop.height();
        Image.Plane[] planes = image.getPlanes();
        byte[] data = new byte[width * height * ImageFormat.getBitsPerPixel(format) / 8];
        byte[] rowData = new byte[planes[0].getRowStride()];
        if (VERBOSE) Log.v(TAG, "get data from " + planes.length + " planes");
        int channelOffset = 0;
        int outputStride = 1;
        for (int i = 0; i < planes.length; i++) {
            switch (i) {
                case 0:
                    channelOffset = 0;
                    outputStride = 1;
                    break;
                case 1:
                    if (colorFormat == COLOR_FormatI420) {
                        channelOffset = width * height;
                        outputStride = 1;
                    } else if (colorFormat == COLOR_FormatNV21) {
                        channelOffset = width * height + 1;
                        outputStride = 2;
                    }
                    break;
                case 2:
                    if (colorFormat == COLOR_FormatI420) {
                        channelOffset = (int) (width * height * 1.25);
                        outputStride = 1;
                    } else if (colorFormat == COLOR_FormatNV21) {
                        channelOffset = width * height;
                        outputStride = 2;
                    }
                    break;
            }
            ByteBuffer buffer = planes[i].getBuffer();
            int rowStride = planes[i].getRowStride();
            int pixelStride = planes[i].getPixelStride();
            if (VERBOSE) {
                Log.v(TAG, "pixelStride " + pixelStride);
                Log.v(TAG, "rowStride " + rowStride);
                Log.v(TAG, "width " + width);
                Log.v(TAG, "height " + height);
                Log.v(TAG, "buffer size " + buffer.remaining());
            }
            int shift = (i == 0) ? 0 : 1;
            int w = width >> shift;
            int h = height >> shift;
            buffer.position(rowStride * (crop.top >> shift) + pixelStride * (crop.left >> shift));
            for (int row = 0; row < h; row++) {
                int length;
                if (pixelStride == 1 && outputStride == 1) {
                    length = w;
                    buffer.get(data, channelOffset, length);
                    channelOffset += length;
                } else {
                    length = (w - 1) * pixelStride + 1;
                    buffer.get(rowData, 0, length);
                    for (int col = 0; col < w; col++) {
                        data[channelOffset] = rowData[col * pixelStride];
                        channelOffset += outputStride;
                    }
                }
                if (row < h - 1) {
                    buffer.position(buffer.position() + rowStride - length);
                }
            }
            if (VERBOSE) Log.v(TAG, "Finished reading data from plane " + i);
        }
        return data;
    }

    private void dumpFile(String fileName, byte[] data) {
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

    private void compressToJpeg(String fileName, Image image) {
        FileOutputStream outStream;
        try {
            outStream = new FileOutputStream(fileName);
        } catch (IOException ioe) {
            throw new RuntimeException("Unable to create output file " + fileName, ioe);
        }
        Rect rect = image.getCropRect();
        YuvImage yuvImage = new YuvImage(getDataFromImage(image, COLOR_FormatNV21), ImageFormat.NV21, rect.width(), rect.height(), null);
        yuvImage.compressToJpeg(rect, 100, outStream);
    }
}