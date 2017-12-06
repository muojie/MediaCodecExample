package net.thdev.mediacodecexample;

import android.app.Application;
import android.content.Intent;
import android.util.Log;

/**
 * Created by haima on 17/9/4.
 */

public class MyApplication extends Application {
    public boolean isPlay = false;

    private static MyApplication myApplication;

    public static MyApplication getMyAplication() {

        return myApplication;
    }

    @Override
    public void onCreate() {
        myApplication = this;
        Log.e("MyApplication", "onCreate-------");
        Intent intent = new Intent(this, MyService.class);
        startService(intent);
        super.onCreate();
    }
}
