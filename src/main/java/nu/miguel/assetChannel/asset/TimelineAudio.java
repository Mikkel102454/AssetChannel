package nu.miguel.assetChannel.asset;

import com.google.gson.*;

import nu.miguel.assetChannel.api.TimelineAudioAsset;
import nu.miguel.assetChannel.texturepack.TexturePack;
import nu.miguel.assetChannel.util.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class TimelineAudio implements Asset {
    private final Path source;
    private final TimelineAudioAsset asset;

    public TimelineAudio(String id, Path source) throws IOException {
        this.source = source;
        double seconds = AudioDurationReader.read(source).toNanos() / 1e9;
        asset = new TimelineAudioAsset(id, seconds, 1, (int) Math.ceil(seconds));
    }

    public TimelineAudioAsset descriptor() {
        return asset;
    }

    @Override
    public String getId() {
        return asset.id();
    }

    public static void buildAll(TexturePack pack, List<Asset> assets)
            throws IOException, InterruptedException {
        JsonObject events = new JsonObject();
        Path root = pack.getRootPath().resolve("assets/assetchannel_scene");
        for (Asset input : assets)
            if (input instanceof TimelineAudio audio) {
                var listening =
                        Ffmpeg.splitTimeline(
                                audio.source,
                                root.resolve("sounds/listener/" + audio.getId().replace('.', '/')),
                                1,
                                false);
                var spatial =
                        Ffmpeg.splitTimeline(
                                audio.source,
                                root.resolve("sounds/spatial/" + audio.getId().replace('.', '/')),
                                1,
                                true);
                if (listening.size() != audio.asset.segments()
                        || spatial.size() != audio.asset.segments())
                    throw new IOException("Unexpected audio clip count: " + audio.getId());
                for (int i = 0; i < listening.size(); i++)
                    for (int lane = 0; lane < 16; lane++)
                        for (boolean mono : new boolean[] {false, true}) {
                            Path file = (mono ? spatial : listening).get(i);
                            String resource =
                                    "assetchannel_scene:"
                                            + root.resolve("sounds")
                                                    .relativize(file)
                                                    .toString()
                                                    .replace('\\', '/')
                                                    .replaceAll("\\.ogg$", "");
                            JsonObject entry = new JsonObject();
                            entry.addProperty("name", resource);
                            entry.addProperty("preload", true);
                            JsonArray sounds = new JsonArray();
                            sounds.add(entry);
                            JsonObject event = new JsonObject();
                            event.add("sounds", sounds);
                            events.add(
                                    audio.asset
                                            .key(i, lane, mono)
                                            .substring("assetchannel_scene:".length()),
                                    event);
                        }
            }
        if (!events.isEmpty()) {
            Files.createDirectories(root);
            Files.writeString(root.resolve("sounds.json"), events.toString());
        }
    }

    @Override
    public boolean build(TexturePack pack) {
        return true;
    }
}
