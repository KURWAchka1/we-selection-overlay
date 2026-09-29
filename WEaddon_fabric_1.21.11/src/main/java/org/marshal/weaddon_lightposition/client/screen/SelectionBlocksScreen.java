package org.marshal.weaddon_lightposition.client.screen;

import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.marshal.weaddon_lightposition.client.BlockIsolationController;
import org.marshal.weaddon_lightposition.client.overlay.SelectionBlockAnalyzer;
import org.marshal.weaddon_lightposition.client.overlay.SelectionBlockSnapshot;
import org.marshal.weaddon_lightposition.client.overlay.SelectionRegion;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class SelectionBlocksScreen extends Screen {
    private static final int PANEL_MARGIN = 16;
    private static final int PANEL_MAX_WIDTH = 980;
    private static final int PANEL_MIN_WIDTH = 760;
    private static final int HEADER_HEIGHT = 112;
    private static final int FOOTER_HEIGHT = 26;
    private static final int ROW_HEIGHT = 26;
    private static final int FILTER_PANEL_WIDTH = 460;
    private static final int FILTER_PANEL_MAX_HEIGHT = 270;
    private static final int FILTER_HEADER_HEIGHT = 70;
    private static final int FILTER_FOOTER_HEIGHT = 28;
    private static final int FILTER_ROW_HEIGHT = 20;
    private static final int FILTER_COLUMNS_GAP = 8;
    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getIntegerInstance(Locale.US);

    private final SelectionBlockAnalyzer analyzer;
    private final Supplier<SelectionRegion> regionSupplier;
    private final Consumer<Set<String>> excludedFiltersConsumer;
    private final Consumer<Set<String>> isolatedBlocksConsumer;
    private final HashSet<String> expandedBlockIds = new HashSet<>();
    private final HashSet<String> excludedBlockIds;
    private final HashSet<String> isolatedBlockIds;

    private TextFieldWidget searchField;
    private TextFieldWidget filterSearchField;
    private ButtonWidget sortButton;
    private ButtonWidget refreshButton;
    private ButtonWidget filterButton;
    private ButtonWidget clearFiltersButton;
    private ButtonWidget resetIsolationButton;

    private SelectionBlockSnapshot lastSnapshot = SelectionBlockSnapshot.noSelection();
    private String lastSearchNormalized = "";
    private SortMode sortMode = SortMode.SMART;
    private int scrollRows;
    private int filterAvailableScrollRows;
    private int filterHiddenScrollRows;
    private boolean filtersMenuOpen;
    private int filteredOutSelectedBlockKinds;
    private long filteredOutSelectedCells;
    private List<GroupView> visibleGroups = List.of();
    private List<RowItem> visibleRows = List.of();
    private List<FilterEntry> availableFilterEntries = List.of();
    private List<FilterEntry> hiddenFilterEntries = List.of();

    public SelectionBlocksScreen(
            SelectionBlockAnalyzer analyzer,
            Supplier<SelectionRegion> regionSupplier,
            Set<String> initialExcludedBlockIds,
            Consumer<Set<String>> excludedFiltersConsumer,
            Set<String> initialIsolatedBlockIds,
            Consumer<Set<String>> isolatedBlocksConsumer
    ) {
        super(Text.translatable("screen.worldedit_selection_overlay.blocks.title"));
        this.analyzer = analyzer;
        this.regionSupplier = regionSupplier;
        this.excludedFiltersConsumer = excludedFiltersConsumer == null ? ignored -> { } : excludedFiltersConsumer;
        this.isolatedBlocksConsumer = isolatedBlocksConsumer == null ? ignored -> { } : isolatedBlocksConsumer;
        this.excludedBlockIds = initialExcludedBlockIds == null
                ? new HashSet<>()
                : new HashSet<>(initialExcludedBlockIds);
        this.isolatedBlockIds = initialIsolatedBlockIds == null
                ? new HashSet<>()
                : new HashSet<>(initialIsolatedBlockIds);
    }

    @Override
    protected void init() {
        String previousSearch = searchField != null ? searchField.getText() : "";
        String previousFilterSearch = filterSearchField != null ? filterSearchField.getText() : "";
        int panelX = panelX();
        int panelY = panelY();
        int panelWidth = panelWidth();
        int gap = 6;
        int refreshWidth = 86;
        int sortWidth = 154;
        int filterWidth = 84;
        int refreshX = panelX + panelWidth - 10 - refreshWidth;
        int sortX = refreshX - gap - sortWidth;
        int filterX = sortX - gap - filterWidth;
        int searchX = panelX + 10;
        int searchWidth = Math.max(120, filterX - gap - searchX);
        int resetWidth = 128;
        int resetX = listX() + 2;
        int resetY = listY() - 24;

        filterButton = addDrawableChild(
                ButtonWidget.builder(filterButtonText(), button -> {
                    setFiltersMenuOpen(!filtersMenuOpen);
                }).dimensions(filterX, panelY + 26, filterWidth, 20).build()
        );

        searchField = new TextFieldWidget(
                textRenderer,
                searchX,
                panelY + 26,
                searchWidth,
                20,
                Text.translatable("screen.worldedit_selection_overlay.blocks.search")
        );
        searchField.setMaxLength(128);
        searchField.setText(previousSearch);
        searchField.setChangedListener(value -> rebuildVisibleEntries());
        addDrawableChild(searchField);

        sortButton = addDrawableChild(
                ButtonWidget.builder(sortButtonText(), button -> {
                    sortMode = sortMode.next();
                    button.setMessage(sortButtonText());
                    rebuildVisibleEntries();
                }).dimensions(sortX, panelY + 26, sortWidth, 20).build()
        );

        resetIsolationButton = addDrawableChild(
                ButtonWidget.builder(
                        Text.translatable("screen.worldedit_selection_overlay.blocks.isolation.reset"),
                        button -> clearIsolatedBlocks()
                ).dimensions(resetX, resetY, resetWidth, 20).build()
        );

        refreshButton = addDrawableChild(
                ButtonWidget.builder(
                        Text.translatable("screen.worldedit_selection_overlay.blocks.refresh"),
                        button -> requestRefresh()
                ).dimensions(refreshX, panelY + 26, refreshWidth, 20).build()
        );

        filterSearchField = new TextFieldWidget(
                textRenderer,
                filterPanelX() + 10,
                filterPanelY() + 26,
                FILTER_PANEL_WIDTH - 20,
                18,
                Text.translatable("screen.worldedit_selection_overlay.blocks.filters.search")
        );
        filterSearchField.setMaxLength(96);
        filterSearchField.setText(previousFilterSearch);
        filterSearchField.setChangedListener(value -> rebuildFilterEntries(analyzer.snapshot()));
        addDrawableChild(filterSearchField);

        clearFiltersButton = addDrawableChild(
                ButtonWidget.builder(
                        Text.translatable("screen.worldedit_selection_overlay.blocks.filters.clear"),
                        button -> {
                            excludedBlockIds.clear();
                            persistExcludedFilters();
                            rebuildVisibleEntries();
                        }
                ).dimensions(filterPanelX() + FILTER_PANEL_WIDTH - 72, filterPanelY() + FILTER_PANEL_MAX_HEIGHT - 24, 62, 16).build()
        );

        setInitialFocus(searchField);
        rebuildVisibleEntries();
        updateIsolationControls();
        updateFilterMenuVisibility();
    }

    @Override
    public void tick() {
        super.tick();

        MinecraftClient client = this.client;
        if (client == null) {
            return;
        }

        analyzer.tick(client.world, regionSupplier.get());
        syncIsolatedBlocksFromController();

        SelectionBlockSnapshot snapshot = analyzer.snapshot();
        String normalizedSearch = normalizeSearch(searchField != null ? searchField.getText() : "");
        if (snapshot != lastSnapshot || !normalizedSearch.equals(lastSearchNormalized)) {
            rebuildVisibleEntries();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        positionFilterMenuWidgets();

        drawScreenBackdrop(context);

        int panelX = panelX();
        int panelY = panelY();
        int panelWidth = panelWidth();
        int panelHeight = panelHeight();

        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xCC101318);
        drawOutline(context, panelX, panelY, panelWidth, panelHeight, 0xFF3A465A);

        context.drawText(textRenderer, title, panelX + 10, panelY + 8, 0xFFEAEFF8, false);
        drawStatus(context, panelX + 10, panelY + 52);

        drawList(context);
        drawFooter(context, panelX + 10, panelY + panelHeight - FOOTER_HEIGHT + 6, panelWidth - 20);

        if (filtersMenuOpen) {
            drawFilterMenu(context, mouseX, mouseY);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        if (click.button() == 0 && filtersMenuOpen) {
            if (isInsideFilterPanel(click.x(), click.y())) {
                if (isInsideAvailableFilterList(click.x(), click.y()) && tryMoveFilterEntryToHidden(click.x(), click.y())) {
                    return true;
                }
                if (isInsideHiddenFilterList(click.x(), click.y()) && tryMoveFilterEntryToVisible(click.x(), click.y())) {
                    return true;
                }
                // Let filter widgets handle the click, but block the underlying list.
                if (super.mouseClicked(click, doubleClick)) {
                    return true;
                }
                return true;
            }

            if (!isInsideButton(filterButton, click.x(), click.y())) {
                setFiltersMenuOpen(false);
                return true;
            }
        }

        if (click.button() == 0 && isMouseInsideList(click.x(), click.y()) && !visibleRows.isEmpty()) {
            int listY = listY();
            int localY = (int) click.y() - (listY + 1);
            if (localY >= 0) {
                int rowInView = localY / ROW_HEIGHT;
                int absoluteRow = scrollRows + rowInView;
                if (absoluteRow >= 0 && absoluteRow < visibleRows.size()) {
                    RowItem rowItem = visibleRows.get(absoluteRow);
                    if (rowItem instanceof GroupRow groupRow) {
                        int rowY = listY + 1 + rowInView * ROW_HEIGHT;
                        if (isInsideIsolationToggle(click.x(), click.y(), listX(), rowY)) {
                            toggleIsolatedGroup(groupRow.group());
                            return true;
                        }
                        if (groupRow.group().expandable()) {
                            toggleExpanded(groupRow.group().groupId());
                            rebuildVisibleEntries();
                            return true;
                        }
                    }
                }
            }
        }
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (filtersMenuOpen && isInsideAvailableFilterList(mouseX, mouseY) && !availableFilterEntries.isEmpty()) {
            int delta = verticalAmount > 0.0 ? -3 : 3;
            setAvailableFilterScrollRows(filterAvailableScrollRows + delta);
            return true;
        }
        if (filtersMenuOpen && isInsideHiddenFilterList(mouseX, mouseY) && !hiddenFilterEntries.isEmpty()) {
            int delta = verticalAmount > 0.0 ? -3 : 3;
            setHiddenFilterScrollRows(filterHiddenScrollRows + delta);
            return true;
        }
        if (filtersMenuOpen && isInsideFilterPanel(mouseX, mouseY)) {
            return true;
        }

        if (isMouseInsideList(mouseX, mouseY) && !visibleRows.isEmpty()) {
            int delta = verticalAmount > 0.0 ? -3 : 3;
            setScrollRows(scrollRows + delta);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void requestRefresh() {
        if (client == null) {
            return;
        }
        analyzer.refresh(client.world, regionSupplier.get());
        rebuildVisibleEntries();
    }

    private void drawStatus(DrawContext context, int x, int y) {
        SelectionBlockSnapshot snapshot = analyzer.snapshot();
        switch (snapshot.state()) {
            case NO_SELECTION -> context.drawText(
                    textRenderer,
                    Text.translatable("screen.worldedit_selection_overlay.blocks.state.no_selection"),
                    x,
                    y,
                    0xFFFFB366,
                    false
            );
            case INCOMPLETE_SELECTION -> context.drawText(
                    textRenderer,
                    Text.translatable("screen.worldedit_selection_overlay.blocks.state.incomplete"),
                    x,
                    y,
                    0xFFFFCC66,
                    false
            );
            case SCANNING -> context.drawText(
                    textRenderer,
                    Text.translatable(
                            "screen.worldedit_selection_overlay.blocks.state.scanning",
                            formatPercent(snapshot.progress()),
                            formatLong(snapshot.scannedBoundsCells()),
                            formatLong(snapshot.totalBoundsCells())
                    ),
                    x,
                    y,
                    0xFF88D0FF,
                    false
            );
            case COMPLETE -> context.drawText(
                    textRenderer,
                    Text.translatable(
                            "screen.worldedit_selection_overlay.blocks.state.complete",
                            formatLong(snapshot.selectedCells())
                    ),
                    x,
                    y,
                    0xFF8DF0A0,
                    false
            );
        }

        int detailY = y + 12;
        if (!isolatedBlockIds.isEmpty()) {
            context.drawText(
                    textRenderer,
                    Text.translatable(
                            "screen.worldedit_selection_overlay.blocks.isolation.active",
                            formatLong(isolatedBlockIds.size())
                    ),
                    x,
                    detailY,
                    0xFFA8F0C0,
                    false
            );
            detailY += 12;
        }

        if (filteredOutSelectedCells > 0L) {
            context.drawText(
                    textRenderer,
                    Text.translatable(
                            "screen.worldedit_selection_overlay.blocks.filters.warning",
                            formatLong(filteredOutSelectedBlockKinds),
                            formatLong(filteredOutSelectedCells)
                    ),
                    x,
                    detailY,
                    0xFFFFD27A,
                    false
            );
        }
    }

    private void drawList(DrawContext context) {
        int listX = listX();
        int listY = listY();
        int listWidth = listWidth();
        int listHeight = listHeight();

        context.fill(listX, listY, listX + listWidth, listY + listHeight, 0x80202833);
        drawOutline(context, listX, listY, listWidth, listHeight, 0xFF3A465A);

        if (visibleRows.isEmpty()) {
            context.drawCenteredTextWithShadow(
                    textRenderer,
                    Text.translatable("screen.worldedit_selection_overlay.blocks.empty_filter"),
                    listX + listWidth / 2,
                    listY + listHeight / 2 - 4,
                    0xFFB0B8C4
            );
            return;
        }

        int visibleRowCount = Math.max(1, listHeight / ROW_HEIGHT);
        int endExclusive = Math.min(visibleRows.size(), scrollRows + visibleRowCount);
        long filteredSelected = Math.max(1L, visibleSelectedCellsCount());

        int rowY = listY + 1;
        for (int index = scrollRows; index < endExclusive; index++) {
            RowItem rowItem = visibleRows.get(index);
            int rowColor = (index & 1) == 0 ? 0x60212B38 : 0x60404A58;
            if (rowItem instanceof StateRow) {
                rowColor = (index & 1) == 0 ? 0x5030384A : 0x50424A5C;
            }
            if (rowItem instanceof GroupRow groupRow && groupRow.group().isolated()) {
                rowColor = (index & 1) == 0 ? 0x70415A34 : 0x70506A3F;
            }
            context.fill(listX + 1, rowY, listX + listWidth - 1, rowY + ROW_HEIGHT - 1, rowColor);

            if (rowItem instanceof GroupRow groupRow) {
                drawGroupRow(context, groupRow.group(), listX, listWidth, rowY, filteredSelected);
            } else if (rowItem instanceof StateRow stateRow) {
                drawStateRow(context, stateRow.state(), listX, listWidth, rowY, filteredSelected);
            }

            rowY += ROW_HEIGHT;
        }
    }

    private void drawGroupRow(
            DrawContext context,
            GroupView group,
            int listX,
            int listWidth,
            int rowY,
            long loadedSelected
    ) {
        SelectionBlockSnapshot.BlockEntry entry = group.representative();
        boolean isolated = group.isolated();
        drawIsolationCheckbox(context, listX + 8, rowY + 7, isolated);

        int iconX = listX + 30;
        int iconY = rowY + 4;
        ItemStack stack = stackFor(entry);
        if (!stack.isEmpty()) {
            context.drawItem(stack, iconX, iconY);
        } else {
            context.drawText(textRenderer, "AIR", iconX, iconY + 4, 0xFFE1C58A, false);
        }

        int textX = listX + 52;
        String marker;
        if (!group.expandable()) {
            marker = "   ";
        } else if (expandedBlockIds.contains(group.groupId())) {
            marker = "[-]";
        } else {
            marker = "[+]";
        }

        context.drawText(
                textRenderer,
                marker + " " + group.displayName(),
                textX,
                rowY + 3,
                isolated ? 0xFFD8FFD8 : (group.air() ? 0xFFFFD28D : 0xFFEAF2FF),
                false
        );

        String stateMeta = Text.translatable(
                "screen.worldedit_selection_overlay.blocks.states_count",
                formatLong(group.states().size())
        ).getString();
        context.drawText(
                textRenderer,
                groupIdLine(group) + "  -  " + stateMeta,
                textX,
                rowY + 13,
                0xFF90A0B7,
                false
        );

        String countText = formatLong(group.count());
        String percentText = formatPercent(group.count() / (double) loadedSelected);
        int countX = listX + listWidth - 8 - textRenderer.getWidth(countText);
        int percentX = listX + listWidth - 8 - textRenderer.getWidth(percentText);
        context.drawText(textRenderer, countText, countX, rowY + 4, 0xFFF7E27E, false);
        context.drawText(textRenderer, percentText, percentX, rowY + 14, 0xFF8FD3FF, false);
    }

    private void drawStateRow(
            DrawContext context,
            StateView state,
            int listX,
            int listWidth,
            int rowY,
            long loadedSelected
    ) {
        SelectionBlockSnapshot.BlockEntry entry = state.entry();
        int textX = listX + 64;

        context.drawText(
                textRenderer,
                "|- " + state.primaryLabel(),
                textX,
                rowY + 3,
                0xFFC8E7FF,
                false
        );
        if (!state.secondaryLabel().isEmpty()) {
            context.drawText(
                    textRenderer,
                    state.secondaryLabel(),
                    textX,
                    rowY + 13,
                    0xFF8399B3,
                    false
            );
        }

        String countText = formatLong(entry.count());
        String percentText = formatPercent(entry.count() / (double) loadedSelected);
        int countX = listX + listWidth - 8 - textRenderer.getWidth(countText);
        int percentX = listX + listWidth - 8 - textRenderer.getWidth(percentText);
        context.drawText(textRenderer, countText, countX, rowY + 4, 0xFFEAD186, false);
        context.drawText(textRenderer, percentText, percentX, rowY + 14, 0xFF8FD3FF, false);
    }

    private void drawIsolationCheckbox(DrawContext context, int x, int y, boolean checked) {
        int borderColor = checked ? 0xFFA8F0C0 : 0xFF758398;
        int fillColor = checked ? 0xFF254A35 : 0xFF161B22;
        context.fill(x, y, x + 12, y + 12, fillColor);
        drawOutline(context, x, y, 12, 12, borderColor);
        if (checked) {
            context.drawText(textRenderer, "\u2713", x + 3, y + 1, 0xFFE9FFF0, false);
        }
    }

    private void drawFooter(DrawContext context, int x, int y, int innerWidth) {
        SelectionBlockSnapshot snapshot = analyzer.snapshot();
        int secondColumnX = x + Math.max(230, innerWidth / 2);
        context.drawText(
                textRenderer,
                Text.translatable("screen.worldedit_selection_overlay.blocks.selected", formatLong(snapshot.selectedCells())),
                x,
                y,
                0xFFEAF2FF,
                false
        );
        context.drawText(
                textRenderer,
                Text.translatable("screen.worldedit_selection_overlay.blocks.loaded", formatLong(snapshot.loadedSelectedCells())),
                secondColumnX,
                y,
                0xFFEAF2FF,
                false
        );
        context.drawText(
                textRenderer,
                Text.translatable("screen.worldedit_selection_overlay.blocks.unloaded", formatLong(snapshot.unloadedSelectedCells())),
                x,
                y + 10,
                0xFFFFB88A,
                false
        );
        context.drawText(
                textRenderer,
                Text.translatable("screen.worldedit_selection_overlay.blocks.entries", formatLong(visibleGroups.size())),
                secondColumnX,
                y + 10,
                0xFFB0C0D7,
                false
        );
    }

    private void drawFilterMenu(DrawContext context, int mouseX, int mouseY) {
        int x = filterPanelX();
        int y = filterPanelY();
        int width = FILTER_PANEL_WIDTH;
        int height = filterPanelHeight();

        context.fill(x, y, x + width, y + height, 0xE4181D25);
        drawOutline(context, x, y, width, height, 0xFF455267);

        context.drawText(
                textRenderer,
                Text.translatable("screen.worldedit_selection_overlay.blocks.filters.title"),
                x + 10,
                y + 8,
                0xFFEAF2FF,
                false
        );
        context.drawText(
                textRenderer,
                Text.translatable(
                        "screen.worldedit_selection_overlay.blocks.filters.excluded",
                        formatLong(excludedBlockIds.size())
                ),
                x + 10,
                y + height - FILTER_FOOTER_HEIGHT + 8,
                0xFFB2C2D9,
                false
        );

        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.translatable("screen.worldedit_selection_overlay.blocks.filters.available"),
                filterAvailableListX() + filterAvailableListWidth() / 2,
                y + 52,
                0xFFCFDEF3
        );
        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.translatable("screen.worldedit_selection_overlay.blocks.filters.hidden"),
                filterHiddenListX() + filterHiddenListWidth() / 2,
                y + 52,
                0xFFDCC9B8
        );

        drawFilterColumn(
                context,
                availableFilterEntries,
                filterAvailableScrollRows,
                filterAvailableListX(),
                filterListY(),
                filterAvailableListWidth(),
                filterListHeight(),
                false
        );
        drawFilterColumn(
                context,
                hiddenFilterEntries,
                filterHiddenScrollRows,
                filterHiddenListX(),
                filterListY(),
                filterHiddenListWidth(),
                filterListHeight(),
                true
        );
    }

    private void drawFilterColumn(
            DrawContext context,
            List<FilterEntry> entries,
            int scrollRows,
            int listX,
            int listY,
            int listWidth,
            int listHeight,
            boolean hiddenColumn
    ) {
        context.fill(listX, listY, listX + listWidth, listY + listHeight, 0x80202733);
        drawOutline(context, listX, listY, listWidth, listHeight, 0xFF3A465A);

        if (entries.isEmpty()) {
            context.drawCenteredTextWithShadow(
                    textRenderer,
                    Text.translatable(
                            hiddenColumn
                                    ? "screen.worldedit_selection_overlay.blocks.filters.empty_hidden"
                                    : "screen.worldedit_selection_overlay.blocks.filters.empty_available"
                    ),
                    listX + listWidth / 2,
                    listY + listHeight / 2 - 4,
                    0xFFB0B8C4
            );
            return;
        }

        int visibleRowCount = Math.max(1, listHeight / FILTER_ROW_HEIGHT);
        int endExclusive = Math.min(entries.size(), scrollRows + visibleRowCount);
        int rowY = listY + 1;
        for (int index = scrollRows; index < endExclusive; index++) {
            FilterEntry entry = entries.get(index);
            int rowColor = hiddenColumn
                    ? ((index & 1) == 0 ? 0x704C3724 : 0x7055402B)
                    : ((index & 1) == 0 ? 0x60303A48 : 0x60404A58);
            context.fill(listX + 1, rowY, listX + listWidth - 1, rowY + FILTER_ROW_HEIGHT - 1, rowColor);

            int iconX = listX + 4;
            int iconY = rowY + 2;
            ItemStack stack = stackFor(entry);
            if (!stack.isEmpty()) {
                context.drawItem(stack, iconX, iconY);
            } else {
                context.drawText(textRenderer, "AIR", iconX, rowY + 6, 0xFFE1C58A, false);
            }

            String countText = formatLong(entry.count());
            int countWidth = textRenderer.getWidth(countText);
            int labelX = listX + 24;
            int labelMaxWidth = Math.max(28, listWidth - 24 - 8 - countWidth - 6);
            String label = trimWithEllipsis(entry.displayName(), labelMaxWidth);

            context.drawText(
                    textRenderer,
                    label,
                    labelX,
                    rowY + 6,
                    hiddenColumn ? 0xFFFFE3AA : 0xFFE7F0FF,
                    false
            );
            context.drawText(
                    textRenderer,
                    countText,
                    listX + listWidth - 8 - countWidth,
                    rowY + 6,
                    0xFFB8C8DF,
                    false
            );
            rowY += FILTER_ROW_HEIGHT;
        }
    }

    private ItemStack stackFor(SelectionBlockSnapshot.BlockEntry entry) {
        if (entry != null && entry.fluid()) {
            ItemStack fluidStack = bucketStackForFluidBlockId(entry.blockId());
            if (!fluidStack.isEmpty()) {
                return fluidStack;
            }
        }
        return stackForBlockId(entry.blockId());
    }

    private ItemStack stackFor(FilterEntry entry) {
        if (entry != null && entry.fluid()) {
            ItemStack fluidStack = bucketStackForFluidBlockId(entry.blockId());
            if (!fluidStack.isEmpty()) {
                return fluidStack;
            }
        }
        return stackForBlockId(entry.blockId());
    }

    private ItemStack stackForBlockId(String blockId) {
        Block block = blockForId(blockId);
        if (block == null) {
            return ItemStack.EMPTY;
        }

        Item item = block.asItem();
        if ((item == null || item == Items.AIR)) {
            Identifier canonicalId = canonicalBlockId(blockId);
            Block canonicalBlock = canonicalId == null ? null : blockForId(canonicalId);
            if (canonicalBlock != null && canonicalBlock != block) {
                item = canonicalBlock.asItem();
            }
        }
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item);
    }

    private static ItemStack bucketStackForFluidBlockId(String blockId) {
        Block block = blockForId(blockId);
        if (block != null) {
            Item item = block.asItem();
            if (item != null && item != Items.AIR && !isKnownBucketBackedBlock(blockId)) {
                return ItemStack.EMPTY;
            }
        }

        Item bucketItem = bucketItemForFluidBlockId(blockId);
        if (bucketItem == null || bucketItem == Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(bucketItem);
    }

    private static Item bucketItemForFluidBlockId(String blockId) {
        Identifier id = Identifier.tryParse(blockId);
        if (id == null) {
            return null;
        }

        String namespace = id.getNamespace();
        String path = id.getPath();
        for (String candidatePath : bucketPathCandidates(path)) {
            Identifier bucketId = Identifier.of(namespace, candidatePath);
            if (Registries.ITEM.containsId(bucketId)) {
                Item item = Registries.ITEM.get(bucketId);
                if (item != null && item != Items.AIR) {
                    return item;
                }
            }
        }

        return switch (blockId) {
            case "minecraft:water" -> Items.WATER_BUCKET;
            case "minecraft:lava" -> Items.LAVA_BUCKET;
            case "minecraft:powder_snow" -> Items.POWDER_SNOW_BUCKET;
            default -> Items.BUCKET;
        };
    }

    private static List<String> bucketPathCandidates(String path) {
        ArrayList<String> candidates = new ArrayList<>(5);
        addCandidate(candidates, path + "_bucket");
        if (path.startsWith("flowing_")) {
            addCandidate(candidates, path.substring("flowing_".length()) + "_bucket");
        }
        addCandidate(candidates, normalizeSuffix(path, "_fluid", "_bucket"));
        addCandidate(candidates, normalizeSuffix(path, "_block", "_bucket"));
        addCandidate(candidates, normalizeSuffix(path, "_source", "_bucket"));
        return candidates;
    }

    private static boolean isKnownBucketBackedBlock(String blockId) {
        return "minecraft:water".equals(blockId)
                || "minecraft:lava".equals(blockId)
                || "minecraft:powder_snow".equals(blockId);
    }

    private static String resolveBlockDisplayName(Block block) {
        if (block == null) {
            return "";
        }
        Item item = block.asItem();
        if (item != null && item != Items.AIR) {
            return new ItemStack(item).getName().getString();
        }
        return block.getName().getString();
    }

    private static String resolveGroupDisplayName(SelectionBlockSnapshot.BlockEntry entry) {
        if (entry == null) {
            return "";
        }

        Identifier canonicalId = canonicalBlockId(entry.blockId());
        Block block = canonicalId == null ? null : blockForId(canonicalId);
        if (block == null) {
            return entry.displayName();
        }

        Item item = block.asItem();
        if (item != null && item != Items.AIR) {
            return new ItemStack(item).getName().getString();
        }
        return entry.displayName();
    }

    private static String groupIdFor(SelectionBlockSnapshot.BlockEntry entry) {
        if (entry == null) {
            return "block:minecraft:air";
        }

        Identifier canonicalId = canonicalBlockId(entry.blockId());
        Block block = canonicalId == null ? null : blockForId(canonicalId);
        if (block == null) {
            return "block:" + entry.blockId();
        }

        Item item = block.asItem();
        if (item != null && item != Items.AIR) {
            Identifier itemId = Registries.ITEM.getId(item);
            if (itemId != null) {
                return "item:" + itemId;
            }
        }
        return "block:" + canonicalId;
    }

    private static Block blockForId(String blockId) {
        Identifier id = Identifier.tryParse(blockId);
        if (id == null) {
            return null;
        }
        return blockForId(id);
    }

    private static Block blockForId(Identifier id) {
        if (id == null || !Registries.BLOCK.containsId(id)) {
            return null;
        }
        return Registries.BLOCK.get(id);
    }

    private static Identifier canonicalBlockId(String blockId) {
        Identifier id = Identifier.tryParse(blockId);
        if (id == null) {
            return null;
        }

        String namespace = id.getNamespace();
        for (String candidatePath : canonicalBlockPathCandidates(id.getPath())) {
            Identifier candidate = Identifier.of(namespace, candidatePath);
            if (Registries.BLOCK.containsId(candidate)) {
                return candidate;
            }
        }
        return id;
    }

    private static List<String> canonicalBlockPathCandidates(String path) {
        ArrayList<String> candidates = new ArrayList<>(4);
        addCandidate(candidates, normalizeWallTorchPath(path));
        addCandidate(candidates, normalizeSuffix(path, "_wall_hanging_sign", "_hanging_sign"));
        addCandidate(candidates, normalizeSuffix(path, "_wall_sign", "_sign"));
        addCandidate(candidates, normalizeSuffix(path, "_wall_banner", "_banner"));
        addCandidate(candidates, normalizeSuffix(path, "_wall_skull", "_skull"));
        addCandidate(candidates, normalizeSuffix(path, "_wall_head", "_head"));
        addCandidate(candidates, normalizeSuffix(path, "_wall_fan", "_fan"));
        addCandidate(candidates, normalizePrefix(path, "attached_"));
        addCandidate(candidates, normalizeSuffix(path, "_plant", ""));
        addCandidate(candidates, path);
        return candidates;
    }

    private static String normalizeWallTorchPath(String path) {
        if ("wall_torch".equals(path)) {
            return "torch";
        }
        return normalizeSuffix(path, "_wall_torch", "_torch");
    }

    private static String normalizeSuffix(String path, String suffix, String replacement) {
        if (path == null || !path.endsWith(suffix) || path.length() <= suffix.length()) {
            return null;
        }
        return path.substring(0, path.length() - suffix.length()) + replacement;
    }

    private static String normalizePrefix(String path, String prefix) {
        if (path == null || !path.startsWith(prefix) || path.length() <= prefix.length()) {
            return null;
        }
        return path.substring(prefix.length());
    }

    private static void addCandidate(List<String> candidates, String path) {
        if (path != null && !path.isEmpty() && !candidates.contains(path)) {
            candidates.add(path);
        }
    }

    private static boolean containsAny(Set<String> selectedIds, List<String> blockIds) {
        if (selectedIds == null || selectedIds.isEmpty() || blockIds == null || blockIds.isEmpty()) {
            return false;
        }
        for (String blockId : blockIds) {
            if (selectedIds.contains(blockId)) {
                return true;
            }
        }
        return false;
    }

    private void rebuildVisibleEntries() {
        SelectionBlockSnapshot snapshot = analyzer.snapshot();
        String normalizedSearch = normalizeSearch(searchField != null ? searchField.getText() : "");
        List<String> tokens = tokenize(normalizedSearch);
        boolean hasSearch = !tokens.isEmpty();
        filteredOutSelectedBlockKinds = 0;
        filteredOutSelectedCells = 0L;

        HashMap<String, GroupBuilder> groupsById = new HashMap<>();
        for (SelectionBlockSnapshot.BlockEntry entry : snapshot.entries()) {
            if (excludedBlockIds.contains(entry.blockId())) {
                filteredOutSelectedBlockKinds++;
                filteredOutSelectedCells += entry.count();
                continue;
            }

            int score = score(entry, tokens);
            if (score < 0) {
                continue;
            }

            String groupId = groupIdFor(entry);
            GroupBuilder group = groupsById.get(groupId);
            if (group == null) {
                group = new GroupBuilder(groupId, entry);
                groupsById.put(groupId, group);
            }
            group.add(entry, score);
        }

        ArrayList<GroupView> groups = new ArrayList<>(groupsById.size());
        for (GroupBuilder groupBuilder : groupsById.values()) {
            groups.add(groupBuilder.build(hasSearch, isolatedBlockIds));
        }
        groups.sort(groupComparator(hasSearch));

        HashSet<String> currentIds = new HashSet<>();
        for (GroupView group : groups) {
            currentIds.add(group.groupId());
            if (!group.expandable()) {
                expandedBlockIds.remove(group.groupId());
            }
        }
        expandedBlockIds.retainAll(currentIds);

        ArrayList<RowItem> rows = new ArrayList<>();
        for (GroupView group : groups) {
            rows.add(new GroupRow(group));
            if (group.expandable() && expandedBlockIds.contains(group.groupId())) {
                for (StateView state : group.states()) {
                    rows.add(new StateRow(group, state));
                }
            }
        }

        visibleGroups = groups;
        visibleRows = rows;
        setScrollRows(scrollRows);
        lastSnapshot = snapshot;
        lastSearchNormalized = normalizedSearch;
        rebuildFilterEntries(snapshot);
    }

    private void rebuildFilterEntries(SelectionBlockSnapshot snapshot) {
        HashMap<String, Long> selectedCounts = new HashMap<>();
        for (SelectionBlockSnapshot.BlockEntry entry : snapshot.entries()) {
            selectedCounts.merge(entry.blockId(), entry.count(), Long::sum);
        }

        String search = normalizeSearch(filterSearchField != null ? filterSearchField.getText() : "");
        List<String> tokens = tokenize(search);
        ArrayList<FilterEntry> entries = new ArrayList<>(Registries.BLOCK.getIds().size());
        HashSet<String> knownBlockIds = new HashSet<>();
        for (Identifier id : Registries.BLOCK.getIds()) {
            Block block = Registries.BLOCK.get(id);
            if (block == null) {
                continue;
            }

            String blockId = id.toString();
            knownBlockIds.add(blockId);
            FilterEntry entry = new FilterEntry(
                    blockId,
                    resolveBlockDisplayName(block),
                    block.getDefaultState().isAir(),
                    !block.getDefaultState().getFluidState().isEmpty(),
                    selectedCounts.getOrDefault(blockId, 0L)
            );
            if (matchesFilterSearch(entry, tokens)) {
                entries.add(entry);
            }
        }

        // Drop stale filters that reference unknown/removed blocks.
        if (excludedBlockIds.retainAll(knownBlockIds)) {
            persistExcludedFilters();
        }

        entries.sort(
                Comparator.comparingLong(FilterEntry::count).reversed()
                        .thenComparing(FilterEntry::displayName)
                        .thenComparing(FilterEntry::blockId)
        );
        ArrayList<FilterEntry> availableEntries = new ArrayList<>();
        ArrayList<FilterEntry> hiddenEntries = new ArrayList<>();
        for (FilterEntry entry : entries) {
            if (excludedBlockIds.contains(entry.blockId())) {
                hiddenEntries.add(entry);
            } else {
                availableEntries.add(entry);
            }
        }

        availableFilterEntries = availableEntries;
        hiddenFilterEntries = hiddenEntries;
        setAvailableFilterScrollRows(filterAvailableScrollRows);
        setHiddenFilterScrollRows(filterHiddenScrollRows);
        if (filterButton != null) {
            filterButton.setMessage(filterButtonText());
        }
    }

    private static boolean matchesFilterSearch(FilterEntry entry, List<String> tokens) {
        if (tokens.isEmpty()) {
            return true;
        }

        String blockId = entry.blockId().toLowerCase(Locale.ROOT);
        String displayName = entry.displayName().toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }
            if (token.startsWith("#")) {
                if ("#air".equals(token) && entry.air()) {
                    continue;
                }
                if ("#fluid".equals(token) && entry.fluid()) {
                    continue;
                }
                if ("#solid".equals(token) && !entry.air() && !entry.fluid()) {
                    continue;
                }
                return false;
            }
            if (!blockId.contains(token) && !displayName.contains(token)) {
                return false;
            }
        }
        return true;
    }

    private Comparator<GroupView> groupComparator(boolean hasSearch) {
        Comparator<GroupView> byBlockId = Comparator.comparing(GroupView::sortId);
        Comparator<GroupView> base = switch (sortMode) {
            case SMART -> {
                if (hasSearch) {
                    yield Comparator.comparingInt(GroupView::score).reversed()
                            .thenComparing(Comparator.comparingLong(GroupView::count).reversed())
                            .thenComparing(byBlockId);
                }
                yield Comparator.comparingLong(GroupView::count).reversed().thenComparing(byBlockId);
            }
            case COUNT_DESC -> Comparator.comparingLong(GroupView::count).reversed().thenComparing(byBlockId);
            case COUNT_ASC -> Comparator.comparingLong(GroupView::count).thenComparing(byBlockId);
            case NAME_ASC -> Comparator.comparing(GroupView::displayName).thenComparing(byBlockId);
            case NAME_DESC -> Comparator.comparing(GroupView::displayName).reversed().thenComparing(byBlockId);
            case ID_ASC -> Comparator.comparing(GroupView::blockId).thenComparing(Comparator.comparingLong(GroupView::count).reversed());
            case ID_DESC -> Comparator.comparing(GroupView::blockId).reversed().thenComparing(Comparator.comparingLong(GroupView::count).reversed());
        };
        return Comparator.comparing((GroupView group) -> !group.isolated())
                .thenComparing(base);
    }

    private void toggleExpanded(String groupId) {
        if (expandedBlockIds.contains(groupId)) {
            expandedBlockIds.remove(groupId);
        } else {
            expandedBlockIds.add(groupId);
        }
    }

    private void toggleIsolatedGroup(GroupView group) {
        if (group.isolated()) {
            isolatedBlockIds.removeAll(group.blockIds());
        } else {
            isolatedBlockIds.addAll(group.blockIds());
        }
        persistIsolatedBlocks();
        rebuildVisibleEntries();
        updateIsolationControls();
    }

    private void clearIsolatedBlocks() {
        if (isolatedBlockIds.isEmpty()) {
            return;
        }
        isolatedBlockIds.clear();
        persistIsolatedBlocks();
        rebuildVisibleEntries();
        updateIsolationControls();
    }

    private int score(SelectionBlockSnapshot.BlockEntry entry, List<String> tokens) {
        if (tokens.isEmpty()) {
            return 0;
        }

        String stateId = entry.stateId().toLowerCase(Locale.ROOT);
        String blockId = entry.blockId().toLowerCase(Locale.ROOT);
        String namespace = entry.namespace().toLowerCase(Locale.ROOT);
        String path = entry.path().toLowerCase(Locale.ROOT);
        String display = entry.displayName().toLowerCase(Locale.ROOT);

        int score = 0;
        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }
            if (token.charAt(0) == '#') {
                if (!matchesTag(entry, token)) {
                    return -1;
                }
                score += 200;
                continue;
            }

            int tokenScore = scoreToken(token, stateId, blockId, namespace, path, display);
            if (tokenScore < 0) {
                return -1;
            }
            score += tokenScore;
        }
        return score;
    }

    private static boolean matchesTag(SelectionBlockSnapshot.BlockEntry entry, String token) {
        return switch (token) {
            case "#air" -> entry.air();
            case "#fluid" -> entry.fluid();
            case "#solid" -> entry.opaque() && !entry.air() && !entry.fluid();
            default -> false;
        };
    }

    private static int scoreToken(String token, String stateId, String blockId, String namespace, String path, String display) {
        if (stateId.equals(token) || blockId.equals(token)) {
            return 900;
        }
        if (stateId.startsWith(token) || blockId.startsWith(token)) {
            return 700;
        }
        if (path.startsWith(token)) {
            return 640;
        }
        if (display.startsWith(token)) {
            return 620;
        }
        if (namespace.equals(token)) {
            return 560;
        }
        if (stateId.contains(token) || blockId.contains(token)) {
            return 430;
        }
        if (display.contains(token)) {
            return 360;
        }
        return -1;
    }

    private void setScrollRows(int value) {
        int maxRows = Math.max(0, visibleRows.size() - maxVisibleRows());
        scrollRows = Math.max(0, Math.min(value, maxRows));
    }

    private int maxVisibleRows() {
        return Math.max(1, listHeight() / ROW_HEIGHT);
    }

    private boolean isMouseInsideList(double mouseX, double mouseY) {
        int listX = listX();
        int listY = listY();
        int listWidth = listWidth();
        int listHeight = listHeight();
        return mouseX >= listX && mouseX <= listX + listWidth && mouseY >= listY && mouseY <= listY + listHeight;
    }

    private boolean tryMoveFilterEntryToHidden(double mouseX, double mouseY) {
        String blockId = tryPickFilterEntry(mouseX, mouseY, availableFilterEntries, filterAvailableScrollRows);
        if (blockId == null) {
            return false;
        }
        excludedBlockIds.add(blockId);
        expandedBlockIds.remove(blockId);
        persistExcludedFilters();
        rebuildVisibleEntries();
        return true;
    }

    private boolean tryMoveFilterEntryToVisible(double mouseX, double mouseY) {
        String blockId = tryPickFilterEntry(mouseX, mouseY, hiddenFilterEntries, filterHiddenScrollRows);
        if (blockId == null) {
            return false;
        }
        excludedBlockIds.remove(blockId);
        persistExcludedFilters();
        rebuildVisibleEntries();
        return true;
    }

    private String tryPickFilterEntry(double mouseX, double mouseY, List<FilterEntry> entries, int scrollRows) {
        int listY = filterListY();
        int localY = (int) mouseY - (listY + 1);
        if (localY < 0) {
            return null;
        }

        int rowInView = localY / FILTER_ROW_HEIGHT;
        int absoluteRow = scrollRows + rowInView;
        if (absoluteRow < 0 || absoluteRow >= entries.size()) {
            return null;
        }
        return entries.get(absoluteRow).blockId();
    }

    private void setFiltersMenuOpen(boolean open) {
        filtersMenuOpen = open;
        updateFilterMenuVisibility();
    }

    private void updateFilterMenuVisibility() {
        boolean visible = filtersMenuOpen;
        if (filterSearchField != null) {
            filterSearchField.visible = visible;
            filterSearchField.active = visible;
            if (!visible) {
                filterSearchField.setFocused(false);
            }
        }
        if (clearFiltersButton != null) {
            clearFiltersButton.visible = visible;
            clearFiltersButton.active = visible;
        }
        if (filterButton != null) {
            filterButton.setMessage(filterButtonText());
        }
    }

    private void positionFilterMenuWidgets() {
        if (filterSearchField == null || clearFiltersButton == null) {
            return;
        }

        int x = filterPanelX();
        int y = filterPanelY();
        int height = filterPanelHeight();

        filterSearchField.setX(x + 10);
        filterSearchField.setY(y + 24);
        filterSearchField.setWidth(FILTER_PANEL_WIDTH - 20);

        clearFiltersButton.setX(x + FILTER_PANEL_WIDTH - 74);
        clearFiltersButton.setY(y + height - FILTER_FOOTER_HEIGHT + 6);
    }

    private void setAvailableFilterScrollRows(int value) {
        int maxRows = Math.max(0, availableFilterEntries.size() - maxVisibleFilterRows(filterListHeight()));
        filterAvailableScrollRows = Math.max(0, Math.min(value, maxRows));
    }

    private void setHiddenFilterScrollRows(int value) {
        int maxRows = Math.max(0, hiddenFilterEntries.size() - maxVisibleFilterRows(filterListHeight()));
        filterHiddenScrollRows = Math.max(0, Math.min(value, maxRows));
    }

    private int maxVisibleFilterRows(int listHeight) {
        return Math.max(1, listHeight / FILTER_ROW_HEIGHT);
    }

    private boolean isInsideFilterPanel(double mouseX, double mouseY) {
        int x = filterPanelX();
        int y = filterPanelY();
        int width = FILTER_PANEL_WIDTH;
        int height = filterPanelHeight();
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private boolean isInsideAvailableFilterList(double mouseX, double mouseY) {
        int x = filterAvailableListX();
        int y = filterListY();
        int width = filterAvailableListWidth();
        int height = filterListHeight();
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private boolean isInsideHiddenFilterList(double mouseX, double mouseY) {
        int x = filterHiddenListX();
        int y = filterListY();
        int width = filterHiddenListWidth();
        int height = filterListHeight();
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private static boolean isInsideButton(ButtonWidget button, double mouseX, double mouseY) {
        if (button == null || !button.visible) {
            return false;
        }
        return mouseX >= button.getX()
                && mouseX <= button.getX() + button.getWidth()
                && mouseY >= button.getY()
                && mouseY <= button.getY() + button.getHeight();
    }

    private static boolean isInsideIsolationToggle(double mouseX, double mouseY, int listX, int rowY) {
        return mouseX >= listX + 2
                && mouseX <= listX + 28
                && mouseY >= rowY
                && mouseY <= rowY + ROW_HEIGHT;
    }

    private String trimWithEllipsis(String text, int maxWidth) {
        if (textRenderer.getWidth(text) <= maxWidth) {
            return text;
        }

        String ellipsis = "...";
        int ellipsisWidth = textRenderer.getWidth(ellipsis);
        if (ellipsisWidth >= maxWidth) {
            return "";
        }

        int end = text.length();
        while (end > 0) {
            String candidate = text.substring(0, end) + ellipsis;
            if (textRenderer.getWidth(candidate) <= maxWidth) {
                return candidate;
            }
            end--;
        }
        return ellipsis;
    }

    private long visibleSelectedCellsCount() {
        long sum = 0L;
        for (GroupView group : visibleGroups) {
            sum += group.count();
        }
        return sum;
    }

    private void persistExcludedFilters() {
        excludedFiltersConsumer.accept(Set.copyOf(excludedBlockIds));
    }

    private void persistIsolatedBlocks() {
        isolatedBlocksConsumer.accept(Set.copyOf(isolatedBlockIds));
    }

    private void updateIsolationControls() {
        if (resetIsolationButton != null) {
            resetIsolationButton.active = !isolatedBlockIds.isEmpty();
        }
    }

    private void syncIsolatedBlocksFromController() {
        Set<String> current = BlockIsolationController.selectedBlockIds();
        if (isolatedBlockIds.equals(current)) {
            return;
        }

        isolatedBlockIds.clear();
        isolatedBlockIds.addAll(current);
        rebuildVisibleEntries();
        updateIsolationControls();
    }

    private static String groupIdLine(GroupView group) {
        if (group.blockIds().isEmpty()) {
            return group.sortId();
        }
        String firstId = group.blockIds().get(0);
        int extra = group.blockIds().size() - 1;
        return extra <= 0 ? firstId : firstId + " +" + extra;
    }

    private Text sortButtonText() {
        return Text.translatable(
                "screen.worldedit_selection_overlay.blocks.sort",
                Text.translatable(sortMode.translationKey)
        );
    }

    private Text filterButtonText() {
        return Text.translatable(
                "screen.worldedit_selection_overlay.blocks.filters.button",
                formatLong(excludedBlockIds.size())
        );
    }

    private static String normalizeSearch(String search) {
        return search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
    }

    private static List<String> tokenize(String search) {
        if (search.isEmpty()) {
            return List.of();
        }
        String[] parts = search.split("\\s+");
        ArrayList<String> tokens = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (!part.isEmpty()) {
                tokens.add(part);
            }
        }
        return tokens;
    }

    private static String formatLong(long value) {
        return NUMBER_FORMAT.format(value);
    }

    private static String formatPercent(double value0to1) {
        double percent = Math.max(0.0, Math.min(100.0, value0to1 * 100.0));
        return String.format(Locale.ROOT, "%.2f%%", percent);
    }

    private static void drawOutline(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + 1, color);
        context.fill(x, y + height - 1, x + width, y + height, color);
        context.fill(x, y, x + 1, y + height, color);
        context.fill(x + width - 1, y, x + width, y + height, color);
    }

    private void drawScreenBackdrop(DrawContext context) {
        context.fill(0, 0, width, height, 0xA010131A);
    }

    private int panelWidth() {
        int available = Math.max(420, width - PANEL_MARGIN * 2);
        int adaptive = (int) Math.round(width * 0.72);
        int preferred = Math.min(PANEL_MAX_WIDTH, adaptive);
        int clamped = Math.min(available, preferred);
        return Math.max(Math.min(available, PANEL_MIN_WIDTH), clamped);
    }

    private int panelX() {
        return (width - panelWidth()) / 2;
    }

    private int panelY() {
        return PANEL_MARGIN;
    }

    private int panelHeight() {
        return height - PANEL_MARGIN * 2;
    }

    private int listX() {
        return panelX() + 10;
    }

    private int listY() {
        return panelY() + HEADER_HEIGHT;
    }

    private int listWidth() {
        return panelWidth() - 20;
    }

    private int listHeight() {
        return panelHeight() - HEADER_HEIGHT - FOOTER_HEIGHT;
    }

    private int filterPanelX() {
        return panelX() + panelWidth() - FILTER_PANEL_WIDTH - 10;
    }

    private int filterPanelY() {
        return panelY() + 52;
    }

    private int filterPanelHeight() {
        int maxByScreen = Math.max(170, panelHeight() - 64);
        return Math.min(FILTER_PANEL_MAX_HEIGHT, maxByScreen);
    }

    private int filterListY() {
        return filterPanelY() + FILTER_HEADER_HEIGHT;
    }

    private int filterAvailableListX() {
        return filterPanelX() + 10;
    }

    private int filterAvailableListWidth() {
        int totalInner = FILTER_PANEL_WIDTH - 20;
        return (totalInner - FILTER_COLUMNS_GAP) / 2;
    }

    private int filterHiddenListX() {
        return filterAvailableListX() + filterAvailableListWidth() + FILTER_COLUMNS_GAP;
    }

    private int filterHiddenListWidth() {
        int totalInner = FILTER_PANEL_WIDTH - 20;
        return totalInner - filterAvailableListWidth() - FILTER_COLUMNS_GAP;
    }

    private int filterListHeight() {
        return filterPanelHeight() - FILTER_HEADER_HEIGHT - FILTER_FOOTER_HEIGHT;
    }

    private record StateView(
            SelectionBlockSnapshot.BlockEntry entry,
            int score,
            String primaryLabel,
            String secondaryLabel
    ) {
    }

    private record GroupView(
            String groupId,
            String sortId,
            String blockId,
            String displayName,
            boolean air,
            long count,
            int score,
            boolean isolated,
            SelectionBlockSnapshot.BlockEntry representative,
            List<StateView> states,
            List<String> blockIds
    ) {
        private boolean expandable() {
            return states.size() > 1;
        }
    }

    private record FilterEntry(
            String blockId,
            String displayName,
            boolean air,
            boolean fluid,
            long count
    ) {
    }

    private static final class GroupBuilder {
        private final String groupId;
        private final String displayName;
        private final boolean air;
        private final HashSet<String> blockIds = new HashSet<>();
        private final ArrayList<StateView> states = new ArrayList<>();

        private SelectionBlockSnapshot.BlockEntry representative;
        private long count;
        private long representativeCount;
        private int bestScore = Integer.MIN_VALUE;

        private GroupBuilder(String groupId, SelectionBlockSnapshot.BlockEntry firstEntry) {
            this.groupId = groupId;
            this.displayName = resolveGroupDisplayName(firstEntry);
            this.air = firstEntry.air();
            this.representative = firstEntry;
            this.representativeCount = Long.MIN_VALUE;
        }

        private void add(SelectionBlockSnapshot.BlockEntry entry, int score) {
            BlockStateLabelFormatter.StateLabel label = BlockStateLabelFormatter.format(entry.stateId());
            states.add(new StateView(entry, score, label.primary(), label.secondary()));
            blockIds.add(entry.blockId());
            count += entry.count();
            if (entry.count() > representativeCount) {
                representative = entry;
                representativeCount = entry.count();
            }
            bestScore = Math.max(bestScore, score);
        }

        private GroupView build(boolean hasSearch, Set<String> isolatedBlockIds) {
            states.sort(stateComparator(hasSearch));
            ArrayList<String> sortedBlockIds = new ArrayList<>(blockIds);
            Collections.sort(sortedBlockIds);
            String sortId = sortedBlockIds.isEmpty() ? representative.blockId() : sortedBlockIds.get(0);
            return new GroupView(
                    groupId,
                    sortId,
                    sortId,
                    displayName,
                    air,
                    count,
                    bestScore == Integer.MIN_VALUE ? 0 : bestScore,
                    containsAny(isolatedBlockIds, sortedBlockIds),
                    representative,
                    List.copyOf(states),
                    List.copyOf(sortedBlockIds)
            );
        }

        private static Comparator<StateView> stateComparator(boolean hasSearch) {
            Comparator<StateView> byState = Comparator.comparing(v -> v.entry().stateId());
            if (hasSearch) {
                return Comparator.comparingInt(StateView::score).reversed()
                        .thenComparing(Comparator.comparingLong((StateView v) -> v.entry().count()).reversed())
                        .thenComparing(byState);
            }
            return Comparator.comparingLong((StateView v) -> v.entry().count()).reversed()
                    .thenComparing(byState);
        }
    }

    private interface RowItem {
    }

    private record GroupRow(GroupView group) implements RowItem {
    }

    private record StateRow(GroupView group, StateView state) implements RowItem {
    }

    private enum SortMode {
        SMART("screen.worldedit_selection_overlay.blocks.sort.smart"),
        COUNT_DESC("screen.worldedit_selection_overlay.blocks.sort.count_desc"),
        COUNT_ASC("screen.worldedit_selection_overlay.blocks.sort.count_asc"),
        NAME_ASC("screen.worldedit_selection_overlay.blocks.sort.name_asc"),
        NAME_DESC("screen.worldedit_selection_overlay.blocks.sort.name_desc"),
        ID_ASC("screen.worldedit_selection_overlay.blocks.sort.id_asc"),
        ID_DESC("screen.worldedit_selection_overlay.blocks.sort.id_desc");

        private final String translationKey;

        SortMode(String translationKey) {
            this.translationKey = translationKey;
        }

        private SortMode next() {
            int nextOrdinal = ordinal() + 1;
            if (nextOrdinal >= values().length) {
                nextOrdinal = 0;
            }
            return values()[nextOrdinal];
        }
    }
}
