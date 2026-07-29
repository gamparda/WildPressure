package kr.gamparida.wildpressure.population;

import kr.gamparida.wildpressure.region.RegionKey;

import java.util.HashMap;
import java.util.Map;

public final class DepletionTracker {
    private final int deathsToDeplete;
    private final long deathWindowMillis;
    private final long refillCooldownMillis;
    private final Map<RegionKey, State> states = new HashMap<>();

    public DepletionTracker(int deathsToDeplete, long deathWindowMillis, long refillCooldownMillis) {
        if (deathsToDeplete < 1 || deathWindowMillis < 1 || refillCooldownMillis < 1) {
            throw new IllegalArgumentException("depletion values must be positive");
        }
        this.deathsToDeplete = deathsToDeplete;
        this.deathWindowMillis = deathWindowMillis;
        this.refillCooldownMillis = refillCooldownMillis;
    }

    public boolean recordDeath(RegionKey key, long now) {
        State state = states.get(key);
        if (state == null || now - state.windowStartedAt > deathWindowMillis) {
            state = new State(0, now, 0);
        }
        int deaths = state.deaths + 1;
        long depletedUntil = state.depletedUntil;
        if (deaths >= deathsToDeplete) depletedUntil = Math.max(depletedUntil, now + refillCooldownMillis);
        states.put(key, new State(deaths, state.windowStartedAt, depletedUntil));
        return depletedUntil > now;
    }

    public boolean isDepleted(RegionKey key, long now) {
        State state = states.get(key);
        return state != null && state.depletedUntil > now;
    }

    public int activeDepletions(long now) {
        cleanup(now);
        int count = 0;
        for (State state : states.values()) if (state.depletedUntil > now) count++;
        return count;
    }

    public void cleanup(long now) {
        states.entrySet().removeIf(e -> e.getValue().depletedUntil <= now
                && now - e.getValue().windowStartedAt > deathWindowMillis);
    }

    private record State(int deaths, long windowStartedAt, long depletedUntil) {}
}
