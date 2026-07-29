package kr.gamparida.wildpressure.pressure;

import java.util.UUID;

public record Stimulus(UUID worldId, double x, double y, double z, double strength,
                       long createdAt, PressureKind kind, UUID sourcePlayer) {
}
