package nu.miguel.assetChannel.pack;

import nu.miguel.assetChannel.asset.Asset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PackDeveloperTest {
    @TempDir Path directory;

    @Test void discoversMp3AndOggCaseInsensitively() throws Exception {
        Files.write(directory.resolve("effect.ogg"), new byte[]{1});
        Files.write(directory.resolve("effect.MP3"), new byte[]{1});
        Files.write(directory.resolve("ignored.wav"), new byte[]{1});

        assertEquals(List.of("effect", "effect"), PackDeveloper.scanSoundFiles(directory).stream()
                .map(AssetFile::id).sorted().toList());
    }

    @Test void rejectsSameStemSoundIds() {
        Asset first = new StubSound("effect");
        Asset second = new StubSound("effect");
        assertThrows(IllegalArgumentException.class,
                () -> PackDeveloper.validateAssets(List.of(first, second)));
    }

    private record StubSound(String id) implements Asset {
        @Override public boolean build(nu.miguel.assetChannel.texturepack.TexturePack texturePack) { return true; }
        @Override public String getId() { return id; }
    }
}
