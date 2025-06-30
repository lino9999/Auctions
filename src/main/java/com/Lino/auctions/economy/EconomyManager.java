package com.Lino.auctions.economy;

import com.Lino.auctions.Auctions;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

public class EconomyManager {
    private final Auctions plugin;
    private Economy economy;

    public EconomyManager(Auctions plugin) {
        this.plugin = plugin;
    }

    public boolean setupEconomy() {
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            plugin.getLogger().severe("Vault dependency not found!");
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    public boolean hasBalance(OfflinePlayer player, double amount) {
        return economy.has(player, amount);
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        return economy.depositPlayer(player, amount).transactionSuccess();
    }

    public double getBalance(OfflinePlayer player) {
        return economy.getBalance(player);
    }

    public String formatMoney(double amount) {
        return economy.format(amount);
    }
}
