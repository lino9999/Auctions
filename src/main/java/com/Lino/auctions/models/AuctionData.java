package com.Lino.auctions.models;

import org.bukkit.inventory.ItemStack;
import java.util.UUID;

public class AuctionData {
    private final String id;
    private final ItemStack item;
    private final double price;
    private final UUID sellerUUID;
    private final String sellerName;
    private final long creationTime;
    private boolean expired;

    public AuctionData(String id, ItemStack item, double price, UUID sellerUUID,
                       String sellerName, long creationTime, boolean expired) {
        this.id = id;
        this.item = item;
        this.price = price;
        this.sellerUUID = sellerUUID;
        this.sellerName = sellerName;
        this.creationTime = creationTime;
        this.expired = expired;
    }

    // Getters
    public String getId() { return id; }
    public ItemStack getItem() { return item; }
    public double getPrice() { return price; }
    public UUID getSellerUUID() { return sellerUUID; }
    public String getSellerName() { return sellerName; }
    public long getCreationTime() { return creationTime; }
    public boolean isExpired() { return expired; }

    // Setters
    public void setExpired(boolean expired) { this.expired = expired; }

    public String getItemDisplayName() {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return item.getType().toString();
    }

    public long getTimeLeft(long auctionDurationMs) {
        return (creationTime + auctionDurationMs) - System.currentTimeMillis();
    }

    public boolean isOwnedBy(UUID playerUUID) {
        return sellerUUID.equals(playerUUID);
    }
}