package dev.osmind.ui;

import dev.osmind.anomaly.HeuristicAnomalyDetector;
import dev.osmind.api.Alert;
import dev.osmind.api.ConsoleAlertNotifier;
import dev.osmind.api.MacOsSnapshotCollector;
import dev.osmind.api.MacOsNotificationNotifier;
import dev.osmind.api.SentinelService;
import dev.osmind.api.SentinelMonitor;
import dev.osmind.behavior.BehaviorEngine;
import dev.osmind.behavior.ProcessBehaviorProfile;
import dev.osmind.explainer.TemplateExplainer;
import dev.osmind.schema.EventType;
import dev.osmind.schema.OsEvent;
import dev.osmind.schema.ProcessIdentity;
import dev.osmind.storage.JsonlEventStore;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

public final class SentinelUi {
    private static final Duration ASK_LOOKBACK = Duration.ofMinutes(3);
    private static final Duration PROFILE_LOOKBACK = Duration.ofMinutes(10);
    private static final Duration MONITOR_INTERVAL = Duration.ofSeconds(30);

    private final SentinelService sentinel;
    private final JTextArea questionArea = new JTextArea("Why did my network traffic spike?\n\nRussian is supported too: Почему у меня резко вырос сетевой трафик?");
    private final JTextArea answerArea = new JTextArea();
    private final JTextArea profileArea = new JTextArea();
    private final JTextArea alertArea = new JTextArea();
    private final JLabel statusLabel = new JLabel();
    private SentinelMonitor monitor;

    private SentinelUi(SentinelService sentinel) {
        this.sentinel = sentinel;
    }

    public static void main(String[] args) {
        System.setProperty("apple.awt.application.name", "OSMind");
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        SentinelService service = new SentinelService(
                new JsonlEventStore(defaultStorePath()),
                new BehaviorEngine(),
                new HeuristicAnomalyDetector(),
                new TemplateExplainer()
        );
        SwingUtilities.invokeLater(() -> new SentinelUi(service).show());
    }

    private void show() {
        JFrame frame = new JFrame("OSMind Sentinel for macOS");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(1120, 760));
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                if (monitor != null) {
                    monitor.close();
                }
            }
        });

        JPanel root = new JPanel(new BorderLayout(14, 14));
        root.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        frame.setContentPane(root);

        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildWorkspace(), BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);

        wireShortcuts(root);
        refreshProfiles();
        startBackgroundMonitor();
        updateStatus("Ready. Background anomaly monitor runs every " + MONITOR_INTERVAL.toSeconds() + " seconds.");
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(12, 10));
        JLabel title = new JLabel("OSMind Sentinel");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        JLabel subtitle = new JLabel("macOS module | English UI | understands English and Russian questions | local heuristic AI mode");
        subtitle.setForeground(new Color(85, 85, 85));

        JPanel titleBlock = new JPanel(new GridLayout(2, 1, 0, 2));
        titleBlock.add(title);
        titleBlock.add(subtitle);
        header.add(titleBlock, BorderLayout.NORTH);

        questionArea.setRows(5);
        questionArea.setLineWrap(true);
        questionArea.setWrapStyleWord(true);
        questionArea.setFont(questionArea.getFont().deriveFont(15f));
        JScrollPane questionScroll = new JScrollPane(questionArea);
        questionScroll.setBorder(BorderFactory.createTitledBorder("Question"));
        header.add(questionScroll, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton askButton = new JButton("Ask Sentinel");
        JButton demoButton = new JButton("Load Network Demo");
        JButton collectButton = new JButton("Collect Live Snapshot");
        JButton clearDemoButton = new JButton("Clear Demo Data");
        JButton refreshButton = new JButton("Refresh Profiles");
        askButton.addActionListener(event -> ask());
        demoButton.addActionListener(event -> {
            seedNetworkDemo();
            refreshProfiles();
            ask();
        });
        collectButton.addActionListener(event -> collectLiveSnapshot());
        clearDemoButton.addActionListener(event -> clearDemoData());
        refreshButton.addActionListener(event -> refreshProfiles());
        actions.add(askButton);
        actions.add(demoButton);
        actions.add(collectButton);
        actions.add(clearDemoButton);
        actions.add(refreshButton);
        header.add(actions, BorderLayout.SOUTH);

        return header;
    }

    private JSplitPane buildWorkspace() {
        answerArea.setEditable(false);
        answerArea.setLineWrap(true);
        answerArea.setWrapStyleWord(true);
        answerArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));

        profileArea.setEditable(false);
        profileArea.setLineWrap(true);
        profileArea.setWrapStyleWord(true);
        profileArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        alertArea.setEditable(false);
        alertArea.setLineWrap(true);
        alertArea.setWrapStyleWord(true);
        alertArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));

        JScrollPane answerScroll = new JScrollPane(answerArea);
        answerScroll.setBorder(BorderFactory.createTitledBorder("Answer"));
        JScrollPane profileScroll = new JScrollPane(profileArea);
        profileScroll.setBorder(BorderFactory.createTitledBorder("Observed Process Profiles"));
        JScrollPane alertScroll = new JScrollPane(alertArea);
        alertScroll.setBorder(BorderFactory.createTitledBorder("Background Alerts"));

        JSplitPane topSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, answerScroll, profileScroll);
        topSplit.setResizeWeight(0.58);
        topSplit.setDividerSize(8);

        JSplitPane workspace = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topSplit, alertScroll);
        workspace.setResizeWeight(0.72);
        workspace.setDividerSize(8);
        return workspace;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        statusLabel.setForeground(new Color(75, 75, 75));
        footer.add(statusLabel, BorderLayout.CENTER);
        return footer;
    }

    private void wireShortcuts(JPanel root) {
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.META_DOWN_MASK), "ask");
        root.getActionMap().put("ask", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                ask();
            }
        });
    }

    private void ask() {
        String question = questionArea.getText().trim();
        answerArea.setText(sentinel.ask(question, ASK_LOOKBACK));
        answerArea.setCaretPosition(0);
        updateStatus("Answered question using events from the last " + ASK_LOOKBACK.toMinutes() + " minutes.");
    }

    private void refreshProfiles() {
        StringBuilder builder = new StringBuilder("Process profiles, last ")
                .append(PROFILE_LOOKBACK.toMinutes())
                .append(" minutes\n\n");
        for (ProcessBehaviorProfile profile : sentinel.profiles(PROFILE_LOOKBACK)) {
            builder.append(profile.process().processName())
                    .append(" | PID ").append(profile.process().pid())
                    .append(" | connects ").append(profile.connectCount())
                    .append(" | network targets ").append(profile.networkTargets().size())
                    .append(" | writes ").append(profile.writeCount())
                    .append('\n')
                    .append("cwd: ").append(profile.process().workingDirectory())
                    .append("\ncmd: ").append(profile.process().commandLine())
                    .append("\n\n");
        }
        profileArea.setText(builder.toString());
        profileArea.setCaretPosition(0);
        updateStatus("Profiles refreshed. Storage: " + defaultStorePath());
    }

    private void startBackgroundMonitor() {
        monitor = sentinel.createMonitor(ASK_LOOKBACK, MONITOR_INTERVAL);
        monitor.addListener(this::showAlert);
        if (!notificationsDisabled()) {
            monitor.addListener(new MacOsNotificationNotifier(new ConsoleAlertNotifier()));
        }
        monitor.start();
        monitor.checkOnce();
    }

    private void showAlert(Alert alert) {
        SwingUtilities.invokeLater(() -> {
            alertArea.append("[%s] %s%n%s%n%n".formatted(
                    alert.severity(),
                    alert.title(),
                    alert.message()
            ));
            alertArea.setCaretPosition(alertArea.getDocument().getLength());
            updateStatus("New alert: " + alert.title());
            refreshProfiles();
        });
    }

    private void seedNetworkDemo() {
        Instant now = Instant.now();
        ProcessIdentity node = new ProcessIdentity(
                1842,
                931,
                "node",
                "/opt/homebrew/bin/node",
                System.getProperty("user.home") + "/dev/parser",
                "node crawler.js"
        );
        for (int i = 0; i < 42; i++) {
            sentinel.ingest(new OsEvent(
                    now.minusSeconds(150 - i),
                    "macos-demo-agent",
                    EventType.CONNECT,
                    node,
                    "203.0.113." + (i % 28) + ":443",
                    0,
                    Map.of("protocol", "tcp")
            ));
        }
        updateStatus("Loaded macOS network demo events.");
    }

    private void clearDemoData() {
        int removed = sentinel.clearDemoEvents();
        answerArea.setText("");
        alertArea.setText("");
        refreshProfiles();
        updateStatus("Cleared " + removed + " demo events. Live and native collector events were kept.");
    }

    private void collectLiveSnapshot() {
        int collected = new MacOsSnapshotCollector(sentinel).collectOnce();
        refreshProfiles();
        updateStatus("Collected " + collected + " live process snapshot events.");
    }

    private void updateStatus(String message) {
        statusLabel.setText(message);
    }

    private static Path defaultStorePath() {
        String explicitStore = System.getenv("OSMIND_STORE");
        if (explicitStore != null && !explicitStore.isBlank()) {
            return Path.of(explicitStore);
        }
        String osmindHome = System.getenv("OSMIND_HOME");
        if (osmindHome != null && !osmindHome.isBlank()) {
            return Path.of(osmindHome, "events.jsonl");
        }
        return Path.of(System.getProperty("user.home"), ".osmind", "events.jsonl");
    }

    private static boolean notificationsDisabled() {
        return "true".equalsIgnoreCase(System.getenv("OSMIND_DISABLE_NOTIFICATIONS"));
    }
}
