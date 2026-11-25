package nu.miguel.assetChannel.pack;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import nu.miguel.assetChannel.asset.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

class ClientEffectsTest {
    static final Path SAMPLE = Path.of("examples/cinematic-pack");
    @TempDir Path root;

    @Test
    void generatesSparseControlsAndSharedRenderersDirectlyFromImportedShaders() throws Exception {
        var zoom = EffectCurve.load("zoom", SAMPLE.resolve("definitions/effects/zoom.yml"));
        var fade = EffectCurve.load("fade", SAMPLE.resolve("definitions/effects/fade.yml"));
        var imported = ImportedResources.scan(SAMPLE.resolve("assets"));
        var prepared = ClientEffects.prepare(imported, List.of(zoom, fade));
        var client = zoom.descriptor().client();
        var shared = json(prepared, client.composition().renderer(3));
        assertEquals(2, shared.getAsJsonArray("passes").size());
        var render = shared.getAsJsonArray("passes").get(0).getAsJsonObject();
        assertEquals(
                client.composition().vertexShader().asString(),
                render.get("vertex_shader").getAsString());
        assertEquals(
                client.composition().fragmentShader().asString(),
                render.get("fragment_shader").getAsString());
        assertEquals(5, render.getAsJsonObject("uniforms").getAsJsonArray("ChannelBounds").size());
        var reset =
                json(prepared, fade.descriptor().client().reset())
                        .getAsJsonArray("passes")
                        .get(0)
                        .getAsJsonObject();
        var parameters =
                reset.getAsJsonObject("uniforms")
                        .getAsJsonArray("ControlConfig")
                        .get(0)
                        .getAsJsonObject()
                        .getAsJsonArray("value");
        assertEquals(22, parameters.get(0).getAsInt());
        assertEquals(22, parameters.get(2).getAsInt());
        assertEquals(
                imported.sourceFiles().keySet(),
                prepared.sourceFiles().keySet().stream()
                        .filter(p -> !p.contains("/post_effect/generated/"))
                        .collect(java.util.stream.Collectors.toSet()));
        assertTrue(
                prepared.sourceFiles().keySet().stream().noneMatch(p -> p.endsWith("/0/0.json")));
    }

    @Test
    void rejectsConflictingCompositorGroupsBeforePublication() throws Exception {
        String source = Files.readString(SAMPLE.resolve("definitions/effects/zoom.yml"));
        Path other = root.resolve("other.yml");
        for (String conflicting :
                List.of(
                        source.replace(
                                "copy-vertex: minecraft:core/screenquad",
                                "copy-vertex: minecraft:core/other"),
                        source.replace("min: 1, max: 4", "min: 1, max: 3"))) {
            Files.writeString(other, conflicting);
            var curves =
                    List.<Asset>of(
                            EffectCurve.load(
                                    "first", SAMPLE.resolve("definitions/effects/zoom.yml")),
                            EffectCurve.load("second", other));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            ClientEffects.prepare(
                                    ImportedResources.scan(SAMPLE.resolve("assets")), curves));
        }
    }

    @Test
    void rejectsMissingImportedShadersAndUnsupportedIncludes() throws Exception {
        var curve = EffectCurve.load("zoom", SAMPLE.resolve("definitions/effects/zoom.yml"));
        var source = ImportedResources.scan(SAMPLE.resolve("assets"));
        var files = new TreeMap<>(source.sourceFiles());
        files.remove("cinematic/shaders/post/composite.vsh");
        assertThrows(
                IllegalArgumentException.class,
                () -> ClientEffects.prepare(ImportedResources.prepared(files), List.of(curve)));
        files.put(
                "cinematic/shaders/post/composite.vsh",
                "#version 330\n#moj_import <minecraft:globals.glsl>\n"
                        .getBytes(StandardCharsets.UTF_8));
        var error =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                ClientEffects.prepare(
                                        ImportedResources.prepared(files), List.of(curve)));
        assertTrue(error.getMessage().contains("composite.vsh"));
        assertTrue(error.getMessage().contains("#include"));
    }

    private JsonObject json(ImportedResources resources, net.kyori.adventure.key.Key key) {
        String path = key.namespace() + "/post_effect/" + key.value() + ".json";
        return JsonParser.parseString(
                        new String(
                                Objects.requireNonNull(resources.sourceFiles().get(path)),
                                StandardCharsets.UTF_8))
                .getAsJsonObject();
    }
}
