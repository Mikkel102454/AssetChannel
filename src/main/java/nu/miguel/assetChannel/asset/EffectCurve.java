package nu.miguel.assetChannel.asset;

import net.kyori.adventure.key.Key;

import nu.miguel.assetChannel.api.*;
import nu.miguel.assetChannel.texturepack.TexturePack;

import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Path;
import java.util.*;

public final class EffectCurve implements Asset {
    private final EffectCurveAsset descriptor;

    private EffectCurve(EffectCurveAsset descriptor) {
        this.descriptor = descriptor;
    }

    public static EffectCurve load(String id, Path path) throws java.io.IOException {
        var yaml = new YamlConfiguration();
        yaml.options().pathSeparator('/');
        try {
            yaml.load(path.toFile());
        } catch (org.bukkit.configuration.InvalidConfigurationException error) {
            throw new java.io.IOException(path + ": " + error.getMessage(), error);
        }
        var section =
                Objects.requireNonNull(
                        yaml.getConfigurationSection("profiles"),
                        "Missing effect profiles: " + path);
        var profiles = new TreeMap<Double, Key>();
        section.getValues(false)
                .forEach((value, key) -> {
                    if (profiles.putIfAbsent(Double.parseDouble(value), Key.key(key.toString()))
                            != null)
                        throw new IllegalArgumentException("Duplicate effect value: " + value);
                });
        var kind =
                EffectCurveAsset.Kind.valueOf(yaml.getString("kind", "").toUpperCase(Locale.ROOT));
        ClientEffectAsset client = null;
        if (yaml.isConfigurationSection("client")) {
            var config = yaml.getConfigurationSection("client");
            if (kind != EffectCurveAsset.Kind.ZOOM
                    || !Set.of("clock-zoom-v1", "clock-zoom-v2")
                            .contains(config.getString("protocol", "")))
                throw new IllegalArgumentException("Unsupported client effect protocol: " + path);
            client =
                    new ClientEffectAsset(
                            Key.key("assetchannel:generated/client/" + id.replace('.', '/')),
                            Key.key(Objects.requireNonNull(config.getString("writer-vertex"))),
                            Key.key(Objects.requireNonNull(config.getString("writer-fragment"))),
                            Key.key(Objects.requireNonNull(config.getString("vertex-shader"))),
                            Key.key(Objects.requireNonNull(config.getString("fragment-shader"))),
                            Key.key(Objects.requireNonNull(config.getString("copy-shader"))),
                            profiles.firstKey(),
                            profiles.lastKey(),
                            config.getString("protocol", "").equals("clock-zoom-v2") ? 2 : 1);
        }
        return new EffectCurve(new EffectCurveAsset(id, kind, profiles, client));
    }

    public EffectCurveAsset descriptor() {
        return descriptor;
    }

    public String getId() {
        return descriptor.id();
    }

    public boolean build(TexturePack pack) {
        return true;
    }
}
