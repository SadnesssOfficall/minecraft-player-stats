package net.sadness.playerstats.bukkit;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import net.sadness.playerstats.bukkit.command.AdminStatsCommand;
import net.sadness.playerstats.bukkit.command.StatsCommand;
import net.sadness.playerstats.bukkit.listener.ActivityListener;
import net.sadness.playerstats.bukkit.listener.ConnectionListener;
import net.sadness.playerstats.bukkit.listener.MovementListener;
import net.sadness.playerstats.bukkit.message.Messages;
import net.sadness.playerstats.config.ConfigException;
import net.sadness.playerstats.config.Settings;
import net.sadness.playerstats.service.StatsService;
import net.sadness.playerstats.storage.StorageException;
import net.sadness.playerstats.storage.StorageExecutor;
import net.sadness.playerstats.storage.sqlite.SqliteStatsRepository;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlayerStatsPlugin extends JavaPlugin {

    private static final long TICKS_PER_SECOND = 20L;

    private Settings settings;
    private SqliteStatsRepository repository;
    private StatsService service;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Messages messages;
        try {
            settings = Settings.parse(getConfig()::get);
            messages = new Messages(getConfig()::getString);
            repository = SqliteStatsRepository.open(getDataFolder().toPath().resolve(settings.databaseFile()), System::currentTimeMillis);
        } catch (ConfigException invalid) {
            invalid.problems().forEach(problem -> getLogger().severe("config.yml: " + problem));
            disable();
            return;
        } catch (StorageException failure) {
            getLogger().log(Level.SEVERE, "Could not open the statistics database", failure);
            disable();
            return;
        }
        service = new StatsService(repository, new StorageExecutor(settings.queueCapacity()),
                () -> TimeUnit.NANOSECONDS.toMillis(System.nanoTime()), getLogger());
        getServer().getPluginManager().registerEvents(new ConnectionListener(service), this);
        getServer().getPluginManager().registerEvents(new ActivityListener(service), this);
        getServer().getPluginManager().registerEvents(new MovementListener(service, settings), this);
        Objects.requireNonNull(getCommand("stats")).setExecutor(new StatsCommand(this, service, messages, settings.pageSize()));
        Objects.requireNonNull(getCommand("adminstats")).setExecutor(new AdminStatsCommand(this, service, messages, settings.pageSize()));
        long period = settings.flushInterval().toSeconds() * TICKS_PER_SECOND;
        getServer().getScheduler().runTaskTimerAsynchronously(this, service::flushAll, period, period);
        for (Player player : getServer().getOnlinePlayers()) {
            service.track(player.getUniqueId(), player.getName(), false);
        }
    }

    @Override
    public void onDisable() {
        getServer().getScheduler().cancelTasks(this);
        if (service != null) {
            service.shutdown(settings.shutdownTimeout());
        }
        if (repository != null) {
            repository.close();
        }
    }

    private void disable() {
        getServer().getPluginManager().disablePlugin(this);
    }
}
