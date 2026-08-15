package nu.miguel.assetChannel.util;

import nu.miguel.assetChannel.Main;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.bukkit.configuration.file.YamlConfiguration;
import ws.schild.jave.process.ffmpeg.DefaultFFMPEGLocator;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BundledFfmpegTest {
    @Test void extractsAndRunsBundledExecutable() throws Exception {
        Path executable = Path.of(new DefaultFFMPEGLocator().getExecutablePath());
        assertTrue(Files.isRegularFile(executable));

        Process process = new ProcessBuilder(executable.toString(), "-version")
                .redirectErrorStream(true).start();
        assertTrue(process.waitFor(10, TimeUnit.SECONDS));
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), output);
        assertTrue(output.startsWith("ffmpeg version"), output);
    }

    @Test void bundledExecutableProducesMonoVorbis(@TempDir Path directory) throws Exception {
        String executable = new DefaultFFMPEGLocator().getExecutablePath();
        Path stereo = directory.resolve("stereo.ogg");
        Path mono = directory.resolve("mono.ogg");
        Process generate = new ProcessBuilder(executable, "-hide_banner", "-loglevel", "error", "-y",
                "-f", "lavfi", "-i", "sine=frequency=440:duration=0.1", "-ac", "2",
                "-c:a", "libvorbis", stereo.toString()).start();
        assertTrue(generate.waitFor(10, TimeUnit.SECONDS));
        assertEquals(0, generate.exitValue());

        Main.config = new YamlConfiguration();
        Ffmpeg.transcodeMono(stereo, mono);

        Process inspect = new ProcessBuilder(executable, "-hide_banner", "-i", mono.toString(),
                "-f", "null", "-").redirectErrorStream(true).start();
        assertTrue(inspect.waitFor(10, TimeUnit.SECONDS));
        String output = new String(inspect.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, inspect.exitValue(), output);
        assertTrue(output.contains("Audio: vorbis") && output.contains("mono"), output);
    }

    @Test void unsplitListenerAudioIsNotLossilyReencoded(@TempDir Path directory) throws Exception {
        String executable = new DefaultFFMPEGLocator().getExecutablePath();
        Path source = directory.resolve("source.ogg");
        Path destination = directory.resolve("listener.ogg");
        Process generate = new ProcessBuilder(executable, "-hide_banner", "-loglevel", "error", "-y",
                "-f", "lavfi", "-i", "sine=frequency=440:duration=0.1", "-ac", "2",
                "-c:a", "libvorbis", source.toString()).start();
        assertTrue(generate.waitFor(10, TimeUnit.SECONDS));
        assertEquals(0, generate.exitValue());

        Ffmpeg.transcodeListening(source, destination);

        assertArrayEquals(Files.readAllBytes(source), Files.readAllBytes(destination));
    }
}
