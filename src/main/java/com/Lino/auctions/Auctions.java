package com.Lino.auctions;

import com.Lino.auctions.commands.AuctionCommand;
import com.Lino.auctions.database.DatabaseManager;
import com.Lino.auctions.economy.EconomyManager;
import com.Lino.auctions.gui.GuiManager;
import com.Lino.auctions.listeners.ChatListener;
import com.Lino.auctions.listeners.InventoryListener;
import com.Lino.auctions.managers.AuctionManager;
import com.Lino.auctions.managers.ConfigManager;
import com.Lino.auctions.managers.MessageManager;
import com.Lino.auctions.managers.SoundManager;
import com.Lino.auctions.tasks.AuctionCleanupTask;
import org.bukkit.plugin.java.JavaPlugin;

public class Auctions extends JavaPlugin {

    private static Auctions instance;

    // Manager instances
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private EconomyManager economyManager;
    private MessageManager messageManager;
    private SoundManager soundManager;
    private AuctionManager auctionManager;
    private GuiManager guiManager;

    @Override
    public void onEnable() {
        instance = this;

        // Initialize managers in correct order
        if (!initializeManagers()) {
            getLogger().severe("Failed to initialize managers! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Register commands and listeners
        registerCommandsAndListeners();

        // Start cleanup task
        new AuctionCleanupTask(this).start();

        getLogger().info("Auctions plugin has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("Auctions plugin has been disabled!");
    }

    private boolean initializeManagers() {
        try {
            // Config must be first
            configManager = new ConfigManager(this);

            // Database initialization
            databaseManager = new DatabaseManager(this);
            if (!databaseManager.initialize()) {
                return false;
            }

            // Economy setup
            economyManager = new EconomyManager(this);
            if (!economyManager.setupEconomy()) {
                return false;
            }

            // Message system
            messageManager = new MessageManager(this);

            // Sound system
            soundManager = new SoundManager(this);

            // Core auction logic
            auctionManager = new AuctionManager(this);

            // GUI system
            guiManager = new GuiManager(this);

            return true;
        } catch (Exception e) {
            getLogger().severe("Error initializing managers: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void registerCommandsAndListeners() {
        // Register commands
        getCommand("auction").setExecutor(new AuctionCommand(this));
        getCommand("ah").setExecutor(new AuctionCommand(this));

        // Register event listeners
        getServer().getPluginManager().registerEvents(new InventoryListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
    }

    // Static access to plugin instance
    public static Auctions getInstance() {
        return instance;
    }

    // Getter methods for managers
    public ConfigManager getConfigManager() { return configManager; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public MessageManager getMessageManager() { return messageManager; }
    public SoundManager getSoundManager() { return soundManager; }
    public AuctionManager getAuctionManager() { return auctionManager; }
    public GuiManager getGuiManager() { return guiManager; }
}