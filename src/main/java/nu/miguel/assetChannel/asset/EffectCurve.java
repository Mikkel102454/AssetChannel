package nu.miguel.assetChannel.asset;

import net.kyori.adventure.key.Key;

import nu.miguel.assetChannel.api.*;
import nu.miguel.assetChannel.texturepack.TexturePack;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/** Loads the current compositor contract directly from imported asset definitions. */
public final class EffectCurve implements Asset {
    private final EffectCurveAsset descriptor;

    private EffectCurve(EffectCurveAsset descriptor) {
        this.descriptor = descriptor;
    }

    public static EffectCurve load(String id, Path path) throws IOException {
        var yaml = new YamlConfiguration();
        yaml.options().pathSeparator('/');
        try {
            yaml.load(path.toFile());
        } catch (org.bukkit.configuration.InvalidConfigurationException error) {
            throw new IOException(path + ": " + error.getMessage(), error);
        }
        if (yaml.contains("profiles")
                || !yaml.getString("client/protocol", "").equals("clock-composite-v1"))
            throw new IllegalArgumentException(
                    path + ": expected clock-composite-v1 with an explicit range");
        var kind =
                EffectCurveAsset.Kind.valueOf(yaml.getString("kind", "").toUpperCase(Locale.ROOT));
        var group = key(yaml, "client/group");
        var composition =
                new ClientEffectComposition(
                        Key.key(
                                "assetchannel",
                                "generated/composite/" + group.namespace() + "/" + group.value()),
                        key(yaml, "client/vertex-shader"),
                        key(yaml, "client/fragment-shader"),
                        kind,
                        yaml.getDouble("neutral", Double.NaN));
        var client =
                new ClientEffectAsset(
                        Key.key("assetchannel:generated/client/" + id.replace('.', '/')),
                        key(yaml, "client/writer-vertex"),
                        key(yaml, "client/writer-fragment"),
                        key(yaml, "client/copy-vertex"),
                        key(yaml, "client/copy-fragment"),
                        yaml.getDouble("range/min", Double.NaN),
                        yaml.getDouble("range/max", Double.NaN),
                        composition);
        return new EffectCurve(new EffectCurveAsset(id, client));
    }

    private static Key key(YamlConfiguration yaml, String path) {
        return Key.key(
                Objects.requireNonNull(yaml.getString(path), "Missing effect field: " + path));
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
