package dev.osmind.api;

import dev.osmind.schema.EventType;
import dev.osmind.schema.OsEvent;
import dev.osmind.schema.ProcessIdentity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.Map;

public final class MacOsSnapshotCollector {
    private final SentinelService sentinel;

    public MacOsSnapshotCollector(SentinelService sentinel) {
        this.sentinel = sentinel;
    }

    public int collectOnce() {
        try {
            return collectWithProcessHandle();
        } catch (RuntimeException exception) {
            try {
                return collectWithPs();
            } catch (RuntimeException fallbackException) {
                return collectSelfSnapshot();
            }
        }
    }

    private int collectWithProcessHandle() {
        Instant now = Instant.now();
        int count = 0;
        for (ProcessHandle handle : ProcessHandle.allProcesses().toList()) {
            ProcessHandle.Info info = handle.info();
            String command = info.command().orElse("");
            String commandLine = info.commandLine().orElse(command);
            String name = processName(command, commandLine);
            long ppid = handle.parent().map(ProcessHandle::pid).orElse(0L);
            sentinel.ingest(new OsEvent(
                    now,
                    "macos-userspace-snapshot",
                    EventType.METRIC,
                    new ProcessIdentity(handle.pid(), ppid, name, command, "", commandLine),
                    "process-snapshot",
                    0,
                    Map.of("collector", "ProcessHandle")
            ));
            count++;
        }
        return count;
    }

    private int collectWithPs() {
        Instant now = Instant.now();
        int count = 0;
        ProcessBuilder builder = new ProcessBuilder("/bin/ps", "-axo", "pid=,ppid=,comm=,command=");
        try {
            Process process = builder.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    ProcessIdentity identity = parsePsLine(line);
                    if (identity == null) {
                        continue;
                    }
                    sentinel.ingest(new OsEvent(
                            now,
                            "macos-userspace-snapshot",
                            EventType.METRIC,
                            identity,
                            "process-snapshot",
                            0,
                            Map.of("collector", "ps")
                    ));
                    count++;
                }
            }
            process.waitFor();
            return count;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not run ps fallback collector", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while collecting process snapshot", exception);
        }
    }

    private ProcessIdentity parsePsLine(String line) {
        String trimmed = line.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        String[] parts = trimmed.split("\\s+", 4);
        if (parts.length < 3) {
            return null;
        }
        long pid = parseLong(parts[0]);
        long ppid = parseLong(parts[1]);
        String command = parts[2];
        String commandLine = parts.length >= 4 ? parts[3] : command;
        return new ProcessIdentity(pid, ppid, processName(command, commandLine), command, "", commandLine);
    }

    private int collectSelfSnapshot() {
        ProcessHandle current = ProcessHandle.current();
        ProcessHandle.Info info = current.info();
        String command = info.command().orElse("java");
        String commandLine = info.commandLine().orElse(command);
        long ppid = current.parent().map(ProcessHandle::pid).orElse(0L);
        sentinel.ingest(new OsEvent(
                Instant.now(),
                "macos-userspace-snapshot",
                EventType.METRIC,
                new ProcessIdentity(current.pid(), ppid, processName(command, commandLine), command, "", commandLine),
                "process-snapshot",
                0,
                Map.of("collector", "self", "degraded", "process enumeration was blocked")
        ));
        return 1;
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private String processName(String command, String commandLine) {
        String source = command == null || command.isBlank() ? commandLine : command;
        if (source == null || source.isBlank()) {
            return "unknown";
        }
        int slash = source.lastIndexOf('/');
        String basename = slash >= 0 ? source.substring(slash + 1) : source;
        int space = basename.indexOf(' ');
        return space >= 0 ? basename.substring(0, space) : basename;
    }
}
