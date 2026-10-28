package com.danzz.danzzbot;

import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.trait.Equipment;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
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
import org.bukkit.entity.EndCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
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
    private final Random random = new Random();
    
    private File botsFile, menuFile;
    private FileConfiguration botsConfig, menuConfig;

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    @Override
    public void onEnable() {
        saveDefaultConfig();
        createBotsFile();
        createMenuFile();
        loadBotsFromStorage();

        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("danzzbot")).setExecutor(this);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new DanzzBotPlaceholderExpansion(this).register();
            getLogger().info("PlaceholderAPI Enterprise Engine terhubung.");
        }

        startEnterpriseEngineTask();
        printEnterpriseBanner();
    }

    @Override
    public void onDisable() {
        removeAllHolograms();
        removeAllBossBars();
        saveBotsToStorage();
        getLogger().info(colorize("&8[&b&lDanzzBot Enterprise&8] &cShutdown aman. Data tersimpan."));
    }

    private void printEnterpriseBanner() {
        getLogger().info("==================================================================");
        getLogger().info("  ___                  _     ____        _   _       _  ___  ");
        getLogger().info(" |   \\ __ _ _ _ _____| |_  | r_ \\  ___ | |_(_)_ _ _(_)/ _ \\ ");
        getLogger().info(" | |) / _` | '_|_ /_-<|  _| |   _/ / _ \\|  _| | '_| | | (_) |");
        getLogger().info(" |___/\\__,_|_| /__/__/ \\__| |_|   \\___/ \\__|_|_| |_|_|\\___/ ");
        getLogger().info("                                                                  ");
        getLogger().info("  Edition : ENTERPRISE S-Class ($100 Professional Tier)          ");
        getLogger().info("  Features: Dynamic GUI, Equipment Manager, RGB Hologram, CPvP   ");
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

    private void loadBotsFromStorage() {
        if (botsConfig.contains("bots")) {
            for (String key : Objects.requireNonNull(botsConfig.getConfigurationSection("bots")).getKeys(false)) {
                createdTemplates.put(key, botsConfig.getString("bots." + key, "default"));
            }
        }
    }

    private void saveBotsToStorage() {
        for (Map.Entry<String, String> entry : createdTemplates.entrySet()) {
            botsConfig.set("bots." + entry.getKey(), entry.getValue());
        }
        try { botsConfig.save(botsFile); } catch (IOException e) { getLogger().severe("Gagal menyimpan bots.yml!"); }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Command ini khusus untuk Player in-game!");
            return true;
        }

        Player player = (Player) sender;
        String prefix = colorize(getConfig().getString("messages.prefix", "&8[&b&lDanzzBot&8] &r"));

        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) {
            openCommercialMainMenu(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
            return true;
        }

        if (args[0].equalsIgnoreCase("create")) {
            if (!player.hasPermission("danzzbot.admin")) {
                player.sendMessage(prefix + ChatColor.RED + "Akses ditolak!");
                return true;
            }
            if (args.length < 2) {
                player.sendMessage(prefix + ChatColor.RED + "Penggunaan: /danzzbot create <nama>");
                return true;
            }
            String name = args[1];
            createdTemplates.put(name, "default");
            saveBotsToStorage();
            
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.5f);
            player.sendActionBar(colorize("&aTemplate Enterprise '" + name + "' berhasil dibuat!"));
            player.sendMessage(prefix + ChatColor.GREEN + "Template bot baru tersimpan: " + ChatColor.YELLOW + name);
            return true;
        }

        if (args[0].equalsIgnoreCase("spawn")) {
            if (args.length < 2) {
                player.sendMessage(prefix + ChatColor.RED + "Penggunaan: /danzzbot spawn <nama>");
                return true;
            }
            spawnEnterpriseBot(player, args[1]);
            return true;
        }

        if (args[0].equalsIgnoreCase("remove")) {
            if (args.length < 2) {
                player.sendMessage(prefix + ChatColor.RED + "Penggunaan: /danzzbot remove <nama>");
                return true;
            }
            removeEnterpriseBot(player, args[1]);
            return true;
        }

        if (args[0].equalsIgnoreCase("equipment")) {
            if (!player.hasPermission("danzzbot.admin")) {
                player.sendMessage(prefix + ChatColor.RED + "Akses ditolak!");
                return true;
            }
            if (args.length < 2) {
                player.sendMessage(prefix + ChatColor.RED + "Penggunaan: /danzzbot equipment <nama_bot>");
                return true;
            }
            NPC npc = CitizensAPI.getNPCRegistry().getByName(args[1]);
            if (npc != null) {
                openEquipmentManagerMenu(player, npc);
            } else {
                player.sendMessage(prefix + ChatColor.RED + "Bot dengan nama tersebut tidak ditemukan!");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("skin")) {
            if (args.length < 3) {
                player.sendMessage(prefix + ChatColor.RED + "Penggunaan: /danzzbot skin <nama_bot> <username_skin>");
                return true;
            }
            NPC npc = CitizensAPI.getNPCRegistry().getByName(args[1]);
            if (npc != null) {
                SkinTrait skinTrait = npc.getOrAddTrait(SkinTrait.class);
                skinTrait.setSkinName(args[2]);
                player.sendMessage(prefix + colorize("&aSkin bot &e" + args[1] + " &aberhasil diubah menjadi: &f" + args[2]));
            } else {
                player.sendMessage(prefix + ChatColor.RED + "Bot tidak ditemukan!");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("removeall")) {
            if (!player.hasPermission("danzzbot.admin")) {
                player.sendMessage(prefix + ChatColor.RED + "Akses ditolak!");
                return true;
            }
            removeAllHolograms();
            removeAllBossBars();
            int count = 0;
            for (NPC npc : CitizensAPI.getNPCRegistry()) {
                npc.destroy();
                count++;
            }
            player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
            player.sendActionBar(ChatColor.RED + "Semua unit (" + count + ") dimusnahkan!");
            player.sendMessage(prefix + ChatColor.GREEN + "Seluruh bot berhasil dibersihkan!");
            return true;
        }

        player.sendMessage(prefix + ChatColor.RED + "Command tidak dikenal. Ketik /danzzbot menu");
        return true;
    }

    private void openCommercialMainMenu(Player player) {
        menuConfig = YamlConfiguration.loadConfiguration(menuFile);
        String title = colorize(menuConfig.getString("main-menu.title", "&d&lDanzzBot Menu"));
        int size = menuConfig.getInt("main-menu.size", 27);

        Inventory inv = Bukkit.createInventory(null, size, title);

        setupMenuItem(inv, "main-menu.items.bot-list", Material.PLAYER_HEAD);
        setupMenuItem(inv, "main-menu.items.create-bot", Material.NETHER_STAR);
        setupMenuItem(inv, "main-menu.items.settings", Material.REDSTONE_COMPARATOR);
        setupMenuItem(inv, "main-menu.items.equipment-manager", Material.NETHERITE_CHESTPLATE);
        setupMenuItem(inv, "main-menu.items.control-bot", Material.NETHERITE_SWORD);
        setupMenuItem(inv, "main-menu.items.teleport-bot", Material.ENDER_PEARL);
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

    private void openSettingsMenu(Player player) {
        menuConfig = YamlConfiguration.loadConfiguration(menuFile);
        String title = colorize(menuConfig.getString("settings-menu.title", "&1&lSettings Engine"));
        int size = menuConfig.getInt("settings-menu.size", 27);

        Inventory inv = Bukkit.createInventory(null, size, title);

        boolean follow = getConfig().getBoolean("settings.follow", true);
        boolean attack = getConfig().getBoolean("settings.attack-cpvp", true);
        boolean mining = getConfig().getBoolean("settings.mining", false);
        boolean chatbot = getConfig().getBoolean("settings.chatbot", true);
        boolean hologram = getConfig().getBoolean("settings.hologram", true);
        boolean bossbar = getConfig().getBoolean("settings.bossbar-status", true);
        boolean aura = getConfig().getBoolean("settings.particle-aura", true);

        inv.setItem(menuConfig.getInt("settings-menu.items.follow.slot", 10), createItem(follow ? Material.LIME_DYE : Material.GRAY_DYE, colorize(menuConfig.getString("settings-menu.items.follow.name") + " &7- " + (follow ? "&a[ACTIVE]" : "&c[OFF]")), List.of()));
        inv.setItem(menuConfig.getInt("settings-menu.items.attack.slot", 11), createItem(attack ? Material.LIME_DYE : Material.GRAY_DYE, colorize(menuConfig.getString("settings-menu.items.attack.name") + " &7- " + (attack ? "&a[ACTIVE]" : "&c[OFF]")), List.of()));
        inv.setItem(menuConfig.getInt("settings-menu.items.mining.slot", 12), createItem(mining ? Material.LIME_DYE : Material.GRAY_DYE, colorize(menuConfig.getString("settings-menu.items.mining.name") + " &7- " + (mining ? "&a[ACTIVE]" : "&c[OFF]")), List.of()));
        inv.setItem(menuConfig.getInt("settings-menu.items.chatbot.slot", 13), createItem(chatbot ? Material.LIME_DYE : Material.GRAY_DYE, colorize(menuConfig.getString("settings-menu.items.chatbot.name") + " &7- " + (chatbot ? "&a[ACTIVE]" : "&c[OFF]")), List.of()));
        inv.setItem(menuConfig.getInt("settings-menu.items.hologram.slot", 14), createItem(hologram ? Material.LIME_DYE : Material.GRAY_DYE, colorize(menuConfig.getString("settings-menu.items.hologram.name") + " &7- " + (hologram ? "&a[ACTIVE]" : "&c[OFF]")), List.of()));
        inv.setItem(menuConfig.getInt("settings-menu.items.bossbar.slot", 15), createItem(bossbar ? Material.LIME_DYE : Material.GRAY_DYE, colorize(menuConfig.getString("settings-menu.items.bossbar.name") + " &7- " + (bossbar ? "&a[ACTIVE]" : "&c[OFF]")), List.of()));
        inv.setItem(menuConfig.getInt("settings-menu.items.aura.slot", 16), createItem(aura ? Material.LIME_DYE : Material.GRAY_DYE, colorize(menuConfig.getString("settings-menu.items.aura.name") + " &7- " + (aura ? "&a[ACTIVE]" : "&c[OFF]")), List.of()));

        setupMenuItem(inv, "settings-menu.items.back", Material.ARROW);
        player.openInventory(inv);
    }

    private void openEquipmentManagerMenu(Player player, NPC npc) {
        Inventory inv = Bukkit.createInventory(null, 27, colorize("&#BD00FF&lEquipment &7| &f" + npc.getName()));

        Equipment eq = npc.getOrAddTrait(Equipment.class);
        inv.setItem(10, eq.getEquipment(Equipment.EquipmentSlot.HELMET) != null ? eq.getEquipment(Equipment.EquipmentSlot.HELMET) : new ItemStack(Material.AIR));
        inv.setItem(11, eq.getEquipment(Equipment.EquipmentSlot.CHESTPLATE) != null ? eq.getEquipment(Equipment.EquipmentSlot.CHESTPLATE) : new ItemStack(Material.AIR));
        inv.setItem(12, eq.getEquipment(Equipment.EquipmentSlot.LEGGINGS) != null ? eq.getEquipment(Equipment.EquipmentSlot.LEGGINGS) : new ItemStack(Material.AIR));
        inv.setItem(13, eq.getEquipment(Equipment.EquipmentSlot.BOOTS) != null ? eq.getEquipment(Equipment.EquipmentSlot.BOOTS) : new ItemStack(Material.AIR));
        inv.setItem(15, eq.getEquipment(Equipment.EquipmentSlot.HAND) != null ? eq.getEquipment(Equipment.EquipmentSlot.HAND) : new ItemStack(Material.AIR));
        inv.setItem(16, eq.getEquipment(Equipment.EquipmentSlot.OFF_HAND) != null ? eq.getEquipment(Equipment.EquipmentSlot.OFF_HAND) : new ItemStack(Material.AIR));

        inv.setItem(22, createItem(Material.ARROW, colorize("&f&l« Kembali ke Menu Utama"), List.of(colorize("&7Klik untuk simpan & kembali"))));
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

    private void openBotListMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, colorize("&#00FF66&lSelect Unit to Deploy"));
        int slot = 0;
        for (String name : createdTemplates.keySet()) {
            if (slot >= 26) break;
            inv.setItem(slot++, createItem(Material.PLAYER_HEAD, colorize("&a" + name), List.of(colorize("&eKlik untuk deploy unit"))));
        }
        inv.setItem(26, createItem(Material.ARROW, colorize("&fKembali"), List.of()));
        player.openInventory(inv);
    }

    private void openControlMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, colorize("&#00F2FF&lSelect Unit to Possess"));
        int slot = 0;
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (slot >= 26) break;
            inv.setItem(slot++, createItem(Material.ZOMBIE_HEAD, colorize("&c" + npc.getName()), List.of(colorize("&7Ambil alih kendali unit"))));
        }
        inv.setItem(26, createItem(Material.ARROW, colorize("&fKembali"), List.of()));
        player.openInventory(inv);
    }

    private void openEquipmentTargetMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, colorize("&#BD00FF&lSelect Unit for Equipment"));
        int slot = 0;
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (slot >= 26) break;
            inv.setItem(slot++, createItem(Material.ARMOR_STAND, colorize("&e" + npc.getName()), List.of(colorize("&7Atur armor & senjata bot ini"))));
        }
        inv.setItem(26, createItem(Material.ARROW, colorize("&fKembali"), List.of()));
        player.openInventory(inv);
    }

    private void openTeleportMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, colorize("&#FFD700&lQuantum Teleport Destination"));
        int slot = 0;
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (slot >= 26) break;
            inv.setItem(slot++, createItem(Material.ENDER_EYE, colorize("&6" + npc.getName()), List.of(colorize("&7Teleport ke posisi unit"))));
        }
        inv.setItem(26, createItem(Material.ARROW, colorize("&fKembali"), List.of()));
        player.openInventory(inv);
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
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();
        String prefix = colorize(getConfig().getString("messages.prefix", "&8[&b&lDanzzBot&8] &r"));

        if (title.contains("DanzzBot") || title.contains("Engine") || title.contains("Select") || title.contains("Quantum") || title.contains("Equipment")) {
            if (title.contains("Equipment")) {
                return;
            }

            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;
            String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.9f, 1.3f);

            if (title.contains("S-Class Enterprise") || title.contains("Menu")) {
                if (name.contains("Deploy Unit")) openBotListMenu(player);
                else if (name.contains("Create New Template")) {
                    player.closeInventory();
                    player.sendMessage(prefix + ChatColor.YELLOW + "Ketik command: /danzzbot create <nama>");
                } else if (name.contains("Engine Settings")) openSettingsMenu(player);
                else if (name.contains("Equipment Manager")) openEquipmentTargetMenu(player);
                else if (name.contains("Possess")) openControlMenu(player);
                else if (name.contains("Quantum Teleport")) openTeleportMenu(player);
                else if (name.contains("Purge All Units")) {
                    removeAllHolograms();
                    removeAllBossBars();
                    for (NPC npc : CitizensAPI.getNPCRegistry()) npc.destroy();
                    player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
                    player.sendActionBar(ChatColor.RED + "Semua unit dimusnahkan!");
                    player.closeInventory();
                }
            } else if (title.contains("Feature Control") || title.contains("Settings")) {
                if (name.contains("Follow AI")) {
                    boolean val = !getConfig().getBoolean("settings.follow");
                    getConfig().set("settings.follow", val);
                    saveConfig();
                    openSettingsMenu(player);
                } else if (name.contains("CPvP / Combat AI")) {
                    boolean val = !getConfig().getBoolean("settings.attack-cpvp");
                    getConfig().set("settings.attack-cpvp", val);
                    saveConfig();
                    openSettingsMenu(player);
                } else if (name.contains("Auto Mining")) {
                    boolean val = !getConfig().getBoolean("settings.mining");
                    getConfig().set("settings.mining", val);
                    saveConfig();
                    openSettingsMenu(player);
                } else if (name.contains("Chatbot Slang")) {
                    boolean val = !getConfig().getBoolean("settings.chatbot");
                    getConfig().set("settings.chatbot", val);
                    saveConfig();
                    openSettingsMenu(player);
                } else if (name.contains("Floating Hologram")) {
                    boolean val = !getConfig().getBoolean("settings.hologram");
                    getConfig().set("settings.hologram", val);
                    saveConfig();
                    if (!val) removeAllHolograms();
                    openSettingsMenu(player);
                } else if (name.contains("Bossbar Tracker")) {
                    boolean val = !getConfig().getBoolean("settings.bossbar-status");
                    getConfig().set("settings.bossbar-status", val);
                    saveConfig();
                    if (!val) removeAllBossBars();
                    openSettingsMenu(player);
                } else if (name.contains("Particle Aura Trail")) {
                    boolean val = !getConfig().getBoolean("settings.particle-aura");
                    getConfig().set("settings.particle-aura", val);
                    saveConfig();
                    openSettingsMenu(player);
                } else if (name.contains("Kembali")) {
                    openCommercialMainMenu(player);
                }
            } else if (title.contains("Select Unit to Deploy")) {
                if (name.contains("Kembali")) openCommercialMainMenu(player);
                else {
                    spawnEnterpriseBot(player, name);
                    player.closeInventory();
                }
            } else if (title.contains("Select Unit for Equipment")) {
                if (name.contains("Kembali")) openCommercialMainMenu(player);
                else {
                    NPC npc = CitizensAPI.getNPCRegistry().getByName(name);
                    if (npc != null) openEquipmentManagerMenu(player, npc);
                }
            } else if (title.contains("Select Unit to Possess")) {
                if (name.contains("Kembali")) openCommercialMainMenu(player);
                else {
                    NPC npc = CitizensAPI.getNPCRegistry().getByName(name);
                    if (npc != null) {
                        controlledBots.put(player.getUniqueId(), npc);
                        player.sendActionBar(colorize("&aMengambil alih unit: &e" + name));
                        player.closeInventory();
                    }
                }
            } else if (title.contains("Quantum Teleport")) {
                if (name.contains("Kembali")) openCommercialMainMenu(player);
                else {
                    NPC npc = CitizensAPI.getNPCRegistry().getByName(name);
                    if (npc != null && npc.isSpawned()) {
                        player.teleport(npc.getStoredLocation());
                        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                        player.sendActionBar(colorize("&6Quantum TP ke unit &e" + name));
                        player.closeInventory();
                    }
                }
            }
        }
    }

    private void spawnEnterpriseBot(Player player, String name) {
        String prefix = colorize(getConfig().getString("messages.prefix", "&8[&b&lDanzzBot&8] &r"));
        Location loc = player.getLocation();
        
        NPC npc = CitizensAPI.getNPCRegistry().createNPC(org.bukkit.entity.EntityType.PLAYER, name);
        npc.spawn(loc);

        if (getConfig().getBoolean("settings.skin-system", true)) {
            SkinTrait skinTrait = npc.getOrAddTrait(SkinTrait.class);
            skinTrait.setSkinName(getConfig().getString("skin-settings.default-skin-user", "DanzzReal"));
        }

        if (npc.getOrAddTrait(Equipment.class) != null) {
            Equipment eq = npc.getOrAddTrait(Equipment.class);
            try {
                eq.setEquipment(Equipment.EquipmentSlot.HELMET, new ItemStack(Material.valueOf(getConfig().getString("default-equipment.helmet", "NETHERITE_HELMET"))));
                eq.setEquipment(Equipment.EquipmentSlot.CHESTPLATE, new ItemStack(Material.valueOf(getConfig().getString("default-equipment.chestplate", "NETHERITE_CHESTPLATE"))));
                eq.setEquipment(Equipment.EquipmentSlot.LEGGINGS, new ItemStack(Material.valueOf(getConfig().getString("default-equipment.leggings", "NETHERITE_LEGGINGS"))));
                eq.setEquipment(Equipment.EquipmentSlot.BOOTS, new ItemStack(Material.valueOf(getConfig().getString("default-equipment.boots", "NETHERITE_BOOTS"))));
                eq.setEquipment(Equipment.EquipmentSlot.HAND, new ItemStack(Material.valueOf(getConfig().getString("default-equipment.main-hand", "NETHERITE_SWORD"))));
                eq.setEquipment(Equipment.EquipmentSlot.OFF_HAND, new ItemStack(Material.valueOf(getConfig().getString("default-equipment.off-hand", "TOTEM_OF_UNDYING"))));
            } catch (Exception e) {
                getLogger().warning("Gagal memuat material equipment!");
            }
        }

        if (npc.getEntity() instanceof LivingEntity) {
            LivingEntity le = (LivingEntity) npc.getEntity();
            le.setHealth(20.0);
            le.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, false, false));
            le.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, getConfig().getInt("combat.resistance-amplifier", 1), false, false));
        }

        loc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc.add(0, 1, 0), 40, 0.6, 0.6, 0.6, 0.15);
        loc.getWorld().playSound(loc, Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);

        Bukkit.broadcastMessage(ChatColor.YELLOW + name + " joined the game");
        player.sendActionBar(colorize("&aUnit Enterprise '" + name + "' berhasil dideploy!"));
        player.sendMessage(prefix + colorize("&aUnit " + name + " aktif dengan Perlengkapan S-Class!"));
    }

    private void removeEnterpriseBot(Player player, String name) {
        String prefix = colorize(getConfig().getString("messages.prefix", "&8[&b&lDanzzBot&8] &r"));
        NPC npc = CitizensAPI.getNPCRegistry().getByName(name);
        if (npc != null) {
            removeHologramOfNPC(npc.getUniqueId());
            removeBossBarOfNPC(npc.getUniqueId());
            Location loc = npc.getStoredLocation();
            if (loc != null && loc.getWorld() != null) {
                loc.getWorld().spawnParticle(Particle.POOF, loc, 25, 0.3, 0.5, 0.3, 0.05);
                loc.getWorld().playSound(loc, Sound.ENTITY_ITEM_BREAK, 1.0f, 0.8f);
            }
            npc.destroy();
            Bukkit.broadcastMessage(ChatColor.YELLOW + name + " left the game");
            player.sendActionBar(ChatColor.RED + "Unit " + name + " dihapus.");
            player.sendMessage(prefix + ChatColor.GREEN + "Unit " + name + " berhasil dihapus.");
        } else {
            player.sendMessage(prefix + ChatColor.RED + "Unit tidak ditemukan!");
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (npc.isSpawned() && npc.getEntity() != null && npc.getEntity().equals(entity)) {
                removeHologramOfNPC(npc.getUniqueId());
                removeBossBarOfNPC(npc.getUniqueId());
                String name = npc.getName();
                Bukkit.broadcastMessage(ChatColor.YELLOW + name + " died");
                break;
            }
        }
    }

    private void startEnterpriseEngineTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                reloadConfig();

                boolean cpvpActive = getConfig().getBoolean("settings.attack-cpvp", true);
                boolean chatActive = getConfig().getBoolean("settings.chatbot", true);
                boolean autoHeal = getConfig().getBoolean("combat.auto-heal", true);
                boolean auraActive = getConfig().getBoolean("settings.particle-aura", true);
                List<String> slangs = getConfig().getStringList("chat-slang");

                for (NPC npc : CitizensAPI.getNPCRegistry()) {
                    if (!npc.isSpawned() || npc.getEntity() == null) continue;

                    updateOrSpawnHologram(npc);
                    updateOrSpawnBossBar(npc);

                    LivingEntity npcEntity = (LivingEntity) npc.getEntity();

                    if (auraActive) {
                        String auraName = getConfig().getString("visuals.aura-type", "SOUL_FIRE_FLAME");
                        Particle p = Particle.SOUL_FIRE_FLAME;
                        try { p = Particle.valueOf(auraName); } catch (Exception ignored) {}
                        npcEntity.getWorld().spawnParticle(p, npcEntity.getLocation().add(0, 1, 0), 2, 0.3, 0.5, 0.3, 0.02);
                    }

                    if (autoHeal && npcEntity.getHealth() < 10.0 && npcEntity.getHealth() > 0.0) {
                        npcEntity.setHealth(Math.min(20.0, npcEntity.getHealth() + 6.0));
                        npcEntity.getWorld().spawnParticle(Particle.HEART, npcEntity.getLocation().add(0, 2, 0), 5, 0.3, 0.3, 0.3, 0.1);
                        npcEntity.getWorld().playSound(npcEntity.getLocation(), Sound.ENTITY_GENERIC_DRINK, 0.8f, 1.0f);
                    }

                    if (cpvpActive) {
                        double radius = getConfig().getDouble("combat.attack-radius", 8.0);
                        for (Entity target : npcEntity.getNearbyEntities(radius, radius, radius)) {
                            if (target instanceof Player && !target.getName().equals(npc.getName())) {
                                if (random.nextInt(7) == 0) {
                                    Location loc = target.getLocation();
                                    loc.getWorld().spawn(loc, EndCrystal.class);
                                    loc.getWorld().playSound(loc, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1.0f, 1.0f);
                                    
                                    if (random.nextInt(3) == 0) {
                                        target.sendMessage(colorize("&c[" + npc.getName() + "] S-Class Crystal & Anchor Combat Active! 💥"));
                                    }
                                }
                            }
                        }
                    }

                    if (chatActive && slangs != null && !slangs.isEmpty() && random.nextInt(120) < 5) {
                        String randomSlang = slangs.get(random.nextInt(slangs.size()));
                        Bukkit.broadcastMessage(ChatColor.GRAY + "<" + ChatColor.AQUA + npc.getName() + ChatColor.GRAY + "> " + ChatColor.WHITE + randomSlang);
                    }
                }
            }
        }.runTaskTimer(this, 0L, 10L);
    }

    private void updateOrSpawnHologram(NPC npc) {
        if (!getConfig().getBoolean("settings.hologram", true) || !npc.isSpawned() || npc.getEntity() == null) {
            removeHologramOfNPC(npc.getUniqueId());
            return;
        }

        LivingEntity entity = (LivingEntity) npc.getEntity();
        Location loc = entity.getLocation().add(0, 2.3, 0);
        String rgbFormat = getConfig().getString("visuals.hologram-rgb-format", "&#00F2FF&l[Enterprise] &#FFFFFF%npc_name% &7- &#00FF66%health% HP");
        String text = colorize(rgbFormat.replace("%npc_name%", npc.getName()).replace("%health%", String.format("%.1f", entity.getHealth())));

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
        
        if (bar == null) {
            bar = Bukkit.createBossBar(colorize("&#00F2FFUnit: &#FFFFFF" + npc.getName()), BarColor.PURPLE, BarStyle.SOLID);
            bar.setVisible(true);
            botBossBars.put(npc.getUniqueId(), bar);
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
        int extra = getConfig().getInt("motd.extra-online-count", 10);
        event.setMaxPlayers(event.getMaxPlayers() + extra);

        String motdText = colorize(getConfig().getString("motd.text", "&#00F2FF&lDanzzBot Enterprise"));
        event.setMotd(motdText);
    }

    public static class DanzzBotPlaceholderExpansion extends PlaceholderExpansion {
        private final DanzzBot plugin;
        public DanzzBotPlaceholderExpansion(DanzzBot plugin) { this.plugin = plugin; }

        @Override public @NotNull String getIdentifier() { return "danzzbot"; }
        @Override public @NotNull String getAuthor() { return "DanzzReal"; }
        @Override public @NotNull String getVersion() { return "8.0"; }
        @Override public boolean persist() { return true; }

        @Override
        public String onPlaceholderRequest(Player player, @NotNull String params) {
            if (params.equalsIgnoreCase("count")) {
                int count = 0;
                for (NPC npc : CitizensAPI.getNPCRegistry()) { if (npc.isSpawned()) count++; }
                return String.valueOf(count);
            }
            if (params.equalsIgnoreCase("templates")) {
                return String.valueOf(plugin.createdTemplates.size());
            }
            return null;
        }
    }
                }
