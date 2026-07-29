package kr.gamparida.wildpressure.region;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ActiveRegionPlanner {
    private ActiveRegionPlanner() {}

    public static Set<RegionKey> around(UUID worldId, int centerChunkX, int centerChunkZ,
                                        int radiusChunks, int regionSizeChunks) {
        if (radiusChunks < 0) throw new IllegalArgumentException("radiusChunks must not be negative");
        Set<RegionKey> result = new HashSet<>();
        int minRegionX = Math.floorDiv(centerChunkX - radiusChunks, regionSizeChunks);
        int maxRegionX = Math.floorDiv(centerChunkX + radiusChunks, regionSizeChunks);
        int minRegionZ = Math.floorDiv(centerChunkZ - radiusChunks, regionSizeChunks);
        int maxRegionZ = Math.floorDiv(centerChunkZ + radiusChunks, regionSizeChunks);
        for (int x = minRegionX; x <= maxRegionX; x++) {
            for (int z = minRegionZ; z <= maxRegionZ; z++) result.add(new RegionKey(worldId, x, z));
        }
        return result;
    }

    public static int targetPerRegion(int targetPerPlayer, int singlePlayerRegionCount) {
        if (targetPerPlayer < 0 || singlePlayerRegionCount < 1) throw new IllegalArgumentException("invalid target");
        return Math.max(1, Math.ceilDiv(targetPerPlayer, singlePlayerRegionCount));
    }
}
