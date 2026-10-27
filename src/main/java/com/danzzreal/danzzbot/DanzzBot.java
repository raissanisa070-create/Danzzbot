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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import java.util.*;

public final class DanzzBot extends JavaPlugin implements CommandExecutor, Listener {

    public static class BotSession {
        private String name;
        private String nametag;
        private String gradientColor;
        private String skinOwner;
        private Location location;
        private ArmorStand entity;
        private boolean spawned = false;
        private boolean possessed = false;
        private int animTick = 0;
        private Vector moveVector = new Vector(0, 0, 0);

        public BotSession(String name) {
            this.name = name;
            this.nametag = name;
            this.gradientColor = "&f";
            this.skinOwner = name;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getNametag() { return nametag; }
        public void setNametag(String nametag) { this.nametag = nametag; }
        public String getGradientColor() { return gradientColor; }
        public void setGradientColor(String gradientColor) { this.gradientColor = gradientColor; }
        public String getSkinOwner() { return skinOwner; }
        public void setSkinOwner(String skinOwner) { this.skinOwner = skinOwner; }
        public Location getLocation() { return location; }
        public void setLocation(Location location) { this.location = location; }
        public ArmorStand getEntity() { return entity; }
        public void setEntity(ArmorStand entity) { this.entity = entity; }
        public boolean isSpawned() { return spawned; }
        public void setSpawned(boolean spawned) { this.spawned = spawned; }
        public boolean isPossessed() { return possessed; }
        public void setPossessed(boolean possessed) { this.possessed = possessed; }
        public int getAnimTick() { return animTick; }
        public void setAnimTick(int animTick) { this.animTick = animTick; }
        public Vector getMoveVector() { return moveVector; }
        public void setMoveVector(Vector moveVector) { this.moveVector = moveVector; }

        public String getFormattedNametag() {
            return ChatColor.translateAlternateColorCodes('&', gradientColor + nametag);
        }
    }

    private final Map<String, BotSession> botRegistry = new HashMap<>();
    private final Map<UUID, String> playerPossessions = new HashMap<>();

    // GUI Titles
    private final String GUI_PAGE_1 = ChatColor.DARK_PURPLE + "✦ DanzzBot | Main Menu (Page 1) ✦";
    private final String GUI_PAGE_2 = ChatColor.GOLD + "✦ DanzzBot | Create & Manage (Page 2) ✦";
    private final String GUI_PAGE_LIST = ChatColor.BLUE + "✦ DanzzBot | Registered Bots List ✦";

    @Override
    public void onEnable() {
        // Membuat folder data plugin otomatis di server/plugins/DanzzBot/
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        String[] cmds = {"botgui", "botcreate", "botspawn", "botrename", "botskin", "nametag", "botgradient"};
        for (String cmd : cmds) {
            if (getCommand(cmd) != null) getCommand(cmd).setExecutor(this);
        }
        getServer().getPluginManager().registerEvents(this, this);
        startHumanoidAnimationTask();
        getLogger().info("DanzzBot v1.3.0 Full GUI & Folder Initialized Successfully!");
    }

    @Override
    public void onDisable() {
        for (BotSession bot : botRegistry.values()) {
            if (bot.getEntity() != null) bot.getEntity().remove();
        }
        botRegistry.clear();
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

        if (cmd.equals("botcreate")) {
            if (args.length < 1) {
                player.sendMessage(ChatColor.YELLOW + "Penggunaan: /botcreate <nama_bot>");
                return true;
            }
            String name = args[0];
            if (botRegistry.containsKey(name)) {
                player.sendMessage(ChatColor.RED + "Bot dengan nama ini sudah terdaftar di list!");
                return true;
            }
            botRegistry.put(name, new BotSession(name));
            player.sendMessage(ChatColor.GREEN + "✔ Bot '" + name + "' berhasil ditambahkan ke daftar list!");
            return true;
        }

        if (cmd.equals("botspawn")) {
            if (args.length < 1) return true;
            spawnBotFromList(args[0], player.getLocation());
            player.sendMessage(ChatColor.GREEN + "🚀 Bot '" + args[0] + "' berhasil di-spawn!");
            return true;
        }

        if (cmd.equals("botrename")) {
            if (args.length < 2) return true;
            BotSession bot = botRegistry.get(args[0]);
            if (bot != null) {
                botRegistry.remove(args[0]);
                bot.setName(args[1]);
                bot.setNametag(args[1]);
                botRegistry.put(args[1], bot);
                if (bot.getEntity() != null) bot.getEntity().setCustomName(bot.getFormattedNametag());
                player.sendMessage(ChatColor.GREEN + "✨ Nama bot diubah menjadi: " + args[1]);
            }
            return true;
        }

        if (cmd.equals("botskin")) {
            if (args.length < 2) return true;
            BotSession bot = botRegistry.get(args[0]);
            if (bot != null) {
                bot.setSkinOwner(args[1]);
                player.sendMessage(ChatColor.GREEN + "🎨 Skin bot " + args[0] + " diubah ke: " + args[1]);
            }
            return true;
        }

        if (cmd.equals("nametag")) {
            if (args.length < 2) return true;
            BotSession bot = botRegistry.get(args[0]);
            if (bot != null) {
                StringBuilder tag = new StringBuilder();
                for (int i = 1; i < args.length; i++) tag.append(args[i]).append(" ");
                bot.setNametag(tag.toString().trim());
                if (bot.getEntity() != null) bot.getEntity().setCustomName(bot.getFormattedNametag());
                player.sendMessage(ChatColor.GREEN + "🏷️ Nametag diperbarui!");
            }
            return true;
        }

        if (cmd.equals("botgradient")) {
            if (args.length < 2) return true;
            BotSession bot = botRegistry.get(args[0]);
            if (bot != null) {
                bot.setGradientColor(args[1]);
                if (bot.getEntity() != null) bot.getEntity().setCustomName(bot.getFormattedNametag());
                player.sendMessage(ChatColor.GREEN + "🌈 Warna Gradient nametag diperbarui!");
            }
            return true;
        }

        return false;
    }

    // ==========================================
    // GUI MULTI-PAGE SYSTEM
    // ==========================================

    // Page 1: Main Menu (Tombol List & Page 2 berada di sini)
    private void openPage1(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, GUI_PAGE_1);
        
        // Button Mengarah ke Page 2 (Create & Manage)
        gui.setItem(11, createCustomHead("Steve", ChatColor.GREEN + "➡️ Go to Page 2 (Create & Manage)", 
                Arrays.asList(ChatColor.GRAY + "Atur pembuatan bot, skin,", ChatColor.GRAY + "nametag, dan gradient warna.")));
        
        // Button List Bot Langsung di Page 1 (Mudah diakses)
        gui.setItem(15, createCustomHead("MHF_TNTServer", ChatColor.GOLD + "📋 Open Bot List Page", 
                Arrays.asList(ChatColor.GRAY + "Melihat seluruh bot yang terdaftar", ChatColor.GRAY + "dan langsung spawn ke game.")));
        
        player.openInventory(gui);
    }

    // Page 2: Create & Manage Menu
    private void openPage2(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, GUI_PAGE_2);

        // Button Create Bot (Registrasi ke list)
        gui.setItem(11, createCustomHead("MHF_Chest", ChatColor.AQUA + "➕ Create Bot (Register to List)", 
                Arrays.asList(ChatColor.GRAY + "Menambah bot baru ke dalam list registry.")));

        // Button Nametag
        gui.setItem(12, createCustomHead("MHF_Question", ChatColor.YELLOW + "🏷️ Set Nametag", 
                Arrays.asList(ChatColor.GRAY + "Atur teks nametag bot.")));

        // Button Gradient (Pisah Warna)
        gui.setItem(13, createCustomHead("MHF_Villager", ChatColor.LIGHT_PURPLE + "🌈 Set Gradient Color", 
                Arrays.asList(ChatColor.GRAY + "Atur warna/gradient khusus nametag.")));

        // Button Skin
        gui.setItem(14, createCustomHead("Alex", ChatColor.GREEN + "🎨 Set Skin URL / Owner", 
                Arrays.asList(ChatColor.GRAY + "Ganti skin bot sesuai Head/URL.")));

        // Button Kembali ke Page 1
        gui.setItem(18, createCustomHead("MHF_ArrowLeft", ChatColor.RED + "⬅️ Back to Page 1", null));

        player.openInventory(gui);
    }

    // Page List: Halaman Berisi Seluruh Bot
    private void openListPage(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, GUI_PAGE_LIST);

        int slot = 0;
        for (BotSession bot : botRegistry.values()) {
            if (slot >= 45) break;
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Status: " + (bot.isSpawned() ? ChatColor.GREEN + "Spawned" : ChatColor.RED + "In List (Unspawned)"));
            lore.add(ChatColor.YELLOW + "Klik untuk Spawn / Teleport Bot ini!");
            
            gui.setItem(slot, createCustomHead(bot.getSkinOwner(), bot.getFormattedNametag(), lore));
            slot++;
        }

        // Button Kembali ke Page 1
        gui.setItem(49, createCustomHead("MHF_ArrowLeft", ChatColor.RED + "⬅️ Back to Page 1", null));

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!title.equals(GUI_PAGE_1) && !title.equals(GUI_PAGE_2) && !title.equals(GUI_PAGE_LIST)) return;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        // Navigasi Page 1
        if (title.equals(GUI_PAGE_1)) {
            if (slot == 11) openPage2(player);
            else if (slot == 15) openListPage(player);
        }
        // Navigasi Page 2
        else if (title.equals(GUI_PAGE_2)) {
            if (slot == 11) {
                player.sendMessage(ChatColor.YELLOW + "Gunakan: /botcreate <nama_bot> untuk mendaftar bot baru ke list!");
                player.closeInventory();
            } else if (slot == 12) {
                player.sendMessage(ChatColor.YELLOW + "Gunakan: /nametag <nama_bot> <teks> untuk ubah nametag!");
                player.closeInventory();
            } else if (slot == 13) {
                player.sendMessage(ChatColor.YELLOW + "Gunakan: /botgradient <nama_bot> <kode_warna> untuk atur gradient!");
                player.closeInventory();
            } else if (slot == 14) {
                player.sendMessage(ChatColor.YELLOW + "Gunakan: /botskin <nama_bot> <nama_skin> untuk ganti skin!");
                player.closeInventory();
            } else if (slot == 18) {
                openPage1(player);
            }
        }
        // Navigasi Page List Bot
        else if (title.equals(GUI_PAGE_LIST)) {
            if (slot == 49) {
                openPage1(player);
            } else if (event.getCurrentItem() != null && event.getCurrentItem().hasItemMeta()) {
                String botDisplayName = ChatColor.stripColor(event.getCurrentItem().getItemMeta().getDisplayName());
                for (BotSession bot : botRegistry.values()) {
                    if (ChatColor.stripColor(bot.getFormattedNametag()).equalsIgnoreCase(botDisplayName)) {
                        spawnBotFromList(bot.getName(), player.getLocation());
                        player.sendMessage(ChatColor.GREEN + "🚀 Bot '" + bot.getName() + "' di-spawn via List GUI!");
                        player.closeInventory();
                        break;
                    }
                }
            }
        }
    }

    private ItemStack createCustomHead(String owner, String displayName, List<String> lore) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(owner));
            meta.setDisplayName(displayName);
            if (lore != null) meta.setLore(lore);
            head.setItemMeta(meta);
        }
        return head;
    }

    private void spawnBotFromList(String name, Location loc) {
        BotSession session = botRegistry.get(name);
        if (session == null) return;

        if (session.getEntity() != null) session.getEntity().remove();

        ArmorStand stand = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        stand.setCustomName(session.getFormattedNametag());
        stand.setCustomNameVisible(true);
        stand.setArms(true);
        stand.setBasePlate(false);
        stand.setGravity(true);
        
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(session.getSkinOwner()));
            head.setItemMeta(meta);
        }
        stand.getEquipment().setHelmet(head);
        stand.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));

        session.setEntity(stand);
        session.setLocation(loc);
        session.setSpawned(true);
    }

    private void startHumanoidAnimationTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (BotSession bot : botRegistry.values()) {
                    if (!bot.isSpawned() || bot.getEntity() == null) continue;
                    ArmorStand entity = bot.getEntity();
                    Location loc = entity.getLocation();

                    Player target = null;
                    double minDist = 10.0;
                    for (Entity nearby : loc.getWorld().getNearbyEntities(loc, 10, 10, 10)) {
                        if (nearby instanceof Player) {
                            double d = loc.distance(nearby.getLocation());
                            if (d < minDist) { minDist = d; target = (Player) nearby; }
                        }
                    }

                    int tick = bot.getAnimTick() + 1;
                    bot.setAnimTick(tick);

                    double armSwing = Math.sin(tick * 0.4) * 0.6;
                    double legSwing = Math.cos(tick * 0.4) * 0.6;

                    entity.setLeftArmPose(new EulerAngle(armSwing, 0, 0));
                    entity.setRightArmPose(new EulerAngle(-armSwing, 0, 0));
                    entity.setLeftLegPose(new EulerAngle(-legSwing, 0, 0));
                    entity.setRightLegPose(new EulerAngle(legSwing, 0, 0));

                    if (target != null) {
                        Vector dir = target.getLocation().toVector().subtract(loc.toVector()).normalize();
                        loc.setDirection(dir);

                        if (minDist > 2.0) {
                            loc.add(dir.multiply(0.35));
                            entity.teleport(loc);
                        } else {
                            entity.setRightArmPose(new EulerAngle(-1.2, 0, 0));
                            target.damage(4.5, entity);
                            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.5f, 1.0f);
                        }
                    } else {
                        if (tick % 20 == 0) {
                            double rx = (Math.random() - 0.5) * 2.0;
                            double rz = (Math.random() - 0.5) * 2.0;
                            bot.setMoveVector(new Vector(rx * 0.15, 0, rz * 0.15));
                        }
                        Vector move = bot.getMoveVector();
                        if (move.lengthSquared() > 0) {
                            loc.add(move);
                            entity.teleport(loc);
                        }
                    }
                }
            }
        }.runTaskTimer(this, 2L, 2L);
    }
                                             }
            
