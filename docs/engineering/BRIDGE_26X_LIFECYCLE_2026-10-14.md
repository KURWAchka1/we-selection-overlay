# Native 26.x Negative And Lifecycle Regression

Date: **2026-10-14**. Baseline:
`8224d90f970c98662e5b15d3f81682d0b6576fc0`.
Companion **0.1.0-dev**, client **1.8.0-bridge-dev**, protocol **1.2**.
This is a bounded development verification stage, not a public release.

## Method And Scope

Reuse the existing 1.21.11 native allowed/denied/absent fixture on exact
**Paper 26.1.2-74 + official WorldEdit 7.4.3** and
**Paper 26.2-133 + official WorldEdit 7.4.5**. Both native loaders join without
client-side WorldEdit, using the same development universal JARs as the
[previous gesture proof](BRIDGE_26X_REMOTE_EDITING_2026-10-13.md).
Java 25 runs new clients/hosts; Java 21 builds the isolated Paper fixture.
The exact tuple and publisher digest checks are retained. No version range,
new artifact download or automatic upgrade is inferred from the test selector.

Each host matrix requires six real game runs: allowed, denied and absent for
Fabric and NeoForge. An allowed run also reconnects within the same game JVM.
The supervisor starts a fresh Paper process per allowed/denied loader pair,
then a fresh companion-absent server for both loaders. Plugin stop/re-enable is
a fault test, not production hot-reload support or a full process restart.

Private Win32 desktops AND the cursor-isolation agent remain mandatory. The
preflight exercises both actual GLFW versions. No desktop switching, window
activation or OS keyboard/mouse injection occurs. Input is synthetic inside
the disposable game JVM; this does not establish hardware ergonomics.

## Required Observations

| Scenario | Required evidence |
| --- | --- |
| Allowed initial state | Ordinary CUI, READY negotiation without unsolicited selection read |
| Remote editing | G resize, remote-source shift, canonical CUI and replies; one capture, two commits/APPLIED; cancel sends no commit |
| Same-world respawn | Actual replacement native player, same world/connection, fresh epoch and explicit fresh metadata read |
| Nether / Overworld | Actual changed native worlds, fresh epochs and correct inclusive world heights |
| Channel off/on | Explicit wire withdrawal clears metadata/grants/pending work; quiet unavailable interval; one fresh negotiation after advertisement |
| Revocation / re-grant | Real read denial; re-grant alone cannot renew an already denied negotiation |
| Same-process reconnect | Old state clears, new network handler/epoch, no metadata read before explicit consumer request |
| Graceful companion stop | Optional negotiated close invalidates state even with remote channel still advertised; ordinary CUI remains usable |
| Companion re-enable | One optional startup hint/handshake, no unsolicited reads; editing remains withheld after classloader close |
| Initially denied | No metadata/edit access; restart hint cannot bypass denial or trigger automatic retry |
| Absent companion | Normal join and CUI; read/editor capability unavailable without disconnecting the game |
| Cleanup | Zero peers, pending frames and dispatcher tasks; normal owned-server stop before proof publication |

The shared native observer now explicitly calls an unavailable remote source:
it must synchronously return an unavailable snapshot without advancing the
request ID. After each tested context loss/recovery, it attempts to commit the
old ready edit snapshot: it must fail locally without a new network request.
These are test-only assertions, not a new production polling loop.

The independent evidence verifier requires exact unique loader/scenario coverage,
ordered phases and all seven allowed lifecycle contexts. Numeric counters must
be finite nonnegative integers. Source reads, canonical applies, restart hint
counts/bytes and final queues/tasks must reconcile; missing cases, duplicated
mutations, recapture and client-only success do not pass. Seven negative/complete
self-tests exercise these acceptance rules. Exact native callback checks remain
separate from this supervisor's server counters.

## Failed Attempt And Correction

The first 26.2 Fabric allowed attempt passed editing, respawn and both world
transitions, then failed on the fixture's deliberately 1.21.11-only channel
injector guard. This was not a production bridge crash. The supervisor recorded
failure, terminated its owned client tree and normally stopped Paper. No pass
proof was published. Logs and failure JSON are retained under
`build/bridge-native-proof/26.2/failed-fixture-version-guard/`.

Both exact resolved Paper JARs were inspected with `javap` before widening the
**test-only** injector guard. All used packet/identifier/connection signatures
match; the guard now also requires loopback IP and fixture ports. Bukkit reserves
registration channels, so existing test-only reflection is retained rather than
pretending public plugin messaging can manufacture channel withdrawal. This code
never ships in the companion/client. See [S56](SOURCES.md#s56-native-26x-lifecycle-and-negative-evidence).

## Execution Status

| Matrix | Current result | Completed UTC |
| --- | --- | --- |
| 26.2 / both loaders / allowed, denied, absent | PASS, six native runs, 14 allowed context transitions + two denied-restart observations | 2026-10-14T11:14:05Z |
| 26.1.2 / same six scenarios | PASS, six native runs, 14 allowed context transitions + two denied-restart observations | 2026-10-14T11:21:02Z |
| 1.21.11 strengthened-observer regression | PASS, six native runs with the same stronger acceptance rules | 2026-10-14T11:28:51Z |
| Full Java/Node/native-codec regression | PASS, 95 Java tests per JVM, 74 Node checks, six native codecs | 2026-10-14 |

All **18 game processes** pass, including **42 allowed lifecycle context
transitions** and **six denied-restart observations**. Reconnect adds another
connection inside each allowed game process; these are not 18 different test
definitions. Every allowed run has one capture and two canonical applies;
all six together have **12 commits/APPLIED**, with no cancellation commit.
Denied runs add zero selection reads/edits relative to their baseline. After
re-enable, allowed peers make exactly one explicit read; denied peers send none.
The new verifier additionally checks exact reply deltas, not just source reads,
so an extra negotiation loop cannot hide behind a zero scan counter.

The accepted proof roots above retain `native-proof.json`, six client logs and
three normally stopped Paper logs each. Raw client guard/CUI/pass markers,
phase/context arrays and server counters were independently reread after all
three matrices, rather than trusting the launcher's final message alone.
Previous 1.21.11 evidence is retained under
`build/bridge-native-proof/pre-20261014-lifecycle/`.
Ordinary offline authentication/key warnings and Windows OSHI/Perflib warnings
remain environmental observations; these are not warning-free or performance
acceptance runs.

The full regression passes on Java 21 and 25: **95 pure tests per JVM**
(14 protocol, 27 client, 50 core, four host-policy), **74 Node harness checks**,
six actual native codecs, diagnostic-agent probes, nine actual-recording JFR
reader checks per JVM and twelve deterministic admission-ordering cases per JVM.
Paper artifact guards confirm Java 21 bytecode and no bundled client/fixture or
provided dependency classes. Four invalid launcher cases additionally fail before
game startup: missing Java 25, unported 26.x fault mode, unknown exact version and
mutually exclusive modes. PowerShell parsing and all-six adapter drift checks pass.

No production source or public artifact change was required. The companion and
both development universal client SHA256 values remain exactly those in the
[previous candidate record](BRIDGE_26X_REMOTE_EDITING_2026-10-13.md#reproduction-and-artifacts),
and match all three new native proofs after the full regression. Only the
separate test fixture changes: **29,643 bytes**, SHA256
`a537e2f5541a9ec6c9f9b8324aa7be8e1ce61d94c34e0c0039ab38240abe8549`.
It must never be shipped with the companion or installed on ordinary servers.

All tested native clients and owned servers are stopped; loopback fixture ports
are released. Published client 1.8.0 downloads/tag are not rebuilt or moved.
This accepts the named functional regression gate, not production release or
performance acceptance. Necessary client fixes remain in scope for later stages.

## Reproduction

Development universal artifacts and pinned S55 downloads must already be cached.
Use the existing owner's Minecraft EULA acceptance only for these disposable
loopback tests, never a production world:

```powershell
.\tools\test-native-paper-bridge.ps1 -Java21 <jdk21> -Java25 <jdk25> -Minecraft 26.2 -ModVersion 1.8.0-bridge-dev -AcceptEula
.\tools\test-native-paper-bridge.ps1 -Java21 <jdk21> -Java25 <jdk25> -Minecraft 26.1.2 -ModVersion 1.8.0-bridge-dev -AcceptEula
.\tools\test-native-paper-bridge.ps1 -Java21 <jdk21> -ModVersion 1.8.0-bridge-dev -AcceptEula
.\tools\test-server-bridge.ps1 -Java21 <jdk21> -Java25 <jdk25> -Offline
```

No mode switch selects the negative/lifecycle matrix. `-EditGestures` remains a
separate proof. The launcher still rejects 26.x `-EditFaults`; that mode has not
been ported or verified. Versioned evidence roots are
`build/bridge-native-proof/26.2/` and `build/bridge-native-proof/26.1.2/`;
legacy 1.21.11 retains its existing root. Successful `native-proof.json` files
include exact tuples, baseline, JAR hashes, phases, contexts and server counters.
Failed runs write diagnostics, not accepted proofs. All raw logs/worlds remain
ignored by Git; no account data, owner worlds or test classes are distributed.

## Remaining Gates

Even successful rows would not establish 26.x delayed/late acknowledgment,
pre-delivery revocation, concurrent load, representative production cost,
ungraceful dependency loss, proxy/backend switching or protection integrations.
Graceful same-JVM companion re-enable deliberately cannot restore editing after
classloader closure: use a full server restart for installation/upgrades.
No remote clipboard, command simulation, dedicated mod-server host or public
companion release is introduced by this test stage. Published 1.8.0 artifacts
and tag are preserved; client changes remain mandatory whenever needed later.
