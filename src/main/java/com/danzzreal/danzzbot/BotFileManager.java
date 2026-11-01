package com.danzzreal.danzzbot;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.io.IOException;

public class BotFileManager {

    private final DanzzBot plugin;
    private File botListFolder;

    public BotFileManager(DanzzBot plugin) {
        this.plugin = plugin;
        setupFolder();
    }

    public void setupFolder() {
        botListFolder = new File(plugin.getDataFolder(), "botlist");
        if (!botListFolder.exists()) {
            botListFolder.mkdirs();
            plugin.getLogger().info("[DanzzBot] Folder 'botlist' berhasil dibuat!");
        }
    }

    // Menyimpan data bot ke dalam file terpisah di dalam folder botlist
    public void saveBotToFile(NPC npc, String skinUrl) {
        File botFile = new File(botListFolder, npc.getId() + "_" + npc.getName() + ".yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(botFile);

        config.set("bot.id", npc.getId());
        config.set("bot.name", npc.getName());
        config.set("bot.skin-url", skinUrl != null ? skinUrl : "default");
        config.set("bot.world", npc.isSpawned() ? npc.getEntity().getWorld().getName() : "world");
        config.set("bot.x", npc.isSpawned() ? npc.getEntity().getLocation().getX() : 0.0);
        config.set("bot.y", npc.isSpawned() ? npc.getEntity().getLocation().getY() : 64.0);
        config.set("bot.z", npc.isSpawned() ? npc.getEntity().getLocation().getZ() : 0.0);

        try {
            config.save(botFile);
        } catch (IOException e) {
            plugin.getLogger().severe("[DanzzBot] Gagal menyimpan file bot ID " + npc.getId() + ": " + e.getMessage());
        }
    }

    // Memuat kembali seluruh bot dari folder botlist saat server direstart
    public void loadBotsFromFolder() {
        if (!botListFolder.exists()) return;

        File[] files = botListFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;

        int loadedCount = 0;
        for (File file : files) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            int id = config.getInt("bot.id");
            String name = config.getString("bot.name", "BotRestored");

            // Cek apakah NPC sudah terdaftar di Citizens
            if (CitizensAPI.getNPCRegistry().getById(id) == null) {
                NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, name);
                loadedCount++;
                plugin.getLogger().info("[DanzzBot] Berhasil memuat ulang bot dari file: " + name + " (ID: " + id + ")");
            }
        }
        if (loadedCount > 0) {
            plugin.getLogger().info("[DanzzBot] Total " + loadedCount + " bot berhasil direstore dari folder botlist!");
        }
    }

    // Menghapus file bot saat bot dihapus dari game
    public void deleteBotFile(NPC npc) {
        File[] files = botListFolder.listFiles((dir, name) -> name.startsWith(npc.getId() + "_"));
        if (files != null) {
            for (File file : files) {
                file.delete();
            }
        }
    }
}
