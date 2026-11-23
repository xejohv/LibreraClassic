package io.github.xejohv.classic;

import android.os.Environment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 回收站：应用里所有“删除”都先进这里，满 7 天再真正删掉。
 *
 * 目录 /sdcard/.PodClassicTrash/<删除时刻>/，里面放原文件（或原文件夹）和一份 meta.txt：
 *   第 1 行 = 原来的绝对路径（恢复时放回去）
 *   第 2 行 = 删除时间（毫秒）
 *
 * 只做文件搬运，不碰内容；同一个文件连续删两次不会打架（每次一个独立子目录）。
 */
public final class Trash {

    private Trash() {
    }

    /** 保留 7 天，逾期开库/启动时清掉。 */
    public static final long KEEP_MS = 7L * 24L * 60L * 60L * 1000L;

    public static File dir() {
        File d = new File(Environment.getExternalStorageDirectory(), ".PodClassicTrash");
        if (!d.exists()) {
            d.mkdirs();
        }
        return d;
    }

    /** 把文件（或文件夹）搬进回收站，返回它所在的回收站条目目录；失败返回 null。 */
    public static synchronized File moveIn(File src) {
        if (src == null || !src.exists()) {
            return null;
        }
        long now = System.currentTimeMillis();
        File slot = new File(dir(), String.valueOf(now));
        int n = 1;
        while (slot.exists()) {
            slot = new File(dir(), now + "_" + n);
            n++;
        }
        if (!slot.mkdirs()) {
            return null;
        }
        File keep = new File(slot, src.getName());
        boolean moved = move(src, keep);
        if (!moved) {
            // 跨分区时 rename 会失败，退回“复制 + 删除”
            moved = copy(src, keep);
            if (moved) {
                deleteTree(src);
            }
        }
        if (!moved) {
            deleteTree(slot);
            return null;
        }
        writeMeta(slot, src.getAbsolutePath(), now);
        return slot;
    }

    /** 恢复某个条目到原位置；目标已存在同名文件时改名追加序号。 */
    public static synchronized boolean restore(File slot) {
        String original = readOriginal(slot);
        File payload = payloadOf(slot);
        if (payload == null || original == null) {
            return false;
        }
        File target = new File(original);
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        if (target.exists()) {
            target = freeName(parent, target.getName());
        }
        boolean ok = move(payload, target);
        if (!ok) {
            ok = copy(payload, target);
            if (ok) {
                deleteTree(payload);
            }
        }
        if (ok) {
            deleteTree(slot);
            return true;
        }
        return false;
    }

    /** 立刻彻底删除某个条目。 */
    public static synchronized boolean drop(File slot) {
        if (slot == null || !slot.exists()) {
            return false;
        }
        return deleteTree(slot);
    }

    /** 清掉超过 7 天的条目，返回删了几个；每次进书库都调一次。 */
    public static synchronized int purge() {
        File[] all = dir().listFiles();
        if (all == null) {
            return 0;
        }
        long now = System.currentTimeMillis();
        int n = 0;
        for (File slot : all) {
            long at = deletedAt(slot);
            if (at > 0 && now - at > KEEP_MS) {
                if (deleteTree(slot)) {
                    n++;
                }
            }
        }
        return n;
    }

    /** 回收站里的条目，最近的在前。 */
    public static synchronized List<File> list() {
        File[] all = dir().listFiles();
        List<File> out = new ArrayList<>();
        if (all != null) {
            for (File f : all) {
                File payload = payloadOf(f);
                if (payload != null) {
                    out.add(f);
                }
            }
        }
        Collections.sort(out, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.compare(deletedAt(b), deletedAt(a));
            }
        });
        return out;
    }

    public static String nameOf(File slot) {
        File payload = payloadOf(slot);
        return payload == null ? "" : payload.getName();
    }

    public static long deletedAt(File slot) {
        String v = readMeta(slot, 1);
        try {
            return v == null ? 0L : Long.parseLong(v.trim());
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    /** 还剩几天（向上取整，最少 1 天）。 */
    public static int daysLeft(File slot) {
        long at = deletedAt(slot);
        if (at <= 0) {
            return 7;
        }
        long left = KEEP_MS - (System.currentTimeMillis() - at);
        if (left <= 0) {
            return 0;
        }
        return (int) Math.max(1, (left + 24L * 60L * 60L * 1000L - 1) / (24L * 60L * 60L * 1000L));
    }

    public static String originalOf(File slot) {
        return readOriginal(slot);
    }

    // ---------------------------------------------------------------- 内部

    private static File payloadOf(File slot) {
        if (slot == null || !slot.isDirectory()) {
            return null;
        }
        File[] kids = slot.listFiles();
        if (kids == null) {
            return null;
        }
        for (File k : kids) {
            if (!"meta.txt".equals(k.getName())) {
                return k;
            }
        }
        return null;
    }

    private static void writeMeta(File slot, String original, long at) {
        try {
            FileOutputStream out = new FileOutputStream(new File(slot, "meta.txt"));
            out.write((original + "\n" + at + "\n").getBytes("UTF-8"));
            out.flush();
            out.close();
        } catch (Throwable ignored) {
        }
    }

    private static String readMeta(File slot, int line) {
        File meta = new File(slot, "meta.txt");
        if (!meta.isFile()) {
            return null;
        }
        try {
            FileInputStream in = new FileInputStream(meta);
            byte[] buf = new byte[(int) Math.min(meta.length(), 4096)];
            int got = in.read(buf);
            in.close();
            if (got <= 0) {
                return null;
            }
            String[] lines = new String(buf, 0, got, "UTF-8").split("\n");
            return lines.length > line ? lines[line] : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String readOriginal(File slot) {
        String v = readMeta(slot, 0);
        return v == null || v.trim().isEmpty() ? null : v.trim();
    }

    private static File freeName(File parent, String name) {
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; i < 100; i++) {
            File f = new File(parent, base + " (" + i + ")" + ext);
            if (!f.exists()) {
                return f;
            }
        }
        return new File(parent, base + " (" + System.currentTimeMillis() + ")" + ext);
    }

    private static boolean move(File from, File to) {
        try {
            return from.renameTo(to);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean copy(File from, File to) {
        try {
            if (from.isDirectory()) {
                if (!to.exists() && !to.mkdirs()) {
                    return false;
                }
                File[] kids = from.listFiles();
                if (kids != null) {
                    for (File k : kids) {
                        if (!copy(k, new File(to, k.getName()))) {
                            return false;
                        }
                    }
                }
                return true;
            }
            File parent = to.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            FileInputStream in = new FileInputStream(from);
            FileOutputStream out = new FileOutputStream(to);
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            out.flush();
            in.close();
            out.close();
            return to.length() == from.length();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean deleteTree(File f) {
        if (f == null || !f.exists()) {
            return true;
        }
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) {
                for (File k : kids) {
                    deleteTree(k);
                }
            }
        }
        return f.delete();
    }
}
