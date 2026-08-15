package nu.miguel.assetChannel.texturepack;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TexturePackTest {
    @Test void emitsMinecraft262PackMetadata() {
        var pack = JsonParser.parseString(TexturePack.metadata(88, 0, "test"))
                .getAsJsonObject().getAsJsonObject("pack");

        assertEquals(88, pack.getAsJsonArray("min_format").get(0).getAsInt());
        assertEquals(0, pack.getAsJsonArray("min_format").get(1).getAsInt());
        assertEquals(pack.get("min_format"), pack.get("max_format"));
    }
}
