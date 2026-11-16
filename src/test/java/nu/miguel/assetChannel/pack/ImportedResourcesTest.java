package nu.miguel.assetChannel.pack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class ImportedResourcesTest {
    @Test void catalogsNativeCategoriesAndPreservesUnknownFutureResources() throws Exception {
        put("demo/particles/spark.json","{\"textures\":[]}");
        put("demo/equipment/armor.json","{}");
        put("demo/blockstates/rock.json","{\"variants\":{}}");
        put("demo/shaders/test.fsh","shader source");
        put("demo/future_category/example.bin","opaque bytes");
        var resources=ImportedResources.scan(root).resources();
        assertTrue(resources.stream().anyMatch(r->r.kind()==nu.miguel.assetChannel.api.ResourceKind.PARTICLE));
        assertTrue(resources.stream().anyMatch(r->r.kind()==nu.miguel.assetChannel.api.ResourceKind.BLOCKSTATE));
        assertTrue(resources.stream().anyMatch(r->r.key().asString().equals("demo:future_category/example.bin")));
    }
    @Test void rejectsBitmapGlyphLargerThanMinecraftFontAtlasEvenWhenRenderHeightIsSmall() throws Exception {
        Path image = root.resolve("demo/textures/oversize.png"); Files.createDirectories(image.getParent());
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(300,16,java.awt.image.BufferedImage.TYPE_INT_ARGB),"png",image.toFile());
        put("demo/font/oversize.json", "{\"providers\":[{\"type\":\"bitmap\",\"file\":\"demo:oversize.png\",\"height\":8,\"ascent\":7,\"chars\":[\"A\"]}]}");
        assertThrows(IllegalArgumentException.class, () -> ImportedResources.scan(root));
    }
    @Test void acceptsLargeBitmapSheetsWhenEachGlyphFitsTheAtlas() throws Exception {
        Path image=root.resolve("demo/textures/sheet.png"); Files.createDirectories(image.getParent());
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(300,16,java.awt.image.BufferedImage.TYPE_INT_ARGB),"png",image.toFile());
        put("demo/font/sheet.json", "{\"providers\":[{\"type\":\"bitmap\",\"file\":\"demo:sheet.png\",\"height\":8,\"ascent\":7,\"chars\":[\"ABC\"]}]}");
        assertDoesNotThrow(() -> ImportedResources.scan(root));
    }
    @TempDir Path root;
    private void put(String path, String value) throws Exception {
        Path file = root.resolve(path); Files.createDirectories(file.getParent()); Files.writeString(file, value);
    }
    @Test void preservesPathsAndRegistersOnlyItemDefinitions() throws Exception {
        put("demo/models/button.json", "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"demo:button\"}}");
        put("demo/items/button.json", "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"demo:button\"}}");
        put("demo/textures/button.png", "texture");
        var resources = ImportedResources.scan(root);
        assertEquals(java.util.List.of("demo:button"), resources.models().stream().map(a -> a.id()).toList());
        Path target = root.resolveSibling(root.getFileName() + "-output");
        resources.copyTo(target);
        assertEquals("texture", Files.readString(target.resolve("demo/textures/button.png")));
    }
    @Test void rejectsMissingLocalModelAndTextureReferences() throws Exception {
        put("demo/items/button.json", "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"demo:missing\"}}");
        assertThrows(IllegalArgumentException.class, () -> ImportedResources.scan(root));
    }
    @Test void rejectsMissingTextureInModel() throws Exception {
        put("demo/models/button.json", "{\"textures\":{\"layer0\":\"demo:missing\"}}");
        assertThrows(IllegalArgumentException.class, () -> ImportedResources.scan(root));
    }
    @Test void acceptsParticleSpritesExtractedFromAnAtlasSheet() throws Exception {
        put("demo/textures/sheet.png", "source texture");
        put("minecraft/atlases/items.json", """
                {"sources":[{"type":"minecraft:unstitch","resource":"demo:sheet",
                  "divisor_x":16,"divisor_y":16,
                  "regions":[{"sprite":"demo:warning_particle","x":4.625,"y":6.125,"width":2,"height":2}]}]}
                """);
        put("demo/models/warning.json", "{\"textures\":{\"particle\":\"demo:warning_particle\"}}");
        assertDoesNotThrow(() -> ImportedResources.scan(root));
    }
    @Test void acceptsRenamedAtlasSpritesWithoutTreatingThemAsPngFiles() throws Exception {
        put("demo/textures/sheet.png", "source texture");
        put("minecraft/atlases/items.json", "{\"sources\":[{\"type\":\"minecraft:single\",\"resource\":\"demo:sheet\",\"sprite\":\"demo:alias\"}]}");
        put("demo/models/button.json", "{\"textures\":{\"layer0\":{\"sprite\":\"demo:alias\"}}}");
        assertDoesNotThrow(() -> ImportedResources.scan(root));
    }
    @Test void rejectsMissingSourceImageForExtractedAtlasSprites() throws Exception {
        put("minecraft/atlases/items.json", """
                {"sources":[{"type":"minecraft:unstitch","resource":"demo:missing",
                  "divisor_x":16,"divisor_y":16,
                  "regions":[{"sprite":"demo:warning_particle","x":0,"y":0,"width":2,"height":2}]}]}
                """);
        var error = assertThrows(IllegalArgumentException.class, () -> ImportedResources.scan(root));
        assertTrue(error.getMessage().contains("demo/textures/missing.png"));
    }
    @Test void rejectsMissingBitmapFontTexture() throws Exception {
        put("demo/font/panel.json", "{\"providers\":[{\"type\":\"bitmap\",\"file\":\"demo:missing.png\",\"chars\":[\"A\"]}]}");
        assertThrows(IllegalArgumentException.class, () -> ImportedResources.scan(root));
    }
    @Test void checksModernTextureSpriteObjects() throws Exception {
        put("demo/models/button.json", "{\"textures\":{\"layer0\":{\"sprite\":\"demo:missing\",\"force_translucent\":true}}}");
        assertThrows(IllegalArgumentException.class, () -> ImportedResources.scan(root));
    }
    @Test void doesNotRegisterDirectoriesNamedItemsInsideTextures() throws Exception {
        put("demo/textures/items/foo.json", "{}");
        assertTrue(ImportedResources.scan(root).models().isEmpty());
    }
    @Test void validatesNativeAssetsAndRetainsSourceBytes() throws Exception {
        Path assets = NativePackFixture.create(root.resolve("source"));
        var resources = ImportedResources.scan(assets);
        assertEquals(java.util.List.of("demo:button", "demo:cube"), resources.models().stream().map(a -> a.id()).toList());
        Path output = root.resolve("output");
        resources.copyTo(output);
        assertArrayEquals(Files.readAllBytes(assets.resolve("demo/textures/ui/cube.png")), Files.readAllBytes(output.resolve("demo/textures/ui/cube.png")));
        assertNotNull(javax.imageio.ImageIO.read(output.resolve("demo/textures/ui/cube.png").toFile()));
    }
    @Test void rejectsModelParentCycles() throws Exception {
        put("demo/models/a.json", "{\"parent\":\"demo:b\"}");
        put("demo/models/b.json", "{\"parent\":\"demo:a\"}");
        assertThrows(IllegalArgumentException.class, () -> ImportedResources.scan(root));
    }
    @Test void rejectsMalformedJsonAndUnsafeResourcePaths() throws Exception {
        put("demo/items/button.json", "broken");
        assertThrows(IllegalArgumentException.class, () -> ImportedResources.scan(root));
        Files.delete(root.resolve("demo/items/button.json"));
        put("Uppercase/models/button.json", "{}");
        assertThrows(IllegalArgumentException.class, () -> ImportedResources.scan(root));
    }
    @Test void neverOverwritesGeneratedResources() throws Exception {
        put("assetchannel/font/spacing.json", "{\"providers\":[]}");
        var resources = ImportedResources.scan(root);
        assertThrows(IllegalArgumentException.class, () -> resources.copyTo(root));
    }
}
