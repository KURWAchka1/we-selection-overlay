# Combined Edit Authority Trial

Status: **rejected and fully reverted; no production change retained**.
Baseline source checkpoint: `550aa0c`; original companion SHA-256:
`2fc45532e48eb1208dab02cf1134835888f58525c7efb94db75f624ea877a03a`.

## Inspected Duplication

Core checks read AND edit authority before a capture/commit and after the host
callback, including before cached replies. Paper's existing edit gate itself
checks the edit grant AND the full live read gate. The host operation also
retains its own edit/read check. Thus a fresh edit request invokes the same read
gate five times, although there are three distinct authority stages. These are
logical gate counts, not a claim that all WorldEdit permissions are checked only
five times or that they consume a known share of CPU.

Add a host specialization `mayReadAndEditSelection`. Its default preserves the
existing separate read-AND-edit expression for all other implementations. Paper
delegates to its **unchanged existing edit gate**, which already enforces both.
Core still invokes fresh authority before the host and again before publication;
capture/apply retain their own guard. No grant/world/actor cache, skipped stage,
permission resolver replacement, asynchronous WorldEdit access, rate/queue change
or wire/client change. Negotiation and ordinary metadata reads are unchanged.

The deterministic Paper-like host reproduces **five actual read-gate calls where
three are needed** on the baseline, before editing production. The expected
failure log/XML are retained under `build/bridge-authority-validation`. Eight
candidate tests cover counted stages, fresh cached-result authorization, read
and edit revocation, revocation inside callbacks, reentrant reset, uncertain
post-apply failure, consumed tokens and released drain ownership. Existing default
host tests must pass unchanged on Java 21/25.

## Primary Sources

Checked 2026-10-17. The public [BukkitAdapter 7.4.2](https://github.com/EngineHub/WorldEdit/blob/7.4.2/worldedit-bukkit/src/main/java/com/sk89q/worldedit/bukkit/BukkitAdapter.java)
routes actor adaptation to WorldEdit's [7.4.5 player wrapper](https://github.com/EngineHub/WorldEdit/blob/7.4.5/worldedit-bukkit/src/main/java/com/sk89q/worldedit/bukkit/WorldEditPlugin.java).
The tagged [BukkitPlayer](https://github.com/EngineHub/WorldEdit/blob/7.4.5/worldedit-bukkit/src/main/java/com/sk89q/worldedit/bukkit/BukkitPlayer.java)
uses WorldEdit's world-aware resolver and OP policy, not simply a direct Bukkit
permission lookup. Keep that path intact and fresh. Removing duplicated adapter/
resolver work is an inference from our call graph, not a supported EngineHub
performance claim. No upstream source code is copied.

## Fixed Comparison And Decision

Candidate companion SHA-256, fixed before measurement:
`5518acd05bb06ea9072482b9263c5a3f58368617a0d44e169a2a6c9bc9af860d`.
Both arms use the same current test-only fixture:
`8e6e6f344f55ef890e0dfbd9c49f24b17a56d358e684d75f1502c148d6237995`.
Old property/native evidence for the original companion is never attributed to
this candidate. Preserve published 1.8.0 artifacts and existing proof roots.

One separate `build/bridge-authority-proof/26.2` study: exact Paper 26.2-133,
WorldEdit 7.4.5/Java 25, frozen modern world, fixed BelowNormal priority, root-only
observer and no spark. Four fresh JVMs in previous/candidate/candidate/previous
order, 32 headless actors, identical gameplay/WorldEdit work and 400 measured
ticks per phase. No competing builds or offline readers during measured windows.

Before measurements, complete Java 21/25, Node-policy/frozen-root, agents/JFR/YAML,
admission ordering and all six native-codec prerequisite tests. Keep the same
unchanged acceptance gates: candidate drain p95 <= 0.5 ms in BOTH pairs, every
ordinary actor >= 95% paired progress, ack-p95 regression <= 100 ms, and all raw/
JFR/authority/cleanup checks. Reject and revert this candidate on any failed gate;
do not rename lower allocation as accepted overall cost or rerun for a favorable
sample. Only an accepted candidate proceeds to native/cross-host regression.

```powershell
./tools/test-paper-bridge-load.ps1 -Java21 <jdk21> -Java25 <jdk25> `
  -Minecraft 26.2 -EditLoad -AuthorityComparison -Players 32 `
  -Samples 400 -Repetitions 4 -AcceptEula
node tools/server-bridge-test/analyze-edit-load-proof.mjs <jdk25> 26.2 --authority-control
```

## Completed Prerequisites

Original control test fails exactly at **expected 3, actual 5** read-gate calls.
The candidate passes **122 pure tests per Java 21/25 JVM**, including eight new
authority cases. The complete premeasurement run also passes **175 Node groups**,
both diagnostic agents, 13 JFR-reader and three YAML/config groups per JVM,
all twelve admission-order conditions per JVM (60/60 ordinary replies), the
Java-21/no-client companion packaging guard and six native payload codecs.
Logs were generated in `build/bridge-validation` and are now preserved in
`build/bridge-authority-validation/candidate-regression`, together with
`build/bridge-authority-validation/full-regression-console.log`; original
regression logs are preserved in that latter directory's `previous-regression`.

The first frozen-helper self-test failed because its test-data generator had not
classified the new root as a modern world. Correct the **test setup**, then all
54 policy/frozen-world/acceptance groups pass. No actual measured fixture was
created or modified by that self-test; this is not a production fix.

Before starting the study, exact candidate sources, test source and both JARs
are retained under `build/bridge-authority-validation`. The initial study runner
checkpoint is `7c625e1`; the production candidate remains uncommitted pending the
predeclared decision. No test result so far establishes live cost acceptance.

## Result: Rejected

Analysis at **2026-10-17T07:37:43.553Z**, frozen world SHA-256
`bb1af9e3f8eaaeccfee859f15fa82dd9827b93ea181f3ae6aa16acf3478a5011`.
All functional/raw/JFR/authority/cleanup checks pass: **2,384 APPLIED commits and
4,800 matched ticks**. Both individual-progress and ack-latency gates pass.
The second candidate p95 is **0.5557 ms**, above the unchanged 0.5 ms target.
Reject per the predeclared rule; do not retain this API specialization because
its call counts or allocations look better.

| Round | Variant | Applies | Drain p95 / p99 / max ms | Over target | Ack p95 / max ms |
| --- | --- | ---: | --- | --- | --- |
| 1 | Original | 598 | 0.4644 / 1.1684 / 1.9779 | 18/395 | 324.5679 / 611.6423 |
| 2 | Combined | 598 | 0.4766 / 1.0812 / 2.1900 | 17/395 | 372.5108 / 543.2728 |
| 3 | Combined | 596 | 0.5557 / 6.4517 / 12.2695 | 26/394 | 340.5410 / 495.1021 |
| 4 | Original | 592 | 0.5132 / 1.3018 / 2.1707 | 20/386 | 370.9848 / 542.1064 |

Pair minimum actor ratios: **98.6486% / 100%**; ack-p95 deltas:
**+47.9429 / -30.4438 ms**. Recorded drain allocations fall from 6,246,328 to
6,069,608 bytes in the first pair and 6,168,368 to 6,079,000 in the second.
This is narrower allocation evidence, not accepted overall cost. Summed short-call
CPU readings are quantized (109.375 / 125 / 140.625 / 0 ms); zero is not free work
and differences are not an explanation for the wall-time spikes.

Restore the exact original companion `2fc45532...`, verified after rebuilding.
Both production changes and all eight candidate-specific tests are removed from
the working tree; the [complete candidate patch](experiments/bridge-combined-authority-2026-10-17.patch)
applies cleanly to checkpoint `7c625e1`. SHA-256:
`3c06454a6f5756ab39ae77d27915649ee37cf1826bb38718591ee532963a34c2`.
Exact sources, JARs and candidate regression remain in
`build/bridge-authority-validation`; the full 122-test regression is preserved
under `candidate-regression`. Historical native/property proofs still describe
the restored original artifact, not this rejected candidate. No favorable repeat
or threshold change is made, and no new native pass is claimed.

## Narrow Follow-Up Observation

The five longest third-run drains (12.2695, 8.7614, 7.6411, 6.4517 and 5.3696 ms)
have no recorded GC/VM-operation/safepoint-begin/park/monitor overlap or owner
deoptimization. This does **not** prove OS preemption or a network stall. Four
have a zero short-call CPU delta; the fifth has a quantized 15.625 ms reading.
Keep all raw intervals, not an adjusted distribution with them removed.

A separate bounded, read-only JFR consumer at
`build/bridge-authority-validation/IntervalSamples.java` extracts the one recorded
owner execution sample in the longest interval. At **07:34:25.040476700Z**, near
that interval's end, its stack is in `CraftPlayer.sendPluginMessage` via our
`sendReply`, with state `STATE_RUNNABLE`. The 18-frame stack is retained in
`longest-owner-sample.log`. That is a **point observation**, not proof that all
12.2695 ms occurred in send, nor permission to bypass Paper's public messaging
API. Other long intervals have no owner sample. More authority microchanges are
not justified by this recording; investigate the unexplained send/scheduling
intervals before choosing another production optimization.

## Restoration Verification

The restored sources complete full Gradle `check --offline --rerun-tasks` on
both Java 21 and 25 (114 tests per JVM across core, Paper, shared client and
protocol; no failures or skipped cases). Logs: `restored-java21.log` and
`restored-java25.log` in the authority-validation directory. The companion
rebuild is byte-identical to the retained 89,160-byte original `2fc45532...`.
Published Fabric and NeoForge 1.8.0 hashes remain `912a4c0c...` and `ea6a8e2e...`.
`git apply --check` accepts the complete rejected patch. All owned study JVMs
have exited; no native Minecraft client or input-capture test was started.
