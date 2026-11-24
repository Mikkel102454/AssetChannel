package nu.miguel.assetChannel.pack;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.UUID;

class PackWebServerTest {
    @Test
    void recognizesOnlyAssetChannelsPackId() {
        assertTrue(PackWebServer.ownsPack(UUID.fromString("2c6f8b1d-45a8-4c8e-9b2e-4f5f7e2a1c11")));
        assertFalse(PackWebServer.ownsPack(UUID.randomUUID()));
    }
}
