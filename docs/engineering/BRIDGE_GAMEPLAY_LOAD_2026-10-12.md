# Mixed Gameplay Bridge Load

Status: **bounded mixed-workload functional gates passed; production cost
acceptance remains open**. Baseline: `ab205ac`, plus the accompanying fixture
and harness changes; production bridge/client code is unchanged.
Scope: experimental read-only Paper 1.21.11 bridge; no public release or client
changes. This is a bounded mixed workload, not a production-server certification.

## Method And Predeclared Gates

- Paper 1.21.11 build 132, official WorldEdit 7.4.2, Java 21; existing pinned
  dependencies, default companion quotas and 1 GiB heap / two JVM processors.
- A separate `build/bridge-gameplay-proof` fixture, loopback/offline synthetic
  identities only. Accepted test-server EULA and existing owned-process guards.
  Restore identical, hashed starting world bytes before every comparison.
- All players fly smooth six-block-radius routes within preloaded chunks,
  sending actual movement packets at five per second. Server-observed
  `PlayerMoveEvent` counts must prove every actor moves, including non-consumers.
- At least one eighth of players never advertise or negotiate the bridge. Remaining
  players use the existing own-selection metadata workload; no OP is granted.
- Six matched windows in both absent and installed runs: `silent`, `idle`,
  `one`, `many`, `flood`, `recovery`. Absent windows send no bridge requests.
  Installed silent has no handshake, idle/recovery have no application work.
- Sixteen live AI cows tick in a bounded pen. Natural spawning, random ticks,
  time/weather cycles remain disabled. AI decisions are not deterministic.
- The test fixture (not the read-only bridge) alternates exactly 512 blocks
  through public WorldEdit edit sessions every 20 ticks; sessions close/flush.
  Every measured window resets the activity counter and contains exactly
  `samples / 20` batches. No player history is manufactured for these synthetic
  host edits. The fixture is excluded from distributed artifacts.
- Every window requires zero chunk loads/generation, exact world-edit counts,
  sixteen valid AI entities with advancing lifetimes, no escaped movement, and
  at least one accepted movement per actor per measured second. Movement packets
  and server events are recorded separately; they are not interchangeable.
- Existing admission, own-player data, per-actor progress and queue bounds stay
  enforced. Ordinary bridge requests must receive at least 90% replies and have
  a measured maximum latency at most 2 seconds in active windows. Non-consumers
  must receive no application data. Idle queue/task and disconnect cleanup must
  return to zero. Failed evidence is retained, never marked validated.
- Run a short pilot, then two full comparisons with absent/installed order
  reversed. Record all ticks, owner CPU/allocation, GC, world-work counters and
  JFR markers. Do not run other test suites concurrently with measurement.
- Review all >50 ms ticks and compare matched p95/p99, CPU and allocation.
  No universal zero-cost or production acceptance threshold is inferred from
  shared-laptop timing, sparse JFR samples or a maintained 20 TPS average.

The pilot started with 24 consumers / 8 ordinary players. Policy review before
the full run changed this to 28 / 4: 24 actors at 4 requests/second cannot
sustain global saturation above 100/second. At 28 consumers, the ordinary actor
at 2/second plus 27 at 4/second can offer 110 eligible requests/second. Raw pilot
evidence keeps its original participant count; it is not relabelled as 28 / 4.

## Reproduce

```powershell
.\tools\test-paper-bridge-load.ps1 -Java21 C:\Users\kurwa\.jdks\temurin-21.0.11 -GameplayLoad -Players 32 -Samples 200 -Repetitions 2 -AcceptEula
node tools/server-bridge-test/analyze-load-proof.mjs gameplay
```

The starting snapshot never overwrites an existing snapshot. Owned worlds are
the three named synthetic fixture worlds, not installed Minecraft profiles.
All servers run hidden without taking desktop focus or capturing the cursor.

## Executed Evidence

The 32-player pilot passed twelve windows / 1,200 ticks with 24 consumers and
8 ordinary players. Preserved evidence is in
`build/bridge-gameplay-proof/pilot-24-consumers-100-ticks/`; its recorded role
count differs deliberately from the full workload. No failed run was discarded.

The full run completed at **2026-10-12 15:36:09 UTC**, using **32 players,
28 consumers, 4 non-consumers, 200 ticks/window and two reversed-order repeats**:
24 windows / **4,800 measured ticks**. The JFR reader verified all 4,800 unique
phase/sample markers and retained the five largest ticks in each window.
All 24 raw windows were revalidated against the final role/workload gates.

- Every window: zero chunk loads/generation; 16 valid AI cows each advanced
  exactly 200 lifetime ticks; ten closed edit sessions changed exactly 5,120
  blocks. Total measured synthetic edits: **122,880 blocks**.
- Every player produced 49 or 50 accepted moves per window; total server-observed
  moves: **37,720**. The four non-consumers sent/received zero bridge application
  requests/replies while continuing the same flight workload.
- All active windows returned **19/19 ordinary requests**. Flood ordinary
  maximum latency was **249.2675 / 224.8642 ms** across the two repeats, below
  the predeclared 2-second fixture gate. Own-player bounds/epochs stayed correct.
- `many` served 532 requests per installed window. Flood admitted/read
  **1,045 / 1,047** requests and throttled **16,667 / 16,554**, within the
  100/second + burst envelope. Initial token credit can keep a 10-second window
  above 1,000; it is not an unlimited sustained rate.
- Queue peak was **28 slots**, no expired/cancelled requests; maximum observed
  queue wait **301.0975 / 298.3448 ms**. Flood ended with 22 pending slots and
  one task, as expected under traffic. Recovery and disconnect both reached
  zero pending slots/tasks; silent/idle/recovery had zero reads/replies/bytes.
- No measured tick exceeded 50 ms. The largest was **34.0864 ms** in the second
  installed flood window. Its narrower public-event bracket was **9.9132 ms**;
  the inferred full tick had zero GC pause overlap and no sampled owner stack.
  Its cause is **unattributed**, not proven to be either bridge work or OS noise.
- Whole-run dispatch slice high-water marks were **9.2953 / 4.5184 ms**. These
  include warmup/negotiation between measurements and are not per-window costs.
  The first mark appeared between the `one` and `many` snapshots. They exceed
  the cooperative 0.5 ms target; no hard timing guarantee or exact cause is
  inferred. The core cannot preempt a host call or JVM stall.

### Paired Timing And Cost

Values are installed minus absent for the matched phase. CPU is owner-thread
milliseconds per wall second; allocation is owner-thread MiB per wall second.
These measure the whole workload, not an isolated per-call bridge cost.

| Repeat / phase | p95 delta ms | p99 delta ms | CPU delta ms/s | Allocation delta MiB/s |
| --- | ---: | ---: | ---: | ---: |
| 1 / silent | -0.3855 | -2.1620 | -6.2434 | -0.0888 |
| 1 / idle | -0.7819 | -0.1385 | +1.5677 | +0.0463 |
| 1 / one | -1.4170 | -0.9939 | -7.8025 | -0.0352 |
| 1 / many | -1.1608 | +0.2105 | +1.5626 | +0.1481 |
| 1 / flood | -1.2960 | -0.3543 | +15.6267 | +0.4395 |
| 1 / recovery | -0.9374 | -1.5472 | -6.2544 | +0.2461 |
| 2 / silent | +1.8945 | +4.5426 | -4.6896 | +0.0817 |
| 2 / idle | +0.4971 | +2.0093 | +3.1246 | -0.0112 |
| 2 / one | +2.6659 | +4.2004 | -7.8134 | +0.0501 |
| 2 / many | +2.1285 | +4.2899 | -15.6181 | +0.2552 |
| 2 / flood | -0.3098 | +3.3378 | +10.9288 | +0.6186 |
| 2 / recovery | +1.6820 | +2.0185 | -1.5596 | +0.1936 |

Installed p95 ranged **2.8149-5.9331 ms**, p99 **3.8894-8.9148 ms**.
Negative timing deltas are not evidence that installing the bridge speeds up
the server: even silent p99 changes sign between repeats. Flood does show
positive CPU/allocation deltas in both repeats; there is real work, not zero
overhead. Windows thread CPU readings are coarse for short brackets, so a zero
bracket reading does not establish zero CPU usage.

JFR retained 4 bridge execution samples across the installed active windows;
sampled bridge allocation weights in flood were 5,401,112 / 3,169,776 bytes.
These weights estimate sampled allocation and must not be treated as exact
retained bytes or compared directly to queue payload memory. The fixture package
is deliberately excluded from bridge-stack attribution. GC pause overlaps were
observed on other ticks (for example 8.2786 ms in installed idle and 10.7369 ms
in absent many); they do not explain the largest 34.0864 ms tick.

No production defect was reproduced by this workload, so quotas and production
code were not changed to optimize ambiguous measurements. This closes the
specified mixed-workload functional scenario, **not** representative busy-server
cost acceptance. Longer repeated runs, spark attribution and the remaining
release gates are still required before advertising production support.

### Artifacts And Regression

| Artifact | SHA-256 |
| --- | --- |
| Unchanged 53,892-byte companion | `be5a082a6b63a8612c993e7dc52c7b0ae498b7249ab5513e72844ced1e1572c7` |
| Separate mixed-workload fixture | `e2f3353287c39c98f85d2ea2ba68660e9d0e20485dd0b22687bfd3aa52f7cec7` |
| Paper 1.21.11-132 | `5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba` |
| Official WorldEdit 7.4.2 | `0ee152b1be5dfb51500505e2bf5a8c9d66f09c7fa484bf9aea384d7e7b459b06` |
| Starting three-world manifest | `bc68fcaf128d6293cee6f4814f43f9a284b208b8f391ca845b60aad0d4d450ce` |

Environment: Temurin **21.0.11+10**, Node **24.21.0**, i5-13420H / 12 logical
processors, shared Windows laptop. The JVM was limited to two processors and
1 GiB, below-normal priority, with bounded profile JFR in every run. No native
Minecraft window was launched. No protection plugins, FAWE, proxies or translators.
Companion wire protocol stays 1.1; these load consumers use the existing minor-0
metadata capability. Restart recovery was not part of the mixed workload.

After measurement, the full offline Java 21/25 regression passed: **68 JUnit
tests per JVM**, **31 Node harness tests**, **4 actual-recording JFR checks per
JVM**, and **12 deterministic admission-order/offset cases per JVM**. Companion
packaging remains Java 21 with no client/fixture/provided dependencies. Native
codec/gameplay runs were not repeated because their production sources/JARs did
not change; earlier evidence is in the fair-dispatch and native reports.

A fresh **17-group real Paper wire regression** then passed with this exact
fixture hash, including cached permission revocation, legacy/minor-1 behavior,
three restart cycles, disabled-WorldEdit rejection and resource cleanup. Its
evidence is `build/bridge-paper-proof/wire-proof.json` / `wire-server.log`.
All owned fixture JVMs stopped normally and port 25579 was verified free.
Development and published client hashes remain unchanged:

| Client | SHA-256 |
| --- | --- |
| Development Fabric | `cef0cd82a534e630309567270c7f1d53e236b053daed4ac9a5cc00177a166cb7` |
| Development NeoForge | `16e1359e98642d579efe774c46a3255a5953abdf55cd4f0ad3502c42806fd322` |
| Published Fabric 1.8.0 | `912a4c0c455928ec05a24fe2001f009dd68322d8aa437c2c9e82d014e5db46d0` |
| Published NeoForge 1.8.0 | `ea6a8e2e3d58cc1ffcb1473ddddc9036d850ae5f773ef108dab1e570eb379b1c` |

Local, ignored evidence: `build/bridge-gameplay-proof/load-proof.json`,
`load-analysis.json`, all per-window raw measurements, four server logs/JFR
recordings and the separately preserved pilot. Regression logs are under
`build/bridge-validation/`. Do not publish generated player identities, worlds
or raw recordings as source. Historical reports keep their tested fixture hashes.

## Limits

Movement uses headless protocol clients, not native GPU clients. This fixture
does not measure exploration/chunk generation, large edits, redstone/hoppers,
protection-plugin interaction, multiple worlds, or arbitrary player behavior.
The bounded host edit sessions do not exercise player command parsing,
permission/protection hooks or a player's undo history.
Frozen bytes do not freeze JVM warmup, entity RNG or packet scheduling. Current
bridge support remains metadata/control only; these tests cannot certify remote
selection editing, clipboard transfer or command simulation.

Primary API/packet references: [source ledger S47](SOURCES.md#s47-controlled-mixed-gameplay-fixture).
Remaining cost/release requirements: [verification gates](SERVER_BRIDGE_VERIFICATION.md#server-cost-acceptance).
