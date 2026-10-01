package org.marshal.weaddon_lightposition.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import org.enginehub.worldeditcui.protocol.CUIPacket;
import org.enginehub.worldeditcui.protocol.CUIPacketHandler;
import org.lwjgl.glfw.GLFW;
import org.marshal.weaddon_lightposition.client.clipboard.ClipboardPreviewController;
import org.marshal.weaddon_lightposition.client.overlay.DiagonalMode;
import org.marshal.weaddon_lightposition.client.overlay.OverlayColorProfile;
import org.marshal.weaddon_lightposition.client.overlay.OverlayColorSlot;
import org.marshal.weaddon_lightposition.client.overlay.OverlayRenderStyle;
import org.marshal.weaddon_lightposition.client.overlay.RenderMode;
import org.marshal.weaddon_lightposition.client.overlay.RulerTrackingMode;
import org.marshal.weaddon_lightposition.client.overlay.SelectionBlockAnalyzer;
import org.marshal.weaddon_lightposition.client.overlay.SelectionManager;
import org.marshal.weaddon_lightposition.client.overlay.SelectionRegion;
import org.marshal.weaddon_lightposition.client.screen.OverlaySettingsScreen;
import org.marshal.weaddon_lightposition.client.screen.SelectionBlocksScreen;
import org.marshal.weaddon_lightposition.client.screen.UiStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WorldEditPointOverlayClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("worldedit_selection_overlay/cui");

    private enum SourceMode {
        AUTO,
        CUI_ONLY
    }

    private static final SourceMode SOURCE_MODE = SourceMode.AUTO;
    private static final int DELAYED_HELO_TICKS = 10;
    private static final int HELO_MIN_INTERVAL_MS = 500;
    private static final int HELO_RETRY_INITIAL_DELAY_TICKS = 40;
    private static final int HELO_RETRY_INTERVAL_TICKS = 120;
    private static final int HELO_RETRY_MAX_ATTEMPTS = 25;
    private static final int CUI_COMMAND_INITIAL_DELAY_TICKS = 20;
    private static final int CUI_COMMAND_RETRY_INTERVAL_TICKS = 80;
    private static final int CUI_COMMAND_MAX_ATTEMPTS = 4;
    private static final Pattern COORDS_PATTERN = Pattern.compile("\\((-?\\d+)\\s*,\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\)");
    private static final String RUS_PRIMARY = "первая точка";
    private static final String RUS_SECONDARY = "вторая точка";

    private static final String SETTINGS_FILE_NAME = "client.properties";
    private static final String OVERLAY_ENABLED_KEY = "overlayEnabled";
    private static final String SELECTION_GRID_ENABLED_KEY = "selectionGridEnabled";
    private static final String CLIPBOARD_PREVIEW_ENABLED_KEY = "clipboardPreviewEnabled";
    private static final String RULER_ENABLED_KEY = "rulerEnabled";
    private static final String RULER_AXIS_X_ENABLED_KEY = "rulerAxisXEnabled";
    private static final String RULER_AXIS_Y_ENABLED_KEY = "rulerAxisYEnabled";
    private static final String RULER_AXIS_Z_ENABLED_KEY = "rulerAxisZEnabled";
    private static final String RENDER_MODE_KEY = "renderMode";
    private static final String DIAGONAL_MODE_KEY = "diagonalMode";
    private static final String RULER_TRACKING_MODE_KEY = "rulerTrackingMode";
    private static final String BLOCK_FILTER_EXCLUDED_KEY = "excludedBlockFilters";
    private static final String ISOLATED_BLOCKS_KEY = "isolatedBlockIds";
    private static final String LINE_WIDTH_SCALE_KEY = "lineWidthScale";
    private static final String UI_STYLE_KEY = "uiStyle";
    private static final String REDUCED_MOTION_KEY = "reducedMotion";
    private static final String UI_BLUR_KEY = "uiBlur";
    private static final String COLOR_KEY_PREFIX = "color.";
    private static final String LINE_WIDTH_KEY_PREFIX = "lineWidth.";
    private static final Identifier KEY_CATEGORY_ID = Identifier.of("worldedit_selection_overlay", "controls");
    private static final KeyBinding.Category KEY_CATEGORY = KeyBinding.Category.create(KEY_CATEGORY_ID);

    private final SelectionManager selectionManager = new SelectionManager();
    private final SelectionBlockAnalyzer selectionBlockAnalyzer = new SelectionBlockAnalyzer();
    private final ClipboardPreviewController clipboardPreviewController = new ClipboardPreviewController();
    private final HashSet<String> excludedBlockFilters = new HashSet<>();
    private final HashSet<String> isolatedBlockIds = new HashSet<>();
    private final OverlayColorProfile colorProfile = OverlayColorProfile.defaults();

    private KeyBinding openSettingsKey;
    private KeyBinding openBlockListKey;
    private KeyBinding deselectKey;
    private Path settingsFile;
    private boolean overlayEnabled = true;
    private boolean selectionGridEnabled = true;
    private boolean clipboardPreviewEnabled = false;
    private boolean rulerEnabled = false;
    private boolean rulerAxisXEnabled = true;
    private boolean rulerAxisYEnabled = true;
    private boolean rulerAxisZEnabled = true;
    private RenderMode renderMode = RenderMode.BLOCKY;
    private DiagonalMode diagonalMode = DiagonalMode.NONE;
    private RulerTrackingMode rulerTrackingMode = RulerTrackingMode.FOLLOW_CAMERA;
    private float lineWidthScale = 1.0f;
    private UiStyle uiStyle = UiStyle.GLASS;
    private boolean reducedMotion;
    private boolean uiBlur;
    private ClientWorld lastWorld;
    private int delayedHeloTicks;
    private int heloRetryAttempts;
    private int nextHeloRetryTick = HELO_RETRY_INITIAL_DELAY_TICKS;
    private int receivedCuiPackets;
    private int receivedSelectionPackets;
    private long lastHeloAtMs;
    private int nextCuiCommandTick = CUI_COMMAND_INITIAL_DELAY_TICKS;
    private int cuiCommandAttempts;
    private SelectionRegion isolatedBlocksRegion;
    private long isolatedBlocksRegionVersion = Long.MIN_VALUE;
    private boolean warnedAboutMissingCui;
    private boolean loggedHeloRetryStop;
    private boolean loggedCuiActivation;

    public void initialize() {
        initSettings();
        clipboardPreviewController.initialize();
        clipboardPreviewController.setEnabled(clipboardPreviewEnabled);
        BlockIsolationController.setSelectedBlockIds(isolatedBlockIds);
        openSettingsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.worldedit_selection_overlay.open_settings",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_O,
                KEY_CATEGORY
        ));
        openBlockListKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.worldedit_selection_overlay.open_block_browser",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                KEY_CATEGORY
        ));
        deselectKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.worldedit_selection_overlay.deselect",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_N,
                KEY_CATEGORY
        ));

        CUIPacketHandler.instance().registerClientboundHandler(this::onCuiPacket);
        ClientPlayConnectionEvents.JOIN.register(this::onJoin);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> resetState());
        ClientTickEvents.END_CLIENT_TICK.register(this::onEndClientTick);
        WorldRenderEvents.AFTER_ENTITIES.register(this::onRenderClipboardPreview);
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(this::onRenderWorld);
        ClientReceiveMessageEvents.GAME.register(this::onReceiveGameMessage);
    }

    private void onJoin(ClientPlayNetworkHandler handler, PacketSender sender, MinecraftClient client) {
        selectionManager.clear();
        BlockIsolationController.clearRegion(client);
        resetCuiHandshakeForWorld();
        scheduleCuiCommand(client);
        sendHelo(true);
        delayedHeloTicks = DELAYED_HELO_TICKS;
    }

    private void onEndClientTick(MinecraftClient client) {
        handleOpenSettingsKey(client);
        handleOpenBlockListKey(client);
        handleDeselectKey(client);
        clipboardPreviewController.setEnabled(clipboardPreviewEnabled);
        clipboardPreviewController.tick(client);

        if (client.world != lastWorld) {
            lastWorld = client.world;
            selectionManager.clear();
            clipboardPreviewController.clear();
            BlockIsolationController.clearRegion(client);

            if (client.getNetworkHandler() != null) {
                resetCuiHandshakeForWorld();
                scheduleCuiCommand(client);
                sendHelo(false);
                delayedHeloTicks = DELAYED_HELO_TICKS;
            }
        }

        if (delayedHeloTicks > 0 && client.getNetworkHandler() != null) {
            delayedHeloTicks--;
            if (delayedHeloTicks == 0) {
                sendHelo(false);
            }
        }
        maybeRetryHelo(client);

        if (SOURCE_MODE != SourceMode.CUI_ONLY && shouldRequestResync(client)) {
            requestCuiHandshakeCommand(client);
        }

        if (!warnedAboutMissingCui && client.player != null && client.player.age % 200 == 0 && receivedCuiPackets == 0) {
            warnedAboutMissingCui = true;
            LOGGER.info("No WorldEdit CUI packets received yet; using message fallback if available.");
        }

        SelectionRegion region = selectionManager.primaryRegionForAnalysis();
        clearIsolatedBlocksIfSelectionChanged(client, region);
        BlockIsolationController.updateRegion(client, region, false);
    }

    private void onRenderWorld(WorldRenderContext context) {
        boolean isolationActive = BlockIsolationController.active();
        boolean showSelectionOutline = isolationActive || overlayEnabled;
        boolean showDiagonals = !isolationActive && overlayEnabled && selectionGridEnabled && diagonalMode != DiagonalMode.NONE;
        boolean clipboardPreviewActive = clipboardPreviewController.active();
        if (!showSelectionOutline && !rulerEnabled && !showDiagonals && !clipboardPreviewActive) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.gameRenderer == null) {
            return;
        }

        if (clipboardPreviewActive) {
            try (GizmoDrawing.CollectorScope ignored = client.worldRenderer.startDrawingGizmos()) {
                clipboardPreviewController.renderBounds();
            }
        }

        if (!showSelectionOutline && !rulerEnabled && !showDiagonals) {
            return;
        }

        List<SelectionRegion> regions = selectionManager.regionsForRender();
        if (regions.isEmpty()) {
            return;
        }

        try (GizmoDrawing.CollectorScope ignored = client.worldRenderer.startDrawingGizmos()) {
            var camera = client.gameRenderer.getCamera();
            Vec3d cameraPos = camera.getCameraPos();
            Vec3d cameraForward = cameraForwardVector(camera.getYaw(), camera.getPitch());
            Vec3d cameraRight = cameraRightVector(cameraForward, camera.getYaw());
            Vec3d cameraUp = cameraUpVector(cameraForward, cameraRight);
            for (SelectionRegion region : regions) {
                if (isolationActive || (overlayEnabled && !selectionGridEnabled)) {
                    region.renderOutline();
                } else if (overlayEnabled) {
                    region.render(renderMode);
                }
                if (rulerEnabled) {
                    region.renderRuler(
                            cameraPos,
                            cameraRight,
                            cameraUp,
                            rulerTrackingMode,
                            rulerAxisXEnabled,
                            rulerAxisYEnabled,
                            rulerAxisZEnabled
                    );
                }
                if (showDiagonals) {
                    region.renderDiagonals(cameraPos, cameraRight, cameraUp, diagonalMode, rulerTrackingMode);
                }
            }
        }
    }

    private void onRenderClipboardPreview(WorldRenderContext context) {
        clipboardPreviewController.renderBlocks(context);
    }

    private void onCuiPacket(CUIPacket payload, CUIPacketHandler.PacketContext context) {
        context.workExecutor().execute(() -> handleCuiPacket(payload));
    }

    private void handleCuiPacket(CUIPacket payload) {
        receivedCuiPackets++;
        if (receivedCuiPackets == 1) {
            heloRetryAttempts = 0;
            nextHeloRetryTick = Integer.MAX_VALUE;
            loggedHeloRetryStop = false;
        }
        if (!loggedCuiActivation) {
            loggedCuiActivation = true;
            if (SOURCE_MODE == SourceMode.AUTO) {
                LOGGER.info("WorldEdit CUI channel is active; chat fallback is now disabled.");
            }
        }

        boolean multi = payload.multi();
        String eventType = payload.eventType();
        List<String> args = payload.args();
        if (isSelectionPayload(eventType)) {
            receivedSelectionPackets++;
        }

        switch (eventType) {
            case "s" -> selectionManager.onSelectionEvent(multi, args);
            case "p" -> handlePointEvent(multi, args);
            case "p2" -> handlePoint2DEvent(multi, args);
            case "e" -> handleEllipsoidEvent(multi, args);
            case "cyl" -> handleCylinderEvent(multi, args);
            case "mm" -> handleBoundsEvent(multi, args);
            case "poly" -> handlePolygonFaceEvent(multi, args);
            case "grid" -> handleGridEvent(multi, args);
            case "col", "u" -> {
                // Optional style/update events.
            }
            default -> LOGGER.debug("Unhandled WorldEdit CUI event type: {}", eventType);
        }
    }

    private void handlePointEvent(boolean multi, List<String> args) {
        if (args.size() < 4) {
            return;
        }

        SelectionRegion region = selectionManager.targetRegion(multi);
        if (region == null) {
            return;
        }

        Integer pointId = parseInteger(args.get(0));
        if (pointId == null) {
            return;
        }

        Vec3d point;
        if ("~".equals(args.get(1)) && "~".equals(args.get(2)) && "~".equals(args.get(3))) {
            point = raycastPoint(args.size() > 4 ? parseDouble(args.get(4)) : null);
            if (point == null) {
                return;
            }
        } else {
            Double x = parseDouble(args.get(1));
            Double y = parseDouble(args.get(2));
            Double z = parseDouble(args.get(3));
            if (x == null || y == null || z == null) {
                return;
            }
            point = new Vec3d(x, y, z);
        }

        region.applyPoint3D(pointId, point);
    }

    private void handlePoint2DEvent(boolean multi, List<String> args) {
        if (args.size() < 4) {
            return;
        }

        SelectionRegion region = selectionManager.targetRegion(multi);
        if (region == null) {
            return;
        }

        Integer pointId = parseInteger(args.get(0));
        Integer x = parseInteger(args.get(1));
        Integer z = parseInteger(args.get(2));
        if (pointId == null || x == null || z == null) {
            return;
        }

        region.applyPoint2D(pointId, x, z);
    }

    private void handleEllipsoidEvent(boolean multi, List<String> args) {
        if (args.size() < 4) {
            return;
        }

        SelectionRegion region = selectionManager.targetRegion(multi);
        if (region == null) {
            return;
        }

        Integer pointId = parseInteger(args.get(0));
        if (pointId == null) {
            return;
        }

        if (pointId == 0) {
            Integer x = parseInteger(args.get(1));
            Integer y = parseInteger(args.get(2));
            Integer z = parseInteger(args.get(3));
            if (x == null || y == null || z == null) {
                return;
            }
            region.applyEllipsoidCenter(new Vec3d(x, y, z));
        } else if (pointId == 1) {
            Double x = parseDouble(args.get(1));
            Double y = parseDouble(args.get(2));
            Double z = parseDouble(args.get(3));
            if (x == null || y == null || z == null) {
                return;
            }
            region.applyEllipsoidRadii(new Vec3d(x, y, z));
        }
    }

    private void handleCylinderEvent(boolean multi, List<String> args) {
        if (args.size() < 5) {
            return;
        }

        SelectionRegion region = selectionManager.targetRegion(multi);
        if (region == null) {
            return;
        }

        Integer x = parseInteger(args.get(0));
        Integer y = parseInteger(args.get(1));
        Integer z = parseInteger(args.get(2));
        Double radiusX = parseDouble(args.get(3));
        Double radiusZ = parseDouble(args.get(4));
        if (x == null || y == null || z == null || radiusX == null || radiusZ == null) {
            return;
        }

        region.applyCylinderCenter(new Vec3d(x, y, z));
        region.applyCylinderRadii(radiusX, radiusZ);
    }

    private void handleBoundsEvent(boolean multi, List<String> args) {
        if (args.size() < 2) {
            return;
        }

        SelectionRegion region = selectionManager.targetRegion(multi);
        if (region == null) {
            return;
        }

        Integer min = parseInteger(args.get(0));
        Integer max = parseInteger(args.get(1));
        if (min == null || max == null) {
            return;
        }

        region.applyMinMax(min, max);
    }

    private void handlePolygonFaceEvent(boolean multi, List<String> args) {
        if (args.size() < 3) {
            return;
        }

        SelectionRegion region = selectionManager.targetRegion(multi);
        if (region == null) {
            return;
        }

        int[] ids = new int[args.size()];
        for (int i = 0; i < args.size(); i++) {
            Integer value = parseInteger(args.get(i));
            if (value == null) {
                return;
            }
            ids[i] = value;
        }
        region.applyPolygonFace(ids);
    }

    private void handleGridEvent(boolean multi, List<String> args) {
        if (args.isEmpty()) {
            return;
        }

        SelectionRegion region = selectionManager.targetRegion(multi);
        if (region == null) {
            return;
        }

        Double spacing = parseDouble(args.get(0));
        if (spacing == null) {
            return;
        }

        boolean cull = args.size() > 1 && "cull".equalsIgnoreCase(args.get(1));
        region.applyGrid(spacing, cull);
    }

    private Vec3d raycastPoint(Double maxDistance) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return null;
        }

        double distance = maxDistance == null ? 256.0 : Math.min(Math.max(maxDistance, 1.0), 256.0);
        HitResult hit = client.player.raycast(distance, 1.0f, false);
        Vec3d pos = hit.getPos();
        return new Vec3d(Math.floor(pos.getX()), Math.floor(pos.getY()), Math.floor(pos.getZ()));
    }

    private void onReceiveGameMessage(Text message, boolean overlay) {
        if (!shouldUseChatFallback()) {
            return;
        }

        BlockPos point = extractPointFromWorldEditMessage(message);
        if (point == null) {
            return;
        }

        String raw = message.getString().toLowerCase(Locale.ROOT);
        if (isPrimaryPointMessage(message, raw)) {
            selectionManager.setFallbackCuboidPoint(0, point);
            LOGGER.debug("Set WorldEdit pos1 from message fallback: {}", point.toShortString());
        } else if (isSecondaryPointMessage(message, raw)) {
            selectionManager.setFallbackCuboidPoint(1, point);
            LOGGER.debug("Set WorldEdit pos2 from message fallback: {}", point.toShortString());
        }
    }

    private void sendHelo(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastHeloAtMs < HELO_MIN_INTERVAL_MS) {
            return;
        }

        try {
            ClientPlayNetworking.send(new CUIPacket("v", CUIPacket.protocolVersion()));
            lastHeloAtMs = now;
            LOGGER.info("Sent WorldEdit CUI hello packet (protocol {})", CUIPacket.protocolVersion());
        } catch (Exception ex) {
            LOGGER.debug("Could not send WorldEdit CUI hello packet", ex);
        }
    }

    private void maybeRetryHelo(MinecraftClient client) {
        if (client.getNetworkHandler() == null || client.player == null) {
            return;
        }
        if (receivedCuiPackets > 0) {
            return;
        }
        if (client.player.age < nextHeloRetryTick) {
            return;
        }
        if (heloRetryAttempts >= HELO_RETRY_MAX_ATTEMPTS) {
            if (!loggedHeloRetryStop) {
                LOGGER.info("Stopped CUI hello retries after {} attempts without response.", HELO_RETRY_MAX_ATTEMPTS);
                loggedHeloRetryStop = true;
            }
            return;
        }

        sendHelo(false);
        heloRetryAttempts++;
        nextHeloRetryTick = client.player.age + HELO_RETRY_INTERVAL_TICKS;
        if (heloRetryAttempts == 1 || heloRetryAttempts == 5 || heloRetryAttempts == 10) {
            LOGGER.info("Retrying WorldEdit CUI hello ({}/{})", heloRetryAttempts, HELO_RETRY_MAX_ATTEMPTS);
        }
    }

    private void requestCuiHandshakeCommand(MinecraftClient client) {
        try {
            client.getNetworkHandler().sendChatCommand("we cui");
            cuiCommandAttempts++;
            nextCuiCommandTick = client.player.age + CUI_COMMAND_RETRY_INTERVAL_TICKS;
            LOGGER.info("Requested WorldEdit CUI sync via command: we cui ({}/{})", cuiCommandAttempts, CUI_COMMAND_MAX_ATTEMPTS);
        } catch (Exception ex) {
            cuiCommandAttempts++;
            nextCuiCommandTick = client.player.age + CUI_COMMAND_RETRY_INTERVAL_TICKS;
            LOGGER.debug("Could not request WorldEdit CUI sync via command", ex);
        }
    }

    private void resetCuiHandshakeForWorld() {
        heloRetryAttempts = 0;
        nextHeloRetryTick = HELO_RETRY_INITIAL_DELAY_TICKS;
        receivedCuiPackets = 0;
        receivedSelectionPackets = 0;
        nextCuiCommandTick = CUI_COMMAND_INITIAL_DELAY_TICKS;
        cuiCommandAttempts = 0;
        warnedAboutMissingCui = false;
        loggedHeloRetryStop = false;
        loggedCuiActivation = false;
    }

    private void scheduleCuiCommand(MinecraftClient client) {
        if (client.player != null) {
            nextCuiCommandTick = client.player.age + CUI_COMMAND_INITIAL_DELAY_TICKS;
        }
    }

    private void resetState() {
        delayedHeloTicks = 0;
        lastWorld = null;
        resetCuiHandshakeForWorld();
        lastHeloAtMs = 0;
        selectionManager.clear();
        selectionBlockAnalyzer.reset();
        clipboardPreviewController.clear();
        BlockIsolationController.clearRegion(MinecraftClient.getInstance());
        isolatedBlocksRegion = null;
        isolatedBlocksRegionVersion = Long.MIN_VALUE;
    }

    private void handleOpenSettingsKey(MinecraftClient client) {
        if (openSettingsKey == null) {
            return;
        }
        if (client.currentScreen instanceof OverlaySettingsScreen) {
            return;
        }

        while (openSettingsKey.wasPressed()) {
            Screen parent = client.currentScreen;
            client.setScreen(new OverlaySettingsScreen(this, parent));
        }
    }

    private void handleOpenBlockListKey(MinecraftClient client) {
        if (openBlockListKey == null) {
            return;
        }

        while (openBlockListKey.wasPressed()) {
            if (client.player == null) {
                return;
            }

            SelectionRegion region = selectionManager.primaryRegionForAnalysis();
            selectionBlockAnalyzer.refresh(client.world, region);
            client.setScreen(
                    new SelectionBlocksScreen(
                            selectionBlockAnalyzer,
                            selectionManager::primaryRegionForAnalysis,
                            excludedBlockFilters,
                            this::setExcludedBlockFilters,
                            isolatedBlockIds,
                            this::setIsolatedBlockIds
                    )
            );
        }
    }

    private void handleDeselectKey(MinecraftClient client) {
        if (deselectKey == null) {
            return;
        }

        if (client.currentScreen != null) {
            while (deselectKey.wasPressed()) {
                // Drain GUI text-field key presses so they never fire after the screen closes.
            }
            return;
        }

        boolean requested = false;
        while (deselectKey.wasPressed()) {
            requested = true;
        }
        if (requested) {
            requestWorldEditDeselect(client);
        }
    }

    private void requestWorldEditDeselect(MinecraftClient client) {
        if (client == null || client.player == null || client.getNetworkHandler() == null) {
            return;
        }

        try {
            client.getNetworkHandler().sendChatCommand("/deselect");
            client.player.sendMessage(Text.translatable("message.worldedit_selection_overlay.deselect_sent"), true);
            LOGGER.info("Requested WorldEdit deselect via command: //deselect");
        } catch (Exception ex) {
            client.player.sendMessage(Text.translatable("message.worldedit_selection_overlay.deselect_failed"), true);
            LOGGER.debug("Could not request WorldEdit deselect via command", ex);
        }
    }

    private void setExcludedBlockFilters(Set<String> excludedFilters) {
        HashSet<String> normalized = new HashSet<>();
        if (excludedFilters != null) {
            for (String value : excludedFilters) {
                if (value == null) {
                    continue;
                }
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    normalized.add(trimmed);
                }
            }
        }

        if (excludedBlockFilters.equals(normalized)) {
            return;
        }
        excludedBlockFilters.clear();
        excludedBlockFilters.addAll(normalized);
        saveSettings();
    }

    private void setIsolatedBlockIds(Set<String> blockIds) {
        HashSet<String> normalized = new HashSet<>();
        if (blockIds != null) {
            for (String value : blockIds) {
                if (value == null) {
                    continue;
                }
                String trimmed = value.trim();
                if (!trimmed.isEmpty() && !isAirBlockId(trimmed) && Identifier.tryParse(trimmed) != null) {
                    normalized.add(trimmed);
                }
            }
        }

        if (isolatedBlockIds.equals(normalized)) {
            return;
        }
        isolatedBlockIds.clear();
        isolatedBlockIds.addAll(normalized);
        saveSettings();

        MinecraftClient client = MinecraftClient.getInstance();
        updateIsolatedBlocksRegionSnapshot(selectionManager.primaryRegionForAnalysis());
        BlockIsolationController.setSelectedBlockIds(isolatedBlockIds);
        BlockIsolationController.updateRegion(client, selectionManager.primaryRegionForAnalysis(), true);
    }

    private void clearIsolatedBlocksIfSelectionChanged(MinecraftClient client, SelectionRegion region) {
        long regionVersion = region == null ? Long.MIN_VALUE : region.version();
        if (isolatedBlockIds.isEmpty()) {
            isolatedBlocksRegion = region;
            isolatedBlocksRegionVersion = regionVersion;
            return;
        }

        if (region == isolatedBlocksRegion && regionVersion == isolatedBlocksRegionVersion) {
            return;
        }

        isolatedBlockIds.clear();
        saveSettings();
        updateIsolatedBlocksRegionSnapshot(region);
        BlockIsolationController.setSelectedBlockIds(Set.of());
        BlockIsolationController.updateRegion(client, region, true);
    }

    private void updateIsolatedBlocksRegionSnapshot(SelectionRegion region) {
        isolatedBlocksRegion = region;
        isolatedBlocksRegionVersion = region == null ? Long.MIN_VALUE : region.version();
    }

    public boolean overlayEnabled() {
        return overlayEnabled;
    }

    public UiStyle uiStyle() {
        return uiStyle;
    }

    public void setUiStyle(UiStyle style) {
        uiStyle = style;
        UiStyle.setCurrent(style);
        saveSettings();
    }

    public boolean uiBlur() { return uiBlur; }

    public void setUiBlur(boolean value) {
        uiBlur = value;
        UiStyle.setBlur(value);
        saveSettings();
    }

    public boolean reducedMotion() {
        return reducedMotion;
    }

    public void setReducedMotion(boolean reducedMotion) {
        this.reducedMotion = reducedMotion;
        UiStyle.setReducedMotion(reducedMotion);
        saveSettings();
    }

    public boolean selectionGridEnabled() {
        return selectionGridEnabled;
    }

    public boolean clipboardPreviewEnabled() {
        return clipboardPreviewEnabled;
    }

    public boolean clipboardPreviewAvailable() {
        return clipboardPreviewController.available();
    }

    public boolean rulerEnabled() {
        return rulerEnabled;
    }

    public RenderMode renderMode() {
        return renderMode;
    }

    public DiagonalMode diagonalMode() {
        return diagonalMode;
    }

    public RulerTrackingMode rulerTrackingMode() {
        return rulerTrackingMode;
    }

    public boolean rulerAxisXEnabled() {
        return rulerAxisXEnabled;
    }

    public boolean rulerAxisYEnabled() {
        return rulerAxisYEnabled;
    }

    public boolean rulerAxisZEnabled() {
        return rulerAxisZEnabled;
    }

    public int overlayColor(OverlayColorSlot slot) {
        return colorProfile.color(slot);
    }

    public float overlayLineWidthScale(OverlayColorSlot slot) {
        return colorProfile.lineWidthScale(slot);
    }

    public boolean allOverlayLineWidthsDefault() {
        return colorProfile.allLineWidthScalesDefault();
    }

    public boolean allOverlayLineWidthsSame() {
        return colorProfile.allLineWidthScalesEqual();
    }

    public float commonOverlayLineWidthScale() {
        return colorProfile.commonLineWidthScale();
    }

    public void previewOverlayColor(OverlayColorSlot slot, int color) {
        if (colorProfile.setColor(slot, color)) {
            OverlayRenderStyle.setColor(slot, color);
        }
    }

    public void setOverlayColor(OverlayColorSlot slot, int color) {
        if (slot == null) {
            return;
        }
        colorProfile.setColor(slot, color);
        OverlayRenderStyle.setColor(slot, color);
        saveSettings();
    }

    public void resetOverlayColor(OverlayColorSlot slot) {
        boolean changed = colorProfile.reset(slot);
        changed |= colorProfile.resetLineWidthScale(slot);
        if (changed) {
            OverlayRenderStyle.setColor(slot, colorProfile.color(slot));
            OverlayRenderStyle.setLineWidthScale(slot, colorProfile.lineWidthScale(slot));
            saveSettings();
        }
    }

    public void resetOverlayColors(OverlayColorSlot[] slots) {
        if (slots == null || slots.length == 0) {
            return;
        }
        boolean changed = false;
        for (OverlayColorSlot slot : slots) {
            changed |= colorProfile.reset(slot);
            changed |= colorProfile.resetLineWidthScale(slot);
        }
        if (changed) {
            OverlayRenderStyle.setColors(colorProfile);
            saveSettings();
        }
    }

    public void resetAllOverlayColors() {
        if (!colorProfile.resetColors()) {
            return;
        }
        OverlayRenderStyle.setColors(colorProfile);
        saveSettings();
    }

    public void resetAllOverlayLineWidths() {
        boolean changed = colorProfile.resetLineWidthScales();
        if (Math.abs(lineWidthScale - 1.0f) >= 0.001f) {
            lineWidthScale = 1.0f;
            OverlayRenderStyle.setLineWidthScale(lineWidthScale);
            changed = true;
        }
        if (changed) {
            OverlayRenderStyle.setColors(colorProfile);
            saveSettings();
        }
    }

    public void resetAllOverlayElementSettings() {
        colorProfile.resetAll();
        lineWidthScale = 1.0f;
        OverlayRenderStyle.setColors(colorProfile);
        OverlayRenderStyle.setLineWidthScale(lineWidthScale);
        saveSettings();
    }

    public void applyOverlayColorProfile(OverlayColorProfile profile) {
        if (profile == null) {
            return;
        }
        colorProfile.copyFrom(profile);
        lineWidthScale = 1.0f;
        OverlayRenderStyle.setColors(colorProfile);
        OverlayRenderStyle.setLineWidthScale(lineWidthScale);
        saveSettings();
    }

    public void previewOverlayLineWidthScale(OverlayColorSlot slot, float scale) {
        if (slot == null) {
            return;
        }
        if (colorProfile.setLineWidthScale(slot, clampLineWidthScale(scale))) {
            OverlayRenderStyle.setLineWidthScale(slot, colorProfile.lineWidthScale(slot));
        }
    }

    public void previewAllOverlayLineWidthScale(float scale) {
        if (colorProfile.setLineWidthScales(clampLineWidthScale(scale))) {
            lineWidthScale = 1.0f;
            OverlayRenderStyle.setLineWidthScale(lineWidthScale);
            OverlayRenderStyle.setColors(colorProfile);
        }
    }

    public void setOverlayLineWidthScale(OverlayColorSlot slot, float scale) {
        if (slot == null) {
            return;
        }
        colorProfile.setLineWidthScale(slot, clampLineWidthScale(scale));
        OverlayRenderStyle.setLineWidthScale(slot, colorProfile.lineWidthScale(slot));
        saveSettings();
    }

    public void setAllOverlayLineWidthScale(float scale) {
        boolean changed = colorProfile.setLineWidthScales(clampLineWidthScale(scale));
        if (Math.abs(lineWidthScale - 1.0f) >= 0.001f) {
            lineWidthScale = 1.0f;
            OverlayRenderStyle.setLineWidthScale(lineWidthScale);
            changed = true;
        }
        if (changed) {
            OverlayRenderStyle.setColors(colorProfile);
        }
        saveSettings();
    }

    public float lineWidthScale() {
        return lineWidthScale;
    }

    public void previewLineWidthScale(float scale) {
        lineWidthScale = clampLineWidthScale(scale);
        OverlayRenderStyle.setLineWidthScale(lineWidthScale);
    }

    public void setLineWidthScale(float scale) {
        float normalized = clampLineWidthScale(scale);
        if (Math.abs(lineWidthScale - normalized) < 0.001f) {
            saveSettings();
            return;
        }
        lineWidthScale = normalized;
        OverlayRenderStyle.setLineWidthScale(lineWidthScale);
        saveSettings();
    }

    public void setOverlayEnabled(boolean enabled) {
        if (overlayEnabled == enabled) {
            return;
        }
        overlayEnabled = enabled;
        saveSettings();
    }

    public void setSelectionGridEnabled(boolean enabled) {
        if (selectionGridEnabled == enabled) {
            return;
        }
        selectionGridEnabled = enabled;
        saveSettings();
    }

    public void setClipboardPreviewEnabled(boolean enabled) {
        if (clipboardPreviewEnabled == enabled) {
            return;
        }
        clipboardPreviewEnabled = enabled;
        clipboardPreviewController.setEnabled(enabled);
        saveSettings();
    }

    public void setRulerEnabled(boolean enabled) {
        if (rulerEnabled == enabled) {
            return;
        }
        rulerEnabled = enabled;
        saveSettings();
    }

    public void setRulerAxisXEnabled(boolean enabled) {
        if (rulerAxisXEnabled == enabled) {
            return;
        }
        rulerAxisXEnabled = enabled;
        saveSettings();
    }

    public void setRulerAxisYEnabled(boolean enabled) {
        if (rulerAxisYEnabled == enabled) {
            return;
        }
        rulerAxisYEnabled = enabled;
        saveSettings();
    }

    public void setRulerAxisZEnabled(boolean enabled) {
        if (rulerAxisZEnabled == enabled) {
            return;
        }
        rulerAxisZEnabled = enabled;
        saveSettings();
    }

    public void setRenderMode(RenderMode mode) {
        if (mode == null || renderMode == mode) {
            return;
        }
        renderMode = mode;
        saveSettings();
    }

    public void setDiagonalMode(DiagonalMode mode) {
        if (mode == null || diagonalMode == mode) {
            return;
        }
        diagonalMode = mode;
        saveSettings();
    }

    public void setRulerTrackingMode(RulerTrackingMode mode) {
        if (mode == null || rulerTrackingMode == mode) {
            return;
        }
        rulerTrackingMode = mode;
        saveSettings();
    }

    private void initSettings() {
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("worldedit_selection_overlay");
        settingsFile = configDir.resolve(SETTINGS_FILE_NAME);
        overlayEnabled = true;
        selectionGridEnabled = true;
        clipboardPreviewEnabled = false;
        rulerEnabled = false;
        rulerAxisXEnabled = true;
        rulerAxisYEnabled = true;
        rulerAxisZEnabled = true;
        excludedBlockFilters.clear();
        isolatedBlockIds.clear();
        renderMode = RenderMode.BLOCKY;
        diagonalMode = DiagonalMode.NONE;
        rulerTrackingMode = RulerTrackingMode.FOLLOW_CAMERA;
        lineWidthScale = 1.0f;
        uiStyle = UiStyle.GLASS;
        reducedMotion = false;
        uiBlur = false;
        UiStyle.setBlur(false);
        UiStyle.setCurrent(uiStyle);
        UiStyle.setReducedMotion(reducedMotion);
        colorProfile.resetAll();
        OverlayRenderStyle.setColors(colorProfile);
        OverlayRenderStyle.setLineWidthScale(lineWidthScale);

        if (!Files.exists(settingsFile)) {
            saveSettings();
            return;
        }

        try {
            String overlayValue = null;
            String selectionGridValue = null;
            String clipboardPreviewValue = null;
            String rulerValue = null;
            String rulerAxisXValue = null;
            String rulerAxisYValue = null;
            String rulerAxisZValue = null;
            String renderModeValue = null;
            String diagonalModeValue = null;
            String trackingModeValue = null;
            String excludedFiltersValue = null;
            String isolatedBlocksValue = null;
            String lineWidthScaleValue = null;
            String uiStyleValue = null;
            String reducedMotionValue = null;
            String uiBlurValue = null;
            boolean lineWidthSlotValuesFound = false;
            for (String line : Files.readAllLines(settingsFile, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                int separator = trimmed.indexOf('=');
                if (separator < 0) {
                    continue;
                }

                String key = trimmed.substring(0, separator).trim();
                String rawValue = trimmed.substring(separator + 1).trim();
                if (OVERLAY_ENABLED_KEY.equals(key)) {
                    overlayValue = rawValue;
                } else if (SELECTION_GRID_ENABLED_KEY.equals(key)) {
                    selectionGridValue = rawValue;
                } else if (CLIPBOARD_PREVIEW_ENABLED_KEY.equals(key)) {
                    clipboardPreviewValue = rawValue;
                } else if (RULER_ENABLED_KEY.equals(key)) {
                    rulerValue = rawValue;
                } else if (RULER_AXIS_X_ENABLED_KEY.equals(key)) {
                    rulerAxisXValue = rawValue;
                } else if (RULER_AXIS_Y_ENABLED_KEY.equals(key)) {
                    rulerAxisYValue = rawValue;
                } else if (RULER_AXIS_Z_ENABLED_KEY.equals(key)) {
                    rulerAxisZValue = rawValue;
                } else if (RENDER_MODE_KEY.equals(key)) {
                    renderModeValue = rawValue;
                } else if (DIAGONAL_MODE_KEY.equals(key)) {
                    diagonalModeValue = rawValue;
                } else if (RULER_TRACKING_MODE_KEY.equals(key)) {
                    trackingModeValue = rawValue;
                } else if (BLOCK_FILTER_EXCLUDED_KEY.equals(key)) {
                    excludedFiltersValue = rawValue;
                } else if (ISOLATED_BLOCKS_KEY.equals(key)) {
                    isolatedBlocksValue = rawValue;
                } else if (LINE_WIDTH_SCALE_KEY.equals(key)) {
                    lineWidthScaleValue = rawValue;
                } else if (UI_STYLE_KEY.equals(key)) {
                    uiStyleValue = rawValue;
                } else if (REDUCED_MOTION_KEY.equals(key)) {
                    reducedMotionValue = rawValue;
                } else if (UI_BLUR_KEY.equals(key)) {
                    uiBlurValue = rawValue;
                } else if (key.startsWith(COLOR_KEY_PREFIX)) {
                    colorProfile.applyConfigValue(key.substring(COLOR_KEY_PREFIX.length()), rawValue);
                } else if (key.startsWith(LINE_WIDTH_KEY_PREFIX)) {
                    lineWidthSlotValuesFound = true;
                    colorProfile.applyLineWidthConfigValue(key.substring(LINE_WIDTH_KEY_PREFIX.length()), rawValue);
                }
            }

            if (overlayValue != null) {
                overlayEnabled = Boolean.parseBoolean(overlayValue);
            }
            if (selectionGridValue != null) {
                selectionGridEnabled = Boolean.parseBoolean(selectionGridValue);
            }
            if (clipboardPreviewValue != null) {
                clipboardPreviewEnabled = Boolean.parseBoolean(clipboardPreviewValue);
            }
            if (rulerValue != null) {
                rulerEnabled = Boolean.parseBoolean(rulerValue);
            }
            if (rulerAxisXValue != null) {
                rulerAxisXEnabled = Boolean.parseBoolean(rulerAxisXValue);
            }
            if (rulerAxisYValue != null) {
                rulerAxisYEnabled = Boolean.parseBoolean(rulerAxisYValue);
            }
            if (rulerAxisZValue != null) {
                rulerAxisZEnabled = Boolean.parseBoolean(rulerAxisZValue);
            }
            renderMode = RenderMode.fromConfigValue(renderModeValue, RenderMode.BLOCKY);
            diagonalMode = DiagonalMode.fromConfigValue(diagonalModeValue, DiagonalMode.NONE);
            rulerTrackingMode = RulerTrackingMode.fromConfigValue(trackingModeValue, RulerTrackingMode.FOLLOW_CAMERA);
            lineWidthScale = parseFloat(lineWidthScaleValue, 1.0f);
            lineWidthScale = clampLineWidthScale(lineWidthScale);
            if (lineWidthScaleValue != null && !lineWidthSlotValuesFound) {
                for (OverlayColorSlot slot : OverlayColorSlot.values()) {
                    colorProfile.setLineWidthScale(slot, lineWidthScale);
                }
            }
            lineWidthScale = 1.0f;
            uiStyle = UiStyle.fromConfigValue(uiStyleValue);
            reducedMotion = Boolean.parseBoolean(reducedMotionValue);
            uiBlur = Boolean.parseBoolean(uiBlurValue);
            UiStyle.setBlur(uiBlur);
            UiStyle.setCurrent(uiStyle);
            UiStyle.setReducedMotion(reducedMotion);
            decodeBlockIdSet(excludedFiltersValue, excludedBlockFilters);
            decodeBlockIdSet(isolatedBlocksValue, isolatedBlockIds);
            isolatedBlockIds.removeIf(WorldEditPointOverlayClient::isAirBlockId);
            OverlayRenderStyle.setColors(colorProfile);
            OverlayRenderStyle.setLineWidthScale(lineWidthScale);
        } catch (Exception ex) {
            LOGGER.warn("Could not read settings file {}, using defaults.", settingsFile, ex);
            overlayEnabled = true;
            selectionGridEnabled = true;
            clipboardPreviewEnabled = false;
            rulerEnabled = false;
            rulerAxisXEnabled = true;
            rulerAxisYEnabled = true;
            rulerAxisZEnabled = true;
            excludedBlockFilters.clear();
            isolatedBlockIds.clear();
            renderMode = RenderMode.BLOCKY;
            diagonalMode = DiagonalMode.NONE;
            rulerTrackingMode = RulerTrackingMode.FOLLOW_CAMERA;
            lineWidthScale = 1.0f;
            uiStyle = UiStyle.GLASS;
            reducedMotion = false;
            uiBlur = false;
            UiStyle.setBlur(false);
            UiStyle.setCurrent(uiStyle);
            UiStyle.setReducedMotion(reducedMotion);
            colorProfile.resetAll();
            OverlayRenderStyle.setColors(colorProfile);
            OverlayRenderStyle.setLineWidthScale(lineWidthScale);
        }
    }

    private void saveSettings() {
        if (settingsFile == null) {
            return;
        }

        try {
            Path parent = settingsFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            String content = OVERLAY_ENABLED_KEY + "=" + overlayEnabled + System.lineSeparator()
                    + SELECTION_GRID_ENABLED_KEY + "=" + selectionGridEnabled + System.lineSeparator()
                    + CLIPBOARD_PREVIEW_ENABLED_KEY + "=" + clipboardPreviewEnabled + System.lineSeparator()
                    + RULER_ENABLED_KEY + "=" + rulerEnabled + System.lineSeparator()
                    + RULER_AXIS_X_ENABLED_KEY + "=" + rulerAxisXEnabled + System.lineSeparator()
                    + RULER_AXIS_Y_ENABLED_KEY + "=" + rulerAxisYEnabled + System.lineSeparator()
                    + RULER_AXIS_Z_ENABLED_KEY + "=" + rulerAxisZEnabled + System.lineSeparator()
                    + RENDER_MODE_KEY + "=" + renderMode.name() + System.lineSeparator()
                    + DIAGONAL_MODE_KEY + "=" + diagonalMode.name() + System.lineSeparator()
                    + RULER_TRACKING_MODE_KEY + "=" + rulerTrackingMode.name() + System.lineSeparator()
                    + LINE_WIDTH_SCALE_KEY + "=" + lineWidthScale + System.lineSeparator()
                    + UI_STYLE_KEY + "=" + uiStyle.name() + System.lineSeparator()
                    + REDUCED_MOTION_KEY + "=" + reducedMotion + System.lineSeparator()
                    + UI_BLUR_KEY + "=" + uiBlur + System.lineSeparator()
                    + BLOCK_FILTER_EXCLUDED_KEY + "=" + encodeBlockIdSet(excludedBlockFilters) + System.lineSeparator()
                    + ISOLATED_BLOCKS_KEY + "=" + encodeBlockIdSet(isolatedBlockIds) + System.lineSeparator()
                    + String.join(System.lineSeparator(), colorProfile.toConfigLines(COLOR_KEY_PREFIX))
                    + System.lineSeparator()
                    + String.join(System.lineSeparator(), colorProfile.toLineWidthConfigLines(LINE_WIDTH_KEY_PREFIX))
                    + System.lineSeparator();
            Files.writeString(settingsFile, content, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            LOGGER.warn("Could not write settings file {}.", settingsFile, ex);
        }
    }

    private static void decodeBlockIdSet(String rawValue, Set<String> output) {
        output.clear();
        if (rawValue == null || rawValue.isBlank()) {
            return;
        }

        String[] parts = rawValue.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty() && Identifier.tryParse(trimmed) != null) {
                output.add(trimmed);
            }
        }
    }

    private static String encodeBlockIdSet(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        ArrayList<String> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return String.join(",", sorted);
    }

    private static boolean isAirBlockId(String blockId) {
        return "minecraft:air".equals(blockId)
                || "minecraft:cave_air".equals(blockId)
                || "minecraft:void_air".equals(blockId);
    }

    private static Vec3d cameraForwardVector(float yawDegrees, float pitchDegrees) {
        Vec3d forward = Vec3d.fromPolar(pitchDegrees, yawDegrees);
        if (forward.lengthSquared() < 1.0e-8) {
            return new Vec3d(0.0, 0.0, 1.0);
        }
        return forward.normalize();
    }

    private static Vec3d cameraRightVector(Vec3d forward, float yawDegrees) {
        Vec3d worldUp = new Vec3d(0.0, 1.0, 0.0);
        Vec3d right = forward.crossProduct(worldUp);
        if (right.lengthSquared() < 1.0e-8) {
            double yawRad = Math.toRadians(yawDegrees);
            right = new Vec3d(-Math.cos(yawRad), 0.0, -Math.sin(yawRad));
        }
        if (right.lengthSquared() < 1.0e-8) {
            return new Vec3d(-1.0, 0.0, 0.0);
        }
        return right.normalize();
    }

    private static Vec3d cameraUpVector(Vec3d forward, Vec3d right) {
        Vec3d up = right.crossProduct(forward);
        if (up.lengthSquared() < 1.0e-8) {
            return new Vec3d(0.0, 1.0, 0.0);
        }
        return up.normalize();
    }

    private boolean shouldUseChatFallback() {
        if (SOURCE_MODE == SourceMode.CUI_ONLY) {
            return false;
        }
        return receivedSelectionPackets == 0;
    }

    private boolean shouldRequestResync(MinecraftClient client) {
        if (client.getNetworkHandler() == null || client.player == null) {
            return false;
        }
        if (receivedSelectionPackets > 0) {
            return false;
        }
        if (cuiCommandAttempts >= CUI_COMMAND_MAX_ATTEMPTS) {
            return false;
        }
        return client.player.age >= nextCuiCommandTick;
    }

    private static boolean isSelectionPayload(String eventType) {
        return switch (eventType) {
            case "s", "p", "p2", "e", "cyl", "mm", "poly", "grid" -> true;
            default -> false;
        };
    }

    private static boolean isPrimaryPointMessage(Text message, String raw) {
        if (message.getContent() instanceof TranslatableTextContent translatable) {
            String key = translatable.getKey();
            if ("worldedit.selection.cuboid.explain.primary".equals(key) || "worldedit.selection.cuboid.explain.primary-area".equals(key)) {
                return true;
            }
        }
        return raw.contains(RUS_PRIMARY) || raw.contains("first position");
    }

    private static boolean isSecondaryPointMessage(Text message, String raw) {
        if (message.getContent() instanceof TranslatableTextContent translatable) {
            String key = translatable.getKey();
            if ("worldedit.selection.cuboid.explain.secondary".equals(key) || "worldedit.selection.cuboid.explain.secondary-area".equals(key)) {
                return true;
            }
        }
        return raw.contains(RUS_SECONDARY) || raw.contains("second position");
    }

    private static BlockPos extractPointFromWorldEditMessage(Text message) {
        Matcher matcher = COORDS_PATTERN.matcher(message.getString());
        if (!matcher.find()) {
            return null;
        }

        try {
            int x = Integer.parseInt(matcher.group(1));
            int y = Integer.parseInt(matcher.group(2));
            int z = Integer.parseInt(matcher.group(3));
            return new BlockPos(x, y, z);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Integer parseInteger(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            Double fallback = parseDouble(value);
            if (fallback == null) {
                return null;
            }
            return (int) Math.floor(fallback);
        }
    }

    private static Double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static float parseFloat(String value, float fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static float clampLineWidthScale(float scale) {
        return Math.max(0.5f, Math.min(2.0f, scale));
    }
}
