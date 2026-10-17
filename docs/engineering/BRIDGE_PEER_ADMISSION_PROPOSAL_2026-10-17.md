# Peer-Local Admission Trial

Status: **rejected and fully reverted**. Implementation starts
only after the original-hash native matrix completes all six rows and its
independent revalidation passes; that checkpoint is committed as `77b028d`.

## Motivation And Boundaries

The rejected [request-kind candidate](BRIDGE_KIND_ADMISSION_2026-10-17.md)
demonstrates that remembered cost can fix a specific mixed-cost overrun, but does
not pass both live p95 gates. It adds header peeking, a ByteBuffer wrapper per
visit and an extra clock call per reply. A request-kind estimate is also shared
between actors with different permissions/worlds/selectors. These are concrete
design tradeoffs, **not proven causes** of the observed p95 values.

Investigate a narrower owner-thread alternative: one last reply-visit duration
per already-bounded peer, no header classifier, no codec/client change, no extra
clock calls. Reuse the existing next-iteration `now - previousVisit` measurement;
for the final reply of a slice, use the existing finally clock measurement.
Only full decode/reply-producing visits learn a duration, including publication.
An actual connection owns this value; an actor cannot alter another actor's hint
through a fabricated payload identity. No live permission/session/world cache.

Keep eight visits, the original within-slice largest reservation, FIFO, pending
expiry and token limits. Defer a non-first live head if its peer's last observed
duration does not fit; do not dequeue or spend the global token. First visits
always run and replace the estimate after completion. Clip remembered durations
to the slice target; no idle task, decay timer, fixed floor or queue reordering.
Memory cost is one long per admitted peer (at most 1,024 by current default),
not an unbounded history. Shutdown/disconnect naturally release it with the peer.

Tradeoff: different commands by one peer can have different costs. This is a
conservative last-visit estimate, not request-kind discrimination or WCET. A
previous expensive read can defer that same actor's cheap next command by one
slice; test actual actor progress and acknowledgment latency, not just averages.
Malformed/silent calls do not erase the last legitimate observed cost. Capture
the pending peer before publication so exceptional/reentrant callbacks still
record the whole visit and retain existing exception/cleanup semantics.

## Preimplementation Gates

First save a failing test on original eight for the known 300 us read after five
50 us hellos, source/publication variants and signed nanoTime wrap. Additional
tests must prove no new clock calls, no cross-peer estimate penalty, FIFO/global
token ownership, bounded pause recovery, expiry under exhausted quota, malformed
input, callback exceptions/reentrancy, revocation, duplicate apply and idle zero
work. Existing Java 21/25 and admission probes must stay green.

If implemented, pin its own candidate hash and separate study root before any
data, original `2fc45532...` as control. One four-JVM old/new/new/old exact 26.2
moving-load study; unchanged 0.5 ms p95 / individual 95% progress / +100 ms ack-p95
gates, all raw/JFR/authority/cleanup checks. No favorable repeats, threshold changes
or statistical claims from quantized CPU zero. Reject/revert on failure. Only a
retained candidate proceeds to affected native/cross-host regression. Current
native evidence proves the original hash, never an untested future candidate.

Research basis: [S72](SOURCES.md#s72-observed-cost-admission) estimated admission
and elapsed-time differences, bounded owner-thread state from the existing server
design. This proposal imports no V8 code and promises no hard preemption.

## Baseline Reproduction And Candidate

The original eight-visit core fails the new mixed-cost test before modification:
expected five visits, actual six. Preserve the log/XML at
`build/bridge-peer-admission-validation/expected-baseline-failure.log` and
`baseline-failure.xml`. The first candidate passes the whole core suite, including
source/publication and signed-wrap variants. Additional clock-count, cross-peer,
malformed-body, paused-peer and publication-exception tests precede any live study.
The production change is one peer-local long and existing-clock bookkeeping in
`drain`; no protocol/codec/client source is changed. Native evidence for `2fc...`
must not be attributed to this untested candidate.

The expanded test suite initially does not compile because its new cross-peer
test omits the `HashMap` import. Fix that test-only import, retain the failure
log, then rerun the full core suite successfully before any server measurement.

Candidate companion SHA-256, fixed before measurement:
`6815f2b33d3da765b18385f3fa2bbcb26f4bf4334eafafdd2801196bd458fcdd`.
No further production edits inside the study. Separate root
`build/bridge-peer-admission-proof/26.2/`, identical root observer, fixed
BelowNormal, exact Paper 26.2-133/WE 7.4.5/Java 25, 32 actors, 400 ticks per phase,
four fresh JVMs, frozen world and server-quit barrier. No competing builds or
offline JFR readers while measured windows run. Previous native evidence and all
rejected roots remain untouched.

Before measurement: 120 pure tests per Java 21/25 JVM pass, including all six new
peer-cost cases; extended Node harness passes 152 groups. Full regression also
covers both agents/JFR/YAML readers, admission ordering and six native payload
codecs. These are prerequisite checks, not live performance acceptance.

```powershell
./tools/test-paper-bridge-load.ps1 -Java21 <jdk21> -Java25 <jdk25> `
  -Minecraft 26.2 -EditLoad -PeerAdmissionComparison -Players 32 `
  -Samples 400 -Repetitions 4 -AcceptEula
node tools/server-bridge-test/analyze-edit-load-proof.mjs <jdk25> 26.2 --peer-control
```

## Result: Rejected

Analysis at `2026-10-17T06:08:06.290Z`; frozen digest:
`488af81e837bd5b386085ae0b25bfc3b7dc2cc28fa97dacbeaac0eeb8ff37bc3`.
Functional/raw/JFR/cleanup checks pass, 2,353 applies and 4,800 matched ticks.
Both candidate p95 values pass, but **both individual-progress gates fail**;
the second ack-p95 delta also exceeds the unchanged +100 ms allowance.

| Round | Variant | Applies | Drain p95 / p99 / max ms | Over target | Ack p95 / max ms |
| --- | --- | ---: | --- | --- | --- |
| 1 | Original | 598 | 0.4930 / 0.9893 / 1.2891 | 19/396 | 340.9020 / 495.1947 |
| 2 | Peer estimate | 577 | 0.4459 / 1.0465 / 1.6747 | 11/397 | 434.8098 / 636.7937 |
| 3 | Peer estimate | 572 | 0.4368 / 1.3023 / 2.0563 | 11/390 | 433.7991 / 605.3808 |
| 4 | Original | 606 | 0.4688 / 0.8443 / 1.6286 | 16/396 | 310.6242 / 528.2042 |

First pair: `WesoLoad13` reader 70/74 = 94.5946%; ack-p95 +93.9078 ms.
Second pair: 11 ordinary actors below 95%, minimum `WesoLoad01` editor 71/77 =
92.2078%; ack-p95 +123.1749 ms. Do not use aggregate progress or lower p95 to
override either gate. Raw values remain exact in `edit-load-analysis.json`.

Restore original `2fc45532...` companion. No codec/client/published artifact
changed. The mixed-cost counterexample remains open, not fixed in retained
production. Do not retain an adaptive scheduling penalty because it compiles or
looks faster. No favorable repeat or threshold adjustment is authorized by this
study; investigate avoidable per-visit work separately before more backoff.

The [exact candidate patch](experiments/bridge-peer-admission-2026-10-17.patch)
applies to baseline `77b028d`; source copies/JAR/regression/failing-test evidence
remain in `build/bridge-peer-admission-validation/`, live raw proof/JFR/logs and
failed analysis in `build/bridge-peer-admission-proof/26.2/`. Independent
`git apply --check` passes after restoration. Native matrix in the preceding
commit still describes only the original companion, never this rejected variant.
Patch SHA-256: `cae0dd6b63b640b43591019a5f5f6f001f7f58244cc366dab7cefe38e34d4f16`.
