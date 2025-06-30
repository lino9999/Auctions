package com.Lino.auctions;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

public class Auctions extends JavaPlugin implements Listener {
    private Economy economy;
    private Connection connection;
    private FileConfiguration config;
    private Map<UUID, Double> pendingPrices = new HashMap<>();
    private Map<UUID, ItemStack> pendingItems = new HashMap<>();
    private Map<UUID, String> pendingPurchase = new HashMap<>();
    private Map<UUID, String> pendingCancellation = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();

        if (!setupEconomy()) {
            getLogger().severe("Vault dependency not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(this, this);
        initDatabase();

        new BukkitRunnable() {
            @Override
            public void run() {
                checkExpiredAuctions();
            }
        }.runTaskTimer(this, 20L * 60, 20L * 60 * 30);
    }

    @Override
    public void onDisable() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }

    private void initDatabase() {
        try {
            File dbFile = new File(getDataFolder(), "auctions.db");
            if (!dbFile.getParentFile().exists()) {
                dbFile.getParentFile().mkdirs();
            }

            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());

            Statement stmt = connection.createStatement();
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS auctions (" +
                            "id TEXT PRIMARY KEY," +
                            "item BLOB NOT NULL," +
                            "price REAL NOT NULL," +
                            "seller_uuid TEXT NOT NULL," +
                            "seller_name TEXT NOT NULL," +
                            "time BIGINT NOT NULL," +
                            "expired BOOLEAN DEFAULT 0)"
            );

            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("auction") || label.equalsIgnoreCase("ah")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                if (sender.hasPermission("auctions.reload")) {
                    reloadConfig();
                    config = getConfig();
                    sender.sendMessage(getMessage("messages.reload"));
                } else {
                    sender.sendMessage(getMessage("messages.no-permission"));
                }
                return true;
            }

            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;

            if (args.length == 0) {
                openMainAuction(player);
            } else if (args[0].equalsIgnoreCase("expired")) {
                openExpiredAuction(player);
            } else if (args[0].equalsIgnoreCase("sell")) {
                openSellMenu(player);
            }
            return true;
        }
        return false;
    }

    private void openMainAuction(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, getMessage("gui.main-title"));

        for (int i = 0; i < 9; i++) {
            inv.setItem(i, createGlassPane(Material.PURPLE_STAINED_GLASS_PANE, " "));
            inv.setItem(45 + i, createGlassPane(Material.PURPLE_STAINED_GLASS_PANE, " "));
        }

        inv.setItem(49, createMenuItem(Material.EMERALD, getMessage("gui.create-auction"),
                Arrays.asList(getMessage("gui.create-auction-lore").split("\n"))));

        inv.setItem(45, createMenuItem(Material.HOPPER, getMessage("gui.expired-items"),
                Arrays.asList(getMessage("gui.expired-items-lore").split("\n"))));

        inv.setItem(53, createMenuItem(Material.SUNFLOWER, getMessage("gui.refresh"),
                Arrays.asList(getMessage("gui.refresh-lore").split("\n"))));

        try {
            PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM auctions WHERE expired = 0 ORDER BY time DESC"
            );
            ResultSet rs = ps.executeQuery();

            int slot = 9;
            while (rs.next() && slot < 45) {
                String auctionId = rs.getString("id");
                byte[] itemData = rs.getBytes("item");
                double price = rs.getDouble("price");
                String sellerName = rs.getString("seller_name");
                String sellerUUID = rs.getString("seller_uuid");
                long time = rs.getLong("time");

                ItemStack item = deserializeItem(itemData);
                if (item != null) {
                    ItemStack displayItem = item.clone();
                    ItemMeta meta = displayItem.getItemMeta();
                    List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

                    lore.add("");
                    lore.add(ChatColor.DARK_PURPLE + "═══════════════");
                    lore.add(getMessage("gui.price-label") + ChatColor.YELLOW + getCurrencySymbol() + price);
                    lore.add(getMessage("gui.seller-label") + ChatColor.WHITE + sellerName);

                    long timeLeft = time + (config.getLong("auction-duration-hours") * 60 * 60 * 1000) - System.currentTimeMillis();
                    long hoursLeft = timeLeft / (1000 * 60 * 60);
                    lore.add(getMessage("gui.expires-label") + ChatColor.WHITE + hoursLeft + " hours");

                    lore.add(ChatColor.DARK_PURPLE + "═══════════════");

                    if (sellerUUID.equals(player.getUniqueId().toString())) {
                        lore.add(getMessage("gui.click-cancel"));
                    } else {
                        lore.add(getMessage("gui.click-purchase"));
                    }
                    lore.add(ChatColor.DARK_GRAY + "ID: " + auctionId);

                    meta.setLore(lore);
                    displayItem.setItemMeta(meta);
                    inv.setItem(slot, displayItem);
                }
                slot++;
            }

            rs.close();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        player.openInventory(inv);
        playSound(player, "open-menu");
    }

    private void openExpiredAuction(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, getMessage("gui.expired-title"));

        for (int i = 0; i < 9; i++) {
            inv.setItem(i, createGlassPane(Material.RED_STAINED_GLASS_PANE, " "));
            inv.setItem(45 + i, createGlassPane(Material.RED_STAINED_GLASS_PANE, " "));
        }

        inv.setItem(45, createMenuItem(Material.ARROW, getMessage("gui.back"),
                Arrays.asList(getMessage("gui.back-lore").split("\n"))));

        try {
            PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM auctions WHERE expired = 1 AND seller_uuid = ?"
            );
            ps.setString(1, player.getUniqueId().toString());
            ResultSet rs = ps.executeQuery();

            int slot = 9;
            while (rs.next() && slot < 45) {
                String auctionId = rs.getString("id");
                byte[] itemData = rs.getBytes("item");
                double price = rs.getDouble("price");

                ItemStack item = deserializeItem(itemData);
                if (item != null) {
                    ItemStack displayItem = item.clone();
                    ItemMeta meta = displayItem.getItemMeta();
                    List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

                    lore.add("");
                    lore.add(ChatColor.DARK_RED + "═══════════════");
                    lore.add(getMessage("gui.original-price-label") + ChatColor.RED + getCurrencySymbol() + price);
                    lore.add(ChatColor.DARK_RED + "═══════════════");
                    lore.add(getMessage("gui.click-reclaim"));
                    lore.add(ChatColor.DARK_GRAY + "ID: " + auctionId);

                    meta.setLore(lore);
                    displayItem.setItemMeta(meta);
                    inv.setItem(slot, displayItem);
                }
                slot++;
            }

            rs.close();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        player.openInventory(inv);
        playSound(player, "open-menu");
    }

    private void openSellMenu(Player player) {
        int activeAuctions = getPlayerActiveAuctions(player);
        int maxAuctions = config.getInt("max-auctions-per-player");

        if (activeAuctions >= maxAuctions) {
            player.sendMessage(getMessage("messages.max-auctions-reached").replace("{max}", String.valueOf(maxAuctions)));
            playSound(player, "error");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27, getMessage("gui.sell-title"));

        for (int i = 0; i < 27; i++) {
            if (i != 13) {
                inv.setItem(i, createGlassPane(Material.ORANGE_STAINED_GLASS_PANE, " "));
            }
        }

        inv.setItem(22, createMenuItem(Material.EMERALD_BLOCK, getMessage("gui.confirm-sale"),
                Arrays.asList(getMessage("gui.confirm-sale-lore").split("\n"))));

        inv.setItem(18, createMenuItem(Material.BARRIER, getMessage("gui.cancel"),
                Arrays.asList(getMessage("gui.cancel-lore").split("\n"))));

        player.openInventory(inv);
        playSound(player, "open-menu");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player player = (Player) e.getWhoClicked();

        String title = e.getView().getTitle();

        if (title.equals(getMessage("gui.main-title"))) {
            e.setCancelled(true);

            if (e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.AIR) return;

            if (e.getSlot() == 49) {
                openSellMenu(player);
                playSound(player, "button-click");
            } else if (e.getSlot() == 45) {
                openExpiredAuction(player);
                playSound(player, "button-click");
            } else if (e.getSlot() == 53) {
                player.closeInventory();
                player.performCommand("ah");
                playSound(player, "button-click");
            } else if (e.getSlot() >= 9 && e.getSlot() < 45) {
                String auctionId = getAuctionIdFromLore(e.getCurrentItem());
                if (auctionId != null) {
                    checkAuctionOwnership(player, auctionId);
                    playSound(player, "button-click");
                }
            }
        } else if (title.equals(getMessage("gui.expired-title"))) {
            e.setCancelled(true);

            if (e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.AIR) return;

            if (e.getSlot() == 45) {
                openMainAuction(player);
                playSound(player, "button-click");
            } else if (e.getSlot() >= 9 && e.getSlot() < 45) {
                handleExpiredReclaim(player, e.getCurrentItem());
            }
        } else if (title.equals(getMessage("gui.sell-title"))) {
            if (e.getRawSlot() == 13) {
                // Permetti l'inserimento dell'item nello slot 13
                return;
            }

            if (e.getRawSlot() >= 0 && e.getRawSlot() < 27) {
                e.setCancelled(true);

                if (e.getRawSlot() == 22) {
                    ItemStack item = e.getInventory().getItem(13);
                    if (item != null && item.getType() != Material.AIR) {
                        pendingItems.put(player.getUniqueId(), item.clone());
                        e.getInventory().setItem(13, null);
                        player.closeInventory();
                        player.sendMessage(getMessage("messages.enter-price-prompt"));
                        playSound(player, "button-click");
                    } else {
                        player.sendMessage(getMessage("messages.no-item-placed"));
                        playSound(player, "error");
                    }
                } else if (e.getRawSlot() == 18) {
                    player.closeInventory();
                    playSound(player, "button-click");
                }
            }
        } else if (title.contains(getMessage("gui.confirm-purchase-title")) || title.contains(getMessage("gui.cancel-auction-title"))) {
            e.setCancelled(true);

            if (e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.AIR) return;

            if (e.getSlot() == 11 && e.getCurrentItem().getType() == Material.EMERALD_BLOCK) {
                if (title.contains(getMessage("gui.confirm-purchase-title"))) {
                    String auctionId = pendingPurchase.remove(player.getUniqueId());
                    if (auctionId != null) {
                        executePurchase(player, auctionId);
                    }
                } else {
                    String auctionId = pendingCancellation.remove(player.getUniqueId());
                    if (auctionId != null) {
                        cancelAuction(player, auctionId);
                    }
                }
                playSound(player, "success");
            } else if (e.getSlot() == 15 && e.getCurrentItem().getType() == Material.BARRIER) {
                pendingPurchase.remove(player.getUniqueId());
                pendingCancellation.remove(player.getUniqueId());
                player.closeInventory();
                openMainAuction(player);
                playSound(player, "button-click");
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player)) return;
        Player player = (Player) e.getPlayer();

        if (e.getView().getTitle().equals(getMessage("gui.sell-title"))) {
            if (!pendingItems.containsKey(player.getUniqueId())) {
                ItemStack item = e.getInventory().getItem(13);
                if (item != null && item.getType() != Material.AIR) {
                    player.getInventory().addItem(item);
                }
            }
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player player = e.getPlayer();

        if (pendingItems.containsKey(player.getUniqueId())) {
            e.setCancelled(true);

            String message = e.getMessage().trim();

            if (message.equalsIgnoreCase("cancel")) {
                ItemStack item = pendingItems.remove(player.getUniqueId());
                player.getInventory().addItem(item);
                player.sendMessage(getMessage("messages.auction-cancelled"));
                return;
            }

            if (message.startsWith("/")) {
                player.sendMessage(getMessage("messages.cannot-use-commands"));
                return;
            }

            try {
                double price = Double.parseDouble(message);
                if (price <= 0) {
                    player.sendMessage(getMessage("messages.price-too-low"));
                    return;
                }

                if (price > config.getDouble("max-price")) {
                    player.sendMessage(formatMessage(getMessage("messages.price-too-high"), price, config.getDouble("max-price"), "", ""));
                    return;
                }

                ItemStack item = pendingItems.remove(player.getUniqueId());
                String auctionId = UUID.randomUUID().toString();

                PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO auctions (id, item, price, seller_uuid, seller_name, time, expired) VALUES (?, ?, ?, ?, ?, ?, ?)"
                );

                ps.setString(1, auctionId);
                ps.setBytes(2, serializeItem(item));
                ps.setDouble(3, price);
                ps.setString(4, player.getUniqueId().toString());
                ps.setString(5, player.getName());
                ps.setLong(6, System.currentTimeMillis());
                ps.setBoolean(7, false);

                ps.executeUpdate();
                ps.close();

                String itemName = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                        ? item.getItemMeta().getDisplayName()
                        : item.getType().toString();

                for (String line : getMessage("messages.auction-created-broadcast").split("\n")) {
                    Bukkit.broadcastMessage(formatMessage(line, price, 0, player.getName(), itemName));
                }

                for (Player p : Bukkit.getOnlinePlayers()) {
                    playSound(p, "auction-created");
                }

                player.sendMessage(getMessage("messages.auction-created"));

            } catch (NumberFormatException ex) {
                player.sendMessage(getMessage("messages.invalid-price"));
            } catch (SQLException ex) {
                ex.printStackTrace();
                player.sendMessage(getMessage("messages.database-error"));
                pendingItems.remove(player.getUniqueId());
            }
        }
    }

    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent e) {
        Player player = e.getPlayer();

        if (pendingItems.containsKey(player.getUniqueId())) {
            e.setCancelled(true);
            player.sendMessage(getMessage("messages.cannot-use-commands"));
        }
    }

    private void checkAuctionOwnership(Player player, String auctionId) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "SELECT seller_uuid FROM auctions WHERE id = ? AND expired = 0"
            );
            ps.setString(1, auctionId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                String sellerUUID = rs.getString("seller_uuid");
                if (sellerUUID.equals(player.getUniqueId().toString())) {
                    openCancellationConfirm(player, auctionId);
                } else {
                    openPurchaseConfirm(player, auctionId);
                }
            }

            rs.close();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void openPurchaseConfirm(Player player, String auctionId) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM auctions WHERE id = ? AND expired = 0"
            );
            ps.setString(1, auctionId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                ItemStack item = deserializeItem(rs.getBytes("item"));
                double price = rs.getDouble("price");
                String sellerName = rs.getString("seller_name");

                Inventory inv = Bukkit.createInventory(null, 27, getMessage("gui.confirm-purchase-title"));

                for (int i = 0; i < 27; i++) {
                    inv.setItem(i, createGlassPane(Material.BLACK_STAINED_GLASS_PANE, " "));
                }

                inv.setItem(13, item);

                List<String> confirmLore = new ArrayList<>();
                for (String line : getMessage("gui.confirm-purchase-lore").split("\n")) {
                    confirmLore.add(formatMessage(line, price, 0, sellerName, ""));
                }

                inv.setItem(11, createMenuItem(Material.EMERALD_BLOCK, getMessage("gui.confirm"), confirmLore));

                inv.setItem(15, createMenuItem(Material.BARRIER, getMessage("gui.cancel-button"),
                        Arrays.asList(getMessage("gui.return-to-auctions").split("\n"))));

                pendingPurchase.put(player.getUniqueId(), auctionId);
                player.openInventory(inv);
                playSound(player, "open-menu");
            }

            rs.close();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void openCancellationConfirm(Player player, String auctionId) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM auctions WHERE id = ? AND expired = 0"
            );
            ps.setString(1, auctionId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                ItemStack item = deserializeItem(rs.getBytes("item"));
                double price = rs.getDouble("price");

                Inventory inv = Bukkit.createInventory(null, 27, getMessage("gui.cancel-auction-title"));

                for (int i = 0; i < 27; i++) {
                    inv.setItem(i, createGlassPane(Material.RED_STAINED_GLASS_PANE, " "));
                }

                inv.setItem(13, item);

                List<String> cancelLore = new ArrayList<>();
                for (String line : getMessage("gui.cancel-auction-lore").split("\n")) {
                    cancelLore.add(formatMessage(line, price, 0, "", ""));
                }

                inv.setItem(11, createMenuItem(Material.EMERALD_BLOCK, getMessage("gui.cancel-confirm"), cancelLore));

                inv.setItem(15, createMenuItem(Material.BARRIER, getMessage("gui.keep-auction"),
                        Arrays.asList(getMessage("gui.return-to-auctions").split("\n"))));

                pendingCancellation.put(player.getUniqueId(), auctionId);
                player.openInventory(inv);
                playSound(player, "open-menu");
            }

            rs.close();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void executePurchase(Player buyer, String auctionId) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM auctions WHERE id = ? AND expired = 0"
            );
            ps.setString(1, auctionId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                double price = rs.getDouble("price");
                String sellerUUID = rs.getString("seller_uuid");

                if (!economy.has(buyer, price)) {
                    buyer.sendMessage(formatMessage(getMessage("messages.insufficient-funds"), price, 0, "", ""));
                    buyer.closeInventory();
                    playSound(buyer, "error");
                    return;
                }

                ItemStack item = deserializeItem(rs.getBytes("item"));
                if (item == null) return;

                economy.withdrawPlayer(buyer, price);
                OfflinePlayer seller = Bukkit.getOfflinePlayer(UUID.fromString(sellerUUID));
                economy.depositPlayer(seller, price);

                buyer.getInventory().addItem(item);
                buyer.sendMessage(formatMessage(getMessage("messages.purchase-success"), price, 0, "", ""));

                if (seller.isOnline()) {
                    seller.getPlayer().sendMessage(formatMessage(getMessage("messages.item-sold"), price, 0, "", ""));
                    playSound(seller.getPlayer(), "item-sold");
                }

                PreparedStatement deletePs = connection.prepareStatement("DELETE FROM auctions WHERE id = ?");
                deletePs.setString(1, auctionId);
                deletePs.executeUpdate();
                deletePs.close();

                buyer.closeInventory();
                openMainAuction(buyer);
            }

            rs.close();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void cancelAuction(Player player, String auctionId) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM auctions WHERE id = ? AND expired = 0"
            );
            ps.setString(1, auctionId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                ItemStack item = deserializeItem(rs.getBytes("item"));
                if (item != null) {
                    player.getInventory().addItem(item);
                    player.sendMessage(getMessage("messages.auction-cancelled-success"));

                    PreparedStatement deletePs = connection.prepareStatement("DELETE FROM auctions WHERE id = ?");
                    deletePs.setString(1, auctionId);
                    deletePs.executeUpdate();
                    deletePs.close();
                }
            }

            rs.close();
            ps.close();

            player.closeInventory();
            openMainAuction(player);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void handleExpiredReclaim(Player player, ItemStack displayItem) {
        String auctionId = getAuctionIdFromLore(displayItem);
        if (auctionId == null) return;

        try {
            PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM auctions WHERE id = ? AND expired = 1 AND seller_uuid = ?"
            );
            ps.setString(1, auctionId);
            ps.setString(2, player.getUniqueId().toString());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                ItemStack item = deserializeItem(rs.getBytes("item"));
                if (item == null) return;

                player.getInventory().addItem(item);

                PreparedStatement deletePs = connection.prepareStatement("DELETE FROM auctions WHERE id = ?");
                deletePs.setString(1, auctionId);
                deletePs.executeUpdate();
                deletePs.close();

                player.sendMessage(getMessage("messages.item-reclaimed"));
                player.closeInventory();
                openExpiredAuction(player);
                playSound(player, "success");
            }

            rs.close();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private int getPlayerActiveAuctions(Player player) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "SELECT COUNT(*) FROM auctions WHERE seller_uuid = ? AND expired = 0"
            );
            ps.setString(1, player.getUniqueId().toString());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getInt(1);
            }

            rs.close();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    private String getAuctionIdFromLore(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) return null;

        List<String> lore = item.getItemMeta().getLore();
        for (String line : lore) {
            if (line.startsWith(ChatColor.DARK_GRAY + "ID: ")) {
                return line.substring((ChatColor.DARK_GRAY + "ID: ").length());
            }
        }
        return null;
    }

    private void checkExpiredAuctions() {
        long currentTime = System.currentTimeMillis();
        long auctionDuration = config.getLong("auction-duration-hours") * 60 * 60 * 1000;

        try {
            PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM auctions WHERE expired = 0 AND time < ?"
            );
            ps.setLong(1, currentTime - auctionDuration);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                String auctionId = rs.getString("id");
                ItemStack item = deserializeItem(rs.getBytes("item"));
                String sellerUUID = rs.getString("seller_uuid");

                PreparedStatement updatePs = connection.prepareStatement(
                        "UPDATE auctions SET expired = 1 WHERE id = ?"
                );
                updatePs.setString(1, auctionId);
                updatePs.executeUpdate();
                updatePs.close();

                String itemName = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                        ? item.getItemMeta().getDisplayName()
                        : item.getType().toString();

                Player seller = Bukkit.getPlayer(UUID.fromString(sellerUUID));
                if (seller != null && seller.isOnline()) {
                    for (String line : getMessage("messages.auction-expired-notify").split("\n")) {
                        seller.sendMessage(line.replace("{item}", itemName));
                    }
                }
            }

            rs.close();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private String getMessage(String path) {
        return ChatColor.translateAlternateColorCodes('&', config.getString(path, "Message not found: " + path));
    }

    private String getCurrencySymbol() {
        return config.getString("currency-symbol", "$");
    }

    private String formatMessage(String message, double price, double maxPrice, String playerName, String itemName) {
        String currencySymbol = getCurrencySymbol();
        return message
                .replace("{currency}", currencySymbol)
                .replace("{price}", String.valueOf(price))
                .replace("{max}", String.valueOf(maxPrice))
                .replace("{player}", playerName)
                .replace("{item}", itemName)
                .replace("{seller}", playerName);
    }

    private void playSound(Player player, String soundType) {
        if (!config.getBoolean("sounds.enabled")) return;

        String soundPath = "sounds." + soundType;
        if (config.contains(soundPath)) {
            try {
                Sound sound = Sound.valueOf(config.getString(soundPath + ".sound"));
                float volume = (float) config.getDouble(soundPath + ".volume");
                float pitch = (float) config.getDouble(soundPath + ".pitch");
                player.playSound(player.getLocation(), sound, volume, pitch);
            } catch (Exception e) {
                getLogger().warning("Invalid sound configuration for: " + soundType);
            }
        }
    }

    private byte[] serializeItem(ItemStack item) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            dataOutput.writeObject(item);
            dataOutput.close();
            return outputStream.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private ItemStack deserializeItem(byte[] data) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            ItemStack item = (ItemStack) dataInput.readObject();
            dataInput.close();
            return item;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
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
}