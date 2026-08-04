package com.cloudstore.client.api;

import java.util.ArrayList;
import java.util.List;

public final class CloudStoreModels {
    private CloudStoreModels() {
    }

    public static class User {
        public String uuid = "";
        public String username = "";
    }

    public static class Owner {
        public String ownerUuid = "";
        public String label = "";
        public String type = "";
        public String role = "";
        public long totalAmount;
        public boolean isDefault;
    }

    public static class Active {
        public String username = "";
        public String targetUsername = "";
        public String message = "";
        public String phase = "";
    }

    public static class MeResponse {
        public boolean ok;
        public boolean loggedIn;
        public String error = "";
        public String botName = "";
        public String defaultOwnerUuid = "";
        public User user;
        public List<Owner> owners = new ArrayList<>();
        public Active active;
    }

    public static class Item {
        public String itemKey = "";
        public String itemId = "";
        public String displayName = "";
        public String baseDisplayName = "";
        public String shortCode = "";
        public long amount;
        public long looseAmount;
        public long boxedAmount;
        public List<Item> shulkerContents = new ArrayList<>();
    }

    public static class WithdrawRequest {
        public String itemKey = "";
        public String displayName = "";
        public long amount;

        public WithdrawRequest(Item item, long amount) {
            this.itemKey = item.itemKey;
            this.displayName = item.displayName;
            this.amount = amount;
        }
    }

    public static class Stats {
        public long totalAmount;
        public int itemTypes;
    }

    public static class Quota {
        public int usedSlots;
        public int quotaSlots;
        public double usagePercent;
    }

    public static class InventoryResponse {
        public boolean ok;
        public String error = "";
        public String ownerUuid = "";
        public String ownerLabel = "";
        public Stats stats = new Stats();
        public Quota quota = new Quota();
        public List<Item> items = new ArrayList<>();
        public Active active;
    }

    public static class ActionResponse {
        public boolean ok;
        public String error = "";
        public String message = "";
        public Active active;
    }
}
