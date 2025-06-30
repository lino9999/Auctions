package com.Lino.auctions.gui;

import com.Lino.auctions.Auctions;
import com.Lino.auctions.models.AuctionData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GuiManager {
    private final Auctions plugin;

    public GuiManager(Auctions plugin) {
        this.plugin = plugin;
    }

    public void openMainAuction(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, plugin.getMessageManager().getMessage("gui.main-title"));

        // Add decorative glass panes
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, createGlassPane(Material.PURPLE_STAINED_GLASS_PANE, " "));
            inv.setItem(45 + i, createGlassPane(Material.PURPLE_STAINED_GLASS_PANE, " "));
        }

        // Add control buttons
        inv.setItem(49, createMenuItem(Material.EMERALD,
                plugin.getMessageManager().getMessage("gui.create-auction"),
                Arrays.asList(plugin.getMessageManager().getMessage("gui.create-auction-lore").split("\n"))));

        inv.setItem(45, createMenuItem(Material.HOPPER,
                plugin.getMessageManager().getMessage("gui.expired-items"),
                Arrays.asList(plugin.getMessageManager().getMessage("gui.expired-items-lore").split("\n"))));

        inv.setItem(53, createMenuItem(Material.SUNFLOWER,
                plugin.getMessageManager().getMessage("gui.refresh"),
                Arrays.asList(plugin.getMessageManager().getMessage("gui.refresh-lore").split("\n"))));

        // Load active auctions
        List<AuctionData> auctions = plugin.getDatabaseManager().getActiveAuctions();
        int slot = 9;

        for (AuctionData auction : auctions) {
            if (slot >= 45) break;

            ItemStack displayItem = createAuctionDisplayItem(auction, player);
            inv.setItem(slot, displayItem);
            slot++;
        }

        player.openInventory(inv);
        plugin.getSoundManager().playSound(player, "open-menu");
    }

    public void openExpiredAuction(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, plugin.getMessageManager().getMessage("gui.expired-title"));

        // Add decorative glass panes
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, createGlassPane(Material.RED_STAINED_GLASS_PANE, " "));
            inv.setItem(45 + i, createGlassPane(Material.RED_STAINED_GLASS_PANE, " "));
        }

        // Add back button
        inv.setItem(45, createMenuItem(Material.ARROW,
                plugin.getMessageManager().getMessage("gui.back"),
                Arrays.asList(plugin.getMessageManager().getMessage("gui.back-lore").split("\n"))));

        // Load expired auctions for this player
        List<AuctionData> expiredAuctions = plugin.getDatabaseManager().getExpiredAuctions(player.getUniqueId());
        int slot = 9;

        for (AuctionData auction : expiredAuctions) {
            if (slot >= 45) break;

            ItemStack displayItem = createExpiredAuctionDisplayItem(auction);
            inv.setItem(slot, displayItem);
            slot++;
        }

        player.openInventory(inv);
        plugin.getSoundManager().playSound(player, "open-menu");
    }

    public void openSellMenu(Player player) {
        // Check auction limit
        int activeCount = plugin.getDatabaseManager().getPlayerActiveAuctionCount(player.getUniqueId());
        int maxAuctions = plugin.getConfigManager().getMaxAuctionsPerPlayer();

        if (activeCount >= maxAuctions) {
            player.sendMessage(plugin.getMessageManager().formatMessage(
                    plugin.getMessageManager().getMessage("messages.max-auctions-reached"),
                    maxAuctions
            ));
            plugin.getSoundManager().playSound(player, "error");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27, plugin.getMessageManager().getMessage("gui.sell-title"));

        // Fill with glass panes except center slot
        for (int i = 0; i < 27; i++) {
            if (i != 13) {
                inv.setItem(i, createGlassPane(Material.ORANGE_STAINED_GLASS_PANE, " "));
            }
        }

        // Add control buttons
        inv.setItem(22, createMenuItem(Material.EMERALD_BLOCK,
                plugin.getMessageManager().getMessage("gui.confirm-sale"),
                Arrays.asList(plugin.getMessageManager().getMessage("gui.confirm-sale-lore").split("\n"))));

        inv.setItem(18, createMenuItem(Material.BARRIER,
                plugin.getMessageManager().getMessage("gui.cancel"),
                Arrays.asList(plugin.getMessageManager().getMessage("gui.cancel-lore").split("\n"))));

        player.openInventory(inv);
        plugin.getSoundManager().playSound(player, "open-menu");
    }

    public void openPurchaseConfirm(Player player, String auctionId) {
        AuctionData auction = plugin.getDatabaseManager().getAuction(auctionId);
        if (auction == null) return;

        Inventory inv = Bukkit.createInventory(null, 27, plugin.getMessageManager().getMessage("gui.confirm-purchase-title"));

        // Fill with black glass panes
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, createGlassPane(Material.BLACK_STAINED_GLASS_PANE, " "));
        }

        // Display the item
        inv.setItem(13, auction.getItem());

        // Confirm button
        List<String> confirmLore = new ArrayList<>();
        for (String line : plugin.getMessageManager().getMessage("gui.confirm-purchase-lore").split("\n")) {
            confirmLore.add(plugin.getMessageManager().formatMessage(line, auction.getPrice(), 0, auction.getSellerName(), ""));
        }

        inv.setItem(11, createMenuItem(Material.EMERALD_BLOCK,
                plugin.getMessageManager().getMessage("gui.confirm"), confirmLore));

        // Cancel button
        inv.setItem(15, createMenuItem(Material.BARRIER,
                plugin.getMessageManager().getMessage("gui.cancel-button"),
                Arrays.asList(plugin.getMessageManager().getMessage("gui.return-to-auctions").split("\n"))));

        plugin.getAuctionManager().setPendingPurchase(player.getUniqueId(), auctionId);
        player.openInventory(inv);
        plugin.getSoundManager().playSound(player, "open-menu");
    }

    public void openCancellationConfirm(Player player, String auctionId) {
        AuctionData auction = plugin.getDatabaseManager().getAuction(auctionId);
        if (auction == null) return;

        Inventory inv = Bukkit.createInventory(null, 27, plugin.getMessageManager().getMessage("gui.cancel-auction-title"));

        // Fill with red glass panes
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, createGlassPane(Material.RED_STAINED_GLASS_PANE, " "));
        }

        // Display the item
        inv.setItem(13, auction.getItem());

        // Confirm cancellation button
        List<String> cancelLore = new ArrayList<>();
        for (String line : plugin.getMessageManager().getMessage("gui.cancel-auction-lore").split("\n")) {
            cancelLore.add(plugin.getMessageManager().formatMessage(line, auction.getPrice(), 0, "", ""));
        }

        inv.setItem(11, createMenuItem(Material.EMERALD_BLOCK,
                plugin.getMessageManager().getMessage("gui.cancel-confirm"), cancelLore));

        // Keep auction button
        inv.setItem(15, createMenuItem(Material.BARRIER,
                plugin.getMessageManager().getMessage("gui.keep-auction"),
                Arrays.asList(plugin.getMessageManager().getMessage("gui.return-to-auctions").split("\n"))));

        plugin.getAuctionManager().setPendingCancellation(player.getUniqueId(), auctionId);
        player.openInventory(inv);
        plugin.getSoundManager().playSound(player, "open-menu");
    }

    private ItemStack createAuctionDisplayItem(AuctionData auction, Player viewer) {
        ItemStack displayItem = auction.getItem().clone();
        ItemMeta meta = displayItem.getItemMeta();

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.DARK_PURPLE + "═══════════════");

        lore.add(plugin.getMessageManager().getMessage("gui.price-label") +
                ChatColor.YELLOW + plugin.getConfigManager().getCurrencySymbol() + auction.getPrice());

        lore.add(plugin.getMessageManager().getMessage("gui.seller-label") +
                ChatColor.WHITE + auction.getSellerName());

        // Calculate time left
        long timeLeft = auction.getTimeLeft(plugin.getConfigManager().getAuctionDurationHours() * 60 * 60 * 1000);
        long hoursLeft = timeLeft / (1000 * 60 * 60);
        lore.add(plugin.getMessageManager().getMessage("gui.expires-label") +
                ChatColor.WHITE + hoursLeft + " hours");

        lore.add(ChatColor.DARK_PURPLE + "═══════════════");

        // Add action text based on ownership
        if (auction.isOwnedBy(viewer.getUniqueId())) {
            lore.add(plugin.getMessageManager().getMessage("gui.click-cancel"));
        } else {
            lore.add(plugin.getMessageManager().getMessage("gui.click-purchase"));
        }

        lore.add(ChatColor.DARK_GRAY + "ID: " + auction.getId());

        meta.setLore(lore);
        displayItem.setItemMeta(meta);
        return displayItem;
    }

    private ItemStack createExpiredAuctionDisplayItem(AuctionData auction) {
        ItemStack displayItem = auction.getItem().clone();
        ItemMeta meta = displayItem.getItemMeta();

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.DARK_RED + "═══════════════");

        lore.add(plugin.getMessageManager().getMessage("gui.original-price-label") +
                ChatColor.RED + plugin.getConfigManager().getCurrencySymbol() + auction.getPrice());

        lore.add(ChatColor.DARK_RED + "═══════════════");
        lore.add(plugin.getMessageManager().getMessage("gui.click-reclaim"));
        lore.add(ChatColor.DARK_GRAY + "ID: " + auction.getId());

        meta.setLore(lore);
        displayItem.setItemMeta(meta);
        return displayItem;
    }

    private ItemStack createGlassPane(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createMenuItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public String getAuctionIdFromLore(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) return null;

        List<String> lore = item.getItemMeta().getLore();
        for (String line : lore) {
            if (line.startsWith(ChatColor.DARK_GRAY + "ID: ")) {
                return line.substring((ChatColor.DARK_GRAY + "ID: ").length());
            }
        }
        return null;
    }
}