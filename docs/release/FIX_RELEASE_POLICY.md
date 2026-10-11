# Client Fix Releases

Owner decision: 2026-10-11.

Changes to shipped client behavior or packaged client code require a new client
release version after verification. Never replace a published JAR, archived
artifact or existing release tag with different bytes under the same version.

Bug fixes against 1.8.0 belong in **1.8.1**. The NeoForge standalone CUI fallback
direction fix is one such change. Keep its release notes separate from the
unreleased server bridge; do not ship experimental bridge transports merely to
deliver a client regression fix.

Server-companion-only changes have their own version. Documentation and test-only
changes do not require a client release when they leave client JARs unchanged.
Development candidates remain explicitly named `1.8.0-bridge-dev`; their test
results are not a published 1.8.1 release or authorization to overwrite 1.8.0.
