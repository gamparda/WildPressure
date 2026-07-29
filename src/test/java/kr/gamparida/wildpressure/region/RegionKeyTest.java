package kr.gamparida.wildpressure.region;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RegionKeyTest {
    @Test void mapsNegativeChunksWithFloorDivision() {
        UUID world = UUID.randomUUID();
        assertEquals(new RegionKey(world, -1, -1), RegionKey.fromChunk(world, -1, -8, 8));
        assertEquals(new RegionKey(world, -2, -2), RegionKey.fromChunk(world, -9, -16, 8));
    }

    @Test void rejectsInvalidRegionSize() {
        assertThrows(IllegalArgumentException.class, () -> RegionKey.fromChunk(UUID.randomUUID(), 0, 0, 0));
    }
}
