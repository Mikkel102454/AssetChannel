package nu.miguel.assetChannel.api;

import net.kyori.adventure.key.Key;
import java.util.Objects;

/** Definition of an item-model-backed block, its collision carrier and presentation effects. */
public record BlockAsset(String id, String model, String carrier, String particleBlock, String particleModel,
                         String breakSound, String placeSound, int particleCount, boolean drops) {
    public BlockAsset {
        Objects.requireNonNull(id); Objects.requireNonNull(model); Objects.requireNonNull(carrier); Objects.requireNonNull(particleBlock);
        if (!id.matches("[a-z0-9._-]+")) throw new IllegalArgumentException("Invalid block asset id: "+id);
        Key.key(model); if(particleModel!=null)Key.key(particleModel);
        if(breakSound!=null)Key.key(breakSound); if(placeSound!=null)Key.key(placeSound);
        if(particleCount<0||particleCount>256)throw new IllegalArgumentException("particle-count must be 0..256");
        for(String data:java.util.List.of(carrier,particleBlock))
            if(!data.matches("(?:minecraft:)?[a-z0-9_]+(?:\\[[a-z0-9_=,]+\\])?"))throw new IllegalArgumentException("Invalid vanilla block data: "+data);
    }
}
