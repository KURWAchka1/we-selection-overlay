# Request-Kind Admission Regression

## Reproducer Before Change

Both fixed-batch candidates are rejected: [six](BRIDGE_BATCH_2026-10-17.md)
loses individual progress, [seven](BRIDGE_BATCH_SEVEN_2026-10-17.md) misses both
p95 gates. Restore original eight before investigating this separate defect.

The deterministic test first observes one 300 us selection request in a separate
slice. Five subsequent 50 us hellos consume 250 us. Original within-slice-only
reservation admits the next 300 us read, overrunning the 500 us target to 550 us.
The test fails on original eight (`expected 5, actual 6`), archived at
`build/bridge-kind-admission-validation/expected-baseline-failure.log` and
`baseline-failure.xml`. This reproduces an avoidable admission defect, not a
proven explanation of every real-server timing outlier.

## Narrow Candidate

Retain the existing eight-visit cap, FIFO, token limits and within-slice largest
observed reservation. Add five owner-thread long slots (unknown plus four request
kinds). Before a non-first, non-expired visit, defer if the last reply-producing
visit of that kind cannot fit in the remaining cooperative target. Do not remove
the entry or spend an aggregate token when deferring. First visits always run;
fresh completed replies replace the estimate, capped at the slice target, so a
cold/paused sample cannot permanently block progress. No wall-clock maintenance,
permission/world cache, new task, per-player history or async WorldEdit access.

Codec header peeking returns only a non-authoritative kind, using the existing
bounded envelope and exact request size. Full decode, actual connection identity,
live permission/session/selector checks remain mandatory. Only a fully decoded
reply-producing call updates its kind, including publication time. Malformed
bodies, unknown/wrong-direction frames and silent invalid sessions do not train
an expensive category. Peer-owned defensive frame copying remains unchanged.
No wire bytes or protocol versions change; player artifacts need rebuilding
because the common codec gains a helper, not because old clients are incompatible.

Source basis: [S72](SOURCES.md#s72-observed-cost-admission), an established estimated
cost/deadline pattern, adapted narrowly rather than importing V8 or claiming a
worst-case runtime bound. A suddenly slow indivisible host call still overruns;
report that honestly, never interrupt/retry an apply or discard the sample.

## Gates Before Retaining

The failing case must pass for source and publication costs, ordinary and wrapped
nanoTime. Cover kind separation, malformed-body non-training, FIFO/token ownership,
expired work, pause recovery, sudden slowdown, reentrant callbacks, cancellation,
live revocation and idle zero-clock behavior. Repeat full Java 21/25 suite, six
native payload codecs, six development runtime builds and both universal packagers.
Archive previous development JARs first; published 1.8.0 remains immutable.

Then test actual native faults and editing through the private desktop plus
cursor guard. Performance acceptance requires a separate predeclared old/new/
new/old moving-load study, original eight companion `2fc45532...` as control,
same numerical gates (both p95 <=0.5 ms, every actor >=95% progress, ack-p95 growth
<=100 ms) and unchanged fixture settings. No concurrent builds/readers during
measurement. Deterministic correctness alone does not close real cost gates.

Candidate SHA-256, pinned before measurement:
`9cbf75252fa6a88cfb5c32d68ccaec8091ae2d8307df558089efabbb0404735b`.
Separate root `build/bridge-kind-admission-proof/26.2/`, identical minimal root
observer, Paper 26.2-133/WE 7.4.5/Java 25, BelowNormal, 32 actors, 400 ticks per
phase, four fresh JVMs; no priority/profiler/batch/quotas intervention. Exact
candidate sources and JAR are preserved in the validation root. No retry until
favorable, no relaxed gates. Rejection remains a rejected performance candidate.

```powershell
./tools/test-paper-bridge-load.ps1 -Java21 <jdk21> -Java25 <jdk25> `
  -Minecraft 26.2 -EditLoad -KindAdmissionComparison -Players 32 `
  -Samples 400 -Repetitions 4 -AcceptEula
node tools/server-bridge-test/analyze-edit-load-proof.mjs <jdk25> 26.2 --kind-control
```

## Observed Result: Rejected And Reverted

`checkedAt=2026-10-17T05:12:02.826Z`; frozen digest
`eae509a7dc54af8896ecc9fe096e171506181c6943df0a26e027211dd314f520`.
All functional/raw/JFR/cleanup checks pass: 2,356 applies, 4,800 matched ticks,
no recorded data loss. Both absolute candidate p95 gates fail, without rounding
0.5151 or 0.5143 down to 0.5. Actor-progress/ack gates pass; they do not override
the rejection. Analyzer sets exit code 2 for rejected performance.

| Round | Variant | Applies | Drain p95 / p99 / max ms | Over target | Ack p95 / max ms |
| --- | --- | ---: | --- | --- | --- |
| 1 | Original | 598 | 0.4760 / 1.2313 / 7.6160 | 18/392 | 341.8503 / 450.6839 |
| 2 | Kind hint | 590 | 0.5151 / 1.1530 / 1.9915 | 21/396 | 388.6660 / 591.7404 |
| 3 | Kind hint | 576 | 0.5143 / 1.4654 / 6.7058 | 21/397 | 449.1675 / 744.6298 |
| 4 | Original | 592 | 0.5382 / 1.6880 / 2.7726 | 24/397 | 373.0001 / 497.0073 |

Minimum paired actor ratios: 97.3333% and 95.9459%; ack-p95 changes +46.8157 and
+76.1674 ms. Mixed wall-time results do not establish a general speedup. Quantized
CPU readings (including zero) do not establish zero CPU work or explain outliers.

Candidate pure suite passes 118 tests on each Java 21/25; all six native payload
codecs, JFR/YAML/agent/admission checks pass. Extended Node harness passes 150
groups. No candidate universal player JARs were built and no real-client join
was run against this candidate. Previous development/public player JARs remain
unchanged. This limitation replaces the planned candidate native checks above.

**Revert the production core and codec helper completely.** Keep only additional
original eight-visit FIFO/nominal-rate tests. The deterministic mixed-cost overrun
remains a known limitation; do not claim it fixed in the retained bridge. Native
fault/gesture regression will use the original `2fc45532...` companion.

The [rejected candidate patch](experiments/bridge-kind-admission-2026-10-17.patch)
reproduces against clean commit `07be42a4a88b03712b97c14617903f8fc8abcce4`;
`git apply --check` passes there. SHA-256
`e6b090f0bbe9f8d1008188bac011bde764cefc684bf25397f9ea6555327907c1`.
Exact sources/JAR/regression results and the old failing test are also preserved
locally under `build/bridge-kind-admission-validation/`; raw server/JFR/proof and
failed numerical analysis stay under the separate study root. Do not overwrite
or relabel these data, tune/repeat until favorable, or close production cost gates.
