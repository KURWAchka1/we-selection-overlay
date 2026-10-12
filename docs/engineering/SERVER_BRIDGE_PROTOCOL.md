# Bridge Transport: Experimental Read-Only Schema

Updated: 2026-10-12. Implementation: `0.1.0-dev`. This schema is **experimental**;
numeric version 1 does not establish a released stable protocol. Unsupported
capabilities are not advertised just because the longer-term design mentions them.

## Framing

Play channel: `worldedit_selection_overlay:bridge`. Use the existing Minecraft
connection. Paper plugin-message bytes and native payload bytes are identical:
**no additional array-length prefix, compression or fragmentation** in this stage.
All integers are fixed-width, big-endian. No strings, NBT, paths or arbitrary
serialized objects occur. A UUID is two signed 64-bit words, most significant
first; it is a freshness value here, not a player identity or credential.

Every frame begins with magic `0x5745534f` (4 bytes, ASCII WESO), envelope version
`1` (unsigned byte), and opcode (unsigned byte). Hard length: 6..256 bytes.
Reject unknown headers/opcodes, wrong directions, truncated/trailing data and
invalid domain values. A negotiated frame allowance must accommodate the largest
current message (64 bytes); a smaller allowance cannot enable this capability.

## Messages

The following fields come **after** the six-byte header, in this exact order.
`u8`/`u16` are unsigned integers; `i32`/`i64` are signed integers.

| Opcode | Direction | Fields | Full length |
| --- | --- | --- | --- |
| 1 Hello | Client -> server | nonce UUID, major u16, minor u16, requested capabilities i32 | 30 |
| 2 Welcome | Server -> client | echoed nonce UUID, server epoch UUID, status u8, major u16, minor u16, granted capabilities i32, max frame bytes u16 | 49 |
| 3 SelectionRequest | Client -> server | epoch UUID, request ID i64 | 30 |
| 4 SelectionReply | Server -> client | epoch UUID, request ID i64, selection status u8, optional ready fields below | 31 or 64 |
| 5 SessionClosed | Server -> client | echoed nonce UUID, closed epoch UUID (nonzero) | 38 |

Current application version: major `1`, minor `1`. A different major produces
an incompatible welcome with no grants. Negotiate the lower supported minor;
unknown requested capability bits are ignored, not authorized. Capability `0x01`
means **read own selection metadata**, not a write/edit grant. Optional `0x02`
means **session-close notification**, available only at minor >= 1, explicitly
requested and together with an authorized selection-read grant. Requesting only
notifications does not authorize a session. Minor-zero clients retain their
original four-message contract; every welcome status uses the negotiated minor.

Welcome status values: `0 READY`, `1 DENIED`, `2 INCOMPATIBLE`.
Non-ready welcomes grant no capabilities and use the zero UUID epoch. A ready
welcome needs a nonzero epoch and at least one supported grant. The client
accepts it only while negotiating its matching nonce. Discovery/negotiation
times out after ten seconds without retries; no heartbeat is added.

Selection status values: `0 READY`, `1 EMPTY`, `2 UNSUPPORTED`, `3 DENIED`,
`4 FAILED`. Only READY includes these additional fields:

1. minX, minY, minZ, maxX, maxY, maxZ as six i32 values (inclusive bounds).
2. world minimum Y and world maximum Y as two i32 values (inclusive).
3. permission bits u8: `1 expand`, `2 contract`, `4 shift`.

Minima cannot exceed maxima. X/Z coordinates are within +/-30,000,000; selection
Y bounds must fit the declared world height. Unknown permission bits are invalid.
These are only bounds, not the precise geometry or points of a non-cuboid region.
No block content, world name, player ID, inventories, entities or clipboard is sent.

## Ownership And Freshness

The platform supplies the actual connection player to the server core. No field
allows selecting another actor or world. Require the opt-in bridge permission
and a relevant WorldEdit selection grant before negotiating/serving data.
Permissions are rechecked at service time, even before duplicate-read caching
is used, and after a host read before publishing its result.
Either an authorization check or the host read returning DENIED invalidates the
server epoch. Restored permission requires a new hello/context negotiation.

Server epoch belongs to that player's negotiated context, never a JVM object.
A changed hello nonce rotates it; disconnect, world change, respawn and bridge
channel unregistration clear it. A still-open connection retains only its bounded
admission record across context resets: unregistration/respawn cannot refill its
request budget. Actual disconnect removes that record too. Request IDs
are positive, increasing i64 values within the epoch. The server only caches the
last reply: the same ID returns its original result; older IDs are dropped.
The client has at most one pending read and accepts only its matching ID/epoch.
A timed-out read is not retried, and its late result cannot complete a newer one.

The server now accepts at most one pending frame per connection for deferred
owning-thread service; additional frames cannot replace it or reorder that actor.
Global admission applies before decoding at dispatch, not before queuing.
The 5-second server service-eligibility deadline is shorter than the existing
10-second client timeout. Expired/rejected frames receive no invented busy
response or retry instruction. Cleanup occurs on bounded visits or immediately
on context/connection/host teardown; a server stall can delay reclamation.
Deferred replies retain the same nonce/epoch/request correlation and wire bytes.
Native clients bind that state to connection, world **and local-player instance**.
Replacing the player on a same-world respawn renews discovery with a fresh nonce,
without automatically reading selection metadata. Incoming frames and explicit
requests also check those identities; old-context results cannot cross a reset.
Native verification is recorded in the [lifecycle regression](NATIVE_BRIDGE_LIFECYCLE_2026-10-12.md).

On an observed native channel-availability falling edge, the client discards
metadata, grants, epoch and pending request. A later rising edge starts one fresh
nonce negotiation, including a channel first advertised after discovery timeout.
Constant channel availability never retries a denied or timed-out handshake.
Receiving frames also requires the native channel to remain available.
These checks add no permission polling, metadata subscription or heartbeat.
At minor 1 with `0x02` granted, a matching SessionClosed also immediately discards
metadata, grants, nonce, epoch and pending request. Match both nonce and epoch;
ignore unsolicited, duplicate, old-context and non-negotiated notifications.
The native channel may remain advertised: keep that observation so constant
availability cannot start a retry loop. Late welcomes/replies cannot resurrect
closed state. Existing CUI is independent and remains usable.

On graceful Paper disable, send the notification from `PluginDisableEvent`
**before** the plugin loses send authority, never from `onDisable`. Send only
to negotiated live sessions, at most one 38-byte frame per bounded core peer;
then close the core terminally and release its records. No tasks, subscriptions,
WorldEdit reads or retained recovery registry are introduced by notification;
the pending-frame dispatch task is cancelled first. `onDisable` still
unregisters channels and clears resources if early notification is unavailable.

This does not detect an ungraceful loss without a wire signal or a channel flap
entirely between availability observations. Minor-zero peers cannot be notified.
An additional **optional S2C-only** channel, `worldedit_selection_overlay:restart`,
supports one-shot discovery after a negotiated graceful close. Its independent
22-byte frame is magic `0x57455352` (WESR), version u8 `1`, kind u8 `1`, then a
nonzero startup-generation UUID. It has no length prefix or authority fields;
all other lengths/headers and the zero UUID are invalid. Application protocol
1.1 and the original channel's five message layouts are unchanged.

After successful Paper enable, send the same newly generated hint once to each
already-online player advertising this optional channel. Initial joins use the
normal hello; there are no registration-triggered replies, per-player recovery
records, tasks, permission probes or WorldEdit reads. Work is O(online players)
once per enable, not per tick; hint counts/bytes are separate diagnostics.
Any resulting hello uses the same bounded metadata/control dispatcher.
Legacy and ordinary clients not advertising the channel receive no hint.

Only a matching negotiated SessionClosed arms recovery. In UNAVAILABLE the
first valid new-generation hint consumes that arm, clears old state and starts
one fresh nonce negotiation. Cache only the last accepted generation to reject
its duplicate after another close. Malformed hints do not consume the arm;
hints cannot re-arm it, retry denial/incompatibility/timeouts, or complete a read.
Channel/context loss clears the arm and generation. A failed recovery remains
unavailable until a normal fresh context/channel edge, not a recurring retry.
New grants always require a fresh Welcome and live server authorization.
The normal aggregate/per-player admission limits also apply to this hello.
If a crowded restart saturates them, discovery may time out and safely fall back
to CUI; there is no guaranteed all-player recovery or admission-bypass retry.
Fair recovery under a simultaneous reconnect burst remains a load/release gate.

The hint is a lifecycle signal on the current ordered game connection, **not**
a credential or authenticated history of every server generation. An arbitrary
server replay from an older-than-last generation can consume the single armed
attempt, but cannot restore old grants or metadata. Ungraceful disappearance,
missed lifecycle signals and backend switches remain separate gates. Do not
claim general native hot-reload support or retry on every tick.
See the earlier [channel/reconnect evidence](BRIDGE_CHANNEL_AND_BASELINE_2026-10-12.md)
for the separate advertisement-based path.

Handlers and the pending-frame dispatcher are owned by their platform game
thread. Cheap frame/per-player checks precede queuing; the aggregate service
budget precedes application decoding and session access. Invalid or throttled traffic is
dropped without per-packet logging; quotas apply globally as well as per player.
No unsolicited selection stream, background polling, mutation acknowledgment or
clipboard/command-preview operation exists in this schema.
