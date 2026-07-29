package kr.gamparida.wildpressure.population;

import kr.gamparida.wildpressure.region.RegionKey;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.*;

public final class EntityIndex {
    private final NamespacedKey managedKey;
    private final int regionSizeChunks;
    private final Map<UUID, ManagedMob> byId = new HashMap<>();
    private final Map<RegionKey, Set<UUID>> byRegion = new HashMap<>();
    private int refreshCursor;

    public EntityIndex(Plugin plugin, int regionSizeChunks) {
        this.managedKey = new NamespacedKey(plugin, "managed");
        this.regionSizeChunks = regionSizeChunks;
    }

    public boolean isManaged(Entity entity) {
        return entity.getPersistentDataContainer().has(managedKey, PersistentDataType.BYTE);
    }

    public void markAndRegister(Entity entity) {
        entity.getPersistentDataContainer().set(managedKey, PersistentDataType.BYTE, (byte) 1);
        register(entity);
    }

    public void register(Entity entity) {
        if (!(entity instanceof Mob)) return;
        RegionKey key = keyOf(entity);
        ManagedMob old = byId.put(entity.getUniqueId(), new ManagedMob(entity.getUniqueId(), entity.getType(), key));
        if (old != null && !old.region().equals(key)) removeFromRegion(old.region(), old.entityId());
        byRegion.computeIfAbsent(key, ignored -> new HashSet<>()).add(entity.getUniqueId());
    }

    public void unregister(UUID entityId) {
        ManagedMob removed = byId.remove(entityId);
        if (removed != null) removeFromRegion(removed.region(), entityId);
    }

    private void removeFromRegion(RegionKey key, UUID id) {
        Set<UUID> ids = byRegion.get(key);
        if (ids == null) return;
        ids.remove(id);
        if (ids.isEmpty()) byRegion.remove(key);
    }

    public int count(RegionKey key) {
        Set<UUID> ids = byRegion.get(key);
        return ids == null ? 0 : ids.size();
    }

    public int total() { return byId.size(); }

    public Collection<ManagedMob> snapshot() { return List.copyOf(byId.values()); }

    public Map<EntityType, Long> countTypes(RegionKey key) {
        Map<EntityType, Long> counts = new EnumMap<>(EntityType.class);
        Set<UUID> ids = byRegion.getOrDefault(key, Set.of());
        for (UUID id : ids) {
            ManagedMob mob = byId.get(id);
            if (mob != null) counts.merge(mob.type(), 1L, Long::sum);
        }
        return counts;
    }

    public void refreshRegions(int budget) {
        if (budget <= 0 || byId.isEmpty()) return;
        List<UUID> ids = new ArrayList<>(byId.keySet());
        refreshCursor = Math.floorMod(refreshCursor, ids.size());
        int checks = Math.min(budget, ids.size());
        for (int i = 0; i < checks; i++) {
            UUID id = ids.get((refreshCursor + i) % ids.size());
            ManagedMob existing = byId.get(id);
            if (existing == null) continue;
            World world = Bukkit.getWorld(existing.region().worldId());
            Entity entity = world == null ? null : world.getEntity(id);
            if (entity == null || !entity.isValid()) {
                unregister(id);
                continue;
            }
            RegionKey now = keyOf(entity);
            if (!now.equals(existing.region())) {
                removeFromRegion(existing.region(), id);
                byId.put(id, existing.moveTo(now));
                byRegion.computeIfAbsent(now, ignored -> new HashSet<>()).add(id);
            }
        }
        refreshCursor = (refreshCursor + checks) % Math.max(1, ids.size());
    }

    public RegionKey keyOf(Entity entity) {
        return RegionKey.fromChunk(entity.getWorld().getUID(), entity.getLocation().getBlockX() >> 4,
                entity.getLocation().getBlockZ() >> 4, regionSizeChunks);
    }
}
