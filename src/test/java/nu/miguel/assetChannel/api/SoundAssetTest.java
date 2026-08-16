package nu.miguel.assetChannel.api;

import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class SoundAssetTest {
    @Test void exposesDuration() {
        SoundAsset asset = new SoundAsset("npc.talk", Key.key("assetchannel:npc.talk"),
                Duration.ofMillis(1250));

        assertEquals(Duration.ofMillis(1250), asset.duration());
    }

    @Test void requiresPositiveDuration() {
        Key key = Key.key("assetchannel:npc.talk");
        assertThrows(IllegalArgumentException.class,
                () -> new SoundAsset("npc.talk", key, Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new SoundAsset("npc.talk", key, Duration.ofNanos(-1)));
    }
}
