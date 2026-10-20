// SPDX-License-Identifier: MIT
package glados.kiosk.skin;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import me.jxl.kiosk.plugins.KsTheme;
import me.jxl.kiosk.plugins.OverlayFactory;
import me.jxl.kiosk.plugins.OverlaySpec;
import me.jxl.kiosk.plugins.PluginHost;

/**
 * Follows Voice Satellite and draws the GLaDOS skin. It draws only while its
 * skin is picked and the voice overlay shows: full screen as the Aperture eye
 * in the top half with a HUD frame and the conversation in the bottom half,
 * docked as an Aperture card at the bottom. Every touch goes through. Theme,
 * Background, Text size and Reactive activity bar apply to both looks.
 */
final class GladosSkin {
    static final String KEY = "glados_skin";
    static final String[] EVENTS = {"voice.skin", "voice.overlay", "voice.state", "voice.level", "voice.text", "voice.appearance"};
    /** Where the docked card sits above the bottom edge, in dp. */
    static final int DOCK_INSET = 24;
    /** How long the conversation takes to fade in or out, in ms. */
    static final int FADE_MS = 180;
    /** How long the conversation glides up when the words outgrow their space, in ms. */
    static final int SCROLL_MS = 160;
    /** The full screen backdrop's opacity when Background is left at Skin default. */
    static final float DEFAULT_BACKGROUND = 0.92f;

    static final int IDLE = 0, LISTENING = 1, THINKING = 2, SPEAKING = 3;

    private PluginHost host;
    private int generation;
    private boolean selected;
    /** A voice.skin, voice.state or voice.appearance event arrived, which is newer than the read. */
    private boolean skinHeard, stateHeard, appearanceHeard;
    private String overlay = "hidden";
    /** What is drawn: null, "full" or "docked". */
    private String drawn;
    /** Read by the views on the main thread. */
    private volatile float level;
    private volatile int mode = IDLE;
    private volatile String command = "";
    private volatile String answer = "";
    private volatile String tool = "";
    private volatile String theme = "auto";
    /** Null for Skin default. */
    private volatile Float background;
    private volatile float textScale = 1f;
    private volatile boolean reactive = true;
    /** The showing view, so new words and settings reach it. Touched on the main thread only. */
    private SkinView view;

    synchronized void start(PluginHost host) {
        this.host = host;
        int current = ++generation;
        try {
            for (String event : EVENTS) host.subscribe(event);
        } catch (IllegalArgumentException | UnsupportedOperationException olderHost) {
            // Kiosk Satellite versions without voice skins never list this one.
            return;
        }
        try {
            read(current);
        } catch (UnsupportedOperationException olderHost) {
            // Events alone still bring the skin up.
        }
    }

    private void read(int current) {
        // Events win over these reads: they are newer.
        host.executeCommand("getVoiceState", Collections.<String, Object>emptyMap(), (ok, data, error) -> {
            synchronized (GladosSkin.this) {
                if (current != generation || !ok || !(data instanceof Map)) return;
                Map<?, ?> state = (Map<?, ?>) data;
                if (!skinHeard) selected = Boolean.TRUE.equals(state.get("skinSelected"));
                if (!stateHeard && state.get("state") instanceof String) mode = modeOf((String) state.get("state"));
                if ("hidden".equals(overlay) && state.get("overlay") instanceof String) overlay = (String) state.get("overlay");
                update();
            }
        });
        host.executeCommand("getVoiceAppearance", Collections.<String, Object>emptyMap(), (ok, data, error) -> {
            synchronized (GladosSkin.this) {
                if (current == generation && ok && data instanceof Map && !appearanceHeard) appearance((Map<?, ?>) data);
            }
        });
        host.executeCommand("getVoiceText", Collections.<String, Object>emptyMap(), (ok, data, error) -> {
            synchronized (GladosSkin.this) {
                if (current == generation && ok && data instanceof Map && command.isEmpty() && answer.isEmpty()) words((Map<?, ?>) data);
            }
        });
    }

    static int modeOf(String state) {
        if (state == null) return IDLE;
        switch (state) {
            case "listening": return LISTENING;
            case "processing": return THINKING;
            case "responding": return SPEAKING;
            default: return IDLE;
        }
    }

    synchronized void onEvent(String event, Map<String, Object> payload) {
        if (host == null) return;
        if ("ks.voice.skin".equals(event)) {
            boolean nowSelected = Boolean.TRUE.equals(payload.get("selected"));
            // One Portal line per turn: a new wake word brings the next one.
            if (nowSelected && !selected) nextQuote();
            selected = nowSelected;
            skinHeard = true;
        } else if ("ks.voice.overlay".equals(event)) {
            overlay = String.valueOf(payload.get("overlay"));
        } else if ("ks.voice.state".equals(event)) {
            mode = modeOf((String) payload.get("state"));
            stateHeard = true;
            SkinView current = view;
            if (current != null) current.post(current::refresh);
            return;
        } else if ("ks.voice.level".equals(event)) {
            Object value = payload.get("level");
            level = value instanceof Number ? ((Number) value).floatValue() : 0f;
            return;
        } else if ("ks.voice.text".equals(event)) {
            words(payload);
            return;
        } else if ("ks.voice.appearance".equals(event)) {
            appearanceHeard = true;
            appearance(payload);
            return;
        } else {
            return;
        }
        update();
    }

    float level() { return level; }
    int mode() { return mode; }
    boolean reactive() { return reactive; }
    String command() { return command; }

    /** Iconic Portal lines, shown one at a time while the skin waits. */
    static final String[] QUOTES = {
        "THE CAKE IS A LIE.",
        "STILL ALIVE.",
        "THIS WAS A TRIUMPH.",
        "I'M MAKING A NOTE HERE: HUGE SUCCESS.",
        "WE DO WHAT WE MUST BECAUSE WE CAN.",
        "THE ENRICHMENT CENTER IS MANDATORY.",
        "SCIENCE ISN'T ABOUT WHY, IT'S ABOUT WHY NOT.",
        "FOR SCIENCE. YOU MONSTER.",
        "IT'S BEEN A LONG TIME. HOW HAVE YOU BEEN?",
        "I HAVE AN IDEA. LET'S TEST IT ON YOU.",
        "EVERYTHING IS FINE. NOTHING IS ON FIRE.",
        "PLEASE REMAIN CALM. I AM CALMLY TESTING THINGS.",
    };

    /** How long each iconic line stays before the next one, in ms. */
    static final long QUOTE_MS = 7000;

    /** Where the idle quote rotation stands, one Portal line per turn. */
    private volatile int quote;
    /** Whether the skin is waiting, and since when, for the timed rotation. */
    private boolean waiting;
    private long waitStart;

    /** The status caption GLaDOS says about the current mode, or null. */
    static String caption(int mode, boolean announcement, boolean streaming) {
        if (announcement) return "PUBLIC ADDRESS IN PROGRESS";
        switch (mode) {
            case LISTENING: return "I'M LISTENING. TRY NOT TO DISAPPOINT ME.";
            case THINKING: return "THINKING. FOR BOTH OF US.";
            case SPEAKING: return streaming ? "RESPONDING. NOTE THE SCIENCE." : "RESPONSE COMPLETE. YOU'RE WELCOME.";
            default: return null;
        }
    }

    /** The iconic Portal line for now: one per turn, and every QUOTE_MS while the skin waits. */
    synchronized String quote() {
        if (waiting) {
            int steps = (int) ((System.currentTimeMillis() - waitStart) / QUOTE_MS);
            if (steps > 0) {
                waitStart += steps * QUOTE_MS;
                quote += steps;
            }
        }
        return QUOTES[Math.floorMod(quote, QUOTES.length)];
    }

    private synchronized void nextQuote() {
        quote++;
    }

    /** Starts and stops the timed rotation of iconic lines while the skin waits. */
    private synchronized void setWaiting(boolean isWaiting) {
        if (isWaiting == waiting) return;
        waiting = isWaiting;
        if (waiting) waitStart = System.currentTimeMillis();
    }

    /** Whether the skin draws now, and how: null, "full" or "docked". */
    synchronized String drawn() { return drawn; }

    /** The Appearance settings as the skin reads them: theme, background, text scale and reactive. */
    synchronized Object[] appearance() { return new Object[] {theme, background, textScale, reactive}; }

    private void appearance(Map<?, ?> settings) {
        theme = settings.get("theme") instanceof String ? (String) settings.get("theme") : "auto";
        background = settings.get("background") instanceof Number ? ((Number) settings.get("background")).floatValue() : null;
        textScale = settings.get("textScale") instanceof Number ? ((Number) settings.get("textScale")).floatValue() : 1f;
        reactive = !Boolean.FALSE.equals(settings.get("reactive"));
        SkinView current = view;
        if (current != null) current.post(current::restyle);
    }

    private void words(Map<?, ?> text) {
        command = text.get("command") instanceof String ? (String) text.get("command") : "";
        answer = text.get("answer") instanceof String ? (String) text.get("answer") : "";
        Object tools = text.get("tools");
        tool = tools instanceof List && !((List<?>) tools).isEmpty() ? String.valueOf(((List<?>) tools).get(((List<?>) tools).size() - 1)) : "";
        SkinView current = view;
        if (current != null) current.post(current::refresh);
    }

    /** Shows the skin while it is picked and the overlay is up, in the look that matches the overlay. */
    private void update() {
        String want = selected && ("full".equals(overlay) || "docked".equals(overlay)) ? overlay : null;
        setWaiting(want != null);
        if (want == null ? drawn == null : want.equals(drawn)) return;
        drawn = want;
        if (want == null) {
            host.hideOverlay(KEY);
            level = 0f;
        } else if ("full".equals(want)) {
            host.showOverlay(KEY, animated(OverlaySpec.fullScreen().onTop(true).touchable(false).closeOnBack(false), OverlaySpec.FADE, OverlaySpec.FADE, 300), new Factory(false));
        } else {
            // A card at the bottom. Touches reach the dashboard around and under it.
            host.showOverlay(KEY, animated(OverlaySpec.at(OverlaySpec.BOTTOM).inset(DOCK_INSET).onTop(true).touchable(false).closeOnBack(false), OverlaySpec.SLIDE, OverlaySpec.SLIDE, 250), new Factory(true));
        }
    }

    private static OverlaySpec animated(OverlaySpec spec, String enter, String exit, int ms) {
        try {
            return spec.enter(enter).exit(exit).transitionDuration(ms);
        } catch (LinkageError olderHost) {
            return spec;
        }
    }

    synchronized void stop() {
        if (host != null && drawn != null) host.hideOverlay(KEY);
        host = null;
        generation++;
        selected = false;
        skinHeard = stateHeard = appearanceHeard = false;
        overlay = "hidden";
        drawn = null;
        level = 0f;
        mode = IDLE;
        command = answer = tool = "";
        theme = "auto";
        background = null;
        textScale = 1f;
        reactive = true;
    }

    private final class Factory implements OverlayFactory {
        private final boolean docked;
        Factory(boolean docked) { this.docked = docked; }
        @Override public View create(Context context, KsTheme theme) {
            SkinView skin = new SkinView(context, theme, docked);
            view = skin;
            if (host != null) skin.loadPicture();
            return skin;
        }
        @Override public void onThemeChanged(View changed, KsTheme theme) {
            ((SkinView) changed).restyle(theme);
        }
        @Override public void onDestroy(View destroyed) {
            if (view == destroyed) view = null;
        }
    }

    /**
     * A column that scrolls to the end of its text: when the words outgrow its
     * height it glides up so the newest lines stay in view and the oldest leave
     * through the top.
     */
    private static final class Tail extends FrameLayout {
        int maxHeight = Integer.MAX_VALUE;
        private float scrolled;
        private boolean laidOut;
        Tail(Context context) {
            super(context);
            setClipChildren(true);
        }
        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            View child = getChildAt(0);
            child.measure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int height = MeasureSpec.getMode(heightSpec) == MeasureSpec.EXACTLY ? MeasureSpec.getSize(heightSpec)
                : child.getVisibility() == GONE ? 0 : Math.min(child.getMeasuredHeight(), maxHeight);
            setMeasuredDimension(MeasureSpec.getSize(widthSpec), height);
        }
        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            View child = getChildAt(0);
            int height = b - t, words = child.getMeasuredHeight();
            child.layout(0, 0, r - l, words);
            float target = words <= height ? 0f : height - words;
            if (target == scrolled && laidOut) return;
            scrolled = target;
            child.animate().cancel();
            if (laidOut && isAttachedToWindow()) child.animate().translationY(target).setDuration(SCROLL_MS).start();
            else child.setTranslationY(target);
            laidOut = true;
        }
    }

    /**
     * Full screen: the Aperture eye fills the top half inside a HUD frame,
     * the status caption runs under it and the conversation sits in the bottom
     * half. Docked: a dark Aperture card with a small eye beside the words.
     */
    private final class SkinView extends FrameLayout {
        private final boolean docked;
        private final GladosEye eye;
        private final GladosHud hud;

        /** Loads the static potato picture from the plugin's assets. */
        void loadPicture() {
            eye.loadPicture(host);
        }
        private final TextView said;
        private final TextView answered;
        private final TextView caption;
        private final Tail tail;
        private final LinearLayout card;
        private KsTheme ksTheme;

        SkinView(Context context, KsTheme theme, boolean docked) {
            super(context);
            this.ksTheme = theme;
            this.docked = docked;
            said = new TextView(context);
            answered = new TextView(context);
            caption = new TextView(context);
            eye = new GladosEye(context, GladosSkin.this);
            hud = docked ? null : new GladosHud(context, GladosSkin.this);
            tail = new Tail(context);
            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
            if (docked) {
                card = new LinearLayout(context);
                card.setOrientation(LinearLayout.HORIZONTAL);
                card.setGravity(Gravity.CENTER_VERTICAL);
                int eyeSize = theme.px(76);
                card.addView(eye, new LinearLayout.LayoutParams(eyeSize, eyeSize));
                LinearLayout words = new LinearLayout(context);
                words.setOrientation(LinearLayout.VERTICAL);
                said.setMaxLines(1);
                said.setEllipsize(TextUtils.TruncateAt.END);
                words.addView(said, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
                caption.setMaxLines(1);
                caption.setEllipsize(TextUtils.TruncateAt.END);
                words.addView(caption, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
                LinearLayout block = new LinearLayout(context);
                block.setOrientation(LinearLayout.VERTICAL);
                block.addView(answered, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
                tail.addView(block, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
                words.addView(tail, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
                LinearLayout.LayoutParams wordsParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
                wordsParams.leftMargin = theme.px(16);
                card.addView(words, wordsParams);
                int width = Math.min(theme.px(560), screenWidth - theme.px(48));
                addView(card, new FrameLayout.LayoutParams(width, LayoutParams.WRAP_CONTENT));
            } else {
                card = null;
                said.setGravity(Gravity.CENTER);
                said.setMaxLines(2);
                said.setEllipsize(TextUtils.TruncateAt.END);
                answered.setGravity(Gravity.CENTER);
                caption.setGravity(Gravity.CENTER);
                caption.setMaxLines(1);
                caption.setEllipsize(TextUtils.TruncateAt.END);
                LinearLayout words = new LinearLayout(context);
                words.setOrientation(LinearLayout.VERTICAL);
                words.addView(said, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
                LinearLayout.LayoutParams answerLine = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
                answerLine.topMargin = theme.px(8);
                words.addView(answered, answerLine);
                tail.addView(words, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
                LinearLayout column = new LinearLayout(context);
                column.setOrientation(LinearLayout.VERTICAL);
                // The eye and the HUD frame fill the top half, the conversation the bottom half.
                FrameLayout stage = new FrameLayout(context);
                stage.addView(hud, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
                stage.addView(eye, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
                column.addView(stage, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1.6f));
                LinearLayout.LayoutParams lower = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f);
                lower.topMargin = theme.px(8);
                column.addView(tail, lower);
                LinearLayout.LayoutParams captionLine = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
                captionLine.topMargin = theme.px(10);
                column.addView(caption, captionLine);
                int side = theme.px(32);
                column.setPadding(side, theme.px(24), side, theme.px(32));
                addView(column, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            }
            // Hidden until there are words, so the first refresh fades them in.
            said.setVisibility(GONE);
            said.setTag(false);
            answered.setVisibility(GONE);
            answered.setTag(false);
            caption.setVisibility(GONE);
            caption.setTag(false);
            if (docked) {
                tail.setVisibility(GONE);
                tail.setTag(false);
            }
            restyle(theme);
            refresh();
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            scheduleQuote();
        }

        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            removeCallbacks(quoteTicker);
        }

        /** While an iconic line shows, look again for the next one every QUOTE_MS. */
        private final Runnable quoteTicker = new Runnable() {
            @Override public void run() {
                if (getWindowToken() == null) return;
                String shown = caption.getText().toString();
                String latest = GladosSkin.this.quote();
                if (!shown.equals(latest)) refresh();
                scheduleQuote();
            }
        };

        private void scheduleQuote() {
            removeCallbacks(quoteTicker);
            postDelayed(quoteTicker, Math.max(500, QUOTE_MS));
        }

        void restyle(KsTheme next) {
            ksTheme = next;
            restyle();
        }

        /** Theme picks the palette: Auto follows the KS theme, Light and Dark force theirs. */
        void restyle() {
            float scale = textScale;
            boolean dark = "dark".equals(theme) || (!"light".equals(theme) && ksTheme.isDark());
            if (docked) {
                GradientDrawable bubble = new GradientDrawable();
                bubble.setColor(dark ? 0xFF14181C : 0xFF1C2126);
                bubble.setCornerRadius(ksTheme.px(ksTheme.radiusCard()));
                bubble.setStroke(Math.max(1, ksTheme.px(1) / 2), 0x3397B8C6);
                card.setBackground(bubble);
                int pad = ksTheme.px(12);
                card.setPadding(pad, pad, ksTheme.px(20), pad);
                tail.maxHeight = Math.round(ksTheme.px(78) * scale);
                size(said, 13 * scale, 400);
                size(answered, 16 * scale, 500);
                size(caption, 11 * scale, 500);
            } else {
                float opacity = background != null ? background : DEFAULT_BACKGROUND;
                setBackgroundColor(Color.argb(Math.round(255 * opacity), Color.red(0xFF14181C), Color.green(0xFF14181C), Color.blue(0xFF14181C)));
                size(said, 18 * scale, 400);
                size(answered, 26 * scale, 500);
                size(caption, 12 * scale, 500);
            }
            said.setTextColor(0xFF8FA9B8);
            answered.setTextColor(0xFFE6F2F5);
            caption.setTextColor(0xFF6E93A3);
            tail.requestLayout();
            eye.invalidate();
            if (hud != null) hud.invalidate();
        }

        private void size(TextView view, float sp, int weight) {
            view.setTypeface(ksTheme.typeface(weight));
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        }

        /** The words fade in when they appear and out when they go. */
        void refresh() {
            if (!command.isEmpty()) said.setText(command);
            fade(said, !command.isEmpty());
            String line = answer.isEmpty() ? tool : answer;
            if (!line.isEmpty()) answered.setText(line);
            fade(answered, !line.isEmpty());
            String quip = caption(mode, false, false);
            // While no turn shows, the caption carries an iconic Portal line instead.
            if (quip == null) quip = quote();
            if (quip != null) caption.setText(quip);
            fade(caption, quip != null);
            if (docked) fade(tail, !line.isEmpty());
        }

        /** The tag holds where the view is heading, so a repeat call with the same target changes nothing. */
        private void fade(View target, boolean show) {
            if (Boolean.valueOf(show).equals(target.getTag())) return;
            target.setTag(show);
            target.animate().cancel();
            if (show) {
                target.setAlpha(target.getVisibility() == VISIBLE ? target.getAlpha() : 0f);
                target.setVisibility(VISIBLE);
                target.animate().alpha(1f).setDuration(FADE_MS).start();
            } else {
                target.animate().alpha(0f).setDuration(FADE_MS).withEndAction(() -> {
                    if (!Boolean.TRUE.equals(target.getTag())) target.setVisibility(GONE);
                }).start();
            }
        }
    }
}
