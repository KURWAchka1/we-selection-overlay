package org.marshal.weaddon_lightposition.preview;

import com.sk89q.worldedit.WorldEditException;

/** One bounded unit at a time; providers never receive a writable game world. */
public interface PreviewProvider {
    boolean hasNext();
    void advance() throws WorldEditException;
}
