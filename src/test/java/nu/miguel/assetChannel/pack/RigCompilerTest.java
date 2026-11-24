package nu.miguel.assetChannel.pack;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;

class RigCompilerTest {
    @TempDir Path directory;

    @Test
    void embeddedRigTexturesAreRegisteredInTheNativeItemAtlas() throws Exception {
        var compiled =
                RigCompiler.compile(
                        "learning", Path.of("src/test/resources/rigs/learning.bbmodel"));
        var resources =
                RigCompiler.merge(
                        ImportedResources.prepared(java.util.Map.of()),
                        java.util.List.of(compiled));
        byte[] atlas = resources.sourceFiles().get("minecraft/atlases/items.json");
        assertNotNull(atlas, "A PNG outside item/ is not stitched automatically");
        var json =
                com.google.gson.JsonParser.parseString(
                                new String(atlas, java.nio.charset.StandardCharsets.UTF_8))
                        .getAsJsonObject();
        assertTrue(
                json.getAsJsonArray("sources").asList().stream()
                        .anyMatch(raw ->
                                raw.getAsJsonObject()
                                        .get("resource")
                                        .getAsString()
                                        .equals("assetchannel:generated/rigs/learning/texture_0")));
        var model =
                com.google.gson.JsonParser.parseString(
                                new String(
                                        resources
                                                .sourceFiles()
                                                .get(
                                                        "assetchannel/models/generated/rigs/learning/part_0.json"),
                                        java.nio.charset.StandardCharsets.UTF_8))
                        .getAsJsonObject();
        var binding = model.getAsJsonObject("textures").getAsJsonObject("0");
        assertEquals("minecraft:items", binding.get("atlas").getAsString());
        assertEquals(
                "assetchannel:generated/rigs/learning/texture_0",
                binding.get("sprite").getAsString());
    }

    @Test
    void importsLearningModelWithV5GroupDefinitionsAndIgnoresEmptyAnimationPlaceholders()
            throws Exception {
        Path source = Path.of("src/test/resources/rigs/learning.bbmodel");
        byte[] original = Files.readAllBytes(source);
        var rig = RigCompiler.compile("learning", source).rig();
        assertEquals(20, rig.parts().size());
        assertEquals(25, rig.bones().size());
        assertEquals(java.util.Set.of("idle", "walk"), rig.clips().keySet());
        assertEquals(2, rig.clips().get("walk").length());
        var body =
                rig.bones().stream().filter(b -> b.name().equals("body")).findFirst().orElseThrow();
        assertEquals(3.25, body.pivot().y());
        assertTrue(
                rig.parts().stream()
                        .allMatch(
                                p -> rig.bones().stream().anyMatch(b -> b.id().equals(p.bone()))));
        assertArrayEquals(original, Files.readAllBytes(source));
    }

    @Test
    void looseCubesHaveARealRootAndInflationChangesBoundsWithoutChangingTheSource()
            throws Exception {
        Path source = directory.resolve("loose.bbmodel");
        String text =
                """
                {"elements":[{"uuid":"cube","from":[0,0,0],"to":[8,8,8],"inflate":1,"faces":{}}],"outliner":["cube"]}
                """;
        Files.writeString(source, text);
        var compiled = RigCompiler.compile("loose", source);
        assertEquals("__root", compiled.rig().bones().getFirst().id());
        assertEquals("__root", compiled.rig().parts().getFirst().bone());
        var resources =
                RigCompiler.merge(
                        ImportedResources.prepared(java.util.Map.of()),
                        java.util.List.of(compiled));
        var model =
                com.google.gson.JsonParser.parseString(
                                new String(
                                        resources
                                                .sourceFiles()
                                                .get(
                                                        "assetchannel/models/generated/rigs/loose/part_0.json"),
                                        java.nio.charset.StandardCharsets.UTF_8))
                        .getAsJsonObject();
        var cube = model.getAsJsonArray("elements").get(0).getAsJsonObject();
        assertEquals(7, cube.getAsJsonArray("from").get(0).getAsDouble());
        assertEquals(17, cube.getAsJsonArray("to").get(0).getAsDouble());
        assertEquals(text, Files.readString(source));
    }

    @Test
    void excludedCubesAndGroupsAreNotRendered() throws Exception {
        Path source = directory.resolve("excluded.bbmodel");
        Files.writeString(
                source,
                """
                {"elements":[{"uuid":"a","export":false},{"uuid":"b"}],
                 "outliner":["a",{"uuid":"group","name":"group","export":false,"children":["b"]}]}
                """);
        assertTrue(RigCompiler.compile("excluded", source).rig().parts().isEmpty());
    }

    @Test
    void rejectsRescaleAndDelayedAnimationsInsteadOfSilentlyChangingThem() throws Exception {
        Path source = directory.resolve("unsupported.bbmodel");
        Files.writeString(
                source,
                """
                {"elements":[{"uuid":"a","from":[0,0,0],"to":[8,8,8],"rescale":true}]}
                """);
        assertTrue(
                assertThrows(
                                IllegalArgumentException.class,
                                () -> RigCompiler.compile("bad", source))
                        .getMessage()
                        .contains("rescale"));
        Files.writeString(
                source,
                """
                {"animations":[{"name":"delayed","length":1,"start_delay":"2"}]}
                """);
        assertTrue(
                assertThrows(
                                IllegalArgumentException.class,
                                () -> RigCompiler.compile("bad", source))
                        .getMessage()
                        .contains("start_delay"));
    }

    @Test
    void preservesBezierHandles() throws Exception {
        Path source = directory.resolve("curve.bbmodel");
        Files.writeString(
                source,
                """
                {"elements":[],"textures":[],"outliner":[{"uuid":"root","name":"root","children":[]}],
                "animations":[{"name":"curve","length":1,"animators":{"root":{"keyframes":[
                {"channel":"position","time":0,"interpolation":"bezier","bezier_right_time":[0.25,0.25,0.25],"bezier_right_value":[0,2,0],"data_points":[{"x":0,"y":0,"z":0}]}]}}}]}
                """);
        var frame =
                RigCompiler.compile("curve", source)
                        .rig()
                        .clips()
                        .get("curve")
                        .channels()
                        .getFirst()
                        .frames()
                        .getFirst();
        assertEquals(2, frame.rightValue().y());
    }

    @Test
    void preservesHierarchyAndRejectsExpressionsInsteadOfSilentlyChangingAnimation()
            throws Exception {
        Path source = directory.resolve("guide.bbmodel");
        Files.writeString(
                source,
                """
                {"elements":[],"textures":[],"outliner":[{"uuid":"root","name":"body","origin":[0,8,0],"children":[]}],
                 "animations":[{"name":"talk","length":1,"loop":"loop","animators":{"root":{"keyframes":[
                 {"channel":"rotation","time":0,"interpolation":"linear","data_points":[{"x":0,"y":0,"z":0}]},
                 {"channel":"rotation","time":1,"interpolation":"linear","data_points":[{"x":0,"y":45,"z":0}]}]}}}]}
                """);
        var rig = RigCompiler.compile("guide", source).rig();
        assertEquals(8, rig.bones().getFirst().pivot().y());
        assertEquals(2, rig.clips().get("talk").channels().getFirst().frames().size());
        Files.writeString(
                source, Files.readString(source).replace("\"y\":45", "\"y\":\"query.life_time\""));
        assertThrows(IllegalArgumentException.class, () -> RigCompiler.compile("guide", source));
    }
}
