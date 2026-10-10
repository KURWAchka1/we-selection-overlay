# Codebase Knowledge Graph

This repository uses codebase-memory-mcp to maintain a knowledge graph of the codebase. Index the repository (or the affected runtime project) before graph queries if it has not been indexed yet. Re-index after source changes when graph results are stale.

For code discovery, prefer:

1. `search_graph` to find functions, classes, routes, and variables.
2. `trace_path` to inspect callers and callees.
3. `get_code_snippet` to read a specific symbol.
4. `query_graph` for complex relationships.
5. `get_architecture` for a high-level summary.

Fall back to text/file search for string literals, error messages, config values, and non-code files, or when graph tools return insufficient results.

Before implementing or changing the mod's GUI screens, read `UI_REDESIGN_RESEARCH.md`.
It records the user's compact-settings/full-screen-inspector requirements,
reference research, animation constraints, and cross-version verification gates.

## Non-Disruptive Native Tests

Run automated Minecraft clients through `tools/test-command-matrix.ps1` or
`tools/test-ui-matrix.ps1` (or `tools/test-ui-layout.ps1` for layout checks).
They require a private Win32 desktop AND the test-only
cursor isolation agent. A separate desktop alone does not prevent global cursor
confinement. Never bypass their launch guards, activate a test window on the
user's desktop, switch desktops, or inject system keyboard/mouse input.
The fixture dispatches synthetic input inside Minecraft instead. The interactive
`tools/manual-test.ps1` launcher is for an explicitly requested manual playtest.
