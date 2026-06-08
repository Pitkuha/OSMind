package dev.osmind.schema;

public final class NativeEventProtocol {
    public static final String VERSION = "osmind.event.v1";

    private NativeEventProtocol() {
    }

    public static String unixSocketName() {
        return "osmind-events.sock";
    }
}
