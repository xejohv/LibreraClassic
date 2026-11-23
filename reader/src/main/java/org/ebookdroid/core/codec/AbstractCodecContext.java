package org.ebookdroid.core.codec;

import android.graphics.Bitmap;

import com.foobnix.android.utils.LOG;
import com.foobnix.ext.CacheZipUtils;
import com.foobnix.ext.CacheZipUtils.CacheDir;
import com.foobnix.model.AppSP;
import com.foobnix.pdf.info.AppsConfig;
import com.foobnix.pdf.info.ExtUtils;
import com.foobnix.sys.TempHolder;

import org.ebookdroid.BookType;
import org.ebookdroid.droids.mupdf.codec.exceptions.MuPdfPasswordException;
import org.ebookdroid.droids.mupdf.codec.exceptions.MuPdfPasswordRequiredException;
import org.ebookdroid.ui.viewer.VerticalViewActivity;

import java.io.File;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicLong;

public abstract class AbstractCodecContext implements CodecContext {

    private static final AtomicLong SEQ = new AtomicLong();

    private static Integer densityDPI;

    private long contextHandle;

    /**
     * Constructor.
     */
    protected AbstractCodecContext() {
        this(SEQ.incrementAndGet());
    }

    public abstract CodecDocument openDocumentInner(String fileName, String password);

    public CodecDocument openDocumentInnerCanceled(String fileName, String password) {
        long t = System.currentTimeMillis();
        CodecDocument openDocument = openDocumentInner(fileName, password);
        LOG.d("openDocumentInner-time", (float) (System.currentTimeMillis() - t) / 1000, fileName);
        LOG.d("removeTempFiles1", TempHolder.get().loadingCancelled.get());
//        if (TempHolder.get().loadingCancelled.get()) {
//            removeTempFiles();
//            return null;
//        }


        return openDocument;
    }

    public void removeTempFilesIfCancel() {
        LOG.d("removeTempFiles2", "remove temp files", TempHolder.get().loadingCancelled.get());
        if (TempHolder.get().loadingCancelled.get()) {
            //recycle();
            try {
                Thread.sleep(1000);
                CacheZipUtils.removeFiles(CacheZipUtils.CACHE_BOOK_DIR.listFiles());
                CacheZipUtils.removeFiles(CacheZipUtils.CACHE_TEMP.listFiles());
            }catch (Exception e){
                LOG.w(e);
            }
        }
    }

    public static long getFileNameSalt(String path) {
        long hashCode = 0;
        try {
            File file = new File(path);
            hashCode = file.length() + file.lastModified();
            LOG.d("getFileNameSalt", path, file.length(), file.lastModified());
        } catch (Exception e) {
            LOG.e(e);
        }
        return hashCode;
    }

    @Override
    public CodecDocument openDocument(String fileNameOriginal, String password) {
        LOG.d("Open-Document", fileNameOriginal);
        // TempHolder.loadingCancelled = false;
        if (ExtUtils.isZip(fileNameOriginal)) {
            LOG.d("Open-Document ZIP", fileNameOriginal);
            return openDocumentInnerCanceled(fileNameOriginal, password);
        }

        LOG.d("Open-Document 2 LANG:", AppSP.get().hypenLang, fileNameOriginal);

        File cacheFileName = getCacheFileName(fileNameOriginal + getFileNameSalt(fileNameOriginal));
        // 原来这里把缓存目录里「除本书以外」的所有东西全删了：一开新书，上一本书
        // 预转换好的 epb 缓存连 .ok 标记一起被清掉，下次再开又得从头转一遍，
        // 于是每本书都是「第二次打开反而更慢」。缓存名本身就带这本书的键，
        // 同名旧缓存不存在长期堆积问题，这里只清本书自己的半截文件。
        if (!BookType.ODT.is(fileNameOriginal) && cacheFileName != null) {
            File part = new File(cacheFileName.getPath() + ".part");
            if (part.isFile()) part.delete();
            File tmpDir = new File(CacheZipUtils.CACHE_BOOK_DIR, "pod" + Math.abs(cacheFileName.getName().hashCode()) + ".epub");
            if (tmpDir.isFile()) tmpDir.delete();
        }

        if (cacheFileName != null && cacheFileName.isFile()) {
            LOG.d("Open-Document from cache", fileNameOriginal);
            return openDocumentInnerCanceled(fileNameOriginal, password);
        }

        CacheZipUtils.cacheLock2.lock();
        CacheZipUtils.createAllCacheDirs();
        try {
            String fileName = CacheZipUtils.extracIfNeed(fileNameOriginal, CacheDir.ZipApp).unZipPath;
            LOG.d("Open-Document extract", fileName);
            if (!ExtUtils.isValidFile(fileName)) {
                LOG.d( "isValidFile",fileName);
                return null;
            }
            try {
                return openDocumentInnerCanceled(fileName, password);
            } catch (MuPdfPasswordException e) {
                throw new MuPdfPasswordRequiredException();
            } catch (Throwable e) {
                LOG.w(e);
                return null;
            }
        } finally {
            CacheZipUtils.cacheLock2.unlock();
        }

    }

    public File getCacheFileName(String fileNameOriginal) {
        return null;
    }

    /**
     * Constructor.
     *
     * @param contextHandle contect handler
     */
    protected AbstractCodecContext(final long contextHandle) {
        this.contextHandle = contextHandle;
    }

    @Override
    protected final void finalize() throws Throwable {
        // recycle();
        super.finalize();
    }

    /**
     * {@inheritDoc}
     *
     * @see org.ebookdroid.core.codec.CodecContext#recycle()
     */
    @Override
    public final void recycle() {
        TempHolder.lock.lock();
        try {
            if (!isRecycled()) {
                freeContext();
                contextHandle = 0;
            }
        } finally {
            TempHolder.lock.unlock();
        }
    }

    protected void freeContext() {
    }

    /**
     * {@inheritDoc}
     *
     * @see org.ebookdroid.core.codec.CodecContext#isRecycled()
     */
    @Override
    public final boolean isRecycled() {
        return contextHandle == 0;
    }

    /**
     * {@inheritDoc}
     *
     * @see org.ebookdroid.core.codec.CodecContext#getContextHandle()
     */
    @Override
    public final long getContextHandle() {
        return contextHandle;
    }

    @Override
    public boolean isPageSizeCacheable() {
        return true;
    }

    @Override
    public boolean isParallelPageAccessAvailable() {
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * @see org.ebookdroid.core.codec.CodecContext#getBitmapConfig()
     */
    @Override
    public Bitmap.Config getBitmapConfig() {
        return AppsConfig.CURRENT_BITMAP_ARGB;
    }

    public static int getSizeInPixels(final float pdfHeight, float dpi) {
        if (dpi == 0) {
            // Archos fix
            dpi = getDensityDPI();
        }
        if (dpi < 72) { // Density lover then 72 is to small
            dpi = 72; // Set default density to 72
        }
        return (int) (pdfHeight * dpi / 72);
    }

    private static int getDensityDPI() {
        if (densityDPI == null) {
            try {
                final Field f = VerticalViewActivity.DM.getClass().getDeclaredField("densityDpi");
                densityDPI = ((Integer) f.get(VerticalViewActivity.DM));
            } catch (final Throwable ex) {
                densityDPI = Integer.valueOf(120);
            }
        }
        return densityDPI.intValue();
    }
}
