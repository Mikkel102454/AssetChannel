package nu.miguel.assetChannel.asset;

import com.google.gson.*;
import nu.miguel.assetChannel.Main;
import nu.miguel.assetChannel.texturepack.TexturePack;
import nu.miguel.assetChannel.util.AudioDurationReader;
import nu.miguel.assetChannel.util.Ffmpeg;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

public final class Music implements Asset {
    public static final String LEGACY_STAGE = "default";

    private final String id;
    private final String defaultStage;
    private final Map<String, Stage> stages;
    private final boolean muteVanillaMusic;
    private final boolean muteOtherMusic;

    private Music(String id, String defaultStage, Map<String, Stage> stages, boolean muteVanillaMusic,
                  boolean muteOtherMusic) {
        this.id = id;
        this.defaultStage = defaultStage;
        this.stages = Collections.unmodifiableMap(new LinkedHashMap<>(stages));
        this.muteVanillaMusic = muteVanillaMusic;
        this.muteOtherMusic = muteOtherMusic;
    }

    public static Music load(String id, Path folder) throws IOException {
        Path normalizedFolder = folder.normalize();
        Path configPath = normalizedFolder.resolve("config.yml");
        if (!Files.isRegularFile(configPath))
            throw new IllegalArgumentException("Missing config.yml for soundtrack '" + id + "'");
        YamlConfiguration rootConfig = YamlConfiguration.loadConfiguration(configPath.toFile());
        boolean muteVanillaMusic = rootConfig.getBoolean("mute-vanilla-music", false);
        boolean muteOtherMusic = rootConfig.getBoolean("mute-other-music", false);

        Path stagesFolder = normalizedFolder.resolve("stages");
        List<Path> rootAudio = listAudio(normalizedFolder);
        if (!Files.isDirectory(stagesFolder)) {
            Stage stage = loadStage(id, LEGACY_STAGE, normalizedFolder);
            return new Music(id, LEGACY_STAGE, Map.of(LEGACY_STAGE, stage), muteVanillaMusic, muteOtherMusic);
        }
        if (!rootAudio.isEmpty())
            throw new IllegalArgumentException("Soundtrack '" + id + "' cannot mix root-level audio with stages/");

        String configuredDefault = rootConfig.getString("default-stage");
        if (configuredDefault == null || configuredDefault.isBlank())
            throw new IllegalArgumentException("Staged soundtrack '" + id + "' requires default-stage");

        Map<String, Stage> loaded = new TreeMap<>();
        Set<String> normalizedIds = new HashSet<>();
        try (var paths = Files.list(stagesFolder)) {
            for (Path stageFolder : paths.filter(Files::isDirectory).sorted().toList()) {
                String stageId = stageFolder.getFileName().toString();
                String normalizedId = stageId.toLowerCase(Locale.ROOT);
                if (!stageId.equals(normalizedId) || !stageId.matches("[a-z0-9][a-z0-9._-]*"))
                    throw new IllegalArgumentException("Invalid stage id '" + stageId + "' in soundtrack '" + id + "'");
                if (!normalizedIds.add(normalizedId))
                    throw new IllegalArgumentException("Duplicate stage id '" + stageId + "' in soundtrack '" + id + "'");
                loaded.put(stageId, loadStage(id, stageId, stageFolder));
            }
        }
        if (loaded.isEmpty()) throw new IllegalArgumentException("Staged soundtrack '" + id + "' requires at least one stage");
        if (!configuredDefault.equals(configuredDefault.toLowerCase(Locale.ROOT)) || !loaded.containsKey(configuredDefault))
            throw new IllegalArgumentException("Unknown default-stage '" + configuredDefault + "' in soundtrack '" + id + "'");
        return new Music(id, configuredDefault, loaded, muteVanillaMusic, muteOtherMusic);
    }

    private static Stage loadStage(String soundtrackId, String stageId, Path folder) throws IOException {
        Path configPath = folder.resolve("config.yml");
        if (!Files.isRegularFile(configPath))
            throw new IllegalArgumentException("Missing config.yml for stage '" + stageId + "' in soundtrack '" + soundtrackId + "'");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configPath.toFile());
        List<Path> audioFiles = listAudio(folder);
        if (audioFiles.isEmpty())
            throw new IllegalArgumentException("No supported audio files in stage '" + stageId + "' of soundtrack '" + soundtrackId + "'");

        List<String> order = yaml.getStringList("order");
        List<Path> sources = new ArrayList<>();
        if (order.isEmpty()) {
            if (audioFiles.size() != 1)
                throw new IllegalArgumentException("Stage '" + stageId + "' of soundtrack '" + soundtrackId + "' requires an order list");
            sources.add(audioFiles.getFirst());
        } else {
            Set<Path> seen = new HashSet<>();
            Path normalizedFolder = folder.normalize();
            for (String name : order) {
                Path candidate = normalizedFolder.resolve(name).normalize();
                if (!Objects.equals(candidate.getParent(), normalizedFolder) || !Files.isRegularFile(candidate)
                        || !AudioDurationReader.isSupported(candidate))
                    throw new IllegalArgumentException("Invalid ordered file '" + name + "' in stage '" + stageId + "'");
                if (!seen.add(candidate)) throw new IllegalArgumentException("Duplicate ordered file '" + name + "' in stage '" + stageId + "'");
                sources.add(candidate);
            }
            if (seen.size() != audioFiles.size() || !seen.containsAll(audioFiles))
                throw new IllegalArgumentException("The order for stage '" + stageId + "' of soundtrack '" + soundtrackId + "' must contain every supported audio file exactly once");
        }

        boolean split = yaml.getBoolean("auto-split.enabled", false);
        double interval = yaml.getDouble("auto-split.interval-seconds", 0);
        if (split && sources.size() != 1) throw new IllegalArgumentException("Auto-split requires exactly one source file");
        if (split && (!Double.isFinite(interval) || interval <= 0))
            throw new IllegalArgumentException("Auto-split interval must be positive");
        long handoffLead = yaml.contains("playback.handoff-lead-milliseconds")
                ? yaml.getLong("playback.handoff-lead-milliseconds") : -1;
        if (handoffLead < -1 || handoffLead > 250)
            throw new IllegalArgumentException("Playback handoff lead must be between 0 and 250 milliseconds");
        return new Stage(stageId, folder, sources, split, interval, handoffLead);
    }

    private static List<Path> listAudio(Path folder) throws IOException {
        try (var files = Files.list(folder)) {
            return files.filter(Files::isRegularFile)
                    .filter(AudioDurationReader::isSupported)
                    .sorted().toList();
        }
    }

    public static boolean build(TexturePack pack, List<Music> music) {
        try {
            Path soundsJson = pack.getAssetPath().resolve("sounds.json");
            JsonObject events = Files.exists(soundsJson)
                    ? JsonParser.parseString(Files.readString(soundsJson)).getAsJsonObject() : new JsonObject();
            for (Music soundtrack : music) {
                if (!soundtrack.build(pack)) return false;
                for (Stage stage : soundtrack.stages.values()) {
                    for (Segment segment : stage.segments) {
                        addSoundEvent(events, segment.eventId(), segment.resourceName(), stage.autoSplit);
                        addSoundEvent(events, segment.spatialEventId(), segment.spatialResourceName(), stage.autoSplit);
                    }
                }
            }
            Files.writeString(soundsJson, new GsonBuilder().setPrettyPrinting().create().toJson(events));
            return true;
        } catch (Exception e) {
            Main.plugin.getLogger().severe("Failed to build music: " + e.getMessage());
            return false;
        }
    }

    static boolean shouldStream(boolean autoSplit) {
        return !autoSplit;
    }

    private static void addSoundEvent(JsonObject events, String id, String resource, boolean autoSplit) {
        boolean stream = shouldStream(autoSplit);
        JsonObject event = new JsonObject();
        JsonArray entries = new JsonArray();
        JsonObject entry = new JsonObject();
        entry.addProperty("name", resource);
        entry.addProperty("stream", stream);
        if (!stream) entry.addProperty("preload", true);
        entries.add(entry);
        event.add("sounds", entries);
        events.add(id, event);
    }

    @Override public boolean build(TexturePack pack) {
        try {
            Path soundtrackRoot = pack.getAssetPath().resolve("sounds/music").resolve(id.replace('.', '/'));
            for (Stage stage : stages.values()) {
                Path stageRoot = soundtrackRoot.resolve(stage.id);
                Path listenerRoot = stageRoot.resolve("listener");
                Path spatialRoot = stageRoot.resolve("spatial");
                List<Path> listener = stage.autoSplit
                        ? Ffmpeg.splitListening(stage.sources.getFirst(), listenerRoot, stage.splitIntervalSeconds)
                        : transcodeSources(stage, listenerRoot, false);
                List<Path> spatial = stage.autoSplit
                        ? Ffmpeg.splitMono(stage.sources.getFirst(), spatialRoot, stage.splitIntervalSeconds)
                        : transcodeSources(stage, spatialRoot, true);
                if (listener.size() != spatial.size())
                    throw new IOException("Listener and spatial audio produced different segment counts");
                List<Duration> timeline = timelineDurations(stage, listener.size());
                List<Segment> result = new ArrayList<>();
                for (int i = 0; i < listener.size(); i++) {
                    String index = String.format(Locale.ROOT, "%04d", i);
                    result.add(new Segment(
                            "music." + id + "." + stage.id + "." + index,
                            "music." + id + "." + stage.id + "." + index + ".spatial",
                            "assetchannel:music/" + id.replace('.', '/') + "/" + stage.id + "/listener/" + index,
                            "assetchannel:music/" + id.replace('.', '/') + "/" + stage.id + "/spatial/" + index,
                            timeline.get(i)
                    ));
                }
                stage.segments = List.copyOf(result);
            }
            return true;
        } catch (Exception e) {
            Main.plugin.getLogger().severe("Failed to build soundtrack '" + id + "': " + e.getMessage());
            return false;
        }
    }

    private static List<Path> transcodeSources(Stage stage, Path root, boolean mono) throws IOException, InterruptedException {
        Files.createDirectories(root);
        List<Path> result = new ArrayList<>();
        for (int i = 0; i < stage.sources.size(); i++) {
            Path destination = root.resolve(String.format(Locale.ROOT, "%04d.ogg", i));
            if (mono) Ffmpeg.transcodeMono(stage.sources.get(i), destination);
            else Ffmpeg.transcodeListening(stage.sources.get(i), destination);
            result.add(destination);
        }
        return result;
    }

    private static List<Duration> timelineDurations(Stage stage, int segmentCount) throws IOException {
        if (!stage.autoSplit) {
            List<Duration> durations = new ArrayList<>();
            for (Path source : stage.sources) durations.add(AudioDurationReader.read(source));
            return List.copyOf(durations);
        }
        Duration sourceDuration = AudioDurationReader.read(stage.sources.getFirst());
        return splitTimelineDurations(sourceDuration, stage.splitIntervalSeconds, segmentCount);
    }

    static List<Duration> splitTimelineDurations(Duration sourceDuration, double intervalSeconds, int segmentCount)
            throws IOException {
        long intervalNanos = Math.max(1, Math.round(intervalSeconds * 1_000_000_000d));
        long sourceNanos = sourceDuration.toNanos();
        long expectedCount = (sourceNanos + intervalNanos - 1) / intervalNanos;
        if (segmentCount != expectedCount)
            throw new IOException("FFmpeg produced " + segmentCount + " segments; expected " + expectedCount);
        List<Duration> durations = new ArrayList<>();
        for (int i = 0; i < segmentCount; i++) {
            long consumed = Math.multiplyExact((long) i, intervalNanos);
            long remaining = sourceNanos - consumed;
            if (remaining <= 0) throw new IOException("FFmpeg produced more segments than the source timeline");
            durations.add(Duration.ofNanos(Math.min(intervalNanos, remaining)));
        }
        return List.copyOf(durations);
    }

    @Override public String getId() { return id; }
    public String getDefaultStage() { return defaultStage; }
    public boolean isMuteVanillaMusic() { return muteVanillaMusic; }
    public boolean isMuteOtherMusic() { return muteOtherMusic; }
    public Map<String, Stage> getStages() { return stages; }
    public Stage getStage(String stage) { return stages.get(stage); }
    public List<Segment> getSegments() { return stages.get(defaultStage).segments; }
    public boolean isAutoSplit() { return stages.get(defaultStage).autoSplit; }
    public Duration getDuration() { return stages.get(defaultStage).duration(); }

    public static final class Stage {
        private final String id;
        private final Path folder;
        private final List<Path> sources;
        private final boolean autoSplit;
        private final double splitIntervalSeconds;
        private final long handoffLeadMilliseconds;
        private List<Segment> segments = List.of();

        private Stage(String id, Path folder, List<Path> sources, boolean autoSplit, double splitIntervalSeconds,
                      long handoffLeadMilliseconds) {
            this.id = id;
            this.folder = folder;
            this.sources = List.copyOf(sources);
            this.autoSplit = autoSplit;
            this.splitIntervalSeconds = splitIntervalSeconds;
            this.handoffLeadMilliseconds = handoffLeadMilliseconds;
        }
        public String id() { return id; }
        public List<Segment> segments() { return segments; }
        public boolean autoSplit() { return autoSplit; }
        public long handoffLeadMilliseconds() { return handoffLeadMilliseconds; }
        public Duration duration() { return segments.stream().map(Segment::duration).reduce(Duration.ZERO, Duration::plus); }
    }

    public record Segment(String eventId, String spatialEventId, String resourceName,
                          String spatialResourceName, Duration duration) {
        public String key(boolean spatial) {
            return "assetchannel:" + (spatial ? spatialEventId : eventId);
        }
    }
}
