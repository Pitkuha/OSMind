# OSMind macOS Native Agent

Prototype target: Endpoint Security collector.

Expected events:
- `ES_EVENT_TYPE_NOTIFY_EXEC`
- file open/read/write style notifications available through Endpoint Security and related metadata
- `unlink`
- `chmod`
- network/process correlation through supplemental user-space probes until a deeper network source is added

This is the only active native target right now. Linux eBPF and Windows ETW are intentionally out of scope until the macOS module is useful end to end.

This agent is experimental because macOS low-level telemetry requires entitlements and careful user consent. The Java core can already run with demo or synthetic events while the native collector is being built.
