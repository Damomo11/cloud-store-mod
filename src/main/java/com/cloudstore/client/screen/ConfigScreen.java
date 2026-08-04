package com.cloudstore.client.screen;

import com.cloudstore.client.CloudStoreClient;
import com.cloudstore.client.config.CloudStoreConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class ConfigScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget urlField;
    private TextFieldWidget tokenField;
    private ButtonWidget testButton;
    private String status = "";
    private int statusColor = 0xB9C3D0;
    private int titleY;
    private int urlLabelY;
    private int tokenLabelY;
    private int statusY;

    public ConfigScreen(Screen parent) {
        super(Text.literal("云仓库设置"));
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

        urlField = new TextFieldWidget(this.textRenderer, formX, urlFieldY, formWidth, 20, Text.literal("服务地址"));
        urlField.setPlaceholder(Text.literal("例如：http://服务器IP:端口"));
        urlField.setText(CloudStoreClient.config.apiBaseUrl);
        urlField.setMaxLength(256);
        this.addDrawableChild(urlField);
        tokenField = new TextFieldWidget(this.textRenderer, formX, tokenFieldY, formWidth, 20, Text.literal("登录密码"));
        tokenField.setPlaceholder(Text.literal("填写游戏内设置的 Web 登录密码"));
        tokenField.setText(CloudStoreClient.config.loginToken);
        tokenField.setMaxLength(256);
        tokenField.addFormatter((text, firstCharacterIndex) -> Text.literal("*".repeat(text.length())).asOrderedText());
        this.addDrawableChild(tokenField);

        int gap = 6;
        int buttonWidth = (formWidth - gap * 2) / 3;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("保存"), button -> save())
            .dimensions(formX, buttonY, buttonWidth, 20).build());
        testButton = ButtonWidget.builder(Text.literal("保存并测试"), button -> testConnection())
            .dimensions(formX + buttonWidth + gap, buttonY, buttonWidth, 20).build();
        this.addDrawableChild(testButton);
        this.addDrawableChild(ButtonWidget.builder(Text.literal("取消"), button -> close())
            .dimensions(formX + (buttonWidth + gap) * 2, buttonY, buttonWidth, 20).build());
        this.setInitialFocus(urlField);
    }

    private void save() {
        saveValues();
        status = "已保存";
        statusColor = 0x71D49A;
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    private void testConnection() {
        status = "正在测试连接...";
        statusColor = 0xB9C3D0;
        testButton.setMessage(Text.literal("测试中..."));
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
            MinecraftClient client = MinecraftClient.getInstance();
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
            testButton.setMessage(Text.literal("保存并测试"));
            testButton.active = true;
        }
        CloudStoreClient.showStatus(message);
    }

    private void saveValues() {
        CloudStoreClient.config.apiBaseUrl = CloudStoreConfig.normalizeUrl(urlField.getText());
        CloudStoreClient.config.loginToken = tokenField.getText().trim();
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
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xE9101116);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, titleY, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("服务地址（IP 或 URL）"), urlField.getX(), urlLabelY, 0xB9C3D0);
        context.drawTextWithShadow(this.textRenderer, Text.literal("登录密码（游戏内设置的 Web 密码）"), tokenField.getX(), tokenLabelY, 0xB9C3D0);
        if (!status.isEmpty()) {
            String displayStatus = this.textRenderer.trimToWidth(status, Math.max(80, this.width - 20));
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(displayStatus), this.width / 2, statusY, statusColor);
        }
        super.render(context, mouseX, mouseY, delta);
    }
}
