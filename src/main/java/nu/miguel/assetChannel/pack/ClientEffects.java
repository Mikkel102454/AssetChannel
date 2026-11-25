package nu.miguel.assetChannel.pack;

import com.google.gson.*;

import net.kyori.adventure.key.Key;

import nu.miguel.assetChannel.api.*;
import nu.miguel.assetChannel.asset.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Generates native control definitions from imported shader assets. No shader source is embedded
 * here.
 */
public final class ClientEffects {
    private ClientEffects() {}

    public static ImportedResources prepare(ImportedResources imported, List<Asset> assets) {
        var files = new TreeMap<>(imported.sourceFiles());
        var groups = new TreeMap<Key, List<ClientEffectAsset>>(Comparator.comparing(Key::asString));
        for (var entry : assets) {
            if (!(entry instanceof EffectCurve curve)) continue;
            var client = curve.descriptor().client();
            var composition = client.composition();
            requireShader(files, client.writerVertex(), ".vsh");
            requireShader(files, client.writerFragment(), ".fsh");
            requireShader(files, client.copyVertex(), ".vsh");
            requireShader(files, client.copyShader(), ".fsh");
            requireShader(files, composition.vertexShader(), ".vsh");
            requireShader(files, composition.fragmentShader(), ".fsh");
            groups.computeIfAbsent(composition.prefix(), ignored -> new ArrayList<>()).add(client);
            var reset = pass(client.writerVertex(), client.writerFragment(), "minecraft:main");
            uniform(
                    reset,
                    "ControlConfig",
                    composition.offset(),
                    0,
                    ClientEffectComposition.STRIDE,
                    0);
            put(files, client.reset(), chain(new JsonObject(), reset));
            for (int slot = 0; slot < client.controlBytes(); slot++) {
                for (int value = 1; value < 256; value++) {
                    var write =
                            pass(client.writerVertex(), client.writerFragment(), "minecraft:main");
                    uniform(write, "ControlConfig", composition.offset() + slot, value, 1, 0);
                    put(files, client.control(slot, value), chain(new JsonObject(), write));
                }
            }
        }
        groups.values().forEach(group -> compose(files, group));
        return ImportedResources.prepared(files);
    }

    private static void compose(Map<String, byte[]> files, List<ClientEffectAsset> group) {
        var first = group.getFirst();
        var composition = first.composition();
        var channels =
                new EnumMap<EffectCurveAsset.Kind, ClientEffectAsset>(EffectCurveAsset.Kind.class);
        for (var asset : group) {
            var c = asset.composition();
            if (!c.vertexShader().equals(composition.vertexShader())
                    || !c.fragmentShader().equals(composition.fragmentShader())
                    || !asset.copyVertex().equals(first.copyVertex())
                    || !asset.copyShader().equals(first.copyShader()))
                throw new IllegalArgumentException(
                        "Conflicting compositor shaders: " + composition.prefix());
            var previous = channels.putIfAbsent(c.channel(), asset);
            if (previous != null
                    && (previous.minimum() != asset.minimum()
                            || previous.maximum() != asset.maximum()
                            || previous.composition().neutral() != c.neutral()))
                throw new IllegalArgumentException(
                        "Conflicting compositor channel bounds: " + composition.prefix());
        }
        int available =
                channels.values().stream()
                        .mapToInt(a -> a.composition().mask())
                        .reduce(0, (a, b) -> a | b);
        for (int mask = 1; mask < 32; mask++) {
            if ((mask & available) != mask) continue;
            var targets = new JsonObject();
            targets.add("composite", new JsonObject());
            var render =
                    pass(composition.vertexShader(), composition.fragmentShader(), "composite");
            input(render, "minecraft:main", true);
            uniform(render, "CompositorConfig", mask, 24000, 0, 0);
            var bounds = new JsonArray();
            for (var kind : EffectCurveAsset.Kind.values()) {
                var asset = channels.get(kind);
                bounds.add(
                        uniformValue(
                                "Channel" + kind.ordinal(),
                                asset == null ? 0 : asset.minimum(),
                                asset == null ? 1 : asset.maximum(),
                                asset == null ? 0 : asset.controlBytes(),
                                kind.ordinal() * ClientEffectComposition.STRIDE));
            }
            render.getAsJsonObject("uniforms").add("ChannelBounds", bounds);
            // The imported compositor vertex has additional outputs; use the ordinary screenquad
            // for blit.
            var copy = pass(first.copyVertex(), first.copyShader(), "minecraft:main");
            input(copy, "composite", false);
            uniform(copy, "BlitConfig", 1, 1, 1, 1);
            var chain = chain(targets, render, copy);
            put(files, composition.renderer(mask), chain);
            for (var asset : group)
                if (asset.composition().mask() == mask) put(files, asset.renderer(), chain);
        }
    }

    private static void requireShader(Map<String, byte[]> files, Key key, String extension) {
        String path = key.namespace() + "/shaders/" + key.value() + extension;
        byte[] source = files.get(path);
        if (source == null) {
            if (!key.namespace().equals("minecraft"))
                throw new IllegalArgumentException(
                        "Client effect references missing imported shader: " + key);
            return;
        }
        if (java.util.regex.Pattern.compile("(?m)^\\s*#\\s*moj_import\\b")
                .matcher(new String(source, StandardCharsets.UTF_8))
                .find())
            throw new IllegalArgumentException(
                    path + ": Minecraft 26.3 requires #include instead of #moj_import");
    }

    private static JsonObject pass(Key vertex, Key fragment, String output) {
        var pass = new JsonObject();
        pass.addProperty("vertex_shader", vertex.asString());
        pass.addProperty("fragment_shader", fragment.asString());
        pass.addProperty("output", output);
        pass.add("inputs", new JsonArray());
        return pass;
    }

    private static void input(JsonObject pass, String target, boolean bilinear) {
        var inputs = new JsonArray();
        var input = new JsonObject();
        input.addProperty("sampler_name", "In");
        input.addProperty("target", target);
        input.addProperty("bilinear", bilinear);
        inputs.add(input);
        pass.add("inputs", inputs);
    }

    private static void uniform(JsonObject pass, String block, double... values) {
        var uniforms = new JsonObject();
        var list = new JsonArray();
        list.add(uniformValue("Parameters", values));
        uniforms.add(block, list);
        pass.add("uniforms", uniforms);
    }

    private static JsonObject uniformValue(String name, double... values) {
        var item = new JsonObject();
        item.addProperty("name", name);
        item.addProperty("type", "vec4");
        var data = new JsonArray();
        for (double value : values) data.add(value);
        item.add("value", data);
        return item;
    }

    private static JsonObject chain(JsonObject targets, JsonObject... passes) {
        var chain = new JsonObject();
        chain.add("targets", targets);
        var list = new JsonArray();
        for (var pass : passes) list.add(pass);
        chain.add("passes", list);
        return chain;
    }

    private static void put(Map<String, byte[]> files, Key key, JsonObject value) {
        String path = key.namespace() + "/post_effect/" + key.value() + ".json";
        if (files.putIfAbsent(path, value.toString().getBytes(StandardCharsets.UTF_8)) != null)
            throw new IllegalArgumentException("Client effect resource collision: " + key);
    }
}
