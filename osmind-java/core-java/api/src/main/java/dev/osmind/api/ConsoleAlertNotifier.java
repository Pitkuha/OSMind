package dev.osmind.api;

public final class ConsoleAlertNotifier implements AlertNotifier {
    @Override
    public void onAlert(Alert alert) {
        System.out.printf(
                "[%s] %s: %s%n",
                alert.severity(),
                alert.title(),
                alert.message()
        );
    }
}
