package io.github.xejohv.podclassicxzs.base

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import io.github.xejohv.podclassicxzs.util.LocaleHelper
import io.github.xejohv.podclassicxzs.util.MediaScanUtil
import java.io.File

/**
 * 合并后的唯一 Application。
 *
 * 两种进程在这里分流，且只走一条：
 * - 默认进程（启动器 + 音乐）：只做 PodClassic 自己的初始化，绝不碰阅读器；
 * - :reader 进程：把初始化交给阅读器入口。走反射调用不是为了好看，
 *   而是让默认进程的字节码里连一个阅读器类都不出现，桌面永远不会被阅读器拖累。
 */
class BaseApplication : Application() {
    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var context: Context
        private const val TAG = "PodClassic"

        fun processName(): String {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Application.getProcessName()?.let { return it }
            }
            return try {
                File("/proc/self/cmdline").readText().trim('\u0000', '\n', ' ')
            } catch (e: Exception) {
                ""
            }
        }

        fun isReaderProcess(): Boolean = processName().endsWith(":reader")
    }

    override fun attachBaseContext(base: Context) {
        // 先按用户选择的语言包一层，Application 的 resources 就是目标语言，
        // 后面 Core.init() -> setLanguage() -> Strings.init() 全部生效
        super.attachBaseContext(LocaleHelper.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        if (isReaderProcess()) {
            // 只有用户真的从桌面点进阅读，这个进程才会存在；到这一步才允许唤醒阅读器
            try {
                Class.forName("com.foobnix.LibreraApp")
                    .getMethod("boot", Application::class.java)
                    .invoke(null, this)
            } catch (t: Throwable) {
                Log.e(TAG, "reader boot failed", t)
            }
            return
        }
        context = this
        Core.init()
        MediaScanUtil.scanMissingAsync(this)
    }
}