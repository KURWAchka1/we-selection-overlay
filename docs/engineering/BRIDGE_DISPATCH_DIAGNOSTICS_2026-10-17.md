# Dispatch Cause Investigation

Status: **predeclared diagnostic study, not a production optimization**.
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
