package org.marshal.weaddon_lightposition.client.selection;

import net.minecraft.client.Minecraft;
import org.marshal.weaddon_lightposition.selection.CuboidDraft;
import org.marshal.weaddon_lightposition.selection.SelectionEditSnapshot;
import java.util.function.Consumer;

public interface SelectionEditSource {
    void read(Minecraft client, Consumer<SelectionEditSnapshot> result);
    void commit(Minecraft client, SelectionEditSnapshot origin, CuboidDraft draft, CuboidDraft.Face face, Consumer<SelectionEditSnapshot> result);
}
