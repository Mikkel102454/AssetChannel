package nu.miguel.assetChannel.util;

import nu.miguel.assetChannel.Main;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContentLayoutTest {
    @TempDir Path root;
    private Plugin previousPlugin;
    private java.io.File previousData;
    private boolean previousEnsured;

    @BeforeEach void configurePlugin() {
        previousPlugin = Main.plugin;
        previousData = PathResolver.dataPath;
        previousEnsured = PathResolver.hasBeenEnsured;
        Main.plugin = mock(Plugin.class);
        when(Main.plugin.getDataFolder()).thenReturn(root.toFile());
        when(Main.plugin.getDataPath()).thenReturn(root);
    }

    @AfterEach void restorePlugin() {
        Main.plugin = previousPlugin;
        PathResolver.dataPath = previousData;
        PathResolver.hasBeenEnsured = previousEnsured;
    }

    @Test void createsContentFoldersAndKeepsGeneratedFilesSeparate() {
        PathResolver.ensurePaths();
        for (String folder : new String[]{"assets", "definitions/blocks", "definitions/hud",
                "sources/icons", "sources/fonts", "sources/sounds", "sources/music"})
            assertTrue(Files.isDirectory(root.resolve("packs/main").resolve(folder)), folder);
        assertTrue(Files.isDirectory(root.resolve("generated/cache")));
        for (String old : new String[]{"imports", "blocks", "hud", "icons", "font", "sound", "music"})
            assertFalse(Files.exists(root.resolve(old)), old);
    }

    @Test void migratesLegacyContentAndOutputWithoutChangingBytes() throws Exception {
        var moves = Map.ofEntries(
                Map.entry("blocks/warning_block.yml", "packs/main/definitions/blocks/warning_block.yml"),
                Map.entry("hud/ui/banner.yml", "packs/main/definitions/hud/ui/banner.yml"),
                Map.entry("icons/ui/rank.png", "packs/main/sources/icons/ui/rank.png"),
                Map.entry("font/ui/title.ttf", "packs/main/sources/fonts/ui/title.ttf"),
                Map.entry("sound/npc/talk.ogg", "packs/main/sources/sounds/npc/talk.ogg"),
                Map.entry("music/lobby/config.yml", "packs/main/sources/music/lobby/config.yml"),
                Map.entry("imports/assets/demo/models/cube.json", "packs/main/assets/demo/models/cube.json"),
                Map.entry("imports/pack/pack.mcmeta", "packs/main/pack.mcmeta"),
                Map.entry("imports/pack/assets/demo/textures/cube.png", "packs/main/assets/demo/textures/cube.png"),
                Map.entry("pack.zip", "generated/pack.zip"),
                Map.entry("pack/pack.mcmeta", "generated/pack/pack.mcmeta"),
                Map.entry("cache/audio/test.ogg", "generated/cache/audio/test.ogg"));
        for (String source : moves.keySet()) put(source, source);
        PathResolver.ensurePaths();
        for (var move : moves.entrySet()) {
            assertEquals(move.getKey(), Files.readString(root.resolve(move.getValue())));
            assertFalse(Files.exists(root.resolve(move.getKey())), move.getKey());
        }
        PathResolver.ensurePaths(); // A second startup must not duplicate or move content again.
        assertFalse(Files.exists(root.resolve("imports")));
    }

    @Test void detectsMigrationConflictsBeforeMovingAnySources() throws Exception {
        put("font/title.ttf", "font source");
        put("blocks/warning.yml", "old block");
        put("packs/main/definitions/blocks/warning.yml", "different block");
        var error = assertThrows(IllegalStateException.class, PathResolver::ensurePaths);
        assertTrue(error.getMessage().contains("warning.yml"));
        assertEquals("font source", Files.readString(root.resolve("font/title.ttf")));
        assertEquals("old block", Files.readString(root.resolve("blocks/warning.yml")));
        assertEquals("different block", Files.readString(root.resolve("packs/main/definitions/blocks/warning.yml")));
    }

    @Test void coalescesIdenticalMigrationTargets() throws Exception {
        put("blocks/warning.yml", "same content");
        put("packs/main/definitions/blocks/warning.yml", "same content");
        PathResolver.ensurePaths();
        assertEquals("same content", Files.readString(root.resolve("packs/main/definitions/blocks/warning.yml")));
        assertFalse(Files.exists(root.resolve("blocks")));
    }

    @Test void audioCacheUsesGeneratedFolder() throws Exception {
        var method = Ffmpeg.class.getDeclaredMethod("cacheRoot");
        method.setAccessible(true);
        assertEquals(root.resolve("generated/cache/audio"), method.invoke(null));
    }

    private void put(String relative, String text) throws Exception {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }
}
