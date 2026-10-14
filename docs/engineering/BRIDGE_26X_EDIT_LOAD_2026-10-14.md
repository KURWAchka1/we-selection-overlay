# Exact 26.x Concurrent Selection Editing

Date: **2026-10-14**. Baseline **20c97c1** plus the accompanying test-only changes.
Companion **0.1.0-dev**, application protocol **1.2**. No production source,
published client JAR, development client JAR or companion binary changed.

## Executed Scope

| Exact host / WorldEdit | Fresh JVM runs | Applied commits | Proof timestamp UTC |
| --- | --- | --- | --- |
| Paper 26.2-133 / 7.4.5 | Two pass | 569 + 629 = 1,198 | 2026-10-14T12:54:38.944Z |
| Paper 26.1.2-74 / 7.4.3 | Two pass | 627 + 627 = 1,254 | 2026-10-14T12:57:56.253Z |
| Paper 1.21.11-132 / 7.4.2 regression | Two pass | 627 + 625 = 1,252 | 2026-10-14T13:05:48.364Z |

All six runs use 32 real **headless protocol connections**, not 32 Minecraft
renderers or Fabric/NeoForge native gameplay clients. Eight editors alternate
their own cuboid's X bounds, eight readers request their own metadata, twelve
high-rate senders create admission pressure, and four CUI-only players never
negotiate the bridge. Per-editor/per-reader target interval is 250 ms; flood
interval 80 ms. Production admission/queue limits remain unchanged.

Each fresh server measures idle, active and recovered windows of **400 ticks**,
with five seconds of initial settling. Traffic stops at the active-window end;
in-flight requests drain before the final counter snapshot, so commit counts
and latency samples include that bounded drain, not exclusively the 400 tick
brackets. Measurements are sequential on this shared Windows machine, with
the existing default Paper/spark/JFR policy, not independent hardware trials.
Paper uses two available processors/1 GiB maximum heap. Java 21 runs legacy;
Java 25 runs 26.x. No game windows or OS input are created.

## Functional Results

Across all runs, **3,704** sustained commits return canonical own-player bounds
and revisions. Every ordinary editor progresses: 70..79 acknowledgments each.
No capture occurs during sustained editing; canonical acknowledgments are reused.
Each active run records 1,856..1,916 throttled admissions, proving actual pressure.
Pending slots peak at **28**, the number of negotiating actors. Four silent
players per run receive zero bridge payloads; CUI coordinates stay actor-local.

Each run also proves an actual WorldEdit command invalidates a draft, a foreign
actor's revision is refused, and exact commit replay returns the original bytes
without another host apply or CUI update. Live edit revocation denies the next
transaction without ready selection data. All **48 loaded-block/empty-undo
audits** pass. Every measured window records zero generated chunks.

Idle/recovered windows have zero new bridge reads, captures, commits, applies,
replies or actor requests. Final peers, pending slots and dispatcher tasks are
zero. Both Java 26.2 helper JVMs and all six Paper JVMs terminate normally before
a successful final proof can be written. This is resource ownership evidence,
not a claim that an entire JVM's heap immediately shrinks to one fixed number.

## Timing Observations

| Host / run | Active tick p95 / max ms | Commit reply p95 / max ms |
| --- | --- | --- |
| 26.2 / 1 | 6.0858 / **60.4271** | 388.93 / 551.70 |
| 26.2 / 2 | 3.2079 / 20.0115 | 265.39 / 295.58 |
| 26.1.2 / 1 | 1.6163 / 5.6433 | 256.70 / 320.25 |
| 26.1.2 / 2 | 2.6196 / 7.7133 | 265.11 / 297.60 |
| 1.21.11 / 1 | 2.1442 / 22.4231 | 268.13 / 358.39 |
| 1.21.11 / 2 | 2.1689 / 16.4416 | 268.91 / 323.84 |

The first 26.2 run's tick 816 / active sample 228 has a 60.3706 ms public-event
bracket against Paper's 60.4271 ms duration. The default recording shows no
overlapping recorded GC pause, VM operation, owner park/monitor interval or
owner execution sample in that inferred interval. This does **not** identify
the cause or prove that no such stall happened. Windows thread CPU counters
are coarse and sampling is incomplete. No outlier is removed or attributed to
the bridge, OS, profiler or GC without additional evidence.

Raw cumulative dispatcher slice high-water is 19.6386 / 14.4031 ms on 26.2,
7.2336 / 6.1769 ms on 26.1.2, and 6.3391 / 15.2669 ms on legacy. These lifetime
values include setup, not just the active window, and exceed the **soft 0.5 ms**
target. Cumulative maximum queue wait is 1,050.14 ms in the first 26.2 run;
other runs 355.16..415.51 ms. Neither maintained progress nor the smaller second
run is production cost acceptance. Different client libraries also prevent
claiming that one Minecraft version is inherently faster than another here.

## Evidence And Harness Changes

The supervisor/launcher/analyzer now select the three checksum-pinned exact
tuples, their proper JVMs and separate disposable roots. Other workload modes
are not silently enabled on 26.x. The application workload and correctness
thresholds are unchanged. New 26.2 clients delegate their complete Minecraft
wire protocol to the pinned **GeyserMC MCProtocolLib 26.2-20261012.101744-21**,
whose actual codec is **776 / 26.2**. Existing minecraft-protocol **1.68.0** remains
the 774/775 transport on legacy/26.1.2. No version table is fabricated or aliased.
See [S58 primary-source research](SOURCES.md#s58-exact-26x-concurrent-edit-load)
and the [test helper's resource/safety contract](../../tools/bridge-load-clients/README.md).
Its approximately 23 MB test classpath never enters player downloads.

The analyzer reconciles all **7,200 tick markers**, raw fixture JSON, copied
per-run/final results, percentiles, actual audits, stopped-server logs and
runtime artifact hashes. No JFR recording reports DataLoss. For 26.2 it also
reconciles every actor's bridge-frame count with raw helper payload events,
requires one join/end per actor and rejects hidden parser faults. Eight new
IPC failure tests bring the Node harness to **90 passing checks**. Full
regression passes **95 Java tests on each JVM** (14 protocol, 27 client, 50 core,
four host-policy), all six actual native payload codecs, nine JFR reader checks
and twelve admission-order cases per JVM, plus artifact/agent/bytecode guards.
The Java helper's resolved-codec/outgoing guards and an additional actual
zero-actor launch/normal-exit smoke check pass. The earlier native gameplay/UI
and universal-packager matrices are not rerun in this tooling-only cycle; their
evidence stays attached to the unchanged development client artifacts.

Two failed setup attempts are retained, not counted as passing runs: timestamp
snapshot normalization conflicted with Gradle lock state before server startup;
then the first actual 26.2 server reached all 32 joins but stopped at the old
fixed-time-dimension preparation before measurement. The fixture now skips time
reset in fixed-time vanilla worlds using public Bukkit API. These are test
tooling corrections, not excuses to avoid a necessary production fix.

## Reproduction

After accepting the Minecraft EULA, cache the exact S55/S58 dependencies and
build the helper once with network access as described in its README:

```powershell
.\tools\test-paper-bridge-load.ps1 -Java21 <jdk21> -Java25 <jdk25> -Minecraft 26.2 -Players 32 -Samples 400 -Repetitions 2 -EditLoad -AcceptEula
node tools/server-bridge-test/analyze-edit-load-proof.mjs <jdk25> 26.2
.\tools\test-paper-bridge-load.ps1 -Java21 <jdk21> -Java25 <jdk25> -Minecraft 26.1.2 -Players 32 -Samples 400 -Repetitions 2 -EditLoad -AcceptEula
node tools/server-bridge-test/analyze-edit-load-proof.mjs <jdk25> 26.1.2
.\tools\test-paper-bridge-load.ps1 -Java21 <jdk21> -Players 32 -Samples 400 -Repetitions 2 -EditLoad -AcceptEula
node tools/server-bridge-test/analyze-edit-load-proof.mjs <jdk21>
```

Ignored evidence: `build/bridge-edit-load-proof/26.2/`, `26.1.2/`, and the legacy
root. Each has final proof/analysis, two complete server logs/JFRs/results and
six raw fixture windows. 26.2 additionally has two raw Java-client logs and
runtime classpath hashes. Failed clock setup is under
`26.2/failed-clock-baseline-20261014/`; the Gradle failure report is under
`build/bridge-client-downloads/failed-lock-normalization/`. Earlier legacy
artifacts/logs/JFR/raw measurements remain under `pre-20261014-exact-tuples/`.

Companion SHA256 remains `7e40f70b3fab486358a929da73db4132d31bcd3d10e5bc90b99392dce3c4d956`.
The separate changed test fixture is `4dee35f5a0d9a66194b7af721cdb01a8000cd0b5f7581ed1759ff8d6549146e2`.
Published/development client artifact hashes are unchanged from the preceding
[native fault stage](BRIDGE_26X_EDIT_FAULTS_2026-10-14.md).

## Remaining Gates

Concurrent metadata/edit functionality on the named exact hosts is verified.
Representative production performance is **not accepted**, particularly the
unattributed 60 ms tick and cumulative dispatcher high-water. A matched absent/
installed workload and focused owning-method/safepoint diagnostics on 26.x are
separate follow-ups; do not tune limits or move WorldEdit work off-thread by guess.
Moving/native gameplay load, saturated native lifecycle recovery, ungraceful
dependency loss, proxies/backend switches, protection plugins and dedicated
mod-server hosts remain outside these tests. Remote clipboard/simulation remain
unimplemented. Necessary production client changes still require a separately
versioned release, preserving published 1.8.0 artifacts.
