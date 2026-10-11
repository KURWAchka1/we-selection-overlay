package org.marshal.weaddon_lightposition.bridge.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenBucketTest {
    @Test void invalidLimitsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new TokenBucket(0, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucket(1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucket(-1, 1, 0));
    }

    @Test void fractionalRefillPreservesCreditWithoutExceedingCapacity() {
        TokenBucket bucket = new TokenBucket(4, 2, 0);
        assertTrue(bucket.take(0));
        assertTrue(bucket.take(0));
        assertFalse(bucket.take(0));
        assertFalse(bucket.take(125_000_000L));
        assertTrue(bucket.take(250_000_000L));
        assertFalse(bucket.take(250_000_000L));
        assertTrue(bucket.take(10_000_000_000L));
        assertTrue(bucket.take(10_000_000_000L));
        assertFalse(bucket.take(10_000_000_000L));
    }

    @Test void backwardClockCannotCreateCredit() {
        TokenBucket bucket = new TokenBucket(1, 1, 1_000_000_000L);
        assertTrue(bucket.take(1_000_000_000L));
        assertFalse(bucket.take(0));
        assertFalse(bucket.take(1_999_999_999L));
        assertTrue(bucket.take(2_000_000_000L));
    }

    @Test void monotonicClockSignWrapKeepsElapsedTimeCorrect() {
        long start = Long.MAX_VALUE - 100_000_000L;
        TokenBucket bucket = new TokenBucket(4, 1, start);
        assertTrue(bucket.take(start));
        assertFalse(bucket.take(start + 125_000_000L));
        assertTrue(bucket.take(start + 250_000_000L));
        assertFalse(bucket.take(start + 250_000_000L));
    }
}
