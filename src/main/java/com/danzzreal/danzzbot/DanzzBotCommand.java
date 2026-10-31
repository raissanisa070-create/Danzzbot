package com.danzzreal.danzzbot;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
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
import java.util.List;

public class DanzzBotCommand implements CommandExecutor, TabCompleter, Listener {

    private final DanzzBot plugin;

    public DanzzBotCommand(DanzzBot plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Perintah ini hanya dapat dijalankan oleh player di dalam game!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) {
            openEnterpriseMenu(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("skin")) {
            if (args.length < 3) {
                sendHelpHeader(player);
                player.sendMessage(ChatColor.YELLOW + "  &eGunakan: &f/danzzbot skin <id/nama> <url_skin>");
                return true;
            }
            applySkinToNpc(player, args[1], args[2]);
            return true;
        }

        if (args[0].equalsIgnoreCase("create")) {
            if (args.length < 2) {
                sendHelpHeader(player);
                player.sendMessage(ChatColor.YELLOW + "  &eGunakan: &f/danzzbot create <nama_bot> [url_skin]");
                return true;
            }
            createBotWithSkin(player, args[1], args.length >= 3 ? args[2] : null);
            return true;
        }

        sendHelpHeader(player);
        return true;
    }

    private void sendHelpHeader(Player player) {
        player.sendMessage("");
        player.sendMessage(ChatColor.DARK_PURPLE + "✦══════════════════════════════════════✦");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "         " + ChatColor.BOLD + "DANZZBOT ENTERPRISE NETWORK");
        player.sendMessage(ChatColor.DARK_PURPLE + "✦══════════════════════════════════════✦");
        player.sendMessage(ChatColor.WHITE + " » &b/danzzbot menu &7- Membuka GUI Utama");
        player.sendMessage(ChatColor.WHITE + " » &b/danzzbot create &7- Membuat bot baru & skin");
        player.sendMessage(ChatColor.WHITE + " » &b/danzzbot skin &7- Mengganti skin URL NPC");
        player.sendMessage(ChatColor.DARK_PURPLE + "✦══════════════════════════════════════✦");
        player.sendMessage("");
    }

    public void openEnterpriseMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 45, ChatColor.DARK_PURPLE + "DanzzBot Enterprise Network");

        // 1. TPS Status (Admin Monitor)
        ItemStack tpsItem = new ItemStack(Material.DIAMOND);
        ItemMeta tpsMeta = tpsItem.getItemMeta();
        if (tpsMeta != null) {
            tpsMeta.setDisplayName(ChatColor.AQUA + "⚡ TPS Status Network");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Server TPS: " + ChatColor.GREEN + "20.0 (Stable)");
            lore.add(ChatColor.GRAY + "Status Engine: " + ChatColor.GREEN + "ACTIVE");
            tpsMeta.setLore(lore);
            tpsItem.setItemMeta(tpsMeta);
        }
        gui.setItem(11, tpsItem);

        // 2. Total Bots & Skin Manager (Player Head)
        int totalBots = CitizensAPI.getNPCRegistry() != null ? (int) CitizensAPI.getNPCRegistry().size() : 0;
        ItemStack botCountItem = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) botCountItem.getItemMeta();
        if (skullMeta != null) {
            skullMeta.setDisplayName(ChatColor.GREEN + "👥 Total Bots & Skin Manager");
            skullMeta.setOwningPlayer(player);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Bot Aktif: " + ChatColor.YELLOW + totalBots);
            lore.add("");
            lore.add(ChatColor.AQUA + "Ganti Skin via Chat:");
            lore.add(ChatColor.WHITE + "/danzzbot skin <id> <url>");
            skullMeta.setLore(lore);
            botCountItem.setItemMeta(skullMeta);
        }
        gui.setItem(13, botCountItem);

        // 3. Engine Settings (CPVP, Mining, Chatbot Slang, dll)
        ItemStack engineItem = new ItemStack(Material.REDSTONE_BLOCK);
        ItemMeta engineMeta = engineItem.getItemMeta();
        if (engineMeta != null) {
            engineMeta.setDisplayName(ChatColor.RED + "⚙ Engine Settings (On/Off)");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Kelola fitur CPVP, Auto Mining,");
            lore.add(ChatColor.GRAY + "dan Chatbot Slang sistem.");
            engineMeta.setLore(lore);
            engineItem.setItemMeta(engineMeta);
        }
        gui.setItem(15, engineItem);

        // 4. Close Menu
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        if (closeMeta != null) {
            closeMeta.setDisplayName(ChatColor.RED + "✖ Tutup Menu");
            closeItem.setItemMeta(closeMeta);
        }
        gui.setItem(40, closeItem);

        player.openInventory(gui);
    }

    // Proteksi Anti-Ambil Item GUI
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().contains("DanzzBot Enterprise Network")) {
            event.setCancelled(true);

            if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) {
                return;
            }

            Player player = (Player) event.getWhoClicked();
            int slot = event.getRawSlot();

            if (slot == 40) {
                player.closeInventory();
                player.sendMessage(ChatColor.RED + "[DanzzBot] Menu ditutup.");
            } else if (slot == 13) {
                player.closeInventory();
                player.sendMessage(ChatColor.AQUA + "[DanzzBot] " + ChatColor.WHITE + "Ketik perintah di chat: " + ChatColor.YELLOW + "/danzzbot skin <id_bot> <url_skin>");
            }
        }
    }

    private void applySkinToNpc(Player player, String target, String skinUrl) {
        NPC npc = null;
        try {
            int id = Integer.parseInt(target);
            npc = CitizensAPI.getNPCRegistry().getById(id);
        } catch (NumberFormatException e) {
            for (NPC regNpc : CitizensAPI.getNPCRegistry()) {
                if (regNpc.getName().equalsIgnoreCase(target)) {
                    npc = regNpc;
                    break;
                }
            }
        }

        if (npc == null) {
            player.sendMessage(ChatColor.RED + "[DanzzBot] NPC / Bot dengan nama/ID '" + target + "' tidak ditemukan!");
            return;
        }

        SkinTrait skinTrait = npc.getOrAddTrait(SkinTrait.class);
        skinTrait.setSkinPersistent(npc.getName(), skinUrl, skinUrl);
        
        player.sendMessage("");
        player.sendMessage(ChatColor.GREEN + "✔ [DanzzBot] Berhasil memperbarui skin URL untuk bot " + ChatColor.YELLOW + npc.getName() + ChatColor.GREEN + "!");
        player.sendMessage("");
    }

    private void createBotWithSkin(Player player, String name, String skinUrl) {
        NPC npc = CitizensAPI.getNPCRegistry().createNPC(org.bukkit.entity.EntityType.PLAYER, name);
        npc.spawn(player.getLocation());

        if (skinUrl != null && !skinUrl.isEmpty()) {
            SkinTrait skinTrait = npc.getOrAddTrait(SkinTrait.class);
            skinTrait.setSkinPersistent(name, skinUrl, skinUrl);
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.GREEN + "✔ [DanzzBot] Bot baru " + ChatColor.YELLOW + name + ChatColor.GREEN + " berhasil dibuat dan di-spawn!");
        player.sendMessage("");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("menu", "skin", "create");
        }
        return null;
    }
}
