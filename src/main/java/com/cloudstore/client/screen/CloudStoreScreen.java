package com.cloudstore.client.screen;

import com.cloudstore.client.CloudStoreClient;
import com.cloudstore.client.api.CloudStoreModels;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CloudStoreScreen extends Screen {
    private static final int TILE_SIZE = 34;
    private static final int TILE_STEP = 34;
    private static final float ITEM_SCALE = 1.25f;
    private static final int TAB_SIZE = 24;
    private static final int TAB_STEP = 26;

    private final Screen parent;
    private final List<CloudStoreModels.Item> items = new ArrayList<>();
    private final Map<String, Long> selected = new LinkedHashMap<>();
    private final List<Category> categories = new ArrayList<>();
    private CloudStoreModels.MeResponse me;
    private String ownerUuid = "";
    private int ownerIndex;
    private int selectedCategory;
    private int scroll;
    private int gridY;
    private int gridBottom;
    private boolean initialized;
    private boolean busy;
    private boolean submitting;
    private String status = "";
    private String savedSearch = "";
    private String savedTarget = "";
    private TextFieldWidget searchField;
    private TextFieldWidget targetField;
    private ButtonWidget ownerButton;
    private ButtonWidget depositButton;
    private ButtonWidget refreshButton;
    private ButtonWidget submitButton;
    private ButtonWidget clearButton;

    public CloudStoreScreen(Screen parent) {
        super(Text.literal("云仓库"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (searchField != null) savedSearch = searchField.getText();
        if (targetField != null) savedTarget = targetField.getText();

        int searchWidth = Math.min(180, Math.max(100, this.width / 4));
        searchField = new TextFieldWidget(this.textRenderer, 10, 28, searchWidth, 20, Text.literal("搜索物品"));
        searchField.setPlaceholder(Text.literal("搜索名称 / ID / 短码"));
        searchField.setMaxLength(80);
        searchField.setText(savedSearch);
        searchField.setChangedListener(value -> {
            savedSearch = value;
            scroll = 0;
        });
        this.addDrawableChild(searchField);

        int ownerX = 16 + searchWidth;
        int rightToolbarWidth = 106;
        int ownerWidth = Math.max(70, Math.min(130, this.width - ownerX - rightToolbarWidth - 16));
        ownerButton = ButtonWidget.builder(Text.literal("选择仓库"), button -> cycleOwner())
            .dimensions(ownerX, 28, ownerWidth, 20).build();
        this.addDrawableChild(ownerButton);
        refreshButton = ButtonWidget.builder(Text.literal("刷新"), button -> reload())
            .dimensions(this.width - 106, 28, 48, 20).build();
        this.addDrawableChild(refreshButton);
        depositButton = ButtonWidget.builder(Text.literal("存入"), button -> deposit())
            .dimensions(this.width - 52, 28, 42, 20).build();
        this.addDrawableChild(depositButton);

        int bottomY = this.height - 27;
        int actionWidth = this.width < 430 ? 78 : 96;
        int targetWidth = Math.min(150, Math.max(86, this.width - actionWidth - 170));
        targetField = new TextFieldWidget(this.textRenderer, 10, bottomY, targetWidth, 20, Text.literal("领取玩家"));
        targetField.setPlaceholder(Text.literal("留空=自己；可填代取 ID"));
        targetField.setMaxLength(16);
        targetField.setText(savedTarget);
        targetField.setChangedListener(value -> savedTarget = value);
        this.addDrawableChild(targetField);

        int closeX = this.width - 44;
        int settingsX = closeX - 52;
        int submitX = settingsX - actionWidth - 6;
        int clearX = submitX - 48 - 6;
        clearButton = ButtonWidget.builder(Text.literal("清空"), button -> clearSelection())
            .dimensions(clearX, bottomY, 48, 20).build();
        this.addDrawableChild(clearButton);
        submitButton = ButtonWidget.builder(Text.literal("提交取货"), button -> submitWithdraw())
            .dimensions(submitX, bottomY, actionWidth, 20).build();
        this.addDrawableChild(submitButton);
        this.addDrawableChild(ButtonWidget.builder(Text.literal("设置"), button -> CloudStoreClient.openConfig(this.client, this))
            .dimensions(settingsX, bottomY, 46, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("关闭"), button -> close())
            .dimensions(closeX, bottomY, 34, 20).build());

        if (!initialized) {
            initialized = true;
            restoreCachedData();
            load();
        } else {
            updateButtons();
        }
    }

    public void setSelectedAmount(CloudStoreModels.Item item, long amount) {
        if (amount <= 0) selected.remove(item.itemKey);
        else selected.put(item.itemKey, Math.min(amount, item.amount));
        updateButtons();
    }

    public void reload() {
        load();
    }

    private String endpoint() {
        return CloudStoreClient.config.apiBaseUrl;
    }

    private void restoreCachedData() {
        CloudStoreModels.MeResponse cachedMe = CloudStoreClient.cache.me(endpoint());
        if (cachedMe == null) return;
        me = cachedMe;
        ownerUuid = resolveOwner(cachedMe, ownerUuid);
        CloudStoreModels.InventoryResponse cachedInventory = CloudStoreClient.cache.inventory(endpoint(), ownerUuid);
        if (cachedInventory != null) {
            applyInventory(cachedInventory);
            status = "已显示缓存，正在后台刷新...";
        }
        updateButtons();
    }

    private void load() {
        if (busy) return;
        busy = true;
        if (items.isEmpty()) status = "正在连接云仓库...";
        else status = "正在后台刷新...";
        updateButtons();
        String preferredOwner = ownerUuid;
        CloudStoreClient.api.connect().thenCompose(response -> {
            CloudStoreClient.cache.putMe(endpoint(), response);
            String resolvedOwner = resolveOwner(response, preferredOwner);
            return CloudStoreClient.api.inventory(resolvedOwner)
                .thenApply(inventory -> new LoadResult(response, resolvedOwner, inventory));
        }).whenComplete((result, error) -> MinecraftClient.getInstance().execute(() -> {
            busy = false;
            if (error != null) {
                status = message(error);
                updateButtons();
                return;
            }
            me = result.me;
            ownerUuid = result.ownerUuid;
            CloudStoreClient.cache.putInventory(endpoint(), ownerUuid, result.inventory);
            applyInventory(result.inventory);
            status = "";
            updateButtons();
        }));
    }

    private String resolveOwner(CloudStoreModels.MeResponse response, String preferredOwner) {
        if (response.owners == null || response.owners.isEmpty()) {
            ownerIndex = 0;
            return response.user == null ? "" : response.user.uuid;
        }
        for (int i = 0; i < response.owners.size(); i++) {
            CloudStoreModels.Owner owner = response.owners.get(i);
            if (owner.ownerUuid.equals(preferredOwner)) {
                ownerIndex = i;
                return owner.ownerUuid;
            }
        }
        for (int i = 0; i < response.owners.size(); i++) {
            CloudStoreModels.Owner owner = response.owners.get(i);
            if (owner.ownerUuid.equals(response.defaultOwnerUuid)) {
                ownerIndex = i;
                return owner.ownerUuid;
            }
        }
        ownerIndex = 0;
        return response.owners.getFirst().ownerUuid;
    }

    private void cycleOwner() {
        if (me == null || me.owners == null || me.owners.isEmpty()) return;
        ownerIndex = (ownerIndex + 1) % me.owners.size();
        ownerUuid = me.owners.get(ownerIndex).ownerUuid;
        selected.clear();
        scroll = 0;
        CloudStoreModels.InventoryResponse cached = CloudStoreClient.cache.inventory(endpoint(), ownerUuid);
        if (cached != null) applyInventory(cached);
        else items.clear();
        loadInventoryOnly();
    }

    private void loadInventoryOnly() {
        if (busy || ownerUuid.isBlank()) return;
        busy = true;
        status = items.isEmpty() ? "正在读取库存..." : "已显示缓存，正在后台刷新...";
        updateButtons();
        String requestedOwner = ownerUuid;
        CloudStoreClient.api.inventory(requestedOwner).whenComplete((response, error) -> {
            if (error == null) CloudStoreClient.cache.putInventory(endpoint(), requestedOwner, response);
            MinecraftClient.getInstance().execute(() -> {
                busy = false;
                if (error != null) status = message(error);
                else if (requestedOwner.equals(ownerUuid)) {
                    applyInventory(response);
                    status = "";
                }
                updateButtons();
            });
        });
    }

    private void applyInventory(CloudStoreModels.InventoryResponse response) {
        items.clear();
        items.addAll(flattenInventory(response.items));
        items.sort(Comparator.comparing(item -> safe(item.displayName), String.CASE_INSENSITIVE_ORDER));
        Map<String, Long> available = new HashMap<>();
        for (CloudStoreModels.Item item : items) available.put(item.itemKey, item.amount);
        selected.entrySet().removeIf(entry -> !available.containsKey(entry.getKey()));
        selected.replaceAll((key, amount) -> Math.min(amount, available.getOrDefault(key, amount)));
        rebuildCategories();
        scroll = Math.max(0, scroll);
    }

    private List<CloudStoreModels.Item> flattenInventory(List<CloudStoreModels.Item> source) {
        Map<String, CloudStoreModels.Item> flattened = new LinkedHashMap<>();
        for (CloudStoreModels.Item row : source == null ? List.<CloudStoreModels.Item>of() : source) {
            if (isShulkerBox(row.itemId)) {
                for (CloudStoreModels.Item content : row.shulkerContents == null ? List.<CloudStoreModels.Item>of() : row.shulkerContents) {
                    mergeFlattened(flattened, content, 0, safeMultiply(content.amount, row.amount));
                }
            } else {
                mergeFlattened(flattened, row, row.amount, 0);
            }
        }
        return new ArrayList<>(flattened.values());
    }

    private void mergeFlattened(Map<String, CloudStoreModels.Item> rows, CloudStoreModels.Item source, long loose, long boxed) {
        String key = safe(source.itemKey).isBlank() ? safe(source.itemId) : source.itemKey;
        CloudStoreModels.Item target = rows.computeIfAbsent(key, ignored -> copyItem(source));
        target.looseAmount += loose;
        target.boxedAmount += boxed;
        target.amount = target.looseAmount + target.boxedAmount;
    }

    private CloudStoreModels.Item copyItem(CloudStoreModels.Item source) {
        CloudStoreModels.Item copy = new CloudStoreModels.Item();
        copy.itemKey = safe(source.itemKey);
        copy.itemId = safe(source.itemId);
        copy.displayName = safe(source.displayName).isBlank() ? copy.itemId : source.displayName;
        copy.baseDisplayName = safe(source.baseDisplayName);
        copy.shortCode = safe(source.shortCode);
        return copy;
    }

    private long safeMultiply(long left, long right) {
        if (left <= 0 || right <= 0) return 0;
        if (left > Long.MAX_VALUE / right) return Long.MAX_VALUE;
        return left * right;
    }

    private boolean isShulkerBox(String itemId) {
        return safe(itemId).endsWith("shulker_box");
    }

    private void rebuildCategories() {
        categories.clear();
        categories.add(new Category(Text.literal("全部"), new ItemStack(Items.CHEST), Set.of(), true));
        try {
            if (this.client != null && this.client.world != null && this.client.getNetworkHandler() != null) {
                ItemGroups.updateDisplayContext(
                    this.client.getNetworkHandler().getEnabledFeatures(),
                    false,
                    this.client.world.getRegistryManager()
                );
            }
            for (ItemGroup group : ItemGroups.getGroupsToDisplay()) {
                try {
                    Set<Item> members = new HashSet<>();
                    for (ItemStack stack : group.getDisplayStacks()) members.add(stack.getItem());
                    categories.add(new Category(group.getDisplayName(), group.getIcon(), members, false));
                } catch (RuntimeException error) {
                    CloudStoreClient.LOGGER.warn("Unable to build creative category {}: {}", group.getDisplayName().getString(), error.getMessage());
                }
            }
        } catch (RuntimeException error) {
            CloudStoreClient.LOGGER.warn("Unable to build creative categories: {}", error.getMessage());
        }
        selectedCategory = Math.min(selectedCategory, Math.max(0, categories.size() - 1));
    }

    private void deposit() {
        if (submitting || ownerUuid.isBlank()) return;
        submitting = true;
        status = "正在提交存储...";
        updateButtons();
        CloudStoreClient.api.deposit(ownerUuid).whenComplete((response, error) -> MinecraftClient.getInstance().execute(() -> {
            submitting = false;
            if (error != null) status = message(error);
            else {
                status = response.message;
                CloudStoreClient.showStatus(response.message);
            }
            updateButtons();
        }));
    }

    private void submitWithdraw() {
        if (submitting || selected.isEmpty() || me == null || me.user == null) return;
        String target = targetField.getText().trim();
        if (target.isEmpty()) target = me.user.username;
        if (!target.matches("[A-Za-z0-9_]{3,16}")) {
            status = "领取玩家 ID 必须是 3-16 位字母、数字或下划线";
            return;
        }
        Map<String, CloudStoreModels.Item> byKey = new HashMap<>();
        for (CloudStoreModels.Item item : items) byKey.put(item.itemKey, item);
        List<CloudStoreModels.WithdrawRequest> requests = new ArrayList<>();
        for (Map.Entry<String, Long> entry : selected.entrySet()) {
            CloudStoreModels.Item item = byKey.get(entry.getKey());
            if (item != null && entry.getValue() > 0) requests.add(new CloudStoreModels.WithdrawRequest(item, entry.getValue()));
        }
        if (requests.isEmpty()) return;
        submitting = true;
        status = "正在提交 " + requests.size() + " 种物品...";
        updateButtons();
        CloudStoreClient.api.withdrawMany(ownerUuid, requests, target).whenComplete((response, error) -> MinecraftClient.getInstance().execute(() -> {
            submitting = false;
            if (error != null) status = message(error);
            else {
                selected.clear();
                status = response.message;
                CloudStoreClient.showStatus(response.message);
            }
            updateButtons();
        }));
    }

    private void clearSelection() {
        selected.clear();
        updateButtons();
    }

    private void updateButtons() {
        if (ownerButton != null) {
            String label = "选择仓库";
            if (me != null && me.owners != null && ownerIndex >= 0 && ownerIndex < me.owners.size()) {
                label = me.owners.get(ownerIndex).label;
            }
            ownerButton.setMessage(Text.literal(label));
            ownerButton.active = me != null && me.owners != null && me.owners.size() > 1 && !submitting;
        }
        if (refreshButton != null) refreshButton.active = !busy && !submitting;
        if (depositButton != null) depositButton.active = !ownerUuid.isBlank() && !submitting;
        if (submitButton != null) {
            submitButton.active = !selected.isEmpty() && !submitting;
            submitButton.setMessage(Text.literal(submitting ? "提交中..." : "提交取货"));
        }
        if (clearButton != null) clearButton.active = !selected.isEmpty() && !submitting;
    }

    private List<CloudStoreModels.Item> visibleItems() {
        String query = searchField == null ? savedSearch : searchField.getText();
        String normalized = query.trim().toLowerCase();
        Category category = categories.isEmpty() ? null : categories.get(Math.min(selectedCategory, categories.size() - 1));
        return items.stream()
            .filter(item -> normalized.isEmpty() || (safe(item.displayName) + " " + safe(item.itemId) + " " + safe(item.shortCode)).toLowerCase().contains(normalized))
            .filter(item -> category == null || category.all || category.members.contains(itemStack(item.itemId).getItem()))
            .toList();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xF0101116);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFF);
        if (me != null && me.user != null) context.drawTextWithShadow(this.textRenderer, Text.literal(me.user.username), 10, 10, 0x9AA5B1);

        int tabColumns = Math.max(1, (this.width - 20) / TAB_STEP);
        int tabRows = Math.max(1, (int) Math.ceil((double) Math.max(1, categories.size()) / tabColumns));
        int tabY = 55;
        Text tooltip = null;
        for (int i = 0; i < categories.size(); i++) {
            int x = 10 + (i % tabColumns) * TAB_STEP;
            int y = tabY + (i / tabColumns) * TAB_STEP;
            boolean active = i == selectedCategory;
            boolean hovered = mouseX >= x && mouseX < x + TAB_SIZE && mouseY >= y && mouseY < y + TAB_SIZE;
            context.fill(x, y, x + TAB_SIZE, y + TAB_SIZE, active ? 0xFF356A52 : hovered ? 0xFF354252 : 0xFF242A33);
            ItemStack icon = categories.get(i).icon;
            if (icon != null && !icon.isEmpty()) context.drawItem(icon, x + 4, y + 4);
            if (hovered) tooltip = categories.get(i).label;
        }

        gridY = tabY + tabRows * TAB_STEP + 4;
        gridBottom = this.height - 51;
        List<CloudStoreModels.Item> visible = visibleItems();
        int columns = Math.max(1, (this.width - 20) / TILE_STEP);
        int rows = Math.max(1, (gridBottom - gridY) / TILE_STEP);
        int maxScroll = Math.max(0, (int) Math.ceil((double) visible.size() / columns) - rows);
        scroll = Math.min(scroll, maxScroll);
        int start = scroll * columns;
        int x0 = Math.max(10, (this.width - columns * TILE_STEP) / 2);
        for (int i = 0; i < rows * columns && start + i < visible.size(); i++) {
            CloudStoreModels.Item item = visible.get(start + i);
            int x = x0 + (i % columns) * TILE_STEP;
            int y = gridY + (i / columns) * TILE_STEP;
            boolean hovered = mouseX >= x && mouseX < x + TILE_SIZE && mouseY >= y && mouseY < y + TILE_SIZE;
            long selectedAmount = selected.getOrDefault(item.itemKey, 0L);
            int background = selectedAmount > 0 ? 0xFF315C49 : hovered ? 0xFF354252 : 0xFF242A33;
            context.fill(x, y, x + TILE_SIZE, y + TILE_SIZE, background);
            if (selectedAmount > 0) {
                context.fill(x, y, x + TILE_SIZE, y + 2, 0xFF69D69A);
                context.fill(x, y + TILE_SIZE - 2, x + TILE_SIZE, y + TILE_SIZE, 0xFF69D69A);
            }
            ItemStack stack = itemStack(item.itemId);
            if (!stack.isEmpty()) {
                var matrices = context.getMatrices();
                matrices.pushMatrix();
                matrices.translate(x + TILE_SIZE / 2.0f, y + TILE_SIZE / 2.0f);
                matrices.scale(ITEM_SCALE, ITEM_SCALE);
                matrices.translate(-8.0f, -8.0f);
                context.drawItem(stack, 0, 0);
                matrices.popMatrix();
            }
            String amount = compact(item.amount);
            if (!stack.isEmpty()) context.drawStackOverlay(this.textRenderer, stack, x + 17, y + 17, amount);
            if (selectedAmount > 0) {
                String selectedText = compact(selectedAmount);
                context.drawTextWithShadow(this.textRenderer, selectedText, x + 1, y, 0x9FFFC5);
            }
            if (hovered) {
                tooltip = Text.literal(item.displayName + "  可取 " + item.amount + availabilitySuffix(item));
            }
        }

        if (visible.isEmpty() && status.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("此分类没有匹配物品"), this.width / 2, (gridY + gridBottom) / 2, 0xAAB4C0);
        }

        int bottomY = this.height - 27;
        context.drawTextWithShadow(this.textRenderer, Text.literal("领取玩家"), 10, bottomY - 11, 0xC5CED8);
        long totalSelected = selected.values().stream().mapToLong(Long::longValue).sum();
        String summary = "已选 " + selected.size() + " 种 / " + totalSelected + " 件";
        int summaryX = targetField.getX() + targetField.getWidth() + 8;
        int maxSummaryWidth = Math.max(0, clearButton.getX() - summaryX - 8);
        if (maxSummaryWidth > 40) {
            context.drawTextWithShadow(this.textRenderer, this.textRenderer.trimToWidth(summary, maxSummaryWidth), summaryX, bottomY + 6, 0xD4D9E0);
        }
        if (!status.isEmpty()) {
            String displayStatus = this.textRenderer.trimToWidth(status, Math.max(80, this.width - 20));
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(displayStatus), this.width / 2, gridBottom + 3, 0xF1B36B);
        }

        super.render(context, mouseX, mouseY, delta);
        if (tooltip != null) context.drawTooltip(this.textRenderer, tooltip, mouseX, mouseY);
    }

    private String availabilitySuffix(CloudStoreModels.Item item) {
        if (item.boxedAmount <= 0) return "（散装）";
        if (item.looseAmount <= 0) return "（盒内）";
        return "（散装 " + item.looseAmount + " + 盒内 " + item.boxedAmount + "）";
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        if (click.button() == 0) {
            int tabColumns = Math.max(1, (this.width - 20) / TAB_STEP);
            if (mouseY >= 55 && mouseY < gridY && mouseX >= 10) {
                int column = (int) ((mouseX - 10) / TAB_STEP);
                int row = (int) ((mouseY - 55) / TAB_STEP);
                int localX = (int) (mouseX - 10) % TAB_STEP;
                int localY = (int) (mouseY - 55) % TAB_STEP;
                int index = row * tabColumns + column;
                if (localX < TAB_SIZE && localY < TAB_SIZE && index >= 0 && index < categories.size()) {
                    selectedCategory = index;
                    scroll = 0;
                    return true;
                }
            }
            if (mouseY >= gridY && mouseY < gridBottom) {
                List<CloudStoreModels.Item> visible = visibleItems();
                int columns = Math.max(1, (this.width - 20) / TILE_STEP);
                int x0 = Math.max(10, (this.width - columns * TILE_STEP) / 2);
                if (mouseX >= x0) {
                    int localX = (int) (mouseX - x0);
                    int localY = (int) (mouseY - gridY);
                    int column = localX / TILE_STEP;
                    int row = localY / TILE_STEP;
                    int index = scroll * columns + row * columns + column;
                    if (localX % TILE_STEP < TILE_SIZE && localY % TILE_STEP < TILE_SIZE && index >= 0 && index < visible.size()) {
                        CloudStoreModels.Item item = visible.get(index);
                        this.client.setScreen(new WithdrawScreen(this, item, selected.getOrDefault(item.itemKey, 0L)));
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseY < gridY || mouseY >= gridBottom) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        int columns = Math.max(1, (this.width - 20) / TILE_STEP);
        int rows = Math.max(1, (gridBottom - gridY) / TILE_STEP);
        int maxScroll = Math.max(0, (int) Math.ceil((double) visibleItems().size() / columns) - rows);
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(verticalAmount)));
        return true;
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(parent);
    }

    private ItemStack itemStack(String id) {
        try {
            Identifier identifier = Identifier.tryParse(id);
            return identifier == null ? ItemStack.EMPTY : new ItemStack(Registries.ITEM.get(identifier));
        } catch (RuntimeException ignored) {
            return ItemStack.EMPTY;
        }
    }

    private String compact(long amount) {
        if (amount >= 1_000_000) return formatDecimal(amount, 1_000_000, "m");
        if (amount >= 10_000) return formatDecimal(amount, 1_000, "k");
        return String.valueOf(amount);
    }

    private String formatDecimal(long amount, long divisor, String suffix) {
        long whole = amount / divisor;
        if (whole >= 100) return whole + suffix;
        long decimal = (amount % divisor) * 10 / divisor;
        return decimal == 0 ? whole + suffix : whole + "." + decimal + suffix;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String message(Throwable error) {
        Throwable current = error;
        while ((current instanceof java.util.concurrent.CompletionException
            || current instanceof java.util.concurrent.ExecutionException) && current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? "云仓库请求失败" : current.getMessage();
    }

    private record Category(Text label, ItemStack icon, Set<Item> members, boolean all) {
    }

    private record LoadResult(CloudStoreModels.MeResponse me, String ownerUuid, CloudStoreModels.InventoryResponse inventory) {
    }
}
