package kr.gamparida.wildpressure.ai;

import kr.gamparida.wildpressure.WildPressurePlugin;
import kr.gamparida.wildpressure.population.EntityIndex;
import kr.gamparida.wildpressure.population.ManagedMob;
import kr.gamparida.wildpressure.pressure.*;
import kr.gamparida.wildpressure.region.RegionKey;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public final class WildAiDirector implements Runnable {
    private final WildPressurePlugin plugin;
    private final EntityIndex index;
    private final PressureTracker pressure;
    private final SiegeService siege;
    private final RewardService rewards;
    private final Map<UUID, HuntOperation> operations = new LinkedHashMap<>();
    private final Map<RegionKey, Long> regionCooldowns = new HashMap<>();
    private final int maxOperations;
    private final int maxGroupSize;
    private final int recruitmentRegionRadius;
    private final int pathBudgetPerOperation;
    private final double operationThreshold;
    private final double operationPressureCost;
    private final long regionCooldownMillis;
    private final long scoutTimeoutMillis;
    private final double scoutArrivalSquared;
    private final long mobilizeMillis;
    private final long assaultMillis;
    private final long retreatMillis;
    private final double retreatAliveFraction;
    private final double moveSpeed;
    private final double acquisitionRadiusSquared;
    private final double corpsePressure;
    private final double sprintPressure;
    private final double lightPressure;
    private final int lightThreshold;
    private final int lightSampleEveryPasses;
    private BukkitTask task;
    private int lightPass;
    private long lastDurationNanos;
    private int lastPathRequests;
    private final Map<UUID, Location> lastPlayerSamples = new HashMap<>();

    public WildAiDirector(WildPressurePlugin plugin, EntityIndex index, PressureTracker pressure,
                          SiegeService siege, RewardService rewards) {
        this.plugin = plugin;
        this.index = index;
        this.pressure = pressure;
        this.siege = siege;
        this.rewards = rewards;
        var c = plugin.getConfig();
        maxOperations = Math.max(0, c.getInt("ai.max-active-operations", 3));
        maxGroupSize = Math.max(2, c.getInt("ai.max-group-size", 80));
        recruitmentRegionRadius = Math.max(0, c.getInt("ai.recruitment-region-radius", 1));
        pathBudgetPerOperation = Math.max(1, c.getInt("ai.max-path-requests-per-operation-pass", 20));
        operationThreshold = Math.max(0, c.getDouble("pressure.operation-threshold", 30));
        operationPressureCost = Math.max(0, c.getDouble("pressure.operation-cost", 20));
        regionCooldownMillis = seconds(c, "ai.region-operation-cooldown-seconds", 180);
        scoutTimeoutMillis = seconds(c, "ai.scout-timeout-seconds", 60);
        double arrival = Math.max(1, c.getDouble("ai.scout-arrival-distance", 12));
        scoutArrivalSquared = arrival * arrival;
        mobilizeMillis = seconds(c, "ai.mobilize-seconds", 8);
        assaultMillis = seconds(c, "ai.assault-duration-seconds", 180);
        retreatMillis = seconds(c, "ai.retreat-seconds", 10);
        retreatAliveFraction = Math.clamp(c.getDouble("ai.retreat-alive-fraction", 0.25), 0.0, 1.0);
        moveSpeed = Math.max(0.1, c.getDouble("ai.move-speed", 1.1));
        double acquisition = Math.max(1, c.getDouble("ai.player-acquisition-radius", 48));
        acquisitionRadiusSquared = acquisition * acquisition;
        corpsePressure = Math.max(0, c.getDouble("pressure.corpse", 2.5));
        sprintPressure = Math.max(0, c.getDouble("pressure.sprint-per-sample", 0.6));
        lightPressure = Math.max(0, c.getDouble("pressure.light-per-sample", 0.5));
        lightThreshold = Math.clamp(c.getInt("pressure.light-threshold", 11), 0, 15);
        lightSampleEveryPasses = Math.max(1, c.getInt("pressure.light-sample-every-passes", 5));
    }

    private long seconds(org.bukkit.configuration.file.FileConfiguration c, String path, long fallback) {
        return Math.max(1, c.getLong(path, fallback)) * 1000L;
    }

    public void start() {
        long period = Math.max(1, plugin.getConfig().getLong("ai.period-ticks", 20));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this, period, period);
    }

    public void stop() {
        if (task != null) task.cancel();
        for (HuntOperation operation : operations.values()) clearTargets(operation);
        operations.clear();
    }

    @Override public void run() {
        long started = System.nanoTime();
        long now = System.currentTimeMillis();
        lastPathRequests = 0;
        samplePlayerMovement(now);
        if (++lightPass >= lightSampleEveryPasses) {
            lightPass = 0;
            samplePlayerLight(now);
        }
        tickOperations(now);
        startOperations(now);
        siege.cleanup(now);
        pressure.cleanup(now);
        regionCooldowns.entrySet().removeIf(e -> e.getValue() <= now);
        lastDurationNanos = System.nanoTime() - started;
    }

    private void samplePlayerLight(long now) {
        if (lightPressure <= 0) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getLocation().getBlock().getLightLevel() < lightThreshold) continue;
            addPressure(player.getLocation(), lightPressure, PressureKind.LIGHT, player.getUniqueId(), now);
        }
    }

    private void samplePlayerMovement(long now) {
        Set<UUID> online = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            online.add(player.getUniqueId());
            Location current = player.getLocation();
            Location previous = lastPlayerSamples.put(player.getUniqueId(), current.clone());
            if (!player.isSprinting() || previous == null || previous.getWorld() == null
                    || !previous.getWorld().equals(current.getWorld())) continue;
            if (previous.distanceSquared(current) >= 4.0) {
                addPressure(current, sprintPressure, PressureKind.NOISE, player.getUniqueId(), now);
            }
        }
        lastPlayerSamples.keySet().removeIf(id -> !online.contains(id));
    }

    public void addPressure(Location location, double strength, PressureKind kind, UUID sourcePlayer, long now) {
        if (location.getWorld() == null || strength <= 0) return;
        RegionKey key = index.keyOf(location.getWorld(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
        pressure.add(key, new Stimulus(location.getWorld().getUID(), location.getX(), location.getY(), location.getZ(),
                strength, now, kind, sourcePlayer));
    }

    public void onManagedDeath(UUID entityId, Location location, UUID killerId) {
        long now = System.currentTimeMillis();
        addPressure(location, corpsePressure, PressureKind.CORPSE, killerId, now);
        for (HuntOperation operation : operations.values()) {
            if (!operation.members().remove(entityId)) continue;
            operation.recordKill(killerId);
            break;
        }
    }

    private void startOperations(long now) {
        while (operations.size() < maxOperations) {
            Set<RegionKey> excluded = new HashSet<>(regionCooldowns.keySet());
            for (HuntOperation operation : operations.values()) excluded.add(operation.sourceRegion());
            Optional<PressureTracker.HotRegion> hot = pressure.hottest(plugin.director().activeRegions(), excluded,
                    operationThreshold, now);
            if (hot.isEmpty()) return;
            Optional<Mob> scout = findRecruit(hot.get().region(), assignedMembers(), null);
            if (scout.isEmpty()) {
                regionCooldowns.put(hot.get().region(), now + Math.min(regionCooldownMillis, 30_000));
                pressure.consume(hot.get().region(), operationPressureCost / 2, now);
                continue;
            }
            HuntOperation operation = new HuntOperation(hot.get().region(), hot.get().stimulus(), now,
                    scout.get().getUniqueId());
            operations.put(operation.id(), operation);
            pressure.consume(hot.get().region(), operationPressureCost, now);
            regionCooldowns.put(hot.get().region(), now + regionCooldownMillis);
            notifyNearby(targetLocation(operation), "정찰 개체가 흔적을 조사하기 시작했습니다.", 64);
        }
    }

    private void tickOperations(long now) {
        Iterator<HuntOperation> iterator = operations.values().iterator();
        while (iterator.hasNext()) {
            HuntOperation operation = iterator.next();
            boolean completed = switch (operation.phase()) {
                case SCOUTING -> tickScouting(operation, now);
                case MOBILIZING -> tickMobilizing(operation, now);
                case ASSAULT -> tickAssault(operation, now);
                case RETREAT -> tickRetreat(operation, now);
            };
            if (completed) {
                clearTargets(operation);
                rewards.settle(operation);
                iterator.remove();
            }
        }
    }

    private boolean tickScouting(HuntOperation operation, long now) {
        Mob scout = mob(operation.scoutId());
        if (scout == null) return true;
        Location target = targetLocation(operation);
        if (target.getWorld() == null) return true;
        if (sameWorld(scout.getLocation(), target) && scout.getLocation().distanceSquared(target) <= scoutArrivalSquared) {
            operation.transition(OperationPhase.MOBILIZING, now);
            notifyNearby(target, "정찰이 끝났습니다. 주변 개체군이 집결합니다.", 72);
            return false;
        }
        if (now - operation.phaseStartedAt() >= scoutTimeoutMillis) return true;
        if (lastPathRequests < pathBudgetPerOperation && scout.getPathfinder().moveTo(target, moveSpeed)) lastPathRequests++;
        return false;
    }

    private boolean tickMobilizing(HuntOperation operation, long now) {
        if (operation.members().size() == 1) operation.recruit(findRecruits(operation));
        navigate(operation, targetLocation(operation), null, Math.min(pathBudgetPerOperation, 8));
        if (now - operation.phaseStartedAt() >= mobilizeMillis || operation.members().size() >= maxGroupSize) {
            operation.transition(OperationPhase.ASSAULT, now);
            notifyNearby(targetLocation(operation), "대규모 군집이 접근하고 있습니다.", 96);
        }
        return false;
    }

    private boolean tickAssault(HuntOperation operation, long now) {
        operation.members().removeIf(id -> mob(id) == null);
        if (operation.members().isEmpty()) return true;
        boolean strengthCollapsed = operation.initialStrength() > 1
                && operation.members().size() <= Math.max(1,
                (int) Math.floor(operation.initialStrength() * retreatAliveFraction));
        if (now - operation.phaseStartedAt() >= assaultMillis || strengthCollapsed) {
            clearTargets(operation);
            operation.transition(OperationPhase.RETREAT, now);
            return false;
        }
        Location stimulus = targetLocation(operation);
        Player targetPlayer = nearestPlayer(stimulus);
        navigate(operation, targetPlayer == null ? stimulus : targetPlayer.getLocation(), targetPlayer,
                pathBudgetPerOperation);
        return false;
    }

    private boolean tickRetreat(HuntOperation operation, long now) {
        if (now - operation.phaseStartedAt() >= retreatMillis) {
            notifyNearby(targetLocation(operation), "남은 군집이 흩어졌습니다.", 72);
            return true;
        }
        return false;
    }

    private void navigate(HuntOperation operation, Location baseTarget, Player targetPlayer, int budget) {
        int attempts = Math.min(budget, operation.members().size());
        for (int i = 0; i < attempts; i++) {
            UUID id = operation.members().get(operation.nextNavigationIndex());
            Mob mob = mob(id);
            if (mob == null || !mob.getWorld().equals(baseTarget.getWorld())) continue;
            MobRole role = RoleResolver.resolve(mob.getType());
            if (targetPlayer != null && mob.getLocation().distanceSquared(targetPlayer.getLocation()) <= acquisitionRadiusSquared) {
                mob.setTarget(targetPlayer);
            }
            Location destination = roleDestination(role, mob, baseTarget);
            if (mob.getPathfinder().moveTo(destination, moveSpeed)) lastPathRequests++;
            if ((role == MobRole.BREACH || role == MobRole.PRESSURE) && siege.tryBreach(mob, baseTarget)) {
                // 돌파 진행은 SiegeService에서 블록별로 합산한다.
            }
        }
    }

    private Location roleDestination(MobRole role, Mob mob, Location target) {
        if (role != MobRole.FLANK) return target;
        int hash = mob.getUniqueId().hashCode();
        double angle = Math.floorMod(hash, 360) * Math.PI / 180.0;
        return target.clone().add(Math.cos(angle) * 8, 0, Math.sin(angle) * 8);
    }

    private Optional<Mob> findRecruit(RegionKey center, Set<UUID> excluded, Collection<UUID> additionallyExcluded) {
        Mob best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (ManagedMob managed : index.snapshot()) {
            if (!managed.region().worldId().equals(center.worldId()) || excluded.contains(managed.entityId())
                    || (additionallyExcluded != null && additionallyExcluded.contains(managed.entityId()))) continue;
            int distance = Math.abs(managed.region().x() - center.x()) + Math.abs(managed.region().z() - center.z());
            if (distance > recruitmentRegionRadius || distance >= bestDistance) continue;
            Mob entity = mob(managed.entityId());
            if (entity == null) continue;
            best = entity;
            bestDistance = distance;
        }
        return Optional.ofNullable(best);
    }

    private List<UUID> findRecruits(HuntOperation operation) {
        Set<UUID> excluded = assignedMembers();
        excluded.removeAll(operation.members());
        List<UUID> recruited = new ArrayList<>();
        for (ManagedMob managed : index.snapshot()) {
            if (operation.members().size() + recruited.size() >= maxGroupSize) break;
            if (!managed.region().worldId().equals(operation.sourceRegion().worldId())
                    || excluded.contains(managed.entityId()) || operation.members().contains(managed.entityId())) continue;
            int distance = Math.abs(managed.region().x() - operation.sourceRegion().x())
                    + Math.abs(managed.region().z() - operation.sourceRegion().z());
            if (distance > recruitmentRegionRadius || mob(managed.entityId()) == null) continue;
            recruited.add(managed.entityId());
        }
        return recruited;
    }

    private Set<UUID> assignedMembers() {
        Set<UUID> result = new HashSet<>();
        for (HuntOperation operation : operations.values()) result.addAll(operation.members());
        return result;
    }

    private Mob mob(UUID id) {
        Entity entity = index.entity(id);
        if (entity instanceof Mob mob && entity.isValid()) return mob;
        return null;
    }

    private Player nearestPlayer(Location origin) {
        if (origin.getWorld() == null) return null;
        Player nearest = null;
        double best = acquisitionRadiusSquared;
        for (Player player : origin.getWorld().getPlayers()) {
            double distance = player.getLocation().distanceSquared(origin);
            if (distance < best) { best = distance; nearest = player; }
        }
        return nearest;
    }

    private Location targetLocation(HuntOperation operation) {
        Stimulus s = operation.stimulus();
        World world = Bukkit.getWorld(s.worldId());
        return world == null ? new Location(null, s.x(), s.y(), s.z()) : new Location(world, s.x(), s.y(), s.z());
    }

    private boolean sameWorld(Location a, Location b) { return a.getWorld() != null && a.getWorld().equals(b.getWorld()); }

    private void clearTargets(HuntOperation operation) {
        for (UUID id : operation.members()) {
            Mob mob = mob(id);
            if (mob == null) continue;
            mob.setTarget(null);
            mob.getPathfinder().stopPathfinding();
        }
    }

    private void notifyNearby(Location location, String message, double radius) {
        if (location.getWorld() == null) return;
        double squared = radius * radius;
        for (Player player : location.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(location) <= squared) player.sendMessage(plugin.prefix() + message);
        }
    }

    public int operationCount() { return operations.size(); }
    public List<HuntOperation> operations() { return List.copyOf(operations.values()); }
    public long lastDurationNanos() { return lastDurationNanos; }
    public int lastPathRequests() { return lastPathRequests; }
    public int trackedSiegeBlocks() { return siege.trackedBlocks(); }
}
