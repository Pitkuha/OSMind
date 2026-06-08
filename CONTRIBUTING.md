# Contributing to OSMind

Thanks for helping test OSMind. The current target is macOS only.

## Development Setup

Requirements:

- macOS
- JDK 17 or newer
- Maven 3.9 or newer

Build and run the smoke test:

```bash
cd osmind-java
sh scripts/smoke-test
```

Run the GUI:

```bash
cd osmind-java
sh scripts/osmind-gui
```

Run the CLI:

```bash
cd osmind-java
sh scripts/osmind-cli seed-network-demo
sh scripts/osmind-cli ask "Why did my network traffic spike?"
sh scripts/osmind-cli ask "Почему у меня резко вырос сетевой трафик?"
OSMIND_DISABLE_NOTIFICATIONS=true sh scripts/osmind-cli monitor --once
```

## Pull Requests

- Keep the Java core independent from native macOS collection details.
- Keep UI text in English.
- Russian questions should remain supported by the explainer routing layer.
- Do not introduce network calls to cloud LLMs without a clear opt-in setting.
- Keep background monitoring deterministic enough for `monitor --once` smoke tests.
- Add or update `scripts/smoke-test` coverage for user-visible behavior.
