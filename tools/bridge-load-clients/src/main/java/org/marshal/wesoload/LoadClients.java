package org.marshal.wesoload;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.PacketErrorEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.factory.ClientNetworkSessionFactory;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.MinecraftConstants;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftCodec;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundCustomPayloadPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundPingPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundCustomPayloadPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundPongPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundChunkBatchFinishedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundPlayerLoadedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundChunkBatchReceivedPacket;

/** Offline, bounded loopback-only load actors. Never linked into a mod or plugin. */
public final class LoadClients {
    private static final Gson JSON = new Gson();
    private static final String BRIDGE = "worldedit_selection_overlay:bridge";
    private static final Map<String, ClientSession> CLIENTS = new ConcurrentHashMap<>();
    private static final Set<String> POSITION_READY = ConcurrentHashMap.newKeySet();
    private static final ThreadPoolExecutor HANDLERS = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(4096), task -> { var thread = new Thread(task, "WesoLoadPackets"); thread.setDaemon(true); return thread; });

    public static void main(String[] args) {
        int code = 0;
        try {
            require(MinecraftCodec.CODEC.getProtocolVersion() == 776 && MinecraftCodec.CODEC.getMinecraftVersion().equals("26.2"), "Wrong library codec");
            if (args.length == 1 && args[0].equals("--self-test")) { selfTest(); return; }
            require(args.length == 1 && args[0].equals("--owned-loopback-fixture"), "Explicit fixture opt-in required");
            emit("ready", "", "26.2-20261012.101744-21");
            // Bound before parsing; BufferedReader.readLine() would allocate an unbounded line.
            try (var input = new InputStreamReader(System.in, StandardCharsets.UTF_8)) {
                var line = new StringBuilder(); int value;
                while ((value = input.read()) != -1) {
                    if (value == '\n') { command(JSON.fromJson(line.toString(), JsonObject.class)); line.setLength(0); }
                    else { require(line.length() < 4096, "IPC input limit"); line.append((char) value); }
                }
                require(line.isEmpty(), "Truncated IPC command");
            }
        } catch (Throwable ex) { ex.printStackTrace(System.err); code = 1; }
        finally {
            CLIENTS.values().forEach(session -> session.disconnect("Owned fixture shutdown"));
            HANDLERS.shutdown();
        }
        System.exit(code);
    }

    private static void command(JsonObject command) {
        String name = command.get("name").getAsString(), op = command.get("op").getAsString();
        require(name.matches("WesoLoad(?:[0-2][0-9]|3[01])"), "Synthetic identity required");
        switch (op) {
            case "connect" -> connect(name);
            case "send" -> {
                ClientSession session = CLIENTS.get(name); require(session != null && session.isConnected(), "No live actor");
                String channel = command.get("channel").getAsString();
                byte[] data = outgoing(channel, command.get("data").getAsString());
                session.send(new ServerboundCustomPayloadPacket(Key.key(channel), data));
            }
            case "move" -> {
                ClientSession session = CLIENTS.get(name);
                require(session != null && session.isConnected() && POSITION_READY.contains(name), "Actor position not ready");
                session.send(movement(name, command.get("x").getAsDouble(), command.get("y").getAsDouble(), command.get("z").getAsDouble()));
            }
            case "end" -> { ClientSession session = CLIENTS.get(name); require(session != null, "Unknown actor"); session.disconnect("Verification complete"); }
            default -> throw new IllegalArgumentException("Unknown IPC operation");
        }
    }

    private static ServerboundMovePlayerPosPacket movement(String name, double x, double y, double z) {
        int index = Integer.parseInt(name.substring(8));
        require(Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                && x >= 10 && x <= 22 && z >= 10 && z <= 22 && y == 80 + index % 2,
                "Movement outside the owned flight route");
        return new ServerboundMovePlayerPosPacket(false, false, x, y, z);
    }

    private static byte[] outgoing(String channel, String encoded) {
        require(encoded.length() <= 512, "IPC payload limit");
        byte[] data = Base64.getDecoder().decode(encoded);
        require(data.length <= 256, "Wire payload limit");
        if (channel.equals("minecraft:register")) {
            String names = new String(data, StandardCharsets.UTF_8);
            require(names.equals("worldedit:cui") || names.equals(BRIDGE + "\0worldedit:cui"), "Wrong registration");
        } else if (channel.equals("worldedit:cui")) require(new String(data, StandardCharsets.UTF_8).equals("v|4"), "Unexpected CUI request");
        else require(channel.equals(BRIDGE), "Unapproved channel");
        return data;
    }

    private static void connect(String name) {
        var session = ClientNetworkSessionFactory.factory().setAddress("127.0.0.1", 25579)
                .setProtocol(new MinecraftProtocol(name)).setPacketHandlerExecutor(HANDLERS).create();
        require(CLIENTS.putIfAbsent(name, session) == null, "Duplicate actor");
        session.setFlag(MinecraftConstants.FOLLOW_TRANSFERS, false);
        session.addListener(new SessionAdapter() {
            private final AtomicBoolean joined = new AtomicBoolean();
            @Override public void packetReceived(Session owner, Packet packet) {
                if (packet instanceof ClientboundLoginPacket && joined.compareAndSet(false, true)) emit("joined", name, "");
                else if (packet instanceof ClientboundCustomPayloadPacket payload) {
                    String channel = payload.getChannel().asString();
                    if (channel.equals(BRIDGE) || channel.equals("worldedit:cui")) {
                        require(payload.getData().length <= 4096, "Incoming fixture payload limit");
                        var event = event("payload", name); event.addProperty("channel", channel);
                        event.addProperty("data", Base64.getEncoder().encodeToString(payload.getData())); print(event);
                    }
                } else if (packet instanceof ClientboundPlayerPositionPacket position) {
                    owner.send(new ServerboundAcceptTeleportationPacket(position.getId()));
                    owner.send(ServerboundPlayerLoadedPacket.INSTANCE);
                    POSITION_READY.add(name);
                    emit("teleport", name, "");
                } else if (packet instanceof ClientboundChunkBatchFinishedPacket) owner.send(new ServerboundChunkBatchReceivedPacket(20));
                else if (packet instanceof ClientboundPingPacket ping) owner.send(new ServerboundPongPacket(ping.getId()));
            }
            @Override public void packetError(PacketErrorEvent event) {
                emit("fault", name, event.getCause().toString());
                // Never suppress a parser error to make a load test pass.
            }
            @Override public void disconnected(DisconnectedEvent event) {
                POSITION_READY.remove(name);
                if (event.getCause() != null) emit("fault", name, event.getCause().toString());
                emit("ended", name, event.getReason().toString());
            }
        });
        session.connect(false);
    }

    private static JsonObject event(String type, String name) {
        var event = new JsonObject(); event.addProperty("type", type); event.addProperty("name", name); return event;
    }
    private static void emit(String type, String name, String detail) { var event = event(type, name); event.addProperty("detail", detail); print(event); }
    private static void print(JsonObject event) { System.out.println(JSON.toJson(event)); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalArgumentException(message); }
    private static void selfTest() {
        outgoing(BRIDGE, "AA=="); outgoing("worldedit:cui", "dnw0");
        for (var value : new String[][]{{"other:channel", "AA=="}, {"minecraft:register", "AA=="}, {BRIDGE, "!"}, {BRIDGE, "A".repeat(1024)}}) {
            boolean refused = false;
            try { outgoing(value[0], value[1]); } catch (IllegalArgumentException ex) { refused = true; }
            require(refused, "Guard accepted invalid input");
        }
        for (var xyz : new double[][]{{Double.NaN,80,16},{16,Double.POSITIVE_INFINITY,16},{9,80,16},{16,81,16},{16,80,23}}) {
            boolean refused = false;
            try { movement("WesoLoad00",xyz[0],xyz[1],xyz[2]); } catch (IllegalArgumentException ex) { refused = true; }
            require(refused, "Movement guard accepted invalid input");
        }
        var packet = movement("WesoLoad01",22,81,16);
        var bytes = Unpooled.buffer();
        try {
            packet.serialize(bytes);
            require(bytes.readableBytes() == 25, "Unexpected movement codec size");
            require(packet.equals(new ServerboundMovePlayerPosPacket(bytes)) && !bytes.isReadable(), "Movement codec round trip");
        } finally { bytes.release(); }
        System.out.println("LOAD CLIENT GUARDS PASS: exact 776 codec, bounded wire/movement input, movement round trip, explicit channels and offline loopback identities");
    }
}
