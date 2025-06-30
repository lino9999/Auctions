package com.Lino.auctions.commands;

import com.Lino.auctions.Auctions;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AuctionCommand implements CommandExecutor {
    private final Auctions plugin;

    public AuctionCommand(Auctions plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Handle reload command
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (sender.hasPermission("auctions.reload")) {
                plugin.getConfigManager().reload();
                sender.sendMessage(plugin.getMessageManager().getMessage("messages.reload"));
            } else {
                sender.sendMessage(plugin.getMessageManager().getMessage("messages.no-permission"));
            }
            return true;
        }

        // Player-only commands
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        // Handle different subcommands
        if (args.length == 0) {
            plugin.getGuiManager().openMainAuction(player);
        } else if (args[0].equalsIgnoreCase("expired")) {
            plugin.getGuiManager().openExpiredAuction(player);
        } else if (args[0].equalsIgnoreCase("sell")) {
            plugin.getGuiManager().openSellMenu(player);
        } else {
            // Invalid subcommand, show main auction
            plugin.getGuiManager().openMainAuction(player);
        }
    return true;
    }

}