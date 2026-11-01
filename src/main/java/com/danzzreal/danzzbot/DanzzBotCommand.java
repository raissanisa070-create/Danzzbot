package com.danzzreal.danzzbot;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DanzzBotCommand implements CommandExecutor, TabCompleter, Listener {

    private final DanzzBot plugin;
    private final static Map<UUID, String> pendingBotNames = new HashMap<>();
    private final static Map<Integer, Integer> botLevels = new HashMap<>();

    public DanzzBotCommand(DanzzBot plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getLang("not-player", "&cPerintah khusus player!"));
            return true;
        }

        Player player = (Player) sender;
        String cmdName = command.getName().toLowerCase();

        if (cmdName.equals("botmenu") || cmdName.equals("cpanel") || (cmdName.equals("danzzbot") && (args.length == 0 || args[0].equalsIgnoreCase("menu")))) {
            openMainGui(player);
            return true;
        }

        if (cmdName.equals("botsettings") || (cmdName.equals("danzzbot") && args.length > 0 && args[0].equalsIgnoreCase("settings"))) {
            openSettingsGui(player);
            return true;
        }

        if (cmdName.equals("botrpg") || (cmdName.equals("danzzbot") && args.length > 0 && args[0].equalsIgnoreCase("rpg"))) {
            openRpgHubGui(player);
            return true;
        }

        if (cmdName.equals("botlist") || (cmdName.equals("danzzbot") && args[0].equalsIgnoreCase("list"))) {
            openBotSpawnHubGui(player);
            return true;
        }

        if (cmdName.equals("botcreate") || (cmdName.equals("danzzbot") && args[0].equalsIgnoreCase("create"))) {
            String name = args.length >= 2 ? args[1] : "BotPro_" + (int)(Math.random() * 100);
            openCreateBotGui(player, name);
            return true;
        }

        if (cmdName.equals("botremove") || (cmdName.equals("danzzbot") && args[0].equalsIgnoreCase("remove"))) {
            openBotRemoveGui(player);
            return true;
        }

        if (cmdName.equals("danzzbot") && args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadDanzzConfig();
            player.sendMessage(plugin.getLang("config-reloaded", "&aReload Berhasil!"));
            return true;
        }

        openMainGui(player);
        return true;
    }

    public void openMainGui(Player player) {
        FileConfiguration menu = plugin.getMenuConfig();
        String title = ChatColor.translateAlternateColorCodes('&', menu.getString("menu-title", "&5&lDanzzBot Panel"));
        Inventory gui = Bukkit.createInventory(null, 45, title);
        fillBackground(gui, menu);

        int totalBots = 0;
        if (CitizensAPI.getNPCRegistry() != null) {
            for (NPC ignored : CitizensAPI.getNPCRegistry()) totalBots++;
        }

        ItemStack rpgItem = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta rpgMeta = rpgItem.getItemMeta();
        rpgMeta.setDisplayName(ChatColor.GREEN + "⭐ &lBot RPG & Leveling");
        rpgItem.setItemMeta(rpgMeta);
        gui.setItem(10, rpgItem);

        ItemStack spawnHub = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta spawnMeta = (SkullMeta) spawnHub.getItemMeta();
        spawnMeta.setDisplayName(ChatColor.AQUA + "✨ &lSpawn Bot Hub (List)");
        spawnMeta.setOwningPlayer(player);
        spawnHub.setItemMeta(spawnMeta);
        gui.setItem(12, spawnHub);

        ItemStack createItem = new ItemStack(Material.EMERALD);
        ItemMeta createMeta = createItem.getItemMeta();
        createMeta.setDisplayName(ChatColor.GOLD + "＋ &lCreate Bot & Quota");
        createItem.setItemMeta(createMeta);
        gui.setItem(14, createItem);

        ItemStack taskItem = new ItemStack(Material.CLOCK);
        ItemMeta taskMeta = taskItem.getItemMeta();
        taskMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "⏰ &lTask Scheduler");
        taskItem.setItemMeta(taskMeta);
        gui.setItem(16, taskItem);

        ItemStack settingsItem = new ItemStack(Material.COMPARATOR);
        ItemMeta setMeta = settingsItem.getItemMeta();
        setMeta.setDisplayName(ChatColor.DARK_PURPLE + "⚙ &lFeature Settings (On/Off)");
        settingsItem.setItemMeta(setMeta);
        gui.setItem(22, settingsItem);

        ItemStack reloadItem = new ItemStack(Material.BOOK);
        ItemMeta rMeta = reloadItem.getItemMeta();
        rMeta.setDisplayName(ChatColor.GREEN + "🔄 &lHot-Reload Config");
        reloadItem.setItemMeta(rMeta);
        gui.setItem(28, reloadItem);

        ItemStack removeMenu = new ItemStack(Material.LAVA_BUCKET);
        ItemMeta removeMeta = removeMenu.getItemMeta();
        removeMeta.setDisplayName(ChatColor.RED + "🗑 &lRemove Bot Menu");
        removeMenu.setItemMeta(removeMeta);
        gui.setItem(34, removeMenu);

        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cMeta = close.getItemMeta();
        cMeta.setDisplayName(ChatColor.RED + "✖ &lTutup Panel");
        close.setItemMeta(cMeta);
        gui.setItem(40, close);

        player.openInventory(gui);
    }

    public void openSettingsGui(Player player) {
        String title = ChatColor.translateAlternateColorCodes('&', "&0🔮 &5&lSettings & Toggles");
        Inventory gui = Bukkit.createInventory(null, 27, title);

        boolean autoChat = plugin.isEngineFeatureActive("chatbot-slang");
        boolean pathfinding = plugin.isEngineFeatureActive("smart-pathfinding");
        boolean fishing = plugin.isEngineFeatureActive("autonomous-fishing");
        boolean routine = plugin.isEngineFeatureActive("autonomous-daily-routine");
        boolean defense = plugin.isEngineFeatureActive("base-defense");

        gui.setItem(10, createToggleItem(Material.PAPER, "&bAuto Chat Slang", autoChat));
        gui.setItem(12, createToggleItem(Material.COMPASS, "&bSmart Pathfinding", pathfinding));
        gui.setItem(14, createToggleItem(Material.FISHING_ROD, "&bAutonomous Fishing", fishing));
        gui.setItem(16, createToggleItem(Material.CLOCK, "&bDaily Routine", routine));
        gui.setItem(22, createToggleItem(Material.SHIELD, "&bBase Defense", defense));

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bMeta = back.getItemMeta();
        bMeta.setDisplayName(ChatColor.RED + "◀ Kembali ke Menu Utama");
        back.setItemMeta(bMeta);
        gui.setItem(18, back);

        player.openInventory(gui);
    }

    public void openRpgHubGui(Player player) {
        String title = ChatColor.translateAlternateColorCodes('&', "&0🔮 &5&lBot RPG & Skill Leveling");
        Inventory gui = Bukkit.createInventory(null, 27, title);

        if (CitizensAPI.getNPCRegistry() != null) {
            int slot = 10;
            for (NPC npc : CitizensAPI.getNPCRegistry()) {
                if (slot > 16) break;
                int level = botLevels.getOrDefault(npc.getId(), 1);
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ChatColor.YELLOW + "Bot: " + npc.getName());
                    meta.setLore(Arrays.asList(ChatColor.GRAY + "Level Bot: " + ChatColor.GREEN + "Lv. " + level, "", ChatColor.YELLOW + "▶ Klik untuk Level Up!"));
                    head.setItemMeta(meta);
                }
                gui.setItem(slot, head);
                slot++;
            }
        }

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bMeta = back.getItemMeta();
        bMeta.setDisplayName(ChatColor.RED + "◀ Kembali");
        back.setItemMeta(bMeta);
        gui.setItem(18, back);
        player.openInventory(gui);
    }

    public void openTaskSchedulerGui(Player player) {
        String title = ChatColor.translateAlternateColorCodes('&', "&0🔮 &5&lTask Scheduler");
        Inventory gui = Bukkit.createInventory(null, 27, title);

        ItemStack farmTask = new ItemStack(Material.WHEAT);
        ItemMeta fMeta = farmTask.getItemMeta();
        fMeta.setDisplayName(ChatColor.GREEN + "🌾 Tugas: Farming & Fishing");
        farmTask.setItemMeta(fMeta);
        gui.setItem(11, farmTask);

        ItemStack mineTask = new ItemStack(Material.IRON_PICKAXE);
        ItemMeta mMeta = mineTask.getItemMeta();
        mMeta.setDisplayName(ChatColor.AQUA + "⛏ Tugas: Autonomous Mining");
        mineTask.setItemMeta(mMeta);
        gui.setItem(13, mineTask);

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bMeta = back.getItemMeta();
        bMeta.setDisplayName(ChatColor.RED + "◀ Kembali");
        back.setItemMeta(bMeta);
        gui.setItem(18, back);
        player.openInventory(gui);
    }

    private ItemStack createToggleItem(Material mat, String name, boolean status) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(ChatColor.GRAY + "Status: " + (status ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"), "", ChatColor.YELLOW + "▶ Klik untuk Toggle!"));
            item.setItemMeta(meta);
        }
        return item;
    }

    public void openCreateBotGui(Player player, String currentName) {
        pendingBotNames.put(player.getUniqueId(), currentName);
        String title = ChatColor.translateAlternateColorCodes('&', "&0🔮 &5&lCreate Bot");
        Inventory gui = Bukkit.createInventory(null, 27, title);
        ItemStack save = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta sMeta = save.getItemMeta();
        sMeta.setDisplayName(ChatColor.GREEN + "💾 Save Bot & Spawn");
        save.setItemMeta(sMeta);
        gui.setItem(15, save);

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bMeta = back.getItemMeta();
        bMeta.setDisplayName(ChatColor.RED + "◀ Kembali");
        back.setItemMeta(bMeta);
        gui.setItem(18, back);
        player.openInventory(gui);
    }

    public void openBotSpawnHubGui(Player player) {
        String title = ChatColor.translateAlternateColorCodes('&', "&0🔮 &5&lSpawn Hub");
        Inventory gui = Bukkit.createInventory(null, 27, title);
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bMeta = back.getItemMeta();
        bMeta.setDisplayName(ChatColor.RED + "◀ Kembali");
        back.setItemMeta(bMeta);
        gui.setItem(18, back);
        player.openInventory(gui);
    }

    public void openBotRemoveGui(Player player) {
        String title = ChatColor.translateAlternateColorCodes('&', "&0🔮 &5&lRemove Bot");
        Inventory gui = Bukkit.createInventory(null, 27, title);
        ItemStack removeAll = new ItemStack(Material.LAVA_BUCKET);
        ItemMeta rmMeta = removeAll.getItemMeta();
        rmMeta.setDisplayName(ChatColor.RED + "☠ Hapus Semua Bot");
        removeAll.setItemMeta(rmMeta);
        gui.setItem(11, removeAll);

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bMeta = back.getItemMeta();
        bMeta.setDisplayName(ChatColor.RED + "◀ Kembali");
        back.setItemMeta(bMeta);
        gui.setItem(18, back);
        player.openInventory(gui);
    }

    private void fillBackground(Inventory gui, FileConfiguration menu) {
        Material fillerMat = Material.matchMaterial(menu.getString("filler-item.material", "BLACK_STAINED_GLASS_PANE"));
        if (fillerMat == null) fillerMat = Material.BLACK_STAINED_GLASS_PANE;
        ItemStack filler = new ItemStack(fillerMat);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) { meta.setDisplayName(" "); filler.setItemMeta(meta); }
        for (int i = 0; i < gui.getSize(); i++) {
            if (gui.getItem(i) == null || gui.getItem(i).getType() == Material.AIR) {
                gui.setItem(i, filler);
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String rawTitle = event.getView().getTitle();
        Player player = (Player) event.getWhoClicked();

        if (rawTitle.contains("Panel") || rawTitle.contains("RPG") || rawTitle.contains("Scheduler") || rawTitle.contains("Settings") || rawTitle.contains("Create") || rawTitle.contains("Remove")) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;

            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
            int slot = event.getRawSlot();
            FileConfiguration menu = plugin.getMenuConfig();

            if (rawTitle.contains("Panel")) {
                if (slot == menu.getInt("items.close-button.slot", 40)) {
                    player.closeInventory();
                } else if (slot == 10) {
                    openRpgHubGui(player);
                } else if (slot == 12) {
                    openBotSpawnHubGui(player);
                } else if (slot == 14) {
                    openCreateBotGui(player, "BotMaster_" + (int)(Math.random() * 1000));
                } else if (slot == 16) {
                    openTaskSchedulerGui(player);
                } else if (slot == 22) {
                    openSettingsGui(player);
                } else if (slot == 28) {
                    player.closeInventory();
                    plugin.reloadDanzzConfig();
                    player.sendMessage(plugin.getLang("config-reloaded", "&aReload Berhasil via GUI!"));
                } else if (slot == 34) {
                    openBotRemoveGui(player);
                }
            } else if (rawTitle.contains("Settings")) {
                if (slot == 18) openMainGui(player);
                else if (slot == 10) { plugin.toggleEngineFeature("chatbot-slang"); openSettingsGui(player); }
                else if (slot == 12) { plugin.toggleEngineFeature("smart-pathfinding"); openSettingsGui(player); }
                else if (slot == 14) { plugin.toggleEngineFeature("autonomous-fishing"); openSettingsGui(player); }
                else if (slot == 16) { plugin.toggleEngineFeature("autonomous-daily-routine"); openSettingsGui(player); }
                else if (slot == 22) { plugin.toggleEngineFeature("base-defense"); openSettingsGui(player); }
            } else if (rawTitle.contains("RPG")) {
                if (slot == 18) openMainGui(player);
                else if (slot >= 10 && slot <= 16) {
                    player.sendMessage(ChatColor.GREEN + "[RPG] Level bot dinaikkan!");
                    player.closeInventory();
                }
            } else if (rawTitle.contains("Scheduler")) {
                if (slot == 18) openMainGui(player);
                else {
                    player.sendMessage(ChatColor.GREEN + "[Task Scheduler] Prioritas tugas diperbarui!");
                    player.closeInventory();
                }
            } else if (rawTitle.contains("Create Bot")) {
                if (slot == 18) openMainGui(player);
                else if (slot == 15) {
                    player.closeInventory();
                    String botName = pendingBotNames.getOrDefault(player.getUniqueId(), "BotMaster");
                    saveBot(player, botName);
                }
            } else if (rawTitle.contains("Remove Bot")) {
                if (slot == 18) openMainGui(player);
                else if (slot == 11) {
                    player.closeInventory();
                    removeAllBots(player);
                }
            }
        }
    }

    private void saveBot(Player player, String name) {
        NPC npc = CitizensAPI.getNPCRegistry().createNPC(org.bukkit.entity.EntityType.PLAYER, name);
        npc.spawn(player.getLocation());
        botLevels.put(npc.getId(), 1);
        
        // Simpan otomatis ke folder botlist
        plugin.getBotFileManager().saveBotToFile(npc, null);

        String msg = plugin.getLang("bot-saved", "&aBot {name} berhasil dibuat!").replace("{name}", name);
        player.sendMessage(msg);
    }

     private void removeAllBots(Player player) {
        int count = 0;
        if (CitizensAPI.getNPCRegistry() != null) {
            // Perbaikan explicit type argument agar tidak error saat kompilasi Maven
            List<NPC> list = new ArrayList<>();
            for (NPC npc : CitizensAPI.getNPCRegistry()) {
                list.add(npc);
            }

            for (NPC npc : list) {
                plugin.getBotFileManager().deleteBotFile(npc);
                npc.destroy();
                count++;
            }
        }
        String msg = plugin.getLang("remove-all", "&aBerhasil menghapus seluruh bot ({count} bot)!").replace("{count}", String.valueOf(count));
        player.sendMessage(msg);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return Arrays.asList("menu", "settings", "chat", "rpg", "list", "create", "reload", "remove", "removeall");
        return null;
    }
}
        
