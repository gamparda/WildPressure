package kr.gamparida.wildpressure.population;

import kr.gamparida.wildpressure.region.RegionKey;
import org.bukkit.entity.EntityType;

import java.util.UUID;

public record ManagedMob(UUID entityId, EntityType type, RegionKey region) {
    public ManagedMob moveTo(RegionKey destination) {
        return new ManagedMob(entityId, type, destination);
    }
}
