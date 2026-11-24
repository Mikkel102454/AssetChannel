package nu.miguel.assetChannel.pack;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;

class ShaderPreflightTest {
    @TempDir Path root;

    void put(String path, String source) throws Exception {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
    }

    @Test
    void rejectsMissingPostEffectShadersWithAnActionableAssetPath() throws Exception {
        put(
                "film/post_effect/intro.json",
                "{\"targets\":{},\"passes\":[{\"vertex_shader\":\"minecraft:core/screenquad\",\"fragment_shader\":\"film:post/missing\",\"output\":\"minecraft:main\"}]}");
        var error =
                assertThrows(IllegalArgumentException.class, () -> ImportedResources.scan(root));
        assertTrue(error.getMessage().contains("film/post_effect/intro.json"));
        assertTrue(error.getMessage().contains("film/shaders/post/missing.fsh"));
    }

    @Test
    void validatesNestedShaderIncludesAndReportsIncludeCycles() throws Exception {
        put("film/shaders/post/zoom.fsh", "#version 330\n#include <film:one.glsl>\n");
        assertThrows(IllegalArgumentException.class, () -> ImportedResources.scan(root));
        put("film/shaders/include/one.glsl", "#include <film:two.glsl>\n");
        put("film/shaders/include/two.glsl", "#include <film:one.glsl>\n");
        var cycle =
                assertThrows(IllegalArgumentException.class, () -> ImportedResources.scan(root));
        assertTrue(cycle.getMessage().contains("cycle"));
    }

    @Test
    void rejectsInvalidUniformLengthsAndUnknownLocalTargets() throws Exception {
        put(
                "film/post_effect/intro.json",
                "{\"targets\":{},\"passes\":[{\"vertex_shader\":\"minecraft:core/screenquad\",\"fragment_shader\":\"minecraft:post/blit\",\"output\":\"missing_target\"}]}");
        assertThrows(IllegalArgumentException.class, () -> ImportedResources.scan(root));
        put(
                "film/post_effect/intro.json",
                "{\"targets\":{},\"passes\":[{\"vertex_shader\":\"minecraft:core/screenquad\",\"fragment_shader\":\"minecraft:post/blit\",\"output\":\"film:missing_target\"}]}");
        assertThrows(IllegalArgumentException.class, () -> ImportedResources.scan(root));
        put(
                "film/post_effect/intro.json",
                "{\"targets\":{},\"passes\":[{\"vertex_shader\":\"minecraft:core/screenquad\",\"fragment_shader\":\"minecraft:post/blit\",\"output\":\"minecraft:main\",\"uniforms\":{\"BlitConfig\":[{\"type\":\"vec4\",\"value\":[1,1]}]}}]}");
        assertThrows(IllegalArgumentException.class, () -> ImportedResources.scan(root));
    }
}
