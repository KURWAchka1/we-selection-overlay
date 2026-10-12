# One-Shot Graceful Restart Recovery

Date: **2026-10-12**. Inspected baseline: `fe14401`. Source and verification
updates are committed with this report. Companion **0.1.0-dev**; clients
**1.8.0-bridge-dev**. This is experimental read-only transport, not a release.
Published 1.8.0 artifacts/tag are unchanged. Start with the
[knowledge base](JAVA_MINECRAFT_KNOWLEDGE_BASE.md) and [S42](SOURCES.md#s42-opt-in-restart-announcement).

## Gap And Decision

The previous close notification correctly discarded stale state, but Paper kept
the main channel advertised across companion disable/enable. Both clients stayed
unavailable until a new gameplay context or advertisement edge. The previous
proofs are preserved locally as `pre-restart-hint-*`, not relabelled as recovery.

Add a **separate optional S2C channel**, not an unknown main-channel opcode:
`worldedit_selection_overlay:restart`. Exact 22-byte WESR/version-1/kind-1 frame
contains only a nonzero startup generation UUID. All listeners receive the same
generation; it is not an actor, authority token or selection revision.

After successful enable, the Paper host sends one hint to each already-online
listener advertising this channel. Legacy/ordinary peers receive no hint.
There are no registration-event replies, extra listeners, scheduled jobs,
permission probes, WorldEdit reads or server-side recovery registry. Startup
work is O(online players) once per enable, not zero CPU cost. Normal initial
joins still use the original hello; application protocol **1.1** is unchanged.

Only a matching negotiated SessionClosed arms the client. One valid new hint in
UNAVAILABLE consumes that arm and starts fresh nonce negotiation. It neither
publishes data nor implicitly reads a selection. Malformed hints do not consume
the arm; duplicates of the last accepted generation, denied/incompatible states,
timeouts and unarmed contexts cannot start a retry loop. Context/channel loss
clears recovery state. Old welcomes, selection replies and close frames cannot
restore old grants or complete new work.

The hint relies on the current ordered game connection. Keeping one generation
is deliberately bounded, not a replay history: an arbitrary older-than-last
server hint may consume the one attempt but cannot authorize or disclose data.
Existing connection/global admission limits still apply. A saturated restart
may time out and fall back to ordinary CUI, without retrying after capacity
returns. Fair burst recovery needs separate load engineering and acceptance.

## Executed Checks

Commands from the repository root:

```powershell
.\tools\test-server-bridge.ps1 -Java21 <jdk21> -Java25 <jdk25> -Offline
.\tools\test-server-bridge.ps1 -Java21 <jdk21> -Java25 <jdk25> -Offline -SkipNativeCodecs
.\tools\test-bridge-packaging.ps1 -Java21 <jdk21> -Java25 <jdk25> -RerunTests -Offline
.\tools\test-native-paper-bridge.ps1 -Java21 <jdk21> -ModVersion 1.8.0-bridge-dev -AcceptEula
.\tools\test-paper-bridge.ps1 -Java21 <jdk21> -PaperJar <pinned-paper> -WorldEditJar <pinned-we> -NodeDependencies <wire-deps> -AcceptEula -Offline
```

- **50 Java tests per JVM**, Temurin 21.0.11+10 and 25.0.2+10. The full initial
  run had 48 per JVM; the final pure rerun adds replacement-host authorization
  and saturated-admission tests. Final suites: client 19, server 16, token bucket
  4, protocol 11. Zero failures/errors.
- **17 Node harness tests**: owned shutdown, fixture snapshot guards and original
  wire helpers. No new Node test count is claimed for the Java hint codec.
- **Six actual native codecs**: PacketCodec/StreamCodec preserve the exact hint
  bytes, consume the buffer, defensively copy arrays and drop invalid lengths.
  Golden/domain/fuzz tests independently check the Java codec and distinguish
  the new channel from main application frames.
- **Six runtime JARs and 132 universal-packager tests**: both relocated payloads,
  hint codec and state machine present in every runtime; server/fixture/provided
  dependencies remain absent from clients. Only dev artifact tasks were used.
- **17 real Paper wire groups**, including three plugin cycles, no duplicate
  notifications, fresh generations, stale epochs, live permission revocation,
  dependency shutdown and disabled-dependency startup rejection. Minor-zero
  keeps its old contract; a previous minor-one peer gets negotiated close only,
  not an unsupported restart hint. Each enable sent exactly two hints/44 bytes
  to the two opt-in listeners, zero reads before explicit consumer requests.
- **Six real native runs**: Fabric/NeoForge 1.21.11, each allowed/denied/absent.
  Allowed clients recover on the same connection/world/player with a fresh
  epoch, no cached metadata or pending read. After a quiet interval, one explicit
  consumer request succeeds. Denied clients receive a hint but remain DENIED
  and send no new hello. Absent companion still permits normal CUI.

The native allowed scenarios also retain actual same-world respawn,
Nether/Overworld, channel removal/re-advertisement, revocation/re-grant and
same-process reconnect regressions. Before final shutdown each performs eight
explicit metadata reads and fifteen application replies; after recovery the
new core has one hello reply, zero reads until the final explicit request, then
one read/two replies. Disconnect leaves zero core peers. Denied scenarios add
no reads before stop and leave the replacement core at zero reads/replies.
The single startup hint/22 bytes is recorded separately from application replies.

## Exact Evidence

Paper **1.21.11-132**, commit `c5eb079`; official WorldEdit **7.4.2**;
headless TCP library **minecraft-protocol 1.68.0**. No protection plugins or OP.
Native Fabric Loader **0.18.4**, API **0.141.3+1.21.11**; NeoForge **21.11.42**.
No client WorldEdit. Offline synthetic identities on loopback **127.0.0.1:25579**.
The owner previously accepted the disposable server's EULA.

Native proof: `build/bridge-native-proof/native-proof.json`,
**2026-10-12T12:47:27.007Z**. Wire proof:
`build/bridge-paper-proof/wire-proof.json`, **2026-10-12T12:48:35.703Z**.
Logs are adjacent. Pure/codec/package logs: `build/bridge-validation/`;
JUnit XML under the shared protocol/client, core and universal modules' `build/`.

Private-desktop preflight:
`build/desktop-tests/WesoTest-275b2c7c70824a23998d829070bcff1c.log`.
Native worker:
`build/desktop-tests/WesoTest-6b4e7eeb60384af094e9b3c4fd24b9c6.log`.
Both native LWJGL cursor guards passed. No desktop switching, window activation
or OS input injection. Clients capped at 40 FPS/two CPUs/1536 MiB; Paper at two
CPUs/1 GiB/BelowNormal. Owned servers stopped normally before publishing proof.
No new UI screenshot/layout or rendering-pixel acceptance is claimed here.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| Paper companion | 48,921 | `c1472fab548e18341de69621c75cfae56e66bd131acd344613bb01c3a889ad04` |
| Fabric dev universal | 1,672,956 | `cef0cd82a534e630309567270c7f1d53e236b053daed4ac9a5cc00177a166cb7` |
| NeoForge dev universal | 1,645,289 | `16e1359e98642d579efe774c46a3255a5953abdf55cd4f0ad3502c42806fd322` |
| Test fixture, never distributed | 14,461 | `401e1ea16850df552494d817c19b2b7933631c3a140ac14364da2d00bac9b576` |
| Pinned Paper | n/a | `5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba` |
| Official WorldEdit | n/a | `0ee152b1be5dfb51500505e2bf5a8c9d66f09c7fa484bf9aea384d7e7b459b06` |

Compared with the previous candidate: companion +1,708 bytes, Fabric +11,136,
NeoForge +11,322. This is a footprint measurement, not a frame-time benchmark.

## Remaining Gates

This closes only signalled **graceful restart** on the tested host/client tuple.
It is not a recommendation to use `/reload` or a general hot-reload guarantee.
Ungraceful/missed signals, backend switches, between-tick channel flaps and
simultaneous saturated recovery remain separate gates. Busy-server performance,
the previous unattributed long tick and controlled baseline spread are not
accepted or relabelled by this run. No zero-overhead claim is made.

Other Paper versions, dedicated Fabric/NeoForge hosts, FAWE, Folia, proxies and
translators remain unverified. Remote editing, clipboard streaming, command
simulation and switching UI consumers to remote sources remain unimplemented.
Any eventually distributed client change requires the planned **1.8.1** fix
release; this cycle does not overwrite or publish client 1.8.0.
