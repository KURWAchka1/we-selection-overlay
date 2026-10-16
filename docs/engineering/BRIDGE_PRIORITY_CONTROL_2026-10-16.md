# Owning-Server Scheduling Control

## Predeclared Method

The [combined hot-path trial](BRIDGE_HOTPATH_2026-10-16.md) improves both paired
service/latency comparisons but misses the 0.5 ms active drain-p95 gate in one
arm (0.5348 ms). A sampled long interval executes WorldEdit's public cuboid
contract path; it does not establish the cause of that whole interval. Existing
headless servers explicitly use Windows `BelowNormal` priority. Do not assume
that changing production arithmetic/API calls can eliminate scheduling delay.

Microsoft's [scheduling documentation](SOURCES.md#s69-windows-scheduling-control)
motivates an isolated experiment, **not** attribution of the old pauses.
Keep the exact production companion fixed at SHA-256
`aab25479af9b47d330e0fbfd7de495303ae23aec06617c103b5ee2404505d4d6`.
Change only the **owned server process** priority in four fresh JVMs:
**BelowNormal / Normal / Normal / BelowNormal**. Never use AboveNormal, High,
Realtime, change a user's application priority, open game windows or capture
the cursor. Keep the same 2-processor JVM setting, heap, minimal root observer,
inline helper, JFC, disabled spark, frozen starting world, 32 simultaneous joins,
moving/edit/flood workload, quotas and quit barrier. Do not run competing builds
or offline readers during measured windows.

Use Node's public `setPriority/getPriority`. Verify the chosen value at process
startup, before idle, after active and after recovered windows; preserve all four
readings. A wrong value or failed read/set fails the run and still follows owned
shutdown. This is a test-process policy, not a change to the plugin/client or a
recommendation to increase a production server's priority.

All functional/raw/JFR/CUI/canonical/authority/replay/no-chunk-I/O/no-expiry/
quiet-window/cleanup gates remain. Before data, use the same performance checks
on both Normal arms against their paired BelowNormal arms: p95 <=0.5 ms, >=95%
progress for **every** ordinary actor, ack-p95 growth <=100 ms. Keep every
outlier, unknown cause and negative result. A passing local Normal comparison
does not erase previous failed gates or establish a hard deadline; unchanged
results would reject priority as a useful explanation in this experiment.

```powershell
./tools/test-paper-bridge-load.ps1 -Java21 <jdk21> -Java25 <jdk25> `
  -Minecraft 26.2 -EditLoad -PriorityControl -Players 32 -Samples 400 `
  -Repetitions 4 -AcceptEula
node tools/server-bridge-test/analyze-edit-load-proof.mjs <jdk25> 26.2 --priority-control
```

Root: `build/bridge-priority-proof/26.2/`, separately guarded against reuse.
Production/client artifacts and existing study defaults remain unchanged.

## Executed Results

Proof **2026-10-16T16:54:44.044Z** passes 4,800 exact raw/JFR tick pairs,
2,379 applies, 37,824 accepted moves, 122,880 fixture WorldEdit cell changes,
128 actor audits and every functional/authority/replay/canonical/CUI/cleanup
gate. All four priority readings per arm match the chosen value (Node 10 for
BelowNormal, 0 for Normal); an independent PowerShell observation also sees
Normal during run 3. Quiet bridge work, measured chunk I/O/generation, request
expiry and final resources are zero. No measured tick exceeds 50 ms here.

| Round | Priority | Drains >0.5 ms / all | Drain p95 / p99 / max (ms) | Applies | Ack p95 / max (ms) |
| --- | --- | --- | --- | --- | --- |
| 1 | BelowNormal | 16 / 394 | 0.4639 / 2.3771 / 10.2177 | 603 | 322.1054 / 479.6069 |
| 2 | Normal | 9 / 394 | 0.4300 / 0.8677 / 1.3737 | 599 | 323.3422 / 588.0948 |
| 3 | Normal | 20 / 388 | 0.5037 / 2.0352 / 3.4265 | 590 | 343.1010 / 636.1435 |
| 4 | BelowNormal | 20 / 392 | 0.5101 / 1.0370 / 5.1099 | 587 | 387.1160 / 495.0828 |

Every ordinary actor passes the paired progress gate (minimum ratios 97.37% /
98.65%). Ack-p95 changes +1.2368 / -44.0150 ms and passes both gates. However,
run 3 p95 is **0.5037 ms**, 3.7 us above the unchanged target. The analyzer
retains a rejected performance report and exits **2**, not a rounded success.
Normal does not consistently improve p99 and does not eliminate raw outliers.

**Reject priority as a complete solution.** Leave previous workload defaults
BelowNormal. This is only a diagnostic control, not permission to boost a user's
server or to assign all old delays to scheduling. Production bytes stay fixed;
no unknown outlier is relabeled. The next code candidate instead removes the
codec's independently verified temporary array/copy, with separate exact-byte
and compatibility tests before performance measurement.

Frozen digest: `aef0c3ca62d5868e5edc44f0930f9456300facb3a74966aa565a981e46aba135`.
The final post-tooling regression passes **142 Node groups**; the preceding
Java 21/25, agent/JFR and six codec regressions remain as documented in the
[combined hot-path report](BRIDGE_HOTPATH_2026-10-16.md).
