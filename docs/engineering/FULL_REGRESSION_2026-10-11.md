# Full Regression Cycle

Date: 2026-10-11. Starting commit: `e25ee21`.
Client candidate: `1.8.0-bridge-dev`; companion: `0.1.0-dev`.
Published 1.8.0 files/tag are not updated by this cycle.
This document records observed checks, not a claim that every modpack is supported.

## Scope And Progress

| Area | Current evidence |
| --- | --- |
| Protocol/client/server domain | 29 tests pass on Java 21 and Java 25 |
| Native payload codecs | All six pass with the real native buffer codec |
| Paper application transport | Real Paper 1.21.11-132 + official WE 7.4.2 wire proof passes after the fixes |
| Test launcher validation | 32 checks pass on PowerShell 7 and Windows PowerShell 5.1; no client launched |
| Manual launcher internals | Pure ten-target launcher suite passes; this is not a new manual gameplay run |
| Universal packaging | Fresh executions after the CUI fix: 120 Fabric + 9 NeoForge checks pass |
| Commands, editing, clipboard | All six guarded native reruns pass after the CUI fix; fresh-only texture pixel checks pass |
| Isolation, lighting, no-WorldEdit startup | All six guarded reruns pass after the CUI fix: eight toggles each, fullbright AO/flat vertices, surrounding meshes preserved, zero automatic commands |
| UI layout and screenshots | Final CUI-fixed candidate: 42 fresh 26.2 EN/RU captures at requested 2x/3x/4x pass bounds and capture-freshness checks; selected native frames visually inspected |

Use the exact loader/API/WorldEdit tuples from
[the knowledge base](JAVA_MINECRAFT_KNOWLEDGE_BASE.md), not newer dependencies.
JVMs: Eclipse Temurin 21.0.11+10-LTS and 25.0.2+10-LTS.
Fresh native targets are Fabric 1.21.11 / 26.1 / 26.2 and NeoForge 1.21.11 /
26.1.2 / 26.2. The ten-target manual-launcher unit suite checks other supported
launch plans, not fresh gameplay on every 26.1 patch release.
Each native client uses a private Win32 desktop, cursor-isolation agent, 40 FPS
cap and bounded Gradle workers. Native clients run sequentially. Headless
domain/codec checks may overlap; collected command timings are diagnostics,
not a controlled comparative FPS/latency benchmark.

## Confirmed Fixes And Hardening

- A host-side `DENIED` result now invalidates the negotiated server epoch as
  well as withholding data. The new regression failed before the fix: a later
  request with the old epoch was accepted after the host returned to an allowed
  state. It now requires renewed negotiation and a new epoch.
- The Paper host uses the supported `getPluginMeta()` API for the pinned WE
  version check instead of deprecated `getDescription()`; no support range changes.
- All guarded matrix wrappers accept an explicit `-ModVersion`. They check the
  requested JAR exists, log its path/SHA-256 and forward its version to Gradle;
  a missing candidate cannot silently fall back to published 1.8.0.
- Invalid target names fail parameter binding rather than silently running zero
  tests. Isolation runs require cursor-guard and completed-isolation markers,
  not merely a zero process exit code.
- Command texture captures must be newer than the current native launch;
  the UI-layout wrapper also requires 42 captures from that run. A negative
  stale-image check fails before pixel comparison, not after using an old image.
- All six isolation fixtures explicitly cap FPS at 40. NeoForge applies it at
  the first client tick, not before Minecraft options initialization.
- Automatic `/we cui` now waits until the server advertises the `we` command
  in its Brigadier command tree. The regression failed before the change in a
  real no-WorldEdit client: the old build sent unsupported commands into chat.
  Missing/late command trees do not consume attempts. Normal CUI hello packets
  are unchanged; server-hidden command trees can still use packet CUI or manual
  `/we cui`, but no automatic command is guessed. Native fixtures check both
  absence and the real WorldEdit command/retry/selection-stop path.

New domain coverage includes 2,048 identities / 32,768 malformed requests,
aggregate admission/storage bounds, cleanup and quota recovery, fractional
refill/capacity, backward-clock refusal, signed monotonic-time wrap, reconnect
state reset and stale-epoch rejection. These deterministic checks are not a
busy-server performance or network-throughput benchmark.

## Candidate Identity

| Artifact | Bytes | SHA-256 |
| --- | --- | --- |
| Fabric 1.8.0-bridge-dev after CUI fix | 1,656,610 | `d7fe7a8a0c28f522229d65ae745ff082ffc2c15d98fee63b71f4598a1143fc66` |
| NeoForge 1.8.0-bridge-dev after CUI fix | 1,628,774 | `a3c901fe18180f73d1690ddbc81e886c460c49cd75789a4158a705360496fc60` |
| Paper 0.1.0-dev after authorization/API fixes | 44,508 | `2d03373110e6726633e8cfbd3179d32cb6b182b42f23aec629749c7775b044b6` |

The real Paper rerun recorded 14 admitted frames, 2 malformed, 4 metadata reads,
11 replies and zero peers after disconnect. Both published client archives match
their original SHA-256 values in [the release record](../release/RELEASE_1.8.0.md).

## Reproduce

From the repository root, after building the development candidate:

```powershell
.\tools\test-bridge-packaging.ps1 -Java21 <jdk21> -Java25 <jdk25> -Offline -RerunTests
.\tools\test-server-bridge.ps1 -Java21 <jdk21> -Java25 <jdk25> -Offline
.\tools\test-harness-inputs.ps1
.\tools\test-command-matrix.ps1 -Java21 <jdk21> -Java25 <jdk25> -ModVersion 1.8.0-bridge-dev
.\tools\test-ui-matrix.ps1 -Java21 <jdk21> -Java25 <jdk25> -ModVersion 1.8.0-bridge-dev
.\tools\test-ui-layout.ps1 -Java21 <jdk21> -Java25 <jdk25> -ModVersion 1.8.0-bridge-dev
```

Paper proof uses `tools/test-paper-bridge.ps1` with the owner's existing EULA
acceptance and the exact hashed artifacts from the
[bridge implementation record](SERVER_BRIDGE_IMPLEMENTATION.md). It runs only
in the disposable loopback-bound directory and stops the server afterward.

Logs/screenshots remain ignored local artifacts under `build/` and the native
fixture profiles. Never substitute an old success marker or screenshot for a
fresh run. The helper checks execute without GUI, cursor probes or game input.

Final native evidence in this cycle:

- Commands/editing/clipboard: `build/desktop-tests/WesoTest-20881024172d43fd894e3f34487a7a23.log`.
- Isolation/CUI absence: `build/desktop-tests/WesoTest-3aa3a938bcdd48849463801a30e46827.log`.
- UI bounds and 42 fresh captures: `build/desktop-tests/WesoTest-ff7364a7a2634e7e8482ce8d737c6856.log`.
- The fresh-only texture check was also rerun for all six targets with a cutoff
  after the previous cycle, and every capture passed. Old-image rejection was
  tested independently without launching a game.

## Environment Warnings

Development launch logs include offline Realms-authentication noise and, on
26.2 development launches, OSHI/Windows Perflib-009 counter lookup failures. These
are outside WESO's stack and did not abort the native checks. No Windows
registry repair, warning suppression or account changes were performed.
Some bulk fixture setup/resource reloads also log server catch-up warnings;
these runs must not be advertised as an idle/busy-server performance baseline.

## Outstanding Coverage

Native Fabric/NeoForge -> dedicated Paper joins remain a distinct required
bridge gate; headless TCP proof and local native gameplay do not establish it.
Remote editing/clipboard/simulation providers and 26.x/mod-server hosts are
not implemented by this refinement cycle.

Sodium, Iris, Better Block Entities, LabyMod, custom resource packs, IME/narration,
physical focus/cursor restoration and busy-server profiles need their own
compatibility/workload runs. Do not call those passed based on the base-loader
fixtures. No rejected air-isolation or partial-transparency feature is reintroduced.

Next targeted passes: real native Paper joins; fluid/block-entity boundary
fixtures and third-party renderer combinations; all-shape/ruler camera views;
controlled idle/active/multi-player profiles. Existing baseline assertions and
these explicit gaps are kept separate instead of treating a green build as
coverage of every rendering path or server scenario.
