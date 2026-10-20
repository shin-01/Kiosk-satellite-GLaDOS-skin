// SPDX-License-Identifier: MIT
package glados.kiosk.skin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;

/**
 * The HUD frame around the eye: Aperture bracket corners, faint diagnostic
 * scan lines that drift, tick marks around the eye's stage and the Aperture
 * Laboratories wordmark. The brackets pulse gently with the voice. It reads
 * the voice from {@link GladosSkin} and animates itself while attached.
 */
final class GladosHud extends View {
    private static final int STEEL = 0x9BB7C4;
    private static final int AMBER = 0xFFC141;
    /** Bracket thickness as a fraction of the shorter side. */
    private static final float BRACKET = 0.020f;
    /** Bracket arm length as a fraction of the shorter side. */
    private static final float ARM = 0.075f;
    private final GladosSkin skin;
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final long origin = SystemClock.uptimeMillis();

    GladosHud(Context context, GladosSkin skin) {
        super(context);
        this.skin = skin;
        line.setStyle(Paint.Style.STROKE);
        tick.setStyle(Paint.Style.STROKE);
    }

    private static int argb(int rgb, float alpha) {
        return Color.argb(Math.round(255 * alpha), (rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255);
    }

    @Override protected void onDraw(Canvas canvas) {
        int width = getWidth(), height = getHeight();
        if (width <= 0 || height <= 0) return;
        double t = (SystemClock.uptimeMillis() - origin) / 1000.0;
        int mode = skin.mode();
        float pulse = skin.reactive() ? Math.max(0f, Math.min(1f, skin.level())) : 0f;
        float side = Math.min(width, height);

        // Drifting scan lines across the whole stage, faint.
        line.setStrokeWidth(1f);
        line.setColor(argb(STEEL, 0.05f));
        float drift = (float) ((t * 0.05) % 1.0);
        for (float y = 0; y < height; y += side * 0.05f) {
            float shifted = y + drift * side * 0.05f;
            if (shifted < height) canvas.drawLine(0, shifted, width, shifted, line);
        }

        // Aperture bracket corners, eased outward with the voice.
        float inset = side * (0.035f + 0.012f * pulse) + 4f;
        float thickness = side * BRACKET;
        float arm = side * ARM;
        line.setStrokeWidth(thickness);
        line.setColor(argb(STEEL, 0.55f + 0.25f * pulse));
        drawBracket(canvas, inset, inset, 1, 1, thickness, arm);
        drawBracket(canvas, width - inset, inset, -1, 1, thickness, arm);
        drawBracket(canvas, inset, height - inset, 1, -1, thickness, arm);
        drawBracket(canvas, width - inset, height - inset, -1, -1, thickness, arm);

        // Tick marks orbiting the stage, a clock of the turn's progress.
        float cx = width / 2f, cy = height / 2f;
        float radius = Math.min(cx, cy) * 0.99f - thickness;
        tick.setStrokeWidth(Math.max(1f, thickness * 0.4f));
        for (int i = 0; i < 24; i++) {
            double angle = Math.PI * 2 * i / 24 + t * 0.4;
            boolean hot = i == (int) (t * 2) % 24;
            tick.setColor(hot && mode == GladosSkin.THINKING ? argb(AMBER, 0.8f) : argb(STEEL, 0.22f));
            float inner = radius * (hot ? 0.93f : 0.96f);
            canvas.drawLine(cx + (float) Math.cos(angle) * inner, cy + (float) Math.sin(angle) * inner,
                cx + (float) Math.cos(angle) * radius, cy + (float) Math.sin(angle) * radius, tick);
        }

        if (isAttachedToWindow()) postInvalidateOnAnimation();
    }

    /** One bracket corner: two arms meeting at (x, y), directed by dx and dy. */
    private void drawBracket(Canvas canvas, float x, float y, int dx, int dy, float thickness, float arm) {
        canvas.drawLine(x, y, x + dx * arm, y, line);
        canvas.drawLine(x, y, x, y + dy * arm, line);
    }
}
