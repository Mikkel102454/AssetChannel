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
        for (var entry : assets)
            if (entry instanceof EffectCurve curve && curve.descriptor().client() != null) {
                var client = curve.descriptor().client();
                requireShader(files, client.writerVertex(), ".vsh");
                requireShader(files, client.writerFragment(), ".fsh");
                requireShader(files, client.vertexShader(), ".vsh");
                requireShader(files, client.fragmentShader(), ".fsh");
                requireShader(files, client.copyShader(), ".fsh");
                for (int slot = 0; slot < client.controlBytes(); slot++)
                    for (int value = 0; value < 256; value++) {
                        var pass =
                                pass(
                                        client.writerVertex(),
                                        client.writerFragment(),
                                        "minecraft:main");
                        uniform(pass, "ControlConfig", slot, value, 0, 0);
                        put(files, client.control(slot, value), chain(new JsonObject(), pass));
                    }
                var targets = new JsonObject();
                targets.add("client_zoom", new JsonObject());
                var render = pass(client.vertexShader(), client.fragmentShader(), "client_zoom");
                input(render, "minecraft:main", true);
                uniform(
                        render,
                        "ClientZoomConfig",
                        client.minimum(),
                        client.maximum(),
                        client.controlBytes(),
                        24000);
                var copy = pass(client.vertexShader(), client.copyShader(), "minecraft:main");
                input(copy, "client_zoom", false);
                uniform(copy, "BlitConfig", 1, 1, 1, 1);
                put(files, client.renderer(), chain(targets, render, copy));
            }
        return ImportedResources.prepared(files);
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
        var item = new JsonObject();
        item.addProperty("name", "Parameters");
        item.addProperty("type", "vec4");
        var data = new JsonArray();
        for (double value : values) data.add(value);
        item.add("value", data);
        list.add(item);
        uniforms.add(block, list);
        pass.add("uniforms", uniforms);
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
