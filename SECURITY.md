# Security Policy

OSMind is an early local security prototype. It should not be treated as production malware protection yet.

## Current Scope

- macOS only
- local event storage
- heuristic detection and local explanations
- no cloud AI calls
- synthetic demo events until the macOS Endpoint Security collector is implemented

## Reporting Issues

Please open a GitHub issue for non-sensitive bugs. For sensitive security reports, use a private disclosure channel once the repository has one configured.

## Safety Notes

- The current demo data is synthetic.
- The native macOS Endpoint Security collector is not implemented yet.
- Recommendations such as freezing a PID are explanatory and should be reviewed by a human before action.
