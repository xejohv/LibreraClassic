package io.github.xejohv.podclassicxzs.util

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File

/**
 * 阅读入口桥。
 *
 * 合并后阅读器与桌面在同一个安装包里，阅读组件全部挂在 :reader 进程：
 * 这里只发一个跨进程 Intent，启动器进程不会加载任何一个阅读器类，
 * 桌面的秒开与稳定性不受阅读器影响。
 */
object ReaderBridge {
    private const val READER_ENTRY = "io.github.xejohv.classic.ClassicReaderActivity"
    private const val BOOK_PAGE = "com.foobnix.pdf.search.activity.HorizontalViewActivity"

    /** 打开书库；path 非空时直接打开该书 */
    fun open(context: Context, path: String? = null): Boolean {
        val intent = Intent().apply {
            component = ComponentName(context.packageName, READER_ENTRY)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (path != null) {
            intent.action = Intent.ACTION_VIEW
            intent.setDataAndType(Uri.fromFile(File(path)), mimeOf(path))
            intent.putExtra("EXTRA_FILE", path)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 桌面「阅读」入口：书页还在后台就直接把阅读任务提回前台（页码原样保留），
     * 没有书页才打开书库。与实体快门全按的行为保持一致。
     */
    fun resumeOrOpen(context: Context): Boolean {
        val sp = context.getSharedPreferences("PodSwitch", Context.MODE_MULTI_PROCESS)
        if (sp.getBoolean("haveBook", false)) {
            val taskId = sp.getInt("taskId", -1)
            if (taskId > 0) {
                try {
                    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                    // 任务被系统回收后 moveTaskToFront 不抛异常、只是静默失败，
                    // 旧代码当成成功直接 return，于是“点阅读没反应”。先确认任务还活着。
                    val alive = try {
                        am.getRunningTasks(20).any { it.id == taskId }
                    } catch (t: Throwable) {
                        true
                    }
                    if (alive) {
                        am.moveTaskToFront(taskId, 0)
                        return true
                    }
                } catch (t: Throwable) {
                }
            }
            val path = sp.getString("book", null)
            if (path != null && File(path).exists()) {
                try {
                    val intent = Intent().apply {
                        component = ComponentName(context.packageName, BOOK_PAGE)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        action = Intent.ACTION_VIEW
                        setDataAndType(Uri.fromFile(File(path)), mimeOf(path))
                        putExtra("EXTRA_FILE", path)
                    }
                    context.startActivity(intent)
                    return true
                } catch (t: Throwable) {
                }
            }
        }
        return open(context)
    }

    fun mimeOf(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "epub" -> "application/epub+zip"
        "mobi" -> "application/x-mobipocket-ebook"
        "azw3" -> "application/vnd.amazon.ebook"
        "fb2" -> "application/x-fictionbook+xml"
        "cbz" -> "application/vnd.comicbook+zip"
        "txt" -> "text/plain"
        else -> "*/*"
    }
}