# Peer-Local Admission Proposal

Status: **proposal, not implemented or accepted**. Do not modify the companion
while the original-hash native regression matrix is running.

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
