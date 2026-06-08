package dev.osmind.behavior;

import dev.osmind.schema.ProcessIdentity;

import java.time.Instant;
import java.util.Set;

public record ProcessBehaviorProfile(
        ProcessIdentity process,
        Instant firstSeen,
        Instant lastSeen,
        long execCount,
        long openCount,
        long readCount,
        long writeCount,
        long chmodCount,
        long unlinkCount,
        long connectCount,
        long acceptCount,
        long bytesWritten,
        long metricCount,
        double maxCpuPercent,
        double maxMemoryPercent,
        Set<String> fileTargets,
        Set<String> networkTargets,
        Set<String> childProcesses
) {
    public long fileMutationCount() {
        return writeCount + chmodCount + unlinkCount;
    }

    public boolean hasManyDistinctNetworkTargets(int threshold) {
        return networkTargets.size() >= threshold;
    }

    public boolean hasCpuTelemetry() {
        return maxCpuPercent >= 0;
    }
}
