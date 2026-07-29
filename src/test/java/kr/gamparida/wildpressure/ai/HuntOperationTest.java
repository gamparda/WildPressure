package kr.gamparida.wildpressure.ai;

import kr.gamparida.wildpressure.pressure.*;
import kr.gamparida.wildpressure.region.RegionKey;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HuntOperationTest {
    @Test void tracksUniqueMembersTransitionsAndPlayerKills() {
        UUID world = UUID.randomUUID();
        UUID scout = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        HuntOperation operation = new HuntOperation(new RegionKey(world, 0, 0),
                new Stimulus(world, 0, 64, 0, 30, 1_000, PressureKind.NOISE, null), 1_000, scout);
        operation.recruit(List.of(scout, member));
        assertEquals(2, operation.members().size());
        operation.transition(OperationPhase.ASSAULT, 2_000);
        assertEquals(OperationPhase.ASSAULT, operation.phase());
        UUID player = UUID.randomUUID();
        operation.recordKill(player);
        operation.recordKill(player);
        assertEquals(2, operation.totalKills());
    }
}
