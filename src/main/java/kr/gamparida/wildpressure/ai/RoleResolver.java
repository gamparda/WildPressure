package kr.gamparida.wildpressure.ai;

import org.bukkit.entity.EntityType;

public final class RoleResolver {
    private RoleResolver() {}

    public static MobRole resolve(EntityType type) {
        return switch (type) {
            case SKELETON, STRAY, BOGGED -> MobRole.RANGED;
            case SPIDER, CAVE_SPIDER -> MobRole.FLANK;
            case CREEPER -> MobRole.BREACH;
            default -> MobRole.PRESSURE;
        };
    }
}
