package nu.miguel.assetChannel.api;

import net.kyori.adventure.text.Component;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record HudRequest(Map<String, Component> variables, Optional<String> slot,
                         Optional<Integer> priority, Optional<Boolean> resumeInterrupted) {
    public HudRequest {
        variables = Map.copyOf(Objects.requireNonNull(variables, "variables"));
        slot = slot == null ? Optional.empty() : slot;
        priority = priority == null ? Optional.empty() : priority;
        resumeInterrupted = resumeInterrupted == null ? Optional.empty() : resumeInterrupted;
    }
    public HudRequest(Map<String, Component> variables) {
        this(variables, Optional.empty(), Optional.empty(), Optional.empty());
    }
    public static HudRequest of(Map<String, Component> variables) { return new HudRequest(variables); }
}
