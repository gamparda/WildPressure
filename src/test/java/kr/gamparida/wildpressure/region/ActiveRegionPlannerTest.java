package kr.gamparida.wildpressure.region;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ActiveRegionPlannerTest {
    @Test void overlappingPlayersShareRegionsInsteadOfDuplicatingBudgets() {
        UUID world = UUID.randomUUID();
        Set<RegionKey> first = ActiveRegionPlanner.around(world, 0, 0, 10, 8);
        Set<RegionKey> second = ActiveRegionPlanner.around(world, 1, 1, 10, 8);
        Set<RegionKey> union = new HashSet<>(first);
        union.addAll(second);
        assertTrue(union.size() < first.size() + second.size());
    }

    @Test void roundsRegionalTargetUp() {
        assertEquals(167, ActiveRegionPlanner.targetPerRegion(5000, 30));
    }
}
