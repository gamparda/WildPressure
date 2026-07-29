package kr.gamparida.wildpressure.ai;

import kr.gamparida.wildpressure.WildPressurePlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Mob;

import java.util.*;

public final class SiegeService {
    private final boolean enabled;
    private final int hitsRequired;
    private final double targetRadiusSquared;
    private final long progressResetMillis;
    private final Set<Material> allowed;
    private final Map<BlockKey, DamageState> damage = new HashMap<>();

    public SiegeService(WildPressurePlugin plugin) {
        var c = plugin.getConfig();
        enabled = c.getBoolean("ai.siege.enabled", false);
        hitsRequired = Math.max(1, c.getInt("ai.siege.hits-required", 8));
        double radius = Math.max(1, c.getDouble("ai.siege.only-near-target-radius", 12));
        targetRadiusSquared = radius * radius;
        progressResetMillis = Math.max(1, c.getLong("ai.siege.progress-reset-seconds", 60)) * 1000L;
        Set<Material> parsed = EnumSet.noneOf(Material.class);
        for (String value : c.getStringList("ai.siege.allowed-materials")) {
            Material material = Material.matchMaterial(value);
            if (material != null && material.isBlock()) parsed.add(material);
            else plugin.getLogger().warning("돌파 허용 블록을 찾을 수 없습니다: " + value);
        }
        allowed = Set.copyOf(parsed);
    }

    public boolean tryBreach(Mob mob, Location target) {
        if (!enabled || !mob.getWorld().equals(target.getWorld())
                || mob.getLocation().distanceSquared(target) > targetRadiusSquared) return false;
        double dx = target.getX() - mob.getLocation().getX();
        double dz = target.getZ() - mob.getLocation().getZ();
        double length = Math.max(0.001, Math.hypot(dx, dz));
        int stepX = (int) Math.round(dx / length);
        int stepZ = (int) Math.round(dz / length);
        Block block = mob.getLocation().getBlock().getRelative(stepX, 0, stepZ);
        if (!allowed.contains(block.getType())) {
            block = block.getRelative(0, 1, 0);
            if (!allowed.contains(block.getType())) return false;
        }
        BlockKey key = new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        long now = System.currentTimeMillis();
        DamageState previous = damage.get(key);
        int hits = previous == null || now - previous.lastHitAt > progressResetMillis ? 1 : previous.hits + 1;
        damage.put(key, new DamageState(hits, now));
        if (hits < hitsRequired) return true;
        damage.remove(key);
        block.setType(Material.AIR, false);
        return true;
    }

    public void cleanup(long now) {
        damage.entrySet().removeIf(entry -> now - entry.getValue().lastHitAt > progressResetMillis);
    }

    public int trackedBlocks() { return damage.size(); }
    private record BlockKey(UUID worldId, int x, int y, int z) {}
    private record DamageState(int hits, long lastHitAt) {}
}
