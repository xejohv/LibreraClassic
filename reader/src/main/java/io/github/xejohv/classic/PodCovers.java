package io.github.xejohv.classic;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Covers for the shelf, cached so the shelf never waits on a book.
 *
 * A row is drawn immediately with the spine block. Artwork is pulled out of the
 * book on a worker thread the first time that book is seen, and stored as a
 * small JPEG under the cache directory; every later visit is a plain file read.
 * Nothing here parses a book on the main thread, and nothing here is touched
 * until a shelf row asks for its cover.
 */
final class PodCovers {

    /** Longest edge of the stored thumbnail, in pixels. */
    private static final int THUMB = 320;
    /** How many thumbnails the cache keeps before the oldest are dropped. */
    private static final int KEEP = 400;
    /** A cover larger than this is not worth decoding. */
    private static final long MAX_ENTRY = 12L * 1024 * 1024;

    private static ExecutorService pool;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private PodCovers() {
    }

    private static synchronized ExecutorService pool() {
        if (pool == null) {
            pool = Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "pod-cover");
                t.setDaemon(true);
                return t;
            });
        }
        return pool;
    }

    private static File dir(Context c) {
        File d = new File(c.getCacheDir(), "covers");
        if (!d.exists()) {
            d.mkdirs();
        }
        return d;
    }

    /** Path, size and mtime make the key: a replaced book misses its old thumb. */
    private static File thumb(Context c, File book) {
        String key = book.getAbsolutePath() + "|" + book.length() + "|" + book.lastModified();
        return new File(dir(c), md5(key) + ".jpg");
    }

    /**
     * Fill the cover box of one shelf row. Returns at once: the caller keeps the
     * spine block on screen until the artwork is ready, then it fades in.
     */
    static void bind(final Context c, final File book, final ImageView art, final TextView initial) {
        final Context app = c.getApplicationContext();
        final File out = thumb(app, book);
        final String tag = out.getName();
        art.setTag(tag);
        final boolean cached = out.exists();
        pool().execute(() -> {
            if (!cached) {
                generate(book, out);
            }
            deliver(out, art, initial, tag);
        });
    }

    private static void deliver(final File out, final ImageView art, final TextView initial, final String tag) {
        final Bitmap bm = decode(out);
        if (bm == null) {
            // a half written or unreadable thumb: drop it so the next visit retries
            if (out.exists()) {
                out.delete();
            }
            return;
        }
        MAIN.post(() -> {
            if (!tag.equals(art.getTag())) {
                bm.recycle();
                return;
            }
            art.setImageBitmap(bm);
            art.setAlpha(0f);
            art.animate().alpha(1f).setDuration(160).start();
            if (initial != null) {
                initial.setVisibility(View.GONE);
            }
        });
    }

    /** Keep the cache from growing without a bound. Runs off the main thread. */
    static void prune(final Context c) {
        final Context app = c.getApplicationContext();
        pool().execute(() -> {
            try {
                File[] all = dir(app).listFiles();
                if (all == null || all.length <= KEEP) {
                    return;
                }
                Arrays.sort(all, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
                for (int i = 0; i < all.length - KEEP; i++) {
                    all[i].delete();
                }
            } catch (Throwable ignored) {
            }
        });
    }

    // ---------------------------------------------------------------- artwork

    private static void generate(File book, File out) {
        Bitmap art = null;
        File tmp = null;
        try {
            String e = ext(book);
            if ("epub".equals(e) || "cbz".equals(e)) {
                art = fromZip(book);
            } else if ("pdf".equals(e)) {
                art = fromPdf(book);
            } else if ("mobi".equals(e) || "azw".equals(e) || "azw3".equals(e) || "prc".equals(e)) {
                art = fromMobi(book);
            }
            if (art == null) {
                return;
            }
            Bitmap scaled = shrink(art);
            if (scaled != art) {
                art.recycle();
            }
            tmp = new File(out.getAbsolutePath() + ".tmp");
            FileOutputStream fos = new FileOutputStream(tmp);
            scaled.compress(Bitmap.CompressFormat.JPEG, 82, fos);
            fos.flush();
            fos.close();
            scaled.recycle();
            if (!tmp.renameTo(out)) {
                tmp.delete();
            }
        } catch (Throwable ignored) {
            if (tmp != null) {
                tmp.delete();
            }
        }
    }

    private static Bitmap shrink(Bitmap bm) {
        int w = bm.getWidth();
        int h = bm.getHeight();
        int longEdge = Math.max(w, h);
        if (longEdge <= THUMB) {
            return bm;
        }
        float k = (float) THUMB / longEdge;
        int nw = Math.max(1, Math.round(w * k));
        int nh = Math.max(1, Math.round(h * k));
        return Bitmap.createScaledBitmap(bm, nw, nh, true);
    }

    private static Bitmap fromZip(File book) {
        ZipFile zf = null;
        InputStream in = null;
        try {
            zf = new ZipFile(book);
            String entry = findCoverEntry(zf);
            if (entry == null) {
                return null;
            }
            ZipEntry ze = zf.getEntry(entry);
            if (ze == null || ze.getSize() > MAX_ENTRY) {
                return null;
            }
            in = zf.getInputStream(ze);
            return BitmapFactory.decodeStream(in);
        } catch (Throwable ignored) {
            return null;
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (Throwable ignored) {
            }
            try {
                if (zf != null) {
                    zf.close();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * The cover entry of an EPUB/CBZ, in the order the spec allows: the id named
     * by the package metadata, then the item flagged as the cover image, then a
     * file whose name says so.
     */
    private static String findCoverEntry(ZipFile zf) {
        String opf = null;
        String container = read(zf, "META-INF/container.xml");
        if (container != null) {
            String hit = first(container, "full-path\\s*=\\s*\"([^\"]+)\"");
            if (hit != null) {
                opf = hit;
            }
        }
        String base = "";
        if (opf != null) {
            int cut = opf.replace('\\', '/').lastIndexOf('/');
            if (cut >= 0) {
                base = opf.substring(0, cut + 1);
            }
            String pkg = read(zf, opf);
            if (pkg != null) {
                String id = first(pkg, "name\\s*=\\s*\"cover\"[^>]*content\\s*=\\s*\"([^\"]+)\"");
                if (id == null) {
                    id = first(pkg, "content\\s*=\\s*\"([^\"]+)\"[^>]*name\\s*=\\s*\"cover\"");
                }
                if (id != null) {
                    String href = first(pkg, "id\\s*=\\s*\"" + quote(id) + "\"[^>]*href\\s*=\\s*\"([^\"]+)\"");
                    if (href == null) {
                        href = first(pkg, "href\\s*=\\s*\"([^\"]+)\"[^>]*id\\s*=\\s*\"" + quote(id) + "\"");
                    }
                    String hit = resolve(zf, base + unescape(href));
                    if (hit != null) {
                        return hit;
                    }
                }
                String flagged = first(pkg, "properties\\s*=\\s*\"[^\"]*cover-image[^\"]*\"[^>]*href\\s*=\\s*\"([^\"]+)\"");
                if (flagged == null) {
                    flagged = first(pkg, "href\\s*=\\s*\"([^\"]+)\"[^>]*properties\\s*=\\s*\"[^\"]*cover-image[^\"]*\"");
                }
                if (flagged != null) {
                    String hit = resolve(zf, base + unescape(flagged));
                    if (hit != null) {
                        return hit;
                    }
                }
            }
        }
        // last resort: any image that calls itself a cover, shortest name first
        String best = null;
        for (java.util.Enumeration<? extends ZipEntry> en = zf.entries(); en.hasMoreElements(); ) {
            String name = en.nextElement().getName().replace('\\', '/');
            String low = name.toLowerCase(java.util.Locale.ROOT);
            if (!low.contains("cover")) {
                continue;
            }
            if (!(low.endsWith(".jpg") || low.endsWith(".jpeg") || low.endsWith(".png") || low.endsWith(".webp"))) {
                continue;
            }
            if (best == null || name.length() < best.length()) {
                best = name;
            }
        }
        return best;
    }

    private static String resolve(ZipFile zf, String name) {
        name = name.replace('\\', '/');
        ZipEntry ze = zf.getEntry(name);
        if (ze != null) {
            return name;
        }
        for (java.util.Enumeration<? extends ZipEntry> en = zf.entries(); en.hasMoreElements(); ) {
            String n = en.nextElement().getName();
            if (n.equalsIgnoreCase(name)) {
                return n;
            }
        }
        return null;
    }

    private static Bitmap fromPdf(File book) {
        ParcelFileDescriptor fd = null;
        android.graphics.pdf.PdfRenderer renderer = null;
        try {
            fd = ParcelFileDescriptor.open(book, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new android.graphics.pdf.PdfRenderer(fd);
            if (renderer.getPageCount() <= 0) {
                return null;
            }
            android.graphics.pdf.PdfRenderer.Page page = renderer.openPage(0);
            int pw = page.getWidth();
            int ph = page.getHeight();
            if (pw <= 0 || ph <= 0) {
                page.close();
                return null;
            }
            int tw = THUMB;
            int th = Math.max(1, (int) ((float) ph / pw * tw));
            Bitmap bm = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888);
            bm.eraseColor(android.graphics.Color.WHITE);
            page.render(bm, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            page.close();
            return bm;
        } catch (Throwable ignored) {
            return null;
        } finally {
            try {
                if (renderer != null) {
                    renderer.close();
                }
            } catch (Throwable ignored) {
            }
            try {
                if (fd != null) {
                    fd.close();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * MOBI / AZW / AZW3 的封面：这类书是 PalmDB 容器，正文和图片各占一条记录。
     * MOBI 头给出「首图记录号」，EXTH 元数据里的 201 项给出封面是第几张图，
     * 两者相加就是封面所在的记录。没有 201 项的老书，退一步在图片记录里挑竖版最大的。
     */
    private static Bitmap fromMobi(File book) {
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(book, "r");
            raf.seek(60);
            String type = ascii(raf, 4);
            String creator = ascii(raf, 4);
            // BOOK/MOBI 是标准写法，TEXt/REAd 是旧版 PalmDOC，都要认
            if (!("BOOK".equals(type) || "TEXt".equals(type))) {
                return null;
            }
            if (!("MOBI".equals(creator) || "REAd".equals(creator))) {
                return null;
            }
            raf.seek(76);
            int count = beShort(raf);
            if (count < 3) {
                return null;
            }
            long[] off = new long[count + 1];
            raf.seek(78);
            for (int i = 0; i < count; i++) {
                off[i] = beInt(raf);
                raf.skipBytes(4);   // attributes + unique id
            }
            off[count] = book.length();

            int firstImage = 1;
            long mobiStart = off[0] + 16;
            long exthAt = -1;
            try {
                raf.seek(mobiStart);
                if ("MOBI".equals(ascii(raf, 4))) {
                    int headerLen = beInt(raf);
                    raf.seek(mobiStart + 0x5C);
                    int f = beInt(raf);
                    if (f > 0 && f < count) {
                        firstImage = f;
                    }
                    // EXTH 紧跟在 MOBI 头之后，不额外补 4 字节
                    exthAt = mobiStart + headerLen;
                }
            } catch (Throwable ignored) {
            }

            int coverOff = exthCoverOffset(raf, exthAt);
            if (coverOff > 0) {
                int rec = firstImage + coverOff;
                if (rec > 0 && rec < count) {
                    Bitmap byExth = recordImage(raf, off, rec);
                    if (byExth != null) {
                        return byExth;
                    }
                }
            }
            return bestImage(raf, off, count, firstImage);
        } catch (Throwable ignored) {
            return null;
        } finally {
            try {
                if (raf != null) {
                    raf.close();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    /** EXTH 里的 201 项：封面图相对首图的序号；顺带认 202（缩略图）做兜底。 */
    private static int exthCoverOffset(RandomAccessFile raf, long exthAt) {
        if (exthAt <= 0) {
            return 0;
        }
        try {
            raf.seek(exthAt);
            if (!"EXTH".equals(ascii(raf, 4))) {
                return 0;
            }
            raf.skipBytes(4);               // 整块长度
            int items = beInt(raf);
            if (items <= 0 || items > 4096) {
                return 0;
            }
            int cover = 0;
            int thumb = 0;
            for (int i = 0; i < items; i++) {
                int kind = beInt(raf);
                int len = beInt(raf);
                if (len < 8 || len > 4096) {
                    return cover > 0 ? cover : thumb;
                }
                if (kind == 201 || kind == 202) {
                    int value = beInt(raf);
                    raf.skipBytes(len - 12);
                    if (kind == 201 && value > 0 && cover == 0) {
                        cover = value;
                    } else if (kind == 202 && value > 0 && thumb == 0) {
                        thumb = value;
                    }
                } else {
                    raf.skipBytes(len - 8);
                }
            }
            return cover > 0 ? cover : thumb;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /** 一整个记录当图片读出来；不是图或解不开就返回 null。 */
    private static Bitmap recordImage(RandomAccessFile raf, long[] off, int rec) {
        try {
            long len = off[rec + 1] - off[rec];
            if (len <= 12 || len > MAX_ENTRY) {
                return null;
            }
            byte[] head = new byte[4];
            raf.seek(off[rec]);
            raf.readFully(head, 0, 4);
            if (!isImage(head)) {
                return null;
            }
            byte[] buf = new byte[(int) len];
            raf.seek(off[rec]);
            raf.readFully(buf, 0, (int) len);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeByteArray(buf, 0, buf.length, o);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 没有 EXTH 的老书：图片记录里竖版面积最大的那个当封面，横图只算四分之一。 */
    private static Bitmap bestImage(RandomAccessFile raf, long[] off, int count, int from) {
        Bitmap best = null;
        long bestScore = 0;
        for (int i = Math.max(1, from); i < count; i++) {
            long len = off[i + 1] - off[i];
            if (len <= 12 || len > MAX_ENTRY) {
                continue;
            }
            byte[] head = new byte[4];
            try {
                raf.seek(off[i]);
                raf.readFully(head, 0, 4);
            } catch (Throwable e) {
                continue;
            }
            if (!isImage(head)) {
                continue;
            }
            byte[] buf;
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try {
                buf = new byte[(int) len];
                raf.seek(off[i]);
                raf.readFully(buf, 0, (int) len);
                BitmapFactory.decodeByteArray(buf, 0, buf.length, bounds);
            } catch (Throwable e) {
                continue;
            }
            int w = bounds.outWidth;
            int h = bounds.outHeight;
            if (w < 120 || h < 120) {
                continue;
            }
            long score = (long) w * h;
            if (h < w) {
                score = score / 4;      // 横着的多半是插图
            }
            if (best != null && score <= bestScore) {
                continue;
            }
            Bitmap bm = null;
            try {
                BitmapFactory.Options d = new BitmapFactory.Options();
                d.inPreferredConfig = Bitmap.Config.RGB_565;
                bm = BitmapFactory.decodeByteArray(buf, 0, buf.length, d);
            } catch (Throwable ignored) {
            }
            if (bm != null) {
                if (best != null) {
                    best.recycle();
                }
                best = bm;
                bestScore = score;
            }
        }
        return best;
    }

    private static boolean isImage(byte[] h) {
        if (h[0] == (byte) 0xFF && h[1] == (byte) 0xD8 && h[2] == (byte) 0xFF) {
            return true;                                  // JPEG
        }
        if (h[0] == (byte) 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G') {
            return true;                                  // PNG
        }
        if (h[0] == 'G' && h[1] == 'I' && h[2] == 'F') {
            return true;                                  // GIF
        }
        return h[0] == 'B' && h[1] == 'M';                 // BMP
    }

    private static String ascii(RandomAccessFile raf, int n) throws java.io.IOException {
        byte[] b = new byte[n];
        raf.readFully(b);
        return new String(b, "US-ASCII");
    }

    private static int beShort(RandomAccessFile raf) throws java.io.IOException {
        int a = raf.readUnsignedByte();
        int b = raf.readUnsignedByte();
        return (a << 8) | b;
    }

    private static int beInt(RandomAccessFile raf) throws java.io.IOException {
        int a = raf.readUnsignedByte();
        int b = raf.readUnsignedByte();
        int c = raf.readUnsignedByte();
        int d = raf.readUnsignedByte();
        return (a << 24) | (b << 16) | (c << 8) | d;
    }

    private static Bitmap decode(File f) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ------------------------------------------------------------------ bytes

    private static String read(ZipFile zf, String name) {
        InputStream in = null;
        try {
            ZipEntry ze = zf.getEntry(name);
            if (ze == null || ze.getSize() > MAX_ENTRY) {
                return null;
            }
            in = zf.getInputStream(ze);
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return new String(bos.toByteArray(), "UTF-8");
        } catch (Throwable ignored) {
            return null;
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static String first(String text, String regex) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile(regex, java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private static String quote(String s) {
        return java.util.regex.Pattern.quote(s);
    }

    private static String unescape(String s) {
        if (s == null) {
            return "";
        }
        try {
            return java.net.URLDecoder.decode(s.replace("+", "%2B"), "UTF-8");
        } catch (Throwable ignored) {
            return s;
        }
    }

    private static String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] d = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Throwable ignored) {
            return Integer.toHexString(s.hashCode());
        }
    }

    private static String ext(File f) {
        String n = f.getName();
        int p = n.lastIndexOf('.');
        return p >= 0 ? n.substring(p + 1).toLowerCase(java.util.Locale.ROOT) : "";
    }
}
