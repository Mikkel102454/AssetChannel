package nu.miguel.assetChannel.texturepack;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

class TexturePackTest {
    @Test
    void emitsMinecraft263PackMetadata() {
        var pack =
                JsonParser.parseString(
                                TexturePack.metadata(
                                        TexturePack.RESOURCE_PACK_FORMAT_MAJOR,
                                        TexturePack.RESOURCE_PACK_FORMAT_MINOR,
                                        "test"))
                        .getAsJsonObject()
                        .getAsJsonObject("pack");

        assertEquals(97, pack.getAsJsonArray("min_format").get(0).getAsInt());
        assertEquals(1, pack.getAsJsonArray("min_format").get(1).getAsInt());
        assertEquals(pack.get("min_format"), pack.get("max_format"));
    }
}
