package dev.osmind.anomaly;

import dev.osmind.behavior.ProcessBehaviorProfile;
import dev.osmind.schema.Severity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class HeuristicAnomalyDetector implements AnomalyDetector {
    @Override
    public List<Anomaly> detect(List<ProcessBehaviorProfile> profiles) {
        List<Anomaly> anomalies = new ArrayList<>();
        for (ProcessBehaviorProfile profile : profiles) {
            detectRansomwareLike(profile, anomalies);
            detectDropperLike(profile, anomalies);
            detectNetworkSpike(profile, anomalies);
        }
        return anomalies;
    }

    private void detectRansomwareLike(ProcessBehaviorProfile profile, List<Anomaly> anomalies) {
        if (profile.fileTargets().size() >= 25 && profile.writeCount() >= 25 && profile.fileMutationCount() >= 30) {
            anomalies.add(new Anomaly(
                    "ransomware-like-file-mutation",
                    Severity.CRITICAL,
                    "A process is behaving like ransomware",
                    profile,
                    List.of(
                            "touched " + profile.fileTargets().size() + " distinct files",
                            "performed " + profile.writeCount() + " write operations",
                            "performed " + profile.fileMutationCount() + " mutating file operations"
                    ),
                    "Freeze PID " + profile.process().pid() + " and inspect recent file changes before terminating it."
            ));
        }
    }

    private void detectDropperLike(ProcessBehaviorProfile profile, List<Anomaly> anomalies) {
        String command = profile.process().commandLine().toLowerCase(Locale.ROOT);
        boolean scriptingRuntime = command.contains("python") || command.contains("node") || command.contains("ruby") || command.contains("bash");
        boolean downloaderChild = profile.childProcesses().stream().anyMatch(child -> child.equals("curl") || child.equals("wget"));
        boolean tmpExecutable = profile.fileTargets().stream().anyMatch(target -> target.startsWith("/tmp/") || target.contains("/var/folders/"));
        if (scriptingRuntime && downloaderChild && tmpExecutable && profile.chmodCount() > 0) {
            anomalies.add(new Anomaly(
                    "dropper-like-download-execute",
                    Severity.HIGH,
                    "A scripting runtime shows dropper behavior",
                    profile,
                    List.of(
                            profile.process().processName() + " spawned child processes " + profile.childProcesses(),
                            "wrote or modified files in a temporary directory",
                            "changed executable permissions with chmod"
                    ),
                    "Quarantine the downloaded artifact and review the parent script before allowing execution."
            ));
        }
    }

    private void detectNetworkSpike(ProcessBehaviorProfile profile, List<Anomaly> anomalies) {
        if (profile.connectCount() >= 20 && profile.hasManyDistinctNetworkTargets(10)) {
            anomalies.add(new Anomaly(
                    "network-fanout",
                    Severity.MEDIUM,
                    "A process opened many outbound connections",
                    profile,
                    List.of(
                            "opened " + profile.connectCount() + " outbound TCP connections",
                            "contacted " + profile.networkTargets().size() + " distinct network targets",
                            "launched from directory: " + profile.process().workingDirectory()
                    ),
                    "If this is not an expected crawler, build, or sync task, pause the process and inspect the destinations."
            ));
        }
    }
}
