package io.github.xejohv.classic;

import com.foobnix.android.utils.LOG;

import org.ebookdroid.droids.MobiContext;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 书库一打开就把 mobi/azw3 提前转成 epub。
 *
 * 这些格式点开要先跑一次原生转换（十几秒到几十秒），干等一个「请稍候」很糟。
 * 书架出来之后用最低优先级线程一本一本转，用户翻牌子、看封面的时候它就转完了；
 * 缓存按书留存，之后再打开就是秒开。转换幂等，点开时发现没缓存也会自己转。
 */
public final class MobiPreconvert {

    /** 已经排进队列的书，避免同一本重复排。 */
    private static final Set<String> QUEUED = new HashSet<String>();
    /** 最近一次书库的书单；新书进来时只更新它，正在跑的线程会接着往下取。 */
    private static volatile List<File> shelf = new ArrayList<File>();
    private static volatile boolean running = false;

    private MobiPreconvert() {
    }

    public static void kick(final List<File> books) {
        if (books == null || books.isEmpty()) return;
        shelf = new ArrayList<File>(books);
        synchronized (MobiPreconvert.class) {
            if (running) return;
            running = true;
        }
        new Thread("pod-preconvert") {
            @Override
            public void run() {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                try {
                    File next = pick();
                    while (next != null) {
                        try {
                            LOG.d("MobiPreconvert", "start", next.getName());
                            MobiContext.ensureCache(next.getPath());
                            LOG.d("MobiPreconvert", "done", next.getName());
                        } catch (Throwable e) {
                            LOG.e(e);
                        }
                        next = pick();
                    }
                } finally {
                    synchronized (MobiPreconvert.class) {
                        running = false;
                    }
                }
            }
        }.start();
    }

    /** 取下一本还没排过的待转换书，并把它标记为已排。 */
    private static synchronized File pick() {
        List<File> list = shelf;
        LOG.d("MobiPreconvert", "pick over", list.size());
        for (int i = 0; i < list.size(); i++) {
            File f = list.get(i);
            if (f == null || !f.isFile()) continue;
            boolean need = MobiContext.needsConvert(f.getPath());
            String path = f.getPath();
            boolean queued = QUEUED.add(path);
            // 已经有完整缓存就跳过，既省电又不和「点开时转换」抢文件
            File cache = MobiContext.cacheFileFor(path);
            LOG.d("MobiPreconvert", "cand", f.getName(), need, queued, cache.isFile());
            if (!need) continue;
            if (!queued) continue;
            if (cache.isFile()) continue;
            return f;
        }
        return null;
    }
}
