package com.cloudstore.client;

import com.cloudstore.client.api.CloudStoreApi;
import com.cloudstore.client.cache.InventoryCache;
import com.cloudstore.client.config.CloudStoreConfig;
import com.cloudstore.client.screen.CloudStoreScreen;
import com.cloudstore.client.screen.ConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CloudStoreClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("cloudstore");
    public static CloudStoreConfig config;
    public static CloudStoreApi api;
    public static InventoryCache cache;
    private static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        config = CloudStoreConfig.load();
        api = new CloudStoreApi(config);
        cache = InventoryCache.load();
        LOGGER.info("Cloud Store client initialized, endpoint={}", config.apiBaseUrl);
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.cloudstore.open",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            KeyBinding.Category.create(Identifier.of("cloudstore", "main"))
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                open(client);
            }
        });
    }

    public static void open(MinecraftClient client) {
        if (client.currentScreen instanceof CloudStoreScreen) {
            return;
        }
        client.setScreen(new CloudStoreScreen(client.currentScreen));
    }

    public static void openConfig(MinecraftClient client, net.minecraft.client.gui.screen.Screen parent) {
        client.setScreen(new ConfigScreen(parent));
    }

    public static void resetApi() {
        api = new CloudStoreApi(config);
    }

    public static void showStatus(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal("[云仓库] " + message), true);
        }
    }

    public static Identifier id(String path) {
        return Identifier.of("cloudstore", path);
    }
}
