package org.ebookdroid.droids;

import com.foobnix.android.utils.LOG;
import com.foobnix.ext.CacheZipUtils;
import com.foobnix.ext.EpubExtractor;
import com.foobnix.ext.FooterNote;
import com.foobnix.ext.MobiExtract;
import com.foobnix.model.AppSP;
import com.foobnix.pdf.info.JsonHelper;
import com.foobnix.pdf.info.model.BookCSS;
import com.foobnix.sys.TempHolder;

import org.ebookdroid.core.codec.CodecDocument;
import org.ebookdroid.core.codec.AbstractCodecDocument;
import org.ebookdroid.droids.mupdf.codec.PdfContext;

import java.io.File;
import java.util.Map;

public class MobiContext extends PdfContext {

    String fileNameEpub = null;

    public int originalHashCode;
    File cacheFile;

    /**
     * 转换参数 + 拆壳版本一起进 hash：改了转换或拆壳逻辑，旧缓存自然失效，
     * 也保证「书库预转换」和「点开时转换」算出的是同一个缓存文件。
     */
    private static int hashOf(String fileName) {
        // 只用书名 + 拆壳版本。以前这里还拼了断词/BBcode 这些运行时偏好，
        // 而书库预转换跑在偏好加载之前、点开时跑在加载之后，同一本书算出两个 hash，
        // 预转换白转、缓存也互相看不见。断词结果不影响可读，只影响排版细节。
        return (fileName + "|" + MobiSvgUnwrap.VERSION).hashCode();
    }

    private static File cacheFileOf(int hash) {
        return new File(CacheZipUtils.CACHE_BOOK_DIR, hash + "" + hash + ".epub");
    }

    /** 这本书转换后的缓存文件在哪；还没有就说还没有，不触发转换。 */
    public static File cacheFileFor(String fileName) {
        return cacheFileOf(hashOf(fileName));
    }

    /** 这个格式需要先转成 epub 才能读，书库可以提前替它转好。 */
    public static boolean needsConvert(String fileName) {
        if (fileName == null) return false;
        String p = fileName.toLowerCase(java.util.Locale.ROOT);
        return p.endsWith(".mobi") || p.endsWith(".azw") || p.endsWith(".azw3") || p.endsWith(".prc");
    }

    @Override
    public File getCacheFileName(String fileName) {
        originalHashCode = hashOf(fileName);
        cacheFile = cacheFileOf(originalHashCode);
        return cacheFile;
    }

    /**
     * Writes the book as {@code outName}.epub in {@code outDir}.
     */
    protected FooterNote convert(String fileName, String outDir, String outName) throws Exception {
        return MobiExtract.extract(fileName, outDir, outName);
    }

    private static void touch(File f) {
        try {
            if (f.exists()) f.setLastModified(System.currentTimeMillis());
            else f.createNewFile();
        } catch (Throwable ignored) {
        }
    }

    private static void copyFile(File from, File to) throws Exception {
        java.io.FileInputStream in = new java.io.FileInputStream(from);
        try {
            java.io.FileOutputStream out = new java.io.FileOutputStream(to);
            try {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
    }

    /**
     * 转换（+可选的断词/拆 svg 壳）并把结果落到缓存位，返回可读的 epub；失败返回 null。
     *
     * 书库的预转换和点开时的转换都走这里，任何一次成功之后，下次打开就是秒开。
     */
    public static File ensureCache(String fileName) {
        File target = cacheFileFor(fileName);
        File ok = new File(target.getPath() + ".ok");
        // 半截缓存（转换被打断、进程被杀）比没有更糟：打开是一本空书。坏的就扔掉重来。
        // 每次打开都去开十几 MB 的压缩包校验要一秒多，所以校验通过就留一个标记：
        // 标记不比缓存旧，说明这份缓存是完整写过的，直接信任
        if (target.isFile()) {
            // sdcard 的时间戳只有 2 秒精度，同一次转换写出的两个文件常常落到同一格，
            // 标记甚至会“比缓存早”一点，所以留 5 秒宽容，别为了这点误差白扫一遍压缩包
            if (ok.isFile() && ok.lastModified() + 5000L >= target.lastModified()) {
                LOG.d("MobiContext", "marker hit", target.getName());
                return target;
            }
            if (MobiSvgUnwrap.isValid(target)) {
                touch(ok);
                return target;
            }
            LOG.d("Context", "MobiContext drop broken cache", target);
            ok.delete();
            target.delete();
        }

        File tmp = null;
        try {
            int hash = hashOf(fileName);
            LOG.d("MobiContext", "ensure", fileName, target, BookCSS.get().isAutoHypens, hash);
            // 原生转换先写一个临时名，绝不直接写缓存位：
            // 以前这里的名字和缓存名撞在一起，copyFile 会把源文件当目标截成 0 字节
            String outName = "pod" + hash;

            FooterNote extract = MobiExtract.extract(fileName, CacheZipUtils.CACHE_BOOK_DIR.getPath(), outName);
            if (extract.path == null || extract.path.isEmpty() || !new File(extract.path).isFile()) {
                LOG.d("Context", "MobiContext nothing converted", fileName, extract.path);
                return null;
            }
            tmp = new File(extract.path);
            LOG.d("Context", "MobiContext converted", tmp, tmp.length());

            // 中文书断词没意义，直接落缓存；其它语言按原来的断词重打包
            if (BookCSS.get().isAutoHypens && !MobiSvgUnwrap.looksChinese(tmp)) {
                EpubExtractor.proccessHypens(tmp.getPath(), target.getPath(), null);
            } else {
                copyFile(tmp, target);
            }

            // Kindle 的插图常被包在一层 svg 里，WebView 会把它画成模糊缩略图，
            // 转换完就地拆壳，让正文直接引用位图
            MobiSvgUnwrap.fix(target);

            if (TempHolder.get().loadingCancelled.get() && !target.isFile()) {
                return null;
            }
            if (!MobiSvgUnwrap.isValid(target)) {
                target.delete();
                return null;
            }
            LOG.d("Context", "MobiContext cache ready", target, target.length());
            touch(ok);
            return target;
        } catch (Throwable e) {
            LOG.e(e);
            if (target.isFile() && !MobiSvgUnwrap.isValid(target)) target.delete();
            return null;
        } finally {
            if (tmp != null && !tmp.equals(target) && tmp.isFile()) {
                tmp.delete();
            }
        }
    }

    @Override
    public CodecDocument openDocumentInner(String fileName, String password) {

        LOG.d("Context", "MobiContext", fileName);

        // 缓存（或现场转换出来的 epub）就是这本书，和日志开关无关
        long t0 = android.os.SystemClock.elapsedRealtime();
        File cache = ensureCache(fileName);
        if (cache == null) {
            LOG.d("Context", "MobiContext nothing converted", fileName, fileNameEpub);
            return null;
        }
        fileNameEpub = cache.getPath();
        LOG.d("MobiContext", "timer cache", android.os.SystemClock.elapsedRealtime() - t0, cache.length());

        long t1 = android.os.SystemClock.elapsedRealtime();
        final AbstractCodecDocument muPdfDocument = openReflowable(fileNameEpub, password);
        LOG.d("MobiContext", "timer reflow", android.os.SystemClock.elapsedRealtime() - t1);
        if (muPdfDocument == null) {
            // No engine could open it; openReflowable has logged why
            return null;
        }

        final File jsonFile = new File(cacheFile + ".json");
        if (jsonFile.isFile()) {
            muPdfDocument.setFootNotes(JsonHelper.fileToMap(jsonFile));
            LOG.d("Load notes from file", jsonFile);
        } else {

            new Thread("@T mobi set footernotes") {
                @Override
                public void run() {
                    Map<String, String> notes = null;
                    try {
                        notes = EpubExtractor.get().getFooterNotes(fileNameEpub);
                        LOG.d("new file name", fileNameEpub);
                        muPdfDocument.setFootNotes(notes);

                        // a cancelled extraction is empty or partial, it must not stay in the cache
                        if (!TempHolder.get().loadingCancelled.get()) {
                            JsonHelper.mapToFile(jsonFile, notes);
                            LOG.d("save notes to file", jsonFile);
                            removeTempFilesIfCancel();
                        }

                    } catch (OutOfMemoryError e) {
                        System.gc();
                        notes = null;
                        LOG.e(e);
                    } catch (Exception e) {
                        notes = null;
                        LOG.e(e);
                    }
                }
            }.start();
        }


        return muPdfDocument;
    }

}
