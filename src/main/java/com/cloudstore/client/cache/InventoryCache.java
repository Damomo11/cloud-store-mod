package com.cloudstore.client.cache;

import com.cloudstore.client.CloudStoreClient;
import com.cloudstore.client.api.CloudStoreModels;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class InventoryCache {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("cloud-store-cache.json");
    private Snapshot snapshot;

    private InventoryCache(Snapshot snapshot) {
        this.snapshot = snapshot;
    }

    public static InventoryCache load() {
        if (Files.exists(PATH)) {
            try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
                Snapshot loaded = GSON.fromJson(reader, Snapshot.class);
                if (loaded != null) {
                    if (loaded.inventories == null) loaded.inventories = new HashMap<>();
                    return new InventoryCache(loaded);
                }
            } catch (Exception error) {
                CloudStoreClient.LOGGER.warn("Unable to read inventory cache: {}", error.getMessage());
            }
        }
        return new InventoryCache(new Snapshot());
    }

    public synchronized CloudStoreModels.MeResponse me(String endpoint) {
        return matchesEndpoint(endpoint) ? snapshot.me : null;
    }

    public synchronized CloudStoreModels.InventoryResponse inventory(String endpoint, String ownerUuid) {
        return matchesEndpoint(endpoint) ? snapshot.inventories.get(ownerUuid) : null;
    }

    public synchronized void putMe(String endpoint, CloudStoreModels.MeResponse me) {
        selectEndpoint(endpoint);
        snapshot.me = me;
        snapshot.updatedAt = System.currentTimeMillis();
        save();
    }

    public synchronized void putInventory(String endpoint, String ownerUuid, CloudStoreModels.InventoryResponse inventory) {
        selectEndpoint(endpoint);
        snapshot.inventories.put(ownerUuid, inventory);
        snapshot.updatedAt = System.currentTimeMillis();
        save();
    }

    private void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(snapshot, writer);
            }
        } catch (Exception error) {
            CloudStoreClient.LOGGER.warn("Unable to save inventory cache: {}", error.getMessage());
        }
    }

    private boolean matchesEndpoint(String endpoint) {
        return String.valueOf(endpoint).equals(snapshot.endpoint);
    }

    private void selectEndpoint(String endpoint) {
        String value = String.valueOf(endpoint);
        if (!value.equals(snapshot.endpoint)) {
            snapshot = new Snapshot();
            snapshot.endpoint = value;
        }
    }

    private static final class Snapshot {
        CloudStoreModels.MeResponse me;
        Map<String, CloudStoreModels.InventoryResponse> inventories = new HashMap<>();
        String endpoint = "";
        long updatedAt;
    }
}
