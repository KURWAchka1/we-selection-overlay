import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.marshal.weaddon_lightposition.bridge.protocol.BridgeCodec;
import org.marshal.weaddon_lightposition.bridge.protocol.BridgeMessage;
import org.marshal.weaddon_lightposition.bridge.server.BridgeServer;
import static org.marshal.weaddon_lightposition.bridge.protocol.BridgeMessage.*;

/** Deterministic fair-dispatch regression; simulated clocks do not measure CPU cost. */
class AdmissionOrderingProbe {
    public static void main(String[] args) {
        for (int attackers : new int[] {1, 31}) {
            for (int offset : new int[] {0, 1, 4}) {
                run(attackers, true, offset);
                run(attackers, false, offset);
            }
        }
    }

    private static void run(int attackers, boolean ordinaryFirst, int offset) {
        var clock = new AtomicLong(1);
        var epochs = new AtomicLong();
        var server = new BridgeServer(new BridgeServer.Host() {
            public boolean mayReadSelection(UUID id) { return true; }
            public SelectionState readSelection(UUID id) {
                int x = Math.toIntExact(id.getLeastSignificantBits());
                return new SelectionState(SelectionStatus.READY, new Bounds(x, 64, 0, x, 64, 0), -64, 319, 7);
            }
        }, BridgeServer.Limits.defaults(), clock::get, () -> new UUID(8, epochs.incrementAndGet()));
        UUID[] players = new UUID[attackers + 1], sessions = new UUID[players.length];
        long[] ids = new long[players.length], pendingIds = new long[players.length], sentAt = new long[players.length];
        int[] ordinaryReplies = {0};
        long[] ordinaryMaxWait = {0};
        java.util.function.BiConsumer<UUID, byte[]> receive = (player, frame) -> {
            int index = Math.toIntExact(player.getLeastSignificantBits());
            var reply = (SelectionReply) BridgeCodec.decode(frame, BridgeCodec.Direction.TO_CLIENT);
            if (reply.requestId() != pendingIds[index] || !reply.epoch().equals(sessions[index])
                    || reply.selection().bounds().minX() != index) throw new AssertionError("Wrong player/epoch/request");
            pendingIds[index] = 0;
            if (index == 0) {
                ordinaryReplies[0]++;
                ordinaryMaxWait[0] = Math.max(ordinaryMaxWait[0], clock.get() - sentAt[0]);
            }
        };
        try {
            for (int i = 0; i < players.length; i++) {
                players[i] = new UUID(9, i);
                var welcome = (Welcome) exchange(server, players[i], new Hello(new UUID(3, i + 1), 1, 0, SELECTION_READ));
                if (welcome == null || welcome.status() != WelcomeStatus.READY) throw new AssertionError("Initial hello failed");
                sessions[i] = welcome.epoch();
            }
            clock.addAndGet(2_000_000_000L);
            long started = clock.get(), admittedBefore = server.metrics().admitted(), readsBefore = server.metrics().selectionReads();
            int ordinaryAttempts = 0;
            for (int tick = 0; tick < 600; tick++) {
                boolean ordinary = tick % 10 == offset;
                if (ordinary) ordinaryAttempts++;
                if (ordinary && ordinaryFirst) request(server, clock, players, sessions, ids, pendingIds, sentAt, 0);
                if (tick % 5 == 0) for (int i = 1; i < players.length; i++) {
                    for (int burst = 0; burst < (tick == 0 ? 4 : 1); burst++)
                        request(server, clock, players, sessions, ids, pendingIds, sentAt, i);
                }
                if (ordinary && !ordinaryFirst) request(server, clock, players, sessions, ids, pendingIds, sentAt, 0);
                if (server.drain(receive) > 8) throw new AssertionError("Dispatch count exceeded");
                clock.addAndGet(50_000_000L);
            }
            for (int tick = 0; server.pendingCount() > 0 && tick < 20; tick++) {
                server.drain(receive); clock.addAndGet(50_000_000L);
            }
            var metrics = server.metrics();
            var queue = server.queueMetrics();
            long elapsed = clock.get() - started;
            long admitted = metrics.admitted() - admittedBefore, reads = metrics.selectionReads() - readsBefore;
            if (admitted > 100 + elapsed / 10_000_000L || admitted != reads || metrics.malformed() != 0
                    || metrics.connections() != players.length || queue.peakPending() > players.length
                    || queue.pending() != 0 || queue.expired() != 0 || ordinaryReplies[0] != ordinaryAttempts
                    || ordinaryMaxWait[0] > 500_000_000L) throw new AssertionError("Fair progress or bounded invariants failed");
            System.out.printf(Locale.ROOT, "{\"attackers\":%d,\"ordinaryFirst\":%s,\"ordinaryOffsetMillis\":%d,\"simulatedMillis\":%d,"
                    + "\"ordinaryAttempts\":%d,\"ordinaryReplies\":%d,\"ordinaryMaxWaitMillis\":%.3f,"
                    + "\"admittedReads\":%d,\"throttled\":%d,\"peakPending\":%d,\"expired\":%d}%n",
                    attackers, ordinaryFirst, offset * 50, elapsed / 1_000_000L, ordinaryAttempts, ordinaryReplies[0],
                    ordinaryMaxWait[0] / 1_000_000.0, reads, metrics.throttled(), queue.peakPending(), queue.expired());
        } finally {
            server.close();
            if (server.metrics().connections() != 0 || server.pendingCount() != 0) throw new AssertionError("Retained peers/work");
        }
    }

    private static void request(BridgeServer server, AtomicLong clock, UUID[] players, UUID[] sessions,
                                long[] ids, long[] pendingIds, long[] sentAt, int index) {
        long request = ++ids[index];
        if (server.enqueue(players[index], BridgeCodec.encode(new SelectionRequest(sessions[index], request)))) {
            if (pendingIds[index] != 0) throw new AssertionError("Two accepted pending reads for one player");
            pendingIds[index] = request; sentAt[index] = clock.get();
        } else if (index == 0) throw new AssertionError("Ordinary input was rejected");
    }

    private static BridgeMessage exchange(BridgeServer server, UUID player, BridgeMessage message) {
        var response = new AtomicReference<byte[]>();
        server.enqueue(player, BridgeCodec.encode(message));
        server.drain((id, frame) -> response.set(frame));
        return response.get() == null ? null : BridgeCodec.decode(response.get(), BridgeCodec.Direction.TO_CLIENT);
    }
}
