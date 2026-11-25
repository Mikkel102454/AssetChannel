package nu.miguel.assetChannel.asset;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;

class EffectCurveTest {
    @TempDir Path root;

    private String definition() {
        return """
        kind: ZOOM
        range: {min: 1, max: 4}
        neutral: 1
        client:
          protocol: clock-composite-v1
          group: film:screen
          writer-vertex: film:writer
          writer-fragment: film:value
          vertex-shader: film:composite
          fragment-shader: film:composite
          copy-vertex: minecraft:core/screenquad
          copy-fragment: minecraft:post/blit
        """;
    }

    @Test
    void importsCompositorWithAnExplicitRangeAndNoPresetFiles() throws Exception {
        Path file = root.resolve("current.yml");
        Files.writeString(file, definition());
        var curve = EffectCurve.load("camera", file).descriptor();
        assertEquals(1, curve.minimum());
        assertEquals(4, curve.maximum());
        assertTrue(curve.client().supportsRoll());
        assertEquals("film:composite", curve.client().composition().fragmentShader().asString());
        assertEquals(
                "assetchannel:generated/composite/film/screen",
                curve.client().composition().prefix().asString());
    }

    @Test
    void rejectsPresetDefinitionsOldProtocolsAndInvalidRanges() throws Exception {
        Path file = root.resolve("invalid.yml");
        for (String invalid :
                java.util.List.of(
                        definition() + "profiles: {'1': film:wide}\n",
                        definition().replace("clock-composite-v1", "clock-zoom-v3"),
                        definition().replace("min: 1, max: 4", "min: 4, max: 1"),
                        definition().replace("neutral: 1", "neutral: 5"),
                        definition().replace("range: {min: 1, max: 4}\n", ""))) {
            Files.writeString(file, invalid);
            assertThrows(IllegalArgumentException.class, () -> EffectCurve.load("invalid", file));
        }
    }

    @Test
    void importsAllScalarChannelsWithoutPresetKeys() throws Exception {
        Path file = root.resolve("scalar.yml");
        for (String kind : java.util.List.of("FADE", "LETTERBOX", "DESATURATION", "VIGNETTE")) {
            Files.writeString(
                    file,
                    definition()
                            .replace("kind: ZOOM", "kind: " + kind)
                            .replace("min: 1, max: 4", "min: 0, max: 1")
                            .replace("neutral: 1", "neutral: 0"));
            var curve = EffectCurve.load("screen", file).descriptor();
            assertEquals(kind, curve.kind().name());
            assertEquals(15, curve.client().controlBytes());
            assertFalse(curve.client().supportsRoll());
        }
    }
}
