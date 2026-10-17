# Retained Bridge: Native Regression

## Scope And Guards

Retained companion SHA-256:
`2fc45532e48eb1208dab02cf1134835888f58525c7efb94db75f624ea877a03a`.
The [six](BRIDGE_BATCH_2026-10-17.md), [seven](BRIDGE_BATCH_SEVEN_2026-10-17.md)
and [request-kind](BRIDGE_KIND_ADMISSION_2026-10-17.md) candidates are rejected;
none is used by this regression. Production source and artifact are restored.
Player development JARs remain the previous exact-size-encoding builds:

- Fabric: `f003b66d114f3cc249cfb9f4cefc42b8c5904e11284585fd9d754c5b8f2f3384`.
- NeoForge: `51df054607f4d53fc4ac5f769befccb9e2bc6e7543dda5532d8e421bc0a3fa02`.

Use only `tools/test-native-paper-bridge.ps1`, top-level entry, with EULA acceptance
already granted for disposable loopback fixtures. Each row must pass the real
cursor preflight and private-desktop guards; no foreground activation, desktop
switching, system input, captured cursor or normal user worlds. Native FPS remains
40, 1280x720, two processors and 1536 MiB; Paper uses the existing bounded fixture.
Old fault/gesture roots are moved to `*-before-20261017` after resolving both
targets under owned `build` and rejecting reparse points. Only Paper bootstrap
caches/libraries/versions are copied back, not old proof, logs or worlds.

## Completed Matrix

| Host | WorldEdit | Client loaders | Faults | Gestures |
| --- | --- | --- | --- | --- |
| Paper 1.21.11 / Java 21 | 7.4.2 | Fabric + NeoForge | Pass: six clients | Pass: two clients |
| Paper 26.1.2 / Java 25 | 7.4.3 | Fabric + NeoForge | Pass: six clients | Pass: two clients |
| Paper 26.2 / Java 25 | 7.4.5 | Fabric + NeoForge | Pass: six clients | Pass: two clients |

Fault cases: actual delayed request/response, held late successful acknowledgment,
revocation before backend commit delivery. Require observed client pending state,
canonical server bounds, independent unchanged-world/undo audit, exact apply/capture
counts, no automatic retry, zero final connections/queue/tasks and normal exits.
Gesture cases: all faces/corners, held-G release, Esc, cancellation/stale source,
radial XYZ move, actual synthetic Minecraft callbacks/hit tests, CUI, unchanged
world/undo and two fresh render screenshots per loader. Existing verifier requires
29 captures and 23 applies per complete native gesture client.

Raw roots: `build/bridge-native-fault-proof/` and
`build/bridge-native-gesture-proof/`, legacy at root and exact 26.x children.
Row launcher logs: `build/bridge-native-validation-20261017/`.
This is functional/fault/render regression, not native concurrency, ungraceful
restart/proxy/other-host coverage or production cost acceptance. No row is marked
passed from compilation or another version's proof.

## Completed Rows

1.21.11 faults: `checkedAt=2026-10-17T05:20:33.621Z`, six native clients, four
applies/eight captures, both servers stopped normally. Independent revalidation
of all six entries with `verifyFaultMatrix` passes. Each entry has exact canonical
bounds/world audit, observed pending state and zero final queue/tasks/connections.
Proof SHA-256: `766e65aa780025c147283d5f1e56930bd04b18f83e9461f607bd146932e4f7c6`.

1.21.11 gestures: both native loaders complete 28 cases each (46 applies/58
captures total), with four fresh corner/XYZ screenshots, independent canonical
bounds and unchanged-world/history audits. Both servers stop normally and final
connections/queue/tasks are zero. Independent `verifyGestureMatrix` revalidation
of each loader's 28 cases passes. The stale-source case accounts for the extra
capture: 28 cases are not 29 gestures.
`checkedAt=2026-10-17T05:25:12.140Z`; proof SHA-256:
`2b027abb9e918fd5dc6620246934270854ba7a206430617476be81b498b35cb6`.
Two fresh legacy screenshots were visually inspected: corner draft and XYZ mini
axes are present. These synthetic bright-sky frames do not establish complete
visual/design approval; mod polish is a separate follow-up requested by the owner.

26.1.2 faults: `checkedAt=2026-10-17T05:29:23.929Z`, six clients, four applies/
eight captures, independent `verifyFaultMatrix` passes, both servers stopped.
Proof SHA-256: `98a8463046c3df86a2937ffb23dc511ba91f50c0a0d6452c5373f2b2bae0b6dc`.

26.1.2 gestures: both native loaders complete 28 cases each (46 applies/58
captures total), all per-case canonical bounds/counter checks and full matrix
revalidation pass. Both final queues/tasks/connections are zero; four fresh
screenshots are retained. This is exact 26.1.2 evidence, not all 26.1 patches.
`checkedAt=2026-10-17T05:33:47.076Z`; proof SHA-256:
`438091aec2fa03d57716694a33d42c7be3f652aeca0476739fcf1a0c1d809898`.

26.2 faults: `checkedAt=2026-10-17T05:38:04.301Z`, six native clients, four
applies/eight captures, complete independent fault-matrix revalidation and both
normal server stops pass. Proof SHA-256:
`7422b03bfe004a2a765b1f4897048f43cfcb0496f48e89e84e0c77f5390b4040`.

26.2 gestures: `checkedAt=2026-10-17T05:42:24.188Z`, both loaders complete all
28 cases each, 46 applies/58 captures total, zero final resources, four fresh
screenshots and normal owned shutdown. Proof SHA-256:
`e19e5c938e96d06ce513cf68d9e096f143c4a40ff8c58b5833c3bf66a699055d`.

## Independent Final Revalidation

At `2026-10-17T05:42:54.801Z`, the separate offline revalidator passes all six
saved proofs with the existing fault/gesture/per-case verifiers. It recomputes
the actual companion/Paper/WorldEdit/development-client hashes, checks both
stopped fault servers per host, all final resources and all 12 PNG hashes and
1280x720 dimensions. Output is preserved at
`build/bridge-native-validation-20261017/revalidation.json`; local revalidator
source sits beside it. No proof is inferred from another host's completion.

Totals: **24 native clients, 168 gesture cases, 18 fault cases, 150 applies and
198 captures**, twelve orderly Paper JVMs. No listener remains on 25579/25580
and no owned repository Java process remains after the complete matrix. Published
and development player archives are unchanged. This closes the exact retained
artifact's affected native fault/gesture regression, not production cost gates,
native concurrency, ungraceful loss, streaming/command simulation or other hosts.
The [peer-local cost proposal](BRIDGE_PEER_ADMISSION_PROPOSAL_2026-10-17.md) is a
separate next investigation, not part of this native artifact's implementation.

After restoring the original core/codec, `server-bridge check --rerun-tasks`
passes **114 pure tests per JVM on Java 21 and Java 25**; the retained two new
tests cover eight-visit FIFO/expiry-free progress and nominal 100/s capacity.
The current Node harness passes 150 groups. Both historical batch analyses are
rerun with the final reader after preserving their original JSONs: six still
fails individual progress, seven still fails both p95 gates, both set exit code
2. Revalidation is not another server measurement or a favorable rerun.
Published Fabric/NeoForge SHA-256 remain respectively `912a4c0c...` / `ea6a8e2e...`.
