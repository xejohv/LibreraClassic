package io.github.xejohv.podclassicxzs.util

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

    /** 打开书库；path 非空时直接打开该书 */
    fun open(context: Context, path: String? = null): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
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