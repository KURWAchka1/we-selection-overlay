# Optional Server Bridge: Engineering Design

Status: **long-term design, not a frozen wire specification**. A bounded,
metadata transport foundation and narrow Paper cuboid editing are implemented.
Development consumers cover all six adapters; the exact 26.1.2 and 26.2 host
[gesture checks](BRIDGE_26X_REMOTE_EDITING_2026-10-13.md) and later
[negative/lifecycle checks](BRIDGE_26X_LIFECYCLE_2026-10-14.md) are supplemented by
the [exact native fault suite](BRIDGE_26X_EDIT_FAULTS_2026-10-14.md).
Only its completed rows establish the named delay/late-ack/revocation cases,
not broader version/network-fault/performance support. Streaming/simulation, other server hosts
and broader editing remain proposals.
See [remote-editing scope and evidence](BRIDGE_REMOTE_SELECTION_2026-10-13.md),
[executed foundation evidence](SERVER_BRIDGE_IMPLEMENTATION.md)
and the narrower [experimental schema](SERVER_BRIDGE_PROTOCOL.md).
Research date: 2026-10-11; exact fault research updated 2026-10-14 in [S57](SOURCES.md#s57-exact-native-fault-packet-discovery).
Start with the [knowledge base](JAVA_MINECRAFT_KNOWLEDGE_BASE.md).

## Product Boundary

The companion exposes permitted WorldEdit session capabilities to our client;
it is not a replacement WorldEdit, protection bypass, remote command console,
schematic file server, or a reason to modify ordinary Minecraft terrain packets.

| Environment | Existing 1.8.0 | Proposed updated client + companion |
| --- | --- | --- |
| Integrated singleplayer with compatible WorldEdit | Local command/clipboard preview and cuboid editing | Preserve current local sources |
| Remote server with WorldEdit CUI only | Selection overlay; local inspection/isolation of received chunks | Same fallback, no invented server session |
| Remote supported server with compatible companion | No bridge protocol support yet | Only negotiated and authorized capabilities |
| No companion / denied permission / incompatible protocol | Ordinary client behavior | Connection and CUI remain usable; remote capability unavailable |

Retain clipboard preview's off-by-default setting, current visual styles and
command-versus-clipboard ownership. No live `//cut` or `//paste` preview is added
by this plan. Do not revive rejected air-isolation or partial-transparency experiments.

## Module And Dependency Plan

Reuse existing renderers, controllers, pure cuboid drafting and safe simulation
code. Do not copy all client projects into a plugin or import their GUI classes.
The smallest useful boundaries are:

| Proposed boundary | Responsibilities | Must not depend on |
| --- | --- | --- |
| Protocol | JDK 21 message values, bounded byte codecs, version/capability contract | Minecraft, WorldEdit, loader, Netty or GUI classes |
| Server service | Player-owned request state, authorization, revisions, fair scheduling; WorldEdit domain adapter | Client classes or renderer internals |
| Paper host | Plugin lifecycle, Bukkit player adaptation, plugin messaging, scheduling | Fabric/NeoForge runtime classes |
| Fabric / NeoForge hosts | Dedicated-server lifecycle, actor adaptation, native optional payload registration | Client-only entrypoints |
| Client remote sources | Handshake, decoding, stale-response checks; existing source interfaces | Direct server object access |

Names/folder layout are not final. Add modules only when implementation needs
these boundaries. WorldEdit/platform APIs are provided dependencies, not shaded
copies inside the plugin. Verify ABI against 7.4.2, 7.4.3 and 7.4.5 rather than
assuming common package names guarantee compatibility. New dependency reuse must
preserve licenses/notices; research examples are not permission to copy blindly.
See [WorldEdit API setup](SOURCES.md#s01-worldedit-api-setup) and existing
[third-party policy](../../THIRD_PARTY.md).

**Rollout proposal:** Paper 1.21.11 transport and session proof first, then the
other supported Minecraft tuples and Fabric/NeoForge server hosts. Paper first
does not mean Paper-only shared logic. Spigot/Purpur, FAWE, Folia, proxies and
protocol translators each need explicit verification; none is implicit support.

## Authority And Permissions

1. Derive identity from the incoming player connection. A payload cannot choose
   another player's UUID, world, actor, clipboard, permission level or OP status.
2. A capability is availability information, **not authorization**. Recheck current
   permissions and context on every operation, before data publication and before
   applying a selection transaction. Invalidate active transfers after revocation.
3. Implemented selection nodes: `weso.bridge.selection` (read) and
   `weso.bridge.selection.edit` (additional cuboid edit grant, default false).
   Proposed later nodes include `weso.bridge.clipboard` and
   `weso.bridge.preview`. Require each applicable WorldEdit permission as well.
   Sensitive capabilities should initially be opt-in/admin-granted, not default true.
4. Selection shifting needs `worldedit.selection.shift`; resizing needs expand
   and/or contract according to the actual delta, not the client-declared mode.
5. Copy permission alone is not a universal right to read server world/NBT data.
   Define clipboard-preview authorization for buffers obtained through copy, cut,
   schematic load or another plugin. Preview sends only the requesting player's
   sanitized permitted data, never other actors' buffers.
6. Anti-xray, claims and custom plugins are separate policies. A raw snapshot can
   expose real hidden states even in a tracked chunk. Keep authoritative source
   data on the server; gated result differences can also leak it. Restricted
   players must be denied sensitive previews unless a verified visibility policy
   permits them. Do not claim the WorldEdit API automatically enforces every claim.
7. Selection adjustment changes metadata, not block ownership. Later destructive
   execution remains the normal WorldEdit command path with its protections.
   If protected-result fidelity is unavailable, do not label simulation a guaranteed
   execution result. See [WorldGuard protection queries](SOURCES.md#s26-worldguard-protection-query).

## Connection And Protocol Proposal

Use an independently owned namespaced **play-phase** payload, provisionally
`worldedit_selection_overlay:bridge`. Leave `worldedit:cui` untouched.
Paper uses incoming/outgoing plugin-channel registration; mods use their native
payload API. Identical application bytes must work through both adapters.
No login requirement, extra ports, external accounts or client secrets.

Connection states: `UNAVAILABLE -> NEGOTIATING -> READY`, with `DENIED` and
`INCOMPATIBLE` outcomes that fall back to ordinary CUI. Reset on logout,
dimension/world context change, backend transfer and server lifecycle changes.
World-context changes require renewed context binding, not necessarily a new
transport handshake. Discovery must be bounded, not a hello packet every tick.

The handshake negotiates major/minor schema, supported operation/command sets,
compression, frame bounds and current grants. Optional loader registration is
not enough: test vanilla/Paper connections, advertised channels and missing
receiver behavior. NeoForge's registrar version is not cross-loader negotiation.
See [transport sources](SOURCES.md#s17-neoforge-networking).

Each operation is bound to connection epoch, server-issued context token,
monotonic request ID and the relevant selection/clipboard revision. Tokens are
freshness handles tied to a player, not authentication credentials or trusted
serialized Java references. Define binary field order, integer encodings, UTF-8
byte lengths, optional fields and unknown-opcode behavior in golden codec tests
before declaring protocol v1 stable.

| Proposed message group | Direction | Contract |
| --- | --- | --- |
| Hello / capabilities | Both | No session content before negotiation and authorization |
| Selection state / bounded change request / result | Both | Expected revision, canonical bounds, ack/conflict; no world block writes |
| Clipboard subscribe / metadata / sections / completion | Both | Subscription only while enabled; content revision separate from transform revision |
| Command draft / result sections / completion | Both | Allowlisted simulation only; no arbitrary execution opcode |
| Cancel / flow credit / capability update | Both as needed | Bounded outstanding work and explicit revocation |

TCP already orders delivered bytes. Application revisions protect against async
job completion and stale context; do not invent reliable-transport retries or
repeat a mutation because its acknowledgment is slow. Duplicate mutation request
IDs return their prior result within a bounded per-connection window.

## Thread Ownership And Scheduling

### Implemented Metadata Foundation

The experimental Paper core now retains at most one defensively copied frame
per actual connection. Ingress checks length, bounded peer state and per-player
rate, without decoding or reading WorldEdit. An occupied slot preserves its
first request and place. The oldest distinct actor is visited first; after its
slot is removed, new work can join only at the tail. Global frame tokens are
spent here, not at ingress. This prevents another actor's arrival order from
consuming all service eligibility before the ordinary actor can queue.

Default service is at most 8 visits per owning-thread tick slice, with a soft
0.5 ms target and a 5-second service-eligibility deadline. One public synchronous
Paper task exists only while pending work exists. Cancellation clears owned
buffers/tasks on context loss, disconnect and disable. Permissions and context
are checked at actual service/publication time, including cached results.
See [implemented evidence](BRIDGE_FAIR_DISPATCH_2026-10-12.md) and
[public scheduling sources](SOURCES.md#s46-owning-thread-fair-dispatch).

This is fixed metadata/control service, not a measured CPU-share scheduler for
clipboard capture or simulation. No network deficit/CoDel implementation was
copied. The per-player ingress-copy bound and aggregate decode/read bound are
different; a queued frame is not a guaranteed reply. The remaining stages below
must establish their own revisions, cancellation, resource costs and deadlines.

### Future Heavy Stages

| Stage | Owner | Allowed work |
| --- | --- | --- |
| Decode | Transport/codec thread | Validate lengths/opcodes; bounded primitive decoding, no live world calls |
| Authorize and capture | Platform's owning game thread | Resolve current actor/session/selector; capture permitted loaded input in slices |
| Calculate / encode | Owner thread initially; bounded worker only after isolation is proven | Memory-only operations on exclusively owned data; compression/encoding of frozen values |
| Publish / apply | Owning server thread | Revalidate identity, context, grants and revisions; mutate selection only when explicitly requested |
| Client publication | Client thread | Validate current epoch; swap completed snapshots; invalidate only affected render sections |
| Client GPU work | Native rendering lifecycle | Bounded preparation/upload and proper resource disposal |

Default to correctness-preserving tick slices. WorldEdit actors, masks, clipboards
and third-party patterns are not made thread-safe by copying their references.
Official pattern factories can invoke extension parsers: restricted syntax and
post-parse type checks are not a JVM sandbox. Only audited implementations may
run off-thread, and parser/extension execution must also be reviewed.
Paper [ChunkSnapshot](SOURCES.md#s14-paper-chunk-snapshot) is thread-safe data,
but capturing it must respect ownership and does not sanitize hidden world data.

Use one active job plus one replaceable pending draft per player/capability,
with an aggregate scheduler budget and fair rotation across players. Six clients
must not each independently consume a full per-server budget. Track capture,
simulation, encode and send costs separately. Cancellation also stops queued
fragments and releases buffers. Time budgets are cooperative targets: a single
external API call cannot be forcibly preempted safely.

Folia is **not supported by this proposal's initial implementation**. Its region
and entity schedulers have different ownership rules. WorldEdit 7.4.2 itself has
a Folia extent guard, but that does not establish cross-region snapshot/session
safety for our bridge. Never add `folia-supported: true` just to permit loading.
See [Folia sources](SOURCES.md#s12-folia-ownership).

## Low-Overhead Server Contract

**Required product constraint:** the optional bridge must minimize server cost,
not merely move expensive client work to a server. Zero overhead is not a
credible guarantee. These are implementation and release requirements, not
measurements of an existing companion.

- **Idle means no preview work.** Without an enabled, authorized subscription
  or admitted request, do not scan regions/clipboards, hash their contents,
  simulate commands or transfer block data. Use bounded lifecycle discovery;
  do not add a heartbeat just to duplicate the game connection's liveness.
- Prefer verified public change hooks and revision caches. Where a change hook
  is unavailable, use cheap bounded metadata probes only for active consumers;
  do not replace missing invalidation with full-buffer polling every tick.
- Rendering, camera movement, drag drafting and cached clipboard-transform
  previews stay client-side. Send final selection commits, not mouse samples.
  Keep authoritative permission checks, required source capture and server-only
  simulation on the server; client-side convenience must not weaken authority.
- Coalesce superseded command drafts before expensive parsing/capture. Reuse
  valid source sections and content revisions; unchanged input must not trigger
  a full rescan. Transform-only changes send metadata, not clipboard contents.
- Enforce one aggregate admission/scheduling policy for all players, including
  owner-thread work, worker concurrency, retained bytes and send throughput.
  Per-player fairness limits must never multiply the total server allowance.
  Rate-limit and check cheap envelope/context bounds before costly parsing,
  decompression or allocation. Bound scheduler and cancellation overhead too.
- Off-thread work still consumes shared CPU, memory and GC capacity. Only
  isolated, audited memory operations may use bounded workers; live world
  capture obeys thread ownership and short cooperative slices. Never force
  chunk loading/generation to satisfy a preview.
- Normal gameplay and WorldEdit execution take priority. Under pressure, stop
  admitting new preview jobs, coalesce pending drafts and defer/pause capture
  and sending with an explicit busy/updating state. Backpressure must not
  accumulate unbounded snapshots. Define measured recovery thresholds to avoid
  repeatedly pausing/restarting the same job. No silent result truncation.
- Disabling a feature, revoking permission or leaving the server cancels its
  work and queued fragments and releases owned buffers. No residual scanning
  for a closed preview. Shared caches also have bounded ownership/lifetimes.

Measure transport, capture, simulation and publication separately, then their
combined effect against the same server without the bridge. Select defaults
from repeated baseline/load profiles, not an arbitrary per-player millisecond
allowance or the claim that async work is free. Large cold inputs may take time;
the existing loaded-section policy is not permission to exhaust server resources.
See [server cost acceptance](SERVER_BRIDGE_VERIFICATION.md#server-cost-acceptance).

## Selection Transactions

Read authoritative selector state on the server and retain its original identity
and points there. Client drags remain local and immediate; transmit final bounded
coordinates on explicit commit, not on every mouse pixel. On apply:

1. Check negotiated capability, live WorldEdit grants, context, expected revision,
   selector type, world override, coordinate arithmetic and vertical limits.
2. Re-read actual selector points. Reject a stale draft rather than overwriting a
   selection changed by the wand, command, another plugin or another connection.
3. Reuse `SelectionEditTransaction` validation on a clone before the real change.
   Serialize applies on the owning thread and prevent duplicate request execution.
4. Call the selector's public synchronization contract and return canonical state.
   Reconcile normal CUI delivery and bridge ack, whichever arrives first; an ack
   must not resurrect geometry after deselect or dimension change.
5. Escape discards an unsent local draft. After commit has been sent, cancellation
   cannot promise to reverse an already applied server operation. Pending feedback
   must make that distinction, without a speculative rollback or hidden undo.

## Clipboard Synchronization

Subscribe only while copied-object preview is enabled and authorized. Capture
content once per content revision; send origin, bounds, placement semantics and
the WorldEdit transform independently. `//rotate`/`//flip` change transform state,
so they should not retransmit all blocks. During typing, compose a draft without
mutating the authoritative `ClipboardHolder`. After Enter, reconcile the actual
server result. See [pinned transform semantics](SOURCES.md#s08-worldedit-clipboard-commands).

Transmit block IDs/properties, not process-local numeric state IDs. Palette each
section and validate all indices. Unknown client modded blocks need an explicit
unsupported/placeholder state, not silent substitution with air. Default payloads
exclude inventories, text, commands, arbitrary NBT, entities and biomes; support
for any of those needs a separate permission/schema/render audit.

Content object identity alone cannot detect in-place modifications by other mods.
No universal clipboard-changed event was established in this research. Prove
invalidation for supported commands/providers before claiming live sync. Use
cheap revision/metadata detection, bounded validation and explicit refresh;
never hash every clipboard cell each tick or intercept internal fields blindly.

## Command Preview Semantics

Keep existing supported commands, parser restrictions and visible failure states
from [COMMAND_PREVIEW.md](../../COMMAND_PREVIEW.md). New server permissions and
actual WorldEdit version determine capabilities; text alone is not authorization.

- Simulate writes only in isolated memory. Do not dispatch a real command against
  the world and undo it, replace global session state, or intercept all WorldEdit
  edits through an extent listener to pretend this is a sandbox.
- Server verifies selection and interest. A client chunk list can narrow the
  request, never enlarge it beyond currently permitted/tracked server context.
  No force loading/generation or arbitrary coordinate reads. Required halos and
  algorithms crossing source bounds need explicit loaded/authorized coverage.
- Start with current supported syntax, explicit command-permission mapping and
  safe session-mask copies. Reject scripts, NBT, extension expressions and unknown
  stateful masks. Do not promise every WorldEdit/FAWE command can be simulated.
- Keep random previews stable for a source/draft revision and label them samples.
  Standard WorldEdit random execution rolls separately; matching the preview
  cell-for-cell would require a new execution contract, outside this bridge.
  See [RandomPattern source](SOURCES.md#s09-worldedit-random-pattern).
- A sliced source capture is not a global atomic world snapshot. Revalidate
  relevant sections and distinguish updating, incomplete and final results.
  Never show coverage holes as air. Protection hooks, physics, fluids and random
  ticks can change eventual outcomes; report supported fidelity honestly.
- Keep one rendering owner: supported region drafts suspend copied-object work;
  transform drafts own only the clipboard preview; closing/removing drafts restores
  the previous preference and cache. Switching providers cancels stale work.

## Streaming, Memory And Abuse Resistance

Prefer section-sized palette data/differences and small bounded frames over one
compressed schematic or giant JSON message. Protocol frame size must fit the
minimum verified backend/direction/proxy limit minus envelope overhead. Paper's
documented 1 MiB constant is not permission to send 1 MiB C2S messages everywhere.

Validate before allocation: encoded and decoded bytes, strings, section counts,
palette counts/indices, coordinates, finite transforms, fragment totals and job
ownership. Apply per-player **and global** quotas for CPU, buffered bytes,
outstanding jobs, fragments, send rate and lifetime. Receiver-advertised limits
can only reduce local hard bounds. Decompression is incremental and aborts at
the decoded-byte ceiling regardless of the advertised length or compression ratio.

Use application flow credit/queue watermarks to avoid building an entire transfer
for a slow peer. Freeze a section before publishing it; replace section geometry
atomically. If the connection/context ends, release every incomplete transfer.
Initially uncompressed palette frames are acceptable; add compression only after
measuring both bandwidth benefit and tick/CPU cost. No network Java serialization,
client-supplied class names, file paths, URLs, disk schematic writes or code execution.
See the [Servux case study](SOURCES.md#s24-servux-fragmentation-case-study) and
[maintainer security advisory](SOURCES.md#s25-servux-security-advisory).

There is no new arbitrary visible block-count cap proposed here. This does not
mean unlimited memory, network traffic or instantaneous cold results. Stream
permitted loaded sections, coalesce superseded work, cache by revision and return
an explicit busy/incomplete/denied outcome if server resource policy is exhausted.
Do not silently truncate or destabilize the server to claim unlimited support.

## Open Questions Before Coding Each Feature

- Exact dedicated-server artifacts, Java/toolchain and plugin API tuples for all targets.
- Optional payload interoperability among both clients and all server hosts,
  including Paper advertised channels, NeoForge non-Neo links and backend changes.
- Actor/session ownership and reliable revision detection under real commands,
  third-party clipboard mutation and permission changes.
- Restricted-player visibility/protection policy; admin-only preview is safer
  than claiming anti-xray-safe data access without proof.
- Byte/CPU/time defaults and fair scheduling measured on realistic multi-user loads.
- Which parser internals must remain version-pinned, and whether public alternatives suffice.

Resolve these through the [verification gates](SERVER_BRIDGE_VERIFICATION.md),
not by increasing confidence language or declaring universal compatibility.
