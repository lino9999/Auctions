package com.Lino.auctions.managers;

import com.Lino.auctions.Auctions;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class SoundManager {
    private final Auctions plugin;

    public SoundManager(Auctions plugin) {
        this.plugin = plugin;
    }

    public void playSound(Player player, String soundType) {
        if (!plugin.getConfigManager().areSoundsEnabled()) return;

        String soundPath = "sounds." + soundType;
        if (!plugin.getConfigManager().getConfig().contains(soundPath)) return;

        try {
            Sound sound = Sound.valueOf(plugin.getConfigManager().getConfig().getString(soundPath + ".sound"));
            float volume = (float) plugin.getConfigManager().getConfig().getDouble(soundPath + ".volume", 1.0);
            float pitch = (float) plugin.getConfigManager().getConfig().getDouble(soundPath + ".pitch", 1.0);
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (Exception e) {
            plugin.getLogger().warning("Invalid sound configuration for: " + soundType);
        }
    }
}