package io.github.xejohv.classic;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import java.util.Random;

/**
 * 书库的木纹底：深胡桃木的横向木纹 + 四角压暗，跟 iBooks 的书架一个语言。
 *
 * 不用贴图资源：纹理在运行时按屏幕尺寸画一次位图存起来，缩放窗口才重画，
 * 所以拖动列表时每帧只是一次 drawBitmap，不占 CPU，APK 也不多一个图。
 */
public class WoodBackdrop extends Drawable {

    /** 木纹越靠下越深，模拟头顶灯光打下来。 */
    private static final int[] STOPS = {
            0xFF7B4E2B, 0xFF6E4525, 0xFF5F3A1E, 0xFF6B4325, 0xFF5A381D,
    };

    private Bitmap cache;
    private int cacheW;
    private int cacheH;

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (b.width() <= 0 || b.height() <= 0) {
            return;
        }
        if (cache == null || cacheW != b.width() || cacheH != b.height()) {
            build(b.width(), b.height());
        }
        canvas.drawBitmap(cache, null, b, null);
    }

    private void build(int w, int h) {
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bmp.setDensity(Bitmap.DENSITY_NONE);
        Canvas cv = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // 底色：上浅下深的胡桃木
        p.setShader(new LinearGradient(0, 0, w * 0.35f, h, STOPS, null, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, p);

        // 木纹：一根根横向的波浪细线，宽窄、明暗、起伏都不一样，
        // 固定种子保证每次进来纹理位置一致，不会一闪一变
        Random rnd = new Random(0x5EED2026L);
        Path path = new Path();
        float y = -8f;
        while (y < h) {
            float amp = 1.2f + rnd.nextFloat() * 3.4f;
            float freq = 0.0022f + rnd.nextFloat() * 0.0045f;
            float phase = rnd.nextFloat() * 6.28f;
            boolean light = rnd.nextFloat() < 0.34f;
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(0.8f + rnd.nextFloat() * 2.2f);
            p.setColor(light ? 0x22FFD9A8 : 0x330F0702);
            path.reset();
            path.moveTo(-4, y);
            for (float x = 0; x <= w + 4; x += 6f) {
                path.lineTo(x, y + (float) Math.sin(x * freq + phase) * amp);
            }
            cv.drawPath(path, p);
            // 一条深色纹旁边常常跟着一条更暗的细线，木纹才有“沟”的感觉
            if (rnd.nextFloat() < 0.45f) {
                p.setStrokeWidth(0.6f + rnd.nextFloat() * 1.1f);
                p.setColor(0x26000000);
                cv.drawPath(path, p);
            }
            y += 5f + rnd.nextFloat() * 12f;
        }

        // 几处节疤：小椭圆环，别太多
        for (int i = 0; i < 3; i++) {
            float cx = rnd.nextFloat() * w;
            float cy = rnd.nextFloat() * h;
            float rx = w * (0.06f + rnd.nextFloat() * 0.10f);
            float ry = rx * (0.22f + rnd.nextFloat() * 0.2f);
            for (int k = 0; k < 4; k++) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.2f);
                p.setColor(0x1E0A0500);
                cv.drawOval(cx - rx - k * 3f, cy - ry - k * 2f, cx + rx + k * 3f, cy + ry + k * 2f, p);
            }
        }

        // 四角压暗：列表滑动时视线才收在中间
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(w / 2f, h * 0.45f, Math.max(w, h) * 0.78f,
                new int[]{0x00000000, 0x00000000, 0x4A000000}, new float[]{0f, 0.62f, 1f},
                Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, p);
        p.setShader(null);

        cache = bmp;
        cacheW = w;
        cacheH = h;
    }

    @Override
    public void setAlpha(int alpha) {
        // 全屏背景，不需要外层透明度
    }

    @Override
    public void setColorFilter(android.graphics.ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
