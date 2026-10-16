# Dispatcher Guard-Band Trial

Two candidate policies are evaluated below. Neither functional success nor a
passing partial pair substitutes for the predeclared performance gates.

## Predeclared Method

The [world-read comparison](BRIDGE_WORLD_READ_2026-10-16.md) retains a 0.5 ms
cooperative target but still crosses it in ordinary active p95. The next candidate
reserves **at least half of the slice** before starting another indivisible visit.
Within-slice measured visit costs may raise that reserve. No clock read, timer,
thread, queue, quota or target is added. A first visit still makes progress.
Unknown host calls and JVM/OS pauses cannot safely be preempted by this mechanism.

Compare exact baseline SHA-256
`aab25479af9b47d330e0fbfd7de495303ae23aec06617c103b5ee2404505d4d6`
against the candidate in **previous / candidate / candidate / previous** fresh
Paper 26.2 JVMs. Preserve the same root-only agent, JFC, inline helper, disabled
spark, frozen world, simultaneous 32-player joins and moving/edit/flood workload.
Do not run builds or offline JFR analysis during measured windows.

Before results, require **both** paired candidate arms to satisfy:

- Active drain p95 <= 0.5 ms, without rounding or changing the soft target.
- Every ordinary editor and reader has >= 95% of its paired baseline progress.
- Edit acknowledgement p95 grows by <= 100 ms against the paired baseline.
- Existing independent functional/raw-wire/canonical/CUI/permission/replay,
  no-chunk-I/O, no-expiry, quiet-window and final-cleanup assertions all pass.

The pure `guard-acceptance.mjs` records each actor and each gate. The analyzer
retains valid functional evidence but exits 2 if performance is rejected. Keep
all maxima/outliers, including unattributed pauses. Revert the guard candidate
if these gates fail; do not silently weaken acceptance after observing data.

```powershell
./tools/test-paper-bridge-load.ps1 -Java21 <jdk21> -Java25 <jdk25> `
  -Minecraft 26.2 -EditLoad -GuardBandComparison -Players 32 -Samples 400 `
  -Repetitions 4 -AcceptEula
node tools/server-bridge-test/analyze-edit-load-proof.mjs <jdk25> 26.2 --guard-control
```

Root: `build/bridge-guard-band-proof/26.2/`. Retain the baseline as
`previous-companion.jar` before build/copy. This is a local cooperative-budget
trial, not a hard real-time guarantee or acceptance of unrelated release gates.

## Rejected Cleanup-Race Attempt

The first attempt stops at **2026-10-16T15:55:50.528Z**, after two completed
arms and the third arm's measured windows/audits. Its final metrics observe two
connections before the server logs `WesoLoad03` and `WesoLoad02` leaving. Client
socket completion plus a fixed 250 ms sleep is not a server-thread quit barrier.
No full proof or performance acceptance is generated from this partial attempt.

All raw logs/JFR/artifacts are retained under
`build/bridge-guard-band-proof-failed-20261016/26.2/`. The fixture now waits up to
5 seconds for **all 32 owning-server quit log events**, then still requires zero
connections, pending slots and tasks from actual metrics. Timeout/fault remains
fatal. The analyzer independently requires those events before final metrics.
This changes only post-window synchronization in every arm, not measured work,
the candidate/baseline, the acceptance gates or production disconnect behavior.
The entire four-arm experiment must be repeated, not resumed at the fourth arm.

Rejected failure JSON SHA-256:
`f4b125cdea8a3cecc1197851203744dc1bb608294254270f30e3f8376812dd0f`.

The completed first pair also retains an unfavorable progress observation:
reader `WesoLoad10` serves 69 versus 73 reads (**94.52%**), below the declared
95% gate. The missing fourth arm prevents full acceptance, not preservation of
this negative evidence. A repeat must not hide it or be described as universal
progress equivalence merely because another local sample passes.

The second attempt is also rejected at **2026-10-16T16:01:20.595Z**: the new
barrier did not normalize Paper's ANSI-colored player names, although all 32
server quits are present. Raw evidence remains in
`build/bridge-guard-band-proof-failed2-20261016/26.2/`. Normalize a copy of lines
with Node's built-in `stripVTControlCharacters`; preserve original raw logs.
The synthetic regression now includes the exact colored/prompt-prefixed format.
This is a test parser defect, not a server cleanup failure or performance result.
Failure JSON SHA-256:
`c003dd12562e327b9c172bb10f30dfcef27cb40cc5dc7495d63567ade477099d`.

## Half-Slice Candidate Rejected

The complete third attempt (**2026-10-16T16:11:19.315Z**) passes 4,800 raw/JFR
tick pairs, 2,212 applies, 37,760 moves, 122,880 fixture WorldEdit cell changes,
128 actor audits and all functional/authority/cleanup gates. No measured tick
exceeds 50 ms. These facts do **not** pass performance acceptance.

| Round | Companion | Drains >0.5 ms / all | Drain p95 / p99 / max (ms) | Applies | Ack p95 / max (ms) |
| --- | --- | --- | --- | --- | --- |
| 1 | Previous | 17 / 395 | 0.4811 / 1.2423 / 1.5574 | 591 | 386.2567 / 539.5509 |
| 2 | Half reserve | 19 / 399 | 0.4671 / 1.1065 / 1.7063 | 544 | 448.6723 / 743.7110 |
| 3 | Half reserve | 41 / 392 | 0.7011 / 2.2080 / 4.8237 | 476 | 693.4939 / 830.3674 |
| 4 | Previous | 10 / 385 | 0.4461 / 0.7470 / 1.2929 | 601 | 340.6572 / 434.7601 |

Minimum per-ordinary-actor ratios are **90.41% / 77.33%**. Ack-p95 changes are
**+62.4156 / +352.8367 ms**. The second pair also fails the drain-p95 gate.
The analyzer emits a retained rejection report and a nonzero exit, not a pass
based on lower allocation from less served work. Reject the half-slice floor.

Retain all raw evidence, analysis and exact candidate source in
`build/bridge-guard-band-proof-half-rejected-20261016/26.2/`.
Candidate SHA-256: `0808a9a645af4dff3765830d7d95304f11a1be94bcc0ddd7f3073ee94c1c6c04`.
Frozen digest: `575de049745ebd1af8362da9e6fdad1a07b260f027ee75246860366f3513c91b`.

## Two-Fifths Candidate: Predeclared Method

The next candidate reduces only the initial reserve to **two fifths of the
slice** (200 us at the unchanged default). This allows one additional 50 us
visit in the deterministic queue test, retaining FIFO/progress/raw-duration
checks. The largest observed visit may still increase the reserve; there are
no additional clock reads/allocations, queues, tasks or quotas.

Repeat the **entire four-JVM previous/candidate/candidate/previous experiment**
against the same `aab25479...5d4d6` baseline, identical root observer/helper/JFC,
world preparation, simultaneous 32 joins and post-window server-quit barrier.
Use the same launcher/analyzer and a fresh guarded evidence root. All previously
declared acceptance thresholds remain unchanged. Retain this candidate only if
both pairs pass every functional and performance gate; otherwise reject it too.

## Two-Fifths Candidate Rejected

Proof **2026-10-16T16:22:15.602Z** passes 4,800 exact raw/JFR ticks, 2,154
applies, 37,984 moves, 122,880 fixture WorldEdit cell changes, 128 independent
actor audits and all functional/authority/cleanup gates. Performance is rejected
in **both** opposite-order pairs; the analyzer exits 2 and retains the report.

| Round | Companion | Drains >0.5 ms / all | Drain p95 / p99 / max (ms) | Applies | Ack p95 / max (ms) |
| --- | --- | --- | --- | --- | --- |
| 1 | Previous | 17 / 392 | 0.4551 / 1.0035 / 2.8383 | 595 | 326.1754 / 541.5963 |
| 2 | Two-fifths reserve | 39 / 399 | 0.9561 / 5.6472 / 13.3009 | 519 | 574.8393 / 682.9558 |
| 3 | Two-fifths reserve | 69 / 398 | 2.1919 / 8.1308 / 12.9662 | 496 | 539.9734 / 635.6887 |
| 4 | Previous | 65 / 394 | 1.6318 / 8.0284 / 17.7110 | 544 | 465.0343 / 673.5867 |

Minimum ordinary-actor ratios are **85.33% / 88.41%**; ack-p95 changes are
**+248.6639 / +74.9391 ms**. Do not claim lower allocation from fewer served
requests as a per-request optimization. The control also deteriorates in the
fourth arm; these local wall results are variable, not a universal causal model.

All raw evidence and exact candidate source remain in
`build/bridge-guard-band-proof/26.2/`.
Candidate SHA-256: `2c84e643625371788cfed638d3a7050fdebd0b328a39515fa4b2e684947e0481`.
Frozen digest: `dcfe8578d380c20b158d3fa9b37a3bf7bd882e211b72cc74d44ea55b8deab7cd`.

Retain the **102.8304 ms** candidate idle tick and **727.8436 ms** control
recovered tick. Both have **zero bridge drain overlap**, and no recorded
GC/VM/safepoint/owner-park/monitor explanation. Their causes remain unknown;
zero quantized CPU is not proof of zero CPU or an OS-scheduling diagnosis.
These are not removed from summaries or assigned to the candidate by proximity.

**Reject both static floors.** Restore the original within-slice observed-visit
reservation with no initial floor. A deterministic regression now requires all
eight 50 us visits to use the available 400 us while retaining FIFO, progress and
raw duration. The separate [combined hot-path trial](BRIDGE_HOTPATH_2026-10-16.md)
tests only the retained necessary-work reductions against the original
reservation-only baseline, with the same predeclared acceptance gates.
