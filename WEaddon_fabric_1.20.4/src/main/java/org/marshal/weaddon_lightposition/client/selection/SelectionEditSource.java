package org.marshal.weaddon_lightposition.client.selection;

import net.minecraft.client.MinecraftClient;
import org.marshal.weaddon_lightposition.selection.CuboidDraft;
import org.marshal.weaddon_lightposition.selection.SelectionEditSnapshot;
import java.util.function.Consumer;

public interface SelectionEditSource {
    void read(MinecraftClient client, Consumer<SelectionEditSnapshot> result);
    void commit(MinecraftClient client, SelectionEditSnapshot origin, CuboidDraft draft, CuboidDraft.Face face,
                CuboidDraft.Corner corner, Consumer<SelectionEditSnapshot> result);
}
