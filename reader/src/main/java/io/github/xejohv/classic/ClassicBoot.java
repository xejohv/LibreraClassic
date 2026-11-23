package io.github.xejohv.classic;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Process;

/** :main 进程的极轻启动逻辑：锁任务白名单 + 默认 Home，不碰任何阅读器类。 */
public class ClassicBoot {

    public static void apply(Context c) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) c.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = new ComponentName(c, ClassicAdmin.class);
            if (dpm == null || !dpm.isDeviceOwnerApp(c.getPackageName())) {
                return;
            }
            dpm.setLockTaskPackages(admin, new String[]{c.getPackageName()});

            IntentFilter f = new IntentFilter(Intent.ACTION_MAIN);
            f.addCategory(Intent.CATEGORY_HOME);
            f.addCategory(Intent.CATEGORY_DEFAULT);
            dpm.addPersistentPreferredActivity(admin, f, new ComponentName(c, ClassicHomeActivity.class));
        } catch (Throwable ignored) {
        }
    }

    public static String processName(Context c) {
        try {
            return c.getPackageName() + ":main";
        } catch (Throwable t) {
            return "";
        }
    }
}