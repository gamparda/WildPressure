package kr.gamparida.wildpressure.population;

import kr.gamparida.wildpressure.region.RegionKey;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DepletionTrackerTest {
    @Test void depletesOnlyAfterThresholdInsideWindowAndRecoversAfterCooldown() {
        DepletionTracker tracker = new DepletionTracker(3, 1_000, 5_000);
        RegionKey key = new RegionKey(UUID.randomUUID(), 0, 0);
        assertFalse(tracker.recordDeath(key, 100));
        assertFalse(tracker.recordDeath(key, 200));
        assertTrue(tracker.recordDeath(key, 300));
        assertTrue(tracker.isDepleted(key, 5_299));
        assertFalse(tracker.isDepleted(key, 5_300));
    }

    @Test void expiredDeathWindowDoesNotAccumulateForever() {
        DepletionTracker tracker = new DepletionTracker(2, 100, 1_000);
        RegionKey key = new RegionKey(UUID.randomUUID(), 1, 1);
        assertFalse(tracker.recordDeath(key, 0));
        assertFalse(tracker.recordDeath(key, 101));
    }
}
