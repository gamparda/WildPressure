package kr.gamparida.wildpressure.listener;

import kr.gamparida.wildpressure.population.DepletionTracker;
import kr.gamparida.wildpressure.population.EntityIndex;
import org.bukkit.Chunk;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

public final class PopulationListener implements Listener {
    private final EntityIndex index;
    private final DepletionTracker depletion;
    private final Set<CreatureSpawnEvent.SpawnReason> blockedReasons;

    public PopulationListener(EntityIndex index, DepletionTracker depletion,
                              Set<CreatureSpawnEvent.SpawnReason> blockedReasons) {
        this.index = index;
        this.depletion = depletion;
        this.blockedReasons = blockedReasons.isEmpty() ? EnumSet.noneOf(CreatureSpawnEvent.SpawnReason.class)
                : EnumSet.copyOf(blockedReasons);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (blockedReasons.contains(event.getSpawnReason())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        UUID id = event.getEntity().getUniqueId();
        if (!index.isManaged(event.getEntity())) return;
        depletion.recordDeath(index.keyOf(event.getEntity()), System.currentTimeMillis());
        index.unregister(id);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof Mob && index.isManaged(entity)) index.register(entity);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        for (Entity entity : chunk.getEntities()) {
            if (index.isManaged(entity)) index.unregister(entity.getUniqueId());
        }
    }
}
