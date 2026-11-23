package org.ebookdroid.droids;

import com.foobnix.android.utils.LOG;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Kindle 转换出来的 epub 常把插图包在一层 SVG 里：{@code <img src="flow00003.svg"/>}，
 * svg 里再 {@code <image xlink:href="resource00000.jpg"/>}。这类“svg 套位图”在 WebView 里
 * 会被栅格化成模糊缩略图（同一张 jpg 直接嵌入是清晰的），所以转换后在这里把 svg 外壳拆掉，
 * 让 html 直接引用真正的图片。
 */
public final class MobiSvgUnwrap {

    /** 版本号写进缓存名，改了这里老缓存自然失效。 */
    public static final String VERSION = "u2";

    private static final Pattern SVG_IMAGE = Pattern.compile("<image\\b[^>]*?(?:xlink:href|href)\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_IMG = Pattern.compile("<img\\b[^>]*?src\\s*=\\s*([\"'])([^\"']+\\.svg)\\1", Pattern.CASE_INSENSITIVE);
    private static final Pattern IMG_TAG = Pattern.compile("<img\\b[^>]*>", Pattern.CASE_INSENSITIVE);

    private MobiSvgUnwrap() {
    }

    private static String dirOf(String path) {
        int i = path.lastIndexOf('/');
        return i < 0 ? "" : path.substring(0, i + 1);
    }

    private static String normalize(String base, String href) {
        String path = href.startsWith("/") ? href.substring(1) : base + href;
        String[] parts = path.split("/");
        List<String> out = new ArrayList<String>();
        for (String p : parts) {
            if (p.isEmpty() || p.equals(".")) continue;
            if (p.equals("..")) {
                if (!out.isEmpty()) out.remove(out.size() - 1);
                continue;
            }
            out.add(p);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < out.size(); i++) {
            if (i > 0) sb.append('/');
            sb.append(out.get(i));
        }
        return sb.toString();
    }

    /** svg 入口 -> 它引用的位图路径（zip 内路径） */
    private static Map<String, String> collectSvgImages(ZipFile zip) {
        Map<String, String> map = new LinkedHashMap<String, String>();
        for (ZipEntry e : java.util.Collections.list(zip.entries())) {
            if (!e.getName().toLowerCase().endsWith(".svg") || e.isDirectory()) continue;
            try {
                String body = readText(zip.getInputStream(e));
                Matcher m = SVG_IMAGE.matcher(body);
                if (!m.find()) continue;
                map.put(e.getName(), normalize(dirOf(e.getName()), m.group(1)));
            } catch (Exception ex) {
                LOG.e(ex);
            }
        }
        return map;
    }

    private static String readText(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        in.close();
        return new String(bos.toByteArray(), "UTF-8");
    }

    private static String rewriteHtml(String text, String htmlPath, Map<String, String> svgImages) {
        String base = dirOf(htmlPath);
        boolean changed = false;
        StringBuffer sb = new StringBuffer(text.length());
        Matcher m = HTML_IMG.matcher(text);
        while (m.find()) {
            String quote = m.group(1);
            String svgPath = normalize(base, m.group(2));
            String target = svgImages.get(svgPath);
            String repl = m.group(0);
            if (target != null) {
                repl = "<img src=" + quote + relativeTo(base, target) + quote;
                changed = true;
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(repl));
        }
        m.appendTail(sb);
        if (!changed) return null;
        return sb.toString();
    }

    /** html 所在目录（以 / 结尾，根为 ""）到 zip 内目标路径的相对写法 */
    private static String relativeTo(String base, String target) {
        String baseDir = base;
        if (baseDir.endsWith("/")) baseDir = baseDir.substring(0, baseDir.length() - 1);
        String[] b = baseDir.isEmpty() ? new String[0] : baseDir.split("/");
        String[] t = target.split("/");
        int common = 0;
        while (common < b.length && common < t.length - 1 && b[common].equals(t[common])) common++;
        StringBuilder sb = new StringBuilder();
        for (int i = common; i < b.length; i++) sb.append("../");
        for (int i = common; i < t.length; i++) {
            if (i > common) sb.append('/');
            sb.append(t[i]);
        }
        return sb.toString();
    }

    /** 缓存下来的 epub 是否完整可用：至少要有 html 正文，条目数也得像个 epub。 */
    public static boolean isValid(File epub) {
        if (epub == null || !epub.isFile() || epub.length() < 1024) return false;
        ZipFile zip = null;
        try {
            zip = new ZipFile(epub);
            int entries = 0;
            int html = 0;
            for (ZipEntry e : java.util.Collections.list(zip.entries())) {
                if (e.isDirectory()) continue;
                entries++;
                String name = e.getName().toLowerCase();
                if (name.endsWith(".html") || name.endsWith(".xhtml")) html++;
            }
            return entries >= 3 && html >= 1;
        } catch (Throwable e) {
            return false;
        } finally {
            try {
                if (zip != null) zip.close();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 这本书是不是以中日韩文字为主。是的话断词没有意义（中文没有词间断点），
     * 断词整本重打包会白白多花十几秒。
     */
    public static boolean looksChinese(File epub) {
        if (epub == null || !epub.isFile()) return false;
        ZipFile zip = null;
        try {
            zip = new ZipFile(epub);
            int cjk = 0;
            int total = 0;
            int files = 0;
            for (ZipEntry e : java.util.Collections.list(zip.entries())) {
                String name = e.getName().toLowerCase();
                if (e.isDirectory() || !(name.endsWith(".html") || name.endsWith(".xhtml"))) continue;
                if (++files > 3) break;
                String text = readText(zip.getInputStream(e));
                for (int i = 0; i < text.length() && total < 6000; i++) {
                    char c = text.charAt(i);
                    if (Character.isWhitespace(c)) continue;
                    if (c < 0x20) continue;
                    total++;
                    if (c >= 0x3000 && c <= 0x9FFF) cjk++;
                }
                if (total >= 6000) break;
            }
            return total > 200 && cjk * 4 >= total;
        } catch (Throwable e) {
            LOG.e(e);
            return false;
        } finally {
            try {
                if (zip != null) zip.close();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 就地拆掉 svg 图片外壳；没有 svg 图片的 epub 原样返回。
     */
    public static boolean fix(File epub) {
        if (epub == null || !epub.isFile()) return false;
        File tmp = new File(epub.getParentFile(), epub.getName() + ".unwrap");
        ZipFile zip = null;
        ZipOutputStream zos = null;
        try {
            zip = new ZipFile(epub);
            Map<String, String> svgImages = collectSvgImages(zip);
            if (svgImages.isEmpty()) return false;

            zos = new ZipOutputStream(new FileOutputStream(tmp));
            boolean first = true;
            int replaced = 0;
            for (ZipEntry e : java.util.Collections.list(zip.entries())) {
                String name = e.getName();
                byte[] data = null;
                if (!e.isDirectory() && (name.endsWith(".html") || name.endsWith(".xhtml"))) {
                    String text = readText(zip.getInputStream(e));
                    String newText = rewriteHtml(text, name, svgImages);
                    if (newText != null) {
                        data = newText.getBytes("UTF-8");
                        replaced++;
                    }
                }
                ZipEntry out = new ZipEntry(name);
                if (first && name.equals("mimetype")) {
                    out.setMethod(ZipEntry.STORED);
                    out.setSize(data == null ? e.getSize() : data.length);
                    out.setCompressedSize(data == null ? e.getSize() : data.length);
                    java.util.zip.CRC32 crc = new java.util.zip.CRC32();
                    if (data != null) {
                        crc.update(data);
                    } else {
                        byte[] raw = readBytes(zip.getInputStream(e));
                        data = raw;
                        crc.update(raw);
                    }
                    out.setCrc(crc.getValue());
                }
                zos.putNextEntry(out);
                if (e.isDirectory()) {
                    zos.closeEntry();
                } else {
                    if (data == null) data = readBytes(zip.getInputStream(e));
                    zos.write(data);
                    zos.closeEntry();
                }
                first = false;
            }
            zos.close();
            zos = null;
            zip.close();
            zip = null;

            if (replaced == 0) {
                tmp.delete();
                return false;
            }
            if (!epub.delete()) {
                // 删不掉就留着原文件，避免把书搞坏
                tmp.delete();
                return false;
            }
            if (!tmp.renameTo(epub)) {
                return false;
            }
            LOG.d("MobiSvgUnwrap", "fixed", epub.getName(), "htmls", replaced, "svgs", svgImages.size());
            return true;
        } catch (Throwable e) {
            LOG.e(e);
            if (tmp.exists()) tmp.delete();
            return false;
        } finally {
            try {
                if (zos != null) zos.close();
            } catch (Exception ignored) {
            }
            try {
                if (zip != null) zip.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static byte[] readBytes(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        in.close();
        return bos.toByteArray();
    }
}
