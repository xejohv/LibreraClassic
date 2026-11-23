package io.github.xejohv.classic;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

/** 设备所有者入口：只有拿到 device owner 才能静默锁任务。 */
public class ClassicAdmin extends DeviceAdminReceiver {
    @Override
    public void onEnabled(Context c, Intent i) {
        super.onEnabled(c, i);
    }
}