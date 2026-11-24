package nu.miguel.assetChannel.api;

import java.time.Duration;
import java.util.Objects;

public record MusicStageAsset(String id, Duration duration, int segmentCount, boolean autoSplit) {
    public MusicStageAsset {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(duration, "duration");
        if (segmentCount < 0)
            throw new IllegalArgumentException("segmentCount must be non-negative");
    }
}
