package com.cloudstore.client.screen;

import com.cloudstore.client.api.CloudStoreModels;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class WithdrawScreen extends Screen {
    private final CloudStoreScreen parent;
    private final CloudStoreModels.Item item;
    private final long selectedAmount;
    private EditBox amountField;
    private String status = "";
    private int titleY;

    public WithdrawScreen(CloudStoreScreen parent, CloudStoreModels.Item item, long selectedAmount) {
        super(Component.literal("设置取货数量"));
        this.parent = parent;
        this.item = item;
        this.selectedAmount = selectedAmount;
    }

    @Override
    protected void init() {
        int center = this.width / 2;
        titleY = Math.max(12, Math.min(46, (this.height - 150) / 2));
        int fieldY = titleY + 70;
        amountField = new EditBox(this.font, center - 100, fieldY, 146, 20, Component.literal("取货数量")) {
            // EditBox has no text predicate in 26.2; keep the digits-only input
            // check (previously setTextPredicate) by filtering insertions here,
            // which covers both typing and clipboard paste.
            @Override
            public void insertText(String input) {
                StringBuilder digits = new StringBuilder();
                for (int i = 0; i < input.length(); i++) {
                    char character = input.charAt(i);
                    if (Character.isDigit(character)) digits.append(character);
                }
                super.insertText(digits.toString());
            }
        };
        amountField.setHint(Component.literal("输入 1 - " + item.amount));
        amountField.setValue(String.valueOf(selectedAmount > 0 ? selectedAmount : 1));
        amountField.setMaxLength(12);
        this.addRenderableWidget(amountField);
        this.addRenderableWidget(Button.builder(Component.literal("全部"), button -> amountField.setValue(String.valueOf(item.amount)))
            .bounds(center + 52, fieldY, 48, 20).build());

        int actionY = fieldY + 32;
        this.addRenderableWidget(Button.builder(Component.literal("加入取货清单"), button -> addToCart())
            .bounds(center - 100, actionY, 96, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("移除"), button -> removeFromCart())
            .bounds(center + 2, actionY, 46, 20).build()).active = selectedAmount > 0;
        this.addRenderableWidget(Button.builder(Component.literal("取消"), button -> onClose())
            .bounds(center + 54, actionY, 46, 20).build());
        this.setInitialFocus(amountField);
    }

    private void addToCart() {
        long amount;
        try {
            amount = Long.parseLong(amountField.getValue().trim());
        } catch (NumberFormatException error) {
            status = "请输入有效数量";
            return;
        }
        if (amount <= 0 || amount > item.amount) {
            status = "可取数量为 1 - " + item.amount;
            return;
        }
        parent.setSelectedAmount(item, amount);
        onClose();
    }

    private void removeFromCart() {
        parent.setSelectedAmount(item, 0);
        onClose();
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        extractor.fill(0, 0, this.width, this.height, 0xED101116);
        extractor.centeredText(this.font, this.title, this.width / 2, titleY, 0xFFFFFF | 0xFF000000);
        ItemStack stack = itemStack(item.itemId);
        if (!stack.isEmpty()) extractor.item(stack, this.width / 2 - 8, titleY + 18);
        extractor.centeredText(this.font, Component.literal(item.displayName), this.width / 2, titleY + 40, 0xE5E7EB | 0xFF000000);
        extractor.centeredText(this.font,
            Component.literal("可取 " + item.amount + availabilitySuffix()), this.width / 2, titleY + 51, 0xAAB4C0 | 0xFF000000);
        extractor.text(this.font, Component.literal("取货数量"), this.width / 2 - 100, titleY + 59, 0xC5CED8 | 0xFF000000, true);
        if (!status.isEmpty()) {
            extractor.centeredText(this.font, Component.literal(status), this.width / 2, titleY + 126, 0xF1B36B | 0xFF000000);
        }
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    }

    private String availabilitySuffix() {
        if (item.boxedAmount <= 0) return "（散装）";
        if (item.looseAmount <= 0) return "（潜影盒内）";
        return "（散装 " + item.looseAmount + " + 盒内 " + item.boxedAmount + "）";
    }

    private ItemStack itemStack(String id) {
        try {
            Identifier identifier = Identifier.tryParse(id);
            if (identifier == null) return ItemStack.EMPTY;
            Item item = BuiltInRegistries.ITEM.getValue(identifier);
            return item == null ? ItemStack.EMPTY : new ItemStack(item);
        } catch (RuntimeException ignored) {
            return ItemStack.EMPTY;
        }
    }
}
