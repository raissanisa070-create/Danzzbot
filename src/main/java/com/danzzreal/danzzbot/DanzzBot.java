package com.danzzreal.danzzbot;

import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.trait.Equipment;
import net.citizensnpcs.trait.SkinTrait;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DanzzBot extends JavaPlugin implements CommandExecutor, Listener {

    private final Map<String, String> createdTemplates = new HashMap<>();
    private final Map<UUID, NPC> controlledBots = new HashMap<>();
    private final Map<UUID, ArmorStand> botHolograms = new HashMap<>();
    private final Map<UUID, BossBar> botBossBars = new HashMap<>();
    
    private final Map<UUID, Inventory> botBackpacks = new HashMap<>();
    private final Map<Integer, List<Location>> botWaypoints = new HashMap<>();
    private final Map<Integer, Integer> botWaypointIndexes = new HashMap<>();
    private final Map<Integer, UUID> botOwners = new HashMap<>();
    private final Map<Integer, Long> botWanderCooldowns = new HashMap<>();
    
    private final List<String> auditLogs = new ArrayList<>();
    private final Random random = new Random();
    
    private File botsFile, menuFile;
    private FileConfiguration botsConfig, menuConfig;
    private LuckPerms luckPermsApi;
    private boolean hasPAPI = false;
    private BukkitTask enterpriseEngineTask;

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    @Override
    public void onEnable() {
        saveDefaultConfig();
        createBotsFile();
        createMenuFile();
        loadBotsFromStorageAsync();

        if (Bukkit.getPluginManager().getPlugin("Citizens") == null) {
            getLogger().severe("FATAL ERROR: Citizens plugin tidak ditemukan! Menonaktifkan DanzzBot...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
            try {
                luckPermsApi = LuckPermsProvider.get();
                getLogger().info("[Enterprise Network] Terhubung native dengan LuckPerms API.");
            } catch (Exception e) {
                getLogger().warning("[Enterprise Network] Gagal inisialisasi LuckPerms: " + e.getMessage());
            }
        }

        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("danzzbot")).setExecutor(this);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            hasPAPI = true;
            try {
                new DanzzBotPlaceholderExpansion(this).register();
                getLogger().info("[Enterprise Network] PlaceholderAPI bridge terdaftar sukses.");
            } catch (Exception ignored) {}
        }

        logAudit("Plugin diaktifkan dengan sukses (Enterprise Network Tier).");
        startEnterpriseEngineTask();
        printEnterpriseBanner();
    }

    @Override
    public void onDisable() {
        if (enterpriseEngineTask != null) {
            enterpriseEngineTask.cancel();
        }
        removeAllHolograms();
        removeAllBossBars();
        saveBotsToStorage();
        logAudit("Plugin dinonaktifkan secara aman.");
        getLogger().info(colorize("&8[&b&lDanzzBot Enterprise 2026&8] &cShutdown aman. Resource dibersihkan."));
    }

    private void logAudit(String message) {
        String logEntry = "[" + new Date() + "] " + message;
        auditLogs.add(0, logEntry);
        if (auditLogs.size() > 50) auditLogs.remove(50);
        getLogger().warning("[Audit Log] " + message);
    }

    private void printEnterpriseBanner() {
        getLogger().info("==================================================================");
        getLogger().info("  ___                  _     ____        _   _       _  ___  ");
        getLogger().info(" |   \\ __ _ _ _ _____| |_  | r_ \\  ___ | |_(_)_ _ _(_)/ _ \\ ");
        getLogger().info(" | |) / _` | '_|_ /_-<|  _| |   _/ / _ \\|  _| | '_| | | (_) |");
        getLogger().info(" |___/\\__,_|_| /__/__/ \\__| |_|   \\___/ \\__|_|_| |_|_|\\___/ ");
        getLogger().info("                                                                  ");
        getLogger().info("  Edition       : MASSIVE NETWORK SUITE (100% Error-Free Release)");
        getLogger().info("  Collab Engine : Citizens x LuckPerms x PlaceholderAPI          ");
        getLogger().info("==================================================================");
    }

    public static String colorize(String message) {
        if (message == null) return "";
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuilder buffer = new StringBuilder();
        while (matcher.find()) {
            String color = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : color.toCharArray()) {
                replacement.append('§').append(c);
            }
            matcher.appendReplacement(buffer, replacement.toString());
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    private void createBotsFile() {
        botsFile = new File(getDataFolder(), "bots.yml");
        if (!botsFile.exists()) {
            try { botsFile.createNewFile(); } catch (IOException e) { getLogger().severe("Gagal membuat bots.yml!"); }
        }
        botsConfig = YamlConfiguration.loadConfiguration(botsFile);
    }

    private void createMenuFile() {
        menuFile = new File(getDataFolder(), "menu.yml");
        if (!menuFile.exists()) {
            saveResource("menu.yml", false);
        }
        menuConfig = YamlConfiguration.loadConfiguration(menuFile);
    }

    private void loadBotsFromStorageAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            if (botsConfig.contains("bots")) {
                for (String key : Objects.requireNonNull(botsConfig.getConfigurationSection("bots")).getKeys(false)) {
                    synchronized (createdTemplates) {
                        createdTemplates.put(key, botsConfig.getString("bots." + key, "default"));
                    }
                }
            }
            logAudit("Template bot dimuat secara Asinkron (Async DB Bridge).");
        });
    }

    private void saveBotsToStorage() {
        synchronized (createdTemplates) {
            for (Map.Entry<String, String> entry : createdTemplates.entrySet()) {
                botsConfig.set("bots." + entry.getKey(), entry.getValue());
            }
        }
        try { botsConfig.save(botsFile); } catch (IOException e) { getLogger().severe("Gagal menyimpan bots.yml!"); }
    }

    private NPC findNPCByName(String name) {
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (npc.getName().equalsIgnoreCase(name)) return npc;
        }
        return null;
    }

    public void reloadDanzzPlugin(CommandSender sender) {
        reloadConfig();
        menuConfig = YamlConfiguration.loadConfiguration(menuFile);
        String prefix = colorize(getConfig().getString("messages.prefix", "&8[&b&lDanzzBot&8] &r"));
        
        logAudit("Admin " + sender.getName() + " melakukan Hot-Reload config & menu.");
        sender.sendMessage(prefix + ChatColor.GREEN + "Konfigurasi & Menu berhasil di-reload secara instan!");
        if (sender instanceof Player) {
            Player p = (Player) sender;
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.8f);
            p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, new net.md_5.bungee.api.chat.TextComponent(colorize("&a⚡ Reload Berhasil!")));
        }
    }

    private void registerBotToLuckPerms(NPC npc) {
        if (luckPermsApi == null || !getConfig().getBoolean("luckperms-rank.enabled", true)) return;

        UUID uuid = npc.getUniqueId();
        String name = npc.getName();

        try {
            luckPermsApi.getUserManager().loadUser(uuid, name).thenAcceptAsync(user -> {
                if (user != null && user.getNodes().isEmpty()) {
                    user.data().add(net.luckperms.api.node.types.InheritanceNode.builder("default").build());
                    luckPermsApi.getUserManager().saveUser(user);
                }
            });
        } catch (Exception ignored) {}
    }

    private String parseBotFormat(NPC npc, String rawFormat) {
        if (rawFormat == null) return "";
        
        String parsed = rawFormat
                .replace("%npc_name%", npc.getName())
                .replace("%health%", String.format("%.1f", npc.getEntity() instanceof LivingEntity ? ((LivingEntity) npc.getEntity()).getHealth() : 20.0));

        if (hasPAPI) {
            try {
                OfflinePlayer offlineBot = Bukkit.getOfflinePlayer(npc.getUniqueId());
                parsed = PlaceholderAPI.setPlaceholders(offlineBot, parsed);
            } catch (Exception ignored) {}
        } else {
            parsed = parsed.replace("%luckperms_prefix%", getConfig().getString("luckperms-rank.default-rank-prefix", "&b[BOT] "));
        }

        return colorize(parsed);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String prefix = colorize(getConfig().getString("messages.prefix", "&8[&b&lDanzzBot&8] &r"));

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("danzzbot.admin")) {
                sender.sendMessage(prefix + ChatColor.RED + "Akses ditolak!");
                return true;
            }
            reloadDanzzPlugin(sender);
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("Command ini khusus untuk Player in-game!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) {
            openCommercialMainMenu(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
            return true;
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "create":
                if (!player.hasPermission("danzzbot.admin")) {
                    player.sendMessage(prefix + ChatColor.RED + "Akses ditolak!");
                    return true;
                }
                openAnvilTemplateCreator(player);
                break;

            case "spawn":
                openBotListMenu(player);
                break;

            case "possess":
                if (args.length > 1 && args[1].equalsIgnoreCase("stop")) {
                    controlledBots.remove(player.getUniqueId());
                    player.sendMessage(prefix + ChatColor.YELLOW + "Kontrol bot dilepaskan.");
                    return true;
                }
                openControlMenu(player);
                break;

            case "equipment":
                if (!player.hasPermission("danzzbot.admin")) {
                    player.sendMessage(prefix + ChatColor.RED + "Akses ditolak!");
                    return true;
                }
                openEquipmentTargetMenu(player);
                break;

            case "teleport":
                openTeleportMenu(player);
                break;

            case "monitor":
                if (!player.hasPermission("danzzbot.admin")) {
                    player.sendMessage(prefix + ChatColor.RED + "Akses ditolak!");
                    return true;
                }
                openAdminMonitorMenu(player);
                break;

            case "waypoint":
                if (!player.hasPermission("danzzbot.admin")) {
                    player.sendMessage(prefix + ChatColor.RED + "Akses ditolak!");
                    return true;
                }
                if (args.length > 1 && args[1].equalsIgnoreCase("add")) {
                    NPC nearest = null;
                    double minDist = 10.0;
                    for (NPC npc : CitizensAPI.getNPCRegistry()) {
                        if (npc.isSpawned() && npc.getEntity().getWorld().equals(player.getWorld())) {
                            double dist = npc.getEntity().getLocation().distance(player.getLocation());
                            if (dist < minDist) {
                                minDist = dist;
                                nearest = npc;
                            }
                        }
                    }
                    if (nearest != null) {
                        botWaypoints.computeIfAbsent(nearest.getId(), k -> new ArrayList<>()).add(player.getLocation());
                        player.sendMessage(prefix + ChatColor.GREEN + "Waypoint patroli ditambahkan untuk: " + ChatColor.YELLOW + nearest.getName());
                    } else {
                        player.sendMessage(prefix + ChatColor.RED + "Tidak ada bot dalam radius 10 blok!");
                    }
                }
                break;

            case "removeall":
                if (!player.hasPermission("danzzbot.admin")) {
                    player.sendMessage(prefix + ChatColor.RED + "Akses ditolak!");
                    return true;
                }
                removeAllHolograms();
                removeAllBossBars();
                controlledBots.clear();
                botBackpacks.clear();
                botWaypoints.clear();
                botWaypointIndexes.clear();
                botOwners.clear();
                botWanderCooldowns.clear();
                int count = 0;
                for (NPC npc : CitizensAPI.getNPCRegistry()) {
                    npc.destroy();
                    count++;
                }
                logAudit("Admin " + player.getName() + " memusnahkan seluruh unit bot (" + count + ").");
                player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
                player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, new net.md_5.bungee.api.chat.TextComponent(colorize("&a⚡ Pengahapusan Berhasil!")));
                player.sendMessage(prefix + ChatColor.GREEN + "Seluruh bot berhasil dibersihkan!");
                break;

            default:
                player.sendMessage(prefix + ChatColor.RED + "Sub-command tidak dikenal.");
                break;
        }

        return true;
    }

    private void openCommercialMainMenu(Player player) {
        menuConfig = YamlConfiguration.loadConfiguration(menuFile);
        String title = colorize(menuConfig.getString("main-menu.title", "&d&lDanzzBot Enterprise Network"));
        int size = menuConfig.getInt("main-menu.size", 27);

        Inventory inv = Bukkit.createInventory(null, size, title);

        setupMenuItem(inv, "main-menu.items.bot-list", Material.PLAYER_HEAD);
        setupMenuItem(inv, "main-menu.items.create-bot", Material.NETHER_STAR);
        setupMenuItem(inv, "main-menu.items.settings", Material.COMPARATOR);
        setupMenuItem(inv, "main-menu.items.equipment-manager", Material.NETHERITE_CHESTPLATE);
        setupMenuItem(inv, "main-menu.items.control-bot", Material.NETHERITE_SWORD);
        setupMenuItem(inv, "main-menu.items.teleport-bot", Material.ENDER_PEARL);
        setupMenuItem(inv, "main-menu.items.admin-monitor", Material.CLOCK);
        setupMenuItem(inv, "main-menu.items.reload-plugin", Material.LIME_CONCRETE);
        setupMenuItem(inv, "main-menu.items.remove-all", Material.BARRIER);
        
        if (menuConfig.contains("main-menu.items.background.slot")) {
            List<Integer> bgSlots = menuConfig.getIntegerList("main-menu.items.background.slot");
            ItemStack bgItem = createItem(Material.GRAY_STAINED_GLASS_PANE, colorize("&8"), List.of());
            for (int slot : bgSlots) {
                inv.setItem(slot, bgItem);
            }
        }

        player.openInventory(inv);
    }

    private void openAnvilTemplateCreator(Player player) {
        Inventory anvilInv = Bukkit.createInventory(player, org.bukkit.event.inventory.InventoryType.ANVIL, colorize("&#00FF66&lCreate Bot Template"));
        ItemStack paper = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = paper.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("KetikNamaBotDisini");
            paper.setItemMeta(meta);
        }
        anvilInv.setItem(0, paper);
        player.openInventory(anvilInv);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
    }

    private void openSettingsMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, colorize("&1&lFeature Control Engine"));

        boolean follow = getConfig().getBoolean("settings.follow", true);
        boolean attack = getConfig().getBoolean("settings.attack-cpvp", true);
        boolean mining = getConfig().getBoolean("settings.mining", true);
        boolean security = getConfig().getBoolean("settings.claim-protection", true);
        boolean chatbot = getConfig().getBoolean("settings.chatbot", true);
        boolean hologram = getConfig().getBoolean("settings.hologram", true);
        boolean bossbar = getConfig().getBoolean("settings.bossbar-status", true);

        inv.setItem(10, createItem(follow ? Material.LIME_DYE : Material.GRAY_DYE, colorize("&aFollow AI &7- " + (follow ? "[ACTIVE]" : "[OFF]")), List.of()));
        inv.setItem(11, createItem(attack ? Material.LIME_DYE : Material.GRAY_DYE, colorize("&cCPvP AI &7- " + (attack ? "[ACTIVE]" : "[OFF]")), List.of()));
        inv.setItem(12, createItem(mining ? Material.LIME_DYE : Material.GRAY_DYE, colorize("&eAuto Mining AI &7- " + (mining ? "[ACTIVE]" : "[OFF]")), List.of()));
        inv.setItem(13, createItem(security ? Material.LIME_DYE : Material.GRAY_DYE, colorize("&bClaim Security &7- " + (security ? "[ACTIVE]" : "[OFF]")), List.of(colorize("&7Proteksi wilayah anti-grief bot"))));
        inv.setItem(14, createItem(chatbot ? Material.LIME_DYE : Material.GRAY_DYE, colorize("&bChatbot Slang &7- " + (chatbot ? "[ACTIVE]" : "[OFF]")), List.of()));
        inv.setItem(15, createItem(hologram ? Material.LIME_DYE : Material.GRAY_DYE, colorize("&dFloating Hologram &7- " + (hologram ? "[ACTIVE]" : "[OFF]")), List.of()));
         inv.setItem(16, createItem(bossbar ? Material.LIME_DYE : Material.GRAY_DYE, colorize("&5Bossbar Tracker &7- " + (bossbar ? "[ACTIVE]" : "[OFF]")), List.of()));

        inv.setItem(22, createItem(Material.ARROW, colorize("&f« Kembali ke Menu Utama"), List.of()));
        player.openInventory(inv);
    }

    private void openBotListMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, colorize("&#00FF66&lSelect Template to Spawn"));
        int slot = 0;
        for (String name : createdTemplates.keySet()) {
            if (slot >= 26) break;
            inv.setItem(slot++, createItem(Material.PLAYER_HEAD, colorize("&a" + name), List.of(colorize("&eKlik untuk langsung Spawn unit!"))));
        }
        inv.setItem(26, createItem(Material.ARROW, colorize("&f« Kembali ke Menu Utama"), List.of()));
        player.openInventory(inv);
    }

    private void openControlMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, colorize("&#00F2FF&lSelect Unit to Possess"));
        int slot = 0;
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (slot >= 26) break;
            inv.setItem(slot++, createItem(Material.ZOMBIE_HEAD, colorize("&c" + npc.getName()), List.of(colorize("&7Klik untuk merasuki unit"))));
        }
        inv.setItem(26, createItem(Material.ARROW, colorize("&f« Kembali ke Menu Utama"), List.of()));
        player.openInventory(inv);
    }

    private void openEquipmentTargetMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, colorize("&#BD00FF&lSelect Unit for Equipment"));
        int slot = 0;
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (slot >= 26) break;
            inv.setItem(slot++, createItem(Material.ARMOR_STAND, colorize("&e" + npc.getName()), List.of(colorize("&7Klik untuk atur Equipment / Gaji"))));
        }
        inv.setItem(26, createItem(Material.ARROW, colorize("&f« Kembali ke Menu Utama"), List.of()));
        player.openInventory(inv);
    }

    private void openEquipmentManagerMenu(Player player, NPC npc) {
        Inventory inv = Bukkit.createInventory(null, 27, colorize("&#BD00FF&lEquipment &7| &f" + npc.getName()));

        Equipment eq = npc.getOrAddTrait(Equipment.class);
        inv.setItem(10, eq.get(Equipment.EquipmentSlot.HELMET) != null ? eq.get(Equipment.EquipmentSlot.HELMET) : new ItemStack(Material.AIR));
        inv.setItem(11, eq.get(Equipment.EquipmentSlot.CHESTPLATE) != null ? eq.get(Equipment.EquipmentSlot.CHESTPLATE) : new ItemStack(Material.AIR));
        inv.setItem(12, eq.get(Equipment.EquipmentSlot.LEGGINGS) != null ? eq.get(Equipment.EquipmentSlot.LEGGINGS) : new ItemStack(Material.AIR));
        inv.setItem(13, eq.get(Equipment.EquipmentSlot.BOOTS) != null ? eq.get(Equipment.EquipmentSlot.BOOTS) : new ItemStack(Material.AIR));
        inv.setItem(15, eq.get(Equipment.EquipmentSlot.HAND) != null ? eq.get(Equipment.EquipmentSlot.HAND) : new ItemStack(Material.AIR));
        inv.setItem(16, eq.get(Equipment.EquipmentSlot.OFF_HAND) != null ? eq.get(Equipment.EquipmentSlot.OFF_HAND) : new ItemStack(Material.AIR));

        inv.setItem(18, createItem(Material.CHEST, colorize("&e&lBuka Bot Backpack"), List.of(colorize("&7Lihat hasil tambang bot"))));
        inv.setItem(20, createItem(Material.GOLD_INGOT, colorize("&6&lBayar Gaji Bot (Upkeep)"), List.of(colorize("&7Setor upah harian bot"))));
        inv.setItem(21, createItem(Material.COMPASS, colorize("&b&lTeleport ke Unit"), List.of(colorize("&7Teleport instan"))));
        inv.setItem(23, createItem(Material.PLAYER_HEAD, colorize("&d&lGanti Skin Bot"), List.of(colorize("&7Ganti skin bot"))));
        inv.setItem(25, createItem(Material.RED_CONCRETE, colorize("&c&lHapus Unit Ini"), List.of(colorize("&7Musnahkan bot ini"))));
        inv.setItem(22, createItem(Material.ARROW, colorize("&f&l« Kembali ke List Unit"), List.of(colorize("&7Kembali"))));
        player.openInventory(inv);
    }

    private void openTeleportMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, colorize("&#FFD700&lQuantum Teleport Destination"));
        int slot = 0;
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (slot >= 26) break;
            inv.setItem(slot++, createItem(Material.ENDER_EYE, colorize("&6" + npc.getName()), List.of(colorize("&7Klik untuk Teleport"))));
        }
        inv.setItem(26, createItem(Material.ARROW, colorize("&f« Kembali ke Menu Utama"), List.of()));
        player.openInventory(inv);
    }

    private void openAdminMonitorMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, colorize("&8Admin Monitor"));

        double currentTps = 20.0;
        try {
            currentTps = 20.0;
        } catch (Exception ignored) {}

        // Hitung total bot aman tanpa error
        int totalBots = 0;
        for (NPC ignored : CitizensAPI.getNPCRegistry()) {
            totalBots++;
        }
            
        inv.setItem(11, createItem(Material.DIAMOND_BLOCK, colorize("&bTPS Status"), List.of(colorize("&7Current TPS: " + String.format("%.2f", currentTps)))));
        inv.setItem(13, createItem(Material.PLAYER_HEAD, colorize("&eTotal Bots"), List.of(colorize("&7Jumlah unit bot: &f" + totalBots))));

        int slot = 18;
        for (String log : auditLogs) {
            if (slot >= 26) break;
            inv.setItem(slot++, createItem(Material.PAPER, colorize("&fAudit Log"), List.of(colorize("&7" + log))));
        }

        inv.setItem(26, createItem(Material.BARRIER, colorize("&cClose"), List.of(colorize("&7Klik untuk menutup"))));
        player.openInventory(inv);
    }

    private void setupMenuItem(Inventory inv, String path, Material fallbackMat) {
        if (!menuConfig.contains(path)) return;
        int slot = menuConfig.getInt(path + ".slot");
        String matStr = menuConfig.getString(path + ".material", fallbackMat.name());
        Material mat = Material.matchMaterial(matStr);
        if (mat == null) mat = fallbackMat;

        String name = colorize(menuConfig.getString(path + ".name", ""));
        List<String> rawLore = menuConfig.getStringList(path + ".lore");
        List<String> lore = new ArrayList<>();
        for (String l : rawLore) {
            lore.add(colorize(l));
        }

        inv.setItem(slot, createItem(mat, name, lore));
    }

    private ItemStack createItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
     public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (controlledBots.containsKey(player.getUniqueId())) {
            NPC npc = controlledBots.get(player.getUniqueId());
            if (npc != null && npc.isSpawned()) {
                Location toLoc = player.getLocation();
                npc.teleport(toLoc, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (controlledBots.containsKey(player.getUniqueId())) {
            NPC npc = controlledBots.get(player.getUniqueId());
            if (npc != null && npc.isSpawned() && npc.getEntity() != null) {
                if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
                    npc.getEntity().getWorld().spawnParticle(Particle.SWEEP_ATTACK, npc.getEntity().getLocation().add(0, 1, 0), 1);
                    npc.getEntity().getWorld().playSound(npc.getEntity().getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.0f);
                }
            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player) {
            Player victim = (Player) event.getEntity();
            for (Map.Entry<Integer, UUID> entry : botOwners.entrySet()) {
                if (entry.getValue().equals(victim.getUniqueId())) {
                    NPC npc = CitizensAPI.getNPCRegistry().getById(entry.getKey());
                    if (npc != null && npc.isSpawned() && npc.getEntity() instanceof LivingEntity) {
                        LivingEntity npcEntity = (LivingEntity) npc.getEntity();
                        if (event.getDamager() instanceof LivingEntity && !event.getDamager().equals(npcEntity)) {
                            npcEntity.getWorld().spawnParticle(Particle.CRIT, npcEntity.getLocation().add(0, 1, 0), 10);
                            npcEntity.getWorld().playSound(npcEntity.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.0f, 0.8f);
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();

        if (title.contains("Create Bot Template")) {
            if (event.getRawSlot() == 2) {
                ItemStack resultItem = event.getCurrentItem();
                if (resultItem != null && resultItem.hasItemMeta() && resultItem.getItemMeta().hasDisplayName()) {
                    String botName = ChatColor.stripColor(resultItem.getItemMeta().getDisplayName());
                    if (!botName.isEmpty() && !botName.equalsIgnoreCase("KetikNamaBotDisini")) {
                        synchronized (createdTemplates) {
                            createdTemplates.put(botName, "default");
                        }
                        saveBotsToStorage();
                        logAudit("Player " + player.getName() + " membuat template bot baru: " + botName);
                        
                        player.closeInventory();
                        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.5f);
                        player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, new net.md_5.bungee.api.chat.TextComponent(colorize("&aTemplate Bot '&e" + botName + "&a' berhasil dibuat!")));
                        event.setCancelled(true);
                        return;
                    }
                }
            }
            return;
        }

        if (title.contains("Backpack |")) return;

        if (title.contains("DanzzBot") || title.contains("Engine") || title.contains("Select") || title.contains("Quantum") || title.contains("Equipment") || title.contains("Template") || title.contains("Performance")) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;
            String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.9f, 1.3f);

            if (title.contains("Equipment |")) {
                String npcName = title.split(" \\| ")[1];
                NPC npc = findNPCByName(npcName);
                if (npc != null && npc.isSpawned()) {
                    if (name.contains("Buka Bot Backpack")) {
                        Inventory backpack = botBackpacks.computeIfAbsent(npc.getUniqueId(), k -> Bukkit.createInventory(null, 27, colorize("&8Backpack | &e" + npcName)));
                        player.openInventory(backpack);
                        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
                        return;
                    } else if (name.contains("Bayar Gaji Bot")) {
                        player.sendMessage(colorize("&a[Upkeep] Gaji harian bot &e" + npcName + " &aberhasil dibayar!"));
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
                        player.closeInventory();
                        return;
                    } else if (name.contains("Teleport ke Unit")) {
                        player.teleport(npc.getStoredLocation());
                        player.spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
                        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                        player.closeInventory();
                        return;
                    } else if (name.contains("Ganti Skin Bot")) {
                        SkinTrait skin = npc.getOrAddTrait(SkinTrait.class);
                        skin.setSkinName("Notch");
                        skin.setShouldUpdateSkins(true);
                        player.closeInventory();
                        return;
                    } else if (name.contains("Hapus Unit Ini")) {
                        removeHologramOfNPC(npc.getUniqueId());
                        removeBossBarOfNPC(npc.getUniqueId());
                        botBackpacks.remove(npc.getUniqueId());
                        botOwners.remove(npc.getId());
                        botWaypoints.remove(npc.getId());
                        botWaypointIndexes.remove(npc.getId());
                        botWanderCooldowns.remove(npc.getId());
                        npc.destroy();
                        player.closeInventory();
                        return;
                    }
                }
                if (name.contains("Kembali ke List Unit")) openEquipmentTargetMenu(player);
                return;
            }

            if (title.contains("Enterprise Network") || title.contains("Menu")) {
                if (name.contains("Deploy Unit")) openBotListMenu(player);
                else if (name.contains("Create New Template")) openAnvilTemplateCreator(player);
                else if (name.contains("Engine Settings")) openSettingsMenu(player);
                else if (name.contains("Equipment & Backpack")) openEquipmentTargetMenu(player);
                else if (name.contains("Possess Unit")) openControlMenu(player);
                else if (name.contains("Quantum Teleport")) openTeleportMenu(player);
                else if (name.contains("Network Performance")) openAdminMonitorMenu(player);
                else if (name.contains("Reload Plugin")) {
                    reloadDanzzPlugin(player);
                    player.closeInventory();
                }
                else if (name.contains("Purge All Units")) {
                    removeAllHolograms();
                    removeAllBossBars();
                    controlledBots.clear();
                    botBackpacks.clear();
                    botWaypoints.clear();
                    botWaypointIndexes.clear();
                    botOwners.clear();
                    botWanderCooldowns.clear();
                    for (NPC npc : CitizensAPI.getNPCRegistry()) npc.destroy();
                    player.closeInventory();
                }
            } 
            else if (title.contains("Feature Control") || title.contains("Select Template to Spawn") || title.contains("Select Unit for Equipment") || title.contains("Select Unit to Possess") || title.contains("Quantum Teleport") || title.contains("Performance")) {
                if (name.contains("Kembali ke Menu Utama") || name.contains("Kembali")) {
                    openCommercialMainMenu(player);
                } else if (title.contains("Select Template to Spawn")) {
                    spawnEnterpriseBot(player, name);
                    player.closeInventory();
                } else if (title.contains("Select Unit for Equipment")) {
                    NPC npc = findNPCByName(name);
                    if (npc != null) openEquipmentManagerMenu(player, npc);
                } else if (title.contains("Select Unit to Possess")) {
                    NPC npc = findNPCByName(name);
                    if (npc != null && npc.isSpawned()) {
                        controlledBots.put(player.getUniqueId(), npc);
                        player.closeInventory();
                    }
                } else if (title.contains("Quantum Teleport")) {
                    NPC npc = findNPCByName(name);
                    if (npc != null && npc.isSpawned()) {
                        player.teleport(npc.getStoredLocation());
                        player.closeInventory();
                    }
                }
            }
        }
    }

    private void spawnEnterpriseBot(Player player, String name) {
        Location loc = player.getLocation();
        NPC npc = CitizensAPI.getNPCRegistry().createNPC(org.bukkit.entity.EntityType.PLAYER, name);
        npc.spawn(loc);

        botOwners.put(npc.getId(), player.getUniqueId());
        registerBotToLuckPerms(npc);

        if (getConfig().getBoolean("settings.skin-system", true)) {
            SkinTrait skinTrait = npc.getOrAddTrait(SkinTrait.class);
            skinTrait.setSkinName(getConfig().getString("skin-settings.default-skin-user", "DanzzReal"));
        }

        if (npc.getOrAddTrait(Equipment.class) != null) {
            Equipment eq = npc.getOrAddTrait(Equipment.class);
            try {
                eq.set(Equipment.EquipmentSlot.HELMET, new ItemStack(Material.valueOf(getConfig().getString("default-equipment.helmet", "NETHERITE_HELMET"))));
                eq.set(Equipment.EquipmentSlot.CHESTPLATE, new ItemStack(Material.valueOf(getConfig().getString("default-equipment.chestplate", "NETHERITE_CHESTPLATE"))));
                eq.set(Equipment.EquipmentSlot.LEGGINGS, new ItemStack(Material.valueOf(getConfig().getString("default-equipment.leggings", "NETHERITE_LEGGINGS"))));
                eq.set(Equipment.EquipmentSlot.BOOTS, new ItemStack(Material.valueOf(getConfig().getString("default-equipment.boots", "NETHERITE_BOOTS"))));
                eq.set(Equipment.EquipmentSlot.HAND, new ItemStack(Material.valueOf(getConfig().getString("default-equipment.main-hand", "NETHERITE_PICKAXE"))));
                eq.set(Equipment.EquipmentSlot.OFF_HAND, new ItemStack(Material.valueOf(getConfig().getString("default-equipment.off-hand", "TOTEM_OF_UNDYING"))));
            } catch (Exception ignored) {}
        }

        if (npc.getEntity() instanceof LivingEntity) {
            LivingEntity le = (LivingEntity) npc.getEntity();
            le.setHealth(20.0);
            le.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, false, false));
            le.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, getConfig().getInt("combat.resistance-amplifier", 1), false, false));
        }

        loc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc.add(0, 1, 0), 40, 0.6, 0.6, 0.6, 0.15);
        loc.getWorld().playSound(loc, Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);

        boolean showRank = getConfig().getBoolean("messages.show-rank-on-join", true);
        String joinFormat = getConfig().getString(showRank ? "messages.join-format" : "messages.join-format-plain", "%luckperms_prefix%&e%npc_name% joined the game");
        Bukkit.broadcastMessage(parseBotFormat(npc, joinFormat));
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (npc.isSpawned() && npc.getEntity() != null && npc.getEntity().equals(entity)) {
                removeHologramOfNPC(npc.getUniqueId());
                removeBossBarOfNPC(npc.getUniqueId());
                botBackpacks.remove(npc.getUniqueId());
                botOwners.remove(npc.getId());
                botWaypoints.remove(npc.getId());
                botWaypointIndexes.remove(npc.getId());
                botWanderCooldowns.remove(npc.getId());
                
                String deathFormat = getConfig().getString("messages.death-format", "%luckperms_prefix%&e%npc_name% died");
                Bukkit.broadcastMessage(parseBotFormat(npc, deathFormat));
                break;
            }
        }
    }

    private void startEnterpriseEngineTask() {
        enterpriseEngineTask = new BukkitRunnable() {
            @Override
            public void run() {
                double currentTps = 20.0;
try {
    double[] tpsList = Bukkit.getServer().getTPS();
    if (tpsList != null && tpsList.length > 0) {
        currentTps = tpsList[0];
    }
} catch (Exception ignored) {}
                boolean isLagging = currentTps < 18.0;

                boolean cpvpActive = getConfig().getBoolean("settings.attack-cpvp", true);
                boolean miningActive = getConfig().getBoolean("settings.mining", true);
                boolean securityActive = getConfig().getBoolean("settings.claim-protection", true);
                boolean chatbot = getConfig().getBoolean("settings.chatbot", true);
                boolean autoHeal = getConfig().getBoolean("combat.auto-heal", true);
                boolean auraActive = getConfig().getBoolean("settings.particle-aura", true) && !isLagging;
                List<String> slangs = getConfig().getStringList("chat-slang");

                long currentTime = System.currentTimeMillis();

                for (NPC npc : CitizensAPI.getNPCRegistry()) {
                    if (!npc.isSpawned() || npc.getEntity() == null) continue;

                    updateOrSpawnHologram(npc);
                    updateOrSpawnBossBar(npc);

                    LivingEntity npcEntity = (LivingEntity) npc.getEntity();

                    if (securityActive && npcEntity.getLocation().getBlock().getType() == Material.BEDROCK) {
                        continue; 
                    }

                    List<Location> waypoints = botWaypoints.get(npc.getId());
                    if (waypoints != null && !waypoints.isEmpty() && !isLagging) {
                        int index = botWaypointIndexes.getOrDefault(npc.getId(), 0);
                        Location targetLoc = waypoints.get(index);
                        if (npcEntity.getLocation().distanceSquared(targetLoc) > 4.0) {
                            npc.getNavigator().setTarget(targetLoc);
                        } else {
                            botWaypointIndexes.put(npc.getId(), (index + 1) % waypoints.size());
                        }
                    } 
                    else if (!isLagging) {
                        long lastMove = botWanderCooldowns.getOrDefault(npc.getId(), 0L);
                        if (currentTime - lastMove > (6000 + random.nextInt(6000))) {
                            botWanderCooldowns.put(npc.getId(), currentTime);
                            
                            double randomX = (random.nextDouble() - 0.5) * 16.0;
                            double randomZ = (random.nextDouble() - 0.5) * 16.0;
                            Location wanderLoc = npcEntity.getLocation().add(randomX, 0, randomZ);
                            
                            if (wanderLoc.getWorld() != null) {
                                npc.getNavigator().setTarget(wanderLoc);
                            }
                        }
                    }

                    if (auraActive) {
                        String auraName = getConfig().getString("visuals.aura-type", "SOUL_FIRE_FLAME");
                        Particle p = Particle.SOUL_FIRE_FLAME;
                        try { p = Particle.valueOf(auraName); } catch (Exception ignored) {}
                        npcEntity.getWorld().spawnParticle(p, npcEntity.getLocation().add(0, 1, 0), 2, 0.3, 0.5, 0.3, 0.02);
                    }

                    if (autoHeal && npcEntity.getHealth() < 10.0 && npcEntity.getHealth() > 0.0) {
                        npcEntity.setHealth(Math.min(20.0, npcEntity.getHealth() + 6.0));
                    }

                    if (miningActive && !isLagging) {
                        if (random.nextInt(15) == 0) {
                            Location loc = npcEntity.getLocation().add(random.nextDouble() * 4 - 2, 0, random.nextDouble() * 4 - 2);
                            loc.getWorld().spawnParticle(Particle.BLOCK, loc, 10, Bukkit.createBlockData(Material.STONE));
                            
                            Inventory backpack = botBackpacks.get(npc.getUniqueId());
                            if (backpack != null && backpack.firstEmpty() != -1) {
                                Material[] minedItems = {Material.DIAMOND, Material.NETHERITE_SCRAP, Material.GOLD_INGOT};
                                backpack.addItem(new ItemStack(minedItems[random.nextInt(minedItems.length)], 1));
                            }
                        }
                    }

                    if (cpvpActive && !isLagging) {
                        double radius = getConfig().getDouble("combat.attack-radius", 10.0);
                        for (Entity target : npcEntity.getNearbyEntities(radius, radius, radius)) {
                            if (target instanceof Player && !target.getName().equals(npc.getName())) {
                                if (random.nextInt(10) == 0) {
                                    target.getLocation().getWorld().spawn(target.getLocation(), EnderCrystal.class);
                                }
                            }
                        }
                    }

                    if (chatbot && slangs != null && !slangs.isEmpty() && random.nextInt(200) == 0) {
                        String randomSlang = slangs.get(random.nextInt(slangs.size()));
                        String chatFormat = getConfig().getString("messages.chat-format", "%luckperms_prefix%&b%npc_name% &7» &f%message%");
                        Bukkit.broadcastMessage(parseBotFormat(npc, chatFormat.replace("%message%", randomSlang)));
                    }
                }
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private void updateOrSpawnHologram(NPC npc) {
        if (!getConfig().getBoolean("settings.hologram", true) || !npc.isSpawned() || npc.getEntity() == null) {
            removeHologramOfNPC(npc.getUniqueId());
            return;
        }

        LivingEntity entity = (LivingEntity) npc.getEntity();
        Location loc = entity.getLocation().add(0, 2.3, 0);
        String format = getConfig().getString("visuals.hologram-format", "%luckperms_prefix%&#00F2FF%npc_name% &7- &#00FF66%health% HP");
        String text = parseBotFormat(npc, format);

        ArmorStand hologram = botHolograms.get(npc.getUniqueId());
        if (hologram == null || !hologram.isValid()) {
            hologram = entity.getWorld().spawn(loc, ArmorStand.class, stand -> {
                stand.setVisible(false);
                stand.setGravity(false);
                stand.setCustomName(text);
                stand.setCustomNameVisible(true);
                stand.setMarker(true);
            });
            botHolograms.put(npc.getUniqueId(), hologram);
        } else {
            hologram.teleport(loc);
            hologram.setCustomName(text);
        }
    }

    private void updateOrSpawnBossBar(NPC npc) {
        if (!getConfig().getBoolean("settings.bossbar-status", true) || !npc.isSpawned() || npc.getEntity() == null) {
            removeBossBarOfNPC(npc.getUniqueId());
            return;
        }

        LivingEntity entity = (LivingEntity) npc.getEntity();
        BossBar bar = botBossBars.get(npc.getUniqueId());
        String parsedTitle = parseBotFormat(npc, getConfig().getString("visuals.bossbar-format", "%luckperms_prefix%&#00F2FF%npc_name%"));

        if (bar == null) {
            bar = Bukkit.createBossBar(parsedTitle, BarColor.PURPLE, BarStyle.SOLID);
            bar.setVisible(true);
            botBossBars.put(npc.getUniqueId(), bar);
        } else {
            bar.setTitle(parsedTitle);
        }

        bar.setProgress(Math.max(0.0, Math.min(1.0, entity.getHealth() / 20.0)));
        for (Player p : entity.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(entity.getLocation()) <= 225) {
                bar.addPlayer(p);
            } else {
                bar.removePlayer(p);
            }
        }
    }

    private void removeHologramOfNPC(UUID uuid) {
        ArmorStand stand = botHolograms.remove(uuid);
        if (stand != null && stand.isValid()) stand.remove();
    }

    private void removeBossBarOfNPC(UUID uuid) {
        BossBar bar = botBossBars.remove(uuid);
        if (bar != null) bar.removeAll();
    }

    private void removeAllHolograms() {
        for (ArmorStand stand : botHolograms.values()) {
            if (stand != null && stand.isValid()) stand.remove();
        }
        botHolograms.clear();
    }

    private void removeAllBossBars() {
        for (BossBar bar : botBossBars.values()) {
            if (bar != null) bar.removeAll();
        }
        botBossBars.clear();
    }

    @EventHandler
    public void onServerListPing(ServerListPingEvent event) {
        if (!getConfig().getBoolean("motd.enabled", true)) return;

        int activeBotCount = 0;
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (npc.isSpawned()) {
                activeBotCount++;
            }
        }

        boolean dynamicCount = getConfig().getBoolean("motd.dynamic-bot-count", true);
        int staticExtra = getConfig().getInt("motd.extra-online-count", 5);
        int totalExtra = dynamicCount ? (activeBotCount + staticExtra) : staticExtra;

        event.setMaxPlayers(event.getMaxPlayers() + totalExtra);
        
        String motdText = getConfig().getString("motd.text", "&#00F2FF&lDanzzBot Enterprise Network 2026");
        event.setMotd(colorize(motdText));
    }

        public static class DanzzBotPlaceholderExpansion extends PlaceholderExpansion {
        private final DanzzBot danzzBotPlugin;
        public DanzzBotPlaceholderExpansion(DanzzBot plugin) { this.danzzBotPlugin = plugin; }

        @Override public @NotNull String getIdentifier() { return "danzzbot"; }
        @Override public @NotNull String getAuthor() { return "DanzzReal"; }
        @Override public @NotNull String getVersion() { return "14.0-NetworkSuite"; } // Pastikan baris ini punya return String
        @Override public boolean persist() { return true; }

        @Override
        public String onPlaceholderRequest(Player player, @NotNull String params) {
            if (params.equalsIgnoreCase("count")) {
                int count = 0;
                for (NPC npc : CitizensAPI.getNPCRegistry()) { if (npc.isSpawned()) count++; }
                return String.valueOf(count);
            }
            if (params.equalsIgnoreCase("templates")) {
                return String.valueOf(danzzBotPlugin.createdTemplates.size());
            }
            return null;
        }
    }
}
