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
        EXPLORING("Berkeliaran"), MINING("Mining"), BUILDING("Building"), PVP("PVP");
        private final String name;
        BotActivity(String name) { this.name = name; }
        public String getDisplayName() { return name; }
    }

    public static class BotSession {
        private String name, nametag, gradientColor, skinUrl, modelType;
        private Location location;
        private ArmorStand entity;
        private boolean spawned = false, possessed = false, totemPopped = false;
        private double health = 20.0;
        private int animTick = 0, attackCooldown = 0, chatTimer = 0;
        private UUID targetPlayerUUID = null;
        private final ItemStack[] inventoryStorage = new ItemStack[27];
        private BotActivity currentActivity = BotActivity.EXPLORING;
        private Vector moveVector = new Vector(0, 0, 0);

        public BotSession(String name, String skinUrl, String model) {
            this.name = name;
            this.nametag = name;
            this.gradientColor = "&f";
            this.skinUrl = skinUrl;
            this.modelType = model.equalsIgnoreCase("slim") ? "slim" : "wide";
            Arrays.fill(inventoryStorage, new ItemStack(Material.AIR));
        }

        public String getName() { return name; }
        public String getNametag() { return nametag; }
        public void setNametag(String n) { this.nametag = n; }
        public String getGradientColor() { return gradientColor; }
        public void setGradientColor(String g) { this.gradientColor = g; }
        public String getSkinUrl() { return skinUrl; }
        public void setSkinUrl(String s) { this.skinUrl = s; }
        public String getModelType() { return modelType; }
        public void setModelType(String m) { this.modelType = m.equalsIgnoreCase("slim") ? "slim" : "wide"; }
        public Location getLocation() { return location; }
        public void setLocation(Location l) { this.location = l; }
        public ArmorStand getEntity() { return entity; }
        public void setEntity(ArmorStand e) { this.entity = e; }
        public boolean isSpawned() { return spawned; }
        public void setSpawned(boolean s) { this.spawned = s; }
        public boolean isPossessed() { return possessed; }
        public void setPossessed(boolean p) { this.possessed = p; }
        public double getHealth() { return health; }
        public void setHealth(double h) { this.health = h; }
        public int getAnimTick() { return animTick; }
        public void setAnimTick(int a) { this.animTick = a; }
        public int getAttackCooldown() { return attackCooldown; }
        public void setAttackCooldown(int a) { this.attackCooldown = a; }
        public int getChatTimer() { return chatTimer; }
        public void setChatTimer(int c) { this.chatTimer = c; }
        public boolean isTotemPopped() { return totemPopped; }
        public void setTotemPopped(boolean t) { this.totemPopped = t; }
        public UUID getTargetPlayerUUID() { return targetPlayerUUID; }
        public void setTargetPlayerUUID(UUID u) { this.targetPlayerUUID = u; }
        public ItemStack[] getInventoryStorage() { return inventoryStorage; }
        public BotActivity getCurrentActivity() { return currentActivity; }
        public void setCurrentActivity(BotActivity a) { this.currentActivity = a; }
        public Vector getMoveVector() { return moveVector; }
        public void setMoveVector(Vector v) { this.moveVector = v; }

        public String getFormattedNametag(FileConfiguration cfg) {
            String base = ChatColor.translateAlternateColorCodes('&', gradientColor + nametag);
            if (cfg.getBoolean("features.health-indicator.enabled", true)) {
                String hearts = health > 10 ? ChatColor.GREEN + " [❤❤❤❤❤]" : ChatColor.RED + " [❤❤]";
                return base + hearts;
            }
            return base;
        }
    }

    private final Map<String, BotSession> botRegistry = new HashMap<>();
    private final Map<UUID, String> playerPossessions = new HashMap<>();
    private final Map<UUID, String> viewingBotInventory = new HashMap<>();

    private final String GUI_P1 = ChatColor.DARK_PURPLE + "✦ DanzzBot | Main Menu (Page 1) ✦";
    private final String GUI_P2 = ChatColor.GOLD + "✦ DanzzBot | Control Panel (Page 2) ✦";
    private final String GUI_LIST = ChatColor.BLUE + "✦ DanzzBot | Bot Spawn List ✦";
    private final String GUI_POSSESS = ChatColor.LIGHT_PURPLE + "✦ Select Bot to Possess ✦";
    private final String GUI_SKIN = ChatColor.GREEN + "✦ Select Bot for Skin ✦";
    private final String GUI_INV = ChatColor.DARK_AQUA + "✦ Select Bot Inventory ✦";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        for (String msg : getConfig().getStringList("restart-messages")) {
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
        startEngine();
        getLogger().info("DanzzBot v1.3.0 Active!");
    }

    @Override
    public void onDisable() {
        for (BotSession bot : botRegistry.values()) {
            if (bot.getEntity() != null) {
                bot.getEntity().remove();
                if (bot.isSpawned()) Bukkit.broadcastMessage(ChatColor.YELLOW + bot.getName() + " left the game");
            }
        }
        botRegistry.clear();
    }

    @EventHandler
    public void onPing(ServerListPingEvent e) {
        int real = Bukkit.getOnlinePlayers().size();
        int bots = 0;
        for (BotSession b : botRegistry.values()) {
            if (b.isSpawned()) {
                bots++;
                e.getPlayerSample().add(Bukkit.createProfile(UUID.randomUUID(), b.getName()));
            }
        }
        e.setNumPlayers(real + bots);
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String alias, String[] args) {
        List<String> list = new ArrayList<>();
        String cmd = c.getName().toLowerCase();
        if (cmd.equals("botskin")) {
            if (args.length == 1) list.add("url");
            else if (args.length == 2 && args[0].equalsIgnoreCase("url")) list.addAll(botRegistry.keySet());
            else if (args.length == 4) { list.add("wide"); list.add("slim"); }
        } else if (cmd.equals("botkill")) {
            if (args.length == 1) list.addAll(botRegistry.keySet());
            else if (args.length == 2) { list.add("kill"); list.add("remove"); }
        } else if (cmd.equals("botitem") || cmd.equals("botinv") || cmd.equals("botspawn") || cmd.equals("botpossess") || cmd.equals("botchat") || cmd.equals("nametag") || cmd.equals("botgradient")) {
            if (args.length == 1) list.addAll(botRegistry.keySet());
        }
        return list;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;
        String cmd = command.getName().toLowerCase();

        if (cmd.equals("botgui")) { openPage1(p); return true; }
        if (cmd.equals("botreload")) { reloadConfig(); p.sendMessage(ChatColor.GREEN + "✔ Config reloaded!"); return true; }
        if (cmd.equals("botcreate") && args.length > 0) {
            if (botRegistry.containsKey(args[0])) { p.sendMessage(ChatColor.RED + "Bot sudah ada!"); return true; }
            String dUrl = getConfig().getString("default-skin.url", "https://minesk.in/b99aa1f02594468c95307736628af54a");
            String dModel = getConfig().getString("default-skin.model", "wide");
            botRegistry.put(args[0], new BotSession(args[0], dUrl, dModel));
            p.sendMessage(ChatColor.GREEN + "✔ Bot '" + args[0] + "' dibuat!");
            return true;
        }
        if (cmd.equals("botspawn") && args.length > 0) {
            spawnBot(args[0], p.getLocation());
            p.sendMessage(ChatColor.GREEN + "🚀 Bot di-spawn!");
            return true;
        }
        if (cmd.equals("botinv")) {
            if (args.length == 0) { openInvSelect(p); return true; }
            BotSession b = botRegistry.get(args[0]);
            if (b != null) openBotInv(p, b);
            return true;
        }
        if (cmd.equals("botkill") && args.length > 0) {
            BotSession b = botRegistry.get(args[0]);
            if (b != null) {
                if (b.getEntity() != null) b.getEntity().remove();
                b.setSpawned(false);
                Bukkit.broadcastMessage(ChatColor.YELLOW + b.getName() + " left the game");
            }
            return true;
        }
        if (cmd.equals("botpossess") && args.length > 0) {
            BotSession b = botRegistry.get(args[0]);
            if (b != null && b.isSpawned()) {
                b.setPossessed(true);
                playerPossessions.put(p.getUniqueId(), b.getName());
                p.teleport(b.getLocation());
            }
            return true;
        }
        if (cmd.equals("botunpossess")) {
            String bName = playerPossessions.remove(p.getUniqueId());
            if (bName != null && botRegistry.containsKey(bName)) botRegistry.get(bName).setPossessed(false);
            p.sendMessage(ChatColor.GREEN + "🔮 Keluar dari rasuk.");
            return true;
        }
        if (cmd.equals("botskin") && args.length >= 4 && args[0].equalsIgnoreCase("url")) {
            BotSession b = botRegistry.get(args[1]);
            if (b != null) {
                b.setSkinUrl(args[2]);
                b.setModelType(args[3]);
                p.sendMessage(ChatColor.GREEN + "🎨 Skin diperbarui!");
            }
            return true;
        }
        if (cmd.equals("nametag") && args.length >= 2) {
            BotSession b = botRegistry.get(args[0]);
            if (b != null) {
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < args.length; i++) sb.append(args[i]).append(" ");
                b.setNametag(sb.toString().trim());
                if (b.getEntity() != null) b.getEntity().setCustomName(b.getFormattedNametag(getConfig()));
            }
            return true;
        }
        if (cmd.equals("botgradient") && args.length >= 2) {
            BotSession b = botRegistry.get(args[0]);
            if (b != null) {
                b.setGradientColor(args[1]);
                if (b.getEntity() != null) b.getEntity().setCustomName(b.getFormattedNametag(getConfig()));
            }
            return true;
        }
        return false;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (playerPossessions.containsKey(p.getUniqueId())) {
            BotSession b = botRegistry.get(playerPossessions.get(p.getUniqueId()));
            if (b != null && b.getEntity() != null) b.getEntity().teleport(p.getLocation());
        }
    }

    private void openPage1(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, GUI_P1);
        inv.setItem(11, makeHead("gui-heads.page1-to-page2", ChatColor.GREEN + "➡️ Control Panel", null));
        if (getConfig().getBoolean("features.possess-mode.enabled", true)) {
            inv.setItem(13, makeHead("gui-heads.possess-bot", ChatColor.LIGHT_PURPLE + "🔮 Possess Bot", null));
        }
        inv.setItem(15, makeHead("gui-heads.bot-list", ChatColor.GOLD + "📋 Bot List", null));
        p.openInventory(inv);
    }

    private void openPage2(Player p) {
        Inventory inv = Bukkit.createInventory(null, 36, GUI_P2);
        inv.setItem(10, makeHead("gui-heads.create-bot", ChatColor.AQUA + "➕ Create Bot", null));
        inv.setItem(12, makeHead("gui-heads.change-skin", ChatColor.GREEN + "🎨 Skin Config", null));
        inv.setItem(14, makeHead("gui-heads.view-inventory", ChatColor.DARK_AQUA + "🎒 Bot Inventory", null));
        inv.setItem(16, makeHead("gui-heads.reload-config", ChatColor.RED + "🔄 Reload Config", null));
        inv.setItem(27, makeHead("gui-heads.back-button", ChatColor.RED + "⬅️ Back", null));
        p.openInventory(inv);
    }

    private void openList(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, GUI_LIST);
        int slot = 0;
        for (BotSession b : botRegistry.values()) {
            if (slot >= 45) break;
            inv.setItem(slot++, makeHead("gui-heads.bot-list", b.getFormattedNametag(getConfig()), Arrays.asList(ChatColor.YELLOW + "Klik untuk Spawn")));
        }
        inv.setItem(49, makeHead("gui-heads.back-button", ChatColor.RED + "⬅️ Back", null));
        p.openInventory(inv);
    }

    private void openPossessSelect(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, GUI_POSSESS);
        int slot = 0;
        for (BotSession b : botRegistry.values()) {
            if (slot >= 45) break;
            if (b.isSpawned()) inv.setItem(slot++, makeHead("gui-heads.possess-bot", ChatColor.LIGHT_PURPLE + b.getName(), null));
        }
        inv.setItem(49, makeHead("gui-heads.back-button", ChatColor.RED + "⬅️ Back", null));
        p.openInventory(inv);
    }

    private void openSkinSelect(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, GUI_SKIN);
        int slot = 0;
        for (BotSession b : botRegistry.values()) {
            if (slot >= 45) break;
            inv.setItem(slot++, makeHead("gui-heads.change-skin", ChatColor.GREEN + b.getName(), null));
        }
        inv.setItem(49, makeHead("gui-heads.back-button", ChatColor.RED + "⬅️ Back", null));
        p.openInventory(inv);
    }

    private void openInvSelect(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, GUI_INV);
        int slot = 0;
        for (BotSession b : botRegistry.values()) {
            if (slot >= 45) break;
            inv.setItem(slot++, makeHead("gui-heads.view-inventory", ChatColor.DARK_AQUA + b.getName(), null));
        }
        inv.setItem(49, makeHead("gui-heads.back-button", ChatColor.RED + "⬅️ Back", null));
        p.openInventory(inv);
    }

    private void openBotInv(Player p, BotSession b) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_AQUA + "🎒 " + b.getName());
        inv.setContents(b.getInventoryStorage());
        viewingBotInventory.put(p.getUniqueId(), b.getName());
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        String title = e.getView().getTitle();
        Player p = (Player) e.getWhoClicked();
        if (title.startsWith(ChatColor.DARK_AQUA + "🎒 ")) return;
        if (!title.equals(GUI_P1) && !title.equals(GUI_P2) && !title.equals(GUI_LIST) && !title.equals(GUI_POSSESS) && !title.equals(GUI_SKIN) && !title.equals(GUI_INV)) return;

        e.setCancelled(true);
        int slot = e.getRawSlot();

        if (title.equals(GUI_P1)) {
            if (slot == 11) openPage2(p);
            else if (slot == 13) openPossessSelect(p);
            else if (slot == 15) openList(p);
        } else if (title.equals(GUI_P2)) {
            if (slot == 10) { p.sendMessage("Ketik: /botcreate <nama>"); p.closeInventory(); }
            else if (slot == 12) openSkinSelect(p);
            else if (slot == 14) openInvSelect(p);
            else if (slot == 16) { reloadConfig(); p.closeInventory(); }
            else if (slot == 27) openPage1(p);
        } else if (title.equals(GUI_LIST) && slot < 45 && e.getCurrentItem() != null && e.getCurrentItem().hasItemMeta()) {
            String name = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName());
            for (BotSession b : botRegistry.values()) {
                if (ChatColor.stripColor(b.getFormattedNametag(getConfig())).equalsIgnoreCase(name)) {
                    spawnBot(b.getName(), p.getLocation());
                    p.closeInventory();
                    break;
                }
            }
        } else if (title.equals(GUI_POSSESS) && slot < 45 && e.getCurrentItem() != null && e.getCurrentItem().hasItemMeta()) {
            String name = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName());
            BotSession b = botRegistry.get(name);
            if (b != null && b.isSpawned()) {
                b.setPossessed(true);
                playerPossessions.put(p.getUniqueId(), b.getName());
                p.teleport(b.getLocation());
                p.closeInventory();
            }
        } else if (title.equals(GUI_SKIN) && slot < 45 && e.getCurrentItem() != null && e.getCurrentItem().hasItemMeta()) {
            String name = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName());
            p.sendMessage("Ketik: /botskin url " + name + " <url> wide/slim");
            p.closeInventory();
        } else if (title.equals(GUI_INV) && slot < 45 && e.getCurrentItem() != null && e.getCurrentItem().hasItemMeta()) {
            String name = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName());
            BotSession b = botRegistry.get(name);
            if (b != null) openBotInv(p, b);
        } else if (slot == 49) {
            if (title.equals(GUI_LIST) || title.equals(GUI_POSSESS)) openPage1(p);
            else if (title.equals(GUI_SKIN) || title.equals(GUI_INV)) openPage2(p);
        }
    }

    @EventHandler
    public void onClose(org.bukkit.event.inventory.InventoryCloseEvent e) {
        Player p = (Player) e.getPlayer();
        if (viewingBotInventory.containsKey(p.getUniqueId())) {
            String bName = viewingBotInventory.remove(p.getUniqueId());
            BotSession b = botRegistry.get(bName);
            if (b != null) b.setInventoryStorage(e.getInventory().getContents());
        }
    }

    private ItemStack makeHead(String path, String name, List<String> lore) {
        String url = getConfig().getString(path, getConfig().getString("default-skin.url", "https://minesk.in/b99aa1f02594468c95307736628af54a"));
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
           
