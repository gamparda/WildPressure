package kr.gamparida.wildpressure.ai;

import kr.gamparida.wildpressure.pressure.Stimulus;
import kr.gamparida.wildpressure.region.RegionKey;

import java.util.*;

public final class HuntOperation {
    private final UUID id = UUID.randomUUID();
    private final RegionKey sourceRegion;
    private final Stimulus stimulus;
    private final long startedAt;
    private OperationPhase phase = OperationPhase.SCOUTING;
    private long phaseStartedAt;
    private UUID scoutId;
    private final List<UUID> members = new ArrayList<>();
    private final Map<UUID, Integer> playerKills = new HashMap<>();
    private int initialStrength;
    private int navigationCursor;

    public HuntOperation(RegionKey sourceRegion, Stimulus stimulus, long now, UUID scoutId) {
        this.sourceRegion = sourceRegion;
        this.stimulus = stimulus;
        this.startedAt = now;
        this.phaseStartedAt = now;
        this.scoutId = scoutId;
        this.members.add(scoutId);
        this.initialStrength = 1;
    }

    public void transition(OperationPhase next, long now) { phase = next; phaseStartedAt = now; }
    public void recruit(Collection<UUID> ids) {
        for (UUID id : ids) if (!members.contains(id)) members.add(id);
        initialStrength = Math.max(initialStrength, members.size());
    }
    public void recordKill(UUID playerId) { if (playerId != null) playerKills.merge(playerId, 1, Integer::sum); }
    public int nextNavigationIndex() { return members.isEmpty() ? 0 : Math.floorMod(navigationCursor++, members.size()); }

    public UUID id() { return id; }
    public RegionKey sourceRegion() { return sourceRegion; }
    public Stimulus stimulus() { return stimulus; }
    public long startedAt() { return startedAt; }
    public OperationPhase phase() { return phase; }
    public long phaseStartedAt() { return phaseStartedAt; }
    public UUID scoutId() { return scoutId; }
    public List<UUID> members() { return members; }
    public Map<UUID, Integer> playerKills() { return Map.copyOf(playerKills); }
    public int initialStrength() { return initialStrength; }
    public int totalKills() { return playerKills.values().stream().mapToInt(Integer::intValue).sum(); }
}
