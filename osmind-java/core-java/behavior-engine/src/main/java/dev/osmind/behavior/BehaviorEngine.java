package dev.osmind.behavior;

import dev.osmind.schema.EventType;
import dev.osmind.schema.OsEvent;
import dev.osmind.schema.ProcessIdentity;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BehaviorEngine {
    public List<ProcessBehaviorProfile> buildProfiles(List<OsEvent> events) {
        Map<Long, MutableProfile> profiles = new HashMap<>();
        for (OsEvent event : events) {
            profiles.computeIfAbsent(event.process().pid(), ignored -> new MutableProfile(event.process()))
                    .accept(event);

            if (event.type() == EventType.EXEC && event.process().ppid() > 0) {
                profiles.computeIfAbsent(event.process().ppid(), pid -> new MutableProfile(ProcessIdentity.unknown(pid)))
                        .childProcesses.add(event.process().processName());
            }
        }
        return profiles.values().stream()
                .map(MutableProfile::freeze)
                .sorted(Comparator.comparing(profile -> profile.process().processName()))
                .toList();
    }

    private static final class MutableProfile {
        private ProcessIdentity process;
        private Instant firstSeen;
        private Instant lastSeen;
        private long execCount;
        private long openCount;
        private long readCount;
        private long writeCount;
        private long chmodCount;
        private long unlinkCount;
        private long connectCount;
        private long acceptCount;
        private long bytesWritten;
        private final Set<String> fileTargets = new HashSet<>();
        private final Set<String> networkTargets = new HashSet<>();
        private final Set<String> childProcesses = new HashSet<>();

        private MutableProfile(ProcessIdentity process) {
            this.process = process;
        }

        private void accept(OsEvent event) {
            if (!"unknown".equals(event.process().processName())) {
                process = event.process();
            }
            firstSeen = firstSeen == null || event.timestamp().isBefore(firstSeen) ? event.timestamp() : firstSeen;
            lastSeen = lastSeen == null || event.timestamp().isAfter(lastSeen) ? event.timestamp() : lastSeen;

            switch (event.type()) {
                case EXEC -> execCount++;
                case OPEN -> {
                    openCount++;
                    fileTargets.add(event.target());
                }
                case READ -> {
                    readCount++;
                    fileTargets.add(event.target());
                }
                case WRITE -> {
                    writeCount++;
                    bytesWritten += Math.max(0, event.bytes());
                    fileTargets.add(event.target());
                }
                case CHMOD -> {
                    chmodCount++;
                    fileTargets.add(event.target());
                }
                case UNLINK -> {
                    unlinkCount++;
                    fileTargets.add(event.target());
                }
                case CONNECT -> {
                    connectCount++;
                    networkTargets.add(event.target());
                }
                case ACCEPT -> {
                    acceptCount++;
                    networkTargets.add(event.target());
                }
                case PROCESS_EXIT, METRIC -> {
                }
            }
        }

        private ProcessBehaviorProfile freeze() {
            return new ProcessBehaviorProfile(
                    process,
                    firstSeen,
                    lastSeen,
                    execCount,
                    openCount,
                    readCount,
                    writeCount,
                    chmodCount,
                    unlinkCount,
                    connectCount,
                    acceptCount,
                    bytesWritten,
                    Set.copyOf(fileTargets),
                    Set.copyOf(networkTargets),
                    Set.copyOf(childProcesses)
            );
        }
    }
}
