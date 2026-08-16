package nu.miguel.assetChannel.asset;

import nu.miguel.assetChannel.texturepack.TexturePack;
import nu.miguel.assetChannel.util.Ffmpeg;

import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import java.time.Duration;
import com.google.gson.*;
import java.util.List;
import nu.miguel.assetChannel.util.AudioDurationReader;

public class Sound implements Asset{
    String id;
    Path path;
    private final Duration duration;

    public Sound(String id, Path path) throws IOException {
        this.id = id;
        this.path = path;
        this.duration = AudioDurationReader.read(path);
    }

    public static boolean build(TexturePack texturePack, List<Sound> sounds) {
        try {
            JsonObject events = new JsonObject();
            for (Sound sound : sounds) {
                if (!sound.build(texturePack)) return false;
                JsonObject event = new JsonObject();
                JsonArray entries = new JsonArray();
                entries.add(resourceName(sound.id, false));
                event.add("sounds", entries);
                events.add(sound.id, event);

                JsonObject spatialEvent = new JsonObject();
                JsonArray spatialEntries = new JsonArray();
                spatialEntries.add(resourceName(sound.id, true));
                spatialEvent.add("sounds", spatialEntries);
                events.add(sound.id + ".spatial", spatialEvent);
            }
            Files.writeString(texturePack.getAssetPath().resolve("sounds.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(events));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public boolean build(TexturePack texturePack) {
        try {
            Path listener = texturePack.getAssetPath().resolve("sounds/listener")
                    .resolve(id.replace('.', '/') + ".ogg");
            Path spatial = texturePack.getAssetPath().resolve("sounds/spatial")
                    .resolve(id.replace('.', '/') + ".ogg");
            Files.createDirectories(listener.getParent());
            Files.createDirectories(spatial.getParent());
            Ffmpeg.transcodeListening(path, listener);
            Ffmpeg.transcodeMono(path, spatial);
            return true;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public String getId() {
        return id;
    }

    public String getKey() { return "assetchannel:" + id; }
    public String getSpatialKey() { return "assetchannel:" + id + ".spatial"; }
    public Duration getDuration() { return duration; }

    static String resourceName(String id, boolean spatial) {
        return "assetchannel:" + (spatial ? "spatial/" : "listener/") + id.replace('.', '/');
    }
}
