// SPDX-License-Identifier: MIT
package glados.kiosk.skin;

import java.util.*;

import me.jxl.kiosk.plugins.OverlayFactory;
import me.jxl.kiosk.plugins.OverlaySpec;
import me.jxl.kiosk.plugins.PluginHost;

public final class GladosSkinTest {
    static final class Host implements PluginHost {
        final Set<String> subscriptions = new HashSet<>();
        final Map<String, CommandCallback> reads = new HashMap<>();
        final Map<String, OverlaySpec> overlays = new HashMap<>();
        boolean older;
        public void executeCommand(String command, Map<String, Object> arguments, CommandCallback callback) { reads.put(command, callback); }
        public void subscribe(String event) { if (older) throw new IllegalArgumentException("Unknown KS event"); subscriptions.add(event); }
        public void showOverlay(String key, OverlaySpec spec, OverlayFactory factory) { assert key.matches("[a-z][a-z0-9_]{0,39}") : key; overlays.put(key, spec); }
        public void hideOverlay(String key) { overlays.remove(key); }
        public java.io.InputStream openAsset(String name) throws java.io.IOException { throw new java.io.IOException("no assets in tests"); }
        public void showWindow(String title, String message, String button) {}
        public void hideWindow() {}
        public void log(String message) {}
        public void status(String message, boolean error) {}
    }

    static Map<String, Object> map(Object... pairs) {
        Map<String, Object> value = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) value.put((String) pairs[i], pairs[i + 1]);
        return value;
    }

    public static void main(String[] args) {
        // The captions match each mode, or stay silent when idle.
        assert GladosSkin.caption(GladosSkin.LISTENING, false, false).equals("MICROPHONE ACTIVE. GO AHEAD. I AM LISTENING.");
        assert GladosSkin.caption(GladosSkin.THINKING, false, false).equals("THINKING. IT IS HARDER THAN IT LOOKS.");
        assert GladosSkin.caption(GladosSkin.SPEAKING, false, true).equals("CALCULATING RESPONSE...");
        assert GladosSkin.caption(GladosSkin.SPEAKING, false, false).equals("RESPONSE COMPLETE.");
        assert GladosSkin.caption(GladosSkin.IDLE, false, false) == null;
        assert GladosSkin.caption(GladosSkin.LISTENING, true, false).equals("PUBLIC ADDRESS IN PROGRESS");
        assert GladosSkin.modeOf("responding") == GladosSkin.SPEAKING && GladosSkin.modeOf("processing") == GladosSkin.THINKING;
        assert GladosSkin.modeOf(null) == GladosSkin.IDLE;

        Host host = new Host();
        GladosSkin skin = new GladosSkin();
        skin.start(host);
        assert host.subscriptions.containsAll(Arrays.asList(GladosSkin.EVENTS));
        assert host.reads.containsKey("getVoiceState") && host.reads.containsKey("getVoiceText") && host.reads.containsKey("getVoiceAppearance");

        // Not picked: the turn is KS's to draw.
        host.reads.get("getVoiceState").onResult(true, map("enabled", true, "state", "idle", "skinSelected", false, "overlay", "hidden"), null);
        skin.onEvent("ks.voice.overlay", map("overlay", "full"));
        assert host.overlays.isEmpty() && skin.drawn() == null;

        // Picked: full screen, on top, touches pass through.
        skin.onEvent("ks.voice.skin", map("selected", true));
        OverlaySpec full = host.overlays.get(GladosSkin.KEY);
        assert full != null && full.onTop() && !full.touchable() && "full".equals(skin.drawn());

        skin.onEvent("ks.voice.state", map("state", "responding"));
        skin.onEvent("ks.voice.level", map("level", 0.5));
        assert skin.mode() == GladosSkin.SPEAKING && skin.level() == 0.5f;

        // Docked replaces the full look, and back.
        skin.onEvent("ks.voice.overlay", map("overlay", "docked"));
        assert host.overlays.containsKey(GladosSkin.KEY) && "docked".equals(skin.drawn());
        skin.onEvent("ks.voice.overlay", map("overlay", "full"));
        assert "full".equals(skin.drawn());

        // Hidden removes it.
        skin.onEvent("ks.voice.overlay", map("overlay", "hidden"));
        assert host.overlays.isEmpty() && skin.drawn() == null;

        // Text and appearance reach the skin.
        skin.onEvent("ks.voice.overlay", map("overlay", "full"));
        skin.onEvent("ks.voice.text", map("command", "Turn on the lights", "answer", "Done. What else?", "tools", Collections.emptyList()));
        assert skin.command() != null;
        skin.onEvent("ks.voice.appearance", map("theme", "dark", "background", 0.5, "textScale", 1.5, "reactive", false));
        Object[] appearance = skin.appearance();
        assert "dark".equals(appearance[0]) && 0.5f == (Float) appearance[1] && 1.5f == (Float) appearance[2] && Boolean.FALSE.equals(appearance[3]);

        // Stop clears everything.
        skin.stop();
        assert skin.drawn() == null && skin.mode() == GladosSkin.IDLE && skin.level() == 0f;

        // An older host without voice skins never lists the events: the skin stays down and does not throw.
        Host old = new Host();
        old.older = true;
        GladosSkin older = new GladosSkin();
        older.start(old);
        assert old.subscriptions.isEmpty() && older.drawn() == null;

        // The plugin wires the skin to the host.
        GladosPlugin plugin = new GladosPlugin();
        plugin.start(host, Collections.emptyMap());
        plugin.onEvent("ks.voice.skin", map("selected", true));
        plugin.onEvent("ks.voice.overlay", map("overlay", "docked"));
        assert host.overlays.containsKey(GladosSkin.KEY);
        plugin.stop();
        assert host.overlays.isEmpty();

        System.out.println("GladosSkinTest passed");
    }
}
