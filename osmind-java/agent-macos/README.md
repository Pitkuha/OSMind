# OSMind macOS Native Agent

This directory contains the native macOS Endpoint Security collector for OSMind.

The collector writes normalized events into the same JSONL storage used by the Java core. The Java GUI, CLI, and background monitor can then analyze those events without knowing whether they came from demo data or the native macOS layer.

## Collected Events

Implemented Endpoint Security subscriptions:

- `ES_EVENT_TYPE_NOTIFY_EXEC` -> `EXEC`
- `ES_EVENT_TYPE_NOTIFY_OPEN` -> `OPEN`
- `ES_EVENT_TYPE_NOTIFY_WRITE` -> `WRITE`
- `ES_EVENT_TYPE_NOTIFY_UNLINK` -> `UNLINK`
- `ES_EVENT_TYPE_NOTIFY_SETMODE` -> `CHMOD`

Endpoint Security does not provide general TCP connection events. Network telemetry will need a supplemental macOS source in a later iteration.

This is the only active native target right now. Linux eBPF and Windows ETW are intentionally out of scope until the macOS module is useful end to end.

## Build

```bash
cd osmind-java
sh scripts/build-macos-collector
```

The build uses `EndpointSecurity.framework`, so Xcode or Command Line Tools must provide a macOS SDK containing that framework.

## Run

```bash
cd osmind-java
sh scripts/run-macos-collector
```

The run script uses `sudo` because Endpoint Security clients require elevated privileges.

## Storage

The collector writes to:

1. `OSMIND_STORE`, when set;
2. `OSMIND_HOME/events.jsonl`, when set;
3. `~/.osmind/events.jsonl`, by default.

Example:

```bash
OSMIND_HOME=/tmp/osmind sh scripts/run-macos-collector
```

Then, in another terminal:

```bash
OSMIND_HOME=/tmp/osmind sh scripts/osmind-cli monitor
```

## Entitlements

Apple requires the `com.apple.developer.endpoint-security.client` entitlement for real Endpoint Security clients.

This repository includes a template:

```text
agent-macos/entitlements/osmind-es-collector.entitlements
```

For real distribution, the collector must be signed with an Apple Developer identity that has Endpoint Security entitlement approval. Without that entitlement, `es_new_client` will fail even if the code builds.
