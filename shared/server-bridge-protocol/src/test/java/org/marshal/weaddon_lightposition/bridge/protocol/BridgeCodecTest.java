package org.marshal.weaddon_lightposition.bridge.protocol;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.marshal.weaddon_lightposition.bridge.protocol.BridgeMessage.*;

class BridgeCodecTest {
    private final UUID nonce = new UUID(0, 1), epoch = new UUID(2, 3);

    @Test void helloHasStableUnwrappedWireBytes() {
        assertEquals("5745534f0101000000000000000000000000000000010001000000000001",
                HexFormat.of().formatHex(BridgeCodec.encode(new Hello(nonce, 1, 0, 1))));
    }

    @Test void negotiatedSessionCloseHasStableWireBytesAndRejectsZeroEpoch() {
        assertEquals("5745534f01050000000000000000000000000000000100000000000000020000000000000003",
                HexFormat.of().formatHex(BridgeCodec.encode(new SessionClosed(nonce, epoch))));
        assertThrows(IllegalArgumentException.class, () -> new SessionClosed(nonce, NO_EPOCH));
        assertThrows(IllegalArgumentException.class,
                () -> new Welcome(nonce, epoch, WelcomeStatus.READY, 1, 0, SELECTION_READ | SESSION_CLOSE, 256));
        assertThrows(IllegalArgumentException.class,
                () -> new Welcome(nonce, epoch, WelcomeStatus.READY, 1, 1, SESSION_CLOSE, 256));
    }

    @Test void allMessagesRoundTripAndRejectWrongDirection() {
        List<BridgeMessage> messages = List.of(new Hello(nonce, 65535, 65535, -1),
                new Welcome(nonce, epoch, WelcomeStatus.READY, 1, 0, 1, 256),
                new Welcome(nonce, epoch, WelcomeStatus.READY, 1, 1, SELECTION_READ | SESSION_CLOSE, 256),
                new SessionClosed(nonce, epoch),
                new Welcome(nonce, NO_EPOCH, WelcomeStatus.DENIED, 1, 0, 0, 256),
                new SelectionRequest(epoch, Long.MAX_VALUE),
                new SelectionReply(epoch, 1, new SelectionState(SelectionStatus.READY,
                        new Bounds(-30_000_000, -64, -20, 30_000_000, 319, 40), -64, 319, 7)),
                new SelectionReply(epoch, 2, SelectionState.unavailable(SelectionStatus.EMPTY)),
                new SelectionReply(epoch, 3, SelectionState.unavailable(SelectionStatus.DENIED)),
                new SelectionReply(epoch, 4, SelectionState.unavailable(SelectionStatus.FAILED)),
                new SelectionReply(epoch, 5, SelectionState.unavailable(SelectionStatus.UNSUPPORTED)));
        for (BridgeMessage message : messages) {
            BridgeCodec.Direction direction = message instanceof Hello || message instanceof SelectionRequest
                    ? BridgeCodec.Direction.TO_SERVER : BridgeCodec.Direction.TO_CLIENT;
            byte[] bytes = BridgeCodec.encode(message);
            assertTrue(bytes.length <= BridgeCodec.MAX_FRAME_BYTES);
            assertEquals(message, BridgeCodec.decode(bytes, direction));
            assertThrows(IllegalArgumentException.class, () -> BridgeCodec.decode(bytes,
                    direction == BridgeCodec.Direction.TO_SERVER ? BridgeCodec.Direction.TO_CLIENT : BridgeCodec.Direction.TO_SERVER));
            for (int length = 0; length < bytes.length; length++) {
                byte[] prefix = Arrays.copyOf(bytes, length);
                assertThrows(IllegalArgumentException.class, () -> BridgeCodec.decode(prefix, direction));
            }
            assertThrows(IllegalArgumentException.class,
                    () -> BridgeCodec.decode(Arrays.copyOf(bytes, bytes.length + 1), direction));
        }
    }

    @Test void rejectsMalformedHeadersStatusesAndSizes() {
        byte[] hello = BridgeCodec.encode(new Hello(nonce, 1, 0, 1));
        for (int index : new int[]{0, 4, 5}) {
            byte[] broken = hello.clone(); broken[index] = (byte) 255;
            assertThrows(IllegalArgumentException.class, () -> BridgeCodec.decode(broken, BridgeCodec.Direction.TO_SERVER));
        }
        byte[] welcome = BridgeCodec.encode(new Welcome(nonce, epoch, WelcomeStatus.READY, 1, 0, 1, 256));
        welcome[38] = (byte) 255;
        assertThrows(IllegalArgumentException.class, () -> BridgeCodec.decode(welcome, BridgeCodec.Direction.TO_CLIENT));
        assertThrows(IllegalArgumentException.class, () -> BridgeCodec.decode(null, BridgeCodec.Direction.TO_SERVER));
        assertThrows(IllegalArgumentException.class, () -> BridgeCodec.decode(new byte[257], BridgeCodec.Direction.TO_SERVER));
        assertThrows(IllegalArgumentException.class, () -> BridgeCodec.decode(hello, null));
    }

    @Test void invalidDomainValuesCannotBecomeWireMessages() {
        assertThrows(IllegalArgumentException.class, () -> new Bounds(10, 0, 0, 9, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Bounds(Integer.MIN_VALUE, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new SelectionRequest(epoch, 0));
        assertThrows(IllegalArgumentException.class, () -> new SelectionRequest(epoch, -1));
        assertThrows(IllegalArgumentException.class, () -> new Hello(nonce, -1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> SelectionState.unavailable(SelectionStatus.READY));
        assertThrows(IllegalArgumentException.class, () -> new SelectionState(SelectionStatus.EMPTY, null, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new SelectionState(SelectionStatus.READY,
                new Bounds(0, -65, 0, 0, 0, 0), -64, 319, 7));
        assertThrows(IllegalArgumentException.class, () -> new Welcome(nonce, NO_EPOCH, WelcomeStatus.READY, 1, 0, 1, 256));
        assertThrows(IllegalArgumentException.class, () -> new Welcome(nonce, epoch, WelcomeStatus.READY, 1, 0, 2, 256));
        assertThrows(IllegalArgumentException.class, () -> new Welcome(nonce, epoch, WelcomeStatus.DENIED, 1, 0, 0, 256));
        assertThrows(IllegalArgumentException.class, () -> new Welcome(nonce, epoch, WelcomeStatus.INCOMPATIBLE, 1, 0, 0, 256));
    }

    @Test void fuzzNeverEscapesAsIndexOrAllocationFailure() {
        Random random = new Random(81473);
        for (int i = 0; i < 10_000; i++) {
            byte[] frame = new byte[random.nextInt(300)]; random.nextBytes(frame);
            try { BridgeCodec.decode(frame, BridgeCodec.Direction.TO_SERVER); }
            catch (IllegalArgumentException expected) { }
            try { BridgeCodec.decode(frame, BridgeCodec.Direction.TO_CLIENT); }
            catch (IllegalArgumentException expected) { }
        }
    }

    @Test void structuredFuzzReachesEveryOpcodeAndNeverEscapesDomainErrors() {
        Random random = new Random(622419);
        int accepted = 0;
        int[] lengths = {30, 49, 30, 64, 38};
        for (int opcode = 1; opcode <= 5; opcode++) {
            for (int i = 0; i < 25_000; i++) {
                byte[] frame = new byte[i % 2 == 0 ? lengths[opcode - 1] : random.nextInt(257)];
                random.nextBytes(frame);
                if (frame.length >= 6) {
                    System.arraycopy(HexFormat.of().parseHex("5745534f01"), 0, frame, 0, 5);
                    frame[5] = (byte) opcode;
                }
                BridgeCodec.Direction direction = opcode == 1 || opcode == 3
                        ? BridgeCodec.Direction.TO_SERVER : BridgeCodec.Direction.TO_CLIENT;
                if (assertCanonicalOrRejected(frame, direction)) accepted++;
            }
        }
        assertTrue(accepted > 10_000, "Fuzzer did not reach valid payload decoding");
    }

    @Test void everySingleBitMutationHasACanonicalResultOrDomainRejection() {
        List<BridgeMessage> messages = List.of(new Hello(nonce, 1, 0, 1),
                new Welcome(nonce, epoch, WelcomeStatus.READY, 1, 0, 1, 256),
                new SelectionRequest(epoch, 123), new SessionClosed(nonce, epoch),
                new SelectionReply(epoch, 123, new SelectionState(SelectionStatus.READY,
                        new Bounds(-17, -64, -1, 33, 319, 16), -64, 319, 7)),
                new SelectionReply(epoch, 123, SelectionState.unavailable(SelectionStatus.DENIED)));
        for (BridgeMessage message : messages) {
            byte[] original = BridgeCodec.encode(message);
            BridgeCodec.Direction direction = message instanceof Hello || message instanceof SelectionRequest
                    ? BridgeCodec.Direction.TO_SERVER : BridgeCodec.Direction.TO_CLIENT;
            for (int bit = 0; bit < original.length * 8; bit++) {
                byte[] changed = original.clone();
                changed[bit / 8] ^= (byte) (1 << (bit % 8));
                assertCanonicalOrRejected(changed, direction);
            }
            assertArrayEquals(original, BridgeCodec.encode(message), "Decoder mutated the source message");
        }
    }

    private boolean assertCanonicalOrRejected(byte[] frame, BridgeCodec.Direction direction) {
        byte[] original = frame.clone();
        BridgeMessage decoded;
        try { decoded = BridgeCodec.decode(frame, direction); }
        catch (IllegalArgumentException expected) {
            assertArrayEquals(original, frame, "Rejection mutated the input");
            return false;
        }
        assertArrayEquals(original, frame, "Decoder mutated the input");
        assertArrayEquals(original, BridgeCodec.encode(decoded), "Accepted noncanonical frame");
        return true;
    }
}
