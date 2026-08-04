package com.cloudstore.client.screen;

import com.cloudstore.client.api.CloudStoreModels;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class WithdrawScreen extends Screen {
    private final CloudStoreScreen parent;
    private final CloudStoreModels.Item item;
    private final long selectedAmount;
    private TextFieldWidget amountField;
    private String status = "";
    private int titleY;

    public WithdrawScreen(CloudStoreScreen parent, CloudStoreModels.Item item, long selectedAmount) {
        super(Text.literal("设置取货数量"));
        this.parent = parent;
        this.item = item;
        this.selectedAmount = selectedAmount;
    }

    @Override
    protected void init() {
        int center = this.width / 2;
        titleY = Math.max(12, Math.min(46, (this.height - 150) / 2));
        int fieldY = titleY + 70;
        amountField = new TextFieldWidget(this.textRenderer, center - 100, fieldY, 146, 20, Text.literal("取货数量"));
        amountField.setPlaceholder(Text.literal("输入 1 - " + item.amount));
        amountField.setText(String.valueOf(selectedAmount > 0 ? selectedAmount : 1));
        amountField.setMaxLength(12);
        amountField.setTextPredicate(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        this.addDrawableChild(amountField);
        this.addDrawableChild(ButtonWidget.builder(Text.literal("全部"), button -> amountField.setText(String.valueOf(item.amount)))
            .dimensions(center + 52, fieldY, 48, 20).build());

        int actionY = fieldY + 32;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("加入取货清单"), button -> addToCart())
            .dimensions(center - 100, actionY, 96, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("移除"), button -> removeFromCart())
            .dimensions(center + 2, actionY, 46, 20).build()).active = selectedAmount > 0;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("取消"), button -> close())
            .dimensions(center + 54, actionY, 46, 20).build());
        this.setInitialFocus(amountField);
    }

    private void addToCart() {
        long amount;
        try {
            amount = Long.parseLong(amountField.getText().trim());
        } catch (NumberFormatException error) {
            status = "请输入有效数量";
            return;
        }
        if (amount <= 0 || amount > item.amount) {
            status = "可取数量为 1 - " + item.amount;
            return;
        }
        parent.setSelectedAmount(item, amount);
        close();
    }

    private void removeFromCart() {
        parent.setSelectedAmount(item, 0);
        close();
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xED101116);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, titleY, 0xFFFFFF);
        ItemStack stack = itemStack(item.itemId);
        if (!stack.isEmpty()) context.drawItem(stack, this.width / 2 - 8, titleY + 18);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(item.displayName), this.width / 2, titleY + 40, 0xE5E7EB);
        context.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal("可取 " + item.amount + availabilitySuffix()), this.width / 2, titleY + 51, 0xAAB4C0);
        context.drawTextWithShadow(this.textRenderer, Text.literal("取货数量"), this.width / 2 - 100, titleY + 59, 0xC5CED8);
        if (!status.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(status), this.width / 2, titleY + 126, 0xF1B36B);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private String availabilitySuffix() {
        if (item.boxedAmount <= 0) return "（散装）";
        if (item.looseAmount <= 0) return "（潜影盒内）";
        return "（散装 " + item.looseAmount + " + 盒内 " + item.boxedAmount + "）";
    }

    private ItemStack itemStack(String id) {
        try {
            Identifier identifier = Identifier.tryParse(id);
            return identifier == null ? ItemStack.EMPTY : new ItemStack(Registries.ITEM.get(identifier));
        } catch (RuntimeException ignored) {
            return ItemStack.EMPTY;
        }
    }
}
