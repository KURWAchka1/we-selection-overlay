# Exact 26.x Remote Editing Proof

Date: **2026-10-13**. Inspected baseline:
`26567dd0559b9cc31972eb72bcfdacc105332a53`; source changes accompany this record.
Companion **0.1.0-dev**, client **1.8.0-bridge-dev**, protocol **1.2**.
This is a development port and bounded verification stage, not a public release.
Published 1.8.0 downloads and tag are unchanged.

## Exact Tuples

| Actual Minecraft host/client | Paper | Official server WorldEdit | Native clients | JVM |
| --- | --- | --- | --- | --- |
| 26.1.2 | 74 / e4e17fc | 7.4.3+7515-78babeb | Fabric 0.19.3 / API 0.145.1+26.1; NeoForge 26.1.2.75 | Temurin 25.0.2+10 |
| 26.2 | 133 / 58e6e45 | 7.4.5+7590-b8dc4c1 | Fabric 0.19.3 / API 0.158.0+26.2; NeoForge 26.2.0.67 | Temurin 25.0.2+10 |

The Fabric 26.1 test launcher overrides its normal Minecraft 26.1 compile pin
to **26.1.2**, matching the actual host. No local WorldEdit is installed in any
of these four remote gameplay clients. They load the development universal JAR,
not replacement test implementations of the editing controller.

Paper's official exact `26.1` lookup returned `version_not_found`; it is not a
substitute for 26.1.2 or evidence that every 26.1.x host works. The production
startup guard accepts only enabled official WorldEdit with exact pairs
1.21.11/7.4.2, 26.1.2/7.4.3 and 26.2/7.4.5 (optional `+build` suffix).
Four pure policy tests reject unknown versions, prefix confusion, disabled or
absent dependencies and other distribution names. No support for FAWE, Folia,
proxies, translators or dedicated Fabric/NeoForge hosts is added.

Primary sources, resolved API boundaries and publisher checksums:
[S55](SOURCES.md#s55-exact-26x-bridge-tuples).

## Implementation

- The four 26.x native transports now negotiate the same minor-2 editing
  capability and use the existing shared `BridgeEditExchange`.
- Their controllers select the existing integrated-singleplayer source locally
  or the negotiated remote source on a supported server. Local WorldEdit remains
  lazy/optional; an unavailable remote context cannot substitute for authority.
- The existing remote-source implementation is reused without a new polling
  mechanism. Context/generation guard cached snapshots, CUI invalidates them,
  dragging stays local, and confirmation sends one single-use-revision commit.
- The generator now maintains the transport and remote source on all six
  adapters. Protocol/domain/server dispatch semantics are not forked by version.
- The companion's existing Java 21 public Paper/WorldEdit API boundary is retained;
  server tests run on Java 25 for the new hosts. No Minecraft internals or new
  heavy dependency are bundled into production artifacts.

Only a comment changes in the 1.21.11 transports in this cycle. This does not
freeze their source: necessary future client fixes still require implementation,
verification and a separately published client fix/release.

## Native Evidence

All clients run on guarded private Win32 desktops with the cursor-isolation
agent and verified GLFW preflight. No window activation, desktop switching,
cursor warp, OS input injection or attachment to the owner's game occurs.
Test-only native callbacks use exact mappings. The 26.x focus-loss fixture calls
the private `Window.onFocus(long, boolean)` inside its own JVM, because stock
Minecraft has no `setWindowActive` on these versions. The 26.2 test adapter also
uses the new `client.gui` screen boundary; neither reflection is production code.

Each client executes the same **28** cases described in the
[1.21.11 gesture report](BRIDGE_NATIVE_GESTURES_2026-10-13.md#cases): six faces,
eight G corners, eight free corners, five cancellation boundaries and real
radial MOVE with X/Y/Z selection and wheel displacement. Total: **112 cases**
over four fresh native connections, not 112 different gesture definitions.

Per connection the actual WorldEdit bounds reconcile after every case with
**29 captures, 23 commits and 23 APPLIED**. All five cancellations leave the
original bounds. G-mode mouse release keeps the draft local; G confirms. Free
corner release commits once. The MOVE case reaches exactly
`12,65,18,17,71,25` after +2 X, +1 Y, -2 Z; camera turn does not translate it.
Twelve quiet ticks during each draft and after acknowledgment do not trigger
additional capture or mutation requests. All four disconnects leave **zero
peers, pending frames and dispatcher tasks**.

The console-only fixture checks unchanged hashes for **3,315 loaded blocks**
and empty WorldEdit undo history. This is metadata-only authority verification,
not a whole-world scan or proof of zero chunk generation during test setup.
Every owned Paper process completes a normal stop before its proof is written.

| Evidence | Completed UTC | Cases | Commits/APPLIED | Screenshots |
| --- | --- | --- | --- | --- |
| `build/bridge-native-gesture-proof/26.2/native-gesture-proof.json` | 2026-10-13T19:07:50Z | 56 | 46/46 | 4 |
| `build/bridge-native-gesture-proof/26.1.2/native-gesture-proof.json` | 2026-10-13T19:12:46Z | 56 | 46/46 | 4 |

All eight fresh **1280 x 720** native F2 screenshots were opened and inspected.
The corner axes, draft dimensions and XYZ compass/highlighted Z are present;
crosshairs are absent, mod labels fit and do not overlap each other. Vanilla
tutorial toasts occur in some shots. Faint default selection colors against the
sky are visible; these empty-selection fixture shots are not promotional images,
terrain-render acceptance, arbitrary-resolution checks or frame-time benchmarks.

## Failures Retained

The first 26.x test compilation exposed the missing stock focus setter; resolved
Minecraft sources established the correct test-only callback. The first 26.2
server run correctly refused to start the companion under the old 1.21.11-only
guard. No game client started on that failed host. The owned server stopped
normally; diagnostics remain under
`build/bridge-native-gesture-proof/26.2/failed-host-guard/`.
An explicit tuple policy, tests and early disabled-companion assertion replace
any implicit widening of the old guard. Later successful runs do not erase the
failed attempt. Windows Perflib/OSHI and offline-session-key background warnings
remain environmental observations, not proof of silent logs or game failure.

## Regression Status

Fresh all-six runtime packaging and **132** universal packager tests pass
(121 Fabric, 11 NeoForge). The
post-rebuild companion/client SHA256 values still match all four native proofs.
Pure tests pass **95 per JVM** (Java 21 and 25): 14 protocol, 27 client, 50 core,
four host-policy tests. All **67 Node harness checks**, six actual native codecs,
test-agent guards, nine actual-recording JFR checks per JVM and twelve
deterministic admission-ordering cases per JVM pass. The companion remains Java
21 bytecode with no client, fixture or provided API classes bundled.

The fresh **all-six local native matrix passes**, completed at 22:36:40 Moscow
(19:36:40 UTC). It includes WorldEdit reference comparisons, loaded-chunk
streaming/changes, source hiding/restoration, legacy IDs, clipboard coexistence
and transforms, selection editing/cancellation, outline/axis separation,
crosshair lifecycle, palette history and config round trips. Fresh diamond,
clipboard rotation and mirror screenshots pass the pixel gates on every runtime.
All required native success/guard markers and final supervisor acceptances were
independently reread from the new logs, not inherited from old runs.

Additional fresh Russian settings/keybind screenshots at scale 4 were opened
for both Fabric and NeoForge 26.2: mod settings and labels fit their controls;
the native keybind menu does not overlap the mod's short binding labels.
NeoForge's native menu also shows yellow key-conflict highlighting; layout
inspection is not a key-conflict/modpack audit. These are bounded viewport
checks, not all-screen-size or hardware-input acceptance.
The separate supervisor log is
`build/desktop-tests/WesoTest-5fe952985d924efdac0dc8ed399f3827.log`;
per-runtime logs are under `build/command-validation/`. Cursor preflight passed
before these launches. Fixture startup/forced world workloads include
`Can't keep up` warnings; no frame-time improvement or production cost claim is
derived from this functional suite. The published release artifacts were not
rebuilt or overwritten; the 1.8.0 tag is not moved.

## Reproduction And Artifacts

Cache these downloaded publisher artifacts in `build/bridge-server-downloads/`.
The supervisor verifies the pinned digest before copying them to disposable
servers; no existing worlds or ordinary server configuration are reused.

| File | Publisher digest |
| --- | --- |
| `paper-26.1.2-74.jar` | SHA256 `1d70b1dab9cf4a6de615209a536f3a45a2186240253c428213ce2188ab95e5f7` |
| `paper-26.2-133.jar` | SHA256 `bf1dcf627364f8a631ab15250d240eb5a8e89faaac523b391e1c61367fce9ff1` |
| `worldedit-bukkit-7.4.3.jar` | SHA512 `43d2f8865a06d63c71d2b8bc0ded66c2c7f5e413db1a64bc2f665db3bf25ec28ae40789884af4bdaf40740a6632196494d2897fa77675f0babfaf79f18400853` |
| `worldedit-bukkit-7.4.5.jar` | SHA512 `a383492fac6bfb4d43a257dfa7b5fc076aae503a71151b463de4fe80e6f3d5fc11209eaf4097baa115f3febf0adc40ca0a1ecda227b8439b429d0a4ba3a63a4f` |

Native-proof production SHA256:

| Artifact | Digest | Bytes |
| --- | --- | --- |
| Paper companion `0.1.0-dev` | `7e40f70b3fab486358a929da73db4132d31bcd3d10e5bc90b99392dce3c4d956` | 87675 |
| Universal Fabric `1.8.0-bridge-dev` | `22831c631a6bec0f71af0a4aa7ca3e3f4f94304ba62e26b810c52b65a635035b` | 1728101 |
| Universal NeoForge `1.8.0-bridge-dev` | `9110f7e654b09c1eee0136cf314e4872a4da22977f347c2450351eacca368bee` | 1700090 |

Separate fixture SHA256:
`0cf29fe36f90c3bac1d9568b0f8df2b9b87ef2929bbcfd3f29945578ac753029`.
It is not distributed. Logs, worlds, test JARs and PNGs remain ignored by Git.

From the repository root, with cached dependencies and the owner's accepted EULA:

```powershell
.\tools\test-bridge-packaging.ps1 -Java21 <jdk21> -Java25 <jdk25> -Offline -RerunTests
.\tools\test-native-paper-bridge.ps1 -Java21 <jdk21> -Java25 <jdk25> -Minecraft 26.1.2 -ModVersion 1.8.0-bridge-dev -EditGestures -AcceptEula
.\tools\test-native-paper-bridge.ps1 -Java21 <jdk21> -Java25 <jdk25> -Minecraft 26.2 -ModVersion 1.8.0-bridge-dev -EditGestures -AcceptEula
.\tools\test-server-bridge.ps1 -Java21 <jdk21> -Java25 <jdk25> -Offline
.\tools\test-command-matrix.ps1 -Java21 <jdk21> -Java25 <jdk25> -ModVersion 1.8.0-bridge-dev
node tools/sync-server-bridge.mjs --check
```

## Remaining Gates

No 26.x native denial/absent-companion, respawn/world change/reconnect, graceful
restart/dependency loss, delayed/late acknowledgment or concurrent-load proof is
claimed. Existing 1.21.11 evidence is not inherited just because source is shared.
The launcher explicitly rejects unimplemented 26.x native modes. The next stage
should add exact-host negative/lifecycle tests before remote clipboard or command
simulation expands the protocol. Hardware ergonomics, every camera/view/resolution,
protection/proxy combinations and representative production cost remain open.
Passing this bounded gate does not authorize a production companion release.
