# OSMind

OSMind is a local AI Sentinel prototype for macOS. It observes process behavior, builds behavioral profiles, detects suspicious patterns, and explains system anomalies in plain English.

The interface is English-first, but questions in Russian are supported:

```bash
sh scripts/osmind-cli ask "Why did my network traffic spike?"
sh scripts/osmind-cli ask "Почему у меня резко вырос сетевой трафик?"
```

## Current Status

OSMind is an early macOS-only prototype.

- Java is the brain: event schema, storage, behavior engine, anomaly detector, explanation layer, API, CLI, and GUI.
- The native layer target is macOS Endpoint Security. Collector source code is included under `agent-macos`.
- Demo data is synthetic and useful for testing the Java brain and UI.
- The current AI mode is local heuristic analysis plus an explanation module. A local LLM backend is planned, but no cloud AI calls are made.
- Analysis runs in two modes:
  - on demand, when the user asks a question;
  - continuously, through the background monitor, which checks for anomalies on an interval and emits alerts.

## Project Layout

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

## Requirements

- macOS
- JDK 17 or newer
- Maven 3.9 or newer

## Quick Start

Build:

```bash
mvn package
```

Run the GUI:

```bash
sh scripts/osmind-gui
```

Run the CLI:

```bash
sh scripts/osmind-cli seed-network-demo
sh scripts/osmind-cli clear-demo
sh scripts/osmind-cli ask "Why did my network traffic spike?"
sh scripts/osmind-cli ask "Почему у меня резко вырос сетевой трафик?"
```

Run the background monitor:

```bash
sh scripts/osmind-cli monitor
```

Run one monitor check without keeping a process alive:

```bash
sh scripts/osmind-cli monitor --once
```

Tune the interval and analysis window:

```bash
sh scripts/osmind-cli monitor --interval-seconds 30 --lookback-minutes 3
```

Run the smoke test:

```bash
sh scripts/smoke-test
```

Build the native macOS collector:

```bash
sh scripts/build-macos-collector
```

Check native collector readiness:

```bash
sh scripts/doctor-macos-collector
```

Sign the collector for local diagnostics:

```bash
sh scripts/sign-macos-collector
```

Run the native macOS collector:

```bash
sh scripts/run-macos-collector
```

Run this command from an interactive Terminal, because `sudo` must be able to ask for your password. The collector requires a signed binary with Apple's approved `com.apple.developer.endpoint-security.client` entitlement. Without that entitlement, macOS rejects the Endpoint Security client at runtime.

## macOS Shortcut

Create a Desktop shortcut:

```bash
sh scripts/install-macos-shortcut
```

After that, open `OSMind.command` from the Desktop.

There is also a repository-local launcher:

```bash
open launchers/OSMind.command
```

## Storage

By default, events are stored in:

```text
~/.osmind/events.jsonl
```

Override the location for testing:

```bash
OSMIND_HOME=/tmp/osmind sh scripts/osmind-cli seed-network-demo
OSMIND_STORE=/tmp/osmind/events.jsonl sh scripts/osmind-cli profile
```

## Example Answer

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

The GUI starts a background anomaly monitor automatically. It checks recent events every 30 seconds, writes alerts to the `Background Alerts` panel, and uses macOS notifications for new alerts.

Demo events are persistent by design so they can be analyzed by the same pipeline as live events. Use `Clear Demo Data` in the GUI, or `sh scripts/osmind-cli clear-demo` in the CLI, to remove synthetic demo events while keeping native collector events.

The CLI can run the same monitor:

```bash
sh scripts/osmind-cli monitor
```

Set this environment variable to suppress macOS notifications during tests:

```bash
OSMIND_DISABLE_NOTIFICATIONS=true sh scripts/osmind-cli monitor --once
```

## Packaging

To create a macOS `.app` image, use a JDK that includes `jpackage`:

```bash
sh scripts/package-macos-app
```

The output is created under `dist/`.

## GitHub Readiness Checklist

- Build command is documented.
- CLI and GUI launchers are documented.
- Smoke test exists: `scripts/smoke-test`.
- GitHub Actions workflow exists.
- License, contribution guide, and security policy are included.
- macOS-only scope is explicit.
- No cloud AI dependency is hidden from users.

## Roadmap

1. Add signing and packaging flow for the macOS Endpoint Security collector.
2. Add native network telemetry to complement Endpoint Security.
3. Add a Java ingestion server over a local Unix socket.
4. Add a local LLM backend for richer explanations.
5. Package and sign a macOS `.app` for testers.
