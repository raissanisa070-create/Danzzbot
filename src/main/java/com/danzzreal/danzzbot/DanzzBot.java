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
        MINING("Nambang"),
        WOODCUTTING("Woodcutting"),
        PVP("PVP Modern (CPVP/Mace)");

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
        private BotActivity currentActivity = BotActivity.EXPLORING;
        private Vector moveVector = new Vector(0, 0, 0);

        public BotSession(String name) {
            this.name = name;
            this.nametag = name;
            this.gradientColor = "&f";
            this.skinUrl = "";
            this.modelType = "wide";
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
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
        public BotActivity getCurrentActivity() { return currentActivity; }
        public void setCurrentActivity(BotActivity currentActivity) { this.currentActivity = currentActivity; }
        public Vector getMoveVector() { return moveVector; }
        public void setMoveVector(Vector moveVector) { this.moveVector = moveVector; }

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

    private final String GUI_PAGE_1 = ChatColor.DARK_PURPLE + "✦ DanzzBot | Main Menu (Page 1) ✦";
    private final String GUI_PAGE_2 = ChatColor.GOLD + "✦ DanzzBot | Control Panel (Page 2) ✦";
    private final String GUI_PAGE_LIST = ChatColor.BLUE + "✦ DanzzBot | Bot Spawn List ✦";
    private final String GUI_POSSESS_SELECT = ChatColor.LIGHT_PURPLE + "✦ Select Bot to Possess ✦";
    private final String GUI_SELECT_SKIN = ChatColor.GREEN + "✦ Select Bot for Skin Config ✦";

    @Override
    public void onEnable() {
        saveDefaultConfig();

        String[] cmds = {"botgui", "botreload", "botcreate", "botspawn", "botpossess", "botunpossess", "botchat", "botrename", "botskin", "nametag", "botgradient"};
        for (String cmd : cmds) {
            if (getCommand(cmd) != null) {
                getCommand(cmd).setExecutor(this);
                getCommand(cmd).setTabCompleter(this);
            }
        }
        getServer().getPluginManager().registerEvents(this, this);
        startAdvancedPlayerEngine();
        getLogger().info("DanzzBot v1.3.0 Master Config Active!");
    }

    @Override
    public void onDisable() {
        for (BotSession bot : botRegistry.values()) {
            if (bot.getEntity() != null) bot.getEntity().remove();
        }
        botRegistry.clear();
        playerPossessions.clear();
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
        } else if (cmd.equals("botspawn") || cmd.equals("botpossess") || cmd.equals("botchat") || cmd.equals("botrename") || cmd.equals("nametag") || cmd.equals("botgradient")) {
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
            player.sendMessage(ChatColor.GREEN + "✔ Konfigurasi DanzzBot (config.yml) berhasil di-reload!");
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
                player.sendMessage(ChatColor.RED + "Bot dengan nama ini sudah terdaftar!");
                return true;
            }
            botRegistry.put(name, new BotSession(name));
            player.sendMessage(ChatColor.GREEN + "✔ Bot '" + name + "' ditambahkan ke list!");
            return true;
        }

        if (cmd.equals("botspawn")) {
            if (args.length < 1) return true;
            spawnBotFromList(args[0], player.getLocation());
            player.sendMessage(ChatColor.GREEN + "🚀 Bot Manusia '" + args[0] + "' di-spawn!");
            return true;
        }

        if (cmd.equals("botpossess")) {
            if (!getConfig().getBoolean("features.possess-mode.enabled", true)) {
                player.sendMessage(ChatColor.RED + "Fitur Mode Possess sedang dimatikan di config.yml!");
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
                player.sendMessage(ChatColor.LIGHT_PURPLE + "🔮 Kamu sekarang mengendalikan bot '" + bot.getName() + "'!");
            } else {
                player.sendMessage(ChatColor.RED + "Bot belum di-spawn atau tidak ditemukan!");
            }
            return true;
        }

        if (cmd.equals("botunpossess")) {
            String botName = playerPossessions.remove(player.getUniqueId());
            if (botName != null && botRegistry.containsKey(botName)) {
                botRegistry.get(botName).setPossessed(false);
            }
            player.sendMessage(ChatColor.GREEN + "🔮 Berhasil keluar dari mode rasuk bot.");
            return true;
        }

        if (cmd.equals("botchat")) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.YELLOW + "Penggunaan: /botchat <nama_bot> <pesan_gaul>");
                return true;
            }
            BotSession bot = botRegistry.get(args[0]);
            if (bot != null) {
                StringBuilder msg = new StringBuilder();
                for (int i = 1; i < args.length; i++) msg.append(args[i]).append(" ");
                Bukkit.broadcastMessage(bot.getFormattedNametag(getConfig()) + ChatColor.WHITE + ": " + ChatColor.translateAlternateColorCodes('&', msg.toString().trim()));
            }
            return true;
        }

        if (cmd.equals("botskin")) {
            if (args.length >= 4 && args[0].equalsIgnoreCase("url")) {
                String botName = args[1];
                String skinUrl = args[2];
                String model = args[3].toLowerCase();

                BotSession bot = botRegistry.get(botName);
                if (bot != null) {
                    bot.setSkinUrl(skinUrl);
                    bot.setModelType(model.equals("slim") ? "slim" : "wide");
                    player.sendMessage(ChatColor.GREEN + "🎨 Skin URL untuk " + botName + " berhasil dipasang!");
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
                player.sendMessage(ChatColor.GREEN + "🌈 Gradient warna diperbarui!");
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
        gui.setItem(11, createCustomHead("Steve", ChatColor.GREEN + "➡️ Go to Page 2 (Create & Manage)", null));
        
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
        gui.setItem(14, createCustomHead("MHF_Question", ChatColor.YELLOW + "🏷️ Change Nametag", null));
        gui.setItem(16, createCustomHead("MHF_Villager", ChatColor.LIGHT_PURPLE + "🌈 Change Gradient", null));
        gui.setItem(22, createCustomHead("MHF_Redstone", ChatColor.RED + "🔄 Reload Config (Hosting)", 
                Arrays.asList(ChatColor.GRAY + "Reload config.yml & Fitur Master")));
        gui.setItem(18, createCustomHead("MHF_ArrowLeft", ChatColor.RED + "⬅️ Back to Page 1", null));
        player.openInventory(gui);
    }

    private void openListPage(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, GUI_PAGE_LIST);
        int slot = 0;
        for (BotSession bot : botRegistry.values()) {
            if (slot >= 45) break;
            List<String> lore = Arrays.asList(ChatColor.GRAY + "Aktivitas: " + bot.getCurrentActivity().getDisplayName(), ChatColor.YELLOW + "Klik untuk Spawn!");
            gui.setItem(slot, createCustomHead(bot.getName(), bot.getFormattedNametag(getConfig()), lore));
            slot++;
        }
        gui.setItem(49, createCustomHead("MHF_ArrowLeft", ChatColor.RED + "⬅️ Back to Page 1", null));
        player.openInventory(gui);
    }

    private void openPossessSelectGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, GUI_POSSESS_SELECT);
        int slot = 0;
        for (BotSession bot : botRegistry.values()) {
            if (slot >= 45) break;
            if (bot.isSpawned()) {
                gui.setItem(slot, createCustomHead(bot.getName(), ChatColor.LIGHT_PURPLE + "🔮 Possess " + bot.getName(), null));
                slot++;
            }
        }
        gui.setItem(49, createCustomHead("MHF_ArrowLeft", ChatColor.RED + "⬅️ Back to Page 1", null));
        player.openInventory(gui);
    }

    private void openSkinSelectGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, GUI_SELECT_SKIN);
        int slot = 0;
        for (BotSession bot : botRegistry.values()) {
            if (slot >= 45) break;
            gui.setItem(slot, createCustomHead(bot.getName(), ChatColor.GREEN + bot.getName(), null));
            slot++;
        }
        gui.setItem(49, createCustomHead("MHF_ArrowLeft", ChatColor.RED + "⬅️ Back to Page 2", null));
        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!title.equals(GUI_PAGE_1) && !title.equals(GUI_PAGE_2) && !title.equals(GUI_PAGE_LIST) && !title.equals(GUI_POSSESS_SELECT) && !title.equals(GUI_SELECT_SKIN)) return;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        if (title.equals(GUI_PAGE_1)) {
            if (slot == 11) openPage2(player);
            else if (slot == 13 && getConfig().getBoolean("features.possess-mode.enabled", true)) openPossessSelectGUI(player);
            else if (slot == 15) openListPage(player);
        } else if (title.equals(GUI_PAGE_2)) {
            if (slot == 10) { player.sendMessage(ChatColor.YELLOW + "Ketik: /botcreate <nama_bot>"); player.closeInventory(); }
            else if (slot == 12) openSkinSelectGUI(player);
            else if (slot == 22) {
                reloadConfig();
                player.sendMessage(ChatColor.GREEN + "✔ All features & config successfully reloaded!");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                player.closeInventory();
            } else if (slot == 18) openPage1(player);
        } else if (title.equals(GUI_PAGE_LIST)) {
            if (slot == 49) openPage1(player);
            else if (event.getCurrentItem() != null && event.getCurrentItem().hasItemMeta()) {
                String botName = ChatColor.stripColor(event.getCurrentItem().getItemMeta().getDisplayName());
                for (BotSession bot : botRegistry.values()) {
                    if (ChatColor.stripColor(bot.getFormattedNametag(getConfig())).equalsIgnoreCase(botName)) {
                        spawnBotFromList(bot.getName(), player.getLocation());
                        player.sendMessage(ChatColor.GREEN + "🚀 Bot '" + bot.getName() + "' di-spawn!");
                        player.closeInventory();
                        break;
                    }
                }
            }
        } else if (title.equals(GUI_POS
