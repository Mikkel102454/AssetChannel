package nu.miguel.assetChannel.api;

import java.time.Duration;
import java.util.Map;

public record HudAsset(
        String id,
        HudSurface surface,
        String slot,
        Duration duration,
        int priority,
        boolean resumeInterrupted,
        Map<String, HudVariable> variables) {
    public HudAsset {
        variables = Map.copyOf(variables);
    }
}
