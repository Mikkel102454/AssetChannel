package nu.miguel.assetChannel.util;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nu.miguel.assetChannel.Main;
import nu.miguel.assetChannel.asset.*;
import nu.miguel.assetChannel.pack.*;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.zip.ZipFile;

class ContentPackIntegrationTest {
    @TempDir Path root;
    private Plugin previousPlugin;
    private java.io.File previousData;
    private boolean previousEnsured;

    @BeforeEach
    void configurePlugin() {
        previousPlugin = Main.plugin;
        previousData = PathResolver.dataPath;
        previousEnsured = PathResolver.hasBeenEnsured;
        Main.plugin = mock(Plugin.class);
        when(Main.plugin.getDataFolder()).thenReturn(root.toFile());
        when(Main.plugin.getDataPath()).thenReturn(root);
    }

    @AfterEach
    void restorePlugin() {
        Main.plugin = previousPlugin;
        PathResolver.dataPath = previousData;
        PathResolver.hasBeenEnsured = previousEnsured;
    }

    @Test
    void loadsDefinitionsAndSourcesAcrossPacksWithoutChangingIdsOrLeakingSources()
            throws Exception {
        NativePackFixture.create(root.resolve("packs/main/assets"));
        put("packs/main/definitions/blocks/warning_block.yml", "model: demo:cube\n");
        put(
                "packs/extra/definitions/hud/notification/banner.yml",
                "surface: action-bar\nlayout: {content: 'Ready'}\n");
        Path png = root.resolve("packs/extra/sources/icons/ui/rank.png");
        Files.createDirectories(png.getParent());
        javax.imageio.ImageIO.write(
                new java.awt.image.BufferedImage(
                        16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB),
                "png",
                png.toFile());
        put("packs/extra/sources/icons/ui/rank.yml", "height: 16\nascent: 13\n");
        put("packs/extra/sources/fonts/ui/title.ttf", "font bytes");
        PathResolver.ensurePaths();
        var assets = PackDeveloper.createPack();
        assertTrue(
                assets.stream()
                        .anyMatch(a ->
                                a instanceof BlockDefinition && a.getId().equals("warning_block")));
        assertTrue(
                assets.stream()
                        .anyMatch(
                                a -> a instanceof Hud && a.getId().equals("notification.banner")));
        assertTrue(assets.stream().anyMatch(a -> a instanceof Icon && a.getId().equals("ui.rank")));
        assertTrue(
                assets.stream().anyMatch(a -> a instanceof Font && a.getId().equals("ui.title")));
        try (var zip = new ZipFile(root.resolve("generated/pack.zip").toFile())) {
            assertNotNull(zip.getEntry("assets/demo/items/cube.json"));
            assertNotNull(zip.getEntry("assets/assetchannel/font/ui/title.json"));
            assertTrue(
                    zip.stream()
                            .noneMatch(e ->
                                    e.getName().contains("definitions/")
                                            || e.getName().contains("sources/")));
        }
        assertFalse(Files.exists(root.resolve("pack.zip")));
    }

    @Test
    void resolvesNativeReferencesAcrossPacks() throws Exception {
        put(
                "packs/main/assets/demo/items/cube.json",
                "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"shared:cube\"}}");
        put(
                "packs/shared/assets/shared/models/cube.json",
                "{\"textures\":{\"particle\":\"shared:face\"}}");
        put("packs/shared/assets/shared/textures/face.png", "texture bytes");
        PathResolver.ensurePaths();
        var assets = PackDeveloper.createPack();
        var imported =
                assets.stream()
                        .filter(ImportedResources.class::isInstance)
                        .map(ImportedResources.class::cast)
                        .findFirst()
                        .orElseThrow();
        assertEquals("demo:cube", imported.models().getFirst().id());
    }

    @Test
    void rejectsDuplicateDefinitionsAcrossPacksAndRetainsPublishedPack() throws Exception {
        NativePackFixture.create(root.resolve("packs/main/assets"));
        put("packs/main/definitions/blocks/warning_block.yml", "model: demo:cube\n");
        PathResolver.ensurePaths();
        PackDeveloper.createPack();
        byte[] previous = Files.readAllBytes(root.resolve("generated/pack.zip"));
        put("packs/extra/definitions/blocks/warning_block.yml", "model: demo:cube\n");
        var error = assertThrows(IllegalArgumentException.class, PackDeveloper::createPack);
        assertTrue(error.getMessage().contains("warning_block"));
        assertTrue(error.getMessage().contains("main"));
        assertTrue(error.getMessage().contains("extra"));
        assertArrayEquals(previous, Files.readAllBytes(root.resolve("generated/pack.zip")));
    }

    @Test
    void rejectsDuplicateNativeResourcePathsAcrossPacks() throws Exception {
        put("packs/main/assets/demo/models/cube.json", "{}");
        put("packs/extra/assets/demo/models/cube.json", "{}");
        PathResolver.ensurePaths();
        var error = assertThrows(IllegalArgumentException.class, PackDeveloper::createPack);
        assertTrue(error.getMessage().contains("demo/models/cube.json"));
        assertTrue(error.getMessage().contains("main"));
        assertTrue(error.getMessage().contains("extra"));
    }

    @Test
    void keepsSameNamedOverlaysIndependentAndCombinesMetadata() throws Exception {
        for (String name : java.util.List.of("main", "extra")) {
            put("packs/" + name + "/assets/demo/models/" + name + ".json", "{}");
            put(
                    "packs/" + name + "/variant/assets/demo/items/" + name + ".json",
                    "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"demo:" + name + "\"}}");
            put(
                    "packs/" + name + "/pack.mcmeta",
                    """
                    {"pack":{"description":"%s"},"language":{"%s":{"name":"Test","region":"Test"}},
                     "overlays":{"entries":[{"min_format":[97,1],"max_format":[97,1],"directory":"variant"}]}}
                    """
                            .formatted(name, name));
        }
        PathResolver.ensurePaths();
        var assets = PackDeveloper.createPack();
        var imported =
                assets.stream()
                        .filter(ImportedResources.class::isInstance)
                        .map(ImportedResources.class::cast)
                        .findFirst()
                        .orElseThrow();
        assertEquals(
                java.util.List.of("demo:extra", "demo:main"),
                imported.models().stream().map(m -> m.id()).toList());
        try (var zip = new ZipFile(root.resolve("generated/pack.zip").toFile())) {
            var meta =
                    com.google.gson.JsonParser.parseString(
                                    new String(
                                            zip.getInputStream(zip.getEntry("pack.mcmeta"))
                                                    .readAllBytes(),
                                            java.nio.charset.StandardCharsets.UTF_8))
                            .getAsJsonObject();
            assertEquals("main", meta.getAsJsonObject("pack").get("description").getAsString());
            assertEquals(2, meta.getAsJsonObject("language").size());
            var entries = meta.getAsJsonObject("overlays").getAsJsonArray("entries");
            assertEquals(2, entries.size());
            assertNotEquals(
                    entries.get(0).getAsJsonObject().get("directory"),
                    entries.get(1).getAsJsonObject().get("directory"));
            for (var entry : entries) {
                String directory = entry.getAsJsonObject().get("directory").getAsString();
                assertTrue(
                        zip.stream()
                                .anyMatch(e ->
                                        e.getName().startsWith(directory + "/assets/demo/items/")));
            }
        }
    }

    private void put(String relative, String text) throws Exception {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }

    @Test
    void generatesReservedBlockStateModelsAndKeepsNormalNoteBlocksAsFallback() throws Exception {
        NativePackFixture.create(root.resolve("packs/main/assets"));
        put(
                "packs/main/definitions/blocks/warning_block.yml",
                "model: demo:cube\n"
                    + "block-state: minecraft:note_block[instrument=pling,note=24,powered=false]\n"
                    + "block-model: demo:ui/cube\n");
        PathResolver.ensurePaths();
        PackDeveloper.createPack();
        try (var zip = new ZipFile(root.resolve("generated/pack.zip").toFile())) {
            var entry = zip.getEntry("assets/minecraft/blockstates/note_block.json");
            assertNotNull(entry);
            var json =
                    com.google.gson.JsonParser.parseString(
                                    new String(
                                            zip.getInputStream(entry).readAllBytes(),
                                            java.nio.charset.StandardCharsets.UTF_8))
                            .getAsJsonObject();
            var parts = json.getAsJsonArray("multipart");
            assertEquals(2, parts.size());
            assertEquals(
                    "demo:ui/cube",
                    parts.get(0)
                            .getAsJsonObject()
                            .getAsJsonObject("apply")
                            .get("model")
                            .getAsString());
            assertEquals(
                    "pling",
                    parts.get(0)
                            .getAsJsonObject()
                            .getAsJsonObject("when")
                            .get("instrument")
                            .getAsString());
            var fallback = parts.get(1).getAsJsonObject();
            assertEquals(
                    "minecraft:block/note_block",
                    fallback.getAsJsonObject("apply").get("model").getAsString());
            assertTrue(
                    fallback
                            .getAsJsonObject("when")
                            .getAsJsonArray("AND")
                            .get(0)
                            .getAsJsonObject()
                            .getAsJsonArray("OR")
                            .asList()
                            .stream()
                            .anyMatch(e ->
                                    e.getAsJsonObject().has("note")
                                            && e.getAsJsonObject()
                                                    .get("note")
                                                    .getAsString()
                                                    .equals("!24")));
        }
    }

    @Test
    void rejectsDuplicateReservedStatesAndImportedStateFileConflicts() throws Exception {
        NativePackFixture.create(root.resolve("packs/main/assets"));
        String definition =
                "model: demo:cube\n"
                    + "block-state: minecraft:note_block[instrument=pling,note=24,powered=false]\n"
                    + "block-model: demo:ui/cube\n";
        put("packs/main/definitions/blocks/warning.yml", definition);
        put(
                "packs/main/definitions/blocks/other.yml",
                definition.replace(
                        "instrument=pling,note=24,powered=false",
                        "powered=false,note=24,instrument=pling"));
        PathResolver.ensurePaths();
        assertThrows(IllegalArgumentException.class, PackDeveloper::createPack);
        Files.delete(root.resolve("packs/main/definitions/blocks/other.yml"));
        put(
                "packs/main/assets/minecraft/blockstates/note_block.json",
                "{\"variants\":{\"\":{\"model\":\"minecraft:block/note_block\"}}}");
        assertThrows(IllegalArgumentException.class, PackDeveloper::createPack);
    }

    @Test
    void rejectsMissingNativeBlockGeometryBeforePublication() throws Exception {
        NativePackFixture.create(root.resolve("packs/main/assets"));
        put(
                "packs/main/definitions/blocks/warning.yml",
                "model: demo:cube\n"
                    + "block-state: minecraft:note_block[instrument=pling,note=24,powered=false]\n"
                    + "block-model: demo:missing\n");
        PathResolver.ensurePaths();
        assertThrows(IllegalArgumentException.class, PackDeveloper::createPack);
        assertFalse(Files.exists(root.resolve("generated/pack.zip")));
    }

    @Test
    void simpleBlockGeneratesItemAndAtlasesWithoutEditingSourcesAndKeepsItsAssignedState()
            throws Exception {
        simpleModel();
        put("packs/main/definitions/blocks/warning.yml", "render: block\nmodel: demo:warning\n");
        PathResolver.ensurePaths();
        var first = PackDeveloper.createPack();
        var state =
                first.stream()
                        .filter(BlockDefinition.class::isInstance)
                        .map(BlockDefinition.class::cast)
                        .findFirst()
                        .orElseThrow()
                        .descriptor()
                        .blockState();
        assertNotNull(state);
        try (var zip = new ZipFile(root.resolve("generated/pack.zip").toFile())) {
            assertNotNull(zip.getEntry("assets/demo/items/warning.json"));
            assertNotNull(zip.getEntry("assets/minecraft/atlases/items.json"));
            assertNotNull(zip.getEntry("assets/minecraft/atlases/blocks.json"));
            assertNotNull(zip.getEntry("assets/minecraft/blockstates/note_block.json"));
        }
        assertFalse(Files.exists(root.resolve("packs/main/assets/demo/items/warning.json")));
        assertFalse(Files.exists(root.resolve("packs/main/assets/minecraft/atlases/items.json")));
        assertTrue(Files.isRegularFile(root.resolve("storage/block-states.json")));
        put("packs/extra/definitions/blocks/aaa.yml", "render: block\nmodel: demo:warning\n");
        var second = PackDeveloper.createPack();
        assertEquals(
                state,
                second.stream()
                        .filter(BlockDefinition.class::isInstance)
                        .map(BlockDefinition.class::cast)
                        .filter(b -> b.getId().equals("warning"))
                        .findFirst()
                        .orElseThrow()
                        .descriptor()
                        .blockState());
    }

    @Test
    void simpleParticlesUsePixelCoordinatesAndGeneratedWrappersPreserveOriginalModels()
            throws Exception {
        simpleModel();
        Path modelPath = root.resolve("packs/main/assets/demo/models/warning.json");
        byte[] originalModel = Files.readAllBytes(modelPath);
        put(
                "packs/main/definitions/blocks/warning.yml",
                "render: block\n"
                        + "model: demo:warning\n"
                        + "particles: {texture: 'demo:sheet', region: [4, 5, 8, 8]}\n");
        Path sheet = root.resolve("packs/main/assets/demo/textures/sheet.png");
        javax.imageio.ImageIO.write(
                new java.awt.image.BufferedImage(
                        32, 32, java.awt.image.BufferedImage.TYPE_INT_ARGB),
                "png",
                sheet.toFile());
        PathResolver.ensurePaths();
        var assets = PackDeveloper.createPack();
        var descriptor =
                assets.stream()
                        .filter(BlockDefinition.class::isInstance)
                        .map(BlockDefinition.class::cast)
                        .findFirst()
                        .orElseThrow()
                        .descriptor();
        assertEquals("demo:warning", descriptor.model());
        assertNotEquals("demo:warning", descriptor.blockModel());
        assertNotEquals("demo:warning", descriptor.particleModel());
        assertArrayEquals(originalModel, Files.readAllBytes(modelPath));
        try (var zip = new ZipFile(root.resolve("generated/pack.zip").toFile())) {
            var atlas =
                    com.google.gson.JsonParser.parseString(
                                    new String(
                                            zip.getInputStream(
                                                            zip.getEntry(
                                                                    "assets/minecraft/atlases/blocks.json"))
                                                    .readAllBytes(),
                                            java.nio.charset.StandardCharsets.UTF_8))
                            .getAsJsonObject();
            var crop =
                    atlas.getAsJsonArray("sources").asList().stream()
                            .map(e -> e.getAsJsonObject())
                            .filter(s -> s.get("type").getAsString().equals("minecraft:unstitch"))
                            .findFirst()
                            .orElseThrow();
            assertEquals(32, crop.get("divisor_x").getAsInt());
            var region = crop.getAsJsonArray("regions").get(0).getAsJsonObject();
            assertEquals(4, region.get("x").getAsInt());
            assertEquals(5, region.get("y").getAsInt());
            assertEquals(8, region.get("width").getAsInt());
        }
    }

    @Test
    void simpleDisplayDoesNotReserveAWorldBlockStateAndInvalidCropRetainsPreviousPack()
            throws Exception {
        simpleModel();
        String config = "render: display\nmodel: demo:warning\n";
        put("packs/main/definitions/blocks/warning.yml", config);
        PathResolver.ensurePaths();
        var assets = PackDeveloper.createPack();
        assertNull(
                assets.stream()
                        .filter(BlockDefinition.class::isInstance)
                        .map(BlockDefinition.class::cast)
                        .findFirst()
                        .orElseThrow()
                        .descriptor()
                        .blockState());
        byte[] previous = Files.readAllBytes(root.resolve("generated/pack.zip"));
        put(
                "packs/main/definitions/blocks/warning.yml",
                config + "particles: {texture: 'demo:warning', region: [12, 12, 16, 16]}\n");
        assertThrows(IllegalArgumentException.class, PackDeveloper::createPack);
        assertArrayEquals(previous, Files.readAllBytes(root.resolve("generated/pack.zip")));
    }

    private void simpleModel() throws Exception {
        put(
                "packs/main/assets/demo/models/warning.json",
                "{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"demo:warning\",\"particle\":\"demo:warning\"}}");
        Path texture = root.resolve("packs/main/assets/demo/textures/warning.png");
        Files.createDirectories(texture.getParent());
        javax.imageio.ImageIO.write(
                new java.awt.image.BufferedImage(
                        16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB),
                "png",
                texture.toFile());
    }

    @Test
    void simpleSetupPreservesManualMappingsWhileAddingBlockSafeAliases() throws Exception {
        simpleModel();
        var texture = root.resolve("packs/main/assets/demo/textures/warning.png");
        Files.copy(texture, texture.resolveSibling("held.png"));
        Files.copy(texture, texture.resolveSibling("world.png"));
        String items =
                "{\"sources\":[{\"type\":\"minecraft:single\",\"resource\":\"demo:held\",\"sprite\":\"demo:warning\"}]}";
        String blocks =
                "{\"sources\":[{\"type\":\"minecraft:single\",\"resource\":\"demo:world\",\"sprite\":\"demo:warning\"}]}";
        put("packs/main/assets/minecraft/atlases/items.json", items);
        put("packs/main/assets/minecraft/atlases/blocks.json", blocks);
        put("packs/main/definitions/blocks/warning.yml", "render: block\nmodel: demo:warning\n");
        PathResolver.ensurePaths();
        PackDeveloper.createPack();
        try (var zip = new ZipFile(root.resolve("generated/pack.zip").toFile())) {
            // Emitted atlases may gain aliases, while existing mappings and source files stay
            // unchanged.
            assertEquals(
                    "demo:held",
                    readJson(zip, "assets/minecraft/atlases/items.json")
                            .getAsJsonArray("sources")
                            .get(0)
                            .getAsJsonObject()
                            .get("resource")
                            .getAsString());
            var worldSources =
                    readJson(zip, "assets/minecraft/atlases/blocks.json").getAsJsonArray("sources");
            assertEquals(
                    "demo:world",
                    worldSources.get(0).getAsJsonObject().get("resource").getAsString());
            assertTrue(
                    worldSources.asList().stream()
                            .map(s -> s.getAsJsonObject())
                            .anyMatch(s ->
                                    s.has("sprite")
                                            && !s.get("sprite").getAsString().equals("demo:warning")
                                            && s.get("resource")
                                                    .getAsString()
                                                    .equals("demo:world")));
        }
        assertEquals(
                items,
                Files.readString(root.resolve("packs/main/assets/minecraft/atlases/items.json")));
        assertEquals(
                blocks,
                Files.readString(root.resolve("packs/main/assets/minecraft/atlases/blocks.json")));
    }

    @Test
    void simpleBlockModelOverrideDoesNotReplaceItsHeldItemGeometry() throws Exception {
        simpleModel();
        put("packs/main/assets/demo/models/other.json", "{\"parent\":\"demo:warning\"}");
        put(
                "packs/main/definitions/blocks/warning.yml",
                "render: block\nmodel: demo:warning\nblock-model: demo:other\n");
        PathResolver.ensurePaths();
        PackDeveloper.createPack();
        try (var zip = new ZipFile(root.resolve("generated/pack.zip").toFile())) {
            var json =
                    com.google.gson.JsonParser.parseString(
                                    new String(
                                            zip.getInputStream(
                                                            zip.getEntry(
                                                                    "assets/demo/items/warning.json"))
                                                    .readAllBytes(),
                                            java.nio.charset.StandardCharsets.UTF_8))
                            .getAsJsonObject();
            assertEquals("demo:warning", json.getAsJsonObject("model").get("model").getAsString());
        }
    }

    @Test
    void simpleSetupCanReplaceManualParticleAtlasesWithoutEditingTheImportedModel()
            throws Exception {
        simpleModel();
        String model =
                "{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"demo:warning\",\"particle\":\"demo:virtual_particle\"}}";
        put("packs/main/assets/demo/models/warning.json", model);
        put(
                "packs/main/definitions/blocks/warning.yml",
                "render: block\n"
                        + "model: demo:warning\n"
                        + "particles: {texture: 'demo:warning', region: [0, 0, 8, 8]}\n");
        PathResolver.ensurePaths();
        PackDeveloper.createPack();
        assertEquals(
                model,
                Files.readString(root.resolve("packs/main/assets/demo/models/warning.json")));
        assertFalse(Files.exists(root.resolve("packs/main/assets/minecraft/atlases")));
    }

    @Test
    void nativeBlockMaterialsUseDistinctBlockSpritesInsteadOfAmbiguousItemAtlasIds()
            throws Exception {
        simpleModel();
        put(
                "packs/main/definitions/blocks/warning.yml",
                "render: block\n"
                        + "model: demo:warning\n"
                        + "particles: {texture: 'demo:warning', region: [0, 0, 8, 8]}\n");
        put(
                "packs/main/definitions/blocks/shared_display.yml",
                "render: display\nmodel: demo:warning\n");
        PathResolver.ensurePaths();
        var assets = PackDeveloper.createPack();
        var block =
                assets.stream()
                        .filter(BlockDefinition.class::isInstance)
                        .map(BlockDefinition.class::cast)
                        .filter(b -> b.getId().equals("warning"))
                        .findFirst()
                        .orElseThrow()
                        .descriptor();
        try (var zip = new ZipFile(root.resolve("generated/pack.zip").toFile())) {
            var blocks = atlasSprites(zip, "blocks");
            var items = atlasSprites(zip, "items");
            assertTrue(
                    java.util.Collections.disjoint(blocks, items),
                    "Minecraft 26.3 assigns each sprite ID to one atlas");
            var key = net.kyori.adventure.key.Key.key(block.blockModel());
            var model =
                    readJson(zip, "assets/" + key.namespace() + "/models/" + key.value() + ".json");
            assertTrue(model.has("textures"));
            for (var material : model.getAsJsonObject("textures").asMap().values()) {
                String sprite =
                        material.isJsonObject()
                                ? material.getAsJsonObject().get("sprite").getAsString()
                                : material.getAsString();
                if (!sprite.startsWith("#"))
                    assertTrue(
                            blocks.contains(sprite),
                            "World texture must bind to the blocks atlas: " + sprite);
            }
            assertTrue(items.contains("demo:warning"));
            assertTrue(
                    readJson(
                                    zip,
                                    "assets/assetchannel/items/generated/blocks/warning/particles.json")
                            .get("assetchannel:internal")
                            .getAsBoolean());
        }
    }

    private static com.google.gson.JsonObject readJson(ZipFile zip, String path) throws Exception {
        var entry = zip.getEntry(path);
        assertNotNull(entry, path);
        return com.google.gson.JsonParser.parseString(
                        new String(
                                zip.getInputStream(entry).readAllBytes(),
                                java.nio.charset.StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    private static java.util.Set<String> atlasSprites(ZipFile zip, String atlas) throws Exception {
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (var raw :
                readJson(zip, "assets/minecraft/atlases/" + atlas + ".json")
                        .getAsJsonArray("sources")) {
            var source = raw.getAsJsonObject();
            if (source.get("type").getAsString().equals("minecraft:single"))
                ids.add(
                        source.has("sprite")
                                ? source.get("sprite").getAsString()
                                : source.get("resource").getAsString());
            if (source.get("type").getAsString().equals("minecraft:unstitch"))
                for (var region : source.getAsJsonArray("regions"))
                    ids.add(region.getAsJsonObject().get("sprite").getAsString());
        }
        return ids;
    }
}
