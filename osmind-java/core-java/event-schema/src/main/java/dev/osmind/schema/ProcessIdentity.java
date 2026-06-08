package dev.osmind.schema;

public record ProcessIdentity(
        long pid,
        long ppid,
        String processName,
        String executablePath,
        String workingDirectory,
        String commandLine
) {
    public static ProcessIdentity unknown(long pid) {
        return new ProcessIdentity(pid, -1, "unknown", "", "", "");
    }
}
