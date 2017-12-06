package net.thdev.mediacodecexample;

import android.app.Service;
import android.content.Intent;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import net.thdev.mediacodec.R;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MyService extends Service implements VideoToFrames.Callback {
    private static final String TAG = "MyService";
    private static final int REQUEST_CODE_GET_FILE_PATH = 1;
    private static final int REQUEST_CODE_PICK_DIRECTORY = 1;
    private static final int MSG_WHAT_UPDATE_INFO = 0;
    private static final int MSG_WHAT_START_DECODE = 1;
    private OutputImageFormat outputImageFormat;
    private String outputDir;

    VideoToFrames mVideoToFrames = null;
    private List<String> mFileLists = new ArrayList<String>();  //结果 List
    private int mVideoCount = 0;
    private int mDecodeFrameCount = 0;
    private int mDecodeOneVideo = 0;
    private float mTotalUsedTime = 0;
    private float mJpegEncTime = 0;
    private float mTotalVideoDuration = 0;

    public MyService() {
        Log.d(TAG,"MyService --------");
    }

    @Override
    public void onCreate() {
        Log.d(TAG,"onCreate --------");
        firstEnter();
        super.onCreate();
    }

    final Handler handler = new Handler() {
        public void handleMessage(Message msg) {
            switch ((msg.what)) {
                case MSG_WHAT_UPDATE_INFO:
                    String str = (String) msg.obj;
                    updateInfo(str);
                    break;
                case MSG_WHAT_START_DECODE:
                    startDecode();
            }
        }
    };

    public void firstEnter() {
        mDecodeOneVideo = 0;
        mDecodeFrameCount = 0;
        mTotalUsedTime = 0;
        mJpegEncTime = 0;
        mTotalVideoDuration = 0;

        outputDir = Environment.getExternalStorageDirectory() + "/videoToJPEG";
        String inputFilePath = "/storage/emulated/0/news";
//                File file = new File(inputFilePath);
//                GetFiles(file.getParent(), null, false);
        GetFiles(inputFilePath, null, false);
        onFinishDecode();
    }

    private void startDecode() {
        if(mFileLists.size() > 0) {
            mVideoToFrames = new VideoToFrames();
//            VideoToFrames videoToFrames1 = new VideoToFrames();
//            VideoToFrames videoToFrames2 = new VideoToFrames();
//            VideoToFrames videoToFrames3 = new VideoToFrames();
            mVideoToFrames.setCallback(this);
            try {
                File theDir = new File(outputDir);
                if (!theDir.exists()) {
                    theDir.mkdirs();
                } else if (!theDir.isDirectory()) {
                    throw new IOException("Not a directory");
                }
                mVideoCount++;
                mVideoToFrames.setSaveFrames(outputDir + "/" + Integer.toString(mVideoCount), outputImageFormat);
//                videoToFrames1.setSaveFrames(outputDir + "2", outputImageFormat);
//                videoToFrames2.setSaveFrames(outputDir + "3", outputImageFormat);
//                videoToFrames3.setSaveFrames(outputDir + "4", outputImageFormat);
                updateInfo("运行中...");
                Log.d(TAG, "analyze: " + mFileLists.get(0) + ", 剩余: " + (mFileLists.size()-1));
                mVideoToFrames.decode(mFileLists.get(0));
                mFileLists.remove(0);
//                    videoToFrames1.decode(inputFilePath);
//                    videoToFrames2.decode(inputFilePath);
//                    videoToFrames3.decode(inputFilePath);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        } else {
            Message msg = handler.obtainMessage();
            msg.what = MSG_WHAT_UPDATE_INFO;
            msg.obj = " 没有视频文件";
            handler.sendMessage(msg);
        }
    }

    public void GetFiles(String Path, String Extension, boolean IsIterative)  //搜索目录，扩展名，是否进入子文件夹
    {
        File[] files = new File(Path).listFiles();
        Log.d(TAG, "root path: " + Path);

        for (int i = 0; i < files.length; i++)
        {
            File f = files[i];
            if (f.isFile())
            {
//                if (f.getPath().substring(f.getPath().length() - Extension.length()).equals(Extension))  //判断扩展名
                mFileLists.add(f.getPath());
                Log.e(TAG, "file: " + f.getPath().toString());
            }
            else if (f.isDirectory() && IsIterative && f.getPath().indexOf("/.") == -1)  //忽略点文件（隐藏文件/文件夹）
                GetFiles(f.getPath(), Extension, IsIterative);
        }
        Log.d(TAG,  "file num: " + mFileLists.size());
    }

    private void updateInfo(String info) {
        Log.d(TAG, info);
    }

    public void onDecodeFrame(int index) {
        Message msg = handler.obtainMessage();
        msg.what = MSG_WHAT_UPDATE_INFO;
        msg.obj = "运行中...第" + index + "帧";
        mDecodeOneVideo = index;
        handler.sendMessage(msg);
    }

    public void onFinishDecode() {
        Message msg = handler.obtainMessage();
        mDecodeFrameCount += mDecodeOneVideo;
        if (mVideoToFrames != null) {
            mTotalUsedTime += mVideoToFrames.getTotalUsedTime();
            mJpegEncTime += mVideoToFrames.getJpegEncTime();
            mTotalVideoDuration += mVideoToFrames.getVideoDuration();
            Log.d(TAG, "total time: " + mVideoToFrames.getVideoDuration() + ", use: " + mVideoToFrames.getTotalUsedTime());
        }
        if(mFileLists.size() > 0) {
            msg.what = MSG_WHAT_START_DECODE;
            handler.sendMessage(msg);
        } else {
            msg.what = MSG_WHAT_UPDATE_INFO;
            msg.obj = "完成！" + mDecodeFrameCount + "张图片已存储到" + outputDir;
            msg.obj += ", 视频总时长(s): " + mTotalVideoDuration/1000 + ", 总耗时(s): " + mTotalUsedTime/1000;
            msg.obj += ", jpeg编码耗时(s): " + mJpegEncTime/1000;
            handler.sendMessage(msg);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
