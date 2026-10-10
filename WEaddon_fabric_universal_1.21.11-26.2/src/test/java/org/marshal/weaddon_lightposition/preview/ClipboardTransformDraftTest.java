package org.marshal.weaddon_lightposition.preview;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClipboardTransformDraftTest {
    @Test void cutPasteAndOtherCommandsNeverRouteToClipboardTransforms() {
        for (String value : new String[]{"", "//cut", "//cut stone", "//paste", "//paste -a", "//copy", "//rotatex 90", "//set stone"})
            assertFalse(ClipboardTransformDraft.parse(value).recognized(), value);
    }
    @Test void rotationKeepsWorldEditArgumentOrderAndDefaults() {
        var single = ClipboardTransformDraft.parse("  //ROTATE\t90 ");
        assertTrue(single.valid()); assertEquals(90, single.y()); assertEquals(0, single.x()); assertEquals(0, single.z());
        var three = ClipboardTransformDraft.parse("//rotate -90 180 270");
        assertEquals(-90, three.y()); assertEquals(180, three.x()); assertEquals(270, three.z());
        assertEquals(ClipboardTransformDraft.parse("//rotate 90"), ClipboardTransformDraft.parse("//rotate 90.0 0 -0"));
    }
    @Test void incompleteAndInvalidAnglesRemainRecognizedButNeverTransform() {
        for (String value : new String[]{"//rotate", "//rotate -", "//rotate NaN", "//rotate Infinity", "//rotate 90 NaN", "//rotate 90 0 1e309", "//rotate 90 0 0 1"}) {
            var draft = ClipboardTransformDraft.parse(value);
            assertTrue(draft.recognized(), value); assertFalse(draft.valid(), value); assertFalse(draft.reason().isBlank(), value);
        }
    }
    @Test void mirrorSharesFlipSyntaxAndDefaultLookDirection() {
        assertEquals(ClipboardTransformDraft.parse("//flip"), ClipboardTransformDraft.parse("//mirror me"));
        assertEquals("me", ClipboardTransformDraft.parse("//flip").direction());
        for (String direction : new String[]{"north", "south", "east", "west", "up", "down", "left", "right", "forward", "back", "n"}) {
            var draft = ClipboardTransformDraft.parse("//mirror " + direction.toUpperCase(java.util.Locale.ROOT));
            assertTrue(draft.valid()); assertEquals(direction, draft.direction());
        }
        assertFalse(ClipboardTransformDraft.parse("//flip north east").valid());
    }
    @Test void nonOrthogonalRotationsWarnWithoutBeingRejected() {
        assertTrue(ClipboardTransformDraft.parse("//rotate 45").approximate());
        assertTrue(ClipboardTransformDraft.parse("//rotate 90 22.5").approximate());
        assertFalse(ClipboardTransformDraft.parse("//rotate -180 270 360").approximate());
    }
}
