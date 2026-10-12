package org.marshal.weaddon_lightposition.bridge.protocol;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BridgeRestartHintTest {
    @Test void goldenFrameHasNoPrefixAndIsNotAnApplicationMessage() {
        byte[] frame = BridgeRestartHint.encode(new UUID(2, 3));
        assertEquals("57455352010100000000000000020000000000000003", HexFormat.of().formatHex(frame));
        assertEquals(new UUID(2, 3), BridgeRestartHint.decode(frame));
        for (var direction : BridgeCodec.Direction.values())
            assertThrows(IllegalArgumentException.class, () -> BridgeCodec.decode(frame, direction));
    }

    @Test void malformedHintsAreRejectedBeforeAnyVariableAllocation() {
        byte[] frame = BridgeRestartHint.encode(new UUID(2, 3));
        for (int size : new int[]{0, 1, 6, 21, 23, 256, 4096})
            assertThrows(IllegalArgumentException.class, () -> BridgeRestartHint.decode(Arrays.copyOf(frame, size)));
        assertThrows(IllegalArgumentException.class, () -> BridgeRestartHint.decode(null));
        assertThrows(IllegalArgumentException.class, () -> BridgeRestartHint.encode(null));
        assertThrows(IllegalArgumentException.class, () -> BridgeRestartHint.encode(new UUID(0, 0)));
        Arrays.fill(frame, 6, frame.length, (byte) 0);
        assertThrows(IllegalArgumentException.class, () -> BridgeRestartHint.decode(frame));
        for (int i = 0; i < 6; i++) {
            byte[] bad = BridgeRestartHint.encode(new UUID(2, 3)); bad[i] ^= 1;
            assertThrows(IllegalArgumentException.class, () -> BridgeRestartHint.decode(bad));
        }
    }

    @Test void generationsRoundTripAndEveryAcceptedFuzzFrameIsCanonical() {
        Random random = new Random(42);
        for (int i = 0; i < 10_000; i++) {
            UUID generation = new UUID(random.nextLong(), random.nextLong());
            assertEquals(generation, BridgeRestartHint.decode(BridgeRestartHint.encode(generation)));
            byte[] frame = new byte[random.nextInt(64)]; random.nextBytes(frame);
            try { assertArrayEquals(frame, BridgeRestartHint.encode(BridgeRestartHint.decode(frame))); }
            catch (IllegalArgumentException expected) { }
        }
    }
}
