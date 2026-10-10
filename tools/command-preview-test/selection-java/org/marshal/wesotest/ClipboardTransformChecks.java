package org.marshal.wesotest;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.command.ClipboardCommands;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.transform.BlockTransformExtent;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.math.transform.Transform;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import org.marshal.weaddon_lightposition.runtime.mc26_2.client.preview.CommandPreviewController;
import org.marshal.weaddon_lightposition.runtime.mc26_2.preview.ClipboardTransformDraft;

/** Reference transforms are computed by WorldEdit's commands in a detached session. */
public final class ClipboardTransformChecks {
    private final Minecraft client;
    private final Object overlay, source;
    private final Consumer<String> screenshot;
    private final SelectionEditingChecks.Dispatch dispatch;
    private final CommandPreviewController commands = CommandPreviewController.instance();
    private ClipboardHolder holder;
    private Transform base;
    private Object sourceCache;

    private ClipboardTransformChecks(Minecraft client, Object overlay, Object source, Consumer<String> screenshot,
                                     SelectionEditingChecks.Dispatch dispatch) {
        this.client = client; this.overlay = overlay; this.source = source; this.screenshot = screenshot; this.dispatch = dispatch;
    }
    public static void run(Minecraft client, Object overlay, Object source, Consumer<String> screenshot,
                           SelectionEditingChecks.Dispatch dispatch) {
        new ClipboardTransformChecks(client, overlay, source, screenshot, dispatch).run();
    }
    private void run() {
        ClipboardHolder original = server(() -> session().getClipboard());
        boolean previewWasEnabled = onClient(() -> (boolean) call(overlay, "clipboardPreviewEnabled"));
        boolean commandsWereEnabled = onClient(() -> commands.options().enabled());
        List<?> history = server(this::history);
        Map<BlockVector3, String> worldBefore = server(this::worldBlocks);
        try {
            server(() -> { createFixture(); return null; });
            onClient(() -> { commands.setEnabled(true); call(field(overlay, "clipboardPreviewController"), "clear");
                call(overlay, "setClipboardPreviewEnabled", true); client.gui.setScreen(null); return null; });
            await(() -> !(boolean) call(snapshot(), "scanning") && !blocks().isEmpty(), "initial clipboard");
            sourceCache = onClient(() -> field(field(source, "serverState"), "sourceBlocks"));
            server(() -> { checkBuildBudget(); return null; });
            Object baselineBlocks = onClient(this::blocks);
            for (String command : List.of("//rotate 90", "//rotate -90", "//rotate 0 90", "//rotate 0 0 90",
                    "//rotate 90 180 270", "//rotate 45", "//rotate 0.000001", "//flip north", "//flip south", "//flip east", "//flip west",
                    "//flip up", "//flip down", "//mirror n", "//flip", "//flip left", "//flip right", "//flip back")) {
                if (List.of("//flip", "//flip left", "//flip right", "//flip back").contains(command)) setLook(0, 0);
                draft(command);
                await(() -> reason().equals("clipboard_ready"), command);
                Map<BlockVector3, Object> expected = server(() -> reference(command));
                check(onClient(this::actual).equals(expected), "Geometry/state differs from WorldEdit: " + command);
                check(onClient(() -> field(field(source, "serverState"), "sourceBlocks")) == sourceCache, "Transform rescanned source: " + command);
                check(server(() -> holder.getTransform() == base), "Draft modified the real transform: " + command);
                check(onClient(() -> (boolean) call(call(source, "transformStatus"), "approximate")) == ClipboardTransformDraft.parse(command).approximate(),
                        "Interpolation warning differs from WorldEdit: " + command);
                onClient(() -> { check(!commands.ownsPreviewChannel() && commands.snapshot().cells().isEmpty(), "Transform claimed terrain preview channel"); return null; });
                if (command.equals("//rotate 90 180 270") || command.equals("//mirror n")) {
                    frameClipboard();
                    dispatch.pause(200); screenshot.accept(command.startsWith("//rotate") ? "clipboard-draft-rotate" : "clipboard-draft-mirror");
                }
            }
            draft("//flip");
            for (float[] look : List.of(new float[]{0, 0}, new float[]{90, 0}, new float[]{0, -80}, new float[]{0, 80})) {
                setLook(look[0], look[1]);
                Map<BlockVector3, Object> expected = server(() -> reference("//flip"));
                await(() -> reason().equals("clipboard_ready") && actual().equals(expected), "look-relative reflection");
                check(onClient(() -> field(field(source, "serverState"), "sourceBlocks")) == sourceCache, "Camera change rescanned source");
            }
            setLook(45, 0);
            check(server(() -> {
                try { reference("//flip"); return false; }
                catch (com.sk89q.worldedit.UnknownDirectionException expected) { return true; }
            }), "WorldEdit reference unexpectedly accepted diagonal look");
            await(() -> reason().equals("clipboard_direction"), "diagonal look rejected by WorldEdit");
            check(onClient(this::blocks) == baselineBlocks, "Rejected diagonal look left a transformed draft");
            setLook(0, 0);
            draft("//rotate 90"); await(() -> reason().equals("clipboard_ready"), "cache seed");
            Object rotated = onClient(this::blocks);
            onClient(() -> { commands.edited("//rotate 90.0 0 -0"); return null; });
            dispatch.pause(300);
            check(rotated == onClient(this::blocks), "Equivalent angles rebuilt the geometry");
            for (String invalid : List.of("//rotate", "//rotate -", "//rotate NaN", "//rotate 90 0 0 1", "//flip nonsense", "//flip north east")) {
                draft(invalid);
                await(() -> !reason().equals("clipboard_updating"), invalid);
                check(onClient(this::blocks) == baselineBlocks, "Invalid draft retained transformed geometry: " + invalid);
                check(!reason().equals("clipboard_ready"), "Invalid draft reported ready");
            }
            draft("//rotate 90"); await(() -> reason().equals("clipboard_ready"), "Escape setup");
            onClient(() -> { client.gui.screen().keyPressed(new KeyEvent(256, 0, 0));
                check(blocks() == baselineBlocks, "Escape did not immediately restore baseline"); return null; });
            for (String excluded : List.of("//cut", "//cut stone", "//paste", "//paste -a")) {
                draft(excluded); dispatch.pause(150);
                onClient(() -> { check(!commands.ownsPreviewChannel() && reason().equals("waiting"), "Cut/paste gained a live preview");
                    check(blocks() == baselineBlocks, "Cut/paste changed clipboard preview"); return null; });
            }
            onClient(() -> {
                for (String value : List.of("//rotate 90", "//flip north", "//rotate 180", "//rotate 270")) commands.edited(value);
                return null;
            });
            await(() -> reason().equals("clipboard_ready"), "rapid typing");
            check(onClient(this::actual).equals(server(() -> reference("//rotate 270"))), "Published an obsolete draft");
            Object cancellationCache = onClient(() -> field(field(source, "serverState"), "sourceBlocks"));
            onClient(() -> { commands.setEnabled(false); check(blocks() == baselineBlocks, "F8 disable left transformed clipboard"); return null; });
            check(cancellationCache == sourceCache, "Cancellation discarded the scan cache");
            onClient(() -> { commands.setEnabled(true); return null; });
            await(() -> reason().equals("clipboard_ready"), "F8 restore");
            check(history.equals(server(this::history)), "Clipboard draft modified undo history");
            check(worldBefore.equals(server(this::worldBlocks)), "Clipboard draft modified the world");
            draft("//rotate 90"); await(() -> reason().equals("clipboard_ready"), "Enter setup");
            Map<BlockVector3, Object> expected = server(() -> reference("//rotate 90"));
            onClient(() -> { client.gui.screen().keyPressed(new KeyEvent(257, 0, 0)); return null; });
            await(() -> actual().equals(expected), "Enter executes one real rotation");
            check(server(() -> holder.getTransform() != base), "Enter failed to execute WorldEdit normally");
            check(history.equals(server(this::history)), "Rotate unexpectedly changed undo history");
            server(() -> { session().setClipboard(null); return null; });
            draft("//mirror north"); await(() -> reason().equals("clipboard_empty"), "empty clipboard");
            check(onClient(this::blocks).isEmpty(), "Clearing clipboard left old models");
            onClient(() -> { call(overlay, "setClipboardPreviewEnabled", false); return null; });
            check(onClient(this::blocks).isEmpty(), "Disabled preview retained models");
        } finally {
            server(() -> { session().setClipboard(original); return null; });
            onClient(() -> { client.gui.setScreen(null); commands.setEnabled(commandsWereEnabled);
                call(overlay, "setClipboardPreviewEnabled", previewWasEnabled); return null; });
            if (previewWasEnabled) await(() -> !blocks().isEmpty(), "restore fixture clipboard");
        }
        System.out.println("CLIPBOARD TRANSFORM VERIFY PASS: WorldEdit reference Y/X/Z order, stacked transforms, oriented models, flip/mirror and look directions, diagonal rejection, cache, rapid typing, Escape/F8, Enter once, empty/disabled, cut/paste excluded, unchanged world/history");
    }
    private void createFixture() throws Exception {
        BlockVector3 min = BlockVector3.at(0, 66, 0);
        var clipboard = new BlockArrayClipboard(new CuboidRegion(min, min.add(2, 2, 3)));
        clipboard.setOrigin(min.add(-6, -2, -8));
        ParserContext context = new ParserContext(); context.setActor(actor()); context.setWorld(actor().getWorld());
        BlockVector3[] points = {min, min.add(2, 0, 0), min.add(0, 2, 3), min.add(1, 1, 2)};
        String[] types = {"gold_block", "oak_stairs[facing=east,half=bottom,shape=inner_left]", "oak_trapdoor[facing=north,open=true]", "red_wool"};
        for (int i = 0; i < points.length; i++) clipboard.setBlock(points[i], WorldEdit.getInstance().getBlockFactory().parseFromInput(types[i], context));
        holder = new ClipboardHolder(clipboard);
        base = new AffineTransform().rotateY(-90).scale(1, 1, -1);
        holder.setTransform(base); session().setClipboard(holder);
    }
    private void frameClipboard() {
        double[] angles = onClient(() -> {
            Object s = snapshot(), bounds = call(s, "bounds");
            var placement = (net.minecraft.core.BlockPos) call(s, "placement");
            double x = placement.getX() + ((int) call(bounds, "minX") + (int) call(bounds, "maxX") + 1) / 2.0 - client.player.getX();
            double y = placement.getY() + ((int) call(bounds, "minY") + (int) call(bounds, "maxY") + 1) / 2.0 - client.player.getEyeY();
            double z = placement.getZ() + ((int) call(bounds, "minZ") + (int) call(bounds, "maxZ") + 1) / 2.0 - client.player.getZ();
            float yaw = (float) Math.toDegrees(Math.atan2(-x, z));
            float pitch = (float) -Math.toDegrees(Math.atan2(y, Math.hypot(x, z)));
            client.player.setYRot(yaw); client.player.setXRot(pitch);
            return new double[]{yaw, pitch};
        });
        server(() -> { var player = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
            player.setYRot((float) angles[0]); player.setXRot((float) angles[1]); return null; });
    }
    private void setLook(float yaw, float pitch) {
        onClient(() -> { client.player.setYRot(yaw); client.player.setXRot(pitch); return null; });
        server(() -> { var player = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
            player.setYRot(yaw); player.setXRot(pitch); return null; });
    }
    private void checkBuildBudget() throws Exception {
        var type = Class.forName(source.getClass().getName() + "$BuildJob");
        var constructor = type.getDeclaredConstructor(String.class, List.class, Transform.class); constructor.setAccessible(true);
        var advance = type.getDeclaredMethod("advance", long.class, BooleanSupplier.class); advance.setAccessible(true);
        var input = new java.util.ArrayList<Object>();
        List<?> captured = (List<?>) sourceCache;
        for (int i = 0; i < 10_000; i++) input.add(captured.get(i % captured.size()));
        var build = constructor.newInstance("budget-check", input, base);
        check(advance.invoke(build, Long.MAX_VALUE, (BooleanSupplier) () -> false) == null, "Build ignored conversion slice cap");
        check((int) field(build, "index") == 4096, "Build slice did not stop at 4096 steps");
        check(advance.invoke(build, Long.MAX_VALUE, (BooleanSupplier) () -> true) == null && (int) field(build, "index") == 4096,
                "Obsolete build continued doing work");
        while (advance.invoke(build, Long.MAX_VALUE, (BooleanSupplier) () -> false) == null) { }
    }
    private Map<BlockVector3, Object> reference(String command) throws Exception {
        var draft = ClipboardTransformDraft.parse(command);
        var detached = new LocalSession();
        var reference = new ClipboardHolder(holder.getClipboard()); reference.setTransform(base); detached.setClipboard(reference);
        var api = new ClipboardCommands();
        if (draft.kind() == ClipboardTransformDraft.Kind.ROTATE) api.rotate(actor(), detached, draft.y(), draft.x(), draft.z());
        else api.flip(actor(), detached, WorldEdit.getInstance().getDirection(actor(), draft.direction()));
        Map<BlockVector3, Object> expected = new HashMap<>();
        var clipboard = reference.getClipboard();
        for (BlockVector3 position : clipboard.getRegion()) {
            var state = clipboard.getBlock(position); if (state.getBlockType().getMaterial().isAir()) continue;
            var transformed = BlockTransformExtent.transform(state, reference.getTransform()).toImmutableState();
            expected.put(reference.getTransform().apply(position.subtract(clipboard.getOrigin()).toVector3()).toBlockPoint(),
                    FabricAdapter.get().toNativeBlockState(transformed));
        }
        return expected;
    }
    private Map<BlockVector3, Object> actual() {
        Map<BlockVector3, Object> actual = new HashMap<>();
        for (Object block : blocks()) actual.put(BlockVector3.at((int) call(block, "x"), (int) call(block, "y"), (int) call(block, "z")), call(block, "state"));
        return actual;
    }
    private Map<BlockVector3, String> worldBlocks() {
        Map<BlockVector3, String> values = new HashMap<>();
        for (var p : new CuboidRegion(BlockVector3.at(-8, 64, -8), BlockVector3.at(5, 72, 5))) values.put(p, actor().getWorld().getBlock(p).toString());
        return values;
    }
    private Player actor() { return FabricAdapter.get().fromNativePlayer(client.getSingleplayerServer().getPlayerList().getPlayers().getFirst()); }
    private LocalSession session() { return WorldEdit.getInstance().getSessionManager().get(actor()); }
    private List<?> history() { return List.copyOf((java.util.Collection<?>) field(session(), "history")); }
    private Object snapshot() { return call(source, "snapshot"); }
    private List<?> blocks() { return (List<?>) call(snapshot(), "blocks"); }
    private String reason() { return (String) call(call(source, "transformStatus"), "reason"); }
    private void draft(String value) { onClient(() -> { client.gui.setScreen(new ChatScreen(value, false)); return null; }); }
    private <T> T onClient(Callable<T> task) { return dispatch.client(task); }
    private <T> T server(Callable<T> task) { return dispatch.server(task); }
    private void await(BooleanSupplier test, String stage) {
        long deadline = System.nanoTime() + 15_000_000_000L;
        while (!onClient(test::getAsBoolean)) {
            check(System.nanoTime() < deadline, "Timeout: " + stage + " status=" + onClient(this::reason)); dispatch.pause(25);
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static Object field(Object instance, String name) {
        try { var f = instance.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(instance); }
        catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }
    private static Object call(Object instance, String name, Object... args) {
        for (Method method : instance.getClass().getMethods()) if (method.getName().equals(name) && method.getParameterCount() == args.length) {
            try { return method.invoke(instance, args); } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
        }
        throw new AssertionError("Missing " + name);
    }
}
