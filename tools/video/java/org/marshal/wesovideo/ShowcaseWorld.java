package org.marshal.wesovideo;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** Original filming set: an architect's timber-and-stone atelier, not a test gate. */
final class ShowcaseWorld {
    static final int MIN_X = -9, MIN_Y = 65, MIN_Z = -3;
    static final int MAX_X = 20, MAX_Y = 95, MAX_Z = 20;
    private final List<String> commands = new ArrayList<>();
    private final Map<Long, Integer> heights = new HashMap<>();

    static List<String> commands() {
        ShowcaseWorld scene = new ShowcaseWorld();
        scene.landscape();
        scene.atelier();
        scene.tower();
        scene.garden();
        scene.interior();
        scene.commands.add("gamemode spectator @p");
        scene.commands.add("time set 2000");
        scene.commands.add("weather clear");
        scene.commands.add("gamerule minecraft:advance_time false");
        scene.commands.add("gamerule minecraft:spawn_mobs false");
        scene.commands.add("tp @p -40 79.38 54 -135 1");
        return List.copyOf(scene.commands);
    }

    private void landscape() {
        for (int x = -96; x < 96; x += 48) for (int z = -96; z < 96; z += 48) {
            fill(x, 62, z, x + 47, 63, z + 47, "dirt");
            fill(x, 64, z, x + 47, 64, z + 47, "grass_block");
        }
        mound(-34, -22, 25, 17, 8);
        mound(36, -32, 31, 16, 9);
        mound(-50, 28, 18, 22, 5);
        mound(65, 17, 22, 26, 7);
        for (int[] tree : new int[][]{{-24,-6},{-18,-15},{-11,-24},{-3,-25},{8,-27},{21,-24},
                {30,-18},{37,-11},{46,-4},{-30,8},{-27,19},{-18,29},{40,28},{46,20},
                {-42,-40},{-31,-41},{-20,-38},{4,-38},{17,-39},{43,-35},{52,-23}}) {
            int y = heights.getOrDefault(key(tree[0], tree[1]), 64) + 1;
            commands.add("place feature minecraft:oak " + tree[0] + " " + y + " " + tree[1]);
        }
        // Broad shallow water, with a real irregular bank and a separate empty copy area.
        for (int z = -2; z <= 22; z++) for (int x = 25; x <= 50; x++) {
            double r = sq((x - 38) / 12.0) + sq((z - 10) / 11.0);
            if (r < 1) { block(x, 63, z, "sand"); block(x, 64, z, "water"); }
            else if (r < 1.17 && (x + z) % 3 != 0) block(x, 64, z, "gravel");
        }
        fill(51, 64, 2, 79, 64, 26, "grass_block");
    }

    private void mound(int cx, int cz, int rx, int rz, int height) {
        for (int layer = 0; layer < height; layer++) {
            double scale = Math.sqrt(1 - layer / (double) height);
            int a = (int) (rx * scale), b = (int) (rz * scale);
            for (int z = -b; z <= b; z++) {
                int span = (int) (a * Math.sqrt(Math.max(0, 1 - sq(z / (double) Math.max(1, b)))));
                fill(cx - span, 65 + layer, cz + z, cx + span, 65 + layer, cz + z, "grass_block");
                for (int x = cx - span; x <= cx + span; x++)
                    heights.merge(key(x, cz + z), 65 + layer, Math::max);
            }
        }
    }

    private void atelier() {
        fill(-1, 65, -1, 19, 66, 15, "stone_bricks");
        fill(0, 67, 0, 18, 72, 14, "stone_bricks");
        fill(1, 67, 1, 17, 72, 13, "air");
        fill(0, 73, 0, 18, 78, 14, "calcite");
        fill(1, 74, 1, 17, 78, 13, "air");
        fill(1, 73, 1, 17, 73, 13, "spruce_planks");
        fill(1, 79, 1, 17, 79, 13, "spruce_planks");
        for (int x : new int[]{0, 6, 12, 18}) {
            fill(x, 67, 0, x, 79, 0, "stripped_spruce_log");
            fill(x, 67, 14, x, 79, 14, "stripped_spruce_log");
        }
        for (int z : new int[]{0, 5, 10, 14}) {
            fill(0, 67, z, 0, 79, z, "stripped_spruce_log");
            fill(18, 67, z, 18, 79, z, "stripped_spruce_log");
        }
        for (int y : new int[]{72, 73, 79}) {
            fill(0, y, 0, 18, y, 0, "stripped_spruce_log[axis=x]");
            fill(0, y, 14, 18, y, 14, "stripped_spruce_log[axis=x]");
            fill(0, y, 0, 0, y, 14, "stripped_spruce_log[axis=z]");
            fill(18, y, 0, 18, y, 14, "stripped_spruce_log[axis=z]");
        }
        for (int x : new int[]{3, 9, 15}) {
            window(x, 68, 14, true);
            window(x, 75, 14, true);
            window(x, 75, 0, false);
        }
        for (int z : new int[]{3, 8, 12}) {
            fill(18, 68, z - 1, 18, 70, z, "glass");
            fill(18, 75, z - 1, 18, 77, z, "glass");
            block(19, 74, z, "spruce_trapdoor[facing=east,open=true]");
        }
        fill(8, 67, 14, 10, 70, 14, "air");
        for (int x : new int[]{8, 9}) {
            block(x, 67, 14, "spruce_door[facing=south,half=lower,hinge=" + (x == 8 ? "left" : "right") + "]");
            block(x, 68, 14, "spruce_door[facing=south,half=upper,hinge=" + (x == 8 ? "left" : "right") + "]");
        }
        block(10, 68, 14, "stone_bricks");
        fill(1, 73, 15, 17, 73, 18, "spruce_planks");
        fill(1, 74, 18, 17, 74, 18, "spruce_fence");
        fill(1, 74, 15, 1, 74, 18, "spruce_fence");
        fill(17, 74, 15, 17, 74, 18, "spruce_fence");
        fill(8, 74, 14, 10, 76, 14, "glass");
        for (int x : new int[]{1, 6, 12, 17}) {
            fill(x, 67, 18, x, 72, 18, "spruce_log");
            block(x, 72, 17, "spruce_stairs[facing=north,half=top]");
        }
        for (int x : new int[]{4, 14}) block(x, 72, 17, "lantern[hanging=true]");
        for (int i = 0; i <= 10; i++) {
            int left = -1 + i, right = 19 - i, y = 80 + i;
            if (left < right) {
                fill(left, y, -2, left, y, 16, "dark_prismarine_stairs[facing=east]");
                fill(right, y, -2, right, y, 16, "dark_prismarine_stairs[facing=west]");
                if (left + 1 <= right - 1) {
                    fill(left + 1, y, 0, right - 1, y, 0, "calcite");
                    fill(left + 1, y, 14, right - 1, y, 14, "calcite");
                }
                block(left, y, -2, "deepslate_tile_stairs[facing=east]");
                block(right, y, -2, "deepslate_tile_stairs[facing=west]");
                block(left, y, 16, "deepslate_tile_stairs[facing=east]");
                block(right, y, 16, "deepslate_tile_stairs[facing=west]");
            } else fill(9, y, -2, 9, y, 16, "deepslate_tile_slab");
        }
        fill(9, 80, 14, 9, 88, 14, "stripped_spruce_log");
        fill(6, 82, 14, 8, 84, 14, "glass");
        fill(10, 82, 14, 12, 84, 14, "glass");
        fill(5, 81, 15, 13, 81, 15, "spruce_slab[type=top]");
        fill(14, 78, 3, 15, 87, 4, "bricks");
        for (int x : new int[]{14, 15}) {
            block(x, 88, 3, "campfire[lit=true]");
            block(x, 89, 3, "iron_bars");
        }
        // Hidden material runs make isolation genuinely useful, with no staged overlay geometry.
        fill(2, 65, 2, 16, 65, 2, "gold_block");
        fill(2, 65, 12, 16, 65, 12, "cut_copper");
        fill(2, 65, 3, 2, 65, 11, "cut_copper");
    }

    private void window(int x, int y, int z, boolean front) {
        fill(x - 1, y, z, x + 1, y + 2, z, "glass");
        int face = front ? z + 1 : z - 1;
        String dir = front ? "south" : "north";
        block(x - 2, y + 1, face, "spruce_trapdoor[facing=" + dir + ",open=true]");
        block(x + 2, y + 1, face, "spruce_trapdoor[facing=" + dir + ",open=true]");
        fill(x - 1, y - 1, face, x + 1, y - 1, face, "spruce_slab[type=top]");
        if (front && y == 75) {
            for (int bx = x - 1; bx <= x + 1; bx++) block(bx, y, face, "potted_cornflower");
        }
    }

    private void tower() {
        fill(-8, 65, 2, -2, 77, 8, "stone_bricks");
        fill(-7, 67, 3, -3, 77, 7, "air");
        fill(-8, 78, 2, -2, 87, 8, "calcite");
        fill(-7, 79, 3, -3, 87, 7, "air");
        for (int x : new int[]{-8, -2}) for (int z : new int[]{2, 8})
            fill(x, 67, z, x, 88, z, "stripped_spruce_log");
        for (int y : new int[]{77, 83, 88}) {
            fill(-8, y, 2, -2, y, 2, "spruce_planks");
            fill(-8, y, 8, -2, y, 8, "spruce_planks");
            fill(-8, y, 2, -8, y, 8, "spruce_planks");
            fill(-2, y, 2, -2, y, 8, "spruce_planks");
        }
        for (int y : new int[]{69, 79, 85}) {
            fill(-6, y, 8, -4, y + 1, 8, "glass");
            fill(-8, y, 4, -8, y + 1, 6, "glass");
        }
        for (int i = 0; i < 5; i++) {
            int lo = -9 + i, hi = -1 - i, near = 1 + i, far = 9 - i, y = 89 + i;
            if (lo <= hi && near <= far) {
                fill(lo, y, near, hi, y, far, "oxidized_cut_copper");
                fill(lo, y, far, hi, y, far, "oxidized_cut_copper_stairs[facing=north]");
                fill(lo, y, near, hi, y, near, "oxidized_cut_copper_stairs[facing=south]");
                fill(lo, y, near, lo, y, far, "oxidized_cut_copper_stairs[facing=east]");
                fill(hi, y, near, hi, y, far, "oxidized_cut_copper_stairs[facing=west]");
            }
        }
        block(-5, 94, 5, "lightning_rod");
        fill(-2, 67, 4, 0, 70, 6, "air");
    }

    private void garden() {
        Random random = new Random(1800);
        for (int z = 16; z <= 48; z++) {
            int cx = 9 - Math.max(0, z - 28) / 3;
            for (int x = cx - 2; x <= cx + 2; x++)
                block(x, 64, z, random.nextInt(4) == 0 ? "coarse_dirt" : "gravel");
        }
        for (int[] bed : new int[][]{{-6,18},{18,22},{-14,12},{23,16},{-9,33}}) {
            for (int z = bed[1] - 2; z <= bed[1] + 2; z++) for (int x = bed[0] - 2; x <= bed[0] + 2; x++) {
                if (Math.abs(x - bed[0]) + Math.abs(z - bed[1]) > 3) continue;
                block(x, 64, z, "moss_block");
                block(x, 65, z, random.nextInt(3) == 0 ? "cornflower" : random.nextBoolean() ? "oxeye_daisy" : "short_grass");
            }
        }
        fill(3, 65, 19, 15, 65, 20, "stone_brick_slab");
        fill(6, 65, 21, 12, 65, 21, "stone_brick_stairs[facing=north]");
        for (int[] light : new int[][]{{-1,25},{16,25},{1,37},{-11,21}}) {
            fill(light[0], 65, light[1], light[0], 67, light[1], "spruce_fence");
            block(light[0], 68, light[1], "lantern");
        }
        for (int x = 29; x <= 40; x++) fill(x, 65, 19, x, 65, 21, "spruce_slab[type=bottom]");
        for (int x : new int[]{30, 39}) fill(x, 64, 19, x, 67, 19, "spruce_log");
        for (int x : new int[]{30, 39}) block(x, 68, 19, "lantern");
        for (int x = -1; x <= 1; x++) for (int z = 18; z <= 19; z++)
            block(x, 65, z, "barrel[facing=up]");
        block(1, 66, 18, "potted_fern");
        block(-1, 66, 18, "lantern");
    }

    private void interior() {
        fill(2, 67, 1, 5, 70, 1, "bookshelf");
        fill(13, 67, 1, 16, 70, 1, "bookshelf");
        for (int x = 7; x <= 11; x++) block(x, 67, 3, "cartography_table");
        block(6, 67, 3, "crafting_table");
        block(12, 67, 3, "smithing_table");
        fill(3, 67, 8, 5, 67, 9, "spruce_slab[type=top]");
        block(4, 68, 9, "lantern");
        block(3, 68, 8, "potted_fern");
        fill(13, 67, 11, 16, 67, 12, "barrel[facing=north]");
        block(16, 68, 12, "lantern");
        block(14, 67, 2, "furnace[facing=south,lit=true]");
        block(15, 67, 2, "blast_furnace[facing=south,lit=true]");
        fill(7, 67, 7, 11, 67, 10, "blue_carpet");
        fill(2, 74, 1, 6, 76, 1, "bookshelf");
        fill(12, 74, 1, 16, 76, 1, "bookshelf");
        block(9, 74, 4, "lectern[facing=south]");
        block(8, 74, 4, "lantern");
        for (int y : new int[]{71, 78}) for (int x : new int[]{4, 14})
            block(x, y, 6, "lantern[hanging=true]");
        fill(16, 67, 5, 16, 78, 5, "ladder[facing=west]");
        block(16, 73, 5, "air");
        fill(-6, 67, 3, -4, 67, 5, "water");
    }

    private void block(int x, int y, int z, String state) {
        commands.add("setblock " + x + " " + y + " " + z + " " + state);
    }
    private void fill(int ax, int ay, int az, int bx, int by, int bz, String state) {
        if (ax > bx || ay > by || az > bz) throw new IllegalArgumentException("Inverted filming-set bounds");
        if ((long) (bx - ax + 1) * (by - ay + 1) * (bz - az + 1) > 32768)
            throw new IllegalArgumentException("Oversized vanilla fill");
        commands.add("fill " + ax + " " + ay + " " + az + " " + bx + " " + by + " " + bz + " " + state);
    }
    private static double sq(double value) { return value * value; }
    private static long key(int x, int z) { return ((long) x << 32) | (z & 0xffffffffL); }
}
