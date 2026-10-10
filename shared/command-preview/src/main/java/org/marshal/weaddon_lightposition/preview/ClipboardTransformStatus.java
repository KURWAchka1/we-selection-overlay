package org.marshal.weaddon_lightposition.preview;

public record ClipboardTransformStatus(String reason, boolean approximate) {
    public static final ClipboardTransformStatus IDLE = new ClipboardTransformStatus("waiting", false);
    public static final ClipboardTransformStatus UPDATING = new ClipboardTransformStatus("clipboard_updating", false);
    public static ClipboardTransformStatus status(String reason) { return new ClipboardTransformStatus(reason, false); }
}
