# Dispatch Cause Investigation

Status: **both live series complete; sampler mechanism and native switch evidence
verified; exact off-CPU durations and production cost acceptance remain open**.
Live recordings: 2026-10-17. Independent hardened-reader review: 2026-10-23.
Original companion remains `2fc45532...`; no permission, scheduling, quota,
wire or client change. Existing studies and published artifacts stay immutable.

## Question

Historical 5-12 ms unassigned intervals have sparse owner samples and quantized
Windows CPU readings. The default JFR locking/I/O thresholds were 20 ms, so an
absence of their events does not exclude shorter stalls. Do not choose a code
optimization or call an interval OS preemption based only on a point sample.

Use Java 25's documented `jdk.MethodTrace` filter on our process/authorization/
capture/apply/send methods and Paper's public message validator/send. Both study
arms lower monitor/wait/park/sleep/file/socket thresholds to zero and enable class
loads. Middle arms additionally trace these methods and collect Windows dispatch
snapshots at the root boundary. Keep the observer intervention explicit.

Microsoft's public `EnableThreadProfiling`/`ReadThreadProfilingData` APIs support
profiling only the current thread, with no hardware counters requested. Our
Windows x64 preflight succeeds without elevation. Data exposes context-switch
count, wait-reason bitmask since last read and raw charged cycles. Use differences
for unsigned cumulative counters; never convert cycles to elapsed time. The raw
cycle value on this host is signed-negative, so its absolute value is not used.
Record read durations; boundary observer work can itself be descheduled.
Disable the counter on the same owning thread before thread exit. No machine-wide
ETW session, driver installation, GUI or input capture is introduced.

Sources checked 2026-10-17:
- [JFR filter API](https://docs.oracle.com/en/java/javase/25/docs/api/jdk.jfr/jdk/jfr/package-summary.html)
- [Oracle JFR troubleshooting](https://docs.oracle.com/en/java/javase/25/troubleshoot/troubleshoot-performance-issues-using-jfr.html)
- [EnableThreadProfiling](https://learn.microsoft.com/en-us/windows/win32/api/winbase/nf-winbase-enablethreadprofiling)
- [ReadThreadProfilingData](https://learn.microsoft.com/en-us/windows/win32/api/winbase/nf-winbase-readthreadprofilingdata)
- [PERFORMANCE_DATA](https://learn.microsoft.com/en-us/windows/win32/api/winnt/ns-winnt-performance_data)
- [DisableThreadProfiling](https://learn.microsoft.com/en-us/windows/win32/api/winbase/nf-winbase-disablethreadprofiling)
- [WinSDK layouts/constants](https://github.com/microsoft/win32metadata/blob/main/generation/WinSDK/RecompiledIdlHeaders/um/winnt.h)
- [FFM native linker](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/foreign/Linker.html)
- [Windows context switches](https://learn.microsoft.com/en-us/windows/win32/procthread/context-switches)
- [Microsoft wait-reason enumeration](https://learn.microsoft.com/en-us/dotnet/api/microsoft.windows.eventtracing.cpu.kernelwaitreason?view=trace-processor-dotnet-1.0)

## Fixed Study

Exact Paper 26.2-133 / WorldEdit 7.4.5 / Java 25, original companion and current
fixture `8e6e6f34...`. Four fresh JVMs: **drain / dispatch / dispatch / drain**,
all BelowNormal with spark disabled, 32 moving headless actors, fixed 400 ticks
per idle/active/recovered phase and one frozen world. Preparation and ordinary
progress/authority/canonical/cleanup checks are unchanged. No compilation or
offline JFR processing during measured windows. Root is separately guarded at
`build/bridge-dispatch-diagnostics/26.2`; existing proofs cannot be overwritten.

This is diagnostic attribution, not another candidate acceptance run. Retain
all intervals; require complete original raw/JFR reconciliation, matched root
and native records, exact instrumentation, zero data loss and owned normal
shutdown with the native counter closed. Inspect observer-read time separately.
Correlated preemption reasons prove a switch occurred, not its exact duration;
method traces locate wall intervals, not CPU work. Use controlled follow-ups if
the observations still leave multiple explanations. Do not relabel this study
as having passed the old strict production p95 gate.

## Preflight

The native API-only busy/sleep preflight and logs are retained under
`build/bridge-authority-validation`. The first agent self-test failed before any
server started because new agent references to ASM were not relocated. Use the
existing Shadow relocation, then verify original return/exception identity,
bootstrap isolation, owning-thread guard, sleep switches, close/reopen and the
onDisable cleanup hook. Original nested/root agent artifacts are unchanged.

```powershell
./tools/test-paper-bridge-load.ps1 -Java21 <jdk21> -Java25 <jdk25> `
  -Minecraft 26.2 -EditLoad -DispatchDiagnostics -Players 32 -Samples 400 `
  -Repetitions 4 -AcceptEula
```

## First Series And Instrumentation Limitation

All four fresh servers stop normally: 595 / 596 / 598 / 590 applies and 4,800
raw/JFR ticks reconcile. The independent method-completeness guard then correctly
fails: Java 25's built-in tracer skips our plugin classes because its hierarchy
resolver cannot obtain `IllegalArgumentException`, `Record` or `RuntimeException`
through Paper's plugin loader. The exact warnings are in `edit-2.log`; this is
not a production mod error. Do not count six absent filters as observed methods
or call full attribution passed. The exact agents are archived inside this
study's `diagnostic-artifacts` before further tooling changes.

Native records and the three actually traced public/diagnostic targets remain
usable narrow evidence. Round 3 root 54 lasts 6.1648 ms, has 1,398,622 raw cycle
delta, six context switches, reason bitmap `80000020` (DispatchInt + Suspended),
no recorded stall/class-load overlap and 119.3 us total counter-read duration.
Its four Paper sends total 79 us. The suspension is observed, not guessed from
a missing Java sample; it does not identify who suspended the thread by itself.
Round 2 also has preemption-only long intervals; cold class loading, a monitor
wait and a file read appear in other roots. No single cause is inferred for all.

`JfrDispatchDetails` has four independent reader groups, including corrupt/missing/
duplicate/foreign counters, lost data and plain-observer controls. Its synthetic
counters validate parsing only. A separate `QueryThreadCycleTime` busy/sleep
cross-check confirms positive short-call cycle deltas even without a context
switch; raw cycles are never converted to elapsed time.

## Controlled Sampler Mechanism

`SamplerCauseProbe` executes fixed 250,000-iteration CPU work 500 times per arm,
with the same native reader. A same-JVM off/20/1/1/20/off pilot shows 0/12/145/
139/12/1 suspended intervals; the final off arm may contain an in-flight sample.
Do not discard it. Repeat in **six fresh JVMs** to remove that carry-over:
**0 / 11 / 139 / 139 / 13 / 0** suspended intervals. Raw cycle totals remain
approximately 676-686 million for the fixed work. General preemption persists
with sampling off. Dense sampling is a mechanism probe, not a server benchmark.
All JFRs/logs are retained under `build/bridge-sampler-cause-20261017` and
`build/bridge-sampler-fresh-20261017`.

Exact [OpenJDK 25.0.2+10 sampler source](https://github.com/openjdk/jdk25u/blob/jdk-25.0.2%2B10/src/hotspot/share/jfr/periodic/sampling/jfrThreadSampler.cpp)
uses platform thread suspension and can return without a successful sample.
Thus no recorded owner sample does not exclude a sampler-induced suspension.
No code is copied from the JDK. A failed optional Gradle `javaexec` launcher
(removed; unavailable on Gradle 9) and an unquoted PowerShell `-D` launch are
preserved as setup failures; no measured server ran in either attempt. The
successful fresh-JVM probe uses the direct Java CLI with quoted properties.

## Predeclared Bridge Sampler Intervention

New isolated root `build/bridge-dispatch-sampling-proof/26.2`. Same exact host,
original companion/current fixture, frozen world, BelowNormal priority, actor
preparation/work and 400-tick windows. Four fresh JVMs **on / off / off / on**.
All use the **same native root observer and same three proven method filters**;
the only recorder-setting difference is `enabled` for `jdk.ExecutionSample`
and `jdk.NativeMethodSample`. Other JFR/stall/class-load settings stay equal.
No build or offline reader competes with measured windows.

Require complete raw/phase/authority/canonical/normal-stop reconciliation, exact
method filters, matched native records and cleanup. Verify recorder settings and
actual samples, not merely the filename. Compare Suspended/Preempted reason
counts and all outliers without trimming. This tests the sampler's causal role,
not a production optimization or permission to waive the existing cost gate.

```powershell
./tools/test-paper-bridge-load.ps1 -Java21 <jdk21> -Java25 <jdk25> `
  -Minecraft 26.2 -EditLoad -DispatchSamplingControl -Players 32 -Samples 400 `
  -Repetitions 4 -AcceptEula
```

## Completed Bridge Intervention

All four fresh servers stop normally and close their owning-thread counter.
Applied commits: **595 / 594 / 589 / 598 = 2,376**. All **4,800** raw/JFR tick
markers, wire/authority/canonical/progress checks and original summaries
reconcile. All three requested method filters actually record in every arm;
there are no method-instrumentation warnings or recorded data-loss events.

An XML comparison verifies that settings differ only in the two sampler enable
bits. Java/native sample counts in the active phases are **32/958, 0/0, 0/0,
26/955**. Thus the off intervention is observed, not inferred from a filename.

| Fresh JVM | Sampler | Roots | p95 ms | p99 ms | Maximum ms | Roots >0.5 ms | With Preempted bit, >0.5 ms | Suspended bit, all roots |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | On | 393 | 0.4622 | 1.3682 | 2.7144 | 15 | 12 | 2 |
| 2 | Off | 398 | 0.5381 | 1.0648 | 1.6067 | 24 | 18 | 0 |
| 3 | Off | 397 | 0.6828 | 2.6139 | 12.3254 | 28 | 26 | 0 |
| 4 | On | 394 | 0.5657 | 1.3845 | 9.1177 | 27 | 24 | 1 |

Across **1,582** roots, **80/94** intervals above 0.5 ms, **27/33** above 1 ms
and **7/7** above 2 ms have a Preempted bit in their enclosing native window.
The last group has no overlapping recorded stall or class load. This does not
measure the duration of a context switch. The observed window also includes
boundary diagnostics; do not subtract charged cycles from wall time or relabel
every nanosecond as scheduler delay.

The sampler is a demonstrated source of Suspended switches, but **not a sufficient
explanation for the longest intervals**. Disabling it neither eliminates those
intervals nor improves every p95. Do not choose a universal sampler-off setting
or declare the strict 0.5 ms cost gate passed from these data.

### Two Retained Outliers

- **Off, round 3, root 253:** 12.3254 ms, two switches, `180000000`
  (DispatchInt + Preempted), 404,068 raw charged-cycle delta, 28.2 us of native
  read durations. One Paper send spans 12.2854 ms; its validator spans 1.4 us.
  There is no overlapping recorded GC/VM/stall/class load or owner sample.
  This reproduces the send-path wall-time symptom **without a JFR sampler**.
- **On, round 4, root 88:** 9.1177 ms, two switches, the same preemption-only
  bitmap, 1,572,313 raw cycles and 85.7 us of native reads. Sixteen Paper sends
  total only 56.4 us; the long interval falls between sends. There is no recorded
  stall/class-load overlap or owner sample. A long root is not necessarily a
  slow send operation, even when earlier sparse samples pointed to sending.

Thirteen >0.5 ms roots have class-load overlap, including six of the seven
zero-switch long roots. Small monitor/file overlaps also exist and remain in
the report. Categories overlap and are not an exclusive CPU-time decomposition.
The remaining 0.5122 ms zero-switch, no-class-load root is not assigned an
invented cause. No live permission, public send path or thread ownership is
bypassed to make the benchmark pass.

### Independent Review And Reproduction

`dispatch-cause-analysis.json` retains the original reader hash and all original
intervals. `dispatch-review-20261023.json` is a new result, not an overwrite:
the hardened reader reproduces every total/interval exactly and rejects extra
unmatched native records. All four JFR hashes remain identical. Its cohort
summary is covered by deterministic strict-boundary/overlap/corruption tests.
The settings validator additionally rejects ambiguous duplicate events/settings,
unrelated recorder changes, DTDs, malformed XML and oversized inputs.

```powershell
node tools/server-bridge-test/analyze-edit-load-proof.mjs <jdk25> 26.2 --dispatch-sampling-control
node tools/server-bridge-test/analyze-dispatch-cause.mjs <jdk25> --sampling-control
# Review requires a new, unused result filename; never overwrite a completed proof.
node tools/server-bridge-test/review-dispatch-proof.mjs <jdk25> dispatch-review-20261023.json
```

Existing completed analysis/review commands deliberately refuse overwrite.
The first series' nine-filter completeness failure remains a failure; the later
successful three-filter experiment does not retroactively change its policy.

## Conclusion And Remaining Gates

Established narrow mechanisms: the JDK sampler can suspend the owner thread;
Windows preemption occurs in newly reproduced long intervals even with both
samplers disabled; cold class loading and occasional real stalls are additional
observations. Exact JDK sources explain sampler suspension with no successful
sample, and Microsoft documents the native fields/reason values. This is stronger
than inferring a cause from missing samples, but not an exact off-CPU timeline.

**Do not claim** that the historical uninstrumented 12.2695 ms instance has been
retroactively attributed, that all 94 overruns share one cause, or that a named
competing process has been identified. This experiment does not establish those
facts. Exact switch durations would require a different trace with separate
privacy/privilege and observer-cost considerations; no machine-wide trace or
priority boost is introduced here.

The retained companion and published clients are unchanged. The strict dispatcher
cost gate, mixed-cost admission limitation and absent/installed representative
production baseline remain release work, not closed by these diagnostic runs.

## Fresh Transport Regression On 2026-10-23

The legacy wire launcher reused one directory and removed its old success report.
It now creates a unique `build/bridge-paper-proof/<UTC-date-time-random-id>/`
directory. Existing evidence is rejected before launch; the log is synchronously
opened with exclusive creation, and the success report also uses exclusive
creation. Unit checks cover path confinement, byte-preserved prior evidence and
the real Windows PowerShell wrapper's refusal before build/copy/launch.

The first fresh attempt, `20261023-161737-b8283aca`, stalls during Paperclip's
Mojang-JAR download. The 180-second startup guard fails; the owned downloader
requires termination after the stop grace period. **No success proof exists.**
Its partial download/log remain untouched. This is a setup/network failure,
not a completed game test or an acceptable shutdown result.

Reuse the existing load launcher's approach for the next attempt: copy only
`cache`, `libraries` and `versions` from the original isolated fixture **if its
Paper JAR SHA-256 matches the requested JAR**. No world, plugin configuration,
player data or prior log is copied. Do not reuse the failed attempt directory.

The separately fresh `20261023-162149-353d58b8` run passes **all 27 wire groups**
on exact Paper 1.21.11-132/c5eb079, WorldEdit 7.4.2 and Java 21.0.11. It covers
grant/revocation, foreign/expired context, malformed/legacy frames, respawn and
reconnect, every face/corner, stale concurrent WorldEdit commands, ordered and
extending selectors, expand/contract/shift authority, unchanged blocks/history,
three disable/enable cycles and dependency shutdown. Original CUI and zero
unsolicited idle source work remain verified. The server stops normally before
success publication. This is functional transport regression, not native GUI,
modern-host revalidation or cost acceptance.

Proof: `build/bridge-paper-proof/20261023-162149-353d58b8/wire-proof.json`,
SHA-256 `1b7bc602bc95dc22d12f5990b1c950b1424320315ff9a208844c939f61fd6d61`.
Original parent `wire-proof.json` and `wire-server.log` hashes are unchanged
across both attempts. Companion remains `2fc45532...`, fixture `8e6e6f34...`
and WorldEdit `0ee152b1...`; the published Fabric/NeoForge JAR hashes also stay
`912a4c0c...` / `ea6a8e2e...`.

## Regression Evidence On 2026-10-23

The full protocol/client/core/Paper JUnit checks are forced to execute, not read
from an up-to-date result: **114 passed on Java 21 and 114 on Java 25**, zero
failures/errors/skips. XML reports are retained separately per JVM under
`build/bridge-authority-validation/junit-java<major>-20261023/`.
Original nested/bootstrap/root agent probes pass on both JVMs, plus the native
dispatch agent probe on Java 25. Historical agent JAR hashes remain byte-identical.

After the wire-launcher changes, rerun the entire Node tooling suite with explicit
`WESO_TEST_JAVA` and `--test-concurrency=1`: **210 pass on Java 21, 214 on Java 25**,
zero failures/cancellations/skips. The four Java-25-only method-trace reader groups
are run on 25, not incorrectly compiled or silently skipped on 21. Combined with
JUnit, this is **652 automated checks**, separately from the 27 live wire groups
and agent probes. Logs: `tooling-final-java21-20261023.log` and
`tooling-final-java25-20261023.log` in `build/bridge-authority-validation`.
JavaScript syntax, Windows PowerShell parsing and `git diff --check` also pass.

Initial offline attempts fail before tests because JUnit and the Shadow plugin
have no usable resolution metadata in the offline cache. Retain those logs;
resolve the same pinned dependencies online, without changing versions, then
execute the suites. These dependency-resolution failures are not counted as
test passes. No native game window, desktop input capture, priority boost or
machine-wide trace is used during this cycle.
