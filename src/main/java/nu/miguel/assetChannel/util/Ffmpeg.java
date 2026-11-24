package nu.miguel.assetChannel.util;

import nu.miguel.assetChannel.Main;

import ws.schild.jave.process.ffmpeg.DefaultFFMPEGLocator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Runs the bundled FFmpeg and caches deterministic audio conversions. */
public final class Ffmpeg {
    private static final String CACHE_VERSION = "audio-v2";
    private static volatile String bundledExecutable;
    private static final Object CACHE_LOCK = new Object();

    private Ffmpeg() {}

    /** Preserves the source channel layout for listener-relative playback. */
    public static void transcodeListening(Path source, Path destination)
            throws IOException, InterruptedException {
        if (AudioDurationReader.isOgg(source)) {
            // Keeping valid Vorbis packets avoids a lossy second encode.
            AudioDurationReader.read(source);
            Files.createDirectories(destination.getParent());
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
        } else {
            AudioDurationReader.read(source);
            transcode(source, destination, 0);
        }
    }

    /** Produces mono audio so Minecraft can spatialize it. */
    public static void transcodeMono(Path source, Path destination)
            throws IOException, InterruptedException {
        transcode(source, destination, 1);
    }

    public static List<Path> splitListening(
            Path source, Path destinationRoot, double intervalSeconds)
            throws IOException, InterruptedException {
        return split(source, destinationRoot, intervalSeconds, 0);
    }

    public static List<Path> splitMono(Path source, Path destinationRoot, double intervalSeconds)
            throws IOException, InterruptedException {
        return split(source, destinationRoot, intervalSeconds, 1);
    }

    /**
     * Extracts exact source-time ranges. Segment-muxer packet boundaries may otherwise add an extra
     * padding clip.
     */
    public static List<Path> splitTimeline(
            Path source, Path destinationRoot, double intervalSeconds, boolean mono)
            throws IOException, InterruptedException {
        if (!Double.isFinite(intervalSeconds) || intervalSeconds <= 0)
            throw new IllegalArgumentException("Positive timeline interval required");
        double seconds = AudioDurationReader.read(source).toNanos() / 1e9;
        if (seconds > 3600)
            throw new IllegalArgumentException(
                    "Prepared scene audio is limited to one hour per file");
        int count = (int) Math.ceil(seconds / intervalSeconds);
        List<String> audioArgs = encodeArguments(mono ? 1 : 0);
        String key =
                cacheKey(
                        source,
                        "timeline-ranges-v1",
                        Double.toString(intervalSeconds),
                        Boolean.toString(mono),
                        String.join("\u0000", audioArgs));
        Path cached = cacheRoot().resolve(key);
        synchronized (CACHE_LOCK) {
            if (!validOggDirectory(cached) || listOggs(cached).size() != count) {
                deleteTree(cached);
                Files.createDirectories(cached.getParent());
                Path temporary =
                        cached.resolveSibling(cached.getFileName() + ".tmp-" + UUID.randomUUID());
                Files.createDirectories(temporary);
                try {
                    for (int index = 0; index < count; index++) {
                        double start = index * intervalSeconds;
                        Path clip =
                                temporary.resolve(String.format(Locale.ROOT, "%04d.ogg", index));
                        List<String> arguments =
                                new ArrayList<>(
                                        List.of(
                                                "-ss",
                                                Double.toString(start),
                                                "-i",
                                                source.toAbsolutePath().toString(),
                                                "-t",
                                                Double.toString(
                                                        Math.min(intervalSeconds, seconds - start)),
                                                "-map",
                                                "0:a:0",
                                                "-vn",
                                                "-map_metadata",
                                                "-1"));
                        arguments.addAll(audioArgs);
                        arguments.addAll(List.of("-f", "ogg", clip.toAbsolutePath().toString()));
                        run(arguments);
                        if (!validOgg(clip))
                            throw new IOException("Invalid timeline audio clip " + index);
                    }
                    moveAtomic(temporary, cached);
                } finally {
                    deleteTree(temporary);
                }
            }
        }
        Files.createDirectories(destinationRoot);
        List<Path> result = new ArrayList<>();
        for (Path clip : listOggs(cached)) {
            Path target = destinationRoot.resolve(clip.getFileName().toString());
            Files.copy(clip, target, StandardCopyOption.REPLACE_EXISTING);
            result.add(target);
        }
        return List.copyOf(result);
    }

    private static void transcode(Path source, Path destination, int channels)
            throws IOException, InterruptedException {
        List<String> audioArgs = encodeArguments(channels);
        String key =
                cacheKey(
                        source,
                        "transcode",
                        Integer.toString(channels),
                        String.join("\u0000", audioArgs));
        Path cached = cacheRoot().resolve(key + ".ogg");
        Files.createDirectories(destination.getParent());

        synchronized (CACHE_LOCK) {
            if (!validOgg(cached)) {
                Files.createDirectories(cached.getParent());
                Path temporary =
                        cached.resolveSibling(cached.getFileName() + ".tmp-" + UUID.randomUUID());
                try {
                    List<String> arguments =
                            new ArrayList<>(
                                    List.of(
                                            "-i",
                                            source.toAbsolutePath().toString(),
                                            "-map",
                                            "0:a:0",
                                            "-vn",
                                            "-map_metadata",
                                            "-1"));
                    arguments.addAll(audioArgs);
                    arguments.addAll(List.of("-f", "ogg"));
                    arguments.add(temporary.toAbsolutePath().toString());
                    run(arguments);
                    if (!validOgg(temporary))
                        throw new IOException("FFmpeg produced invalid audio");
                    moveAtomic(temporary, cached);
                } finally {
                    Files.deleteIfExists(temporary);
                }
            }
        }
        Files.copy(cached, destination, StandardCopyOption.REPLACE_EXISTING);
    }

    private static List<Path> split(
            Path source, Path destinationRoot, double intervalSeconds, int channels)
            throws IOException, InterruptedException {
        List<String> audioArgs = encodeArguments(channels);
        String key =
                cacheKey(
                        source,
                        "split",
                        Double.toString(intervalSeconds),
                        Integer.toString(channels),
                        String.join("\u0000", audioArgs));
        Path cachedDirectory = cacheRoot().resolve(key);

        synchronized (CACHE_LOCK) {
            if (!validOggDirectory(cachedDirectory)) {
                deleteTree(cachedDirectory);
                Files.createDirectories(cachedDirectory.getParent());
                Path temporary =
                        cachedDirectory.resolveSibling(
                                cachedDirectory.getFileName() + ".tmp-" + UUID.randomUUID());
                Files.createDirectories(temporary);
                try {
                    List<String> arguments =
                            new ArrayList<>(
                                    List.of(
                                            "-i",
                                            source.toAbsolutePath().toString(),
                                            "-map",
                                            "0:a:0",
                                            "-vn",
                                            "-map_metadata",
                                            "-1"));
                    arguments.addAll(audioArgs);
                    arguments.addAll(
                            List.of(
                                    "-f",
                                    "segment",
                                    "-segment_time",
                                    Double.toString(intervalSeconds),
                                    "-reset_timestamps",
                                    "1",
                                    temporary.resolve("%04d.ogg").toAbsolutePath().toString()));
                    run(arguments);
                    if (!validOggDirectory(temporary))
                        throw new IOException("FFmpeg produced invalid audio segments");
                    moveAtomic(temporary, cachedDirectory);
                } finally {
                    deleteTree(temporary);
                }
            }
        }

        Files.createDirectories(destinationRoot);
        List<Path> result = new ArrayList<>();
        for (Path cached : listOggs(cachedDirectory)) {
            Path destination = destinationRoot.resolve(cached.getFileName().toString());
            Files.copy(cached, destination, StandardCopyOption.REPLACE_EXISTING);
            result.add(destination);
        }
        return List.copyOf(result);
    }

    private static List<String> encodeArguments(int channels) {
        int quality =
                Main.config == null
                        ? 6
                        : Math.max(
                                0,
                                Math.min(10, Main.config.getInt("music.ffmpeg.vorbis-quality", 6)));
        List<String> result = new ArrayList<>();
        if (channels > 0) result.addAll(List.of("-ac", Integer.toString(channels)));
        result.addAll(
                List.of("-ar", "48000", "-c:a", "libvorbis", "-q:a", Integer.toString(quality)));
        return result;
    }

    private static Path cacheRoot() {
        if (Main.plugin != null) return Main.plugin.getDataPath().resolve("generated/cache/audio");
        return Path.of(System.getProperty("java.io.tmpdir"), "assetchannel-audio-cache");
    }

    private static String cacheKey(Path source, String... settings) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(CACHE_VERSION.getBytes(StandardCharsets.UTF_8));
            for (String setting : settings) {
                digest.update((byte) 0);
                digest.update(setting.getBytes(StandardCharsets.UTF_8));
            }
            try (InputStream input = Files.newInputStream(source)) {
                byte[] buffer = new byte[64 * 1024];
                for (int read; (read = input.read(buffer)) >= 0; ) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static List<Path> listOggs(Path folder) throws IOException {
        if (!Files.isDirectory(folder)) return List.of();
        try (var files = Files.list(folder)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".ogg"))
                    .sorted()
                    .toList();
        }
    }

    private static boolean validOgg(Path path) {
        try {
            if (!Files.isRegularFile(path) || Files.size(path) <= 0) return false;
            var duration = OggDurationReader.read(path);
            return !duration.isZero() && !duration.isNegative();
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean validOggDirectory(Path directory) throws IOException {
        List<Path> files = listOggs(directory);
        return !files.isEmpty() && files.stream().allMatch(Ffmpeg::validOgg);
    }

    private static void moveAtomic(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, destination);
        }
    }

    private static void deleteTree(Path root) {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
                Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // A later cache build uses a different UUID and is unaffected.
        }
    }

    private static void run(List<String> arguments) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(arguments.size() + 6);
        command.add(executable());
        command.add("-hide_banner");
        command.add("-loglevel");
        command.add("error");
        command.add("-y");
        command.addAll(arguments);

        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        AtomicReference<byte[]> capturedOutput = new AtomicReference<>(new byte[0]);
        Thread outputReader =
                Thread.ofVirtual()
                        .start(() -> {
                            try {
                                capturedOutput.set(process.getInputStream().readAllBytes());
                            } catch (IOException ignored) {
                            }
                        });
        long timeout =
                Math.max(
                        1,
                        Main.config == null
                                ? 300
                                : Main.config.getLong("music.ffmpeg.timeout-seconds", 300));
        boolean done = process.waitFor(timeout, TimeUnit.SECONDS);
        if (!done) {
            process.destroyForcibly();
            outputReader.join();
            throw new IOException("FFmpeg timed out after " + timeout + " seconds");
        }
        outputReader.join();
        if (process.exitValue() != 0) {
            String output = new String(capturedOutput.get(), StandardCharsets.UTF_8).trim();
            throw new IOException(
                    "FFmpeg exited with code "
                            + process.exitValue()
                            + (output.isEmpty() ? "" : ": " + output));
        }
    }

    private static String executable() {
        String configured =
                Main.config == null ? "" : Main.config.getString("music.ffmpeg.executable", "");
        if (configured != null && !configured.isBlank() && !configured.equalsIgnoreCase("bundled"))
            return configured.trim();
        String cached = bundledExecutable;
        if (cached == null) {
            synchronized (Ffmpeg.class) {
                cached = bundledExecutable;
                if (cached == null)
                    bundledExecutable = cached = new DefaultFFMPEGLocator().getExecutablePath();
            }
        }
        return cached;
    }
}
