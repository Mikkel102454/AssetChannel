package nu.miguel.assetChannel.api;

import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModelAssetTest {
    @Test void descriptorUsesAnItemDefinitionKeyAndRejectsMissingIdentity() {
        var asset = new ModelAsset("demo:ui/button", Key.key("demo:ui/button"));
        assertEquals("demo:ui/button", asset.key().asString());
        assertFalse(asset.internal(), "Original two-argument constructor remains public");
        assertTrue(new ModelAsset("demo:alias", Key.key("demo:alias"), true).internal());
        assertThrows(NullPointerException.class, () -> new ModelAsset(null, asset.key()));
        assertThrows(IllegalArgumentException.class, () -> asset.createItem(org.bukkit.Material.AIR));
    }
}
