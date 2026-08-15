package nu.miguel.assetChannel.api;

import java.time.Duration;
import java.util.*;

public record SoundtrackAsset(String id, Duration duration, int segmentCount, boolean autoSplit,
                              String defaultStage, Map<String, MusicStageAsset> stages,
                              boolean muteVanillaMusic) {
    public SoundtrackAsset {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(defaultStage, "defaultStage");
        stages = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(stages, "stages")));
        if (!stages.containsKey(defaultStage)) throw new IllegalArgumentException("defaultStage must exist in stages");
    }

    public SoundtrackAsset(String id, Duration duration, int segmentCount, boolean autoSplit) {
        this(id, duration, segmentCount, autoSplit, "default",
                Map.of("default", new MusicStageAsset("default", duration, segmentCount, autoSplit)), false);
    }

    public SoundtrackAsset(String id, Duration duration, int segmentCount, boolean autoSplit,
                           String defaultStage, Map<String, MusicStageAsset> stages) {
        this(id, duration, segmentCount, autoSplit, defaultStage, stages, false);
    }
}
