package nu.miguel.assetChannel.api;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

class SoundtrackAssetTest {
    @Test
    void exposesAnImmutableStageMap() {
        Map<String, MusicStageAsset> input = new LinkedHashMap<>();
        input.put("phase-1", new MusicStageAsset("phase-1", Duration.ofSeconds(2), 1, false));
        SoundtrackAsset asset =
                new SoundtrackAsset("boss", Duration.ofSeconds(2), 1, false, "phase-1", input);
        input.clear();

        assertEquals("phase-1", asset.defaultStage());
        assertTrue(asset.stages().containsKey("phase-1"));
        assertFalse(asset.muteVanillaMusic());
        assertThrows(UnsupportedOperationException.class, () -> asset.stages().clear());
    }

    @Test
    void exposesVanillaMusicMuteSetting() {
        MusicStageAsset stage = new MusicStageAsset("default", Duration.ofSeconds(2), 1, false);
        SoundtrackAsset asset =
                new SoundtrackAsset(
                        "boss",
                        Duration.ofSeconds(2),
                        1,
                        false,
                        "default",
                        Map.of("default", stage),
                        true);
        assertTrue(asset.muteVanillaMusic());
    }

    @Test
    void exposesOtherMusicMuteSetting() {
        MusicStageAsset stage = new MusicStageAsset("default", Duration.ofSeconds(2), 1, false);
        SoundtrackAsset asset =
                new SoundtrackAsset(
                        "boss",
                        Duration.ofSeconds(2),
                        1,
                        false,
                        "default",
                        Map.of("default", stage),
                        false,
                        true);
        assertTrue(asset.muteOtherMusic());
    }

    @Test
    void legacyConstructorCreatesTheImplicitDefaultStage() {
        SoundtrackAsset asset = new SoundtrackAsset("lobby", Duration.ofSeconds(3), 2, false);
        assertEquals("default", asset.defaultStage());
        assertEquals(2, asset.stages().get("default").segmentCount());
    }

    @Test
    void defaultStageMustExist() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SoundtrackAsset("boss", Duration.ZERO, 0, false, "missing", Map.of()));
    }
}
