package com.foobnix.pdf.info;

import java.util.Locale;

/**
 * 版式书判定：PDF、DjVu、XPS 与漫画是把一页纸当图片画的，页面本身已经是成品纸的颜色，
 * 夜里也应当原样显示；只有可重排的 EPUB / MOBI / AZW3 / TXT 才跟随用户选的白天或夜间。
 *
 * 这个判断被阅读页外壳（PodReader）和位图缓存层（PageTreeNode）共同使用：前者的主题设置
 * 会被皮肤在每个 Activity 醒来时重刷，后者的反色标记决定一张页面到底要不要取反，两边必须
 * 认同一个口径，否则会出现“菜单写白天、纸面仍是黑底青字”。
 */
public final class LayoutBooks {

    private LayoutBooks() {
    }

    public static boolean isLayout(String path) {
        if (path == null) {
            return false;
        }
        String p = path.toLowerCase(Locale.ROOT);
        return p.endsWith(".pdf") || p.endsWith(".djvu") || p.endsWith(".xps")
                || p.endsWith(".cbz") || p.endsWith(".cbr") || p.endsWith(".zip")
                || p.endsWith(".jpg") || p.endsWith(".jpeg") || p.endsWith(".png");
    }
}
