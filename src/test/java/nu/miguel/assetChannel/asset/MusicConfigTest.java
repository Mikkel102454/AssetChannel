package nu.miguel.assetChannel.asset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;

class MusicConfigTest {
    @Test void splitTimelineUsesRequestedBoundariesWithoutEncoderPadding() throws Exception {
        List<Duration> durations = Music.splitTimelineDurations(Duration.ofMillis(12_032), 5, 3);
        assertEquals(List.of(Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ofMillis(2_032)), durations);
        assertEquals(Duration.ofMillis(12_032), durations.stream().reduce(Duration.ZERO, Duration::plus));
    }
    @TempDir Path directory;

    @Test void infersAOnlySingleFile() throws Exception {
        Files.writeString(directory.resolve("config.yml"), "auto-split:\n  enabled: false\n");
        Files.write(directory.resolve("song.ogg"), new byte[]{1});
        assertEquals("theme", Music.load("theme", directory).getId());
    }

    @Test void autoSplitChunksAreBufferedWhileLongTracksAreStreamed() {
        assertFalse(Music.shouldStream(true));
        assertTrue(Music.shouldStream(false));
    }

    @Test void loadsVanillaMusicMuteSetting() throws Exception {
        Files.writeString(directory.resolve("config.yml"), "mute-vanilla-music: true\nauto-split:\n  enabled: false\n");
        Files.write(directory.resolve("song.ogg"), new byte[]{1});

        assertTrue(Music.load("theme", directory).isMuteVanillaMusic());
    }

    @Test void multipleFilesRequireOrder() throws Exception {
        Files.writeString(directory.resolve("config.yml"), "auto-split:\n  enabled: false\n");
        Files.write(directory.resolve("one.ogg"), new byte[]{1});
        Files.write(directory.resolve("two.ogg"), new byte[]{1});
        assertThrows(IllegalArgumentException.class, () -> Music.load("theme", directory));
    }

    @Test void acceptsACompleteOrder() throws Exception {
        Files.write(directory.resolve("one.ogg"), new byte[]{1});
        Files.write(directory.resolve("two.ogg"), new byte[]{1});
        Files.writeString(directory.resolve("config.yml"), "order: [two.ogg, one.ogg]\nauto-split:\n  enabled: false\n");
        assertDoesNotThrow(() -> Music.load("theme", directory));
    }

    @Test void splitIntervalMustBePositive() throws Exception {
        Files.write(directory.resolve("one.ogg"), new byte[]{1});
        Files.writeString(directory.resolve("config.yml"), "auto-split:\n  enabled: true\n  interval-seconds: 0\n");
        assertThrows(IllegalArgumentException.class, () -> Music.load("theme", directory));
    }

    @Test void validatesStageHandoffLead() throws Exception {
        Files.writeString(directory.resolve("config.yml"), "playback:\n  handoff-lead-milliseconds: 251\n");
        Files.write(directory.resolve("theme.ogg"), new byte[]{1});
        assertThrows(IllegalArgumentException.class, () -> Music.load("theme", directory));
    }

    @Test void loadsNamedStagesAndConfiguredDefault() throws Exception {
        Files.writeString(directory.resolve("config.yml"), "default-stage: phase-2\n");
        createStage("phase-1", "calm.ogg", "auto-split:\n  enabled: false\n");
        createStage("phase-2", "intense.ogg", "auto-split:\n  enabled: true\n  interval-seconds: 10\n");

        Music music = Music.load("boss", directory);

        assertEquals("phase-2", music.getDefaultStage());
        assertEquals(List.of("phase-1", "phase-2"), music.getStages().keySet().stream().toList());
        assertTrue(music.getStage("phase-2").autoSplit());
    }

    @Test void stagedLayoutRequiresAtLeastOneStage() throws Exception {
        Files.writeString(directory.resolve("config.yml"), "default-stage: phase-1\n");
        Files.createDirectories(directory.resolve("stages"));
        assertThrows(IllegalArgumentException.class, () -> Music.load("boss", directory));
    }

    @Test void stagedLayoutRequiresAValidDefault() throws Exception {
        Files.writeString(directory.resolve("config.yml"), "default-stage: missing\n");
        createStage("phase-1", "calm.ogg", "auto-split:\n  enabled: false\n");
        assertThrows(IllegalArgumentException.class, () -> Music.load("boss", directory));
    }

    @Test void rejectsMixedLegacyAndStagedLayouts() throws Exception {
        Files.writeString(directory.resolve("config.yml"), "default-stage: phase-1\n");
        Files.write(directory.resolve("root.ogg"), new byte[]{1});
        createStage("phase-1", "calm.ogg", "auto-split:\n  enabled: false\n");
        assertThrows(IllegalArgumentException.class, () -> Music.load("boss", directory));
    }

    @Test void rejectsUppercaseStageIds() throws Exception {
        Files.writeString(directory.resolve("config.yml"), "default-stage: Phase-1\n");
        createStage("Phase-1", "calm.ogg", "auto-split:\n  enabled: false\n");
        assertThrows(IllegalArgumentException.class, () -> Music.load("boss", directory));
    }

    @Test void validatesOrderInsideEachStage() throws Exception {
        Files.writeString(directory.resolve("config.yml"), "default-stage: phase-1\n");
        Path stage = directory.resolve("stages/phase-1");
        Files.createDirectories(stage);
        Files.write(stage.resolve("one.ogg"), new byte[]{1});
        Files.write(stage.resolve("two.ogg"), new byte[]{1});
        Files.writeString(stage.resolve("config.yml"), "order: [two.ogg]\n");
        assertThrows(IllegalArgumentException.class, () -> Music.load("boss", directory));
    }

    private void createStage(String id, String audio, String config) throws Exception {
        Path stage = directory.resolve("stages").resolve(id);
        Files.createDirectories(stage);
        Files.write(stage.resolve(audio), new byte[]{1});
        Files.writeString(stage.resolve("config.yml"), config);
    }
}
