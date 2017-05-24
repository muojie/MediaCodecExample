package net.thdev.mediacodecexample;

/**
 * Created by lenovo on 2017/5/24.
 */

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import net.thdev.mediacodec.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MyMainActivity extends Activity implements VideoToFrames.Callback {
    private static final String TAG = "MyMainActivity";
    private static final int REQUEST_CODE_GET_FILE_PATH = 1;
    private static final int MSG_WHAT_UPDATE_INFO = 0;
    private static final int MSG_WHAT_START_DECODE = 1;
    private OutputImageFormat outputImageFormat;
    private MyMainActivity self = this;
    private String outputDir;

    VideoToFrames mVideoToFrames = null;
    private List<String> mFileLists = new ArrayList<String>();  //结果 List
    private int mDecodeFrameCount = 0;
    private int mDecodeOneVideo = 0;
    private long mTotalUsedTime = 0;
    private long mJpegEncTime = 0;
    private long mTotalVideoDuration = 0;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initImageFormatSpinner();

        final Button buttonFilePathInput = (Button) findViewById(R.id.button_file_path_input);
        buttonFilePathInput.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getFilePath(REQUEST_CODE_GET_FILE_PATH);
            }
        });

        final Button buttonStart = (Button) findViewById(R.id.button_start);
        buttonStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mDecodeOneVideo = 0;
                mDecodeFrameCount = 0;
                mTotalUsedTime = 0;
                mJpegEncTime = 0;
                mTotalVideoDuration = 0;

                EditText editTextOutputFolder = (EditText) findViewById(R.id.folder_created);
                outputDir = Environment.getExternalStorageDirectory() + "/" + editTextOutputFolder.getText().toString();
                EditText editTextInputFilePath = (EditText) findViewById(R.id.file_path_input);
                String inputFilePath = editTextInputFilePath.getText().toString();
//                File file = new File(inputFilePath);
//                GetFiles(file.getParent(), null, false);
                GetFiles(inputFilePath, null, false);
                onFinishDecode();
            }
        });
    }

    private void startDecode() {
        if(mFileLists.size() > 0) {
            mVideoToFrames = new VideoToFrames();
//            VideoToFrames videoToFrames1 = new VideoToFrames();
//            VideoToFrames videoToFrames2 = new VideoToFrames();
//            VideoToFrames videoToFrames3 = new VideoToFrames();
            mVideoToFrames.setCallback(self);
            try {
                mVideoToFrames.setSaveFrames(outputDir + "1", outputImageFormat);
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

    private void initImageFormatSpinner() {
        Spinner barcodeFormatSpinner = (Spinner) findViewById(R.id.image_format);
        ArrayAdapter<OutputImageFormat> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, OutputImageFormat.values());
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        barcodeFormatSpinner.setAdapter(adapter);
        barcodeFormatSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                outputImageFormat = OutputImageFormat.values()[position];
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    private void getFilePath(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(Intent.createChooser(intent, "Select a File"), requestCode);
        } else {
            new AlertDialog.Builder(this).setTitle("未找到文件管理器")
                    .setMessage("请安装文件管理器以选择文件")
                    .setPositiveButton("确定", null)
                    .show();
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        int id = 0;
        switch (requestCode) {
            case REQUEST_CODE_GET_FILE_PATH:
                id = R.id.file_path_input;
                break;
        }
        if (resultCode == Activity.RESULT_OK) {
            EditText editText = (EditText) findViewById(id);
            String curFileName = data.getData().getPath();
            editText.setText(curFileName);
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
        TextView textView = (TextView) findViewById(R.id.info);
        textView.setText(info);
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
            msg.obj += ", 视频总时长: " + mTotalVideoDuration + ", 总耗时(ms): " + mTotalUsedTime;
            msg.obj += ", jpeg编码耗时(ms): " + mJpegEncTime;
            handler.sendMessage(msg);
        }
    }
}