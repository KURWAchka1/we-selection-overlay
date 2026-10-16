# Request-Local World Reference

The [observer study](BRIDGE_DRAIN_OBSERVER_2026-10-16.md) records a sample in
`BukkitWorld` construction during a metadata read. The exact WorldEdit 7.4.5
public adapter creates a wrapper, weak reference and native-access adapter.
The old host independently called `actor.getWorld()` for equality, selection
lookup and both height bounds. This is source-level redundant work; the sample
does not attribute the whole 4.4114 ms interval to constructor CPU.

The candidate resolves the world once **within that synchronous read** and uses
the local reference for those four operations. No world is retained across
requests, no loaded-source policy/permission check is removed, and no work moves
off the owning thread. The core's publication/context guards still apply after
the host returns. Paper's public message path is unchanged.

Compare exact sequence baseline SHA-256
`42528d3b606e6e2cba9142a482d5ba6e95a589ec16fc3b693a57dfe1af9186cd`
to this candidate in **previous / candidate / candidate / previous** fresh
Paper 26.2 JVMs. All arms retain the same minimal drain agent, JFC, inline
MCProtocolLib helper, disabled spark, frozen world, simultaneous 32-player joins
and moving/edit/flood workload. Require the existing independent raw/JFR/wire/
canonical/permission/replay/cleanup gates. Compare progress and latency along
with wall/allocation cost; keep all outliers and the unchanged 0.5 ms target.

```powershell
./tools/test-paper-bridge-load.ps1 -Java21 <jdk21> -Java25 <jdk25> `
  -Minecraft 26.2 -EditLoad -WorldReadComparison -Players 32 -Samples 400 `
  -Repetitions 4 -AcceptEula
node tools/server-bridge-test/analyze-edit-load-proof.mjs <jdk25> 26.2 --world-control
```

Root: `build/bridge-world-read-proof/26.2/`. Preserve the exact sequence baseline
as `previous-companion.jar` before build/copy. Record results and retention only
after normal owned shutdown and offline analysis. Published/client JARs are not
changed by this server-only candidate; native/other-host release gates remain
separate. Source references are in [S67](SOURCES.md#s67-request-local-worldedit-world-adaptation).

## Executed Results

Proof **2026-10-16T15:29:57.326Z** passes 4,800 exact raw/JFR tick pairs,
2,392 applies, 37,728 accepted moves, 122,880 fixture WorldEdit cell changes and
128 independent actor audits across four normal fresh-server shutdowns. The
wire/authority/replay/canonical/CUI/ordinary-progress/cleanup checks pass. Quiet
bridge work, measured chunk I/O/generation, request expiry and final connections,
queue and tasks are zero. No measured tick exceeds 50 ms in this experiment.

| Round | Companion | Drains >0.5 ms / all | Drain p95 / p99 / max (ms) | Applies | Ack p95 / max (ms) |
| --- | --- | --- | --- | --- | --- |
| 1 | Previous | 13 / 395 | 0.4391 / 0.9892 / 2.2866 | 599 | 345.2842 / 591.6472 |
| 2 | Local world | 21 / 398 | 0.5224 / 1.4568 / 2.9896 | 597 | 342.1239 / 604.9848 |
| 3 | Local world | 10 / 392 | 0.4525 / 0.7756 / 1.2744 | 603 | 339.6167 / 496.8771 |
| 4 | Previous | 21 / 399 | 0.5151 / 1.4128 / 14.7261 | 593 | 342.3355 / 607.1335 |

Active drain allocation falls from 7,684,280 / 7,633,376 bytes to
6,733,952 / 6,822,280, with -0.33% / +1.69% applied throughput. Ordinary editor
and reader counts remain comparable. Ack p95 improves by about 3 ms in both
pairs. However, drain p95 changes **+0.0833 / -0.0626 ms**, not a consistent wall
improvement. Quantized CPU is **higher** in both candidate arms (62.5 / 93.75 ms
versus 0 / 31.25 ms); zero is not evidence of free baseline CPU. Keep the raw
unattributed maxima, rather than assigning them to GC/OS without recorded proof.

**Keep request-local reuse for reduced redundant adaptation/allocation**, not as
a proven CPU or p95 fix. The [guard-band trial](BRIDGE_GUARD_BAND_2026-10-16.md)
retains this candidate and predeclares stricter progress/latency/p95 gates.

Candidate companion SHA-256:
`aab25479af9b47d330e0fbfd7de495303ae23aec06617c103b5ee2404505d4d6`.
Frozen digest: `56eeb0e089d06bb81f26ffa6dc03f6af8671ed42bc211eb01e01536bbe0a58c0`.
Original fixture/codec/agents/JFC and all published/development player JARs are
unchanged. This is server-only development evidence, not a new player release.
