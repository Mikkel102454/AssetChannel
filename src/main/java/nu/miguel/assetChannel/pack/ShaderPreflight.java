package nu.miguel.assetChannel.pack;

import com.google.gson.*;

import net.kyori.adventure.key.Key;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/** Static preflight of imported shader dependencies and native post-effect structure. */
final class ShaderPreflight {
    private static final Pattern INCLUDE =
            Pattern.compile("(?m)^\\s*#\\s*include\\s*[<\"]([^>\"]+)[>\"]");
    private static final Pattern LEGACY = Pattern.compile("(?m)^\\s*#\\s*moj_import\\b");

    static void validate(Map<String, byte[]> files, Map<String, JsonObject> json) {
        var done = new HashSet<String>();
        for (String path : files.keySet())
            if (path.contains("/shaders/")
                    && (path.endsWith(".vsh") || path.endsWith(".fsh") || path.endsWith(".glsl")))
                shader(path, files, done, new LinkedHashSet<>());
        for (var entry : json.entrySet())
            if (entry.getKey().contains("/post_effect/"))
                post(entry.getKey(), entry.getValue(), files);
    }

    private static void shader(
            String path, Map<String, byte[]> files, Set<String> done, Set<String> visiting) {
        if (done.contains(path)) return;
        if (!visiting.add(path))
            throw new IllegalArgumentException(
                    "Shader include cycle: " + String.join(" -> ", visiting) + " -> " + path);
        String source = new String(files.get(path), StandardCharsets.UTF_8);
        if (LEGACY.matcher(source).find())
            throw new IllegalArgumentException(
                    path + ": Minecraft 26.3 requires #include instead of #moj_import");
        var includes = INCLUDE.matcher(source);
        while (includes.find()) {
            Key key = Key.key(includes.group(1));
            String dependency = key.namespace() + "/shaders/include/" + key.value();
            if (files.containsKey(dependency)) shader(dependency, files, done, visiting);
            else if (!key.namespace().equals("minecraft"))
                throw new IllegalArgumentException(
                        path + " references missing shader include " + dependency);
        }
        visiting.remove(path);
        done.add(path);
    }

    private static void post(String path, JsonObject value, Map<String, byte[]> files) {
        if (!value.has("passes") || !value.get("passes").isJsonArray())
            throw new IllegalArgumentException(path + ": post effect needs passes");
        var targets = new HashSet<String>();
        targets.add("minecraft:main");
        if (value.has("targets"))
            value.getAsJsonObject("targets")
                    .keySet()
                    .forEach(t -> targets.add(Key.key(t).asString()));
        for (var element : value.getAsJsonArray("passes")) {
            var pass = element.getAsJsonObject();
            requireShader(pass, "vertex_shader", ".vsh", path, files);
            requireShader(pass, "fragment_shader", ".fsh", path, files);
            if (!pass.has("output"))
                throw new IllegalArgumentException(path + ": pass needs output");
            target(pass.get("output").getAsString(), targets, path);
            var samplers = new HashSet<String>();
            if (pass.has("inputs"))
                for (var input : pass.getAsJsonArray("inputs")) {
                    var entry = input.getAsJsonObject();
                    if (!entry.has("sampler_name")
                            || !samplers.add(entry.get("sampler_name").getAsString()))
                        throw new IllegalArgumentException(
                                path + ": missing or duplicate sampler name");
                    if (entry.has("target"))
                        target(entry.get("target").getAsString(), targets, path);
                }
            if (pass.has("uniforms"))
                for (var block : pass.getAsJsonObject("uniforms").entrySet())
                    for (var field : block.getValue().getAsJsonArray()) {
                        var uniform = field.getAsJsonObject();
                        String type = uniform.get("type").getAsString();
                        int size =
                                switch (type) {
                                    case "vec2", "ivec2" -> 2;
                                    case "vec3", "ivec3" -> 3;
                                    case "vec4", "ivec4" -> 4;
                                    case "mat4" -> 16;
                                    default -> 1;
                                };
                        if (!uniform.has("value"))
                            throw new IllegalArgumentException(path + ": uniform needs value");
                        var data = uniform.get("value");
                        if ((data.isJsonArray() ? data.getAsJsonArray().size() : 1) != size)
                            throw new IllegalArgumentException(
                                    path
                                            + ": "
                                            + block.getKey()
                                            + " "
                                            + type
                                            + " needs "
                                            + size
                                            + " values");
                        if (data.isJsonArray()) {
                            for (var number : data.getAsJsonArray()) finite(number, path);
                        } else finite(data, path);
                    }
        }
    }

    private static void finite(JsonElement value, String path) {
        if (!value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isNumber()
                || !Double.isFinite(value.getAsDouble()))
            throw new IllegalArgumentException(
                    path + ": shader uniform values must be finite numbers");
    }

    private static void target(String value, Set<String> targets, String path) {
        var key = Key.key(value);
        if (!targets.contains(key.asString())
                && (!value.contains(":") || !key.namespace().equals("minecraft")))
            throw new IllegalArgumentException(path + ": undeclared local render target " + value);
    }

    private static void requireShader(
            JsonObject pass, String field, String suffix, String path, Map<String, byte[]> files) {
        if (!pass.has(field)) throw new IllegalArgumentException(path + ": pass needs " + field);
        Key key = Key.key(pass.get(field).getAsString());
        String file = key.namespace() + "/shaders/" + key.value() + suffix;
        if (!key.namespace().equals("minecraft") && !files.containsKey(file))
            throw new IllegalArgumentException(path + " references missing shader " + file);
    }
}
