package com.danzzreal.danzzbot;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.citizensnpcs.api.CitizensAPI;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class DanzzBotPlaceholderExpansion extends PlaceholderExpansion {

    private final DanzzBot plugin;

    public DanzzBotPlaceholderExpansion(DanzzBot plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "danzzbot";
    }

    @Override
    public @NotNull String getAuthor() {
        return "DanzzReal";
    }

    @Override
    public @NotNull String getVersion() {
        return "47.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (params.equalsIgnoreCase("total_bots")) {
            int count = 0;
            if (CitizensAPI.getNPCRegistry() != null) {
                // Gunakan perulangan iterator untuk menghitung total NPC
                for (net.citizensnpcs.api.npc.NPC ignored : CitizensAPI.getNPCRegistry()) {
                    count++;
                }
            }
            return String.valueOf(count);
        }

        if (params.equalsIgnoreCase("status")) {
            return "ONLINE";
        }

        return null;
    }
}
