// SPDX-License-Identifier: MIT
package glados.kiosk.skin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/**
 * The Aperture eye: a chrome ring, a dark lens and a glowing yellow-orange
 * core that flares with the voice and the answer. Six shutter blades tighten
 * around the core while the assistant thinks, the ring rotates slowly, and
 * the whole eye breathes at rest. It animates itself while attached and reads
 * the voice from {@link GladosSkin}.
 */
final class GladosEye extends View {
    /** Aperture's cold blue-white for the ring and lens rim. */
    private static final int STEEL = 0x9BB7C4;
    /** GLaDOS's optic glow, a warm aperture yellow. */
    private static final int GLOW = 0xFFC141;
    /** Radians a second of the shutter's chattering while the assistant speaks. */
    private static final float SYLLABLE_RATE = 26f;

    private final GladosSkin skin;
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringShade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lens = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final long origin = SystemClock.uptimeMillis();
    /** Eased toward their targets every frame, so a change of mode glides. */
    private float flare = 0.2f, close;
    private double last = -1;
    /** The loudest recent level while listening and while speaking, each decaying. */
    private float micPeak, playPeak;

    GladosEye(Context context, GladosSkin skin) {
        super(context);
        this.skin = skin;
        ring.setColor(argb(STEEL, 0.9f));
        ring.setStyle(Paint.Style.STROKE);
        ringShade.setColor(argb(STEEL, 0.28f));
        ringShade.setStyle(Paint.Style.STROKE);
        lens.setColor(0xFF0C0E10);
        blade.setColor(argb(STEEL, 0.55f));
        blade.setStyle(Paint.Style.STROKE);
        core.setColor(argb(GLOW, 0.95f));
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

    /** 0 to 1: how far the shutter blades close over the core. Only while the assistant thinks. */
    private static float closeTarget(int mode) {
        return mode == GladosSkin.THINKING ? 0.62f : 0f;
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
        float target = closeTarget(mode);
        close += (target - close) * (1f - (float) Math.exp((target > close ? -30 : -14) * dt));

        float cx = width / 2f, cy = height / 2f;
        float radius = Math.min(cx, cy) * (0.86f + 0.02f * flare);
        // The chrome ring: an outer shade, the ring itself and a thin inner rim.
        float strokeWidth = radius * 0.16f;
        ringShade.setStrokeWidth(strokeWidth * 1.7f);
        ring.setStrokeWidth(strokeWidth);
        box.set(cx - radius, cy - radius, cx + radius, cy + radius);
        canvas.drawOval(box, ringShade);
        canvas.save();
        canvas.rotate((float) Math.toDegrees(t * 2.6), cx, cy);
        canvas.drawOval(box, ring);
        canvas.restore();
        // The lens.
        float lensRadius = radius - strokeWidth * 0.6f;
        box.set(cx - lensRadius, cy - lensRadius, cx + lensRadius, cy + lensRadius);
        lens.setShader(new RadialGradient(cx, cy, lensRadius,
            new int[] {0xFF1A1F24, 0xFF0C0E10}, new float[] {0.2f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawOval(box, lens);
        // The core and its glow, swelled by the flare.
        float coreRadius = lensRadius * (0.20f + 0.30f * flare);
        glow.setShader(new RadialGradient(cx, cy, lensRadius * 0.9f,
            new int[] {argb(GLOW, 0.5f * flare), argb(GLOW, 0.22f * flare), argb(GLOW, 0)},
            new float[] {0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, lensRadius * 0.9f, glow);
        canvas.drawCircle(cx, cy, coreRadius, core);
        // The six shutter blades, rotated slowly and closed while thinking.
        float open = 1f - close;
        float inner = coreRadius + (lensRadius - coreRadius) * 0.55f * open;
        blade.setStrokeWidth(radius * 0.035f);
        canvas.save();
        canvas.rotate((float) Math.toDegrees(t * 9), cx, cy);
        for (int i = 0; i < 6; i++) {
            double angle = Math.PI * 2 * i / 6;
            float midX = cx + (float) Math.cos(angle) * (inner + lensRadius) / 2f;
            float midY = cy + (float) Math.sin(angle) * (inner + lensRadius) / 2f;
            float half = (lensRadius - inner) / 2f;
            canvas.save();
            canvas.rotate((float) Math.toDegrees(angle), midX, midY);
            box.set(midX - half, midY - radius * 0.018f, midX + half, midY + radius * 0.018f);
            canvas.drawRoundRect(box, radius * 0.018f, radius * 0.018f, blade);
            canvas.restore();
        }
        canvas.restore();
        if (isAttachedToWindow()) postInvalidateOnAnimation();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        last = -1;
        postInvalidateOnAnimation();
    }
}
