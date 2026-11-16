package nu.miguel.assetChannel.util;

import nu.miguel.assetChannel.asset.Icon;
import nu.miguel.assetChannel.pack.ImportedResources;
import nu.miguel.assetChannel.pack.PackBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

class PackImportIntegrationTest {
    @TempDir Path directory;
    @Test void preservesNativeMetadataAndIconWithGenerated263CompatibilityBounds() throws Exception {
        var oldPath=PathResolver.dataPath;boolean oldEnsured=PathResolver.hasBeenEnsured;
        PathResolver.dataPath=directory.toFile();PathResolver.hasBeenEnsured=true;
        try {
            Path root=directory.resolve("native");Files.createDirectories(root);
            Files.writeString(root.resolve("pack.mcmeta"),"{\"pack\":{\"pack_format\":75,\"description\":\"Imported\"},\"language\":{\"en_test\":{\"name\":\"Test\",\"region\":\"Test\"}}}");
            javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1,1,java.awt.image.BufferedImage.TYPE_INT_ARGB),"png",root.resolve("pack.png").toFile());
            var extras=nu.miguel.assetChannel.pack.PackExtras.scan(root,directory.resolve("loose"));
            assertTrue(PackBuilder.build(List.of(extras.resources(),extras)));
            try(var zip=new ZipFile(directory.resolve("generated/pack.zip").toFile())) {
                assertNotNull(zip.getEntry("pack.png"));
                var meta=com.google.gson.JsonParser.parseString(new String(zip.getInputStream(zip.getEntry("pack.mcmeta")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                assertTrue(meta.has("language"));assertEquals(97,meta.getAsJsonObject("pack").getAsJsonArray("min_format").get(0).getAsInt());
                assertEquals("Imported",meta.getAsJsonObject("pack").get("description").getAsString());
            }
        }finally{PathResolver.dataPath=oldPath;PathResolver.hasBeenEnsured=oldEnsured;}
    }
    @Test void publishesNativeAssetsAndRetainsPreviousZipOnConflict() throws Exception {
        var oldPath = PathResolver.dataPath;
        boolean oldEnsured = PathResolver.hasBeenEnsured;
        PathResolver.dataPath = directory.toFile(); PathResolver.hasBeenEnsured = true;
        try {
            var imported = ImportedResources.scan(nu.miguel.assetChannel.pack.NativePackFixture.create(directory.resolve("source")));
            Path iconFile = directory.resolve("icon.png"), iconConfig = directory.resolve("icon.yml");
            javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(16,16,java.awt.image.BufferedImage.TYPE_INT_ARGB),"png",iconFile.toFile());
            Files.writeString(iconConfig,"height: 16\nascent: 13\n");
            var icon = new Icon("test.icon", iconFile, iconConfig);
            icon.assignGeneratedCodePoint(0xE001);
            Path fontFile = directory.resolve("hud.ttf"); Files.write(fontFile, new byte[]{1, 2, 3});
            var font = new nu.miguel.assetChannel.asset.Font("ui.hud", fontFile);
            assertTrue(PackBuilder.build(List.of(imported, icon, font)));
            byte[] published = Files.readAllBytes(directory.resolve("generated/pack.zip"));
            try (var zip = new ZipFile(directory.resolve("generated/pack.zip").toFile())) {
                assertNotNull(zip.getEntry("assets/demo/items/cube.json"));
                assertNotNull(zip.getEntry("assets/demo/models/ui/cube.json"));
                assertNotNull(zip.getEntry("assets/demo/textures/ui/cube.png"));
                assertNotNull(zip.getEntry("assets/minecraft/atlases/items.json"));
                assertNotNull(zip.getEntry("assets/assetchannel/font/icons.json"));
                assertNotNull(zip.getEntry("assets/assetchannel/font/ui.hud.json"), "Legacy dotted API key must resolve in the client");
                assertNotNull(zip.getEntry("assets/assetchannel/font/ui/hud.json"));
                String meta = new String(zip.getInputStream(zip.getEntry("pack.mcmeta")).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                assertTrue(meta.contains("[97, 1]"));
            }
            Path conflict = directory.resolve("conflict/assetchannel/font/spacing.json");
            Files.createDirectories(conflict.getParent()); Files.writeString(conflict, "{\"providers\":[]}");
            var bad = ImportedResources.scan(directory.resolve("conflict"));
            assertThrows(IllegalArgumentException.class, () -> PackBuilder.build(List.of(bad)));
            assertArrayEquals(published, Files.readAllBytes(directory.resolve("generated/pack.zip")));
        } finally { PathResolver.dataPath = oldPath; PathResolver.hasBeenEnsured = oldEnsured; }
    }
}
