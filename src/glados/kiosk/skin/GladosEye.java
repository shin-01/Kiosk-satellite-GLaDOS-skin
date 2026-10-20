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

import java.util.Random;

/**
 * Potato GLaDOS: the Aperture eye jammed into a potato, as the game's second
 * act has her. The potato is a lumpy brown ellipse with darker spots, the eye
 * is a metal ring with bolts and a glowing yellow lens with a red pupil that
 * flares with the voice and the answer, and a few clipped wires hang from the
 * back. It animates itself while attached and reads the voice from
 * {@link GladosSkin}. Everything is drawn with simple shapes, so it renders
 * cheaply on weak kiosks.
 */
final class GladosEye extends View {
    /** GLaDOS's optic glow, a warm aperture yellow. */
    private static final int GLOW = 0xFFC141;
    /** The red pupil at the lens center. */
    private static final int PUPIL = 0xE23B2E;
    /** Radians a second of the lens's chattering while the assistant speaks. */
    private static final float SYLLABLE_RATE = 26f;
    /** The lens, as a fraction of the ring's radius. */
    private static final float LENS_FRACTION = 0.52f;

    private final GladosSkin skin;
    private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bolt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lens = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pupil = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wire = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final long origin = SystemClock.uptimeMillis();
    /** One potato per skin instance, so the lumps stay put across frames. */
    private final float[] spots = new float[14];
    private final float[] wireTips = new float[6];
    /** Eased toward their targets every frame, so a change of mode glides. */
    private float flare = 0.2f;
    private double last = -1;
    /** The loudest recent level while listening and while speaking, each decaying. */
    private float micPeak, playPeak;

    GladosEye(Context context, GladosSkin skin) {
        super(context);
        this.skin = skin;
        Random random = new Random(2049);
        for (int i = 0; i < spots.length; i += 2) {
            spots[i] = random.nextFloat();
            spots[i + 1] = random.nextFloat();
        }
        for (int i = 0; i < wireTips.length; i++) wireTips[i] = random.nextFloat();
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

        float cx = width / 2f, cy = height / 2f;
        float radius = Math.min(cx, cy) * 0.92f;
        // A slow wobble: the potato sways a little, alive even when idle.
        float sway = (float) Math.toDegrees(Math.sin(t * 0.8) * 2.5);

        canvas.save();
        canvas.rotate(sway, cx, cy);

        // The clipped wires hanging from the potato's back, drawn first so the potato covers their roots.
        wire.setStrokeWidth(radius * 0.028f);
        wire.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < wireTips.length; i++) {
            double angle = Math.PI * (0.75 + 0.5 * wireTips[i]);
            float rootX = cx + (float) Math.cos(angle) * radius * 0.95f;
            float rootY = cy + (float) Math.sin(angle) * radius * 0.95f;
            float endX = rootX + (float) Math.cos(angle) * radius * (0.18f + 0.12f * wireTips[i]);
            float endY = rootY + (float) Math.sin(angle) * radius * 0.18f + radius * 0.10f * (float) Math.sin(t * 2 + i);
            wire.setColor(argb(i % 2 == 0 ? 0x9BB7C4 : 0xC25B4A, 0.75f));
            canvas.drawLine(rootX, rootY, endX, endY, wire);
        }

        // The potato: a lumpy ellipse, wider than tall, with a darker underside and baked spots.
        float bodyRx = radius, bodyRy = radius * 0.78f;
        oval.set(cx - bodyRx, cy - bodyRy, cx + bodyRx, cy + bodyRy);
        body.setShader(new RadialGradient(cx, cy - bodyRy * 0.4f, radius * 1.3f,
            new int[] {0xFFB7793F, 0xFF9A6232}, new float[] {0.3f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawOval(oval, body);
        shade.setColor(argb(0x5C3A1E, 0.5f));
        oval.set(cx - bodyRx, cy, cx + bodyRx, cy + bodyRy);
        canvas.drawOval(oval, shade);
        spot.setColor(argb(0x6B421F, 0.8f));
        for (int i = 0; i < spots.length; i += 2) {
            float sx = cx + (spots[i] - 0.5f) * 2 * bodyRx * 0.72f;
            float sy = cy + (spots[i + 1] - 0.5f) * 2 * bodyRy * 0.6f;
            float sr = radius * (0.03f + 0.03f * spots[(i + 1) % spots.length]);
            canvas.drawOval(new RectF(sx - sr, sy - sr * 0.8f, sx + sr, sy + sr * 0.8f), spot);
        }

        // The eye, riding high on the potato: a metal ring with bolts and the glowing lens.
        float eyeR = radius * LENS_FRACTION;
        float ex = cx + radius * 0.18f, ey = cy - bodyRy * 0.28f;
        ring.setColor(argb(0x9BB7C4, 0.95f));
        ring.setStyle(Paint.Style.STROKE);
        float ringWidth = eyeR * 0.24f;
        ring.setStrokeWidth(ringWidth);
        canvas.drawCircle(ex, ey, eyeR, ring);
        bolt.setColor(argb(0x5E7683, 0.95f));
        for (int i = 0; i < 6; i++) {
            double angle = Math.PI * 2 * i / 6 + t * 0.2;
            float bx = ex + (float) Math.cos(angle) * (eyeR + ringWidth / 2f);
            float by = ey + (float) Math.sin(angle) * (eyeR + ringWidth / 2f);
            canvas.drawCircle(bx, by, ringWidth * 0.28f, bolt);
        }
        // The lens and its glow, swelled by the flare.
        float lensR = eyeR - ringWidth / 2f;
        lens.setShader(new RadialGradient(ex, ey, lensR,
            new int[] {0xFF2A2620, 0xFF141210}, new float[] {0.2f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(ex, ey, lensR, lens);
        float coreR = lensR * (0.34f + 0.30f * flare);
        glow.setShader(new RadialGradient(ex, ey, lensR * 0.95f,
            new int[] {argb(GLOW, 0.5f * flare), argb(GLOW, 0.22f * flare), argb(GLOW, 0)},
            new float[] {0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(ex, ey, lensR * 0.95f, glow);
        core.setColor(argb(GLOW, 0.95f));
        canvas.drawCircle(ex, ey, coreR, core);
        // The red pupil, GLaDOS's menace even as a potato.
        float wobble = 0.5f + 0.5f * (float) Math.sin(t * 1.7);
        pupil.setColor(argb(PUPIL, 0.75f + 0.25f * flare));
        canvas.drawCircle(ex + lensR * 0.05f * wobble, ey - lensR * 0.05f * wobble, coreR * (0.42f + 0.10f * flare), pupil);

        canvas.restore();
        if (isAttachedToWindow()) postInvalidateOnAnimation();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        last = -1;
        postInvalidateOnAnimation();
    }
}
