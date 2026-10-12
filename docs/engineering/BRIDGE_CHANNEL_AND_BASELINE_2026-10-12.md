# Bridge Channel, Reconnect And Controlled Baseline

Date: **2026-10-12**. Baseline commit: `f78880e`; the accompanying change contains
the source/tests below. Client **1.8.0-bridge-dev**, companion **0.1.0-dev**.
Published 1.8.0 archives/tag are unchanged. This is experimental foundation QA,
not a production companion release or a wider server compatibility claim.

## Reproduced Defect And Fix

The former client handled initial absence but ignored loss of an already-ready
channel. Two new pure tests failed before the fix. An actual Fabric 1.21.11
client, after reading metadata and receiving a real `minecraft:unregister`,
retained READY and the previous metadata for the entire five-second assertion
window. Its negative evidence is preserved locally:

- `build/bridge-validation/channel-before-fix-unit.log` (12 tests, 2 failed).
- `build/bridge-native-proof/channel-before-fix-fabric.log`, stage 20:
  `Channel loss retained state: READY`.
- `build/bridge-native-proof/channel-before-fix-server.log` and private worker
  `build/desktop-tests/WesoTest-d01d1bbbb4394e37baa02cbff47c5c7c.log`.

The shared `BridgeClient.poll` now detects observed availability edges. Loss
clears metadata, epoch, pending work and grants. Re-advertisement starts one
fresh-nonce negotiation; late initial advertisement also recovers after timeout.
A constant advertisement never retries DENIED or a failed negotiation. All six
native adapters additionally reject frames when their public channel check is
false. No frame format, production server code, polling task, source scan,
permission bypass or automatic metadata subscription was added.

Pure regression cases include cached and outstanding metadata, delayed old
welcomes/replies, fresh nonce, 100,000 quiet polls, late advertisement, denial,
negotiation timeout and final disconnect. Universal bytecode tests check both
context and channel guards in tick, receive and explicit request across all six
relocated runtimes.

## Native Dedicated-Server Matrix

Command: `tools/test-native-paper-bridge.ps1 -Java21 <jdk21> -AcceptEula`.
Exact host: Paper **1.21.11-132**, commit `c5eb079`, official WorldEdit **7.4.2**.
Fabric Loader **0.18.4** / API **0.141.3+1.21.11**; NeoForge **21.11.42**.
Temurin **21.0.11+10-LTS**. No local client WorldEdit, OP or protection plugins.
Loopback/offline synthetic identities only. Private Win32 desktop plus cursor
agent, 40 FPS / 2 client CPUs / 1536 MiB heap; Paper 2 CPUs / 1 GiB / BelowNormal.

All six granted/denied/absent runs pass. Both granted native clients also pass:

1. Same-world death/respawn, then Nether and Overworld return without reconnect.
2. Actual wire channel removal; cached state, epoch and pending work clear.
3. Quiet unavailable interval without reads, then one renewed handshake and an
   explicit read after real wire re-advertisement on the same game connection.
4. Empty/recreated selection and permission revocation without breaking CUI.
5. Server kick, verified DISCONNECTED cleanup, then normal client reconnect in
   the same Minecraft process with a different handler/epoch and working CUI.
6. No metadata read merely because the player rejoined; one explicit read works,
   and the final disconnect leaves zero tracked server connection records.

Each granted run performs exactly **8 metadata reads / 15 replies**; each denied
run performs **0 reads / 1 reply**. Cumulative companion totals: **16 reads,
32 replies, 0 retained connections**. Absent companion runs have no bridge state.
Proof timestamp: **2026-10-12T10:42:37.364Z**.

Local proof/logs: `build/bridge-native-proof/native-proof.json`, loader/scenario
logs and `native-server.log`/`absent-server.log`. Guarded worker:
`build/desktop-tests/WesoTest-0faae679d336498e8ed6aa5276c47073.log`.
Native harness now records companion/fixture hashes and rejects forced or failed
Paper shutdown rather than publishing a successful proof after forced termination.

The channel test is explicit wire fault injection, not a plugin reload test.
Bukkit reserves REGISTER/UNREGISTER. `ChannelFaults` is a separately built,
version-pinned test-only packet injector, absent from distributed clients and
companion. Reconnect uses ordinary Minecraft ConnectScreen APIs, no OS input.
See [primary API/source checks S37](SOURCES.md#s37-channel-advertisements-and-native-reconnect).

## Other Regression Gates

- `test-server-bridge.ps1 -Java21 <jdk21> -Java25 <jdk25> -Offline`: **36 pure
  tests per JVM** (protocol 7, client 12, server 13, token bucket 4), all pass.
- Six actual native buffer-codec probes pass: Fabric/NeoForge 1.21.11, 26.1, 26.2;
  these are headless codec checks, not six versioned dedicated-server joins.
- `test-bridge-packaging.ps1 ... -Offline -RerunTests`: six runtime builds and
  **121 Fabric + 11 NeoForge tests** pass. Development hashes match the native
  candidates after the final packager rerun. No published archive overwritten.
- Four frozen-world helper tests and four wire-helper tests pass. Snapshot
  corruption, junctions and paths outside the intended fixture fail before
  removing any live fixture world; exact byte restoration is tested repeatedly.
- `test-paper-bridge.ps1` repeats all **12 wire proof groups**, timestamp
  **2026-10-12T10:49:57.845Z**, including real server respawn/reconnect,
  cross-player rejection, cached revocation, fuzzing and WorldEdit-disable cleanup.
  These protocol clients do not replace the native matrix above.

## Candidate Identity

| Artifact | SHA-256 |
| --- | --- |
| Fabric development universal | `98935c241dfa0fa8fcd03472dd7afeb2b8e55fd82e782ce3df279f2b84a43416` |
| NeoForge development universal | `b07cf96797dc5b8aa08416e62df5c0aed81cf5857438df66291613ade76ca124` |
| Paper companion, unchanged server implementation | `97b60640cdc7e32dd83f3f0f792c32c8bc4c25f41428a8c13b5709686d3b4f7e` |
| Native test fixture, channel/reconnect matrix | `77607183b517cd5306b7036a68791ef8d9d0d3f58d65873b278960c38d700301` |
| Later fixture adding controlled baseline, wire/baseline runs | `a4b8345d2015b4d8e824785306cf74ab3457f128c90c6d128bb89b5264c09fff` |
| Paper 1.21.11-132 | `5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba` |
| Official WorldEdit Bukkit 7.4.2 | `0ee152b1be5dfb51500505e2bf5a8c9d66f09c7fa484bf9aea384d7e7b459b06` |

The fixture gained baseline preparation after native tests; that test-only change
does not alter the tested production companion or client hashes. Raw worlds,
recordings and process metadata remain ignored local evidence, not Git contents.

## Controlled Baseline

The new `-ControlledBaseline` mode uses `build/bridge-baseline-proof/`, leaving
the previous 16-player load pilot intact. It prepares a separate disposable
world, shuts Paper down, freezes all three saved-world directories and verifies
a content-hash manifest. Before every measured server start it restores and
rechecks exactly those files, including synthetic player data. No user world
or running server is moved/deleted. Link, path and corruption checks fail closed.

The console-only fixture checks public gamerule updates, disables natural mob
spawning, random ticks and daylight/weather progression, clears loaded non-player
entities, and holds all 16 synthetic players at the same fixed position before
warmup. The plugin JAR itself does none of this. Every comparison uses the same
Paper, WorldEdit, JVM, fixture, monitoring and JFR configuration.

Command: `test-paper-bridge-load.ps1 -Java21 <jdk21> -Players 16 -Samples 1200
-Repetitions 2 -ControlledBaseline -AcceptEula`. All four ABBA-order windows
completed, **4,800 measured ticks**, timestamp **2026-10-12T10:58:55.734Z**.
Every restored world had digest
`8b69505b7cb060254fcb085b78b3fd1951d38bd245235ae0e5364d6ff934c9f6`.

| Run / companion | p50 ms | p95 ms | p99 ms | Max ms | Owner CPU ms/s | Owner allocated MiB/s |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 absent | 0.7692 | 2.5602 | 16.3605 | 63.2402 | 11.731 | 1.017 |
| 1 installed | 0.7178 | 4.2405 | 16.6205 | 63.3364 | 13.294 | 1.088 |
| 2 installed | 0.6682 | 3.5899 | 16.1739 | 32.5315 | 9.905 | 1.041 |
| 2 absent | 0.6716 | 3.2611 | 15.9428 | 62.7531 | 10.426 | 1.034 |

All measured windows have **zero chunk loads/generation** and zero application
frames sent/received. Both installed windows have zero admitted work, selection
reads, replies and retained bridge connection records. JFR contains **279
execution / 289 allocation samples** across those two installed windows, none
with a production bridge frame. Sampling cannot exclude small costs. CPU and
allocation counters cover the whole owner thread, not just the companion.

Installed p95 exceeds absent p95 by **1.6803 ms** and **0.3288 ms** respectively;
the CPU direction differs between pairs. These measurements neither establish
zero overhead nor attribute the tick-tail difference to the companion. Maxima
above 50 ms also occur without it. This closes the repeatability check for this
quiet fixture, **not performance acceptance** or active/busy-server coverage.

JFR GC totals are explicitly separated from pause time in the streaming reader:

| Run | Collection duration ms | Sum of pauses ms | Longest pause ms |
| --- | ---: | ---: | ---: |
| 1 absent | 584.5984 | 53.4877 | 30.8900 |
| 1 installed | 583.3002 | 40.2949 | 20.8600 |
| 2 installed | 519.4782 | 47.6455 | 29.4564 |
| 2 absent | 0 | 0 | 0 |

These JFR figures include collection events whose **start** falls inside each
window. Legacy profile fields `gcMillis`/`maxGcMillis` mean collection duration,
not stop-the-world time; new `gcPauseMillis`/`maxGcPauseMillis` read the actual
pause fields. They are not interchangeable with fixture MXBean deltas, and do
not align a specific pause with a specific slow tick. See [S39](SOURCES.md#s39-jfr-gc-duration-versus-pauses).
Both the four new windows and all 14 previous pilot windows were re-analysed
successfully with this reader; no server rerun or historical proof replacement
was needed. The legacy active-load path itself was not rerun in this cycle.

Local evidence: `build/bridge-baseline-proof/load-proof.json`,
`load-analysis.json`, `measurements/<run>/silent.json`, recordings and the
`frozen-world/manifest.json`. All owned servers/clients have stopped; the
loopback test port is free. No GUI automation or foreground input was used
during the baseline.

## Limits And Next Gates

- Only explicit observed channel loss/restoration is established. A silent
  server-side plugin disable without a wire signal and a complete between-tick
  channel flap are not detected by this availability sampler. These require a
  separately designed control/lifecycle path; no heartbeat was added to hide them.
- Backend/proxy switches, other dedicated host/version tuples and protection
  integrations are unverified. The 26.x adapter builds/codecs are not host proofs.
- The earlier **134.4245 ms** tick remains unattributed. A new quiet-world
  comparison cannot retroactively explain it or establish a busy-server budget.
- Shared-laptop scheduling, JIT and a short warmup remain confounds. No statement
  of zero overhead, fixed MSPT allowance or production acceptance follows.
- Remote selection transactions, clipboard streaming and command simulation
  remain unimplemented. Published 1.8.0 is immutable; shipped client corrections
  require a later fix release under the existing release policy.
