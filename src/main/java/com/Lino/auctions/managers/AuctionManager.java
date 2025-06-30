package com.Lino.auctions.managers;

import com.Lino.auctions.Auctions;
import com.Lino.auctions.models.AuctionData;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AuctionManager {
    private final Auctions plugin;
    private final Map<UUID, ItemStack> pendingItems = new HashMap<>();
    private final Map<UUID, String> pendingPurchases = new HashMap<>();
    private final Map<UUID, String> pendingCancellations = new HashMap<>();

    public AuctionManager(Auctions plugin) {
        this.plugin = plugin;
    }

    public boolean createAuction(Player seller, ItemStack item, double price) {
        // Validate input
        if (item == null || price <= 0) {
            return false;
        }

        // Check price limits
        if (price > plugin.getConfigManager().getMaxPrice()) {
            seller.sendMessage(plugin.getMessageManager().formatMessage(
                    plugin.getMessageManager().getMessage("messages.price-too-high"),
                    price, plugin.getConfigManager().getMaxPrice()
            ));
            return false;
        }

        // Check auction limit
        int activeCount = plugin.getDatabaseManager().getPlayerActiveAuctionCount(seller.getUniqueId());
        if (activeCount >= plugin.getConfigManager().getMaxAuctionsPerPlayer()) {
            seller.sendMessage(plugin.getMessageManager().formatMessage(
                    plugin.getMessageManager().getMessage("messages.max-auctions-reached"),
                    plugin.getConfigManager().getMaxAuctionsPerPlayer()
            ));
            return false;
        }

        // Create auction
        String auctionId = UUID.randomUUID().toString();
        AuctionData auction = new AuctionData(
                auctionId, item, price, seller.getUniqueId(),
                seller.getName(), System.currentTimeMillis(), false
        );

        if (plugin.getDatabaseManager().createAuction(auction)) {
            // Broadcast creation
            String itemName = auction.getItemDisplayName();
            for (String line : plugin.getMessageManager().getMessage("messages.auction-created-broadcast").split("\n")) {
                Bukkit.broadcastMessage(plugin.getMessageManager().formatMessage(line, price, 0, seller.getName(), itemName));
            }

            // Play sounds
            for (Player p : Bukkit.getOnlinePlayers()) {
                plugin.getSoundManager().playSound(p, "auction-created");
            }

            seller.sendMessage(plugin.getMessageManager().getMessage("messages.auction-created"));
            return true;
        }

        return false;
    }

    public boolean purchaseAuction(Player buyer, String auctionId) {
        AuctionData auction = plugin.getDatabaseManager().getAuction(auctionId);
        if (auction == null || auction.isExpired()) {
            return false;
        }

        // Check if buyer is not the seller
        if (auction.isOwnedBy(buyer.getUniqueId())) {
            return false;
        }

        // Check funds
        if (!plugin.getEconomyManager().hasBalance(buyer, auction.getPrice())) {
            buyer.sendMessage(plugin.getMessageManager().formatMessage(
                    plugin.getMessageManager().getMessage("messages.insufficient-funds"),
                    auction.getPrice()
            ));
            return false;
        }

        // Execute transaction
        if (!plugin.getEconomyManager().withdraw(buyer, auction.getPrice())) {
            return false;
        }

        OfflinePlayer seller = Bukkit.getOfflinePlayer(auction.getSellerUUID());
        plugin.getEconomyManager().deposit(seller, auction.getPrice());

        // Give item to buyer
        buyer.getInventory().addItem(auction.getItem());

        // Remove auction from database
        plugin.getDatabaseManager().deleteAuction(auctionId);

        // Send messages
        buyer.sendMessage(plugin.getMessageManager().formatMessage(
                plugin.getMessageManager().getMessage("messages.purchase-success"),
                auction.getPrice()
        ));

        if (seller.isOnline()) {
            seller.getPlayer().sendMessage(plugin.getMessageManager().formatMessage(
                    plugin.getMessageManager().getMessage("messages.item-sold"),
                    auction.getPrice()
            ));
            plugin.getSoundManager().playSound(seller.getPlayer(), "item-sold");
        }

        return true;
    }

    public boolean cancelAuction(Player seller, String auctionId) {
        AuctionData auction = plugin.getDatabaseManager().getAuction(auctionId);
        if (auction == null || !auction.isOwnedBy(seller.getUniqueId())) {
            return false;
        }

        // Return item to seller
        seller.getInventory().addItem(auction.getItem());

        // Remove from database
        plugin.getDatabaseManager().deleteAuction(auctionId);

        seller.sendMessage(plugin.getMessageManager().getMessage("messages.auction-cancelled-success"));
        return true;
    }

    public boolean reclaimExpiredItem(Player player, String auctionId) {
        AuctionData auction = plugin.getDatabaseManager().getAuction(auctionId);
        if (auction == null || !auction.isExpired() || !auction.isOwnedBy(player.getUniqueId())) {
            return false;
        }

        // Return item to player
        player.getInventory().addItem(auction.getItem());

        // Remove from database
        plugin.getDatabaseManager().deleteAuction(auctionId);

        player.sendMessage(plugin.getMessageManager().getMessage("messages.item-reclaimed"));
        return true;
    }

    public void checkExpiredAuctions() {
        long currentTime = System.currentTimeMillis();
        long auctionDuration = plugin.getConfigManager().getAuctionDurationHours() * 60 * 60 * 1000;
        long threshold = currentTime - auctionDuration;

        List<AuctionData> expiredAuctions = plugin.getDatabaseManager().getExpiredAuctionsBefore(threshold);

        for (AuctionData auction : expiredAuctions) {
            // Mark as expired
            plugin.getDatabaseManager().expireAuction(auction.getId());

            // Notify seller if online
            Player seller = Bukkit.getPlayer(auction.getSellerUUID());
            if (seller != null && seller.isOnline()) {
                String itemName = auction.getItemDisplayName();
                for (String line : plugin.getMessageManager().getMessage("messages.auction-expired-notify").split("\n")) {
                    seller.sendMessage(line.replace("{item}", itemName));
                }
            }
        }
    }

    // Pending action management
    public void setPendingItem(UUID playerUUID, ItemStack item) {
        pendingItems.put(playerUUID, item);
    }

    public ItemStack getPendingItem(UUID playerUUID) {
        return pendingItems.get(playerUUID);
    }

    public ItemStack removePendingItem(UUID playerUUID) {
        return pendingItems.remove(playerUUID);
    }

    public void setPendingPurchase(UUID playerUUID, String auctionId) {
        pendingPurchases.put(playerUUID, auctionId);
    }

    public String removePendingPurchase(UUID playerUUID) {
        return pendingPurchases.remove(playerUUID);
    }

    public void setPendingCancellation(UUID playerUUID, String auctionId) {
        pendingCancellations.put(playerUUID, auctionId);
    }

    public String removePendingCancellation(UUID playerUUID) {
        return pendingCancellations.remove(playerUUID);
    }

    public boolean hasPendingItem(UUID playerUUID) {
        return pendingItems.containsKey(playerUUID);
    }
}