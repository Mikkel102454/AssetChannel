package nu.miguel.assetChannel.pack;

import com.google.gson.*;

import nu.miguel.assetChannel.api.RigAsset;
import nu.miguel.assetChannel.api.RigAsset.*;
import nu.miguel.assetChannel.asset.Asset;
import nu.miguel.assetChannel.texturepack.TexturePack;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Converts cube rigs into independent native render parts without changing source files. */
public final class RigCompiler implements Asset {
    private final RigAsset rig;
    private final Map<String, byte[]> files;

    private RigCompiler(RigAsset rig, Map<String, byte[]> files) {
        this.rig = rig;
        this.files = files;
    }

    public RigAsset rig() {
        return rig;
    }

    @Override
    public String getId() {
        return rig.id();
    }

    public static RigCompiler compile(String id, Path path) throws IOException {
        try {
            JsonObject source = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            if (source.has("animation_controllers")
                    && !source.getAsJsonArray("animation_controllers").isEmpty())
                throw new IllegalArgumentException("Bake animation controllers");
            Map<String, byte[]> files = new TreeMap<>();
            String prefix = "assetchannel:generated/rigs/" + id.replace('.', '/');
            JsonObject textures = new JsonObject();
            int textureIndex = 0;
            for (JsonElement raw : array(source, "textures")) {
                JsonObject texture = raw.getAsJsonObject();
                String data = texture.has("source") ? texture.get("source").getAsString() : "";
                if (!data.startsWith("data:image/png;base64,"))
                    throw new IllegalArgumentException("Embed PNG textures in the bbmodel");
                byte[] bytes = Base64.getDecoder().decode(data.substring(data.indexOf(',') + 1));
                var image = javax.imageio.ImageIO.read(new ByteArrayInputStream(bytes));
                if (image == null) throw new IllegalArgumentException("Invalid embedded PNG");
                String key = prefix + "/texture_" + textureIndex;
                files.put(resource(key, "textures", ".png"), bytes);
                JsonObject material = new JsonObject();
                material.addProperty("sprite", key);
                material.addProperty("atlas", "minecraft:items");
                textures.add(Integer.toString(textureIndex++), material);
            }
            List<Bone> bones = new ArrayList<>();
            Map<String, String> owners = new HashMap<>();
            Set<String> excluded = new HashSet<>();
            Map<String, JsonObject> groups = new HashMap<>();
            for (JsonElement raw : array(source, "groups")) {
                JsonObject group = raw.getAsJsonObject();
                String uuid = group.get("uuid").getAsString();
                if (groups.putIfAbsent(uuid, group) != null)
                    throw new IllegalArgumentException("Duplicate group definition " + uuid);
            }
            walk(array(source, "outliner"), null, bones, owners, excluded, false, groups);
            Set<String> ids = new HashSet<>();
            for (Bone bone : bones)
                if (!ids.add(bone.id()))
                    throw new IllegalArgumentException("Duplicate bone " + bone.id());
            List<Part> parts = new ArrayList<>();
            int index = 0;
            for (JsonElement raw : array(source, "elements")) {
                JsonObject element = raw.getAsJsonObject();
                if (excluded.contains(element.get("uuid").getAsString())
                        || (element.has("export") && !element.get("export").getAsBoolean()))
                    continue;
                if (element.has("type") && !element.get("type").getAsString().equals("cube"))
                    throw new IllegalArgumentException("Only cubes are supported; bake meshes");
                if (element.has("rescale") && element.get("rescale").getAsBoolean())
                    throw new IllegalArgumentException("Bake cube rescale before importing");
                String owner = owners.get(element.get("uuid").getAsString());
                if (owner == null || owner.equals("__root")) {
                    owner = "__root";
                    if (ids.add(owner)) bones.add(new Bone(owner, owner, null, Vec.ZERO, Vec.ZERO));
                }
                Vec pivot = vec(element.get("origin"), Vec.ZERO),
                        rotation = vec(element.get("rotation"), Vec.ZERO);
                JsonObject cube = new JsonObject();
                Vec from = vec(element.get("from"), Vec.ZERO),
                        to = vec(element.get("to"), Vec.ZERO);
                double inflate = element.has("inflate") ? element.get("inflate").getAsDouble() : 0;
                from = new Vec(from.x() - inflate, from.y() - inflate, from.z() - inflate);
                to = new Vec(to.x() + inflate, to.y() + inflate, to.z() + inflate);
                cube.add(
                        "from",
                        json(
                                new Vec(
                                        from.x() - pivot.x() + 8,
                                        from.y() - pivot.y() + 8,
                                        from.z() - pivot.z() + 8)));
                cube.add(
                        "to",
                        json(
                                new Vec(
                                        to.x() - pivot.x() + 8,
                                        to.y() - pivot.y() + 8,
                                        to.z() - pivot.z() + 8)));
                JsonObject faces =
                        element.has("faces")
                                ? element.getAsJsonObject("faces").deepCopy()
                                : new JsonObject();
                for (var face : faces.entrySet()) {
                    JsonObject f = face.getValue().getAsJsonObject();
                    if (!f.has("texture") || f.get("texture").isJsonNull()) continue;
                    String number = f.get("texture").getAsString().replace("#", "");
                    if (!textures.has(number))
                        throw new IllegalArgumentException("Unknown texture " + number);
                    f.addProperty("texture", "#" + number);
                    if (f.has("uv") && source.has("resolution")) {
                        JsonObject resolution = source.getAsJsonObject("resolution");
                        JsonArray uv = f.getAsJsonArray("uv");
                        for (int n = 0; n < 4; n++)
                            uv.set(
                                    n,
                                    new JsonPrimitive(
                                            uv.get(n).getAsDouble()
                                                    * 16
                                                    / resolution
                                                            .get(n % 2 == 0 ? "width" : "height")
                                                            .getAsDouble()));
                    }
                }
                faces.entrySet()
                        .removeIf(e ->
                                !e.getValue().getAsJsonObject().has("texture")
                                        || e.getValue()
                                                .getAsJsonObject()
                                                .get("texture")
                                                .isJsonNull());
                cube.add("faces", faces);
                JsonArray elements = new JsonArray();
                elements.add(cube);
                String model = prefix + "/part_" + index++;
                JsonObject geometry = new JsonObject();
                geometry.add("textures", textures);
                geometry.add("elements", elements);
                put(files, resource(model, "models", ".json"), geometry);
                JsonObject item = new JsonObject(), reference = new JsonObject();
                reference.addProperty("type", "minecraft:model");
                reference.addProperty("model", model);
                item.add("model", reference);
                item.addProperty("assetchannel:internal", true);
                put(files, resource(model, "items", ".json"), item);
                parts.add(new Part(owner, model, pivot, rotation));
            }
            Map<String, Clip> clips = new TreeMap<>();
            for (JsonElement raw : array(source, "animations")) {
                JsonObject animation = raw.getAsJsonObject();
                String name = animation.get("name").getAsString();
                List<Channel> channels = new ArrayList<>();
                for (String timing : List.of("start_delay", "loop_delay"))
                    if (animation.has(timing)
                            && !animation.get(timing).getAsString().isBlank()
                            && number(animation, timing) != 0)
                        throw new IllegalArgumentException(
                                "Bake animation " + timing + " before importing");
                if (animation.has("blend_weight")
                        && !animation.get("blend_weight").getAsString().isBlank()
                        && number(animation, "blend_weight") != 1)
                    throw new IllegalArgumentException(
                            "Bake animation blend_weight or use scene layer weights");
                if (animation.has("loop")
                        && !Set.of("once", "loop", "hold")
                                .contains(animation.get("loop").getAsString()))
                    throw new IllegalArgumentException("Unsupported animation loop mode");
                if (animation.has("animators"))
                    for (var entry : animation.getAsJsonObject("animators").entrySet()) {
                        if (!ids.contains(entry.getKey()))
                            throw new IllegalArgumentException(
                                    "Unsupported effects animator or missing bone: "
                                            + entry.getKey());
                        Map<String, List<Frame>> grouped = new TreeMap<>();
                        for (JsonElement frameRaw :
                                array(entry.getValue().getAsJsonObject(), "keyframes")) {
                            JsonObject f = frameRaw.getAsJsonObject();
                            String property = f.get("channel").getAsString();
                            if (!Set.of("position", "rotation", "scale").contains(property))
                                throw new IllegalArgumentException(
                                        "Unsupported channel " + property);
                            String interpolation =
                                    f.has("interpolation")
                                            ? f.get("interpolation").getAsString()
                                            : "linear";
                            if (!Set.of("linear", "step", "catmullrom", "bezier")
                                    .contains(interpolation))
                                throw new IllegalArgumentException(
                                        "Bake unsupported interpolation " + interpolation);
                            JsonArray points = f.getAsJsonArray("data_points");
                            if (points.size() != 1)
                                throw new IllegalArgumentException("Bake pre/post keyframes");
                            JsonObject p = points.get(0).getAsJsonObject();
                            Vec value = new Vec(number(p, "x"), number(p, "y"), number(p, "z"));
                            grouped.computeIfAbsent(property, k -> new ArrayList<>())
                                    .add(
                                            new Frame(
                                                    f.get("time").getAsDouble(),
                                                    value,
                                                    interpolation,
                                                    vec(f.get("bezier_left_time"), Vec.ZERO),
                                                    vec(f.get("bezier_left_value"), Vec.ZERO),
                                                    vec(f.get("bezier_right_time"), Vec.ZERO),
                                                    vec(f.get("bezier_right_value"), Vec.ZERO)));
                        }
                        grouped.forEach((property, frames) ->
                                channels.add(new Channel(entry.getKey(), property, frames)));
                    }
                double length = animation.get("length").getAsDouble();
                if (length == 0 && channels.isEmpty())
                    continue; // Blockbench creates empty named placeholders.
                if (clips.putIfAbsent(
                                name,
                                new Clip(
                                        name,
                                        length,
                                        animation.has("loop")
                                                ? animation.get("loop").getAsString()
                                                : "once",
                                        channels))
                        != null) throw new IllegalArgumentException("Duplicate animation " + name);
            }
            return new RigCompiler(new RigAsset(id, bones, parts, clips), Map.copyOf(files));
        } catch (RuntimeException error) {
            throw new IllegalArgumentException(path + ": " + error.getMessage(), error);
        }
    }

    private static double number(JsonObject p, String key) {
        try {
            return Double.parseDouble(p.get(key).getAsString());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Bake animation expressions: " + p, e);
        }
    }

    private static void walk(
            JsonArray nodes,
            String parent,
            List<Bone> bones,
            Map<String, String> owners,
            Set<String> excluded,
            boolean hidden,
            Map<String, JsonObject> groups) {
        for (JsonElement node : nodes) {
            if (node.isJsonPrimitive()) {
                if (owners.putIfAbsent(node.getAsString(), parent == null ? "__root" : parent)
                        != null) throw new IllegalArgumentException("Duplicate cube reference");
                if (hidden) excluded.add(node.getAsString());
            } else {
                JsonObject hierarchy = node.getAsJsonObject();
                String id = hierarchy.get("uuid").getAsString();
                JsonObject group =
                        groups.containsKey(id) ? groups.get(id).deepCopy() : new JsonObject();
                hierarchy.entrySet().forEach(entry -> group.add(entry.getKey(), entry.getValue()));
                if (!group.has("name"))
                    throw new IllegalArgumentException("Missing group definition " + id);
                bones.add(
                        new Bone(
                                id,
                                group.get("name").getAsString(),
                                parent,
                                vec(group.get("origin"), Vec.ZERO),
                                vec(group.get("rotation"), Vec.ZERO)));
                walk(
                        array(group, "children"),
                        id,
                        bones,
                        owners,
                        excluded,
                        hidden || (group.has("export") && !group.get("export").getAsBoolean()),
                        groups);
            }
        }
    }

    private static JsonArray array(JsonObject o, String key) {
        return o.has(key) ? o.getAsJsonArray(key) : new JsonArray();
    }

    private static Vec vec(JsonElement raw, Vec fallback) {
        if (raw == null) return fallback;
        JsonArray a = raw.getAsJsonArray();
        return new Vec(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
    }

    private static JsonArray json(Vec v) {
        JsonArray a = new JsonArray();
        a.add(v.x());
        a.add(v.y());
        a.add(v.z());
        return a;
    }

    private static String resource(String key, String category, String extension) {
        return key.replace(":", "/" + category + "/") + extension;
    }

    private static void put(Map<String, byte[]> files, String path, JsonObject json) {
        files.put(path, json.toString().getBytes(StandardCharsets.UTF_8));
    }

    static ImportedResources merge(ImportedResources imported, List<Asset> assets) {
        Map<String, byte[]> files = new TreeMap<>(imported.sourceFiles());
        Set<String> sprites = new TreeSet<>();
        for (Asset asset : assets)
            if (asset instanceof RigCompiler compiled)
                compiled.files.forEach((key, value) -> {
                    if (files.putIfAbsent(key, value) != null)
                        throw new IllegalArgumentException("Generated rig collision: " + key);
                    if (key.contains("/textures/") && key.endsWith(".png")) {
                        int slash = key.indexOf('/');
                        sprites.add(
                                key.substring(0, slash)
                                        + ":"
                                        + key.substring(
                                                key.indexOf("/textures/") + 10, key.length() - 4));
                    }
                });
        if (!sprites.isEmpty()) {
            String path = "minecraft/atlases/items.json";
            JsonObject atlas =
                    files.containsKey(path)
                            ? JsonParser.parseString(
                                            new String(files.get(path), StandardCharsets.UTF_8))
                                    .getAsJsonObject()
                            : new JsonObject();
            JsonArray sources =
                    atlas.has("sources") ? atlas.getAsJsonArray("sources") : new JsonArray();
            for (String sprite : sprites) {
                JsonObject entry = new JsonObject();
                entry.addProperty("type", "minecraft:single");
                entry.addProperty("resource", sprite);
                sources.add(entry);
            }
            atlas.add("sources", sources);
            put(files, path, atlas);
        }
        return ImportedResources.prepared(files);
    }

    @Override
    public boolean build(TexturePack pack) {
        return true; /* Resources participate in native validation and staging. */
    }
}
