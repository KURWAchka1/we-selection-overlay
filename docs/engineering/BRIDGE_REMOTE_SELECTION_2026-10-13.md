# Remote Cuboid Editing: Development Evidence

Date: **2026-10-13**. Baseline: `0f69e1f88c2073d72115b16cff06ef920030daaf`;
the accompanying commit contains this implementation. Companion **0.1.0-dev**,
client **1.8.0-bridge-dev**, experimental application protocol **1.2**.
Published 1.8.0 artifacts/tag are unchanged. This is not a companion release.

The later [network-fault and concurrent-load cycle](BRIDGE_EDIT_FAULTS_AND_LOAD_2026-10-13.md)
adds the named native latency/late-ack/revocation and 32-actor editing checks;
the original remaining-gates list below describes this stage's baseline.
The later [native gesture cycle](BRIDGE_NATIVE_GESTURES_2026-10-13.md) covers
the actual callback/hit-test path and inspected native screenshots on both
1.21.11 loaders; it remains distinct from manual hardware ergonomics.

## Implemented Scope

- Paper **1.21.11-132 / c5eb079** + official WorldEdit **7.4.2**.
- Existing selection editor in development **Fabric and NeoForge 1.21.11**,
  with no client WorldEdit required on the remote path. Integrated singleplayer
  retains its local source and normal CUI remains independent.
- Explicit read of an editable cuboid, local draft, one confirmation commit,
  canonical bounds acknowledgment and public WorldEdit CUI update. Cancellation
  before submission sends no commit. Other selectors are refused, not approximated.
- Separate opt-in `weso.bridge.selection.edit` plus existing bridge-read and
  applicable WorldEdit expand/contract/shift grants. No automatic OP/default grant.
- Only selection metadata changes. No block/chunk capture, clipboard transfer,
  command simulation, world write, EditSession or undo-history entry.

The Paper artifact compiles the **same three existing shared selection-domain
classes** used by local editing, not a second implementation. Remote sources use
the current controller/drafting interfaces rather than new mouse/render logic.
26.x adapters compile the shared protocol but request only metadata/close; they
do not advertise an unintegrated remote-edit consumer. The adapter generator
preserves that distinction; `node tools/sync-server-bridge.mjs --check` passes.

## Correctness And Resource Boundary

Each negotiated peer retains at most one original WorldEdit snapshot and random
opaque revision. All request types share monotonic IDs, one pending ingress slot,
existing per-player/aggregate quotas and the fair owning-thread dispatcher.
Edit reads/commits do not add background tasks or worker threads. The largest
new reply is **81 bytes**; the hard frame limit remains **256 bytes**.

A commit consumes the original token before crossing the host boundary. Exact
last-ID/payload duplicates may return a cached acknowledgment, never reapply.
Another player's revision/epoch, changed selector/session/world/ordered points,
out-of-height bounds and insufficient current grants fail closed. Host access
uses actual connection identity, never a payload actor or world name.

If an operation may have applied but cannot be confirmed, status is **UNKNOWN**,
not success, failure-with-rollback, or automatic retry. This includes client
timeout/context loss and authority revoked after host application. A new edit
requires current state; no lost-ack mutation is automatically resubmitted.
This is current-state optimistic concurrency, not an event history: identical
points restored on the same selector can be indistinguishable from no change.

Client cache reads preserve snapshot identity. CUI changes invalidate the cache;
ordinary ticks/frames do not read WorldEdit remotely. Idle negotiation alone does
not capture a selection. This avoids a request for each mouse sample or frame.
Separate edit diagnostics count captures, host commit calls and host-reported
APPLIED outcomes; metadata read counters retain their earlier meaning.

## Executed Verification

| Check | Observed result |
| --- | --- |
| Java domain/protocol/client tests | **91 pass per JVM**, Java 21 and 25: protocol 14, core 50, client 27; zero failures |
| Node harness checks | **42 pass**, including new edit golden bytes/offsets and existing process/world/evidence guards |
| Native payload codecs | All **six** actual PacketCodec/StreamCodec probes pass, including four new message types |
| Runtime builds | All six development runtimes build |
| Universal packaging | Fabric **121** and NeoForge **11** tests pass; two development JARs |
| Actual Paper wire proof | **27 groups pass**, normal owned-server shutdown |
| Actual native Paper connections | **Six pass**: Fabric/NeoForge allowed, denied and companion absent |
| Cursor isolation | Both native GLFW versions pass preflight; gameplay runs on a private desktop with cursor agent |

Pure tests include wrong operation/epoch/request replies, all truncated edit
frames, replay with changed payload, cross-player ownership, context reset,
queued revocation/expiry/terminal close, revocation before cached disclosure,
height rejection, uncertain host exit and no automatic retry over 100,000 polls.
Two final regressions reset the context from the post-read/post-apply authorization
callback: neither an old snapshot nor an applied acknowledgment may be published
into that reset context. Ownership is rechecked after these host calls as well.
Default metadata adapters cannot request or accept editing; overlapping requests
cannot replace the first pending callback.

### Actual WorldEdit Wire Cases

The real loopback server passes resize/shift and ordinary CUI, exact duplicate
acknowledgment without duplicate CUI, a real WorldEdit `/shift 1 east` during a
draft, independent expand-only/contract-only/shift-only grants, reversed point
ordering, the extending selector, unsupported/incomplete selectors, replaced
selector with identical bounds, all **six faces/eight corners**, foreign tokens,
height overflow and live edit revocation. The real command case dispatches only
from the **test fixture**, never from production bridge code.

A test-only SHA-256 compares **3,315 already-loaded blocks** before/after edits;
missing audit chunks fail instead of generating them. Fresh WorldEdit history
remains empty. This bounded audit is combined with the production path's absence
of block/EditSession access; it is not a whole-world checksum claim.

### Native Consumer Cases

Both loaders join without client WorldEdit. Test-only in-JVM key state drives
the actual G toggle and resize controller; confirmation waits for APPLIED and
canonical CUI. Whole-box shift invokes the actual native remote source with a
draft and waits for its callback. Forty quiet ticks do not advance request IDs.
Public controller cancellation (the body used by Escape) sends no apply.

Independent server counters are **one edit capture, two commits, two applied**
for each allowed loader, unchanged through quiet/cancel phases. Existing actual
respawn, dimension, channel, reconnect, revocation and graceful-close/recovery
checks still pass. Denied/absent cases preserve CUI without edit traffic. All
owned servers terminate normally; no desktop switch/system input is used.

This is not a physical mouse-drag or actual Escape key-injection proof. Existing
mouse geometry is reused; its remote-host ergonomics remain an acceptance gate.
Offline Fabric test credentials emit expected Realms/profile HTTP 401 messages;
these are retained, not attributed to successful bridge operations.

### Integrated Singleplayer Regression

The guarded command matrix also passes on both modified 1.21.11 loaders with
their local WorldEdit source: command previews, clipboard rotate/mirror, selection
editing, outline/XYZ behavior, crosshair restoration, palette history and editing
palette. Actual screenshot pixel gates pass, not just success log markers:
Fabric diamond/rotate/mirror **34,099 / 4,225 / 1,113** pixels; NeoForge
**28,398 / 4,225 / 1,113**. This is a regression of the existing fixture, not
remote-host physical mouse coverage or a new comprehensive performance run.

## Reproduced Failure And Boundary

The initial wire edit check ran after three plugin disable/re-enable cycles.
The pinned Paper loader was already closed; newly referenced edit classes failed
with a closed-ZIP `IllegalStateException`, and the read timed out. The failed log
is retained at `build/bridge-paper-proof/failed-edit-after-hot-enable-20261013/`.
See the [pinned source/reasoning](SOURCES.md#s52-remote-cuboid-transactions-and-paper-disable-boundary).

No eager-loading, internal loader reflection or unsupported reload workaround
was added. Once this plugin instance disables, same-JVM re-enable withholds the
edit grant until a **full server restart**. The wire test requests all bits after
re-enable and verifies only read/close are granted even with edit permission.
Each native loader starts on a fresh Paper process before exercising edits.
Previously proven metadata/close recovery remains a narrower supported fixture
outcome, not a promise of general hot-reload support.

## Reproduction And Evidence

Run from the repository root with cached pinned dependencies and owner-accepted
Minecraft EULA. Do not use the fixture on an ordinary server.

```powershell
.\tools\test-server-bridge.ps1 -Java21 <jdk21> -Java25 <jdk25> -Offline
.\tools\test-bridge-packaging.ps1 -Java21 <jdk21> -Java25 <jdk25> -RerunTests -Offline
.\tools\test-paper-bridge.ps1 -Java21 <jdk21> -PaperJar <pinned-paper.jar> -WorldEditJar <pinned-worldedit.jar> -NodeDependencies <bridge-wire-deps> -AcceptEula
.\tools\test-native-paper-bridge.ps1 -Java21 <jdk21> -ModVersion 1.8.0-bridge-dev -AcceptEula
node tools/sync-server-bridge.mjs --check
.\tools\test-command-matrix.ps1 -Java21 <jdk21> -Java25 <jdk25> -Only @('fabric-1.21.11', 'neoforge-1.21.11') -ModVersion 1.8.0-bridge-dev
```

Raw local evidence, intentionally ignored by Git:

- `build/bridge-validation/`: Java21/25, six codecs, Node/JFR/admission and packaging logs.
- `build/bridge-paper-proof/wire-proof.json`, `wire-server.log`: checked at
  **2026-10-13T15:48:46Z**, 27 wire groups. Wire fixture SHA-256:
  `d9504b55288c304e230e3da712d6816333a005a8885695a521182632b1aee422`.
- `build/bridge-native-proof/native-proof.json` and six scenario logs plus
  per-loader server logs: checked at **2026-10-13T15:53:50Z**. Native fixture hash:
  `d9504b55288c304e230e3da712d6816333a005a8885695a521182632b1aee422`.
- Prior successful evidence is preserved under each proof root's
  `pre-edit-20261013/`; the failed hot-enable attempt has its own directory above.
- `build/desktop-tests/`: cursor preflight and guarded native worker logs.
- `build/command-validation/fabric-1.21.11.log` and `neoforge-1.21.11.log`:
  integrated singleplayer regressions and screenshot pixel assertions.
- `build/bridge-native-proof/pre-reentrant-guard-20261013/`: first successful
  edit matrix before the final two ownership-check regressions were added.

Candidate SHA-256 and footprint:

| Artifact | Bytes | SHA-256 |
| --- | --- | --- |
| Paper companion | 86,526 | `bc6c3e6f70f96cb9e4cb1e823b60d1063154b4859f80525141353057c539a909` |
| Universal Fabric development client | 1,715,738 | `b6a5a92de34124e741bf6224b63124697f4f0e07a397a5b780e167e7021ea8cc` |
| Universal NeoForge development client | 1,687,810 | `534f2a4011c8e365b20cc6a3cb77a4d627bb08e883502d5471ddf8ff5ad128c6` |
| Pinned Paper bootstrap | 54,846,016 | `5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba` |
| Official WorldEdit Bukkit 7.4.2 | Provided dependency | `0ee152b1be5dfb51500505e2bf5a8c9d66f09c7fa484bf9aea384d7e7b459b06` |

The companion bytecode/contents guard passes: Java21, project license present,
no client/test classes, Paper, WorldEdit or other provided dependencies bundled.
JDKs: Temurin **21.0.11+10-LTS** and **25.0.2+10-LTS**. Clients use repository pins:
Fabric Loader **0.18.4**, API **0.141.3+1.21.11**, NeoForge **21.11.42**.

## Remaining Gates

This does not establish arbitrary RTT/lost-ack proxy behavior, physical remote
mouse dragging, every permission/protection plugin, FAWE/Folia, 26.x remote
consumers, dedicated modded-server hosts or historical ABA detection. Sustained
multi-player edit-load/latency/allocation acceptance remains separate from the
earlier read-only performance evidence. No claim of zero server cost is made.
Clipboard and command preview still require independently budgeted/sanitized
designs and tests. Any public client update must be a new fix/release, not a
replacement of the already published 1.8.0 JARs.
