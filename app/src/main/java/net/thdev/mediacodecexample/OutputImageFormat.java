package net.thdev.mediacodecexample;

/**
 * Created by lenovo on 2017/5/24.
 */

/**
 * Created by zhantong on 16/9/8.
 */
public enum OutputImageFormat {
    JPEG("JPEG"),
    I420("I420"),
    NV21("NV21");
    private String friendlyName;

    private OutputImageFormat(String friendlyName) {
        this.friendlyName = friendlyName;
    }

    public String toString() {
        return friendlyName;
    }
}
