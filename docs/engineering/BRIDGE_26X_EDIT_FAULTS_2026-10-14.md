# Exact Native Edit Network Faults

Date: **2026-10-14**. Inspected/tested baseline **545e36b** plus the accompanying
test-only changes. Companion **0.1.0-dev**, clients **1.8.0-bridge-dev**, protocol
**1.2**. Published 1.8.0 downloads/tag are not rebuilt or replaced.

## Execution Status

| Exact host | Native Fabric | Native NeoForge | Proof timestamp UTC |
| --- | --- | --- | --- |
| Paper 26.2-133 / WorldEdit 7.4.5 | Three fault cases pass | Three fault cases pass | 2026-10-14T12:04:58.645Z |
| Paper 26.1.2-74 / WorldEdit 7.4.3 | Three fault cases pass | Three fault cases pass | 2026-10-14T12:10:14.330Z |
| Paper 1.21.11-132 / WorldEdit 7.4.2 | Three fault cases pass | Three fault cases pass | 2026-10-14T12:15:09.388Z |

All **18 native client processes** and **six fresh Paper processes** pass. There
are **18 intercepted commit requests/replies**, **12 host commits/applies**,
**24 captures** including six explicit post-timeout refreshes, and **six revoked
requests with zero host apply**. Every world/undo audit passes and each final
peer/queue/task count is zero. Exact dependencies and hashes
are shared with the [tuple record](BRIDGE_26X_REMOTE_EDITING_2026-10-13.md).
Java 21 runs the legacy client/server and compiles the companion/fixture; Java
25 runs 26.x. Loader/API pins are unchanged from that record.

## Method And Assertions

Each tuple uses a fresh owned Paper JVM per loader and three actual native
client processes, without client-side WorldEdit. The controller creates a local
G resize draft from Y=70 to Y=71 and submits one real bridge transaction. All
Minecraft windows stay on the guarded private desktop with cursor isolation;
no desktop activation, OS input or manual launcher is used.

| Fault | Required independent observations |
| --- | --- |
| 400 ms one-way ordered traffic delay after native PLAY/capture | Pending native controller; acknowledgment after at least 650 ms; one capture, one commit and one apply; canonical CUI/server bounds |
| Successful commit acknowledgment retained past the ten-second client timeout, then released | Native UNKNOWN; normal CUI already updated; one explicit read refreshes state; late reply cannot restore success or repeat mutation; two captures, one commit/apply |
| Commit held before backend delivery; edit grant revoked before release | Server counters still show no new commit/apply before delivery; real permission attachment revoked; client denied/not ready; unchanged CUI/server bounds; one capture, zero host commits/applies |

Every case requires exactly one intercepted commit request and one reply, no
quiet-controller retry, ordered unique phases, world-block hash/empty WorldEdit
undo-history audit and zero final peers/pending slots/dispatcher tasks. Server
WorldEdit bounds are queried independently of native assertions. The proof is
written only after both owned servers shut down normally with exit code zero;
forced termination or a premature process exit cannot pass.

These are controlled **application faults**, not arbitrary Internet TCP loss.
Holding the commit before delivery does not establish revocation of a request
already queued inside Paper under load; pure core tests cover that separate
dispatch boundary. The timeout outcome remains uncertain even when the test
supervisor independently knows the operation applied.

## Test Harness Change

The old common fault fixture hardcoded the 1.21.11 runtime and its supervisor
selected only the legacy tuple. Both now use the same exact runtime/version
selection as the existing native lifecycle and gesture suites. The guarded
PowerShell wrappers accept `-EditFaults` on the three approved hosts, with Java
25 required for 26.x, separate versioned disposable roots and unchanged artifact
guards. Unsupported hosts remain rejected.

The pinned test-only minecraft-protocol 1.68.0 library has no 26.2 data. Instead
of aliasing another version, the separate Paper fixture discovers custom-payload
IDs through the running host's `ProtocolInfo.Details` packet registry and encodes
the prefixes with its `FriendlyByteBuf`. The supervisor validates the exact host,
canonical packet ID and bridge channel before arming the relay. See primary
research and the explicit test-only internals exception in [S57](SOURCES.md#s57-exact-native-fault-packet-discovery).
No extra library, reflection, polling or server scan enters production code.

The existing relay keeps its single connection/held-frame limits, per-direction
8 MiB input/output budgets and packet ceiling. It binds only 127.0.0.1:25579,
forwarding to the owned offline/uncompressed server on port 25580. Actual
intercepted request/reply counts must reconcile; a wrong packet prefix cannot
silently produce a passing native proof.

The independent evidence validator adds eight self-tests for missing/repeated
scenarios, reordered phases, extra mutations/captures, wrong canonical bounds,
leaks, absent revocation/late-ack evidence and malformed packet prefixes. The
complete **82 Node harness checks pass**. Additional Java/native-codec regression
coverage also passes: **95 tests on each JVM** (14 protocol, 27 client, 50 core,
four host-policy tests), all **six actual native payload codecs**, nine JFR
reader checks per JVM, agent/bytecode/package guards and twelve deterministic
admission-order cases per JVM. Every ordinary admission probe receives 60/60
replies with zero expirations. JFR reader tests are not production cost measurements.

The final raw-log reconciliation independently checks **18 client logs**,
**60 ordered server counter snapshots**, all eighteen canonical WorldEdit bounds
and world/undo audit completions, and six normal server-stop logs against the
stored proof. No native fault run failed in this cycle. Server-discovered PLAY
custom-payload IDs are 0x15 serverbound / 0x18 clientbound on 1.21.11 and 0x16 /
0x18 on both tested 26.x hosts; these observations are not hardcoded in the relay.
Both development client hashes and the companion hash remain unchanged after
regression. Published 1.8.0 hashes remain
`912a4c0c455928ec05a24fe2001f009dd68322d8aa437c2c9e82d014e5db46d0` (Fabric) and
`ea6a8e2e3d58cc1ffcb1473ddddc9036d850ae5f773ef108dab1e570eb379b1c` (NeoForge).
The earlier full local command/UI/clipboard/editing matrix and universal-packager
tests are not rerun in this test-only cycle; their evidence remains attached to
these unchanged development artifacts. All six generated bridge adapters pass
`node tools/sync-server-bridge.mjs --check`.

## Reproduction And Evidence

With the owner's Minecraft EULA accepted and checksum-pinned dependencies cached:

```powershell
.\tools\test-native-paper-bridge.ps1 -Java21 <jdk21> -Java25 <jdk25> -Minecraft 26.2 -ModVersion 1.8.0-bridge-dev -EditFaults -AcceptEula
.\tools\test-native-paper-bridge.ps1 -Java21 <jdk21> -Java25 <jdk25> -Minecraft 26.1.2 -ModVersion 1.8.0-bridge-dev -EditFaults -AcceptEula
.\tools\test-native-paper-bridge.ps1 -Java21 <jdk21> -ModVersion 1.8.0-bridge-dev -EditFaults -AcceptEula
.\tools\test-server-bridge.ps1 -Java21 <jdk21> -Java25 <jdk25> -Offline
```

Ignored evidence roots: `build/bridge-native-fault-proof/26.2/`,
`build/bridge-native-fault-proof/26.1.2/` and legacy
`build/bridge-native-fault-proof/`. Each contains `native-fault-proof.json`, six
client logs and two complete normally-stopped server logs. Earlier legacy
evidence is retained under `pre-20261014-exact-tuples/` rather than discarded.

Development Fabric SHA256: `22831c631a6bec0f71af0a4aa7ca3e3f4f94304ba62e26b810c52b65a635035b`.
Development NeoForge: `9110f7e654b09c1eee0136cf314e4872a4da22977f347c2450351eacca368bee`.
Companion: `7e40f70b3fab486358a929da73db4132d31bcd3d10e5bc90b99392dce3c4d956`.
Separate test fixture: `90b871366888fc369c8182a69ac45ccc7dddcde17fdcdf496cbe964a9defe15e`.

## Remaining Gates

This stage does not establish concurrent 26.x editor/load performance, arbitrary
network/backend/proxy behavior, ungraceful dependency loss, protection-plugin
combinations, native saturated restart recovery or production cost acceptance.
Remote clipboard synchronization, command simulation and dedicated mod-server
host adapters remain unimplemented. Necessary production fixes are still allowed
and must ship in a separate client fix/release, preserving published artifacts.
