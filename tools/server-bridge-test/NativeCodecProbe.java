package org.marshal.weaddon_lightposition.bridge.test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;
import org.marshal.weaddon_lightposition.bridge.protocol.BridgeCodec;
import org.marshal.weaddon_lightposition.bridge.protocol.BridgeMessage;
import org.marshal.weaddon_lightposition.client.bridge.BridgePayload;
import org.marshal.weaddon_lightposition.client.bridge.BridgeRestartPayload;
import org.marshal.weaddon_lightposition.bridge.protocol.BridgeRestartHint;

public final class NativeCodecProbe {
    public static void main(String[] args) throws Exception {
        Class<?> codecInterface = Class.forName(System.getProperty("weso.bridge.codec"));
        Method encode = codecInterface.getMethod("encode", Object.class, Object.class);
        Method decode = codecInterface.getMethod("decode", Object.class);
        byte[] frame = BridgeCodec.encode(new BridgeMessage.Hello(new UUID(0, 1), 1, 0, 1));
        BridgePayload payload = new BridgePayload(frame);
        frame[0] = 0;
        check(payload.bytes()[0] == 0x57, "Payload constructor leaked its array");
        byte[] exposed = payload.bytes(); exposed[0] = 0;
        check(payload.bytes()[0] == 0x57, "Payload accessor leaked its array");
        ByteBuf buffer = Unpooled.buffer();
        try {
            encode.invoke(BridgePayload.CODEC, buffer, payload);
            byte[] actual = new byte[buffer.readableBytes()]; buffer.getBytes(0, actual);
            check(Arrays.equals(actual, payload.bytes()), "Native codec added framing incompatible with Paper");
            BridgePayload decoded = (BridgePayload) decode.invoke(BridgePayload.CODEC, buffer);
            check(Arrays.equals(decoded.bytes(), payload.bytes()), "Native decode differs");
            check(!buffer.isReadable(), "Native decode left trailing bytes");
            check(BridgeCodec.decode(decoded.bytes(), BridgeCodec.Direction.TO_SERVER) instanceof BridgeMessage.Hello,
                    "Application decode failed");
            var closed = new BridgeMessage.SessionClosed(new UUID(0, 1), new UUID(2, 3));
            byte[] closedBytes = BridgeCodec.encode(closed);
            buffer.clear();
            encode.invoke(BridgePayload.CODEC, buffer, new BridgePayload(closedBytes));
            byte[] actualClose = new byte[buffer.readableBytes()]; buffer.getBytes(0, actualClose);
            check(Arrays.equals(actualClose, closedBytes), "Native close notification gained framing");
            var decodedClose = (BridgePayload) decode.invoke(BridgePayload.CODEC, buffer);
            check(closed.equals(BridgeCodec.decode(decodedClose.bytes(), BridgeCodec.Direction.TO_CLIENT)),
                    "Native close notification failed application decoding");
            check(!buffer.isReadable(), "Native close notification left trailing bytes");
            var editSnapshot = new BridgeMessage.EditSnapshot(new UUID(4, 5), new BridgeMessage.SelectionState(
                    BridgeMessage.SelectionStatus.READY, new BridgeMessage.Bounds(0, -64, 0, 5, 319, 7), -64, 319, 7));
            for (BridgeMessage edit : java.util.List.of(new BridgeMessage.EditReadRequest(new UUID(2, 3), 1),
                    new BridgeMessage.EditReadReply(new UUID(2, 3), 1, editSnapshot),
                    new BridgeMessage.EditCommitRequest(new UUID(2, 3), 2, editSnapshot.revision(), editSnapshot.selection().bounds()),
                    new BridgeMessage.EditCommitReply(new UUID(2, 3), 2, BridgeMessage.CommitStatus.APPLIED, editSnapshot))) {
                byte[] expected = BridgeCodec.encode(edit); buffer.clear();
                encode.invoke(BridgePayload.CODEC, buffer, new BridgePayload(expected));
                byte[] actualEdit = new byte[buffer.readableBytes()]; buffer.getBytes(0, actualEdit);
                check(Arrays.equals(expected, actualEdit), "Native edit frame gained framing");
                var decodedEdit = (BridgePayload) decode.invoke(BridgePayload.CODEC, buffer);
                var direction = edit instanceof BridgeMessage.EditReadRequest || edit instanceof BridgeMessage.EditCommitRequest
                        ? BridgeCodec.Direction.TO_SERVER : BridgeCodec.Direction.TO_CLIENT;
                check(edit.equals(BridgeCodec.decode(decodedEdit.bytes(), direction)), "Native edit frame differs");
                check(!buffer.isReadable(), "Native edit frame left trailing bytes");
            }
            byte[] restart = BridgeRestartHint.encode(new UUID(4, 5));
            var restartPayload = new BridgeRestartPayload(restart);
            restart[0] = 0;
            check(restartPayload.bytes()[0] == 0x57, "Restart wrapper leaked input array");
            var exposedRestart = restartPayload.bytes(); exposedRestart[0] = 0;
            buffer.clear();
            encode.invoke(BridgeRestartPayload.CODEC, buffer, restartPayload);
            byte[] restartBytes = new byte[buffer.readableBytes()]; buffer.getBytes(0, restartBytes);
            check(Arrays.equals(restartBytes, restartPayload.bytes()), "Restart hint gained framing");
            var decodedRestart = (BridgeRestartPayload) decode.invoke(BridgeRestartPayload.CODEC, buffer);
            check(BridgeRestartHint.decode(decodedRestart.bytes()).equals(new UUID(4, 5)), "Restart decode differs");
            check(!buffer.isReadable(), "Restart decode left trailing bytes");
            for (int size : new int[]{0, 21, 23, 256, 4096}) {
                buffer.clear().writeZero(size);
                var invalid = (BridgeRestartPayload) decode.invoke(BridgeRestartPayload.CODEC, buffer);
                check(invalid.bytes().length == 0 && !buffer.isReadable(), "Invalid restart payload did not drop boundedly");
            }
            for (int size : new int[]{0, 5, 257, 4096}) {
                buffer.clear().writeZero(size);
                BridgePayload invalid = (BridgePayload) decode.invoke(BridgePayload.CODEC, buffer);
                check(invalid.bytes().length == 0 && !buffer.isReadable(), "Invalid payload did not drop boundedly");
            }
            buffer.clear().writeZero(256);
            check(((BridgePayload) decode.invoke(BridgePayload.CODEC, buffer)).bytes().length == 256,
                    "Max-sized frame was not accepted by the native wrapper");
        } finally { buffer.release(); }
        System.out.println("NATIVE BRIDGE PAYLOAD PASS: " + codecInterface.getSimpleName() + ", Java " + Runtime.version());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
