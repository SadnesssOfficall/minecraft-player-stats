package net.sadness.playerstats.bukkit.listener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.sadness.playerstats.config.Settings;
import net.sadness.playerstats.movement.MovementMode;
import net.sadness.playerstats.movement.MovementTracker;
import net.sadness.playerstats.service.StatsService;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class MovementListener implements Listener {

    private final StatsService service;
    private final double minStep;
    private final double maxStep;
    private final Map<UUID, MovementTracker> trackers = new HashMap<>();

    public MovementListener(StatsService service, Settings settings) {
        this.service = service;
        this.minStep = settings.minStep();
        this.maxStep = settings.maxStep();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedPosition()) {
            return;
        }
        Player player = event.getPlayer();
        Location to = event.getTo();
        MovementTracker tracker = trackerFor(player.getUniqueId());
        UUID world = to.getWorld().getUID();
        if (player.isInsideVehicle()) {
            tracker.reanchor(world, to.getX(), to.getY(), to.getZ());
            return;
        }
        MovementMode mode = modeOf(player);
        long centimeters = tracker.record(world, to.getX(), to.getY(), to.getZ(), mode);
        service.record(player.getUniqueId(), mode.field(), centimeters);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        reanchor(event.getPlayer(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        reanchor(event.getPlayer(), event.getRespawnLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        trackers.remove(event.getPlayer().getUniqueId());
    }

    private void reanchor(Player player, Location location) {
        trackerFor(player.getUniqueId()).reanchor(location.getWorld().getUID(), location.getX(), location.getY(), location.getZ());
    }

    private MovementTracker trackerFor(UUID id) {
        return trackers.computeIfAbsent(id, ignored -> new MovementTracker(minStep, maxStep));
    }

    private static MovementMode modeOf(Player player) {
        if (player.isGliding() || player.isFlying()) {
            return MovementMode.FLYING;
        }
        if (player.isInWater()) {
            return MovementMode.SWIMMING;
        }
        return MovementMode.WALKING;
    }
}
