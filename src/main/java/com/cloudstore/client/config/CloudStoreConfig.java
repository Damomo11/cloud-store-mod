package com.cloudstore.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CloudStoreConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("cloud-store.json");

    public String apiBaseUrl = "http://127.0.0.1:8787";
    public String loginToken = "";

    public static CloudStoreConfig load() {
        if (!Files.exists(PATH)) {
            CloudStoreConfig config = new CloudStoreConfig();
            config.save();
            return config;
        }

        try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
            CloudStoreConfig config = GSON.fromJson(reader, CloudStoreConfig.class);
            if (config == null) {
                return new CloudStoreConfig();
            }
            config.apiBaseUrl = normalizeUrl(config.apiBaseUrl);
            config.loginToken = config.loginToken == null ? "" : config.loginToken;
            return config;
        } catch (Exception ignored) {
            return new CloudStoreConfig();
        }
    }

    public void save() {
        apiBaseUrl = normalizeUrl(apiBaseUrl);
        loginToken = loginToken == null ? "" : loginToken.trim();
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("无法保存云仓库配置：" + exception.getMessage(), exception);
        }
    }

    public boolean isConfigured() {
        return !apiBaseUrl.isBlank() && !loginToken.isBlank();
    }

    public static String normalizeUrl(String value) {
        String result = value == null ? "" : value.trim();
        if (result.isEmpty()) {
            return "http://127.0.0.1:8787";
        }
        if (!result.startsWith("http://") && !result.startsWith("https://")) {
            result = "http://" + result;
        }
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
