package org.marshal.weaddon_lightposition.bridge.server;

import java.util.Objects;
import java.util.UUID;

/** Owner-thread correlation IDs, not bearer credentials. Seeded by an existing random session epoch. */
final class RevisionSequence {
    private final UUID seed;
    private long high, low;
    private boolean exhausted;

    RevisionSequence(UUID seed) {
        this.seed = Objects.requireNonNull(seed);
        high = seed.getMostSignificantBits(); low = seed.getLeastSignificantBits();
    }

    UUID next() {
        if (exhausted) throw new IllegalStateException("Revision identifier space exhausted");
        do {
            if (++low == 0) high++;
            if (high == seed.getMostSignificantBits() && low == seed.getLeastSignificantBits()) {
                exhausted = true;
                throw new IllegalStateException("Revision identifier space exhausted");
            }
        } while (high == 0 && low == 0);
        return new UUID(high, low);
    }
}
