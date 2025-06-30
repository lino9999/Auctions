package com.Lino.auctions.listeners;

import com.Lino.auctions.Auctions;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

public class InventoryListener implements Listener {
    private final Auctions plugin;

    public InventoryListener(Auctions plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;

        Player player = (Player) e.getWhoClicked();
        String title = e.getView().getTitle();

        // Main auction GUI
        if (title.equals(plugin.getMessageManager().getMessage("gui.main-title"))) {
            handleMainAuctionClick(e, player);
        }
        // Expired auctions GUI
        else if (title.equals(plugin.getMessageManager().getMessage("gui.expired-title"))) {
            handleExpiredAuctionClick(e, player);
        }
        // Sell menu GUI
        else if (title.equals(plugin.getMessageManager().getMessage("gui.sell-title"))) {
            handleSellMenuClick(e, player);
        }
        // Confirmation GUIs
        else if (title.contains(plugin.getMessageManager().getMessage("gui.confirm-purchase-title")) ||
                title.contains(plugin.getMessageManager().getMessage("gui.cancel-auction-title"))) {
            handleConfirmationClick(e, player, title);
        }
    }

    private void handleMainAuctionClick(InventoryClickEvent e, Player player) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        if (e.getSlot() == 49) { // Create auction button
            plugin.getGuiManager().openSellMenu(player);
            plugin.getSoundManager().playSound(player, "button-click");
        } else if (e.getSlot() == 45) { // Expired items button
            plugin.getGuiManager().openExpiredAuction(player);
            plugin.getSoundManager().playSound(player, "button-click");
        } else if (e.getSlot() == 53) { // Refresh button
            player.closeInventory();
            plugin.getGuiManager().openMainAuction(player);
            plugin.getSoundManager().playSound(player, "button-click");
        } else if (e.getSlot() >= 9 && e.getSlot() < 45) { // Auction items
            String auctionId = plugin.getGuiManager().getAuctionIdFromLore(e.getCurrentItem());
            if (auctionId != null) {
                handleAuctionItemClick(player, auctionId);
                plugin.getSoundManager().playSound(player, "button-click");
            }
        }
    }

    private void handleExpiredAuctionClick(InventoryClickEvent e, Player player) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        if (e.getSlot() == 45) { // Back button
            plugin.getGuiManager().openMainAuction(player);
            plugin.getSoundManager().playSound(player, "button-click");
        } else if (e.getSlot() >= 9 && e.getSlot() < 45) { // Expired items
            String auctionId = plugin.getGuiManager().getAuctionIdFromLore(e.getCurrentItem());
            if (auctionId != null) {
                if (plugin.getAuctionManager().reclaimExpiredItem(player, auctionId)) {
                    plugin.getGuiManager().openExpiredAuction(player); // Refresh
                    plugin.getSoundManager().playSound(player, "success");
                }
            }
        }
    }

    private void handleSellMenuClick(InventoryClickEvent e, Player player) {
        // Allow interactions only in slot 13 (item placement slot)
        if (e.getRawSlot() >= 0 && e.getRawSlot() < 27) {
            if (e.getRawSlot() == 13) {
                return; // Allow free interaction in item slot
            }

            e.setCancelled(true);

            if (e.getRawSlot() == 22) { // Confirm sale button
                ItemStack item = e.getInventory().getItem(13);
                if (item != null && item.getType().name() != "AIR") {
                    plugin.getAuctionManager().setPendingItem(player.getUniqueId(), item.clone());
                    e.getInventory().setItem(13, null);
                    player.closeInventory();
                    player.sendMessage(plugin.getMessageManager().getMessage("messages.enter-price-prompt"));
                    plugin.getSoundManager().playSound(player, "button-click");
                } else {
                    player.sendMessage(plugin.getMessageManager().getMessage("messages.no-item-placed"));
                    plugin.getSoundManager().playSound(player, "error");
                }
            } else if (e.getRawSlot() == 18) { // Cancel button
                player.closeInventory();
                plugin.getSoundManager().playSound(player, "button-click");
            }
        }
    }

    private void handleConfirmationClick(InventoryClickEvent e, Player player, String title) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        if (e.getSlot() == 11) { // Confirm button
            if (title.contains(plugin.getMessageManager().getMessage("gui.confirm-purchase-title"))) {
                String auctionId = plugin.getAuctionManager().removePendingPurchase(player.getUniqueId());
                if (auctionId != null) {
                    if (plugin.getAuctionManager().purchaseAuction(player, auctionId)) {
                        player.closeInventory();
                        plugin.getGuiManager().openMainAuction(player);
                        plugin.getSoundManager().playSound(player, "success");
                    }
                }
            } else {
                String auctionId = plugin.getAuctionManager().removePendingCancellation(player.getUniqueId());
                if (auctionId != null) {
                    if (plugin.getAuctionManager().cancelAuction(player, auctionId)) {
                        player.closeInventory();
                        plugin.getGuiManager().openMainAuction(player);
                        plugin.getSoundManager().playSound(player, "success");
                    }
                }
            }
        } else if (e.getSlot() == 15) { // Cancel button
            plugin.getAuctionManager().removePendingPurchase(player.getUniqueId());
            plugin.getAuctionManager().removePendingCancellation(player.getUniqueId());
            player.closeInventory();
            plugin.getGuiManager().openMainAuction(player);
            plugin.getSoundManager().playSound(player, "button-click");
        }
    }

    private void handleAuctionItemClick(Player player, String auctionId) {
        var auction = plugin.getDatabaseManager().getAuction(auctionId);
        if (auction == null) return;

        if (auction.isOwnedBy(player.getUniqueId())) {
            plugin.getGuiManager().openCancellationConfirm(player, auctionId);
        } else {
            plugin.getGuiManager().openPurchaseConfirm(player, auctionId);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player)) return;

        Player player = (Player) e.getPlayer();

        // Handle sell menu closure - return item if not processed
        if (e.getView().getTitle().equals(plugin.getMessageManager().getMessage("gui.sell-title"))) {
            if (!plugin.getAuctionManager().hasPendingItem(player.getUniqueId())) {
                ItemStack item = e.getInventory().getItem(13);
                if (item != null && item.getType().name() != "AIR") {
                    player.getInventory().addItem(item);
                }
            }
        }
    }
}