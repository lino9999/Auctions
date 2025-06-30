package com.Lino.auctions.tasks;

import com.Lino.auctions.Auctions;
import org.bukkit.scheduler.BukkitRunnable;

public class AuctionCleanupTask {
    private final Auctions plugin;

    public AuctionCleanupTask(Auctions plugin) {
        this.plugin = plugin;
    }

    public void start() {
        long intervalTicks = plugin.getConfigManager().getCleanupIntervalMinutes() * 60 * 20; // Convert to ticks

        new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getAuctionManager().checkExpiredAuctions();
            }
        }.runTaskTimer(plugin, 20L * 60, intervalTicks); // Start after 1 minute, repeat every configured interval
    }
}