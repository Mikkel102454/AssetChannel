package nu.miguel.assetChannel.asset;

import nu.miguel.assetChannel.texturepack.TexturePack;

public interface Asset {
    boolean build(TexturePack texturePack);

    String getId();
}
