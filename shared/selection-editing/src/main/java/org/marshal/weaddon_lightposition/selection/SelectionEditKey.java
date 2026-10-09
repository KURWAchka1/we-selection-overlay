package org.marshal.weaddon_lightposition.selection;

/** Monotonic tap/hold recognition. Repeats and a cancelled held key cannot re-enter a tool. */
public final class SelectionEditKey {
    public enum Action { NONE, TOGGLE, OPEN_MENU, CLOSE_MENU }
    public static final long HOLD_NANOS = 400_000_000L;
    private long started;
    private boolean pressed, menu, blocked;

    public Action update(boolean down, long now) {
        if (blocked) { if (!down) blocked = false; return Action.NONE; }
        if (down) {
            if (!pressed) { pressed = true; started = now; }
            if (!menu && now - started >= HOLD_NANOS) { menu = true; return Action.OPEN_MENU; }
        } else if (pressed) {
            pressed = false;
            if (menu) { menu = false; return Action.CLOSE_MENU; }
            // A stall cannot turn an intended long hold into an accidental apply.
            if (now - started < HOLD_NANOS) return Action.TOGGLE;
        }
        return Action.NONE;
    }

    public void cancel(boolean down) { pressed = menu = false; blocked = down; }
}
