# Bridge Transport: Experimental Read-Only Schema

Date: 2026-10-11. Implementation: `0.1.0-dev`. This schema is **experimental**;
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

Current application version: major `1`, minor `0`. A different major produces
an incompatible welcome with no grants. Negotiate the lower supported minor;
unknown requested capability bits are ignored, not authorized. The sole current
bit is `0x01`: **read own selection metadata**. It is not a write/edit grant.

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
Permissions are rechecked even before duplicate-read caching is used.
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
They cannot detect a silent server-side plugin shutdown with no wire signal, or
an unregister/register pair entirely between availability observations; those
remain separate lifecycle gates. See the [channel/reconnect evidence](BRIDGE_CHANNEL_AND_BASELINE_2026-10-12.md).

Handlers are owned by their platform game thread. Cheap frame/admission checks
precede application decoding and session access. Invalid or throttled traffic is
dropped without per-packet logging; quotas apply globally as well as per player.
No unsolicited state stream, background polling, mutation acknowledgment or
clipboard/command-preview operation exists in this schema.
