package com.danzzreal.danzzbot;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.Property;
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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
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

        public BotSession(String name, String defaultSkinUrl, String defaultModel) {
            this.name = name;
            this.nametag = name;
            this.gradientColor = "&f";
            this.skinUrl = defaultSkinUrl;
            this.modelType = defaultModel.equalsIgnoreCase("slim") ? "slim" : "wide";
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
        public void setModelType(String modelType) { this.modelType = modelType.equalsIgnoreCase("slim") ? "slim" : "wide"; }
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

        // Teks Banner Keren saat Plugin Dimuat / Restart
        List<String> restartMsgs = getConfig().getStringList("restart-messages");
        for (String msg : restartMsgs) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&b[DanzzBot] &f" + msg));
        }

        String[] cmds = {"botgui", "botreload", "botcreate", "botspawn", "botpossess", "botunpossess", "botchat", "botkill", "botitem", "botinv", "botskin", "nametag", "botgradient"};
        for (String cmd : cmds) {
            if (getCommand(cmd) != null) {
                getCommand(cmd).setExecutor(this);
                getCommand(cmd).setTabCompleter(this);
            }
        }
        getServer().getPluginManager().registerEvents(this, this);
        startAdvancedPlayerEngine();
        getLogger().info("DanzzBot v1.3.0 Master Active!");
    }

    @Override
    public void onDisable() {
        for (BotSession bot : botRegistry.values()) {
            removeBotAndAnnounce(bot, "left the game");
        }
        botRegistry.clear();
        playerPossessions.clear();
        viewingBotInventory.clear();
    }

    private void removeBotAndAnnounce(BotSession bot, String reasonMessage) {
        if (bot.getEntity() != null) {
            bot.getEntity().remove();
            bot.setEntity(null);
            if (bot.isSpawned()) {
                Bukkit.broadcastMessage(ChatColor.YELLOW + bot.getName() + " " + reasonMessage);
            }
            bot.setSpawned(false);
        }
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
            else if (args.length == 3) suggestions.add("https://minesk.in/b99aa1f02594468c95307736628af54a");
            else if (args.length == 4) { suggestions.add("wide"); suggestions.add("slim"); }
        } else if (cmd.equals("botkill")) {
            if (args.length == 1) suggestions.addAll(botRegistry.keySet());
            else if (args.length == 2) { suggestions.add("kill"); suggestions.add("remove"); }
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
            player.sendMessage(ChatColor.GREEN + "✔ Konfigurasi DanzzBot berhasil di-reload!");
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
            String defaultUrl = getConfig().getString("default-skin.url", "https://minesk.in/b99aa1f02594468c95307736628af54a");
            String defaultModel = getConfig().getString("default-skin.model", "wide");
            botRegistry.put(name, new BotSession(name, defaultUrl, defaultModel));
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
            return true;
        }
        if (cmd.equals("botkill")) {
            if (args.length < 1) return true;
            BotSession bot = botRegistry.get(args[0]);
            if (bot == null) return true;
            if (args.length >= 2 && args[1].equalsIgnoreCase("remove")) {
                removeBotAndAnnounce(bot, "left the game");
                player.sendMessage(ChatColor.GREEN + "✔ Bot dihapus.");
            } else {
                Bukkit.broadcastMessage(ChatColor.YELLOW + bot.getName() + " died");
                removeBotAndAnnounce(bot, "left the game");
            }
            return true;
        }
        if (cmd.equals("botitem")) {
            if (args.length < 2) return true;
            BotSession bot = botRegistry.get(args[0]);
            if (bot != null && bot.getEntity() != null) {
                if (args[1].equalsIgnoreCase("sword")) {
                    bot.getEntity().getEquipment().setItemInMainHand(player.getInventory().getItemInMainHand());
                } else if (args[1].equalsIgnoreCase("armor")) {
                    bot.getEntity().getEquipment().setHelmet(player.getInventory().getHelmet());
                    bot.getEntity().getEquipment().setChestplate(player.getInventory().getChestplate());
                    bot.getEntity().getEquipment().setLeggings(player.getInventory().getLeggings());
                    bot.getEntity().getEquipment().setBoots(player.getInventory().getBoots());
                }
            }
            return true;
        }
        if (cmd.equals("botpossess")) {
            if (!getConfig().getBoolean("features.possess-mode.enabled", true)) return true;
            if (args.length < 1) {
                openPossessSelectGUI(player);
                return true;
            }
            BotSession bot = botRegistry.get(args[0]);
            if (bot != null && bot.isSpawned()) {
                bot.setPossessed(true);
                playerPossessions.put(player.getUniqueId(), args[0]);
                player.teleport(bot.getLocation());
            }
            return true;
        }
        if (cmd.equals("botunpossess")) {
            String botName = playerPossessions.remove(player.getUniqueId());
            if (botName != null && botRegistry.containsKey(botName)) {
                botRegistry.get(botName).setPossessed(false);
            }
            player.sendMessage(ChatColor.GREEN + "🔮 Keluar dari mode rasuk.");
            return true;
        }
        if (cmd.equals("botskin")) {
            if (args.length >= 4 && args[0].equalsIgnoreCase("url")) {
                BotSession bot = botRegistry.get(args[1]);
                if (bot != null) {
                    bot.setSkinUrl(args[2]);
                    bot.setModelType(args[3]);
                    player.sendMessage(ChatColor.GREEN + "🎨 Skin diperbarui untuk " + args[1]);
                }
                return true;
            }
            openSkinSelectGUI(player);
            return true;
        }
        if (cmd.equals("nametag") && args.length >= 2) {
            BotSession bot = botRegistry.get(args[0]);
            if (bot != null) {
                StringBuilder tag = new StringBuilder();
                for (int i = 1; i < args.length; i++) tag.append(args[i]).append(" ");
                bot.setNametag(tag.toString().trim());
                if (bot.getEntity() != null) bot.getEntity().setCustomName(bot.getFormattedNametag(getConfig()));
            }
            return true;
        }
        if (cmd.equals("botgradient") && args.length >= 2) {
            BotSession bot = botRegistry.get(args[0]);
            if (bot != null) {
                bot.setGradientColor(args[1]);
                if (bot.getEntity() != null) bot.getEntity().setCustomName(bot.getFormattedNametag(getConfig()));
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

    private void openPage1(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, GUI_PAGE_1);
        gui.setItem(11, createConfigHead("gui-heads.page1-to-page2", ChatColor.GREEN + "➡️ Control Panel & Toggles (Page 2)", null));
        if (getConfig().getBoolean("features.possess-mode.enabled", true)) {
            gui.setItem(13, createConfigHead("gui-heads.possess-bot", ChatColor.LIGHT_PURPLE + "🔮 Possess / Control Bot", null));
        }
        gui.setItem(15, createConfigHead("gui-heads.bot-list", ChatColor.GOLD + "📋 Open Bot List Page", null));
        player.openInventory(gui);
    }

    private void openPage2(Player player) {
        Inventory gui = Bukkit.createInventory(null, 36, GUI_PAGE_2);
        gui.setItem(10, createConfigHead("gui-heads.create-bot", ChatColor.AQUA + "➕ Create Bot", null));
        gui.setItem(12, createConfigHead("gui-heads.change-skin", ChatColor.GREEN + "🎨 Configure MineSkin URL", null));
        gui.setItem(14, createConfigHead("gui-heads.view-inventory", ChatColor.DARK_AQUA + "🎒 View Bot Inventory", null));
        gui.setItem(16, createConfigHead("gui-heads.reload-config", ChatColor.RED + "🔄 Reload Config", null));

        boolean pvpEnabled = getConfig().getBoolean("features.bot-pvp-attack.enabled", true);
        gui.setItem(19, createConfigHead("gui-heads.toggle-pvp", (pvpEnabled ? ChatColor.GREEN + "⚔ PvP Attack: ON" : ChatColor.RED + "⚔ PvP Attack: OFF"), Arrays.asList(ChatColor.GRAY + "Klik untuk ON/OFF")));

        boolean autoKillEnabled = getConfig().getBoolean("features.auto-kill-nearest-player.enabled", false);
        gui.setItem(20, createConfigHead("gui-heads.toggle-autokill", (autoKillEnabled ? ChatColor.DARK_RED + "☠ Auto-Kill: ON" : ChatColor.GRAY + "☠ Auto-Kill: OFF"), Arrays.asList(ChatColor.GRAY + "Klik untuk ON/OFF")));

        boolean autoChatEnabled = getConfig().getBoolean("features.auto-chat.enabled", true);
        gui.setItem(21, createConfigHead("gui-heads.toggle-autochat", (autoChatEnabled ? ChatColor.GREEN + "💬 Auto Chat: ON" : ChatColor.RED + "💬 Auto Chat: OFF"), Arrays.asList(ChatColor.GRAY + "Klik untuk ON/OFF")));

        boolean possessEnabled = getConfig().getBoolean("features.possess-mode.enabled", true);
        gui.setItem(22, createConfigHead("gui-heads.toggle-possess", (possessEnabled ? ChatColor.GREEN + "🔮 Possess Mode: ON" : ChatColor.RED + "🔮 Possess Mode: OFF"), Arrays.asList(ChatColor.GRAY + "Klik untuk ON/OFF")));

        boolean aiEnabled = getConfig().getBoolean("features.ai-activities.enabled", true);
        gui.setItem(23, createConfigHead("gui-heads.toggle-ai", (aiEnabled ? ChatColor.GREEN + "⛏ AI Activities: ON" : ChatColor.RED + "⛏ AI Activities: OFF"), Arrays.asList(ChatColor.GRAY + "Klik untuk ON/OFF")));

        boolean indicatorEnabled = getConfig().getBoolean("features.health-indicator.enabled", true);
        gui.setItem(24, createConfigHead("gui-heads.toggle-health", (indicatorEnabled ? ChatColor.GREEN + "❤ Health Indicator: ON" : ChatColor.RED + "❤ Healt
