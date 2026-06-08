package dev.osmind.explainer;

import dev.osmind.behavior.ProcessBehaviorProfile;

import java.util.List;

record CapabilityReport(
        boolean hasProfiles,
        boolean hasCpuTelemetry,
        boolean hasNetworkEvents,
        boolean hasFileEvents,
        int profileCount,
        int cpuProfileCount
) {
    static CapabilityReport from(List<ProcessBehaviorProfile> profiles) {
        int cpuProfiles = (int) profiles.stream().filter(ProcessBehaviorProfile::hasCpuTelemetry).count();
        boolean networkEvents = profiles.stream().anyMatch(profile -> profile.connectCount() > 0 || profile.acceptCount() > 0);
        boolean fileEvents = profiles.stream().anyMatch(profile -> profile.openCount() > 0 || profile.readCount() > 0 || profile.fileMutationCount() > 0);
        return new CapabilityReport(
                !profiles.isEmpty(),
                cpuProfiles > 0,
                networkEvents,
                fileEvents,
                profiles.size(),
                cpuProfiles
        );
    }
}
