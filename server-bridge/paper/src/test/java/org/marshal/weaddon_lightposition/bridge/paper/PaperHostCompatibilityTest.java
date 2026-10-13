package org.marshal.weaddon_lightposition.bridge.paper;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PaperHostCompatibilityTest {
    @Test void acceptsExactOfficialDevelopmentTuples() {
        for (String[] tuple : new String[][]{{"1.21.11", "7.4.2"}, {"26.1.2", "7.4.3"}, {"26.2", "7.4.5"}}) {
            assertTrue(PaperHostCompatibility.supports(tuple[0], "WorldEdit", tuple[1], true));
            assertTrue(PaperHostCompatibility.supports(tuple[0], "WorldEdit", tuple[1] + "+7590-build", true));
        }
    }

    @Test void rejectsVersionPrefixConfusionAndOtherDistributions() {
        for (String version : new String[]{"7.4.50", "7.4.5-SNAPSHOT", "7.4.5-beta", "", "7.5.0", "7.4.5_FAWE"})
            assertFalse(PaperHostCompatibility.supports("26.2", "WorldEdit", version, true));
        for (String name : new String[]{"FastAsyncWorldEdit", "worldedit", "", null})
            assertFalse(PaperHostCompatibility.supports("26.2", name, "7.4.5", true));
    }

    @Test void rejectsUnknownMinecraftAndMismatchedPairs() {
        for (String minecraft : new String[]{"26.1", "26.1.1", "26.2.1", "26.3", "1.21.10", "", null})
            assertFalse(PaperHostCompatibility.supports(minecraft, "WorldEdit", "7.4.5", true));
        assertFalse(PaperHostCompatibility.supports("26.2", "WorldEdit", "7.4.3", true));
        assertFalse(PaperHostCompatibility.supports("1.21.11", "WorldEdit", "7.4.5", true));
    }

    @Test void refusesDisabledOrAbsentWorldEdit() {
        assertFalse(PaperHostCompatibility.supports("26.2", "WorldEdit", "7.4.5", false));
        assertFalse(PaperHostCompatibility.supports("26.2", "WorldEdit", null, true));
    }
}
