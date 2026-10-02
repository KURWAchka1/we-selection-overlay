package org.marshal.weaddon_lightposition.preview;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.function.RegionFunction;
import com.sk89q.worldedit.function.block.BlockReplace;
import com.sk89q.worldedit.function.mask.BlockMask;
import com.sk89q.worldedit.function.mask.BlockTypeMask;
import com.sk89q.worldedit.function.mask.ExistingBlockMask;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.mask.MaskIntersection;
import com.sk89q.worldedit.function.mask.MaskUnion;
import com.sk89q.worldedit.function.mask.Masks;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.function.pattern.RandomPattern;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.regions.Regions;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public final class WorldEditPreviewProgram {
    private record Context(Region region, PreviewExtent extent, RegionFunction replace, Mask filter,
                           Supplier<Iterator<BlockVector3>> candidates, Supplier<Iterator<BlockVector2>> columns) { }
    private static final Map<PreviewCommand.Kind, Function<Context, PreviewProvider>> PROVIDERS = new EnumMap<>(PreviewCommand.Kind.class);
    static {
        PROVIDERS.put(PreviewCommand.Kind.SET, c -> visit(c, c.replace));
        PROVIDERS.put(PreviewCommand.Kind.REPLACE, c -> visit(c,
                p -> c.filter.test(p) && c.replace.apply(p)));
        PROVIDERS.put(PreviewCommand.Kind.WALLS, c -> boundary(c, false));
        PROVIDERS.put(PreviewCommand.Kind.FACES, c -> boundary(c, true));
        PROVIDERS.put(PreviewCommand.Kind.OVERLAY, WorldEditPreviewProgram::overlay);
        PROVIDERS.put(PreviewCommand.Kind.CENTER, WorldEditPreviewProgram::center);
    }

    private final PreviewProvider provider;
    private final boolean random;
    private long visited;

    public WorldEditPreviewProgram(PreviewCommand command, Region region, PreviewExtent extent,
                                   ParserContext parser, Mask globalMask) throws WorldEditException {
        this(command, region, extent, parser, globalMask, region::iterator,
                () -> Regions.asFlatRegion(region).asFlatRegion().iterator());
    }

    public WorldEditPreviewProgram(PreviewCommand command, Region region, PreviewExtent extent,
                                   ParserContext parser, Mask globalMask,
                                   Supplier<Iterator<BlockVector3>> candidates,
                                   Supplier<Iterator<BlockVector2>> columns) throws WorldEditException {
        if (command.kind() == PreviewCommand.Kind.NATURALIZE) {
            random = false;
            provider = naturalize(new Context(region, extent, null, copyMask(globalMask, extent), candidates, columns));
            return;
        }
        if (command.kind() == PreviewCommand.Kind.FLORA) {
            Mask global = copyMask(globalMask, extent);
            var temperate = com.sk89q.worldedit.function.generator.FloraGenerator.getTemperatePattern();
            var desert = com.sk89q.worldedit.function.generator.FloraGenerator.getDesertPattern();
            var density = new com.sk89q.worldedit.function.mask.NoiseFilter2D(
                    new com.sk89q.worldedit.math.noise.RandomNoise(), Double.parseDouble(command.pattern()) / 100);
            random = true;
            Context context = new Context(region, extent, null, null, candidates, columns);
            provider = ground(context, p -> {
                var soil = extent.getBlock(p).getBlockType();
                Pattern plants = soil == com.sk89q.worldedit.world.block.BlockTypes.GRASS_BLOCK ? temperate
                        : soil == com.sk89q.worldedit.world.block.BlockTypes.SAND ? desert : null;
                BlockVector3 output = p.add(0, 1, 0);
                return plants != null && global.test(output) && extent.setBlock(output, plants.applyBlock(p));
            }, density);
            return;
        }
        validatePattern(command.pattern());
        parser.setExtent(extent);
        Pattern pattern = WorldEdit.getInstance().getPatternFactory().parseFromInput(command.pattern(), parser);
        if (!(pattern instanceof BlockStateHolder<?> || pattern instanceof RandomPattern))
            throw new IllegalArgumentException("unsupported_pattern");
        random = pattern instanceof RandomPattern;
        BlockReplace replace = new BlockReplace(extent, pattern);
        Mask global = copyMask(globalMask, extent);
        RegionFunction maskedReplace = p -> global.test(p) && replace.apply(p);
        Mask filter = Masks.alwaysTrue();
        if (!command.mask().isEmpty()) {
            validateMask(command.mask());
            filter = WorldEdit.getInstance().getMaskFactory().parseFromInput(command.mask(), parser);
            filter = copyMask(filter, extent);
        }
        Context context = new Context(region, extent, maskedReplace, filter, candidates, columns);
        provider = switch (command.kind()) {
            case HOLLOW -> new PreviewHollowProvider(region, extent, maskedReplace, (int) command.thickness(), candidates);
            case LINE, CURVE -> new PreviewGeometryProvider(command, region, extent, maskedReplace);
            default -> PROVIDERS.get(command.kind()).apply(context);
        };
    }

    public boolean random() { return random; }
    public long visited() { return visited; }
    public boolean complete() { return !provider.hasNext(); }
    public void advance(int maxSteps, long deadlineNanos) throws WorldEditException {
        int steps = 0;
        while (provider.hasNext() && steps++ < maxSteps && System.nanoTime() < deadlineNanos) {
            provider.advance();
            visited++;
        }
    }

    // Match EditSession's BlockReplace/RegionShape semantics, with a resumable iterator.
    // WorldEdit 7.4 RegionVisitor.resume ignores RunContext and traverses the entire region.
    private static PreviewProvider visit(Context c, RegionFunction function) {
        Iterator<BlockVector3> iterator = c.candidates.get();
        return new PreviewProvider() {
            @Override public boolean hasNext() { return iterator.hasNext(); }
            @Override public void advance() throws WorldEditException {
                BlockVector3 p = iterator.next();
                if (c.region.contains(p)) function.apply(p);
            }
        };
    }

    private static PreviewProvider boundary(Context c, boolean faces) {
        if (c.region instanceof CuboidRegion) return visit(c, p -> {
            BlockVector3 min = c.region.getMinimumPoint(), max = c.region.getMaximumPoint();
            boolean edge = p.x() == min.x() || p.x() == max.x() || p.z() == min.z() || p.z() == max.z();
            if (faces) edge |= p.y() == min.y() || p.y() == max.y();
            return edge && c.replace.apply(p);
        });
        return visit(c, p -> {
            boolean edge = !c.region.contains(p.add(1, 0, 0)) || !c.region.contains(p.add(-1, 0, 0))
                    || !c.region.contains(p.add(0, 0, 1)) || !c.region.contains(p.add(0, 0, -1));
            if (faces) edge |= !c.region.contains(p.add(0, 1, 0)) || !c.region.contains(p.add(0, -1, 0));
            else {
                int minY = c.region.getMinimumPoint().y(), maxY = c.region.getMaximumPoint().y();
                if (p.y() < maxY) edge |= !c.region.contains(p.add(0, 1, 0));
                if (p.y() > minY) edge |= !c.region.contains(p.add(0, -1, 0));
            }
            return edge && c.replace.apply(p);
        });
    }

    // LayerVisitor/GroundFunction semantics: skip underground columns, then the first
    // non-air surface within minY..maxY. The block above maxY is intentionally included.
    private static PreviewProvider overlay(Context c) {
        return ground(c, p -> c.replace.apply(p.add(0, 1, 0)), null);
    }

    private static PreviewProvider center(Context c) {
        var middle = c.region.getCenter();
        var points = new CuboidRegion(BlockVector3.at((int) middle.x(), (int) middle.y(), (int) middle.z()),
                BlockVector3.at(com.sk89q.worldedit.math.MathUtils.roundHalfUp(middle.x()),
                        com.sk89q.worldedit.math.MathUtils.roundHalfUp(middle.y()),
                        com.sk89q.worldedit.math.MathUtils.roundHalfUp(middle.z()))).iterator();
        return new PreviewProvider() {
            public boolean hasNext() { return points.hasNext(); }
            public void advance() throws WorldEditException {
                var p = points.next();
                if (c.extent.captured(p)) c.replace.apply(p);
            }
        };
    }

    // Naturalizer is coupled to EditSession. Preserve its layer/material semantics
    // without constructing an editing session or traversing a whole column per step.
    private static PreviewProvider naturalize(Context c) {
        var columns = c.columns.get();
        var footprint = Regions.asFlatRegion(c.region);
        int minY = c.region.getMinimumPoint().y(), maxY = c.region.getMaximumPoint().y();
        Mask soil = new BlockTypeMask(c.extent, com.sk89q.worldedit.world.block.BlockTypes.GRASS_BLOCK,
                com.sk89q.worldedit.world.block.BlockTypes.DIRT, com.sk89q.worldedit.world.block.BlockTypes.STONE);
        return new PreviewProvider() {
            BlockVector2 column;
            int y, groundY;
            boolean found;
            public boolean hasNext() { return column != null || columns.hasNext(); }
            public void advance() throws WorldEditException {
                if (column == null) {
                    var next = columns.next();
                    if (!footprint.contains(next.toBlockVector3(minY)) || soil.test(next.toBlockVector3(maxY + 1))) return;
                    column = next; y = maxY; found = false;
                }
                var p = column.toBlockVector3(y);
                if (soil.test(p)) {
                    if (!found) { found = true; groundY = y; }
                    int depth = groundY - y;
                    var type = depth == 0 ? com.sk89q.worldedit.world.block.BlockTypes.GRASS_BLOCK
                            : depth <= 3 ? com.sk89q.worldedit.world.block.BlockTypes.DIRT : com.sk89q.worldedit.world.block.BlockTypes.STONE;
                    if (c.filter.test(p)) c.extent.setBlock(p, type.getDefaultState());
                }
                if (--y < minY) column = null;
            }
        };
    }

    private static PreviewProvider ground(Context c, RegionFunction atGround, com.sk89q.worldedit.function.mask.Mask2D density) {
        Iterator<BlockVector2> columns = c.columns.get();
        var footprint = Regions.asFlatRegion(c.region);
        int minY = c.region.getMinimumPoint().y(), maxY = c.region.getMaximumPoint().y();
        Mask ground = new ExistingBlockMask(c.extent);
        return new PreviewProvider() {
            private BlockVector2 column;
            private int y;
            @Override public boolean hasNext() { return column != null || columns.hasNext(); }
            @Override public void advance() throws WorldEditException {
                if (column == null) {
                    BlockVector2 next = columns.next();
                    if (!footprint.contains(next.toBlockVector3(minY))) return;
                    if (density != null && !density.test(next)) return;
                    if (ground.test(next.toBlockVector3(maxY + 1))) return;
                    column = next;
                    y = maxY;
                }
                if (ground.test(column.toBlockVector3(y))) {
                    atGround.apply(column.toBlockVector3(y));
                    column = null;
                } else if (--y < minY) {
                    column = null;
                }
            }
        };
    }

    public static Mask copyMask(Mask mask, PreviewExtent extent) {
        if (mask == null || mask == Masks.alwaysTrue()) return Masks.alwaysTrue();
        if (mask == Masks.negate(Masks.alwaysTrue())) return mask;
        // Exact classes only: an extension must not smuggle stateful semantics via a subclass.
        if (mask.getClass() == BlockMask.class) return new BlockMask(extent, ((BlockMask) mask).getBlocks());
        if (mask.getClass() == BlockTypeMask.class) return new BlockTypeMask(extent, ((BlockTypeMask) mask).getBlocks());
        if (mask.getClass() == ExistingBlockMask.class) return new ExistingBlockMask(extent);
        if (mask.getClass() == MaskIntersection.class || mask.getClass() == MaskUnion.class) {
            List<Mask> copies = ((MaskIntersection) mask).getMasks().stream().map(m -> copyMask(m, extent)).toList();
            return mask.getClass() == MaskUnion.class ? new MaskUnion(copies) : new MaskIntersection(copies);
        }
        throw new IllegalArgumentException("unsupported_mask");
    }

    private static void validatePattern(String input) {
        List<String> terms = splitTerms(input);
        if (terms.size() > 32) throw new IllegalArgumentException("unsupported_pattern");
        for (String term : terms) {
            int percent = term.indexOf('%');
            if (percent >= 0) {
                double weight;
                try { weight = Double.parseDouble(term.substring(0, percent)); }
                catch (NumberFormatException ex) { throw new IllegalArgumentException("unsupported_pattern"); }
                if (!Double.isFinite(weight) || weight <= 0 || weight > 1_000_000)
                    throw new IllegalArgumentException("unsupported_pattern");
                term = term.substring(percent + 1);
            }
            if (!blockToken(term)) throw new IllegalArgumentException("unsupported_pattern");
        }
    }

    private static void validateMask(String input) {
        List<String> terms = splitTerms(input);
        if (terms.size() > 32) throw new IllegalArgumentException("unsupported_mask");
        for (String term : terms) if (!term.equals("#existing") && !blockToken(term))
            throw new IllegalArgumentException("unsupported_mask");
    }

    private static boolean blockToken(String token) {
        return !List.of("hand", "offhand", "pos1", "pos2").contains(token)
                && token.matches("[a-z0-9_./:-]+(?:\\[[a-z0-9_=,./:-]+\\])?");
    }

    private static List<String> splitTerms(String text) {
        List<String> terms = new ArrayList<>();
        int depth = 0, start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') depth--;
            else if (c == ',' && depth == 0) { terms.add(text.substring(start, i)); start = i + 1; }
            if (depth < 0 || depth > 1) throw new IllegalArgumentException("incomplete");
        }
        if (depth != 0) throw new IllegalArgumentException("incomplete");
        terms.add(text.substring(start));
        return terms;
    }
}
