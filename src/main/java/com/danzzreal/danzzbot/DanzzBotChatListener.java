package com.danzzreal.danzzbot;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Arrays;
import java.util.List;

public class DanzzBotChatListener implements Listener, CommandExecutor, TabCompleter {

    private final DanzzBot plugin;

    public DanzzBotChatListener(DanzzBot plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("auto")) {
            plugin.toggleEngineFeature("chatbot-slang");
            boolean status = plugin.isEngineFeatureActive("chatbot-slang");
            String statusText = status ? ChatColor.GREEN + "AKTIF (ON)" : ChatColor.RED + "MATI (OFF)";
            String msg = plugin.getLang("auto-chat-toggle", "&5&l[DanzzBot] &7Auto Chat: {status}").replace("{status}", statusText);
            sender.sendMessage(msg);
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Gunakan: /botchat auto (On/Off) atau /botchat <id_bot> <pesan>");
            return true;
        }

        try {
            int botId = Integer.parseInt(args[0]);
            NPC npc = CitizensAPI.getNPCRegistry().getById(botId);
            if (npc == null || !npc.isSpawned()) {
                sender.sendMessage(ChatColor.RED + "[DanzzBot] Bot dengan ID " + botId + " tidak ditemukan!");
                return true;
            }

            StringBuilder messageBuilder = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                messageBuilder.append(args[i]).append(" ");
            }
            Bukkit.broadcastMessage(ChatColor.YELLOW + "[BOT] " + npc.getName() + ": " + ChatColor.WHITE + messageBuilder.toString().trim());
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "[DanzzBot] ID Bot harus berupa angka!");
        }
        return true;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!plugin.isEngineFeatureActive("social-chat-response")) return;

        String message = event.getMessage().toLowerCase();
        Player player = event.getPlayer();

        if (CitizensAPI.getNPCRegistry() != null) {
            for (NPC npc : CitizensAPI.getNPCRegistry()) {
                if (npc.isSpawned() && npc.getEntity() != null) {
                    Location botLoc = npc.getEntity().getLocation();
                    if (botLoc.getWorld().equals(player.getWorld()) && botLoc.distanceSquared(player.getLocation()) <= 225) {
                        if (message.contains("halo") || message.contains("p") || message.contains("hai")) {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                String reply = plugin.getLang("chat-reply-hello", "Halo @{player}!").replace("{player}", player.getName());
                                Bukkit.broadcastMessage(ChatColor.YELLOW + "[BOT] " + npc.getName() + ": " + ChatColor.WHITE + reply);
                            });
                        } else if (message.contains("terima kasih") || message.contains("thanks")) {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                String reply = plugin.getLang("chat-reply-thanks", "Sama-sama!");
                                Bukkit.broadcastMessage(ChatColor.YELLOW + "[BOT] " + npc.getName() + ": " + ChatColor.WHITE + reply);
                            });
                        }
                        break;
                    }
                }
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return Arrays.asList("auto");
        return null;
    }
}
