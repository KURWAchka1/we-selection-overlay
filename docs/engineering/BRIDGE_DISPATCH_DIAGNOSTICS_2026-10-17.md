# Dispatch Cause Investigation

Status: **first live series complete; method-attribution gate incomplete**.
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
