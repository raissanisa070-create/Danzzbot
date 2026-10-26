package com.danzzreal.danzzbot;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
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

        getLogger().info("=======================================");
        getLogger().info(" DanzzBot v1.3.0 - Created by DanzzReal");
        getLogger().info(" Full Autonomous AI & Aggressive PVP Ready!");
        getLogger().info("=======================================");
    }

    @Override
    public void onDisable() {
        for (BotSession bot : activeBots.values()) {
            if (bot.getEntity() != null) {
                bot.getEntity().remove();
            }
        }
        activeBots.clear();
        playerPossessions.clear();
        getLogger().info("DanzzBot dimatikan.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Perintah ini hanya dapat dijalankan oleh player.");
            return true;
        }

        Player player = (Player) sender;

        if (command.getName().equalsIgnoreCase("nametag")) {
            if (args.length >= 3 && args[0].equalsIgnoreCase("bot")) {
                String botName = args[1];
                if (!activeBots.containsKey(botName)) {
                    player.sendMessage(ChatColor.RED + "Bot tidak ditemukan!");
                    return true;
                }
                StringBuilder tagBuilder = new StringBuilder();
                for (int i = 2; i < args.length; i++) {
                    tagBuilder.append(args[i]).append(" ");
                }
                String formattedTag = applyGradientAndColors(tagBuilder.toString().trim());
                BotSession bot = activeBots.get(botName);
                bot.setNametag(formattedTag);
                if (bot.getEntity() != null) {
                    bot.getEntity().setCustomName(formattedTag);
                }
                player.sendMessage(ChatColor.GREEN + "[DanzzBot] Nametag berhasil diubah!");
                return true;
            }
            player.sendMessage(ChatColor.YELLOW + "Penggunaan: /nametag bot <nama_bot> <teks>");
            return true;
        }

        if (command.getName().equalsIgnoreCase("bot")) {
            if (args.length == 0 || args[0].equalsIgnoreCase("gui")) {
                openModernGui(player);
                return true;
            }

            String sub = args[0].toLowerCase();

            if (sub.equals("create")) {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Penggunaan: /bot create <nama_bot>");
                    return true;
                }
                String botName = args[1];
                if (activeBots.containsKey(botName)) {
                    player.sendMessage(ChatColor.RED + "Bot sudah ada!");
                    return true;
                }
                spawnBotEntity(botName, player.getLocation());
                player.sendMessage(ChatColor.GREEN + "✦ [DanzzBot] Bot '" + botName + "' berhasil dibuat!");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                return true;
            }

            if (sub.equals("chat") || sub.equals("say")) {
                if (args.length < 3) {
                    player.sendMessage(ChatColor.YELLOW + "Penggunaan: /bot chat <nama_bot> <pesan>");
                    return true;
                }
                String botName = args[1];
                if (!activeBots.containsKey(botName)) return true;
                StringBuilder chatMsg = new StringBuilder();
                for (int i = 2; i < args.length; i++) {
                    chatMsg.append(args[i]).append(" ");
                }
                BotSession bot = activeBots.get(botName);
                Bukkit.broadcastMessage(bot.getNametag() + ChatColor.WHITE + ": " + applyGradientAndColors(chatMsg.toString().trim()));
                return true;
            }

            if (sub.equals("autochat") || sub.equals("ac")) {
                if (args.length < 3) return true;
                BotSession bot = activeBots.get(args[1]);
                if (bot != null) {
                    bot.setAutoChat(args[2].equalsIgnoreCase("on") || args[2].equalsIgnoreCase("true"));
                    player.sendMessage(ChatColor.GREEN + "[DanzzBot] Auto-Chat diubah.");
                }
                return true;
            }

            if (sub.equals("kill") || sub.equals("attack")) {
                if (args.length < 3) return true;
                BotSession bot = activeBots.get(args[1]);
                Player target = Bukkit.getPlayer(args[2]);
                if (bot != null && target != null) {
                    bot.setTargetPlayer(target);
                    bot.setActivity(BotActivity.PVP_TARGET);
                    player.sendMessage(ChatColor.RED + "⚔️ Bot menyerang " + target.getName());
                }
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
                    player.sendMessage(ChatColor.LIGHT_PURPLE + "🔮 Merasuki bot!");
                }
                return true;
            }

            if (sub.equals("unpossess")) {
                String botName = playerPossessions.remove(player.getUniqueId());
                if (botName != null && activeBots.containsKey(botName)) {
                    activeBots.get(botName).setPossessed(false);
                }
                player.sendMessage(ChatColor.GREEN + "🔮 Keluar dari mode rasuk.");
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
        stand.setGravity(true);
        
        stand.getEquipment().setItemInMainHand(new ItemStack(Material.MACE));
        stand.getEquipment().setHelmet(new ItemStack(Material.NETHERITE_HELMET));
        stand.getEquipment().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));

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
        if (event.getView().getTitle().equals(GUI_TITLE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onServerListPing(ServerListPingEvent event) {
        if (!activeBots.isEmpty()) {
            event.setMaxPlayers(event.getMaxPlayers());
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (activeBots.isEmpty()) return;
        new BukkitRunnable() {
            @Override
            public void run() {
                if (Math.random() < 0.35 && !activeBots.isEmpty()) {
                    List<BotSession> bots = new ArrayList<>(activeBots.values());
                    BotSession randomBot = bots.get((int) (Math.random() * bots.size()));
                    if (randomBot.isAutoChat() && !randomBot.isPossessed()) {
                        String[] responses = {"otw mining!", "lagi potong kayu", "danzzreal plugin mantap!", "siapa pvp?"};
                        String reply = responses[(int) (Math.random() * responses.length)];
                        Bukkit.broadcastMessage(randomBot.getNametag() + ChatColor.WHITE + ": " + reply);
                    }
                }
            }
        }.runTaskLater(this, 40L);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (playerPossessions.containsKey(player.getUniqueId())) {
            String botName = playerPossessions.get(player.getUniqueId());
            BotSession bot = activeBots.get(botName);
            if (bot != null && bot.getEntity() != null) {
                bot.getEntity().teleport(player.getLocation());
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof ArmorStand) {
            ArmorStand stand = (ArmorStand) event.getEntity();
            for (BotSession bot : activeBots.values()) {
                if (bot.getEntity() != null && bot.getEntity().equals(stand)) {
                    if (event.getDamager() instanceof Player) {
                        Player attacker = (Player) event.getDamager();
                        attacker.playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f);
                    }
                }
            }
        }
    }

    private void startBotFullyAutonomousAITask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (BotSession bot : activeBots.values()) {
                    if (bot.isPossessed() || bot.getEntity() == null) continue;

                    ArmorStand entity = bot.getEntity();
                    Location loc = entity.getLocation();

                    Player nearestTarget = null;
                    double nearestDist = 10.0;
                    for (Entity nearby : loc.getWorld().getNearbyEntities(loc, 10, 10, 10)) {
                        if (nearby instanceof Player) {
                            Player pTarget = (Player) nearby;
                            if (playerPossessions.containsKey(pTarget.getUniqueId())) continue;
                            double dist = loc.distance(pTarget.getLocation());
                            if (dist < nearestDist) {
                                nearestDist = dist;
                                nearestTarget = pTarget;
                            }
                        }
                    }

                    if (bot.getTargetPlayer() != null && bot.getTargetPlayer().isOnline()) {
                        nearestTarget = bot.getTargetPlayer();
                    }

                    if (nearestTarget != null) {
                        bot.setActivity(BotActivity.PVP_TARGET);
                        Location targetLoc = nearestTarget.getLocation();
                        Vector dir = targetLoc.toVector().subtract(loc.toVector()).normalize();
                        loc.setDirection(dir);
                        
                        if (nearestDist > 2.0) {
                            loc.add(dir.multiply(0.45));
                            entity.teleport(loc);
                        } else {
                            nearestTarget.damage(6.5, entity);
                            nearestTarget.getWorld().playSound(targetLoc, Sound.ITEM_MACE_SMASH_GROUND, 1.0f, 1.0f);
                        }
                        continue;
                    } else {
                        if (bot.getActivity() == BotActivity.PVP_TARGET) {
                            bot.setTargetPlayer(null);
                            bot.setActivity(BotActivity.IDLE);
                        }
                    }

                    int timer = bot.getActivityTimer() + 1;
                    bot.setActivityTimer(timer);
                    if (timer >= 40) { 
                        bot.setActivityTimer(0);
                        BotActivity[] acts = {BotActivity.IDLE, BotActivity.MINING, BotActivity.WOODCUTTING, BotActivity.FARMING, BotActivity.FISHING};
                        BotActivity nextAct = acts[(int) (Math.random() * acts.length)];
                        bot.setActivity(nextAct);

                        if (bot.getEntity() != null) {
                            switch (nextAct) {
                                case MINING:
                                    bot.getEntity().getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_PICKAXE));
                                    break;
                                case WOODCUTTING:
                                    bot.getEntity().getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_AXE));
                                    break;
                                case FARMING:
                                    bot.getEntity().getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_HOE));
                                    break;
                                case FISHING:
                                    bot.getEntity().getEquipment().setItemInMainHand(new ItemStack(Material.FISHING_ROD));
                                    break;
                                default:
                                    bot.getEntity().getEquipment().setItemInMainHand(new ItemStack(Material.MACE));
                                    break;
                            }
                        }
                    }

                    switch (bot.getActivity()) {
                        case MINING:
                            loc.getWorld().playSound(loc, Sound.BLOCK_STONE_BREAK, 0.4f, 1.0f);
                            loc.getWorld().spawnParticle(Particle.BLOCK, loc.clone().add(0, -0.5, 0.5), 4, Material.STONE.createBlockData());
                            break;
                        case WOODCUTTING:
                            loc.getWorld().playSound(loc, Sound.BLOCK_WOOD_BREAK, 0.4f, 1.0f);
                            break;
                        case FARMING:
                            loc.getWorld().playSound(loc, Sound.ITEM_CROP_PLANT, 0.4f, 1.0f);
                            break;
                        case FISHING:
                            loc.getWorld().playSound(loc, Sound.ENTITY_FISHING_BOBBER_SPLASH, 0.4f, 1.0f);
                            break;
                        default:
                            break;
                    }
                }
            }
        }.runTaskTimer(this, 10L, 10L);
    }

    private String applyGradientAndColors(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
Bots.values()) {
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
          
