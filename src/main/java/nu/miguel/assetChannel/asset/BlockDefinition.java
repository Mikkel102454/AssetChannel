package nu.miguel.assetChannel.asset;

import nu.miguel.assetChannel.api.BlockAsset;
import nu.miguel.assetChannel.texturepack.TexturePack;
import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.Path;

public record BlockDefinition(BlockAsset descriptor) implements Asset {
    public static BlockDefinition load(String id, Path path) {
        var y=YamlConfiguration.loadConfiguration(path.toFile());
        String model=y.getString("model");
        if(model==null||model.isBlank())throw new IllegalArgumentException("Block "+id+" requires model");
        return new BlockDefinition(new BlockAsset(id,model,y.getString("carrier","minecraft:barrier"),
                y.getString("particle-block","minecraft:stone"),y.getString("particle-model"),
                y.getString("break-sound","minecraft:block.stone.break"),y.getString("place-sound","minecraft:block.stone.place"),
                y.getInt("particle-count",20),y.getBoolean("drops",true)));
    }
    @Override public String getId(){return descriptor.id();}
    @Override public boolean build(TexturePack pack){return true;}
}
