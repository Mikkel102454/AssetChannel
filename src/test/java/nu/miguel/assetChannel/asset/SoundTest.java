package nu.miguel.assetChannel.asset;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SoundTest {
    @Test void resourceNamesAreRelativeToTheNamespaceSoundsFolder() {
        assertEquals("assetchannel:listener/npc/hello_there",
                Sound.resourceName("npc.hello_there", false));
        assertEquals("assetchannel:spatial/npc/hello_there",
                Sound.resourceName("npc.hello_there", true));
    }
}
