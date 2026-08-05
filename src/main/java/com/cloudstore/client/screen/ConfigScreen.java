package com.cloudstore.client.screen;

import com.cloudstore.client.CloudStoreClient;
import com.cloudstore.client.config.CloudStoreConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ConfigScreen extends Screen {
    private final Screen parent;
    private EditBox urlField;
    private EditBox tokenField;
    private Button testButton;
    private String status = "";
    private int statusColor = 0xB9C3D0;
    private int titleY;
    private int urlLabelY;
    private int tokenLabelY;
    private int statusY;

    public ConfigScreen(Screen parent) {
        super(Component.literal("云仓库设置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int center = this.width / 2;
        int formWidth = Math.min(300, this.width - 20);
        int formX = center - formWidth / 2;
        titleY = Math.max(8, Math.min(42, (this.height - 158) / 2));
        urlLabelY = titleY + 22;
        int urlFieldY = titleY + 34;
        tokenLabelY = titleY + 64;
        int tokenFieldY = titleY + 76;
        int buttonY = titleY + 112;
        statusY = Math.min(this.height - 18, titleY + 140);

        urlField = new EditBox(this.font, formX, urlFieldY, formWidth, 20, Component.literal("服务地址"));
        urlField.setHint(Component.literal("例如：http://服务器IP:端口"));
        urlField.setValue(CloudStoreClient.config.apiBaseUrl);
        urlField.setMaxLength(256);
        this.addRenderableWidget(urlField);
        tokenField = new EditBox(this.font, formX, tokenFieldY, formWidth, 20, Component.literal("登录密码"));
        tokenField.setHint(Component.literal("填写游戏内设置的 Web 登录密码"));
        tokenField.setValue(CloudStoreClient.config.loginToken);
        tokenField.setMaxLength(256);
        tokenField.addFormatter((text, firstCharacterIndex) -> Component.literal("*".repeat(text.length())).getVisualOrderText());
        this.addRenderableWidget(tokenField);

        int gap = 6;
        int buttonWidth = (formWidth - gap * 2) / 3;
        this.addRenderableWidget(Button.builder(Component.literal("保存"), button -> save())
            .bounds(formX, buttonY, buttonWidth, 20).build());
        testButton = Button.builder(Component.literal("保存并测试"), button -> testConnection())
            .bounds(formX + buttonWidth + gap, buttonY, buttonWidth, 20).build();
        this.addRenderableWidget(testButton);
        this.addRenderableWidget(Button.builder(Component.literal("取消"), button -> onClose())
            .bounds(formX + (buttonWidth + gap) * 2, buttonY, buttonWidth, 20).build());
        this.setInitialFocus(urlField);
    }

    private void save() {
        saveValues();
        status = "已保存";
        statusColor = 0x71D49A;
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    private void testConnection() {
        status = "正在测试连接...";
        statusColor = 0xB9C3D0;
        testButton.setMessage(Component.literal("测试中..."));
        testButton.active = false;
        CloudStoreClient.showStatus(status);
        try {
            saveValues();
        } catch (RuntimeException error) {
            finishTest("保存配置失败：" + errorMessage(error), false);
            return;
        }
        CloudStoreClient.LOGGER.info("Testing Cloud Store endpoint {}", CloudStoreClient.config.apiBaseUrl);
        CloudStoreClient.api.connect().whenComplete((response, error) -> {
            Minecraft client = Minecraft.getInstance();
            client.execute(() -> {
                if (error != null) {
                    String message = errorMessage(error);
                    CloudStoreClient.LOGGER.warn("Cloud Store connection test failed: {}", message);
                    finishTest(message, false);
                    return;
                }
                String username = response.user == null ? "未知用户" : response.user.username;
                CloudStoreClient.LOGGER.info("Cloud Store connection test succeeded for {}", username);
                finishTest("连接成功，当前用户：" + username, true);
            });
        });
    }

    private void finishTest(String message, boolean success) {
        status = message;
        statusColor = success ? 0x71D49A : 0xF1B36B;
        if (testButton != null) {
            testButton.setMessage(Component.literal("保存并测试"));
            testButton.active = true;
        }
        CloudStoreClient.showStatus(message);
    }

    private void saveValues() {
        CloudStoreClient.config.apiBaseUrl = CloudStoreConfig.normalizeUrl(urlField.getValue());
        CloudStoreClient.config.loginToken = tokenField.getValue().trim();
        CloudStoreClient.config.save();
        CloudStoreClient.resetApi();
    }

    private String errorMessage(Throwable error) {
        Throwable current = error;
        while ((current instanceof java.util.concurrent.CompletionException
            || current instanceof java.util.concurrent.ExecutionException) && current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "连接失败，请检查服务地址和登录密码。" : current.getMessage();
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xE9101116);
        context.centeredText(this.font, this.title, this.width / 2, titleY, 0xFFFFFF);
        context.text(this.font, Component.literal("服务地址（IP 或 URL）"), urlField.getX(), urlLabelY, 0xB9C3D0, true);
        context.text(this.font, Component.literal("登录密码（游戏内设置的 Web 密码）"), tokenField.getX(), tokenLabelY, 0xB9C3D0, true);
        if (!status.isEmpty()) {
            String displayStatus = this.font.plainSubstrByWidth(status, Math.max(80, this.width - 20));
            context.centeredText(this.font, Component.literal(displayStatus), this.width / 2, statusY, statusColor);
        }
        super.extractRenderState(context, mouseX, mouseY, delta);
    }
}
