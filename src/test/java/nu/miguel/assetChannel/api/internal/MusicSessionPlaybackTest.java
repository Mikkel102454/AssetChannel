package nu.miguel.assetChannel.api.internal;

import org.bukkit.SoundCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MusicSessionPlaybackTest {
    @Test void separatesAssetChannelRecordsFromVanillaMusic() {
        assertEquals(SoundCategory.RECORDS, MusicSessionImpl.SOUNDTRACK_CATEGORY);
        assertEquals(SoundCategory.MUSIC, MusicSessionImpl.VANILLA_MUSIC_CATEGORY);
    }
}
