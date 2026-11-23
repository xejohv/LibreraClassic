package io.github.xejohv.classic;

import android.content.Context;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.List;

/** 轻量音乐内核：常驻在 :main 进程，与阅读器完全无关。 */
public class PodMusic {

    public static class Track {
        public String title;
        public String artist;
        public Uri uri;
        public long duration;
    }

    private static PodMusic sInstance;

    public static synchronized PodMusic get(Context c) {
        if (sInstance == null) {
            sInstance = new PodMusic(c.getApplicationContext());
        }
        return sInstance;
    }

    private final Context app;
    private final List<Track> tracks = new ArrayList<>();
    private MediaPlayer player;
    private int index = -1;
    private Runnable onTick;

    private PodMusic(Context c) {
        app = c;
    }

    public List<Track> tracks() {
        if (tracks.isEmpty()) {
            scan();
        }
        return tracks;
    }

    private void scan() {
        tracks.clear();
        try {
            Cursor c = app.getContentResolver().query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Audio.Media.TITLE,
                            MediaStore.Audio.Media.ARTIST,
                            MediaStore.Audio.Media.DURATION,
                            MediaStore.Audio.Media._ID},
                    MediaStore.Audio.Media.IS_MUSIC + "!=0", null,
                    MediaStore.Audio.Media.TITLE + " ASC");
            if (c != null) {
                while (c.moveToNext() && tracks.size() < 500) {
                    Track t = new Track();
                    t.title = c.getString(0);
                    t.artist = c.getString(1);
                    t.duration = c.getLong(2);
                    t.uri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            String.valueOf(c.getLong(3)));
                    tracks.add(t);
                }
                c.close();
            }
        } catch (Throwable ignored) {
        }
    }

    public void setOnTick(Runnable r) {
        onTick = r;
    }

    public int index() {
        return index;
    }

    public Track current() {
        if (index >= 0 && index < tracks.size()) {
            return tracks.get(index);
        }
        return null;
    }

    public boolean isPlaying() {
        try {
            return player != null && player.isPlaying();
        } catch (Throwable t) {
            return false;
        }
    }

    public int position() {
        try {
            return player == null ? 0 : player.getCurrentPosition();
        } catch (Throwable t) {
            return 0;
        }
    }

    public int duration() {
        try {
            if (player != null && player.getDuration() > 0) return player.getDuration();
        } catch (Throwable ignored) {
        }
        Track t = current();
        return t == null ? 0 : (int) t.duration;
    }

    public void play(int i) {
        if (i < 0 || i >= tracks.size()) return;
        index = i;
        try {
            if (player != null) {
                player.release();
            }
            player = new MediaPlayer();
            player.setDataSource(app, tracks.get(i).uri);
            player.setOnCompletionListener(mp -> next());
            player.prepare();
            player.start();
            if (onTick != null) onTick.run();
        } catch (Throwable ignored) {
        }
    }

    public void toggle() {
        try {
            if (player == null) {
                if (!tracks.isEmpty()) play(index < 0 ? 0 : index);
                return;
            }
            if (player.isPlaying()) {
                player.pause();
            } else {
                player.start();
            }
            if (onTick != null) onTick.run();
        } catch (Throwable ignored) {
        }
    }

    public void next() {
        if (tracks.isEmpty()) return;
        play((index + 1) % tracks.size());
    }

    public void prev() {
        if (tracks.isEmpty()) return;
        play(index <= 0 ? tracks.size() - 1 : index - 1);
    }

    public void seekTo(int ms) {
        try {
            if (player != null) player.seekTo(ms);
        } catch (Throwable ignored) {
        }
    }
}