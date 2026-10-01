package org.marshal.weaddon_lightposition.client.screen;

public final class UiMotion {
    private long started = System.nanoTime();
    private float from;
    private boolean closing;
    private Runnable closeAction;

    public float alpha() {
        if (UiStyle.reducedMotion() || UiStyle.current() == UiStyle.LEGACY) return closing ? 0.0f : 1.0f;
        float progress = Math.min(1.0f, Math.max(0.0f, (System.nanoTime() - started) / (closing ? 130_000_000.0f : 190_000_000.0f)));
        float eased = 1.0f - (1.0f - progress) * (1.0f - progress) * (1.0f - progress);
        return closing ? from * (1.0f - eased) : eased;
    }

    public void close(Runnable action) {
        if (closing || UiStyle.reducedMotion() || UiStyle.current() == UiStyle.LEGACY) {
            closeAction = null;
            action.run();
            return;
        }
        from = alpha();
        started = System.nanoTime();
        closing = true;
        closeAction = action;
    }

    public boolean closing() { return closing; }

    public void finishIfReady() {
        if (closing && alpha() <= 0.001f && closeAction != null) {
            Runnable action = closeAction;
            closeAction = null;
            action.run();
        }
    }

    public static final class Hover {
        private long started;
        private float from;
        private float target;

        public float value(boolean hovered) {
            long now = System.nanoTime();
            float next = hovered ? 1.0f : 0.0f;
            if (UiStyle.reducedMotion() || UiStyle.current() == UiStyle.LEGACY) {
                from = target = next;
                started = now;
                return next;
            }
            if (next != target) {
                from = sample(now);
                target = next;
                started = now;
            }
            return sample(now);
        }

        private float sample(long now) {
            float progress = Math.max(0.0f, Math.min(1.0f, (now - started) / 100_000_000.0f));
            float eased = 1.0f - (1.0f - progress) * (1.0f - progress);
            return from + (target - from) * eased;
        }

        public static Hover[] states(int count) {
            Hover[] states = new Hover[count];
            for (int i = 0; i < count; i++) states[i] = new Hover();
            return states;
        }
    }
}
