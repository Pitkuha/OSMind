# OSMind

OSMind is an early local AI Sentinel prototype for macOS. It is designed to observe process behavior, detect suspicious system activity, and explain anomalies in human-readable language.

The long-term goal is an operating-system-level agent that can answer questions such as:

> Why did my network traffic suddenly spike?

and produce an explanation like:

> During the last 3 minutes, `node` opened 42 outbound TCP connections to 28 distinct network targets. It was launched from a project directory, so this is likely a crawler, parser, test runner, or sync script rather than normal macOS system activity.

## Status

This repository is ready for early developer testing, but it is not production security software yet.

Current capabilities:

- macOS-focused Java core.
- Native macOS Endpoint Security collector source code.
- CLI and desktop GUI.
- English interface.
- Russian questions are understood by the question router.
- Synthetic macOS network demo events for testing.
- On-demand analysis with `ask`.
- Background anomaly monitoring with alerts.
- macOS notifications for detected alerts.
- GitHub Actions smoke test.

Not implemented yet:

- Signed and entitlement-approved collector distribution.
- General TCP connection telemetry from a native macOS source.
- Local LLM backend.
- Signed `.app` distribution.

## Architecture

Java is the brain of the system:

- event schema
- storage
- behavior engine
- anomaly detector
- explanation layer
- API/orchestration
- CLI
- GUI
- background monitoring

The native macOS layer stays intentionally small. Its job is to collect low-level events through Endpoint Security and write normalized events into the Java-readable event store.

Project layout:

```text
osmind-java/
  core-java/
    event-schema/
    storage/
    behavior-engine/
    anomaly-detector/
    llm-explainer/
    api/
    sentinel-cli/
    sentinel-ui/
  agent-macos/
  scripts/
  launchers/
```

## AI Mode

The current prototype uses a local heuristic analyzer plus an explanation module. It does not call a cloud LLM and does not send system data to any external service.

The `llm-explainer` module is intentionally separated so a local LLM backend can be added later without changing the CLI, GUI, storage, or native collector contracts.

## Requirements

- macOS
- JDK 17 or newer
- Maven 3.9 or newer

Check your versions:

```bash
java -version
mvn -version
```

## Quick Start

Clone and enter the repository:

```bash
git clone https://github.com/Pitkuha/OSMind.git
cd OSMind/osmind-java
```

Build:

```bash
mvn package
```

Run the GUI:

```bash
sh scripts/osmind-gui
```

Run the CLI demo:

```bash
sh scripts/osmind-cli seed-network-demo
sh scripts/osmind-cli ask "Why did my network traffic spike?"
```

Collect live process snapshots without Endpoint Security entitlement:

```bash
sh scripts/osmind-cli collect-once
sh scripts/osmind-cli profile
```

In a normal Terminal this uses Java `ProcessHandle` with a `ps` fallback. In restricted sandboxes it may degrade to a self-snapshot instead of failing.

Clear demo data:

```bash
sh scripts/osmind-cli clear-demo
```

Ask in Russian:

```bash
sh scripts/osmind-cli ask "Почему у меня резко вырос сетевой трафик?"
```

Run the smoke test:

```bash
sh scripts/smoke-test
```

Build the macOS Endpoint Security collector:

```bash
sh scripts/build-macos-collector
```

Check collector readiness:

```bash
sh scripts/doctor-macos-collector
```

Sign the collector for local diagnostics:

```bash
sh scripts/sign-macos-collector
```

Run the collector:

```bash
sh scripts/run-macos-collector
```

Run this command from an interactive Terminal, because `sudo` must be able to ask for your password. The collector requires macOS Endpoint Security entitlement approval from Apple. Without a signed binary carrying an Apple-approved `com.apple.developer.endpoint-security.client` entitlement, macOS will reject the ES client at runtime.

Install the signed ES collector as a LaunchDaemon:

```bash
sh scripts/install-macos-collector-launchd
```

Endpoint Security production checklist:

1. Join Apple Developer Program.
2. Request Endpoint Security entitlement for your Team ID.
3. Install the approved signing certificate locally.
4. Sign with `OSMIND_CODESIGN_IDENTITY="Developer ID Application: ..."` and `sh scripts/sign-macos-collector`.
5. Run `sh scripts/doctor-macos-collector`.
6. Start from interactive Terminal with `sh scripts/run-macos-collector`, or install LaunchDaemon.

## GUI

Start the GUI:

```bash
cd osmind-java
sh scripts/osmind-gui
```

The GUI includes:

- a large question field
- `Ask Sentinel`
- `Load Network Demo`
- `Collect Live Snapshot`
- `Clear Demo Data`
- `Refresh Profiles`
- answer panel
- observed process profile panel
- background alert panel

The GUI starts a background anomaly monitor automatically. It checks recent events every 30 seconds and writes new findings to the `Background Alerts` panel.

Demo events are stored in the same local event store so the analyzer can process them exactly like live events. Use `Clear Demo Data` in the GUI, or `sh scripts/osmind-cli clear-demo` in the CLI, to leave demo mode and remove synthetic demo events while keeping native collector events.

## macOS Shortcut

Create a Desktop shortcut:

```bash
cd osmind-java
sh scripts/install-macos-shortcut
```

Then open `OSMind.command` from the Desktop.

You can also run the repository-local shortcut:

```bash
open osmind-java/launchers/OSMind.command
```

If macOS blocks the command file, right-click it and choose **Open**.

## CLI Commands

Seed synthetic demo events:

```bash
sh scripts/osmind-cli seed-network-demo
```

Clear synthetic demo events:

```bash
sh scripts/osmind-cli clear-demo
```

Ask a question:

```bash
sh scripts/osmind-cli ask "Why did my network traffic spike?"
```

Print process profiles:

```bash
sh scripts/osmind-cli profile
```

Run continuous background monitoring:

```bash
sh scripts/osmind-cli monitor
```

Run one monitor check:

```bash
sh scripts/osmind-cli monitor --once
```

Tune monitoring:

```bash
sh scripts/osmind-cli monitor --interval-seconds 30 --lookback-minutes 3
```

Disable macOS notifications during tests:

```bash
OSMIND_DISABLE_NOTIFICATIONS=true sh scripts/osmind-cli monitor --once
```

## Storage

By default, OSMind stores events in:

```text
~/.osmind/events.jsonl
```

Use a temporary storage directory:

```bash
OSMIND_HOME=/tmp/osmind sh scripts/osmind-cli seed-network-demo
OSMIND_HOME=/tmp/osmind sh scripts/osmind-cli ask "Why did my network traffic spike?"
```

Use an exact storage file:

```bash
OSMIND_STORE=/tmp/osmind/events.jsonl sh scripts/osmind-cli profile
```

## Example Output

```text
Question: Почему у меня резко вырос сетевой трафик?

Short answer: A process opened many outbound connections.

Process node (PID 1842) showed a suspicious profile during the last 3 minutes.

Evidence:
- opened 42 outbound TCP connections
- contacted 28 distinct network targets
- launched from directory: /Users/example/dev/parser

Explanation: The process is actively reaching out to the network. If it was started from a project directory, it may be a crawler, parser, test runner, or sync job rather than macOS system activity.
Recommendation: If this is not an expected crawler, build, or sync task, pause the process and inspect the destinations.
AI mode: local heuristic analyzer plus explanation module. A local LLM backend is planned for the next layer.
```

## Background Alerts

OSMind can analyze events periodically and emit alerts.

In the GUI, monitoring starts automatically.

In the CLI:

```bash
sh scripts/osmind-cli monitor
```

For demo data, a monitor alert looks like:

```text
[MEDIUM] A process opened many outbound connections: node (PID 1842): A process opened many outbound connections
```

## macOS Native Collector

The collector lives in:

```text
osmind-java/agent-macos/
```

Implemented subscriptions:

- `ES_EVENT_TYPE_NOTIFY_EXEC`
- `ES_EVENT_TYPE_NOTIFY_OPEN`
- `ES_EVENT_TYPE_NOTIFY_WRITE`
- `ES_EVENT_TYPE_NOTIFY_UNLINK`
- `ES_EVENT_TYPE_NOTIFY_SETMODE`

The collector writes OSMind JSONL events to the same storage used by Java:

- `OSMIND_STORE`
- `OSMIND_HOME/events.jsonl`
- `~/.osmind/events.jsonl`

Current native gap: Endpoint Security does not provide general outbound TCP connect events, so network telemetry still needs a supplemental macOS source.

## Packaging

A `jpackage` helper exists:

```bash
cd osmind-java
sh scripts/package-macos-app
```

This requires a JDK distribution that includes `jpackage`. The generated app image is written under `osmind-java/dist/`.

## Development

Run the main validation command:

```bash
cd osmind-java
sh scripts/smoke-test
```

The smoke test:

- builds all Maven modules
- seeds synthetic network events
- asks a Russian question
- checks the English answer structure
- verifies the background monitor alert path
- verifies that the GUI jar is built

## Repository Readiness

This repository includes:

- MIT license
- contribution guide
- security policy
- GitHub Actions workflow
- smoke-test script
- CLI launcher
- GUI launcher
- macOS shortcut installer
- app packaging helper

## Security Notice

OSMind is an experimental local security tool. It should not be used as the only protection layer on a real machine.

Current limitations:

- demo events are synthetic
- Endpoint Security requires Apple-approved entitlement and signing
- native network telemetry is not implemented yet
- alerts are heuristic
- recommendations should be reviewed by a human

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

MIT. See [LICENSE](LICENSE).
