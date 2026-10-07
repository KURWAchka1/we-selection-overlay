package org.marshal.weaddon_lightposition.selection;

/** Token is opaque on the client; its contents are owned only by the server thread. */
public record SelectionEditSnapshot(Object token, CuboidDraft bounds, int minY, int maxY, boolean expand, boolean contract, String reason) {
    public static SelectionEditSnapshot unavailable(String reason) { return new SelectionEditSnapshot(null, null, 0, 0, false, false, reason); }
    public boolean ready() { return token != null && bounds != null; }
}
