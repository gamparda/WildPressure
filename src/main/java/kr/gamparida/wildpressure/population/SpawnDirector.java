package kr.gamparida.wildpressure.population;

import kr.gamparida.wildpressure.WildPressurePlugin;
import kr.gamparida.wildpressure.region.ActiveRegionPlanner;
import kr.gamparida.wildpressure.region.RegionKey;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class SpawnDirector implements Runnable {
    private final WildPressurePlugin plugin;
    private final EntityIndex index;
    private final DepletionTracker depletion;
    private final int targetPerPlayer;
    private final int regionSize;
    private final int influenceRadius;
    private final int maxSpawns;
    private final int maxRemovals;
    private final int refreshBudget;
    private final long irrelevantGraceMillis;
    private final double pauseAboveMspt;
    private final int minPlayerDistanceSquared;
    private final int locationAttempts;
    private final int maxBlockLight;
    private final boolean keepFromDespawn;
    private final List<EntityType> species;
    private final Map<UUID, Long> irrelevantSince = new HashMap<>();
    private Set<RegionKey> activeRegions = Set.of();
    private BukkitTask task;
    private long lastDurationNanos;
    private int lastSpawned;
    private int lastRemoved;
    private int desiredPopulation;

    public SpawnDirector(WildPressurePlugin plugin, EntityIndex index, DepletionTracker depletion) {
        this.plugin = plugin;
        this.index = index;
        this.depletion = depletion;
        var c = plugin.getConfig();
        targetPerPlayer = Math.max(1, c.getInt("population.target-per-player", 5000));
        regionSize = Math.max(1, c.getInt("population.region-size-chunks", 8));
        influenceRadius = Math.max(1, c.getInt("population.influence-radius-chunks", 10));
        maxSpawns = Math.max(0, c.getInt("population.max-spawns-per-pass", 50));
        maxRemovals = Math.max(0, c.getInt("population.max-removals-per-pass", 20));
        refreshBudget = Math.max(0, c.getInt("population.max-index-refresh-per-pass", 250));
        irrelevantGraceMillis = Math.max(0, c.getLong("population.irrelevant-grace-seconds", 120)) * 1000L;
        pauseAboveMspt = c.getDouble("population.pause-spawning-above-mspt", 45.0);
        int minDistance = Math.max(0, c.getInt("spawn.min-distance-from-player", 32));
        minPlayerDistanceSquared = minDistance * minDistance;
        locationAttempts = Math.max(1, c.getInt("spawn.max-location-attempts", 12));
        maxBlockLight = Math.clamp(c.getInt("spawn.max-block-light", 15), 0, 15);
        keepFromDespawn = c.getBoolean("spawn.keep-managed-mobs-from-vanilla-despawn", true);
        species = parseSpecies(c.getStringList("spawn.species"));
    }

    private List<EntityType> parseSpecies(List<String> names) {
        List<EntityType> result = new ArrayList<>();
        for (String name : names) {
            try {
                EntityType type = EntityType.valueOf(name.toUpperCase(Locale.ROOT));
                if (type.isSpawnable() && type.isAlive()) result.add(type);
                else plugin.getLogger().warning("생성할 수 없는 생물 종류: " + name);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("알 수 없는 생물 종류: " + name);
            }
        }
        if (result.isEmpty()) throw new IllegalStateException("spawn.species에 유효한 생물이 없습니다.");
        return List.copyOf(result);
    }

    public void start() {
        long period = Math.max(1L, plugin.getConfig().getLong("population.reconcile-period-ticks", 20));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this, period, period);
    }

    public void stop() { if (task != null) task.cancel(); }

    @Override public void run() {
        long started = System.nanoTime();
        long now = System.currentTimeMillis();
        index.refreshRegions(refreshBudget);
        activeRegions = calculateActiveRegions();
        int singlePlayerRegions = ActiveRegionPlanner.around(new UUID(0, 0), 0, 0,
                influenceRadius, regionSize).size();
        int perRegionTarget = ActiveRegionPlanner.targetPerRegion(targetPerPlayer, singlePlayerRegions);
        desiredPopulation = activeRegions.size() * perRegionTarget;
        lastRemoved = removeIrrelevant(now);
        lastSpawned = Bukkit.getAverageTickTime() >= pauseAboveMspt ? 0 : fillDeficits(perRegionTarget, now);
        depletion.cleanup(now);
        lastDurationNanos = System.nanoTime() - started;
    }

    private Set<RegionKey> calculateActiveRegions() {
        Set<RegionKey> result = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            result.addAll(ActiveRegionPlanner.around(player.getWorld().getUID(),
                    player.getLocation().getBlockX() >> 4, player.getLocation().getBlockZ() >> 4,
                    influenceRadius, regionSize));
        }
        return Set.copyOf(result);
    }

    private int fillDeficits(int perRegionTarget, long now) {
        if (activeRegions.isEmpty() || maxSpawns == 0) return 0;
        List<RegionKey> candidates = activeRegions.stream()
                .filter(key -> !depletion.isDepleted(key, now))
                .filter(key -> index.count(key) < perRegionTarget)
                .sorted(Comparator.comparingInt(index::count))
                .toList();
        int spawned = 0;
        for (RegionKey key : candidates) {
            int deficit = perRegionTarget - index.count(key);
            while (deficit-- > 0 && spawned < maxSpawns) {
                if (spawnOne(key)) spawned++;
                else break;
            }
            if (spawned >= maxSpawns) break;
        }
        return spawned;
    }

    private boolean spawnOne(RegionKey key) {
        World world = Bukkit.getWorld(key.worldId());
        if (world == null) return false;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < locationAttempts; attempt++) {
            int chunkX = key.x() * regionSize + random.nextInt(regionSize);
            int chunkZ = key.z() * regionSize + random.nextInt(regionSize);
            if (!world.isChunkLoaded(chunkX, chunkZ)) continue;
            int x = (chunkX << 4) + random.nextInt(16);
            int z = (chunkZ << 4) + random.nextInt(16);
            int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
            if (y <= world.getMinHeight() || y + 1 >= world.getMaxHeight()) continue;
            Location location = new Location(world, x + 0.5, y, z + 0.5);
            if (!validLocation(location)) continue;
            EntityType type = species.get(random.nextInt(species.size()));
            Entity entity;
            try {
                entity = world.spawnEntity(location, type, CreatureSpawnEvent.SpawnReason.CUSTOM);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("생물 생성 실패(" + type + "): " + ex.getMessage());
                return false;
            }
            if (!(entity instanceof Mob mob)) {
                entity.remove();
                return false;
            }
            if (keepFromDespawn) mob.setRemoveWhenFarAway(false);
            index.markAndRegister(mob);
            return true;
        }
        return false;
    }

    private boolean validLocation(Location location) {
        Block feet = location.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block ground = feet.getRelative(0, -1, 0);
        if (!feet.isPassable() || !head.isPassable() || !ground.getType().isSolid()) return false;
        if (feet.getLightLevel() > maxBlockLight) return false;
        for (Player player : location.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(location) < minPlayerDistanceSquared) return false;
        }
        return true;
    }

    private int removeIrrelevant(long now) {
        int removed = 0;
        for (ManagedMob managed : index.snapshot()) {
            if (activeRegions.contains(managed.region())) {
                irrelevantSince.remove(managed.entityId());
                continue;
            }
            long since = irrelevantSince.computeIfAbsent(managed.entityId(), ignored -> now);
            if (now - since < irrelevantGraceMillis || removed >= maxRemovals) continue;
            World world = Bukkit.getWorld(managed.region().worldId());
            Entity entity = world == null ? null : world.getEntity(managed.entityId());
            if (entity != null) entity.remove();
            index.unregister(managed.entityId());
            irrelevantSince.remove(managed.entityId());
            removed++;
        }
        return removed;
    }

    public Set<RegionKey> activeRegions() { return activeRegions; }
    public int desiredPopulation() { return desiredPopulation; }
    public long lastDurationNanos() { return lastDurationNanos; }
    public int lastSpawned() { return lastSpawned; }
    public int lastRemoved() { return lastRemoved; }
}
