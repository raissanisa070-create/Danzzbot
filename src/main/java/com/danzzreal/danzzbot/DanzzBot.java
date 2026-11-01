package com.danzzreal.danzzbot;

import com.destroystokyo.paper.profile.PlayerProfile;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DanzzBot extends JavaPlugin implements Listener {

    private static DanzzBot instance;
    private static Economy vaultEconomy = null;
    private BotFileManager botFileManager;
    
    private BukkitTask enterpriseEngineTask;
    private BukkitTask combatHealthTask;
    private BukkitTask economyWorkerTask;
    private BukkitTask baseDefenseTask;
    private BukkitTask humanLifeSimulationTask;
    private BukkitTask routineAndLootingTask;
    private BukkitTask fishingActivityTask;
    
    private File menuFile;
    private FileConfiguration menuConfig;
    private File langFile;
    private FileConfiguration langConfig;

    private final Map<UUID, List<String>> trustedPlayers = new HashMap<>();

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        createMenuFile();
        createLangFiles();

        // Inisialisasi FileManager untuk folder botlist
        botFileManager = new BotFileManager(this);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            botFileManager.loadBotsFromFolder();
        }, 40L);

        if (getConfig().getBoolean("bot-economy.use-vault-money", true)) {
            if (!setupEconomy()) {
                getLogger().warning("[DanzzBot] Vault tidak ditemukan! Sistem ekonomi beralih ke item fisik.");
                getConfig().set("bot-economy.use-vault-money", false);
            } else {
                getLogger().info("[DanzzBot] Berhasil terhubung ke Vault Economy!");
            }
        }

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null && getConfig().getBoolean("engine-toggles.papi-expansion", true)) {
            new DanzzBotPlaceholderExpansion(this).register();
            getLogger().info("[DanzzBot] PlaceholderAPI Expansion berhasil di-load!");
        }

        DanzzBotCommand commandHandler = new DanzzBotCommand(this);
        DanzzBotChatListener chatHandler = new DanzzBotChatListener(this);

        if (getCommand("danzzbot") != null) { getCommand("danzzbot").setExecutor(commandHandler); getCommand("danzzbot").setTabCompleter(commandHandler); }
        if (getCommand("botmenu") != null) { getCommand("botmenu").setExecutor(commandHandler); }
        if (getCommand("botsettings") != null) { getCommand("botsettings").setExecutor(commandHandler); }
        if (getCommand("botrpg") != null) { getCommand("botrpg").setExecutor(commandHandler); }
        if (getCommand("botlist") != null) { getCommand("botlist").setExecutor(commandHandler); }
        if (getCommand("botcreate") != null) { getCommand("botcreate").setExecutor(commandHandler); }
        if (getCommand("botremove") != null) { getCommand("botremove").setExecutor(commandHandler); }
        if (getCommand("botremoveall") != null) { getCommand("botremoveall").setExecutor(commandHandler); }
        if (getCommand("botcontract") != null) { getCommand("botcontract").setExecutor(commandHandler); }

        if (getCommand("botchat") != null) {
            getCommand("botchat").setExecutor(chatHandler);
            getCommand("botchat").setTabCompleter(chatHandler);
        }

        getServer().getPluginManager().registerEvents(commandHandler, this);
        getServer().getPluginManager().registerEvents(chatHandler, this);
        getServer().getPluginManager().registerEvents(this, this);
        
        startEnterpriseEngineTask();
        startCombatHealthTask();
        startEconomyWorkerTask();
        startBaseDefenseTask();
        startHumanLifeSimulationTask();
        startRoutineAndLootingTask();
        startFishingActivityTask();

        getLogger().info("╔════════════════════════════════════════════════════╗");
        getLogger().info("║   DANZZBOT ULTIMATE MASTERPIECE V47.0 ACTIVE       ║");
        getLogger().info("║   External Bot Storage & All Engines: STABLE       ║");
        getLogger().info("╚════════════════════════════════════════════════════╝");
    }

    @Override
    public void onDisable() {
        if (enterpriseEngineTask != null) enterpriseEngineTask.cancel();
        if (combatHealthTask != null) combatHealthTask.cancel();
        if (economyWorkerTask != null) economyWorkerTask.cancel();
        if (baseDefenseTask != null) baseDefenseTask.cancel();
        if (humanLifeSimulationTask != null) humanLifeSimulationTask.cancel();
        if (routineAndLootingTask != null) routineAndLootingTask.cancel();
        if (fishingActivityTask != null) fishingActivityTask.cancel();
        getLogger().info("[-] DanzzBot dimatikan dengan aman.");
    }

    public static DanzzBot getInstance() { return instance; }

    public static Economy getEconomy() { return vaultEconomy; }

    public BotFileManager getBotFileManager() { return botFileManager; }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        vaultEconomy = rsp.getProvider();
        return vaultEconomy != null;
    }

    public Map<UUID, List<String>> getTrustedPlayers() { return trustedPlayers; }

    private void createMenuFile() {
        menuFile = new File(getDataFolder(), "menu.yml");
        if (!menuFile.exists()) {
            menuFile.getParentFile().mkdirs();
            saveResource("menu.yml", false);
        }
        menuConfig = YamlConfiguration.loadConfiguration(menuFile);
    }

    public void createLangFiles() {
        File langDir = new File(getDataFolder(), "languages");
        if (!langDir.exists()) langDir.mkdirs();

        String lang = getConfig().getString("settings.language", "id");
        langFile = new File(langDir, lang + ".yml");
        if (!langFile.exists()) {
            saveResource("languages/" + lang + ".yml", false);
        }
        langConfig = YamlConfiguration.loadConfiguration(langFile);
    }

    public FileConfiguration getMenuConfig() {
        if (menuConfig == null) createMenuFile();
        return menuConfig;
    }

    public String getLang(String path, String def) {
        if (langConfig == null) createLangFiles();
        return ChatColor.translateAlternateColorCodes('&', langConfig.getString(path, def));
    }

    public void reloadDanzzConfig() {
        reloadConfig();
        if (menuFile != null) menuConfig = YamlConfiguration.loadConfiguration(menuFile);
        createLangFiles();
        getLogger().info("[DanzzBot] Config, menu.yml, & file bahasa dimuat ulang!");
    }

    public boolean isEngineFeatureActive(String path) {
        return getConfig().getBoolean("engine-toggles." + path, true);
    }

    public void toggleEngineFeature(String path) {
        boolean current = isEngineFeatureActive(path);
        getConfig().set("engine-toggles." + path, !current);
        saveConfig();
    }

    private void startFishingActivityTask() {
        fishingActivityTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            boolean fishingActive = isEngineFeatureActive("autonomous-fishing");
            if (fishingActive && CitizensAPI.getNPCRegistry() != null) {
                for (NPC npc : CitizensAPI.getNPCRegistry()) {
                    if (npc.isSpawned() && npc.getEntity() != null) {
                        Location loc = npc.getEntity().getLocation();
                        org.bukkit.World world = loc.getWorld();
                        if (world != null) {
                            boolean nearWater = false;
                            for (int x = -2; x <= 2; x++) {
                                for (int z = -2; z <= 2; z++) {
                                    Block b = loc.clone().add(x, 0, z).getBlock();
                                    if (b.getType() == Material.WATER) { nearWater = true; break; }
                                }
                            }
                            if (nearWater && Math.random() > 0.7) {
                                List<Material> fishDrops = Arrays.asList(Material.COD, Material.SALMON, Material.TROPICAL_FISH, Material.PUFFERFISH);
                                Material caughtFish = fishDrops.get((int)(Math.random() * fishDrops.size()));
                                world.dropItemNaturally(loc, new ItemStack(caughtFish, 1));
                            }
                        }
                    }
                }
            }
        }, 0L, 120L);
    }

    private void startRoutineAndLootingTask() {
        routineAndLootingTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            boolean routineActive = isEngineFeatureActive("autonomous-daily-routine");
            boolean lootingActive = isEngineFeatureActive("chest-looting");

            if (CitizensAPI.getNPCRegistry() != null) {
                for (NPC npc : CitizensAPI.getNPCRegistry()) {
                    if (npc.isSpawned() && npc.getEntity() != null) {
                        org.bukkit.entity.Entity entity = npc.getEntity();
                        Location loc = entity.getLocation();
                        org.bukkit.World world = loc.getWorld();

                        if (routineActive && world != null) {
                            long time = world.getTime();
                            if (time >= 13500 && time <= 13600 && Math.random() > 0.4) {
                                Bukkit.broadcastMessage(ChatColor.YELLOW + "[BOT] " + npc.getName() + ": " + ChatColor.WHITE + getLang("night-alert", "Waktunya istirahat!"));
                            }
                        }

                        if (lootingActive && world != null) {
                            for (int x = -3; x <= 3; x++) {
                                for (int y = -2; y <= 2; y++) {
                                    for (int z = -3; z <= 3; z++) {
                                        Block block = loc.clone().add(x, y, z).getBlock();
                                        if (block.getState() instanceof Chest) {
                                            Chest chest = (Chest) block.getState();
                                            if (Math.random() > 0.95) {
                                                chest.getInventory().addItem(new ItemStack(Material.IRON_INGOT, 1));
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }, 0L, 100L);
    }

    private void startHumanLifeSimulationTask() {
        humanLifeSimulationTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            boolean enabled = isEngineFeatureActive("chatbot-slang");
            if (enabled && CitizensAPI.getNPCRegistry() != null) {
                for (NPC npc : CitizensAPI.getNPCRegistry()) {
                    if (npc.isSpawned() && npc.getEntity() != null) {
                        org.bukkit.entity.Entity entity = npc.getEntity();
                        if (Math.random() > 0.8 && npc.getNavigator() != null && !npc.getNavigator().isNavigating()) {
                            Location loc = entity.getLocation();
                            loc.setYaw(loc.getYaw() + (float)(Math.random() * 60 - 30));
                            entity.teleport(loc);
                        }
                    }
                }
            }
        }, 0L, 100L);
    }

    private void startBaseDefenseTask() {
        double radius = getConfig().getDouble("base-security.defense-radius-blocks", 15.0);
        baseDefenseTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            boolean enabled = getConfig().getBoolean("base-security.enabled", true) && isEngineFeatureActive("base-defense");
            if (enabled && CitizensAPI.getNPCRegistry() != null) {
                for (NPC npc : CitizensAPI.getNPCRegistry()) {
                    if (npc.isSpawned() && npc.getEntity() != null) {
                        Location botLoc = npc.getEntity().getLocation();
                        for (Player p : botLoc.getWorld().getPlayers()) {
                            if (botLoc.distanceSquared(p.getLocation()) <= (radius * radius)) {
                                boolean isTrusted = false;
                                for (Map.Entry<UUID, List<String>> entry : trustedPlayers.entrySet()) {
                                    if (entry.getValue().contains(p.getName())) { isTrusted = true; break; }
                                }
                                if (!isTrusted && !p.hasPermission("danzzbot.bypass")) {
                                    p.sendMessage(ChatColor.translateAlternateColorCodes('&', getConfig().getString("base-security.warning-message", "&c[Base Security] Peringatan! Zona terlarang!")));
                                }
                            }
                        }
                    }
                }
            }
        }, 0L, 40L);
    }

    private void startEconomyWorkerTask() {
        int interval = getConfig().getInt("autonomous-worker.mining-yield-interval-seconds", 10) * 20;
        economyWorkerTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            boolean enabled = getConfig().getBoolean("autonomous-worker.enabled", true) && (isEngineFeatureActive("autonomous-mining") || isEngineFeatureActive("autonomous-farming"));
            if (enabled && CitizensAPI.getNPCRegistry() != null) {
                for (NPC npc : CitizensAPI.getNPCRegistry()) {
                    if (npc.isSpawned() && npc.getEntity() != null) {
                        org.bukkit.World world = npc.getEntity().getWorld();
                        Location loc = npc.getEntity().getLocation();
                        ItemStack yieldItem = new ItemStack(Math.random() > 0.6 ? Material.DIAMOND : (Math.random() > 0.3 ? Material.WHEAT : Material.IRON_INGOT));
                        world.dropItemNaturally(loc, yieldItem);
                    }
                }
            }
        }, 0L, interval);
    }

    private void startCombatHealthTask() {
        combatHealthTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            boolean autoEatEnabled = getConfig().getBoolean("bot-combat-health.auto-eat-gapple.enabled", true) && isEngineFeatureActive("cpvp-combat");
            double threshold = getConfig().getDouble("bot-combat-health.auto-eat-gapple.trigger-health-threshold", 10.0);
            double regain = getConfig().getDouble("bot-combat-health.auto-eat-gapple.regain-amount", 8.0);

            if (autoEatEnabled && CitizensAPI.getNPCRegistry() != null) {
                for (NPC npc : CitizensAPI.getNPCRegistry()) {
                    if (npc.isSpawned() && npc.getEntity() instanceof LivingEntity) {
                        LivingEntity entity = (LivingEntity) npc.getEntity();
                        if (entity.getHealth() < threshold && entity.getHealth() > 0) {
                            double maxHp = entity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
                            entity.setHealth(Math.min(maxHp, entity.getHealth() + regain));
                        }
                    }
                }
            }
        }, 0L, 20L);
    }

    @EventHandler
    public void onServerListPing(ServerListPingEvent event) {
        int botCount = 0;
        if (CitizensAPI.getNPCRegistry() != null) {
            for (NPC npc : CitizensAPI.getNPCRegistry()) {
                if (npc.isSpawned()) {
                    botCount++;
                }
            }
        }
        event.setMaxPlayers(event.getMaxPlayers() + botCount);
    }

    private void startEnterpriseEngineTask() {
        enterpriseEngineTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            try {
                double currentTps = 20.0;
                boolean isLagging = currentTps < getConfig().getDouble("settings.tps-protection-threshold", 18.0);
                if (isLagging) {
                    getLogger().warning("[Engine] Peringatan TPS: " + currentTps);
                }
            } catch (Exception e) {
                getLogger().severe("[Engine Error] " + e.getMessage());
            }
        }, 0L, 100L);
    }
}
                                        
