package kr.gamparida.wildpressure.ai;

import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoleResolverTest {
    @Test void assignsSpeciesRoles() {
        assertEquals(MobRole.PRESSURE, RoleResolver.resolve(EntityType.ZOMBIE));
        assertEquals(MobRole.RANGED, RoleResolver.resolve(EntityType.SKELETON));
        assertEquals(MobRole.FLANK, RoleResolver.resolve(EntityType.SPIDER));
        assertEquals(MobRole.BREACH, RoleResolver.resolve(EntityType.CREEPER));
    }
}
