package kr.gamparida.wildpressure.pressure;

import kr.gamparida.wildpressure.region.RegionKey;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PressureTrackerTest {
    @Test void accumulatesDecaysAndSelectsHottestRegion() {
        PressureTracker tracker = new PressureTracker(1.0, 100);
        UUID world = UUID.randomUUID();
        RegionKey quiet = new RegionKey(world, 0, 0);
        RegionKey loud = new RegionKey(world, 1, 0);
        tracker.add(quiet, new Stimulus(world, 0, 64, 0, 10, 1_000, PressureKind.NOISE, null));
        tracker.add(loud, new Stimulus(world, 10, 64, 0, 40, 1_000, PressureKind.LURE, null));
        assertEquals(35.0, tracker.pressure(loud, 6_000), 0.001);
        var hottest = tracker.hottest(List.of(quiet, loud), Set.of(), 20, 6_000);
        assertTrue(hottest.isPresent());
        assertEquals(loud, hottest.get().region());
    }

    @Test void consumesPressureAndHonoursExcludedRegions() {
        PressureTracker tracker = new PressureTracker(0, 100);
        UUID world = UUID.randomUUID();
        RegionKey key = new RegionKey(world, 2, 3);
        tracker.add(key, new Stimulus(world, 0, 0, 0, 50, 0, PressureKind.NOISE, null));
        tracker.consume(key, 20, 1_000);
        assertEquals(30, tracker.pressure(key, 1_000), 0.001);
        assertTrue(tracker.hottest(List.of(key), Set.of(key), 1, 1_000).isEmpty());
    }
}
