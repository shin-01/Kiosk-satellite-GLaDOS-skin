// SPDX-License-Identifier: MIT
package glados.kiosk.skin;

import java.util.LinkedHashMap;
import java.util.Map;

import me.jxl.kiosk.plugins.KioskPlugin;
import me.jxl.kiosk.plugins.PluginHost;

/**
 * The GLaDOS voice skin and screensaver. The skin has no settings: Voice
 * Satellite's Appearance settings drive it. The screensaver shows the same
 * potato with its breathing eye and rotating Portal lines.
 */
public final class GladosPlugin implements KioskPlugin {
    static final String SCREENSAVER_KEY = "glados";

    private final GladosSkin skin = new GladosSkin();
    private PluginHost host;

    @Override public void start(PluginHost host, Map<String, Object> settings) {
        this.host = host;
        host.log("GLaDOS skin started");
        skin.start(host);
        publishScreensaver(host, settings);
    }

    /** Registers the potato screensaver so it shows in the mode picker and schedules. */
    private void publishScreensaver(PluginHost host, Map<String, Object> settings) {
        try {
            Map<String, Object> options = new LinkedHashMap<>();
            options.put("quotes", !Boolean.FALSE.equals(settings.get("screensaverQuotes")));
            host.publishScreensaverAsset(SCREENSAVER_KEY, "GLaDOS", "glados/index.html", options);
        } catch (UnsupportedOperationException unavailable) {
            host.log("GLaDOS screensaver unavailable on this Kiosk Satellite version");
        }
    }

    @Override public void configure(Map<String, Object> settings) {
        // Publishing new options recreates the document, so the change applies at once.
        if (host != null) publishScreensaver(host, settings);
    }

    @Override public void execute(String command, Map<String, Object> arguments) {}

    @Override public void onEvent(String event, Map<String, Object> payload) {
        skin.onEvent(event, payload);
    }

    @Override public void stop() {
        skin.stop();
    }
}
