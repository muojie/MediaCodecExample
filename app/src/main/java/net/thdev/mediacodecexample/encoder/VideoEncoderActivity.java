package net.thdev.mediacodecexample.encoder;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * MediaCodec SurfaceHolder Example
 * @author taehwan
 *
 */
public class VideoEncoderActivity extends Activity implements SurfaceHolder.Callback {
    private VideoEncoderThread mVideoEncoder;

    private static final String FILE_PATH = Environment.getExternalStorageDirectory() + "/Tennis_1920x1080_24.yuv";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SurfaceView surfaceView = new SurfaceView(this);
        surfaceView.getHolder().addCallback(this);
        setContentView(surfaceView);

        mVideoEncoder = new VideoEncoderThread();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,	int height) {
        if (mVideoEncoder != null) {
//            if (mVideoEncoder.init(holder.getSurface(), FILE_PATH)) {
            if (mVideoEncoder.init(1920, 1080, 25, 5000*1000, FILE_PATH)) {
                mVideoEncoder.start();
            } else {
                mVideoEncoder = null;
            }
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (mVideoEncoder != null) {
            mVideoEncoder.StopThread();
        }
    }

}
