# Native Paper Bridge Regression

Date: **2026-10-11**. Baseline: `0f4f6c4bb0d285c1f961e4992008944a42c94997`
plus the accompanying CUI fix and test changes. Client candidate:
**1.8.0-bridge-dev**; companion: **0.1.0-dev**. Neither is a new supported release.
Published 1.8.0 artifacts and tag remain unchanged. Client fixes require a
[separate fix release](../release/FIX_RELEASE_POLICY.md).

## Real Client Matrix

The six-case real Minecraft 1.21.11 matrix passed twice: **12 successful launches**,
not synthetic protocol-only clients. The final run also asserts the exact decoded
CUI geometry in every case:

| Client | Companion / opt-in grant | Observed |
| --- | --- | --- |
| Fabric Loader 0.18.4 / API 0.141.3+1.21.11 | Installed / allowed | READY, CUI, explicit metadata reads, revocation and disconnect cleanup |
| Fabric, same tuple | Installed / denied | DENIED, CUI intact, zero metadata reads |
| NeoForge 21.11.42 | Installed / allowed | READY, CUI, explicit metadata reads, revocation and disconnect cleanup |
| NeoForge, same tuple | Installed / denied | DENIED, CUI intact, zero metadata reads |
| Fabric, same tuple | Absent | UNAVAILABLE, ordinary CUI intact, normal join/disconnect |
| NeoForge, same tuple | Absent | UNAVAILABLE, ordinary CUI intact, normal join/disconnect |

Host: Paper **1.21.11-132**, commit `c5eb079`, official WorldEdit Bukkit **7.4.2**.
JVM: Temurin **21.0.11+10-LTS**. WorldEdit is deliberately **not installed on the
clients**, exercising the standalone CUI fallback rather than the shared
EngineHub client handler. No OP, protection plugins, renderer mods or proxies.
The test-only console fixture grants opt-in permission and seeds selection
metadata using WorldEdit APIs; it is not bundled in client or companion JARs.

For each authorized client the actual payload returns inclusive bounds
`[10,64,20]..[15,70,27]`, build height `-64..319` and permission flags `7`.
Clearing the selector returns EMPTY with no bounds/grants; reseeding returns READY.
Revocation returns DENIED before a WorldEdit metadata read. Re-grant alone cannot
reuse the revoked negotiation. CUI remains available independently.

Counters: hello/initial observation causes **zero metadata reads**. Each authorized
scenario makes exactly three metadata reads; denied scenarios make zero.
Per matrix, across four companion-enabled clients: six reads, twelve replies, zero retained
peer records after every disconnect. This is a correctness/counting result, not
an MSPT benchmark or a claim of zero total overhead.

## Defect And Regression Test

The first NeoForge attempt joined Paper and reached bridge READY, but timed out
without CUI. The private `RuntimeCuiBridge` registered `deliver` as serverbound,
leaving its clientbound handler empty. This was a standalone CUI defect, not a
failed bridge handshake. The shared EngineHub handler path was not changed.

Official resolved NeoForge sources for **21.11.42**, **26.1.2.75** and **26.2.0.67**
confirm the four-argument overload takes serverHandler then clientHandler; see
[source pin S32](SOURCES.md#s32-neoforge-bidirectional-handler-order).
The fix changes the shared fallback once for all three NeoForge runtimes.

The packaged-bytecode test inspects actual lambda targets and their order in all
three relocated runtime classes. Its negative run against the unchanged released
1.8.0 JAR fails with `serverbound must not deliver CUI`; the full fixed NeoForge
packager suite passes ten tests. Fabric packaging passes 120 tests.
The negative result is intentional evidence and is not counted as a passing run.

Protocol fuzzing additionally sends 100,000 seeded frames with valid headers and
all four opcodes, plus every one-bit mutation of five valid messages. Decoding
must preserve input bytes and either reject with a domain error or round-trip
canonically; encoding failures cannot be swallowed as decode rejection.
The complete pure suite has 31 tests on each of Java 21 and Java 25.
Harness parameter checks pass 42 assertions on PowerShell 5.1 and 7.

## Local NeoForge Recheck

The fixed universal JAR also passed the guarded local command/editor matrix on
NeoForge **21.11.42 / Minecraft 1.21.11**, **26.1.2.75 / Minecraft 26.1.2** and
**26.2.0.67 / Minecraft 26.2**, with matching local WorldEdit 7.4.2/7.4.3/7.4.5.
This separately exercises the EngineHub shared CUI handler path retained by the fix.

Fresh assertions cover command previews and texture captures, stable mixtures,
clipboard transform/cache/priority/restoration, normal Enter submission, resizing
and movement axes, Escape/crosshair lifecycle, palette history and configuration.
All three fresh texture checks pass. Evidence:
`build/desktop-tests/WesoTest-4ac7bcedd1a740d992a824eec683dd66.log` and the three
`build/command-validation/neoforge-*.log` files. The packager was restored to its
default 1.21.11 development configuration after the runtime overrides.
This is not a new full UI-layout/modpack matrix; that earlier evidence remains separate.

## Artifact Identity

| Artifact | Bytes | SHA-256 |
| --- | --- | --- |
| Fabric development universal | 1,656,610 | `d7fe7a8a0c28f522229d65ae745ff082ffc2c15d98fee63b71f4598a1143fc66` |
| NeoForge development universal | 1,628,774 | `6843166774941d48110ecd94aab8e8004ce67271de797d004995e5f42cccf00c` |
| Paper development companion | 44,508 | `2d03373110e6726633e8cfbd3179d32cb6b182b42f23aec629749c7775b044b6` |
| Paper server | - | `5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba` |
| WorldEdit Bukkit 7.4.2 | - | `0ee152b1be5dfb51500505e2bf5a8c9d66f09c7fa484bf9aea384d7e7b459b06` |

## Reproduce And Evidence

Build the development universal candidates first. The earlier wire fixture must
already contain the pinned server/WorldEdit dependencies; the wrapper reuses its
bootstrap cache but never a user's world.

```powershell
.\tools\test-native-paper-bridge.ps1 -Java21 <jdk21> -ModVersion 1.8.0-bridge-dev -AcceptEula
```

`-AcceptEula` requires the server owner's prior Minecraft EULA acceptance.
The wrapper uses a private Win32 desktop plus the cursor-isolation agent, routes
game launches through the guarded command harness, and injects no system input.
Clients are capped at 40 FPS, two processors and 1.5 GiB; Paper runs headlessly
on loopback `127.0.0.1:25579`, with two processors, 1 GiB and BelowNormal priority.
It shuts down after the assertions, including failure cleanup.
The controller removes the preceding success report when a matrix starts, checks
candidate hashes before and after every client run, and records final success
only after Paper stops. The final run left no guarded test JVMs or listener on
port 25579. Test orchestration uses Node **24.21.0**.

Local ignored evidence:

- `build/bridge-native-proof/native-proof.json`, timestamp `2026-10-11T18:59:21.350Z`.
- `build/desktop-tests/WesoTest-b9946977d1684941aa5ebaf137bd396f.log`: final strengthened run.
- `native-proof-first-pass.json` and `build/desktop-tests/WesoTest-3856d77eada940958c8c6abec35cd6a8.log`: first successful matrix.
- Six per-client logs and the companion-present/absent server logs in the proof directory.
- `before-fallback-fix/neoforge-allowed.log`: original native failure.
- `build/bridge-validation/cui-negative.log` and `cui-negative.xml`: old-JAR failure.
- `build/bridge-validation/cui-positive.log`: fixed-JAR packager pass.

Generated worlds, logs, offline identities and fixture JARs remain excluded from
Git and release files. The graph was refreshed after the production source change.

## Remaining Gates

This closes the initial native 1.21.11 Paper transport gate only. No remote
selection editing, clipboard streaming or command simulation is implemented yet.
26.x Paper and mod-server hosts, same-process native reconnect/world transfer,
FAWE/Folia/proxies, renderer modpacks and controlled CPU/allocation/MSPT load
profiles are not established by these six runs. The earlier
[local/UI regression](FULL_REGRESSION_2026-10-11.md) is separate evidence.
