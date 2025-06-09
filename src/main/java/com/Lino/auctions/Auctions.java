package com.Lino.auctions;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
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
    private Map<UUID, Double> pendingPrices = new HashMap<>();
    private Map<UUID, ItemStack> pendingItems = new HashMap<>();
    private Map<UUID, LocalDateTime> lastClaim = new HashMap<>();
    private Map<UUID, String> pendingPurchase = new HashMap<>();
    private Map<UUID, String> pendingCancellation = new HashMap<>();

    @Override
    public void onEnable() {
        if (!setupEconomy()) {
            getLogger().severe("Vault dependency not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(this, this);
        initDatabase();
        loadClaimData();

        new BukkitRunnable() {
            @Override
            public void run() {
                checkExpiredAuctions();
            }
        }.runTaskTimer(this, 20L * 60, 20L * 60 * 30);
    }

    @Override
    public void onDisable() {
        saveClaimData();
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

            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS claims (" +
                            "player_uuid TEXT PRIMARY KEY," +
                            "last_claim TEXT NOT NULL)"
            );

            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void loadClaimData() {
        try {
            PreparedStatement ps = connection.prepareStatement("SELECT * FROM claims");
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                LocalDateTime time = LocalDateTime.parse(rs.getString("last_claim"));
                lastClaim.put(uuid, time);
            }

            rs.close();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void saveClaimData() {
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "REPLACE INTO claims (player_uuid, last_claim) VALUES (?, ?)"
            );

            for (Map.Entry<UUID, LocalDateTime> entry : lastClaim.entrySet()) {
                ps.setString(1, entry.getKey().toString());
                ps.setString(2, entry.getValue().toString());
                ps.executeUpdate();
            }

            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;

        if (label.equalsIgnoreCase("auction") || label.equalsIgnoreCase("ah")) {
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
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_PURPLE + "✦ " + ChatColor.LIGHT_PURPLE + "Active Auctions" + ChatColor.DARK_PURPLE + " ✦");

        for (int i = 0; i < 9; i++) {
            inv.setItem(i, createGlassPane(Material.PURPLE_STAINED_GLASS_PANE, " "));
            inv.setItem(45 + i, createGlassPane(Material.PURPLE_STAINED_GLASS_PANE, " "));
        }

        inv.setItem(49, createMenuItem(Material.EMERALD, ChatColor.GREEN + "✦ Create Auction",
                Arrays.asList(ChatColor.GRAY + "Click to sell an item", ChatColor.YELLOW + "Left-Click to open")));

        inv.setItem(45, createMenuItem(Material.HOPPER, ChatColor.YELLOW + "☀ Expired Items",
                Arrays.asList(ChatColor.GRAY + "View expired auctions", ChatColor.AQUA + "1 free claim per day!")));

        inv.setItem(53, createMenuItem(Material.SUNFLOWER, ChatColor.AQUA + "⟳ Refresh",
                Arrays.asList(ChatColor.GRAY + "Click to update the list", ChatColor.YELLOW + "See latest auctions!")));

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
                    lore.add(ChatColor.GOLD + "✦ Price: " + ChatColor.YELLOW + "$" + price);
                    lore.add(ChatColor.AQUA + "✦ Seller: " + ChatColor.WHITE + sellerName);

                    long timeLeft = time + (3 * 24 * 60 * 60 * 1000) - System.currentTimeMillis();
                    long hoursLeft = timeLeft / (1000 * 60 * 60);
                    lore.add(ChatColor.RED + "✦ Expires in: " + ChatColor.WHITE + hoursLeft + " hours");

                    lore.add(ChatColor.DARK_PURPLE + "═══════════════");

                    if (sellerUUID.equals(player.getUniqueId().toString())) {
                        lore.add(ChatColor.RED + "» Click to cancel auction!");
                    } else {
                        lore.add(ChatColor.GREEN + "» Click to purchase!");
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
    }

    private void openExpiredAuction(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_RED + "☠ " + ChatColor.RED + "Expired Auctions" + ChatColor.DARK_RED + " ☠");

        for (int i = 0; i < 9; i++) {
            inv.setItem(i, createGlassPane(Material.RED_STAINED_GLASS_PANE, " "));
            inv.setItem(45 + i, createGlassPane(Material.RED_STAINED_GLASS_PANE, " "));
        }

        inv.setItem(45, createMenuItem(Material.ARROW, ChatColor.YELLOW + "« Back",
                Arrays.asList(ChatColor.GRAY + "Return to main menu")));

        LocalDateTime lastClaimTime = lastClaim.get(player.getUniqueId());
        boolean canClaim = lastClaimTime == null || !lastClaimTime.toLocalDate().equals(LocalDateTime.now().toLocalDate());

        inv.setItem(49, createMenuItem(Material.CHEST, ChatColor.GOLD + "✦ Daily Claim",
                Arrays.asList(
                        ChatColor.GRAY + "Free claim: " + (canClaim ? ChatColor.GREEN + "Available" : ChatColor.RED + "Used today"),
                        ChatColor.YELLOW + "Resets at midnight"
                )));

        try {
            PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM auctions WHERE expired = 1"
            );
            ResultSet rs = ps.executeQuery();

            int slot = 9;
            while (rs.next() && slot < 45) {
                String auctionId = rs.getString("id");
                byte[] itemData = rs.getBytes("item");
                double price = rs.getDouble("price");
                String sellerName = rs.getString("seller_name");

                ItemStack item = deserializeItem(itemData);
                if (item != null) {
                    ItemStack displayItem = item.clone();
                    ItemMeta meta = displayItem.getItemMeta();
                    List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

                    lore.add("");
                    lore.add(ChatColor.DARK_RED + "═══════════════");
                    lore.add(ChatColor.GRAY + "✦ Original Price: " + ChatColor.RED + "$" + price);
                    lore.add(ChatColor.GRAY + "✦ Seller: " + ChatColor.WHITE + sellerName);
                    lore.add(ChatColor.DARK_RED + "═══════════════");

                    if (canClaim) {
                        lore.add(ChatColor.GREEN + "» Click to claim FREE!");
                    } else {
                        lore.add(ChatColor.RED + "✘ Daily claim used");
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
    }

    private void openSellMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.GOLD + "♦ " + ChatColor.YELLOW + "Sell Item" + ChatColor.GOLD + " ♦");

        for (int i = 0; i < 27; i++) {
            if (i != 13) {
                inv.setItem(i, createGlassPane(Material.ORANGE_STAINED_GLASS_PANE, " "));
            }
        }

        inv.setItem(22, createMenuItem(Material.EMERALD_BLOCK, ChatColor.GREEN + "✔ Confirm Sale",
                Arrays.asList(ChatColor.GRAY + "Click after placing item", ChatColor.YELLOW + "You'll set the price next")));

        inv.setItem(18, createMenuItem(Material.BARRIER, ChatColor.RED + "✘ Cancel",
                Arrays.asList(ChatColor.GRAY + "Return to auctions")));

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player player = (Player) e.getWhoClicked();

        String title = e.getView().getTitle();

        if (title.contains("Active Auctions")) {
            e.setCancelled(true);

            if (e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.AIR) return;

            if (e.getSlot() == 49) {
                openSellMenu(player);
            } else if (e.getSlot() == 45) {
                openExpiredAuction(player);
            } else if (e.getSlot() == 53) {
                player.closeInventory();
                player.performCommand("ah");
            } else if (e.getSlot() >= 9 && e.getSlot() < 45) {
                String auctionId = getAuctionIdFromLore(e.getCurrentItem());
                if (auctionId != null) {
                    checkAuctionOwnership(player, auctionId);
                }
            }
        } else if (title.contains("Expired Auctions")) {
            e.setCancelled(true);

            if (e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.AIR) return;

            if (e.getSlot() == 45) {
                openMainAuction(player);
            } else if (e.getSlot() >= 9 && e.getSlot() < 45) {
                handleExpiredClaim(player, e.getCurrentItem());
            }
        } else if (title.contains("Sell Item")) {
            if (e.getRawSlot() >= 0 && e.getRawSlot() < 27) {
                if (e.getRawSlot() == 13) {
                    return;
                }

                e.setCancelled(true);

                if (e.getRawSlot() == 22) {
                    ItemStack item = e.getInventory().getItem(13);
                    if (item != null && item.getType() != Material.AIR) {
                        pendingItems.put(player.getUniqueId(), item.clone());
                        e.getInventory().setItem(13, null);
                        player.closeInventory();
                        player.sendMessage(ChatColor.GOLD + "✦ Auction Setup ✦");
                        player.sendMessage(ChatColor.YELLOW + "Please enter the price for your item in chat:");
                        player.sendMessage(ChatColor.GRAY + "Type 'cancel' to cancel the auction");
                    } else {
                        player.sendMessage(ChatColor.RED + "✘ Please place an item first!");
                    }
                } else if (e.getRawSlot() == 18) {
                    player.closeInventory();
                }
            }
        } else if (title.contains("Confirm Purchase") || title.contains("Cancel Auction")) {
            e.setCancelled(true);

            if (e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.AIR) return;

            if (e.getSlot() == 11 && e.getCurrentItem().getType() == Material.EMERALD_BLOCK) {
                if (title.contains("Confirm Purchase")) {
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
            } else if (e.getSlot() == 15 && e.getCurrentItem().getType() == Material.BARRIER) {
                pendingPurchase.remove(player.getUniqueId());
                pendingCancellation.remove(player.getUniqueId());
                player.closeInventory();
                openMainAuction(player);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player)) return;
        Player player = (Player) e.getPlayer();

        if (e.getView().getTitle().contains("Sell Item")) {
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
                player.sendMessage(ChatColor.RED + "✘ Auction cancelled! Item returned.");
                return;
            }

            if (message.startsWith("/")) {
                player.sendMessage(ChatColor.RED + "✘ Invalid action! You must enter a price or type 'cancel'");
                player.sendMessage(ChatColor.YELLOW + "Please enter the price for your item:");
                return;
            }

            try {
                double price = Double.parseDouble(message);
                if (price <= 0) {
                    player.sendMessage(ChatColor.RED + "✘ Price must be greater than 0!");
                    player.sendMessage(ChatColor.YELLOW + "Please enter a valid price:");
                    return;
                }

                if (price > 999999999) {
                    player.sendMessage(ChatColor.RED + "✘ Price is too high! Maximum: $999,999,999");
                    player.sendMessage(ChatColor.YELLOW + "Please enter a valid price:");
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

                Bukkit.broadcastMessage(ChatColor.GOLD + "═══════════════════════════");
                Bukkit.broadcastMessage(ChatColor.YELLOW + "✦ NEW AUCTION ✦");
                Bukkit.broadcastMessage(ChatColor.WHITE + player.getName() + ChatColor.GRAY + " is selling " + ChatColor.AQUA + itemName);
                Bukkit.broadcastMessage(ChatColor.GOLD + "Price: " + ChatColor.YELLOW + "$" + price);
                Bukkit.broadcastMessage(ChatColor.GRAY + "Type " + ChatColor.YELLOW + "/ah" + ChatColor.GRAY + " to view!");
                Bukkit.broadcastMessage(ChatColor.GOLD + "═══════════════════════════");

                player.sendMessage(ChatColor.GREEN + "✔ Your auction has been created successfully!");

            } catch (NumberFormatException ex) {
                player.sendMessage(ChatColor.RED + "✘ Invalid input! You must enter a number or 'cancel'");
                player.sendMessage(ChatColor.YELLOW + "Please enter the price for your item:");
            } catch (SQLException ex) {
                ex.printStackTrace();
                player.sendMessage(ChatColor.RED + "✘ Database error! Please try again.");
                pendingItems.remove(player.getUniqueId());
            }
        }
    }

    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent e) {
        Player player = e.getPlayer();

        if (pendingItems.containsKey(player.getUniqueId())) {
            e.setCancelled(true);
            player.sendMessage(ChatColor.RED + "✘ You cannot use commands while setting a price!");
            player.sendMessage(ChatColor.YELLOW + "Enter a price or type 'cancel' to exit.");
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

                Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_GREEN + "✦ Confirm Purchase ✦");

                for (int i = 0; i < 27; i++) {
                    inv.setItem(i, createGlassPane(Material.BLACK_STAINED_GLASS_PANE, " "));
                }

                inv.setItem(13, item);

                inv.setItem(11, createMenuItem(Material.EMERALD_BLOCK, ChatColor.GREEN + "✔ CONFIRM",
                        Arrays.asList(
                                ChatColor.GRAY + "Price: " + ChatColor.GOLD + "$" + price,
                                ChatColor.GRAY + "Seller: " + ChatColor.WHITE + sellerName,
                                ChatColor.YELLOW + "Click to purchase!"
                        )));

                inv.setItem(15, createMenuItem(Material.BARRIER, ChatColor.RED + "✘ CANCEL",
                        Arrays.asList(ChatColor.GRAY + "Return to auctions")));

                pendingPurchase.put(player.getUniqueId(), auctionId);
                player.openInventory(inv);
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

                Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_RED + "☠ Cancel Auction? ☠");

                for (int i = 0; i < 27; i++) {
                    inv.setItem(i, createGlassPane(Material.RED_STAINED_GLASS_PANE, " "));
                }

                inv.setItem(13, item);

                inv.setItem(11, createMenuItem(Material.EMERALD_BLOCK, ChatColor.RED + "✔ CANCEL AUCTION",
                        Arrays.asList(
                                ChatColor.GRAY + "Current Price: " + ChatColor.GOLD + "$" + price,
                                ChatColor.YELLOW + "Item will be returned",
                                ChatColor.RED + "This cannot be undone!"
                        )));

                inv.setItem(15, createMenuItem(Material.BARRIER, ChatColor.GREEN + "✘ KEEP AUCTION",
                        Arrays.asList(ChatColor.GRAY + "Return to auctions")));

                pendingCancellation.put(player.getUniqueId(), auctionId);
                player.openInventory(inv);
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
                    buyer.sendMessage(ChatColor.RED + "✘ Insufficient funds! You need $" + price);
                    buyer.closeInventory();
                    return;
                }

                ItemStack item = deserializeItem(rs.getBytes("item"));
                if (item == null) return;

                economy.withdrawPlayer(buyer, price);
                OfflinePlayer seller = Bukkit.getOfflinePlayer(UUID.fromString(sellerUUID));
                economy.depositPlayer(seller, price);

                buyer.getInventory().addItem(item);
                buyer.sendMessage(ChatColor.GREEN + "✔ Successfully purchased item for $" + price + "!");

                if (seller.isOnline()) {
                    seller.getPlayer().sendMessage(ChatColor.GREEN + "✦ Your item was sold for $" + price + "!");
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
                    player.sendMessage(ChatColor.GREEN + "✔ Auction cancelled! Item returned to your inventory.");

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

    private void handleExpiredClaim(Player player, ItemStack displayItem) {
        LocalDateTime lastClaimTime = lastClaim.get(player.getUniqueId());
        boolean canClaim = lastClaimTime == null || !lastClaimTime.toLocalDate().equals(LocalDateTime.now().toLocalDate());

        if (!canClaim) {
            player.sendMessage(ChatColor.RED + "✘ You have already claimed your free item today!");
            return;
        }

        String auctionId = getAuctionIdFromLore(displayItem);
        if (auctionId == null) return;

        try {
            PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM auctions WHERE id = ? AND expired = 1"
            );
            ps.setString(1, auctionId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                ItemStack item = deserializeItem(rs.getBytes("item"));
                if (item == null) return;

                player.getInventory().addItem(item);
                lastClaim.put(player.getUniqueId(), LocalDateTime.now());

                PreparedStatement deletePs = connection.prepareStatement("DELETE FROM auctions WHERE id = ?");
                deletePs.setString(1, auctionId);
                deletePs.executeUpdate();
                deletePs.close();

                player.sendMessage(ChatColor.GREEN + "✔ Successfully claimed your free daily item!");
                player.closeInventory();
                openExpiredAuction(player);
            }

            rs.close();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
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
        long threeDays = 3 * 24 * 60 * 60 * 1000;

        try {
            PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM auctions WHERE expired = 0 AND time < ?"
            );
            ps.setLong(1, currentTime - threeDays);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                String auctionId = rs.getString("id");
                ItemStack item = deserializeItem(rs.getBytes("item"));

                PreparedStatement updatePs = connection.prepareStatement(
                        "UPDATE auctions SET expired = 1 WHERE id = ?"
                );
                updatePs.setString(1, auctionId);
                updatePs.executeUpdate();
                updatePs.close();

                String itemName = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                        ? item.getItemMeta().getDisplayName()
                        : item.getType().toString();

                Bukkit.broadcastMessage(ChatColor.RED + "═══════════════════════════");
                Bukkit.broadcastMessage(ChatColor.DARK_RED + "☠ AUCTION EXPIRED ☠");
                Bukkit.broadcastMessage(ChatColor.GRAY + "Item: " + ChatColor.WHITE + itemName);
                Bukkit.broadcastMessage(ChatColor.YELLOW + "Now available for FREE claim!");
                Bukkit.broadcastMessage(ChatColor.GRAY + "Type " + ChatColor.YELLOW + "/ah expired" + ChatColor.GRAY + " to claim!");
                Bukkit.broadcastMessage(ChatColor.AQUA + "Limited: 1 free claim per day");
                Bukkit.broadcastMessage(ChatColor.RED + "═══════════════════════════");
            }

            rs.close();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
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