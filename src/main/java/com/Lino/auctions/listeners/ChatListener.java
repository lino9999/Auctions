package com.Lino.auctions.listeners;

import com.Lino.auctions.Auctions;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.ItemStack;

public class ChatListener implements Listener {
    private final Auctions plugin;

    public ChatListener(Auctions plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player player = e.getPlayer();

        if (!plugin.getAuctionManager().hasPendingItem(player.getUniqueId())) {
            return;
        }

        e.setCancelled(true);
        String message = e.getMessage().trim();

        // Handle cancellation
        if (message.equalsIgnoreCase("cancel")) {
            ItemStack item = plugin.getAuctionManager().removePendingItem(player.getUniqueId());
            if (item != null) {
                player.getInventory().addItem(item);
            }
            player.sendMessage(plugin.getMessageManager().getMessage("messages.auction-cancelled"));
            return;
        }

        // Prevent commands while in price input mode
        if (message.startsWith("/")) {
            player.sendMessage(plugin.getMessageManager().getMessage("messages.cannot-use-commands"));
            return;
        }

        // Process price input
        try {
            double price = Double.parseDouble(message);

            if (price <= 0) {
                player.sendMessage(plugin.getMessageManager().getMessage("messages.price-too-low"));
                return;
            }

            ItemStack item = plugin.getAuctionManager().removePendingItem(player.getUniqueId());
            if (item != null) {
                if (plugin.getAuctionManager().createAuction(player, item, price)) {
                    plugin.getSoundManager().playSound(player, "success");
                } else {
                    // Return item if auction creation failed
                    player.getInventory().addItem(item);
                    player.sendMessage(plugin.getMessageManager().getMessage("messages.database-error"));
                }
            }

        } catch (NumberFormatException ex) {
            player.sendMessage(plugin.getMessageManager().getMessage("messages.invalid-price"));
        }
    }

    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent e) {
        Player player = e.getPlayer();

        if (plugin.getAuctionManager().hasPendingItem(player.getUniqueId())) {
            e.setCancelled(true);
            player.sendMessage(plugin.getMessageManager().getMessage("messages.cannot-use-commands"));
        }
    }
}