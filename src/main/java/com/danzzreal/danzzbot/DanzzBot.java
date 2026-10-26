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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public final class DanzzBot extends JavaPlugin implements CommandExecutor, Listener {

    public enum BotActivity {
        IDLE, PVP_TARGET, MINING, WOODCUTTING, FARMING, FISHING
    }

    public static class BotSession {
        private String name;
        private String nametag;
        private Location location;
        private ArmorStand entity;
        private boolean possessed = false;
        private boolean autoChat = true;
        private BotActivity activity = BotActivity.IDLE;
        private Player targetPlayer = null;

        public BotSession(String name, Location location) {
            this.name = name;
            this.nametag = ChatColor.GOLD + name;
            this.location = location;
        }

        public String getName() { return name; }
        public String getNametag() { return nametag; }
        public void setNametag(String nametag) { this.nametag = nametag; }
        public Location getLocation() { return location; }
        public ArmorStand getEntity() { return entity; }
        public void setEntity(ArmorStand entity) { this.entity = entity; }
        public boolean isPossessed() { return possessed; }
        public void setPossessed(boolean possessed) { this.possessed = possessed; }
        public boolean isAutoChat() { return autoChat; }
        public BotActivity getActivity() { return activity; }
        public void setActivity(BotActivity activity) { this.activity = activity; }
        public Player getTargetPlayer() { return targetPlayer; }
        public void setTargetPlayer(Player targetPlayer) { this.targetPlayer = targetPlayer; }
    }

    private final Map<String, BotSession> activeBots = new HashMap<>();
    private final Map<UUID, String> playerPossessions = new HashMap<>();
    private final String GUI_TITLE = ChatColor.DARK_PURPLE + "✦ DanzzBot | Panel Control ✦";

    @Override
    public void onEnable() {
        // Pendaftaran perintah terpisah
        String[] cmds = {"botgui", "botcreate", "botchat", "botpossess", "botunpossess", "botkill", "nametag"};
        for (String cmd : cmds) {
            if (getCommand(cmd) != null) {
                getCommand(cmd).setExecutor(this);
            }
        }
        getServer().getPluginManager().registerEvents(this, this);
        startBotAITask();
        getLogger().info("DanzzBot v1.3.0 dengan Perintah Terpisah Siap Digunakan!");
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
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Perintah hanya untuk player!");
            return true;
        }

        Player player = (Player) sender;
        String cmd = command.getName().toLowerCase();

        // 1. Perintah /botgui
        if (cmd.equals("botgui")) {
            openBotGui(player);
            return true;
        }

        // 2. Perintah /botcreate <nama>
        if (cmd.equals("botcreate")) {
            if (args.length < 1) {
                player.sendMessage(ChatColor.YELLOW + "Penggunaan: /botcreate <nama_bot>");
                return true;
            }
            String botName = args[0];
            if (activeBots.containsKey(botName)) {
                player.sendMessage(ChatColor.RED + "Bot dengan nama ini sudah ada!");
                return true;
            }
            spawnBot(botName, player.getLocation());
            player.sendMessage(ChatColor.GREEN + "✔ Bot '" + botName + "' berhasil dibuat!");
            return true;
        }

        // 3. Perintah /botchat <nama> <pesan>
        if (cmd.equals("botchat")) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.YELLOW + "Penggunaan: /botchat <nama_bot> <pesan>");
                return true;
            }
            String botName = args[0];
            if (!activeBots.containsKey(botName)) {
                player.sendMessage(ChatColor.RED + "Bot tidak ditemukan!");
                return true;
            }
            StringBuilder msg = new StringBuilder();
            for (int i = 1; i < args.length; i++) msg.append(args[i]).append(" ");
            BotSession bot = activeBots.get(botName);
            Bukkit.broadcastMessage(bot.getNametag() + ChatColor.WHITE + ": " + ChatColor.translateAlternateColorCodes('&', msg.toString().trim()));
            return true;
        }

        // 4. Perintah /botpossess <nama>
        if (cmd.equals("botpossess")) {
            if (args.length < 1) {
                player.sendMessage(ChatColor.YELLOW + "Penggunaan: /botpossess <nama_bot>");
                return true;
            }
            BotSession bot = activeBots.get(args[0]);
            if (bot != null) {
                bot.setPossessed(true);
                playerPossessions.put(player.getUniqueId(), args[0]);
                player.teleport(bot.getLocation());
                player.sendMessage(ChatColor.LIGHT_PURPLE + "🔮 Kamu sekarang merasuki bot " + bot.getName());
            } else {
                player.sendMessage(ChatColor.RED + "Bot tidak ditemukan!");
            }
            return true;
        }

        // 5. Perintah /botunpossess
        if (cmd.equals("botunpossess")) {
            String botName = playerPossessions.remove(player.getUniqueId());
            if (botName != null && activeBots.containsKey(botName)) {
                activeBots.get(botName).setPossessed(false);
            }
            player.sendMessage(ChatColor.GREEN + "🔮 Berhasil keluar dari mode rasuk.");
            return true;
        }

        // 6. Perintah /botkill <nama>
        if (cmd.equals("botkill")) {
            if (args.length < 1) {
                player.sendMessage(ChatColor.YELLOW + "Penggunaan: /botkill <nama_bot>");
                return true;
            }
            BotSession bot = activeBots.remove(args[0]);
            if (bot != null && bot.getEntity() != null) {
                bot.getEntity().remove();
                player.sendMessage(ChatColor.RED + " Bot '" + args[0] + "' berhasil dihapus.");
            } else {
                player.sendMessage(ChatColor.RED + "Bot tidak ditemukan!");
            }
            return true;
        }

        // 7. Perintah /nametag <nama> <teks>
        if (cmd.equals("nametag")) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.YELLOW + "Penggunaan: /nametag <nama_bot> <teks_baru>");
                return true;
            }
            BotSession bot = activeBots.get(args[0]);
            if (bot != null) {
                StringBuilder newTag = new StringBuilder();
                for (int i = 1; i < args.length; i++) newTag.append(args[i]).append(" ");
                String formatted = ChatColor.translateAlternateColorCodes('&', newTag.toString().trim());
                bot.setNametag(formatted);
                if (bot.getEntity() != null) bot.getEntity().setCustomName(formatted);
                player.sendMessage(ChatColor.GREEN + " Nametag bot berhasil diperbarui!");
            } else {
                player.sendMessage(ChatColor.RED + "Bot tidak ditemukan!");
            }
            return true;
        }

        return false;
    }

    private void spawnBot(String name, Location loc) {
        BotSession session = new BotSession(name, loc);
        ArmorStand stand = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        stand.setCustomName(session.getNametag());
        stand.setCustomNameVisible(true);
        stand.setArms(true);
        stand.setBasePlate(true);
        stand.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
        stand.getEquipment().setHelmet(new ItemStack(Material.NETHERITE_HELMET));
        session.setEntity(stand);
        activeBots.put(name, session);
    }

    private void openBotGui(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, GUI_TITLE);
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(player);
        meta.setDisplayName(ChatColor.GREEN + "DanzzBot Controller");
        head.setItemMeta(meta);
        gui.setItem(13, head);
        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals(GUI_TITLE)) event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (playerPossessions.containsKey(player.getUniqueId())) {
            BotSession bot = activeBots.get(playerPossessions.get(player.getUniqueId()));
            if (bot != null && bot.getEntity() != null) {
                bot.getEntity().teleport(player.getLocation());
            }
        }
    }

    private void startBotAITask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (BotSession bot : activeBots.values()) {
                    if (bot.isPossessed() || bot.getEntity() == null) continue;
                    ArmorStand entity = bot.getEntity();
                    Location loc = entity.getLocation();

                    // Cari player terdekat untuk PVP otomatis
                    Player target = null;
                    double minDist = 8.0;
                    for (Entity nearby : loc.getWorld().getNearbyEntities(loc, 8, 8, 8)) {
                        if (nearby instanceof Player && !playerPossessions.containsKey(nearby.getUniqueId())) {
                            double d = loc.distance(nearby.getLocation());
                            if (d < minDist) { minDist = d; target = (Player) nearby; }
                        }
                    }

                    if (target != null) {
                        Vector dir = target.getLocation().toVector().subtract(loc.toVector()).normalize();
                        loc.setDirection(dir);
                        if (minDist > 1.8) {
                            loc.add(dir.multiply(0.35));
                            entity.teleport(loc);
                        } else {
                            target.damage(4.0, entity);
                        }
                    }
                }
            }
        }.runTaskTimer(this, 10L, 10L);
    }
}
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
                      
