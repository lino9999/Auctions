package com.Lino.auctions.database;

import com.Lino.auctions.Auctions;
import com.Lino.auctions.models.AuctionData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DatabaseManager {
    private final Auctions plugin;
    private Connection connection;

    public DatabaseManager(Auctions plugin) {
        this.plugin = plugin;
    }

    public boolean initialize() {
        try {
            File dbFile = new File(plugin.getDataFolder(), "auctions.db");
            if (!dbFile.getParentFile().exists()) {
                dbFile.getParentFile().mkdirs();
            }

            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            createTables();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialize database: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void createTables() throws SQLException {
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
    }

    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean createAuction(AuctionData auction) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO auctions (id, item, price, seller_uuid, seller_name, time, expired) VALUES (?, ?, ?, ?, ?, ?, ?)"
            );
            ps.setString(1, auction.getId());
            ps.setBytes(2, serializeItem(auction.getItem()));
            ps.setDouble(3, auction.getPrice());
            ps.setString(4, auction.getSellerUUID().toString());
            ps.setString(5, auction.getSellerName());
            ps.setLong(6, auction.getCreationTime());
            ps.setBoolean(7, auction.isExpired());

            int result = ps.executeUpdate();
            ps.close();
            return result > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public List<AuctionData> getActiveAuctions() {
        List<AuctionData> auctions = new ArrayList<>();
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM auctions WHERE expired = 0 ORDER BY time DESC"
            );
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                AuctionData auction = createAuctionFromResultSet(rs);
                if (auction != null) {
                    auctions.add(auction);
                }
            }

            rs.close();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return auctions;
    }

    public List<AuctionData> getExpiredAuctions(UUID playerUUID) {
        List<AuctionData> auctions = new ArrayList<>();
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM auctions WHERE expired = 1 AND seller_uuid = ?"
            );
            ps.setString(1, playerUUID.toString());
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                AuctionData auction = createAuctionFromResultSet(rs);
                if (auction != null) {
                    auctions.add(auction);
                }
            }

            rs.close();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return auctions;
    }

    public AuctionData getAuction(String auctionId) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM auctions WHERE id = ?"
            );
            ps.setString(1, auctionId);
            ResultSet rs = ps.executeQuery();

            AuctionData auction = null;
            if (rs.next()) {
                auction = createAuctionFromResultSet(rs);
            }

            rs.close();
            ps.close();
            return auction;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean deleteAuction(String auctionId) {
        try {
            PreparedStatement ps = connection.prepareStatement("DELETE FROM auctions WHERE id = ?");
            ps.setString(1, auctionId);
            int result = ps.executeUpdate();
            ps.close();
            return result > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean expireAuction(String auctionId) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "UPDATE auctions SET expired = 1 WHERE id = ?"
            );
            ps.setString(1, auctionId);
            int result = ps.executeUpdate();
            ps.close();
            return result > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public int getPlayerActiveAuctionCount(UUID playerUUID) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "SELECT COUNT(*) FROM auctions WHERE seller_uuid = ? AND expired = 0"
            );
            ps.setString(1, playerUUID.toString());
            ResultSet rs = ps.executeQuery();

            int count = 0;
            if (rs.next()) {
                count = rs.getInt(1);
            }

            rs.close();
            ps.close();
            return count;
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public List<AuctionData> getExpiredAuctionsBefore(long timeThreshold) {
        List<AuctionData> auctions = new ArrayList<>();
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM auctions WHERE expired = 0 AND time < ?"
            );
            ps.setLong(1, timeThreshold);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                AuctionData auction = createAuctionFromResultSet(rs);
                if (auction != null) {
                    auctions.add(auction);
                }
            }

            rs.close();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return auctions;
    }

    private AuctionData createAuctionFromResultSet(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        ItemStack item = deserializeItem(rs.getBytes("item"));
        double price = rs.getDouble("price");
        UUID sellerUUID = UUID.fromString(rs.getString("seller_uuid"));
        String sellerName = rs.getString("seller_name");
        long creationTime = rs.getLong("time");
        boolean expired = rs.getBoolean("expired");

        if (item == null) return null;

        return new AuctionData(id, item, price, sellerUUID, sellerName, creationTime, expired);
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
}