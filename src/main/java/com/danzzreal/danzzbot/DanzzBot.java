package com.danzzreal.danzzbot;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DanzzBot extends JavaPlugin implements CommandExecutor, Listener {

    public enum BotActivity {
        IDLE("Wandering / Idle"),
        PVP_TARGET("Targeting Player (Aggressive)"),
        MINING("Mining Ores"),
        WOODCUTTING("Chop Trees"),
        FARMING("Farming Crops"),
        FISHING("Fishing");

        private final String displayName;
        BotActivity(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }

    public static class BotSession {
        private String name;
        private String nametag;
        private Location location;
        private UUID uuid;
        private ArmorStand entity;
        private String skinUrl;
        private String modelType;
        private boolean possessed = false;
        private UUID possessedBy = null;
        private boolean autoChat = true;
        private BotActivity activity = BotActivity.IDLE;
        private Player targetPlayer = null;
        private int activityTimer = 0;

        public BotSession(String name, Location location) {
            this.name = name;
            this.nametag = ChatColor.GOLD + name;
            this.location = location;
            this.uuid = UUID.randomUUID();
            this.skinUrl = "";
            this.modelType = "wide";
        }

        public String getName() { return name; }
        public String getNametag() { return nametag; }
        public void setNametag(String nametag) { this.nametag = nametag; }
        public Location getLocation() { return location; }
        public void setLocation(Location location) { this.location = location; }
        public ArmorStand getEntity() { return entity; }
        public void setEntity(ArmorStand entity) { this.entity = entity; }
        public boolean isPossessed() { return possessed; }
        public void setPossessed(boolean possessed) { this.possessed = possessed; }
        public UUID getPossessedBy() { return possessedBy; }
        public void setPossessedBy(UUID possessedBy) { this.possessedBy = possessedBy; }
        public boolean isAutoChat() { return autoChat; }
        public void setAutoChat(boolean autoChat) { this.autoChat = autoChat; }
        public String getSkinUrl() { return skinUrl; }
        public void setSkinUrl(String skinUrl) { this.skinUrl = skinUrl; }
        public String getModelType() { return modelType; }
        public void setModelType(String modelType) { this.modelType = modelType; }
        public BotActivity getActivity() { return activity; }
        public void setActivity(BotActivity activity) { this.activity = activity; }
        public Player getTargetPlayer() { return targetPlayer; }
        public void setTargetPlayer(Player targetPlayer) { this.targetPlayer = targetPlayer; }
        public int getActivityTimer() { return activityTimer; }
        public void setActivityTimer(int activityTimer) { this.activityTimer = activityTimer; }
    }

    private final Map<String, BotSession> activeBots = new HashMap<>();
    private final Map<UUID, String> playerPossessions = new HashMap<>();
    private final String GUI_TITLE = ChatColor.DARK_PURPLE + "✦ DanzzBot | Control Panel ✦";

    @Override
    public void onEnable() {
        getCommand("bot").setExecutor(this);
        getCommand("nametag").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
        startBotFullyAutonomousAITask();
        getLogger().info("DanzzBot v1.3.0 by DanzzReal Ready!");
    }

    @Override
    public void onDisable() {
        for (BotSession bot : activeBots.values()) {
            if (bot.getEntity() != null) bot.getEntity().remove();
        }
        activeBots.clear();
        playerPossessions.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;

        if (command.getName().equalsIgnoreCase("nametag")) {
            if (args.length >= 3 && args[0].equalsIgnoreCase("bot")) {
                String botName = args[1];
                if (!activeBots.containsKey(botName)) return true;
                StringBuilder tagBuilder = new StringBuilder();
                for (int i = 2; i < args.length; i++) tagBuilder.append(args[i]).append(" ");
                String formattedTag = applyGradientAndColors(tagBuilder.toString().trim());
                BotSession bot = activeBots.get(botName);
                bot.setNametag(formattedTag);
                if (bot.getEntity() != null) bot.getEntity().setCustomName(formattedTag);
                player.sendMessage(ChatColor.GREEN + "[DanzzBot] Nametag diubah!");
                return true;
            }
        }

        if (command.getName().equalsIgnoreCase("bot")) {
            if (args.length == 0 || args[0].equalsIgnoreCase("gui")) {
                openModernGui(player);
                return true;
            }
            String sub = args[0].toLowerCase();
            if (sub.equals("create")) {
                if (args.length < 2) return true;
                String botName = args[1];
                if (activeBots.containsKey(botName)) return true;
                spawnBotEntity(botName, player.getLocation());
                player.sendMessage(ChatColor.GREEN + "✦ Bot '" + botName + "' berhasil dibuat oleh DanzzReal!");
                return true;
            }
            if (sub.equals("chat") || sub.equals("say")) {
                if (args.length < 3) return true;
                String botName = args[1];
                if (!activeBots.containsKey(botName)) return true;
                StringBuilder chatMsg = new StringBuilder();
                for (int i = 2; i < args.length; i++) chatMsg.append(args[i]).append(" ");
                BotSession bot = activeBots.get(botName);
                Bukkit.broadcastMessage(bot.getNametag() + ChatColor.WHITE + ": " + applyGradientAndColors(chatMsg.toString().trim()));
                return true;
            }
            if (sub.equals("autochat") || sub.equals("ac")) {
                if (args.length < 3) return true;
                BotSession bot = activeBots.get(args[1]);
                if (bot != null) bot.setAutoChat(args[2].equalsIgnoreCase("on"));
                return true;
            }
            if (sub.equals("control") || sub.equals("possess")) {
                if (args.length < 2) return true;
                BotSession bot = activeBots.get(args[1]);
                if (bot != null) {
                    bot.setPossessed(true);
                    bot.setPossessedBy(player.getUniqueId());
                    playerPossessions.put(player.getUniqueId(), args[1]);
                    player.teleport(bot.getLocation());
                }
                return true;
            }
            if (sub.equals("unpossess")) {
                String botName = playerPossessions.remove(player.getUniqueId());
                if (botName != null && activeBots.containsKey(botName)) {
                    activeBots.get(botName).setPossessed(false);
                }
                return true;
            }
        }
        return false;
    }

    private void spawnBotEntity(String name, Location loc) {
        BotSession session = new BotSession(name, loc);
        ArmorStand stand = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        stand.setCustomName(session.getNametag());
        stand.setCustomNameVisible(true);
        stand.setArms(true);
        stand.setBasePlate(true);
        stand.getEquipment().setItemInMainHand(new ItemStack(Material.MACE));
        stand.getEquipment().setHelmet(new ItemStack(Material.NETHERITE_HELMET));
        session.setEntity(stand);
        activeBots.put(name, session);
    }

    private void openModernGui(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, GUI_TITLE);
        ItemStack userHead = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta headMeta = (SkullMeta) userHead.getItemMeta();
        headMeta.setOwningPlayer(player);
        headMeta.setDisplayName(ChatColor.AQUA + "Player: " + player.getName());
        userHead.setItemMeta(headMeta);
        gui.setItem(4, userHead);
        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals(GUI_TITLE)) event.setCancelled(true);
    }

    @EventHandler
    public void onServerListPing(ServerListPingEvent event) {
        if (!activeBots.isEmpty()) event.setMaxPlayers(event.getMaxPlayers());
    }

    private void startBotFullyAutonomousAITask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (BotSession bot : activeBots.values()) {
                    if (bot.isPossessed() || bot.getEntity() == null) continue;
                    Location loc = bot.getEntity().getLocation();
                    Player nearestTarget = null;
                    double nearestDist = 10.0;
                    for (Entity nearby : loc.getWorld().getNearbyEntities(loc, 10, 10, 10)) {
                        if (nearby instanceof Player) {
                            Player pTarget = (Player) nearby;
                            double dist = loc.distance(pTarget.getLocation());
                            if (dist < nearestDist) { nearestDist = dist; nearestTarget = pTarget; }
                        }
                    }
                    if (nearestTarget != null) {
                        bot.setActivity(BotActivity.PVP_TARGET);
                        if (nearestDist <= 2.0) {
                            nearestTarget.damage(6.5, bot.getEntity());
                        }
                    }
                }
            }
        }.runTaskTimer(this, 10L, 10L);
    }

    private String applyGradientAndColors(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
      }
          
