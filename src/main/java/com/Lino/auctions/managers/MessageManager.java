package com.Lino.auctions.managers;

import com.Lino.auctions.Auctions;
import org.bukkit.ChatColor;

public class MessageManager {
    private final Auctions plugin;

    public MessageManager(Auctions plugin) {
        this.plugin = plugin;
    }

    public String getMessage(String path) {
        String message = plugin.getConfigManager().getConfig().getString(path, "Message not found: " + path);
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public String formatMessage(String message, Object... placeholders) {
        String formatted = message;
        String currencySymbol = plugin.getConfigManager().getCurrencySymbol();

        // Replace common placeholders
        formatted = formatted.replace("{currency}", currencySymbol);

        // Replace numbered placeholders {0}, {1}, etc.
        for (int i = 0; i < placeholders.length; i++) {
            formatted = formatted.replace("{" + i + "}", String.valueOf(placeholders[i]));
        }

        // Legacy placeholders for backwards compatibility
        if (placeholders.length >= 1) formatted = formatted.replace("{price}", String.valueOf(placeholders[0]));
        if (placeholders.length >= 2) formatted = formatted.replace("{max}", String.valueOf(placeholders[1]));
        if (placeholders.length >= 3) formatted = formatted.replace("{player}", String.valueOf(placeholders[2]));
        if (placeholders.length >= 4) formatted = formatted.replace("{item}", String.valueOf(placeholders[3]));
        if (placeholders.length >= 3) formatted = formatted.replace("{seller}", String.valueOf(placeholders[2]));

        return formatted;
    }
}