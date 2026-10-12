# Native Bridge Lifecycle Regression

Date: **2026-10-12**. Baseline: `04a1189ddf31ef3cc9d45e59f892331972940ac2`.
Scope: experimental read-only bridge, not a new public compatibility claim.
Companion **0.1.0-dev**; clients **1.8.0-bridge-dev**. Published 1.8.0 is immutable.

## Reproduced Defects

### Same-World Respawn

An actual Fabric 1.21.11 client received selection metadata, died through the
test-only Paper console fixture, and requested a normal Minecraft respawn.
The local player instance changed while the client world remained the same.
The server correctly invalidated the old epoch, but the native adapter watched
only connection/world identity. It retained the old epoch and did not recover
within the test's 20-second context deadline. This is a reproduced native failure,
not a conclusion drawn only from source inspection.

Negative evidence: `build/bridge-native-proof/respawn-before-fix-fabric.log`,
`respawn-before-fix-server.log` and private-desktop log
`build/desktop-tests/WesoTest-ebb75b769b9c4bb7b1415d4a39dc266e.log`.
Failed assertion: `Context did not recover: same-world respawn`, stage 13.
The previous Fabric candidate SHA-256 was
`d7fe7a8a0c28f522229d65ae745ff082ffc2c15d98fee63b71f4598a1143fc66`.

Fix: capture **connection + world + local player** identity. Apply the same guard
to ticking, explicit requests and incoming replies, clear all references on reset,
and wait for a non-null player before discovery. A real context replacement
starts one fresh nonce/hello; there are no periodic retries, scans or heartbeats.
The canonical adapter was synchronized to all six native runtimes. Late frames
are also checked against the protocol's nonce/epoch/request identity.

### Headless Codec Test Source Contamination

The full verification command failed in `compileBridgePayloadTestJava`: its
recursive source directory included the new NeoForge gameplay fixture while
compiling the Fabric codec probe. Restricting that source set to
`NativeCodecProbe.java` isolates codec checks from gameplay and JFR tools.
This was a verification-infrastructure failure, not a game/mod crash.
Negative log: `build/bridge-validation/codec-before-source-isolation-fix.log`.

## Verification Status

- Complete: six native adapter JAR builds, both development universal packagers.
- Complete: Fabric 121 / NeoForge 11 packaging/domain assertions, zero failures.
- Complete: 34 pure bridge tests on each of Temurin 21.0.11 and 25.0.2.
- Complete: six native codec probes, with only the intended probe source compiled.
- Complete: six real native Paper runs; both loaders pass granted/denied/absent.
- Complete: four Node wire-helper tests, zero failures.
- Complete: repeated real Paper wire proof, 12 grouped checks including stale
  epochs, actual respawn/reconnect, revocation and WorldEdit dependency shutdown.

Native evidence: `build/bridge-native-proof/native-proof.json`, checked at
`2026-10-12T10:17:34.131Z`; individual loader/scenario logs in that directory.
Private-desktop worker log:
`build/desktop-tests/WesoTest-cde4cfe8c14a4e40bb7ed4ef085a8306.log`.
The cursor preflight also passed for both native LWJGL versions.

| Native scenario | Explicit metadata reads | Bridge replies | Retained peers after disconnect |
| --- | ---: | ---: | ---: |
| Fabric granted | 6 | 11 | 0 |
| Fabric denied | 0 | 1 | 0 |
| NeoForge granted | 6 | 11 | 0 |
| NeoForge denied | 0 | 1 | 0 |
| Both without companion | No bridge capability/data | No advertised bridge | Not applicable |

All six native flows passed CUI selection checks. Both granted flows passed
same-world respawn, Nether and Overworld context/height assertions. Replies include
hello/welcome and denial, so they are not equivalent to metadata reads.
These timings are correctness deadlines, not latency/performance benchmarks.

Wire evidence: `build/bridge-paper-proof/wire-proof.json`, checked at
`2026-10-12T10:19:15.099Z`, and `wire-server.log`. Headless TCP clients use
`minecraft-protocol 1.68.0`; they are separate from the native six-client matrix.
Before lifecycle cases the server recorded 14 admitted frames, two malformed
frames, four metadata reads and 11 replies, as expected. Old-epoch rejection,
same-process reconnect and dependency/channel cleanup passed their own assertions.
All owned native/wire clients and Paper servers finished; no listener remained
on port 25579. No benchmark or third-party modpack claim is inferred from this run.

The added pure round trip replaces server/client context while an old reply is
outstanding, delivers old welcome/reply frames during renewed negotiation and
again during a new request, and checks they cannot restore stale state. Renewal
alone performs no metadata read; 100,000 idle polls perform no additional reads.
Packaged-bytecode tests check all three relocated runtimes per loader: tick,
receive and request all use the context guard, which checks every identity and
captures/releases the player reference. Bytecode checks are not native gameplay.

## Native Scenario Contract

Paper **1.21.11-132**, official WorldEdit **7.4.2**, loopback-only offline fixture;
no OP, no client WorldEdit, no protection plugins. Native Fabric Loader **0.18.4**
/ Fabric API **0.141.3+1.21.11**, and NeoForge **21.11.42**; Java **21.0.11**.
Each loader runs granted, denied and absent-companion scenarios.

Granted flow: CUI + hello without metadata reads, explicit bounds read, actual
same-world death/respawn, Nether transition, Overworld return, empty/reseeded
selection, revoke/re-grant and disconnect. Every context transition must produce
a different epoch **without reconnecting the network handler**. No previous
selection/pending request may remain before the next explicit read. Inclusive
bounds are `(10,64,20)..(15,70,27)`; world height is `-64..319` in Overworld and
`0..255` in Nether. Exactly six metadata reads per granted flow are expected.
Denied/absent flows must not disclose selection metadata; disconnect releases peers.

The fixture uses actual player health/respawn/teleport APIs, not fabricated
lifecycle events. Teleportation loads normal gameplay destination chunks; this
matrix is **not** a no-chunk-generation or performance measurement.
All clients run through the private-desktop AND cursor-isolation guards, with
40 FPS/two processors; Paper is headless, two processors, 1 GiB heap, BelowNormal.

Commands:

```powershell
.\tools\test-bridge-packaging.ps1 -Java21 <jdk21> -Java25 <jdk25> -Offline -RerunTests
.\tools\test-server-bridge.ps1 -Java21 <jdk21> -Java25 <jdk25> -Offline
.\tools\test-native-paper-bridge.ps1 -Java21 <jdk21> -AcceptEula
```

## Remaining Gates

The [previous load pilot](BRIDGE_LOAD_AND_LIFECYCLE_2026-10-11.md) still contains
an unattributed 134 ms tick. These correctness tests neither explain nor remove
that observation. Frozen-world, longer controlled busy-server comparisons remain
open; production performance defaults are not approved from this lifecycle run.

Native same-process reconnect, channel removal/re-advertisement, plugin-disable
recovery and proxy/backend changes remain separate gates. Pure/headless lifecycle
checks do not substitute for these native scenarios. Other server versions,
mod-server hosts, FAWE/Folia/proxies/protection integrations remain unverified.
Remote editing, clipboard streaming and command simulation are still unimplemented.
No public client release is made here; follow the separate
[1.8.1 fix-release policy](../release/FIX_RELEASE_POLICY.md).
The archived release hashes were rechecked unchanged: Fabric
`912a4c0c455928ec05a24fe2001f009dd68322d8aa437c2c9e82d014e5db46d0`,
NeoForge `ea6a8e2e3d58cc1ffcb1473ddddc9036d850ae5f773ef108dab1e570eb379b1c`.
Published tag `1.8.0` still dereferences to
`46ffd4e8bbdc30989d43b45bc2b299bfe4e19048`.

## Candidate Artifacts

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| Fabric universal dev | 1,656,875 | `3961519c62e76f3ed2c48237f9a73372faa7e30c4f250c42219ebb3ff2791157` |
| NeoForge universal dev | 1,629,006 | `1fbd2bb850df87fe6c1c5f1e02010e3bb16c57edabdbe5a21dd70082bcbae402` |
| Paper companion | 44,866 | `97b60640cdc7e32dd83f3f0f792c32c8bc4c25f41428a8c13b5709686d3b4f7e` |
| Test-only fixture | 10,400 | `cf9b8079d1b2d631fd02a702fc81507c51bb1e406a784a84f774e5ce958e8485` |

Paper hash: `5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba`.
WorldEdit hash: `0ee152b1be5dfb51500505e2bf5a8c9d66f09c7fa484bf9aea384d7e7b459b06`.
The companion production bytes did not change in this cycle. Client growth versus
the preceding dev candidates is 265 bytes Fabric / 232 bytes NeoForge.
