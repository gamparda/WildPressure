package kr.gamparida.wildpressure.region;

import java.util.UUID;

public record RegionKey(UUID worldId, int x, int z) {
    public static RegionKey fromChunk(UUID worldId, int chunkX, int chunkZ, int regionSizeChunks) {
        if (regionSizeChunks < 1) throw new IllegalArgumentException("regionSizeChunks must be positive");
        return new RegionKey(worldId, Math.floorDiv(chunkX, regionSizeChunks), Math.floorDiv(chunkZ, regionSizeChunks));
    }
}
