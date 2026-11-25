package nu.miguel.assetChannel.util;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nu.miguel.assetChannel.Main;
import nu.miguel.assetChannel.asset.*;
import nu.miguel.assetChannel.pack.*;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;

class CinematicImportIntegrationTest {
    @TempDir Path root;

    private void installSamplePack() throws Exception {
        Path source = Path.of("examples/cinematic-pack");
        try (var paths = Files.walk(source)) {
            for (Path file :
                    paths.filter(Files::isRegularFile)
                            .filter(p ->
                                    Set.of("sources", "assets", "definitions", "font-licenses")
                                            .contains(source.relativize(p).getName(0).toString()))
                            .toList()) {
                Path target = root.resolve("packs/main").resolve(source.relativize(file));
                Files.createDirectories(target.getParent());
                Files.copy(file, target);
            }
        }
    }

    @Test
    void learningRigAndBottomCaptionFontsPassNativePackValidation() throws Exception {
        var previousPlugin = Main.plugin;
        var previousData = PathResolver.dataPath;
        boolean previousEnsured = PathResolver.hasBeenEnsured;
        var previousConfig = Main.config;
        try {
            Main.plugin = mock(Plugin.class);
            when(Main.plugin.getDataFolder()).thenReturn(root.toFile());
            when(Main.plugin.getDataPath()).thenReturn(root);
            Main.config = new YamlConfiguration();
            PathResolver.dataPath = root.toFile();
            PathResolver.hasBeenEnsured = true;
            installSamplePack();
            Path model = root.resolve("packs/main/sources/models/learning.bbmodel");
            Files.createDirectories(model.getParent());
            Files.copy(Path.of("src/test/resources/rigs/learning.bbmodel"), model);
            var assets = PackDeveloper.createPack();
            var rig =
                    assets.stream()
                            .filter(RigCompiler.class::isInstance)
                            .map(RigCompiler.class::cast)
                            .filter(r -> r.getId().equals("learning"))
                            .findFirst()
                            .orElseThrow()
                            .rig();
            assertEquals(20, rig.parts().size());
            assertEquals(Set.of("idle", "walk"), rig.clips().keySet());
            try (var zip = new ZipFile(root.resolve("generated/pack.zip").toFile())) {
                assertNotNull(
                        zip.getEntry(
                                "assets/assetchannel/font/generated/captions/ui/subtitle/outline/lower.json"));
                assertNotNull(
                        zip.getEntry(
                                "assets/assetchannel/post_effect/generated/composite/cinematic/screen/render/31.json"));
                assertNotNull(
                        zip.getEntry(
                                "assets/assetchannel/items/generated/rigs/learning/part_19.json"));
            }
            byte[] published = Files.readAllBytes(root.resolve("generated/pack.zip"));
            Files.writeString(
                    root.resolve("packs/main/definitions/effects/missing.yml"),
                    Files.readString(
                                    Path.of("examples/cinematic-pack/definitions/effects/zoom.yml"))
                            .replace("cinematic:post/composite", "missing:broken"));
            var error = assertThrows(IllegalArgumentException.class, PackDeveloper::createPack);
            assertTrue(error.getMessage().contains("missing:broken"));
            assertArrayEquals(
                    published,
                    Files.readAllBytes(root.resolve("generated/pack.zip")),
                    "Missing effect references preserve the active pack");
        } finally {
            Main.plugin = previousPlugin;
            Main.config = previousConfig;
            PathResolver.dataPath = previousData;
            PathResolver.hasBeenEnsured = previousEnsured;
        }
    }

    @Test
    void bundledDemonstrationCompilesWithoutChangingItsSources() throws Exception {
        var previousPlugin = Main.plugin;
        var previousData = PathResolver.dataPath;
        boolean previousEnsured = PathResolver.hasBeenEnsured;
        var previousConfig = Main.config;
        try {
            Main.plugin = mock(Plugin.class);
            when(Main.plugin.getDataFolder()).thenReturn(root.toFile());
            when(Main.plugin.getDataPath()).thenReturn(root);
            Main.config = new YamlConfiguration();
            PathResolver.dataPath = root.toFile();
            PathResolver.hasBeenEnsured = true;
            installSamplePack();
            var assets = PackDeveloper.createPack();
            var rig =
                    assets.stream()
                            .filter(RigCompiler.class::isInstance)
                            .map(RigCompiler.class::cast)
                            .findFirst()
                            .orElseThrow()
                            .rig();
            assertEquals(6, rig.parts().size());
            assertTrue(rig.clips().keySet().containsAll(List.of("idle", "walk", "talk")));
            assertTrue(
                    assets.stream()
                            .anyMatch(a ->
                                    a instanceof TimelineAudio && a.getId().equals("demo.line")));
            try (var zip = new ZipFile(root.resolve("generated/pack.zip").toFile())) {
                assertNotNull(
                        zip.getEntry(
                                "assets/assetchannel/post_effect/generated/composite/cinematic/screen/render/31.json"));
                assertFalse(
                        zip.stream()
                                .anyMatch(entry ->
                                        entry.getName()
                                                .startsWith(
                                                        "assets/assetchannel/post_effect/cinematic/zoom/")));
                assertFalse(
                        zip.stream()
                                .anyMatch(entry ->
                                        entry.getName().contains("interpolated_zoom.fsh")));
            }
        } finally {
            Main.plugin = previousPlugin;
            Main.config = previousConfig;
            PathResolver.dataPath = previousData;
            PathResolver.hasBeenEnsured = previousEnsured;
        }
    }

    @Test
    void publishesRigTexturesAndAudioAliasesAndRetainsPackOnBrokenRig() throws Exception {
        var previousPlugin = Main.plugin;
        var previousData = PathResolver.dataPath;
        boolean previousEnsured = PathResolver.hasBeenEnsured;
        var previousConfig = Main.config;
        try {
            Main.plugin = mock(Plugin.class);
            when(Main.plugin.getDataFolder()).thenReturn(root.toFile());
            when(Main.plugin.getDataPath()).thenReturn(root);
            Main.config = new YamlConfiguration();
            PathResolver.dataPath = root.toFile();
            PathResolver.hasBeenEnsured = true;
            Path models = root.resolve("packs/main/sources/models");
            Files.createDirectories(models);
            var image =
                    new java.awt.image.BufferedImage(
                            16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            var png = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image, "png", png);
            Path source = models.resolve("guide.bbmodel");
            Files.writeString(
                    source,
                    """
                    {"resolution":{"width":16,"height":16},"textures":[{"source":"data:image/png;base64,%s"}],
                    "outliner":[{"uuid":"root","name":"root","children":["cube"]}],
                    "elements":[{"uuid":"cube","type":"cube","from":[0,0,0],"to":[8,8,8],"origin":[0,0,0],"faces":{"north":{"uv":[0,0,8,8],"texture":0}}}]}
                    """
                            .formatted(Base64.getEncoder().encodeToString(png.toByteArray())));
            Path audio = root.resolve("packs/main/sources/scene-audio/guide.ogg");
            Files.createDirectories(audio.getParent());
            String ffmpeg =
                    new ws.schild.jave.process.ffmpeg.DefaultFFMPEGLocator().getExecutablePath();
            Process process =
                    new ProcessBuilder(
                                    ffmpeg,
                                    "-hide_banner",
                                    "-loglevel",
                                    "error",
                                    "-y",
                                    "-f",
                                    "lavfi",
                                    "-i",
                                    "sine=frequency=440:duration=2.4",
                                    "-ac",
                                    "2",
                                    "-c:a",
                                    "libvorbis",
                                    audio.toString())
                            .redirectErrorStream(true)
                            .start();
            assertTrue(process.waitFor(20, TimeUnit.SECONDS));
            assertEquals(0, process.exitValue());
            var assets = PackDeveloper.createPack();
            var media =
                    assets.stream()
                            .filter(TimelineAudio.class::isInstance)
                            .map(TimelineAudio.class::cast)
                            .findFirst()
                            .orElseThrow()
                            .descriptor();
            assertEquals(3, media.segments());
            try (var zip = new ZipFile(root.resolve("generated/pack.zip").toFile())) {
                assertNotNull(
                        zip.getEntry("assets/assetchannel/items/generated/rigs/guide/part_0.json"));
                assertNotNull(
                        zip.getEntry(
                                "assets/assetchannel/textures/generated/rigs/guide/texture_0.png"));
                assertNotNull(
                        zip.getEntry("assets/assetchannel_scene/sounds/listener/guide/0000.ogg"));
                String events =
                        new String(
                                zip.getInputStream(
                                                zip.getEntry(
                                                        "assets/assetchannel_scene/sounds.json"))
                                        .readAllBytes(),
                                java.nio.charset.StandardCharsets.UTF_8);
                assertTrue(events.contains("guide.2.15.spatial"));
                assertTrue(zip.stream().noneMatch(e -> e.getName().contains(".bbmodel")));
            }
            byte[] published = Files.readAllBytes(root.resolve("generated/pack.zip"));
            String original = Files.readString(source);
            Files.writeString(source, original.replace("\"type\":\"cube\"", "\"type\":\"mesh\""));
            assertThrows(IllegalArgumentException.class, PackDeveloper::createPack);
            assertArrayEquals(published, Files.readAllBytes(root.resolve("generated/pack.zip")));
        } finally {
            Main.plugin = previousPlugin;
            Main.config = previousConfig;
            PathResolver.dataPath = previousData;
            PathResolver.hasBeenEnsured = previousEnsured;
        }
    }
}
