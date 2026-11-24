package io.github.xejohv.podclassicxzs.util

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import io.github.xejohv.podclassicxzs.base.Core
import java.io.File
import java.util.Locale

/**
 * 资源监视器读的那些数。
 *
 * 全部现读现算，不缓存：列表每次重绑都会调一遍，所以看到的永远是当前状态。
 */
object SysInfo {

    private fun am(): android.app.ActivityManager =
        io.github.xejohv.podclassicxzs.base.BaseApplication.context
            .getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager

    /** 紧凑容量：字体缩放 1.3 时右侧栏只放得下三四个字符，"2.4 G"/"42 M" */
    private fun short(bytes: Long): String {
        if (bytes <= 0L) return "-"
        val gb = 1024.0 * 1024.0 * 1024.0
        val mb = 1024.0 * 1024.0
        return if (bytes >= gb) String.format(Locale.US, "%.1f G", bytes / gb)
        else String.format(Locale.US, "%.0f M", bytes / mb)
    }

    /** 可用内存（系统口径 availMem） */
    fun memAvailable(): String {
        return try {
            val info = android.app.ActivityManager.MemoryInfo()
            am().getMemoryInfo(info)
            short(info.availMem)
        } catch (t: Throwable) {
            "-"
        }
    }

    /** 内存总量 */
    fun memTotal(): String {
        return try {
            val info = android.app.ActivityManager.MemoryInfo()
            am().getMemoryInfo(info)
            short(info.totalMem)
        } catch (t: Throwable) {
            "-"
        }
    }

    /** 已用百分比：总量减去可用，再除总量 */
    fun memUsage(): String {
        return try {
            val info = android.app.ActivityManager.MemoryInfo()
            am().getMemoryInfo(info)
            if (info.totalMem <= 0L) "-"
            else ((info.totalMem - info.availMem) * 100L / info.totalMem).toString() + "%"
        } catch (t: Throwable) {
            "-"
        }
    }

    /** 本应用所有进程（主进程 + 阅读器）的 PSS 合计 */
    fun appMemory(): String {
        return try {
            val pids = ArrayList<Int>()
            pids.add(android.os.Process.myPid())
            val reader = Core.readerPid()
            if (reader > 0) pids.add(reader)
            var pssKb = 0L
            val list = am().getProcessMemoryInfo(pids.toIntArray())
            if (list != null) {
                for (i in list) pssKb += i.totalPss.toLong()
            }
            short(pssKb * 1024L)
        } catch (t: Throwable) {
            "-"
        }
    }

    /** 应用进程数：主进程算 1，阅读器在跑再加 1 */
    fun processCount(): String {
        return if (Core.isReaderRunning()) "2" else "1"
    }

    /**
     * 本应用的 CPU 占用。
     *
     * 这台机器的 SELinux 把 /proc/loadavg 和 /proc/stat 都挡了，拿不到整机负载，
     * 但 /proc/self/stat 和本应用其它进程的 stat 是能读的：
     * 每进程 utime+stime 是累计 tick（HZ=100），隔一小段时间采两次求差，
     * 差值除以这段实际时长就是占用率。
     */
    fun cpuLoad(): String {
        return try {
            val pids = ArrayList<Int>()
            pids.add(android.os.Process.myPid())
            val reader = Core.readerPid()
            if (reader > 0) pids.add(reader)
            val a = ticks(pids) ?: return "-"
            val t0 = android.os.SystemClock.elapsedRealtime()
            // 窗口取 700ms：一个 tick 是 10ms，窗口太短时空闲的进程会一直显示 0，
            // 分不清是"真没占用"还是"没测出来"
            Thread.sleep(700)
            val t1 = android.os.SystemClock.elapsedRealtime()
            val b = ticks(pids) ?: return "-"
            val deltaTicks = b - a
            val elapsedMs = (t1 - t0).coerceAtLeast(1L)
            // 以 0.1% 为单位算：tick 数 × 10ms ÷ 窗口时长
            val permille = deltaTicks * 1000L * 10L / elapsedMs
            val clamped = permille.coerceIn(0L, 1000L)
            String.format(Locale.US, "%.1f%%", clamped / 10.0)
        } catch (t: Throwable) {
            "-"
        }
    }

    /** 若干进程的 utime+stime 合计（tick） */
    private fun ticks(pids: List<Int>): Long? {
        var sum = 0L
        var ok = false
        for (pid in pids) {
            val v = pidTicks(pid) ?: continue
            sum += v
            ok = true
        }
        return if (ok) sum else null
    }

    private fun pidTicks(pid: Int): Long? {
        return try {
            val line = File("/proc/" + pid + "/stat").readText()
            // comm 里可能有空格和括号，从最后一个右括号往后切
            val after = line.substring(line.lastIndexOf(')') + 2).trim()
            val parts = after.split(Regex("\\s+"))
            // state 起算：utime 是第 12 项、stime 第 13 项（0 基下标 11、12）
            val utime = parts[11].toLong()
            val stime = parts[12].toLong()
            utime + stime
        } catch (t: Throwable) {
            null
        }
    }

    /** 电池电量百分比 */
    fun batteryLevel(): String {
        return try {
            val i = io.github.xejohv.podclassicxzs.base.BaseApplication.context
                .registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) (level * 100 / scale).toString() + "%" else "-"
        } catch (t: Throwable) {
            "-"
        }
    }

    /** 电池温度（系统给的是 0.1 摄氏度） */
    fun batteryTemperature(): String {
        return try {
            val i = io.github.xejohv.podclassicxzs.base.BaseApplication.context
                .registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val raw = i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
            if (raw == Int.MIN_VALUE || raw == 0) "-"
            else String.format(Locale.US, "%.1f℃", raw / 10.0)
        } catch (t: Throwable) {
            "-"
        }
    }
}
