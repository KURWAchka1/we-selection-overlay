package org.marshal.weaddon_lightposition.bridge.server;

final class TokenBucket {
    private final double perNano;
    private final int capacity;
    private double tokens;
    private long last;

    TokenBucket(int perSecond, int capacity, long now) {
        if (perSecond <= 0 || capacity <= 0) throw new IllegalArgumentException("Invalid rate limit");
        this.perNano = perSecond / 1_000_000_000.0;
        this.capacity = capacity;
        tokens = capacity;
        last = now;
    }

    boolean take(long now) {
        long elapsed = now - last;
        if (elapsed > 0) {
            tokens = Math.min(capacity, tokens + elapsed * perNano);
            last = now;
        }
        if (tokens < 1.0) return false;
        tokens -= 1.0;
        return true;
    }
}
