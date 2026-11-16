package nu.miguel.assetChannel.asset;

import nu.miguel.assetChannel.api.BlockAsset;
import nu.miguel.assetChannel.texturepack.TexturePack;
import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.Path;

public record BlockDefinition(BlockAsset descriptor, Setup setup) implements Asset {
    public BlockDefinition(BlockAsset descriptor) { this(descriptor, null); }

    public record ParticleTexture(String texture, java.util.List<Integer> region) {
        public ParticleTexture {
            net.kyori.adventure.key.Key.key(texture);
            if (region != null) {
                region = java.util.List.copyOf(region);
                if (region.size() != 4 || region.get(0) < 0 || region.get(1) < 0 || region.get(2) <= 0 || region.get(3) <= 0)
                    throw new IllegalArgumentException("particles.region requires [x, y, width, height] in pixels");
            }
        }
    }
    public record Setup(String render, String geometry, ParticleTexture particles) {}

    public static BlockDefinition load(String id, Path path) {
        var y=YamlConfiguration.loadConfiguration(path.toFile());
        String model=y.getString("model");
        if(model==null||model.isBlank())throw new IllegalArgumentException("Block "+id+" requires model");
        Setup setup = null;
        String state = y.getString("block-state"), geometry = y.getString("block-model");
        String particleModel = y.getString("particle-model");
        int particleCount = y.getInt("particle-count",20);
        if (y.contains("render")) {
            String render = y.getString("render");
            if (render == null || !java.util.Set.of("block", "display").contains(render))
                throw new IllegalArgumentException("Block " + id + " render must be block or display");
            if (render.equals("display") && state != null)
                throw new IllegalArgumentException("render: display cannot specify block-state");
            ParticleTexture particles = null;
            if (y.isConfigurationSection("particles")) {
                String texture = y.getString("particles.texture");
                java.util.List<Integer> region = null;
                if (y.contains("particles.region")) {
                    var values = y.getList("particles.region");
                    if (values == null || values.size() != 4) throw new IllegalArgumentException("particles.region requires four integer pixel coordinates");
                    region = new java.util.ArrayList<>();
                    for (Object value : values) {
                        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue()) || number.doubleValue() != number.intValue())
                            throw new IllegalArgumentException("particles.region requires integer pixel coordinates");
                        region.add(number.intValue());
                    }
                }
                if (texture == null && region != null) throw new IllegalArgumentException("particles.region needs particles.texture");
                if (texture != null) particles = new ParticleTexture(texture, region);
                particleCount = y.getInt("particles.count", particleCount);
            } else if (y.contains("particles")) {
                if (!y.isBoolean("particles")) throw new IllegalArgumentException("particles must be true, false or a texture/count section");
                if (!y.getBoolean("particles")) particleCount = 0;
            }
            if (particles != null && particleModel != null) throw new IllegalArgumentException("Use particles.texture or particle-model, not both");
            setup = new Setup(render, geometry, particles);
            if (particleModel == null) particleModel = model;
            if (state != null && geometry == null) geometry = model;
            if (state == null) geometry = null; // Geometry is resolved during content preparation.
        }
        return new BlockDefinition(new BlockAsset(id,model,y.getString("carrier","minecraft:barrier"),
                y.getString("particle-block","minecraft:stone"),particleModel,
                y.getString("break-sound","minecraft:block.stone.break"),y.getString("place-sound","minecraft:block.stone.place"),
                particleCount,y.getBoolean("drops",true),state,geometry),setup);
    }
    @Override public String getId(){return descriptor.id();}
    @Override public boolean build(TexturePack pack){return true;}
}
