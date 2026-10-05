package net.sadness.playerstats.bukkit.listener;

import net.sadness.playerstats.service.StatsService;
import net.sadness.playerstats.stats.StatField;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

public final class ActivityListener implements Listener {

    private static final long ONE = 1L;

    private final StatsService service;

    public ActivityListener(StatsService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        service.record(event.getPlayer().getUniqueId(), StatField.BLOCKS_BROKEN, ONE);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        service.record(event.getPlayer().getUniqueId(), StatField.BLOCKS_PLACED, ONE);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        service.record(event.getEntity().getUniqueId(), StatField.DEATHS, ONE);
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            service.record(killer.getUniqueId(), StatField.PLAYER_KILLS, ONE);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            service.record(killer.getUniqueId(), StatField.MOB_KILLS, ONE);
        }
    }
}
