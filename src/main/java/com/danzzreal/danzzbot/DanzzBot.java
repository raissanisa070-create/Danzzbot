package com.danzzreal.danzzbot;

import com.destroystokyo.paper.profile.PlayerProfile;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import java.util.*;

public final class DanzzBot extends JavaPlugin implements CommandExecutor, TabCompleter, Listener {

    public enum BotActivity {
        EXPLORING("Berkeliaran"),
        MINING("Mining (Dapat Ore/Block)"),
        BUILDING("Membangun Rumah"),
        PVP("PVP Combat");

        private final String displayName;
        BotActivity(String name) { this.displayName = name; }
        public String getDisplayName() { return displayName; }
    }

    public static class BotSession {
        private String name;
        private String nametag;
        private String gradientColor;
        private String skinUrl;
        private String modelType;
        private Location location;
        private ArmorStand entity;
        private boolean spawned = false;
        private boolean possessed = false;
        private double health = 20.0;
        private int animTick = 0;
        private int attackCooldown = 0;
        private int chatTimer = 0;
        private boolean totemPopped = false;
        private UUID targetPlayerUUID = null;
        private final ItemStack[] inventoryStorage = new ItemStack[27];
        private BotActivity currentActivity = BotActivity.EXPLORING;
        private Vector moveVector = new Vector(0, 0, 0);

        public BotSession(String name) {
            this.name = name;
            this.nametag = name;
            this.gradientColor = "&f";
            this.skinUrl = "";
            this.modelType = "wide";
            Arrays.fill(inventoryStorage, new ItemStack(Material.AIR));
        }

        public String getName() { return name; }
        public String getNametag() { return nametag; }
        public void setNametag(String nametag) { this.nametag = nametag; }
        public String getGradientColor() { return gradientColor; }
        public void setGradientColor(String gradientColor) { this.gradientColor = gradientColor; }
        public String getSkinUrl() { return skinUrl; }
        public void setSkinUrl(String skinUrl) { this.skinUrl = skinUrl; }
        public String getModelType() { return modelType; }
        public void setModelType(String modelType) { this.modelType = modelType; }
        public Location getLocation() { return location; }
        public void setLocation(Location location) { this.location = location; }
        public ArmorStand getEntity() { return entity; }
        public void setEntity(ArmorStand entity) { this.entity = entity; }
        public boolean isSpawned() { return spawned; }
        public void setSpawned(boolean spawned) { this.spawned = spawned; }
        public boolean isPossessed() { return possessed; }
        public void setPossessed(boolean possessed) { this.possessed = possessed; }
        public double getHealth() { return health; }
        public void setHealth(double health) { this.health = health; }
        public int getAnimTick() { return animTick; }
        public void setAnimTick(int animTick) { this.animTick = animTick; }
        public int getAttackCooldown() { return attackCooldown; }
        public void setAttackCooldown(int attackCooldown) { this.attackCooldown = attackCooldown; }
        public int getChatTimer() { return chatTimer; }
        public void setChatTimer(int chatTimer) { this.chatTimer = chatTimer; }
        public boolean isTotemPopped() { return totemPopped; }
        public void setTotemPopped(boolean totemPopped) { this.totemPopped = totemPopped; }
        public UUID getTargetPlayerUUID() { return targetPlayerUUID; }
        public void setTargetPlayerUUID(UUID uuid) { this.targetPlayerUUID = uuid; }
        public ItemStack[] getInventoryStorage() { return inventoryStorage; }
        public BotActivity getCurrentActivity() { return currentActivity; }
        public void setCurrentActivity(BotActivity activity) { this.currentActivity = activity; }
        public Vector getMoveVector() { return moveVector; }
        public void setMoveVector(Vector vec) { this.moveVector = vec; }

        public String getFormattedNametag(FileConfiguration config) {
            String baseName = ChatColor.translateAlternateColorCodes('&', gradientColor + nametag);
            boolean indicatorEnabled = config.getBoolean("features.health-indicator.enabled", true);
            if (indicatorEnabled) {
                String highText = config.getString("features.health-indicator.high-health-text", " [❤❤❤❤❤]");
                String lowText = config.getString("features.health-indicator.low-health-text", " [❤❤]");
                String hearts = health > 10 ? ChatColor.GREEN + highText : ChatColor.RED + lowText;
                return baseName + hearts;
            }
            return baseName;
        }
    }

    private final Map<String, BotSession> botRegistry = new HashMap<>();
    private final Map<UUID, String> playerPossessions = new HashMap<>();
    private final Map<UUID, String> viewingBotInventory = new HashMap<>();

    private final String GUI_PAGE_1 = ChatColor.DARK_PURPLE + "✦ DanzzBot | Main Menu (Page 1) ✦";
    private final String GUI_PAGE_2 = ChatColor.GOLD + "✦ DanzzBot | Control Panel (Page 2) ✦";
    private final String GUI_PAGE_LIST = ChatColor.BLUE + "✦ DanzzBot | Bot Spawn List ✦";
    private final String GUI_POSSESS_SELECT = ChatColor.LIGHT_PURPLE + "✦ Select Bot to Possess ✦";
    private final String GUI_SELECT_SKIN = ChatColor.GREEN + "✦ Select Bot for Skin Config ✦";
    private final String GUI_INVENTORY_SELECT = ChatColor.DARK_AQUA + "✦ Select Bot to View Inventory ✦";

    @Override
    public void onEnable() {
        saveDefaultConfig();

        String[] cmds = {"botgui", "botreload", "botcreate", "botspawn", "botpossess", "botunpossess", "botchat", "botkill", "botitem", "botinv", "botskin", "nametag", "botgradient"};
        for (String cmd : cmds) {
            if (getCommand(cmd) != null) {
                getCommand(cmd).setExecutor(this);
                getCommand(cmd).setTabCompleter(this);
            }
        }
        getServer().getPluginManager().registerEvents(this, this);
        startAdvancedPlayerEngine();
        getLogger().info("DanzzBot v1.3.0 Ultimate Master Suite Active!");
    }

    @Override
    public void onDisable() {
        for (BotSession bot : botRegistry.values()) {
            if (bot.getEntity() != null) bot.getEntity().remove();
        }
        botRegistry.clear();
        playerPossessions.clear();
        viewingBotInventory.clear();
    }

    @EventHandler
    public void onServerListPing(ServerListPingEvent event) {
        int realPlayers = Bukkit.getOnlinePlayers().size();
        int spawnedBots = 0;
        List<PlayerProfile> profiles = new ArrayList<>();

        for (BotSession bot : botRegistry.values()) {
            if (bot.isSpawned()) {
                spawnedBots++;
                PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), bot.getName());
                profiles.add(profile);
            }
        }

        event.setNumPlayers(realPlayers + spawnedBots);
        for (PlayerProfile profile : profiles) {
            event.getPlayerSample().add(profile);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        String cmd = command.getName().toLowerCase();

        if (cmd.equals("botskin")) {
            if (args.length == 1) suggestions.add("url");
            else if (args.length == 2 && args[0].equalsIgnoreCase("url")) suggestions.addAll(botRegistry.keySet());
            else if (args.length == 4) { suggestions.add("wide"); suggestions.add("slim"); }
        } else if (cmd.equals("botkill")) {
            if (args.length == 1) suggestions.addAll(botRegistry.keySet());
            else if (args.length == 2) suggestions.add("kill");
            else if (args.length == 3) {
                for (Player p : Bukkit.getOnlinePlayers()) suggestions.add(p.getName());
            }
        } else if (cmd.equals("botitem") || cmd.equals("botinv")) {
            if (args.length == 1) suggestions.addAll(botRegistry.keySet());
            else if (args.length == 2 && cmd.equals("botitem")) { suggestions.add("sword"); suggestions.add("pickaxe"); suggestions.add("armor"); }
        } else if (cmd.equals("botspawn") || cmd.equals("botpossess") || cmd.equals("botchat") || cmd.equals("nametag") || cmd.equals("botgradient")) {
            if (args.length == 1) suggestions.addAll(botRegistry.keySet());
        }
        return suggestions;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;
        String cmd = command.getName().toLowerCase();

        if (cmd.equals("botgui")) {
            openPage1(player);
            return true;
        }

        if (cmd.equals("botreload")) {
            reloadConfig();
            player.sendMessage(ChatColor.GREEN + "✔ Konfigurasi DanzzBot berhasil di-reload dari hosting!");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            return true;
        }

        if (cmd.equals("botcreate")) {
            if (args.length < 1) {
                player.sendMessage(ChatColor.YELLOW + "Penggunaan: /botcreate <nama_bot>");
                return true;
            }
            String name = args[0];
            if (botRegistry.containsKey(name)) {
                player.sendMessage(ChatColor.RED + "Bot sudah terdaftar!");
                return true;
            }
            botRegistry.put(name, new BotSession(name));
            player.sendMessage(ChatColor.GREEN + "✔ Bot '" + name + "' berhasil dibuat!");
            return true;
        }

        if (cmd.equals("botspawn")) {
            if (args.length < 1) return true;
            spawnBotFromList(args[0], player.getLocation());
            player.sendMessage(ChatColor.GREEN + "🚀 Bot Manusia '" + args[0] + "' di-spawn!");
            return true;
        }

        if (cmd.equals("botinv")) {
            if (args.length < 1) {
                openBotInventorySelectGUI(player);
                return true;
            }
            BotSession bot = botRegistry.get(args[0]);
            if (bot != null) openBotInventory(player, bot);
            else player.sendMessage(ChatColor.RED + "Bot tidak ditemukan!");
            return true;
        }

        if (cmd.equals("botkill")) {
            if (args.length < 3 || !args[1].equalsIgnoreCase("kill")) {
                player.sendMessage(ChatColor.YELLOW + "Penggunaan: /botkill <nama_bot> kill <nama_player>");
                return true;
            }
            BotSession bot = botRegistry.get(args[0]);
            Player target = Bukkit.getPlayer(args[2]);
            if (bot != null && target != null) {
                bot.setTargetPlayerUUID(target.getUniqueId());
                player.sendMessage(ChatColor.RED + "⚔ Bot '" + bot.getName() + "' diperintahkan memburu player " + target.getName() + "!");
            }
            return true;
        }

        if (cmd.equals("botitem")) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.YELLOW + "Penggunaan: /botitem <nama_bot> <sword|pickaxe|armor>");
                return true;
            }
            BotSession bot = botRegistry.get(args[0]);
            if (bot != null && bot.getEntity() != null) {
                String action = args[1].toLowerCase();
                if (action.equals("sword")) {
                    bot.getEntity().getEquipment().setItemInMainHand(player.getInventory().getItemInMainHand());
                    player.sendMessage(ChatColor.GREEN + "🗡️ Senjata bot diperbarui!");
                } else if (action.equals("armor")) {
                    bot.getEntity().getEquipment().setHelmet(player.getInventory().getHelmet());
                    bot.getEntity().getEquipment().setChestplate(player.getInventory().getChestplate());
                    bot.getEntity().getEquipment().setLeggings(player.getInventory().getLeggings());
                    bot.getEntity().getEquipment().setBoots(player.getInventory().getBoots());
                    player.sendMessage(ChatColor.GREEN + "🛡️ Armor bot diperbarui!");
                }
            }
            return true;
        }

        if (cmd.equals("botpossess")) {
            if (!getConfig().getBoolean("features.possess-mode.enabled", true)) {
                player.sendMessage(ChatColor.RED + "Mode Possess dimatikan di config.yml!");
                return true;
            }
            if (args.length < 1) {
                openPossessSelectGUI(player);
                return true;
            }
            BotSession bot = botRegistry.get(args[0]);
            if (bot != null && bot.isSpawned()) {
                bot.setPossessed(true);
                playerPossessions.put(player.getUniqueId(), args[0]);
                player.teleport(bot.getLocation());
                player.sendMessage(ChatColor.LIGHT_PURPLE + "🔮 Mengendalikan bot '" + bot.getName() + "'!");
            }
            return true;
        }

        if (cmd.equals("botunpossess")) {
            String botName = playerPossessions.remove(player.getUniqueId());
            if (botName != null && botRegistry.containsKey(botName)) {
                botRegistry.get(botName).setPossessed(false);
            }
            player.sendMessage(ChatColor.GREEN + "🔮 Keluar dari mode rasuk bot.");
            return true;
        }

        if (cmd.equals("botskin")) {
            if (args.length >= 4 && args[0].equalsIgnoreCase("url")) {
                String botName = args[1];
                BotSession bot = botRegistry.get(botName);
                if (bot != null) {
                    bot.setSkinUrl(args[2]);
                    bot.setModelType(args[3].equalsIgnoreCase("slim") ? "slim" : "wide");
                    player.sendMessage(ChatColor.GREEN + "🎨 Skin URL dipasang untuk " + botName + "!");
                }
                return true;
            }
            openSkinSelectGUI(player);
            return true;
        }

        if (cmd.equals("nametag")) {
            if (args.length >= 2) {
                BotSession bot = botRegistry.get(args[0]);
                if (bot != null) {
                    StringBuilder tag = new StringBuilder();
                    for (int i = 1; i < args.length; i++) tag.append(args[i]).append(" ");
                    bot.setNametag(tag.toString().trim());
                    if (bot.getEntity() != null) bot.getEntity().setCustomName(bot.getFormattedNametag(getConfig()));
                    player.sendMessage(ChatColor.GREEN + "🏷️ Nametag diperbarui!");
                }
                return true;
            }
        }

        if (cmd.equals("botgradient")) {
            if (args.length < 2) return true;
            BotSession bot = botRegistry.get(args[0]);
            if (bot != null) {
                bot.setGradientColor(args[1]);
                if (bot.getEntity() != null) bot.getEntity().setCustomName(bot.getFormattedNametag(getConfig()));
                player.sendMessage(ChatColor.GREEN + "🌈 Gradient diperbarui!");
            }
            return true;
        }

        return false;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (playerPossessions.containsKey(player.getUniqueId())) {
            BotSession bot = botRegistry.get(playerPossessions.get(player.getUniqueId()));
            if (bot != null && bot.getEntity() != null) {
                bot.getEntity().teleport(player.getLocation());
            }
        }
    }

    // ==========================================
    // GUI MASTER CONTROL PANEL & INVENTORY
    // ==========================================

    private void openPage1(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, GUI_PAGE_1);
        gui.setItem(11, createCustomHead("Steve", ChatColor.GREEN + "➡️ Go to Page 2 (Control Panel & Toggles)", null));
        if (getConfig().getBoolean("features.possess-mode.enabled", true)) {
            gui.setItem(13, createCustomHead("MHF_EnderDragon", ChatColor.LIGHT_PURPLE + "🔮 Possess / Control Bot", null));
        }
        gui.setItem(15, createCustomHead("MHF_TNTServer", ChatColor.GOLD + "📋 Open Bot List Page", null));
        player.openInventory(gui);
    }

    private void openPage2(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, GUI_PAGE_2);
        gui.setItem(10, createCustomHead("MHF_Chest", ChatColor.AQUA + "➕ Create Bot", null));
        gui.setItem(12, createCustomHead("Alex", ChatColor.GREEN + "🎨 Change Skin URL", null));
        
        boolean pvpEnabled = getConfig().getBoolean("features.bot-pvp-attack.enabled", true);
        gui.setItem(13, createCustomHead(pvpEnabled ? "MHF_Emerald" : "MHF_Redstone", 
                (pvpEnabled ? ChatColor.GREEN + "⚔ PvP Attack: ON" : ChatColor.RED + "⚔ PvP Attack: OFF"), 
                Arrays.asList(ChatColor.GRAY + "Klik untuk toggle PvP bot")));

        boolean autoKillEnabled = getConfig().getBoolean("features.auto-kill-nearest-player.enabled", false);
        gui.setItem(14, createCustomHead(autoKillEnabled ? "MHF_NetherStar" : "MHF_Redstone", 
                (autoKillEnabled ? ChatColor.DARK_RED + "☠ Auto-Kill Nearest: ON" : ChatColor.GRAY + "☠ Auto-Kill Nearest: OFF"), 
                Arrays.asList(ChatColor.GRAY + "Klik untuk toggle auto-kill player terdekat")));

        gui.setItem(16, createCustomHead("MHF_Chest", ChatColor.DARK_AQUA + "🎒 View Bot Inventory", 
                Arrays.asList(ChatColor.GRAY + "Atur tas & barang hasil mining bot")));

        gui.setItem(22, createCustomHead("MHF_Redstone", ChatColor.RED + "🔄 Reload Config", null));
        gui.setItem(18, createCustomHead("MHF_ArrowLeft", ChatColor.RED + "⬅️ Back to Page 1", null));
        player.openInventory(gui);
    }

    private void openListPage(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, GUI_PAGE_LIST);
        int slot = 0;
        for (BotSession bot : botRegistry.values()) {
            if (slot >= 45) break;
            List<String> lore = Arrays.asList(
                ChatColor.GRAY + "Aktivitas: " + bot.getCurrentActivity().getDisplayName(),
                ChatColor.GRAY + "Isi Tas: " + countBotItems(bot) + " Item",
                ChatColor.YELLOW + "Klik untuk Spawn!"
            );
            gui.setItem(slot, createCustomHead(bot.getName(), bot.g
