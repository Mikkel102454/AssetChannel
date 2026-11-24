package nu.miguel.assetChannel.api;

import java.util.Objects;

public record StageTransitionSettings(StageTransitionTiming timing) {
    public StageTransitionSettings {
        Objects.requireNonNull(timing, "timing");
    }

    public static StageTransitionSettings immediate() {
        return new StageTransitionSettings(StageTransitionTiming.IMMEDIATE);
    }

    public static StageTransitionSettings nextSessionBoundary() {
        return new StageTransitionSettings(StageTransitionTiming.NEXT_SESSION_BOUNDARY);
    }

    public static StageTransitionSettings nextPlayerBoundary() {
        return new StageTransitionSettings(StageTransitionTiming.NEXT_PLAYER_BOUNDARY);
    }
}
