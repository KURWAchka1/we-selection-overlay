# Codebase Knowledge Graph

This repository uses codebase-memory-mcp to maintain a knowledge graph of the codebase. Index the repository (or the affected runtime project) before graph queries if it has not been indexed yet. Re-index after source changes when graph results are stale.

For code discovery, prefer:

1. `search_graph` to find functions, classes, routes, and variables.
2. `trace_path` to inspect callers and callees.
3. `get_code_snippet` to read a specific symbol.
4. `query_graph` for complex relationships.
5. `get_architecture` for a high-level summary.

Fall back to text/file search for string literals, error messages, config values, and non-code files, or when graph tools return insufficient results.
