# Combined Edit Authority Trial

Status: **candidate under test, not accepted production or release evidence**.
Baseline source checkpoint: `550aa0c`; original companion SHA-256:
`2fc45532e48eb1208dab02cf1134835888f58525c7efb94db75f624ea877a03a`.

## Inspected Duplication

Core checks read AND edit authority before a capture/commit and after the host
callback, including before cached replies. Paper's existing edit gate itself
checks the edit grant AND the full live read gate. The host operation also
retains its own edit/read check. Thus a fresh edit request invokes the same read
gate five times, although there are three distinct authority stages. These are
logical gate counts, not a claim that all WorldEdit permissions are checked only
five times or that they consume a known share of CPU.

Add a host specialization `mayReadAndEditSelection`. Its default preserves the
existing separate read-AND-edit expression for all other implementations. Paper
delegates to its **unchanged existing edit gate**, which already enforces both.
Core still invokes fresh authority before the host and again before publication;
capture/apply retain their own guard. No grant/world/actor cache, skipped stage,
permission resolver replacement, asynchronous WorldEdit access, rate/queue change
or wire/client change. Negotiation and ordinary metadata reads are unchanged.

The deterministic Paper-like host reproduces **five actual read-gate calls where
three are needed** on the baseline, before editing production. The expected
failure log/XML are retained under `build/bridge-authority-validation`. Eight
candidate tests cover counted stages, fresh cached-result authorization, read
and edit revocation, revocation inside callbacks, reentrant reset, uncertain
post-apply failure, consumed tokens and released drain ownership. Existing default
host tests must pass unchanged on Java 21/25.

## Primary Sources

Checked 2026-10-17. The public [BukkitAdapter 7.4.2](https://github.com/EngineHub/WorldEdit/blob/7.4.2/worldedit-bukkit/src/main/java/com/sk89q/worldedit/bukkit/BukkitAdapter.java)
routes actor adaptation to WorldEdit's [7.4.5 player wrapper](https://github.com/EngineHub/WorldEdit/blob/7.4.5/worldedit-bukkit/src/main/java/com/sk89q/worldedit/bukkit/WorldEditPlugin.java).
The tagged [BukkitPlayer](https://github.com/EngineHub/WorldEdit/blob/7.4.5/worldedit-bukkit/src/main/java/com/sk89q/worldedit/bukkit/BukkitPlayer.java)
uses WorldEdit's world-aware resolver and OP policy, not simply a direct Bukkit
permission lookup. Keep that path intact and fresh. Removing duplicated adapter/
resolver work is an inference from our call graph, not a supported EngineHub
performance claim. No upstream source code is copied.

## Fixed Comparison And Decision

Candidate companion SHA-256, fixed before measurement:
`5518acd05bb06ea9072482b9263c5a3f58368617a0d44e169a2a6c9bc9af860d`.
Both arms use the same current test-only fixture:
`8e6e6f344f55ef890e0dfbd9c49f24b17a56d358e684d75f1502c148d6237995`.
Old property/native evidence for the original companion is never attributed to
this candidate. Preserve published 1.8.0 artifacts and existing proof roots.

One separate `build/bridge-authority-proof/26.2` study: exact Paper 26.2-133,
WorldEdit 7.4.5/Java 25, frozen modern world, fixed BelowNormal priority, root-only
observer and no spark. Four fresh JVMs in previous/candidate/candidate/previous
order, 32 headless actors, identical gameplay/WorldEdit work and 400 measured
ticks per phase. No competing builds or offline readers during measured windows.

Before measurements, complete Java 21/25, Node-policy/frozen-root, agents/JFR/YAML,
admission ordering and all six native-codec prerequisite tests. Keep the same
unchanged acceptance gates: candidate drain p95 <= 0.5 ms in BOTH pairs, every
ordinary actor >= 95% paired progress, ack-p95 regression <= 100 ms, and all raw/
JFR/authority/cleanup checks. Reject and revert this candidate on any failed gate;
do not rename lower allocation as accepted overall cost or rerun for a favorable
sample. Only an accepted candidate proceeds to native/cross-host regression.

```powershell
./tools/test-paper-bridge-load.ps1 -Java21 <jdk21> -Java25 <jdk25> `
  -Minecraft 26.2 -EditLoad -AuthorityComparison -Players 32 `
  -Samples 400 -Repetitions 4 -AcceptEula
node tools/server-bridge-test/analyze-edit-load-proof.mjs <jdk25> 26.2 --authority-control
```
