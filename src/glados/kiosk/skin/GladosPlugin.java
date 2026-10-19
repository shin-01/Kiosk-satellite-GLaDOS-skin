// SPDX-License-Identifier: MIT
package glados.kiosk.skin;

import java.util.Map;

import me.jxl.kiosk.plugins.KioskPlugin;
import me.jxl.kiosk.plugins.PluginHost;

/**
 * The GLaDOS voice skin. It has no settings: Voice Satellite's Appearance
 * settings drive it. The Aperture eye, the HUD and the captions are drawn in
 * {@link GladosSkin}, from the voice events the host reports.
 */
public final class GladosPlugin implements KioskPlugin {
    private final GladosSkin skin = new GladosSkin();

    @Override public void start(PluginHost host, Map<String, Object> settings) {
        host.log("GLaDOS skin started");
        skin.start(host);
    }

    @Override public void configure(Map<String, Object> settings) {}

    @Override public void execute(String command, Map<String, Object> arguments) {}

    @Override public void onEvent(String event, Map<String, Object> payload) {
        skin.onEvent(event, payload);
    }

    @Override public void stop() {
        skin.stop();
    }
}
