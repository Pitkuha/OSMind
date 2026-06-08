package dev.osmind.api;

import java.io.IOException;
import java.util.Locale;

public final class MacOsNotificationNotifier implements AlertNotifier {
    private final AlertNotifier fallback;

    public MacOsNotificationNotifier(AlertNotifier fallback) {
        this.fallback = fallback;
    }

    @Override
    public void onAlert(Alert alert) {
        if (!isMacOs()) {
            fallback.onAlert(alert);
            return;
        }
        try {
            new ProcessBuilder(
                    "osascript",
                    "-e",
                    "display notification " + appleScriptString(alert.message())
                            + " with title " + appleScriptString("OSMind " + alert.severity())
                            + " subtitle " + appleScriptString(alert.title())
            ).start();
        } catch (IOException exception) {
            fallback.onAlert(alert);
        }
    }

    private boolean isMacOs() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }

    private String appleScriptString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
