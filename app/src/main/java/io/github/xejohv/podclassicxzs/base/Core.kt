package io.github.xejohv.podclassicxzs.base

import io.github.xejohv.podclassicxzs.gestures.GestureService
import io.github.xejohv.podclassicxzs.view.PowerMenuView

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.view.View
import io.github.xejohv.podclassicxzs.activity.MainActivity
import io.github.xejohv.podclassicxzs.service.MediaPresenter
import io.github.xejohv.podclassicxzs.service.MediaService
import io.github.xejohv.podclassicxzs.storage.MusicListTable
import io.github.xejohv.podclassicxzs.storage.MusicTable
import io.github.xejohv.podclassicxzs.storage.SPManager
import io.github.xejohv.podclassicxzs.util.LocaleHelper
import io.github.xejohv.podclassicxzs.util.MediaStoreUtil
import io.github.xejohv.podclassicxzs.util.ThreadUtil
import io.github.xejohv.podclassicxzs.util.VolumeUtil
import io.github.xejohv.podclassicxzs.values.Colors
import io.github.xejohv.podclassicxzs.values.Strings
import io.github.xejohv.podclassicxzs.values.Values
import io.github.xejohv.podclassicxzs.view.MusicPlayerView
import io.github.xejohv.podclassicxzs.widget.Screen
import io.github.xejohv.podclassicxzs.widget.SlideController
import io.github.xejohv.podclassicxzs.widget.SlideController3rd
import java.util.*
import kotlin.system.exitProcess


@SuppressLint("StaticFieldLeak")
object Core {
    private lateinit var controller: View
    private lateinit var screen: Screen
    private lateinit var nightMode: View
    private var activity: MainActivity? = null

    fun bindActivity(
        controller: View,
        screen: Screen,
        darkMode: View,
        context: MainActivity
    ) {
        this.controller = controller
        this.screen = screen
        activity = context
        nightMode = darkMode
        setupTouchListener()
        // 三指上滑回主菜单（等同 HOME）
        screen.onTripleSwipeUp = { home() }
        // 只更新颜色和状态，不调用refresh()避免无限递归
        when (val ctrl = controller) {
            is SlideController -> {
                ctrl.colorController = Colors.controller
                ctrl.colorButton = Colors.button
                ctrl.invalidate()
            }
            is SlideController3rd -> {
                ctrl.colorController = Colors.controller
                ctrl.colorButton = Colors.button
                ctrl.invalidate()
            }
        }
        activity?.setColor(Colors.screen)
        wake()

    }

    private fun setupTouchListener() {
        when (val ctrl = controller) {
            is SlideController -> {
                ctrl.onTouchListener = object : SlideController.OnTouchListener {
                    override fun onEnterClick(): Boolean {
                        return screen.get().enter()
                    }

                    override fun onEnterLongClick(): Boolean {
                        return screen.get().enterLongClick()
                    }

                    override fun onTouch() {
                        wake()
                    }

                    override fun onSlide(slideVal: Int): Boolean {
                        return screen.get().slide(slideVal)
                    }

                    override fun onMenuClick(): Boolean {
                        return removeView()
                    }

                    override fun onMenuLongClick(): Boolean {
                        home()
                        return true
                    }

                    override fun onNextClick(): Boolean {
                        MediaPresenter.next()
                        return true
                    }

                    override fun onNextLongClick(): Boolean {
                        return MediaPresenter.forward()
                    }

                    override fun onPauseClick(): Boolean {
                        MediaPresenter.playPause()
                        return true
                    }


                    override fun onPrevClick(): Boolean {
                        MediaPresenter.prev()
                        return true
                    }

                    override fun onPrevLongClick(): Boolean {
                        return MediaPresenter.backward()
                    }
                }
            }
            is SlideController3rd -> {
                ctrl.onTouchListener = object : SlideController3rd.OnTouchListener {
                    override fun onEnterClick(): Boolean {
                        return screen.get().enter()
                    }

                    override fun onEnterLongClick(): Boolean {
                        return screen.get().enterLongClick()
                    }

                    override fun onTouch() {
                        wake()
                    }

                    override fun onSlide(slideVal: Int): Boolean {
                        return screen.get().slide(slideVal)
                    }

                    override fun onMenuClick(): Boolean {
                        return removeView()
                    }

                    override fun onMenuLongClick(): Boolean {
                        home()
                        return true
                    }

                    override fun onNextClick(): Boolean {
                        MediaPresenter.next()
                        return true
                    }

                    override fun onNextLongClick(): Boolean {
                        return MediaPresenter.forward()
                    }

                    override fun onPauseClick(): Boolean {
                        MediaPresenter.playPause()
                        return true
                    }


                    override fun onPrevClick(): Boolean {
                        MediaPresenter.prev()
                        return true
                    }

                    override fun onPrevLongClick(): Boolean {
                        return MediaPresenter.backward()
                    }
                }
            }
        }
    }


    var lock = false
        private set

    fun lock(lock: Boolean) {
        this.lock = lock
        when (val ctrl = controller) {
            is SlideController -> {
                ctrl.enable = !lock
            }
            is SlideController3rd -> {
                ctrl.enable = !lock
            }
        }
    }

    /** 游戏全屏模式：把下方控制盘折叠掉，让画面占满整块屏 */
    var gameExpanded = false
        private set

    fun setGameExpanded(expanded: Boolean) {
        // 即使状态没变也重新跑一次布局：折叠发生在游戏 View 刚加进来的时候，
        // 偶发的测量时序会把折叠结果冲掉，多刷一次保证稳定
        gameExpanded = expanded
        activity?.initView()
        activity?.window?.decorView?.post {
            activity?.initView()
        }
    }

    fun addView(view: ScreenView) {
        gameExpanded = false
        screen.add(view)
    }

    /** 回到应用时，让当前页重新读一遍外部状态（详见 ScreenView.onViewResume） */
    fun notifyResume() {
        try {
            screen.get().onViewResume()
        } catch (t: Throwable) {
        }
    }

    fun getView(): ScreenView {
        return screen.get()
    }

    fun removeView(): Boolean {
        gameExpanded = false
        val leavingPowerMenu = screen.get() is PowerMenuView
        val result = screen.remove()
        if (leavingPowerMenu) {
            // 退出自己的电源菜单时，把被盖在下面的系统电源菜单一并关掉
            GestureService.closeSystemPowerMenu()
        }
        activity?.initView()
        return result
    }

    fun home() {
        gameExpanded = false
        screen.home()
        activity?.initView()
    }

    fun refresh() {
        // 重新初始化视图，确保主题切换时SlideController的显示状态正确更新
        activity?.initView()
        // 更新控制器颜色和状态
        when (val ctrl = controller) {
            is SlideController -> {
                ctrl.colorController = Colors.controller
                ctrl.colorButton = Colors.button
                ctrl.invalidate()
            }
            is SlideController3rd -> {
                ctrl.colorController = Colors.controller
                ctrl.colorButton = Colors.button
                ctrl.invalidate()
            }
        }
        activity?.setColor(Colors.screen)

        // 通知当前视图配置已变化（横竖屏切换）
        screen.get().onConfigurationChanged()
    }

    fun setNightMode() {
        val id = SPManager.getInt(
            SPManager.NightMode.SP_NAME
        )
        val darkMode = if (SPManager.NightMode.AUTO.id == id) {
            val nightModeFlags =
                BaseApplication.context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            if (nightModeFlags == Configuration.UI_MODE_NIGHT_UNDEFINED || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                val calendar = Calendar.getInstance()
                val hour = calendar[Calendar.HOUR_OF_DAY]
                hour >= 23 || hour <= 5
            } else {
                nightModeFlags == Configuration.UI_MODE_NIGHT_YES
            }
        } else {
            SPManager.NightMode.ENABLE.id == id
        }
        nightMode.visibility = if (darkMode) View.VISIBLE else View.GONE
    }

    fun setLanguage() {
        // 之前这里把 createConfigurationContext() 的返回值丢了，等于什么都没配，
        // 所以语言选项点了也不生效。现在直接把 locale 落到应用上下文的 resources 上，
        // Activity 那层由 attachBaseContext() 负责（见 LocaleHelper）
        LocaleHelper.applyTo(BaseApplication.context)
        Strings.init()
    }

    private const val DELAY = 120000L  // 2 minutes

    private var timer = io.github.xejohv.podclassicxzs.util.Timer(DELAY) {
        if (MediaPresenter.isPlaying() && screen.currentView !is io.github.xejohv.podclassicxzs.view.MusicPlayerView && screen.currentView !is io.github.xejohv.podclassicxzs.view.MusicPlayerView3rd && activity != null) {
            ThreadUtil.runOnUiThread {
                if (SPManager.getInt(SPManager.Theme.SP_NAME) == SPManager.Theme.IPOD_3RD.id) {
                    addView(io.github.xejohv.podclassicxzs.view.MusicPlayerView3rd(activity!!))
                } else {
                    addView(io.github.xejohv.podclassicxzs.view.MusicPlayerView(activity!!))
                }
            }
        }
    }

    private var prevTimerSetTime = 0L
    fun wake() {
        if (!MediaPresenter.isPlaying()) {
            return
        }
        val currentMillis = System.currentTimeMillis()
        if (currentMillis - prevTimerSetTime <= 1000) {
            return
        }
        prevTimerSetTime = currentMillis
        timer.start(0L)
    }

    /** 供设置页原地重建界面（切换语言后不退出应用，直接重建 Activity） */
    fun getActivityInstance(): MainActivity? = activity

    fun reboot() {
        if (Values.LAUNCHER) {
            SPManager.save()
            exitProcess(0)
        } else {
            val context = BaseApplication.context
            activity?.finish()
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            BaseApplication.context.startActivity(intent)
            init()
        }
    }

    fun exit() {
        if (Values.LAUNCHER) {
            MediaPresenter.stop()
        } else {
            MediaPresenter.stop()
            SPManager.save()
            VolumeUtil.releaseSoundPool()
            activity?.stopService(Intent(activity, MediaService::class.java))
            activity?.finish()
            activity = null
            //exitProcess(0)
        }
    }

    /**
     * 恢复所有设置：偏好 + 阅读器外观 + 缓存，最后重启进程。
     * 只有进程重来，Colors 里那几个静态的主题/机身颜色才会按默认重算，
     * 否则界面上看不出任何变化（这正是以前“点了恢复但什么都没变”的原因）。
     */
    fun restoreAll(context: android.content.Context) {
        // 1. 播放器这一侧的偏好：主题、机身颜色、语言、显示、音效、播放、录音……
        SPManager.reset()
        // 2. 阅读器在 :reader 进程里另存一套偏好，纸色和日夜模式也算“设置”
        try {
            context.getSharedPreferences("PodClassicReader", android.content.Context.MODE_MULTI_PROCESS)
                .edit()
                .putInt("paper", 3)            // 3 = 夜间，与 PodReader.PAPER_DEFAULT 一致
                .putBoolean("dayMode", false)
                .commit()
        } catch (t: Throwable) {
            // 从没进过阅读器就没有这个文件，忽略
        }
        // 3. 曲库索引缓存一并清掉，恢复后按默认重新扫
        MusicListTable.album.clear()
        MusicListTable.folder.clear()
        MusicListTable.artist.clear()
        MusicTable.favourite.clear()
        // 4. 偏好先同步落盘，否则紧接着重启进程会把这次写入丢掉
        SPManager.save()
        // 5. 语言按默认重新套到资源层，Strings 立刻按新语言取字符串
        val app = BaseApplication.context
        LocaleHelper.applyAppCompat(app)
        LocaleHelper.applyTo(app)
        Strings.init()
        // 6. 重启（恢复设置本来就等于重启一次）
        reboot()
    }

    /**
     * 阅读器跑在独立的 :reader 进程里，给它一个能单独结束的入口。
     * 同一个 uid，找到 pid 直接 killProcess 就行，不用额外权限。
     */
    fun readerPid(): Int {
        val pkg = BaseApplication.context.packageName
        val target = pkg + ":reader"
        // 先扫 /proc：进程一死目录就没了，判断比系统的进程表更及时
        try {
            val dirs = java.io.File("/proc").listFiles()
            if (dirs != null) {
                for (f in dirs) {
                    val name = f.name
                    if (name.isEmpty() || !name[0].isDigit()) continue
                    try {
                        val cmd = java.io.File(f, "cmdline").readText()
                                .substringBefore('\u0000').trim()
                        if (cmd == target) return name.toInt()
                    } catch (t: Throwable) {
                    }
                }
            }
        } catch (t: Throwable) {
        }
        // 兜底再问系统进程表
        try {
            val am = BaseApplication.context.getSystemService(android.content.Context.ACTIVITY_SERVICE)
                    as android.app.ActivityManager
            val list = am.runningAppProcesses
            if (list != null) {
                for (info in list) {
                    if (info.processName == target) return info.pid
                }
            }
        } catch (t: Throwable) {
        }
        return -1
    }

    /** 刚结束过阅读器的时间点；系统的进程表有滞后，这段时间内直接按“没在运行”算 */
    private var readerKilledAt = 0L

    fun isReaderRunning(): Boolean {
        if (readerKilledAt > 0L &&
            android.os.SystemClock.elapsedRealtime() - readerKilledAt < 5000L
        ) {
            return false
        }
        return readerPid() > 0
    }

    fun killReader(): Boolean {
        val pid = readerPid()
        if (pid <= 0) return false
        return try {
            android.os.Process.killProcess(pid)
            readerKilledAt = android.os.SystemClock.elapsedRealtime()
            true
        } catch (t: Throwable) {
            false
        }
    }

    fun initUI() {
        MediaStoreUtil.init()
        MediaPresenter.init()
        //AppsView.init()
    }


    /** 对外广播的蓝牙名：别的设备搜到的是 iPod Classic，而不是 Xperia XZs */
    private const val BLUETOOTH_NAME = "iPod Classic"

    /**
     * 每次启动把蓝牙名改回来。系统恢复出厂 / 换机 / 被别处改过都能自愈，
     * 名字已经对了就不写，避免每次开机都重启蓝牙。
     */
    fun applyBluetoothName() {
        try {
            val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter() ?: return
            if (!adapter.isEnabled) return
            if (adapter.name == BLUETOOTH_NAME) return
            adapter.name = BLUETOOTH_NAME
        } catch (e: Exception) {
            android.util.Log.w("Core", "rename bluetooth failed", e)
        }
    }

    fun init() {
        setLanguage()
        applyBluetoothName()
        // 无论是否加载Activity都需要初始化
        //MediaStoreUtil.prepare()
        //AppWidget.updateRemoteViews(null)
    }
}