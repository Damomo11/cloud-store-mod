package com.cloudstore.client;

import com.cloudstore.client.api.CloudStoreApi;
import com.cloudstore.client.cache.InventoryCache;
import com.cloudstore.client.config.CloudStoreConfig;
import com.cloudstore.client.screen.CloudStoreScreen;
import com.cloudstore.client.screen.ConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CloudStoreClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("cloudstore");
    public static CloudStoreConfig config;
    public static CloudStoreApi api;
    public static InventoryCache cache;
    private static KeyMapping openKey;

    @Override
    public void onInitializeClient() {
        config = CloudStoreConfig.load();
        api = new CloudStoreApi(config);
        cache = InventoryCache.load();
        LOGGER.info("Cloud Store client initialized, endpoint={}", config.apiBaseUrl);
        openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.cloudstore.open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("cloudstore", "main"))
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.consumeClick()) {
                open(client);
            }
        });
    }

    public static void open(Minecraft client) {
        if (client.screen instanceof CloudStoreScreen) {
            return;
        }
        client.setScreen(new CloudStoreScreen(client.screen));
    }

    public static void openConfig(Minecraft client, net.minecraft.client.gui.screens.Screen parent) {
        client.setScreen(new ConfigScreen(parent));
    }

    public static void resetApi() {
        api = new CloudStoreApi(config);
    }

    public static void showStatus(String message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.sendOverlayMessage(Component.literal("[云仓库] " + message));
        }
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("cloudstore", path);
    }
}
