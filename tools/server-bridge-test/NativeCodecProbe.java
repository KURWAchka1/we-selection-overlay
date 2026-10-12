package org.marshal.weaddon_lightposition.bridge.test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;
import org.marshal.weaddon_lightposition.bridge.protocol.BridgeCodec;
import org.marshal.weaddon_lightposition.bridge.protocol.BridgeMessage;
import org.marshal.weaddon_lightposition.client.bridge.BridgePayload;

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
