// SPDX-License-Identifier: MIT
package glados.kiosk.skin;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import me.jxl.kiosk.plugins.PluginHost;

import java.io.IOException;
import java.io.InputStream;

/**
 * Potato GLaDOS as a static photorealistic picture, with only the eye's light
 * animated over it: the warm glow that flares with the voice and the answer,
 * and the red pupil that breathes. The picture loads from the plugin's assets
 * off the main thread; until then, or if it is missing, the lens alone draws.
 * It reads the voice from {@link GladosSkin} and animates itself while
 * attached, redrawing only the small eye area.
 */
final class GladosEye extends View {
    /** The static picture in assets/, 560x560. */
    static final String POTATO = "potato.png";
    /** Where the eye's lens sits in the picture, as fractions of its side. */
    static final float EYE_X = 0.60f, EYE_Y = 0.43f;
    /** The glowing lens radius, and the metal ring's inner edge, as fractions of the picture's side. */
    static final float LENS_FRACTION = 0.058f, RING_FRACTION = 0.145f;
    /** GLaDOS's optic glow, a warm aperture yellow. */
    private static final int GLOW = 0xFFC141;
    /** The red pupil at the lens center. */
    private static final int PUPIL = 0xE23B2E;
    /** Radians a second of the lens's chattering while the assistant speaks. */
    private static final float SYLLABLE_RATE = 26f;

    private final GladosSkin skin;
    private final Paint picture = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint lens = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pupil = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF dirty = new RectF();
    private final long origin = SystemClock.uptimeMillis();
    /** Eased toward its target every frame, so a change of mode glides. */
    private float flare = 0.2f;
    private double last = -1;
    /** The loudest recent level while listening and while speaking, each decaying. */
    private float micPeak, playPeak;
    private Bitmap potato;
    private float bitmapSide = 1;

    GladosEye(Context context, GladosSkin skin) {
        super(context);
        this.skin = skin;
    }

    /** Decodes the picture off the main thread and shows it once it is ready. */
    void loadPicture(final PluginHost host) {
        Thread thread = new Thread(() -> {
            Bitmap decoded = null;
            try (InputStream in = host.openAsset(POTATO)) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inScaled = false;
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                decoded = BitmapFactory.decodeStream(in, null, options);
            } catch (IOException | UnsupportedOperationException | SecurityException e) {
                host.log("GLaDOS could not read " + POTATO + ": " + e.getMessage());
            }
            if (decoded == null) return;
            final Bitmap bitmap = decoded;
            post(() -> {
                potato = bitmap;
                bitmapSide = bitmap.getWidth();
                invalidate();
            });
        }, "glados-potato");
        thread.setDaemon(true);
        thread.start();
    }

    private static int argb(int rgb, float alpha) {
        return Color.argb(Math.round(255 * alpha), (rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255);
    }

    /** The level against its source's recent peak: a peak decays to half in about two seconds, and the gain stops at 4x so room noise stays low. */
    private float scaled(float dt, boolean speaking) {
        float l = Math.max(0f, Math.min(1f, skin.level()));
        float peak = Math.max(l, (speaking ? playPeak : micPeak) * (float) Math.exp(-0.35 * dt));
        if (speaking) playPeak = peak; else micPeak = peak;
        return Math.max(0f, Math.min(1f, l * Math.min(4f, 0.9f / Math.max(peak, 0.1f))));
    }

    private static float flareTarget(double t, float l, int mode) {
        switch (mode) {
            case GladosSkin.SPEAKING: return 0.32f + l * 0.55f + 0.08f * (float) Math.sin(t * SYLLABLE_RATE) * l;
            case GladosSkin.LISTENING: return 0.26f + l * 0.30f + 0.05f * (float) Math.sin(t * 2.4);
            case GladosSkin.THINKING: return 0.30f + 0.14f * (float) Math.sin(t * 3.6);
            default: return 0.22f + 0.07f * (float) Math.sin(t * 1.3);
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        int width = getWidth(), height = getHeight();
        if (width <= 0 || height <= 0) return;
        double t = (SystemClock.uptimeMillis() - origin) / 1000.0;
        float dt = last < 0 ? 1f : (float) Math.max(0, Math.min(0.1, t - last));
        last = t;
        int mode = skin.mode();
        boolean reactive = skin.reactive();
        boolean listening = mode == GladosSkin.LISTENING, speaking = mode == GladosSkin.SPEAKING;
        // Without a level to follow, the glow chatters on its own while speaking.
        float l = reactive ? (listening || speaking ? scaled(dt, speaking) : 0f)
            : speaking ? 0.3f + 0.25f * (0.5f + 0.5f * (float) Math.sin(t * 3.1)) * (0.5f + 0.5f * (float) Math.sin(t * SYLLABLE_RATE)) : 0f;
        flare += (Math.max(0f, Math.min(1f, flareTarget(t, l, mode))) - flare) * (1f - (float) Math.exp(-20 * dt));

        // The static picture, fitted whole and centered: the potato never moves.
        // The eye sits at fixed fractions of the picture, so the light lands on the picture's own lens.
        float side = Math.min(width, height);
        float scale = 1f, ex = width * EYE_X, ey = height * EYE_Y;
        if (potato != null) {
            scale = Math.min(width / (float) potato.getWidth(), height / (float) potato.getHeight());
            float dw = potato.getWidth() * scale, dh = potato.getHeight() * scale;
            float left = (width - dw) / 2f, top = (height - dh) / 2f;
            canvas.drawBitmap(potato, null, new RectF(left, top, left + dw, top + dh), picture);
            ex = left + dw * EYE_X;
            ey = top + dh * EYE_Y;
        }
        float lensR = side * LENS_FRACTION;
        float ringR = side * RING_FRACTION;
        // The lens itself: a dark disc under the light, so the light reads at any flare.
        lens.setShader(new RadialGradient(ex, ey, ringR,
            new int[] {0xFF2A2620, 0xFF141210}, new float[] {0.2f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(ex, ey, ringR, lens);
        // The glow, swelled by the flare, kept inside the metal ring so the picture stays put.
        float glowR = ringR * 0.92f;
        glow.setShader(new RadialGradient(ex, ey, glowR,
            new int[] {argb(GLOW, 0.55f * flare), argb(GLOW, 0.24f * flare), argb(GLOW, 0)},
            new float[] {0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(ex, ey, glowR, glow);
        float coreR = lensR * (0.9f + 0.35f * flare);
        core.setColor(argb(GLOW, 0.95f));
        canvas.drawCircle(ex, ey, coreR, core);
        // The red pupil, breathing on its own.
        float breath = 0.5f + 0.5f * (float) Math.sin(t * 1.7);
        pupil.setColor(argb(PUPIL, 0.75f + 0.25f * flare));
        canvas.drawCircle(ex, ey, coreR * (0.40f + 0.08f * breath), pupil);

        if (isAttachedToWindow()) postInvalidateOnAnimation();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        last = -1;
        postInvalidateOnAnimation();
    }
}
