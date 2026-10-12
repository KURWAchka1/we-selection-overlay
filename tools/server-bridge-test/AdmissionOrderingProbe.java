import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.marshal.weaddon_lightposition.bridge.protocol.BridgeCodec;
import org.marshal.weaddon_lightposition.bridge.protocol.BridgeMessage;
import org.marshal.weaddon_lightposition.bridge.server.BridgeServer;
import static org.marshal.weaddon_lightposition.bridge.protocol.BridgeMessage.*;

/** Deterministic owner-thread ordering experiment, not a network/gameplay benchmark. */
class AdmissionOrderingProbe {
    public static void main(String[] args) {
        for (int attackers : new int[] {1, 31}) {
            run(attackers, true);
            run(attackers, false);
        }
    }

    private static void run(int attackers, boolean ordinaryFirst) {
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
        long[] ids = new long[players.length];
        try {
            for (int i = 0; i < players.length; i++) {
                players[i] = new UUID(9, i);
                var welcome = (Welcome) exchange(server, players[i], new Hello(new UUID(3, i + 1), 1, 0, SELECTION_READ));
                if (welcome == null || welcome.status() != WelcomeStatus.READY) throw new AssertionError("Initial hello failed");
                sessions[i] = welcome.epoch();
            }
            clock.addAndGet(2_000_000_000L);
            long admittedBefore = server.metrics().admitted(), readsBefore = server.metrics().selectionReads();
            int ordinaryAttempts = 0, ordinaryReplies = 0;
            // 31 conforming senders can exceed aggregate 100/s without violating individual 4/s.
            for (int step = 0; step < 120; step++) {
                if (step > 0) clock.addAndGet(250_000_000L);
                boolean ordinary = step % 2 == 0;
                if (ordinary) ordinaryAttempts++;
                if (ordinary && ordinaryFirst) ordinaryReplies += request(server, players, sessions, ids, 0);
                for (int i = 1; i < players.length; i++) {
                    for (int burst = 0; burst < (step == 0 ? 4 : 1); burst++) request(server, players, sessions, ids, i);
                }
                if (ordinary && !ordinaryFirst) ordinaryReplies += request(server, players, sessions, ids, 0);
            }
            var metrics = server.metrics();
            long admitted = metrics.admitted() - admittedBefore, reads = metrics.selectionReads() - readsBefore;
            if (admitted > 100 + 100 * 29.75 || admitted != reads || metrics.malformed() != 0
                    || metrics.connections() != players.length) throw new AssertionError("Bounded metadata invariants failed");
            System.out.printf(Locale.ROOT, "{\"attackers\":%d,\"ordinaryFirst\":%s,\"simulatedMillis\":29750,"
                    + "\"ordinaryAttempts\":%d,\"ordinaryReplies\":%d,\"admittedReads\":%d,\"throttled\":%d}%n",
                    attackers, ordinaryFirst, ordinaryAttempts, ordinaryReplies, reads, metrics.throttled());
        } finally {
            server.close();
            if (server.metrics().connections() != 0) throw new AssertionError("Retained peers");
        }
    }

    private static int request(BridgeServer server, UUID[] players, UUID[] sessions, long[] ids, int index) {
        var result = (SelectionReply) exchange(server, players[index], new SelectionRequest(sessions[index], ++ids[index]));
        if (result == null) return 0;
        if (result.requestId() != ids[index] || !result.epoch().equals(sessions[index])
                || result.selection().bounds().minX() != index) throw new AssertionError("Wrong player/epoch/request");
        return 1;
    }

    private static BridgeMessage exchange(BridgeServer server, UUID player, BridgeMessage message) {
        byte[] response = server.receive(player, BridgeCodec.encode(message));
        return response == null ? null : BridgeCodec.decode(response, BridgeCodec.Direction.TO_CLIENT);
    }
}
