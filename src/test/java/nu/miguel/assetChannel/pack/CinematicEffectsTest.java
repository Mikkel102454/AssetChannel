package nu.miguel.assetChannel.pack;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;

class CinematicEffectsTest {
    @TempDir Path empty;

    @Test
    void emptyImportsDoNotInventFontsOrShaders() throws Exception {
        assertTrue(ImportedResources.scan(empty).sourceFiles().isEmpty());
    }

    @Test
    void sampleEffectsAreRegularImportedAssets() throws Exception {
        var result = ImportedResources.scan(Path.of("examples/cinematic-pack/assets"));
        var bytes = result.sourceFiles().get("assetchannel/post_effect/cinematic/zoom/123.json");
        assertNotNull(bytes);
        var effect =
                com.google.gson.JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8))
                        .getAsJsonObject();
        assertEquals(
                1.23,
                effect.getAsJsonArray("passes")
                        .get(0)
                        .getAsJsonObject()
                        .getAsJsonObject("uniforms")
                        .getAsJsonArray("CinematicConfig")
                        .get(0)
                        .getAsJsonObject()
                        .getAsJsonArray("value")
                        .get(0)
                        .getAsDouble());
        assertTrue(
                result.resources().stream()
                        .anyMatch(r ->
                                r.key().value().equals("post_effect/cinematic/grayscale.json")));
    }
}
