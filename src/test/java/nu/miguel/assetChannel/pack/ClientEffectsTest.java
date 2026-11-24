package nu.miguel.assetChannel.pack;

import static org.junit.jupiter.api.Assertions.*;

import nu.miguel.assetChannel.asset.EffectCurve;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.*;

class ClientEffectsTest {
    @Test
    void importedZoomShadersUseTheCurrentClientIncludeSyntax() throws Exception {
        for (String file : List.of("control_pixel.vsh", "interpolated_zoom.fsh")) {
            String shader =
                    java.nio.file.Files.readString(
                            Path.of("examples/cinematic-pack/assets/cinematic/shaders/post", file));
            assertFalse(
                    shader.contains("#moj_import"),
                    "26.3 passes native #include directives to its shader compiler: " + file);
            assertTrue(shader.contains("#include <minecraft:globals.glsl>"));
        }
    }

    @org.junit.jupiter.api.io.TempDir Path root;

    @Test
    void rejectsLegacyShaderDirectivesWithTheSourcePath() throws Exception {
        var curve =
                EffectCurve.load(
                        "zoom", Path.of("examples/cinematic-pack/definitions/effects/zoom.yml"));
        var source = ImportedResources.scan(Path.of("examples/cinematic-pack/assets"));
        var files = new TreeMap<>(source.sourceFiles());
        files.put(
                "cinematic/shaders/post/control_pixel.vsh",
                "#version 330\n#moj_import <minecraft:globals.glsl>\n"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var error =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                ClientEffects.prepare(
                                        ImportedResources.prepared(files), List.of(curve)));
        assertTrue(error.getMessage().contains("cinematic/shaders/post/control_pixel.vsh"));
        assertTrue(error.getMessage().contains("#include"));
    }

    @Test
    void rejectsMissingImportedAnimationShadersBeforePublication() throws Exception {
        var config =
                java.nio.file.Files.readString(
                                Path.of("examples/cinematic-pack/definitions/effects/zoom.yml"))
                        .replace("cinematic:post/interpolated_zoom", "missing:zoom");
        Path file = root.resolve("zoom.yml");
        java.nio.file.Files.writeString(file, config);
        var curve = EffectCurve.load("zoom", file);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ClientEffects.prepare(
                                ImportedResources.scan(Path.of("examples/cinematic-pack/assets")),
                                List.of(curve)));
    }

    @Test
    void derivesTransportFromImportedShaderReferencesWithoutEmbeddingShaderSource()
            throws Exception {
        var curve =
                EffectCurve.load(
                        "zoom", Path.of("examples/cinematic-pack/definitions/effects/zoom.yml"));
        assertNotNull(curve.descriptor().client());
        var source = ImportedResources.scan(Path.of("examples/cinematic-pack/assets"));
        var compiled = ClientEffects.prepare(source, List.of(curve));
        assertEquals(
                source.sourceFiles().keySet(),
                compiled.sourceFiles().keySet().stream()
                        .filter(p -> !p.contains("/post_effect/generated/client/"))
                        .collect(java.util.stream.Collectors.toSet()));
        String renderer =
                curve.descriptor().client().renderer().asString().replace(":", "/post_effect/")
                        + ".json";
        assertTrue(compiled.sourceFiles().containsKey(renderer));
        var json =
                com.google.gson.JsonParser.parseString(
                                new String(compiled.sourceFiles().get(renderer)))
                        .getAsJsonObject();
        assertEquals(2, json.getAsJsonArray("passes").size());
        assertEquals(
                curve.descriptor().client().fragmentShader().asString(),
                json.getAsJsonArray("passes")
                        .get(0)
                        .getAsJsonObject()
                        .get("fragment_shader")
                        .getAsString());
    }
}
