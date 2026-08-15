package nu.miguel.assetChannel.asset;

import nu.miguel.assetChannel.texturepack.TexturePack;
import nu.miguel.assetChannel.util.Ffmpeg;

import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import com.google.gson.*;
import java.util.List;

public class Sound implements Asset{
    String id;
    Path path;

    public Sound(String id, Path path) {
        this.id = id;
        this.path = path;
    }

    public static boolean build(TexturePack texturePack, List<Sound> sounds) {
        try {
            JsonObject events = new JsonObject();
            for (Sound sound : sounds) {
                if (!sound.build(texturePack)) return false;
                JsonObject event = new JsonObject();
                JsonArray entries = new JsonArray();
                entries.add("assetchannel:sounds/listener/" + sound.id.replace('.', '/'));
                event.add("sounds", entries);
                events.add(sound.id, event);

                JsonObject spatialEvent = new JsonObject();
                JsonArray spatialEntries = new JsonArray();
                spatialEntries.add("assetchannel:sounds/spatial/" + sound.id.replace('.', '/'));
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
}
