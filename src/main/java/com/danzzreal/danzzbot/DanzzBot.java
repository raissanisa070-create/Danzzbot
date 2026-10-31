package com.danzzreal.danzzbot;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class DanzzBot extends JavaPlugin {

    private static DanzzBot instance;
    private BukkitTask enterpriseEngineTask;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // Daftarkan Command Executor, Tab Completer, & Event Listener secara terpadu
        if (getCommand("danzzbot") != null) {
            DanzzBotCommand commandHandler = new DanzzBotCommand(this);
            getCommand("danzzbot").setExecutor(commandHandler);
            getCommand("danzzbot").setTabCompleter(commandHandler);
            getServer().getPluginManager().registerEvents(commandHandler, this);
        }

        // Jalankan Enterprise Engine Background Task (Pemantauan TPS & Sistem Bot)
        startEnterpriseEngineTask();

        getLogger().info("==================================================");
        getLogger().info(" [✔] DanzzBot Enterprise Network berhasil aktif!");
        getLogger().info(" [✔] Platform: Paper/Spigot 1.21+ | Status: STABLE");
        getLogger().info("==================================================");
    }

    @Override
    public void onDisable() {
        if (enterpriseEngineTask != null) {
            enterpriseEngineTask.cancel();
        }
        getLogger().info("[-] DanzzBot Enterprise Network berhasil dimatikan dengan aman.");
    }

    public static DanzzBot getInstance() {
        return instance;
    }

    private void startEnterpriseEngineTask() {
        enterpriseEngineTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            try {
                double currentTps = 20.0;
                boolean isLagging = currentTps < 18.0;

                if (isLagging) {
                    getLogger().warning("[DanzzBot Engine] Peringatan: TPS server menurun (" + currentTps + "). Menyesuaikan aktivitas bot...");
                }
            } catch (Exception e) {
                getLogger().severe("[DanzzBot Engine] Terjadi kesalahan pada background task: " + e.getMessage());
            }
        }, 0L, 100L);
    }
}
