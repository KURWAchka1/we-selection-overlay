# Bounded Offline Owner Samples

Status: **diagnostic tooling verified; no production or performance fix claimed**.
Follows the rejected [combined-authority experiment](BRIDGE_COMBINED_AUTHORITY_2026-10-17.md).

## Decision And Sources

One stack in a long interval is not enough to choose a production optimization.
Keep Paper's public messaging and all live authority stages intact. Inspect the
same existing recordings offline instead of adding another measured observer.
The exact [Paper 58e6e45 source](https://github.com/PaperMC/Paper/blob/58e6e45/paper-server/src/main/java/org/bukkit/craftbukkit/entity/CraftPlayer.java)
validates messages and checks the channel inside `sendPluginMessage`;
`getListeningPluginChannels` copies its channel set. This identifies possible
work, not its measured share or permission to use internal packets.

Use the public [RecordedEvent API](https://docs.oracle.com/en/java/javase/25/docs/api/jdk.jfr/jdk/jfr/consumer/RecordedEvent.html)
and distinguish missing stacks from [recording truncation](https://docs.oracle.com/en/java/javase/25/docs/api/jdk.jfr/jdk/jfr/consumer/RecordedStackTrace.html).
The [ThreadMXBean contract](https://docs.oracle.com/en/java/javase/25/docs/api/java.management/java/lang/management/ThreadMXBean.html)
does not guarantee nanosecond CPU accuracy. The exact
[OpenJDK 25.0.2+10 Windows implementation](https://github.com/openjdk/jdk25u/blob/jdk-25.0.2%2B10/src/hotspot/os/windows/os_windows.cpp)
uses `GetThreadTimes`. Zero short-call deltas do not prove zero execution or an
OS scheduling cause. Oracle's [Java 25 overview](https://inside.java/2025/10/17/new-in-jdk-25-2-mins/)
explicitly limits JFR CPU-time profiling to Linux; enabling it is not a Windows
solution. Direct JEP pages returned HTTP 403, so no unread JEP text is relied on.

## Implementation

`JfrBridgeSlices.java` still counts every matching owner sample in its selected
intervals. It now also reports up to **eight earliest points per interval**, with
at most **24 frames per point**. Match `sampledThread`, not the event's committer,
and the half-open interval `[start, end)`. Sort across recording buffers; their
iteration order need not be chronological. Existing top-ten/high-water interval
selection and every original metric remain unchanged.

Each point reports timestamp, state, stack presence, original frame count,
recording truncation and our separate frame/output clipping. Class/method labels
are bounded at 512 characters, states at 128, with explicit clipping flags.
JSON quoting preserves quotes, slashes and control characters. No arguments,
actor names, chat, block contents or new live recording are collected.
Missing samples are explicit, not evidence of no work. Data loss still rejects
the recording before any summary. This only changes the offline reader; agent,
bootstrap, companion, client and protocol bytes remain unchanged.

## Regression

The pre-change reader fails the new assertion because sample details are absent;
retain `owner-sample-baseline-failure.log`. Six new deterministic groups cover
sampled-thread versus committer identity, before/inside/after time attribution,
missing stack, nested/truncated stack, output bounds, chronological cross-buffer
selection, escaped/long/null state labels, absent samples and recorded data loss.
The probes deliberately emit **synthetic sampler-shaped events**; they test the
reader and are not evidence of actual CPU sampling quality or server cost.

Full `tools/test-server-bridge.ps1 -Offline` passes: **114 pure tests per Java
21/25 JVM**, **175 Node harness groups**, both test-only agents, **19 JFR-reader
groups plus three YAML/config groups per JVM**, twelve admission-order conditions
per JVM (60/60 ordinary replies each) and all **six native payload-codec adapters**.
Companion packaging remains Java 21 with no client/test/provided dependency.
Logs: `build/bridge-validation`, with the full console retained in
`build/bridge-authority-validation/owner-reader-full-regression.log`.

## Revalidated Real Recordings

At **2026-10-17T07:52:19.463Z**, reread the four 400-tick active phases from the
exact Paper 26.2-133 / WorldEdit 7.4.5 study. Every totals field and every old
interval field compares structurally equal to the original analysis; proof,
analysis and all four JFR hashes remain unchanged. This does not overwrite or
replace the rejected performance decision. No extra server/client is launched.

| Round | Variant | Selected intervals | Intervals with owner samples | Observed point |
| --- | --- | ---: | ---: | --- |
| 1 | Original | 10 | 0 | None recorded |
| 2 | Rejected candidate | 10 | 1 | Dispatcher, 1.3172 ms interval |
| 3 | Rejected candidate | 10 | 1 | Paper send, 12.2695 ms interval |
| 4 | Original | 10 | 1 | ASM class remapping, 1.4458 ms interval |

Each sampled interval has exactly one owner point. The first two stacks fit the
24-frame output bound; the original round-four stack is longer and explicitly
reported as output-clipped, not recording-truncated. Three points among forty
selected intervals are insufficient to attribute their full duration or rank
subsystems. No recorded pause overlap is not proof of preemption. Keep all
outliers and the unchanged 0.5 ms gate; do not use this sparse evidence to retain
the rejected permission change or to remove API checks.

Audit: `build/bridge-owner-audit-20261017/owner-sample-audit.json`, SHA-256
`e629d36272a61c7058267030c03d8c9cd4da38b5d3e731b15fa06f0c6024facc`.
Per-round NDJSON and the read-only reconciliation script under
`build/bridge-authority-validation/audit-owner-samples.mjs` are preserved.
Original analysis SHA-256:
`555ab0e23555eb19d10500092f9b8d4857ccccbc9f0f8e34b0e3966c9b930119`.

## Reproduction And Boundary

```powershell
<jdk25>/bin/java.exe -Xmx256m -XX:ActiveProcessorCount=2 `
  tools/server-bridge-test/JfrBridgeSlices.java `
  build/bridge-authority-proof/26.2/edit-3.jfr drain edit-3-active 400
```

Retained companion: `2fc45532...`, 89,160 bytes. Published 1.8.0 remains immutable.
No native gameplay, ungraceful-loss/backend-switch test or representative
production cost acceptance is implied. Those remain separate open gates.
