# Server Lifecycle And Harness Audit

Date: **2026-10-12**. Source baseline: `54e4b77`. Experimental companion
**0.1.0-dev**; no client code/protocol format changed and no release published.
This cycle uses headless protocol clients, not Minecraft GUI automation.

## Reproduced Startup Defect

`onEnable` checked whether the official WorldEdit plugin was loaded and had the
expected version, but not whether it was enabled. On the real pinned Paper host,
three ordinary companion disable/enable cycles passed. After disabling WorldEdit,
however, a console fixture re-enabled the companion: it registered its channels
and **five event handlers** despite its disabled dependency. The negative
fixture failed with `Bridge resource ownership mismatch: active=false,
listeners=5`; the wire script exited unsuccessfully and wrote no success proof.

Preserved negative evidence: `build/bridge-paper-proof/lifecycle-before-fix-server.log`
(including the disabled WorldEdit followed by companion enable and failed
assertion). Its companion was the preceding candidate, SHA-256
`97b60640cdc7e32dd83f3f0f792c32c8bc4c25f41428a8c13b5709686d3b4f7e`.

The sole production change adds the public `Plugin.isEnabled()` startup guard,
before core construction or channel/listener registration. No scheduling,
world reads, permission changes, protocol frame or client behavior was added.
See [primary checks S40](SOURCES.md#s40-plugin-lifecycle-and-test-process-completion).

## Executed Server Regression

Command: `tools/test-paper-bridge.ps1 -Java21 <jdk21> -PaperJar <pinned-paper>
-WorldEditJar <pinned-worldedit> -NodeDependencies build/bridge-wire-deps
-AcceptEula -Offline`.

Host: Paper **1.21.11-132**, commit `c5eb079`, official WorldEdit
**7.4.2+7450-eb8e82c**, Temurin **21.0.11+10-LTS**, Node **24.21.0** and
`minecraft-protocol` **1.68.0**. Loopback `127.0.0.1:25579`, offline synthetic
identities, explicit test grants, no OP or protection plugins. Paper uses 2 CPUs,
1 GiB heap and BelowNormal process priority. EULA accepted for this disposable
fixture only. No user world or foreground input is involved.

All **14 grouped checks** pass, timestamp **2026-10-12T11:18:04.092Z**.
Existing ownership, permission, cache, malformed-frame, CUI, respawn/reconnect
and dependency-disable checks remain. The new three real disable/enable cycles
assert, for the companion plugin specifically:

- Enabled: one incoming listener registration, the expected incoming/outgoing
  channel sets, five event handlers, a non-null core and no Bukkit pending tasks
  or active workers owned by the companion.
- Disabled: empty incoming/outgoing registrations, zero event handlers/tasks/
  workers and a null core reference. Test-only reflection inspects our own field;
  it is not shipped or used to access Minecraft/WorldEdit internals.
- Re-enabled: zero initial tracked peers/reads/replies; the old epoch yields no
  reply or source read. Explicit hello gets a different epoch and an own-player
  metadata read works. Duplicate reads return identical bytes with no reread.
- Exactly **1 source read / 3 replies** per cycle (welcome, read, duplicate),
  no duplicate callbacks and no counter changes during the quiet interval.
- With WorldEdit disabled, an attempted re-enable stays disabled, holds no
  resources and sends no welcome in response to an explicit hello.

| Candidate / pinned dependency | SHA-256 |
| --- | --- |
| Corrected Paper companion | `6c3776c8f2b637314b4da7a837017b13acfb3dd2756ee0c8806ee844e82a0c21` |
| Lifecycle test fixture | `90e027dffaf4df29d5e364cffde4c987ce6cfdcb4e7683e6889862e304731c24` |
| Paper 1.21.11-132 | `5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba` |
| Official WorldEdit Bukkit 7.4.2 | `0ee152b1be5dfb51500505e2bf5a8c9d66f09c7fa484bf9aea384d7e7b459b06` |

Local proof: `build/bridge-paper-proof/wire-proof.json` and `wire-server.log`.
Earlier native/baseline results refer to the earlier companion hash; they are
not relabelled as native/performance evidence for this new candidate.

## Test Harness Reliability

The former wire runner did not verify Paper's exit status before writing a
success proof. The load runner could treat a forced termination as successful
if it happened to report exit code zero. These are fail-open evidence paths,
not demonstrated production server failures.

All three Paper orchestrators now use one `observeOwnedServer` helper. It is
attached immediately after spawn, waits for `close` rather than just `exit`,
handles process/stdin errors and rejects premature, nonzero, signal or forced
termination. Shutdown is idempotent; wait timers are cleared. Only the exact
spawned child can be terminated, after bounded graceful and termination waits.
Reports are written only after orderly completion.

Eight self-tests cover normal trailing output, idempotence, nonzero/signal exit,
forced termination even with zero exit, failure to terminate, premature zero
exit, process/stdin errors, a real missing executable and a real unresponsive
owned Node process. Together with four snapshot and four wire helper tests,
the main `test-server-bridge.ps1` now runs **16 Node self-tests** automatically.
No Node packages, Minecraft windows or network access are needed for them.

The full `test-server-bridge.ps1 -Java21 <jdk21> -Java25 <jdk25> -Offline`
regression passed: **36 pure tests per JVM**, Java 21 companion packaging/license/
dependency-exclusion checks, and all six Fabric/NeoForge native-buffer codec
probes. After adding the Node step, the integrated script was rerun with
`-SkipNativeCodecs -Offline`: all **16 Node + 36 Java 21 + 36 Java 25** checks
passed. Logs are in `build/bridge-validation/` (`harness-node.log`,
`unit-java21.log`, `unit-java25.log`, `codec-*.log`).

The native and load orchestrators have syntax/shared-helper coverage in this
cycle but have not been rerun with game clients/load windows. The current
development universal JAR hashes still match the preceding native proof:
Fabric `98935c241dfa0fa8fcd03472dd7afeb2b8e55fd82e782ce3df279f2b84a43416`,
NeoForge `b07cf96797dc5b8aa08416e62df5c0aed81cf5857438df66291613ade76ca124`.
No published archive/tag, world snapshot or earlier load/native proof was
replaced. All owned test processes have stopped and port 25579 is free.

## Confirmed Remaining Blockers

Each real companion stop left the bridge channel in the protocol client's
advertised set (`remoteStillAdvertised=true`, all three cycles), even though
Paper-side registrations were empty. Server registration cleanup is therefore
not a reliable native-client invalidation signal on this host. This confirms,
rather than closes, the silent-stop gap. Explicit wire removal/re-advertisement
tested previously is a different scenario.

Before production, define and test a bounded, negotiated lifecycle control
message to discard cached metadata/pending work and handle a fresh server
generation without a heartbeat, source scan or retry loop. It must reject old
generation frames, preserve denial behavior and work with older clients. This
is a required design/test step, **not an implemented protocol change**.

Resource registry checks do not prove absence of all heap/classloader retention.
Full hot reload, WorldEdit re-enable, proxies/backend switching, other host/version
tuples, protection integrations and busy-server performance remain unverified.
The prior p95 differences and 134.4245 ms outlier remain unattributed. No new
MSPT acceptance or zero-overhead claim is made. Remote editing, clipboard and
simulation capabilities remain unimplemented; published 1.8.0 stays immutable.
