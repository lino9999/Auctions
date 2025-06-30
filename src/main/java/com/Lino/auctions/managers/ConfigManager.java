package com.Lino.auctions.managers;

import com.Lino.auctions.Auctions;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {
    private final Auctions plugin;
    private FileConfiguration config;

    public ConfigManager(Auctions plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        this.config = plugin.getConfig();
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public int getMaxAuctionsPerPlayer() {
        return config.getInt("max-auctions-per-player", 5);
    }

    public long getAuctionDurationHours() {
        return config.getLong("auction-duration-hours", 24);
    }

    public double getMaxPrice() {
        return config.getDouble("max-price", 1000000.0);
    }

    public String getCurrencySymbol() {
        return config.getString("currency-symbol", "$");
    }

    public boolean areSoundsEnabled() {
        return config.getBoolean("sounds.enabled", true);
    }

    public long getCleanupIntervalMinutes() {
        return config.getLong("cleanup-interval-minutes", 30);
    }
}