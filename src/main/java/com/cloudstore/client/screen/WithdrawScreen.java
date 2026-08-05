package com.cloudstore.client.screen;

import com.cloudstore.client.api.CloudStoreModels;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
        amountField = new EditBox(this.font, center - 100, fieldY, 146, 20, Component.literal("取货数量"));
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
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xED101116);
        context.centeredText(this.font, this.title, this.width / 2, titleY, 0xFFFFFF);
        ItemStack stack = itemStack(item.itemId);
        if (!stack.isEmpty()) context.item(stack, this.width / 2 - 8, titleY + 18);
        context.centeredText(this.font, Component.literal(item.displayName), this.width / 2, titleY + 40, 0xE5E7EB);
        context.centeredText(this.font,
            Component.literal("可取 " + item.amount + availabilitySuffix()), this.width / 2, titleY + 51, 0xAAB4C0);
        context.text(this.font, Component.literal("取货数量"), this.width / 2 - 100, titleY + 59, 0xC5CED8, true);
        if (!status.isEmpty()) {
            context.centeredText(this.font, Component.literal(status), this.width / 2, titleY + 126, 0xF1B36B);
        }
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private String availabilitySuffix() {
        if (item.boxedAmount <= 0) return "（散装）";
        if (item.looseAmount <= 0) return "（潜影盒内）";
        return "（散装 " + item.looseAmount + " + 盒内 " + item.boxedAmount + "）";
    }

    private ItemStack itemStack(String id) {
        try {
            Identifier identifier = Identifier.tryParse(id);
            return identifier == null ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.getValue(identifier));
        } catch (RuntimeException ignored) {
            return ItemStack.EMPTY;
        }
    }
}
