package kr.gamparida.wildpressure.pressure;

import kr.gamparida.wildpressure.region.RegionKey;

import java.util.*;

public final class PressureTracker {
    private final double decayPerSecond;
    private final double maximum;
    private final Map<RegionKey, State> states = new HashMap<>();

    public PressureTracker(double decayPerSecond, double maximum) {
        if (decayPerSecond < 0 || maximum <= 0) throw new IllegalArgumentException("invalid pressure settings");
        this.decayPerSecond = decayPerSecond;
        this.maximum = maximum;
    }

    public void add(RegionKey key, Stimulus stimulus) {
        State current = states.get(key);
        double value = current == null ? 0 : decayed(current, stimulus.createdAt());
        Stimulus retained = current == null || current.stimulus == null
                || stimulus.strength() >= current.stimulus.strength() * 0.6 ? stimulus : current.stimulus;
        states.put(key, new State(Math.min(maximum, value + Math.max(0, stimulus.strength())),
                stimulus.createdAt(), retained));
    }

    public double pressure(RegionKey key, long now) {
        State state = states.get(key);
        return state == null ? 0 : decayed(state, now);
    }

    public Optional<HotRegion> hottest(Collection<RegionKey> candidates, Set<RegionKey> excluded,
                                       double threshold, long now) {
        HotRegion hottest = null;
        for (RegionKey key : candidates) {
            if (excluded.contains(key)) continue;
            State state = states.get(key);
            if (state == null || state.stimulus == null) continue;
            double value = decayed(state, now);
            if (value < threshold) continue;
            if (hottest == null || value > hottest.pressure()) hottest = new HotRegion(key, value, state.stimulus);
        }
        return Optional.ofNullable(hottest);
    }

    public void consume(RegionKey key, double amount, long now) {
        State state = states.get(key);
        if (state == null) return;
        states.put(key, new State(Math.max(0, decayed(state, now) - Math.max(0, amount)), now, state.stimulus));
    }

    public int activeRegions(double threshold, long now) {
        cleanup(now);
        int count = 0;
        for (State state : states.values()) if (decayed(state, now) >= threshold) count++;
        return count;
    }

    public void cleanup(long now) {
        states.entrySet().removeIf(e -> decayed(e.getValue(), now) <= 0.001);
    }

    private double decayed(State state, long now) {
        long elapsed = Math.max(0, now - state.updatedAt);
        return Math.max(0, state.value - decayPerSecond * elapsed / 1000.0);
    }

    public record HotRegion(RegionKey region, double pressure, Stimulus stimulus) {}
    private record State(double value, long updatedAt, Stimulus stimulus) {}
}
