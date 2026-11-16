package nu.miguel.assetChannel.pack;

import com.google.gson.*;
import net.kyori.adventure.key.Key;
import nu.miguel.assetChannel.api.BlockAsset;
import nu.miguel.assetChannel.asset.*;
import java.nio.file.Path;
import java.util.*;

/** Compiles concise block declarations into virtual native resources; never edits source files. */
final class AutomaticBlockContent {
    private final Map<String, byte[]> files;
    private final Map<String, JsonObject> documents = new HashMap<>();
    private final Map<String, JsonObject> spriteSources = new HashMap<>();

    private AutomaticBlockContent(ImportedResources imported) {
        files = new TreeMap<>(imported.sourceFiles());
        for (String path : files.keySet()) if (path.contains("/atlases/") && path.endsWith(".json")) {
            JsonObject atlas = json(path);
            if (!atlas.has("sources")) continue;
            for (JsonElement raw : atlas.getAsJsonArray("sources")) indexSource(raw.getAsJsonObject());
        }
    }

    static ImportedResources prepare(ImportedResources imported, List<Asset> assets, Path data) {
        var compiler = new AutomaticBlockContent(imported);
        var reservations = new BlockStateReservations(data);
        boolean nativeBlocks = false;
        // Explicit legacy reservations are adopted before allocating any automatic ones.
        for (Asset asset : assets) if (asset instanceof BlockDefinition block && block.descriptor().blockState() != null) {
            reservations.reserve(block.getId(), block.descriptor().blockState()); nativeBlocks = true;
        }
        for (int i = 0, size = assets.size(); i < size; i++) {
            if (!(assets.get(i) instanceof BlockDefinition block) || block.setup() == null) continue;
            var old = block.descriptor(); var setup = block.setup();
            String heldGeometry = compiler.geometry(old.model(), null);
            String geometry = compiler.geometry(old.model(), setup.geometry());
            if (geometry == null && setup.render().equals("block"))
                throw new IllegalArgumentException("Block " + block.getId() + " needs a geometry model; specify block-model for conditional/composite items");
            String particleModel = old.particleModel();
            String nativeModel = geometry;
            String prefix = "assetchannel:generated/blocks/" + block.getId().replace('.', '/');
            String particleSprite = null;
            Set<String> textures = new TreeSet<>();
            if (geometry != null) compiler.modelTextures(geometry, textures, new HashSet<>());
            if (heldGeometry != null) compiler.modelTextures(heldGeometry, textures, new HashSet<>());
            compiler.itemTextures(old.model(), textures);
            if (setup.particles() != null) {
                String sprite = prefix + "/particle";
                particleSprite = sprite;
                compiler.particleSource(sprite, setup.particles()); textures.add(sprite);
                // Older imports may already name a virtual particle sprite. Supply it when converting their atlas to YAML.
                if (geometry != null) {
                    String originalParticle = compiler.particleReference(geometry, new HashSet<>());
                    if (originalParticle != null && !originalParticle.startsWith("#")
                            && !compiler.files.containsKey(path(originalParticle, "textures", ".png")) && !compiler.spriteSources.containsKey(Key.key(originalParticle).asString())) {
                        compiler.particleSource(originalParticle, setup.particles()); textures.add(originalParticle);
                    }
                }
                JsonObject wrapper = new JsonObject();
                wrapper.addProperty("parent", geometry != null ? geometry : "minecraft:item/generated");
                JsonObject particle = new JsonObject(); particle.addProperty("particle", sprite);
                if (geometry == null) particle.addProperty("layer0", sprite);
                wrapper.add("textures", particle);
                String particleGeometry = prefix + "/particle_model";
                compiler.generated(path(particleGeometry, "models", ".json"), wrapper);
                particleModel = prefix + "/particles";
                compiler.item(particleModel, particleGeometry, true);
            }
            compiler.item(old.model(), heldGeometry != null ? heldGeometry : geometry);
            for (String texture : textures) compiler.atlas("items", texture);
            if (setup.render().equals("block")) {
                JsonObject bindings = compiler.textureBindings(geometry, new HashSet<>());
                if (particleSprite != null) bindings.addProperty("particle", particleSprite);
                for (String slot : List.copyOf(bindings.keySet())) {
                    JsonElement value = bindings.get(slot);
                    String sprite = value.isJsonObject() ? value.getAsJsonObject().get("sprite").getAsString() : value.getAsString();
                    if (sprite.startsWith("#")) continue;
                    String blockSprite = compiler.blockSprite(sprite);
                    if (value.isJsonObject()) { JsonObject material = value.getAsJsonObject().deepCopy(); material.addProperty("sprite", blockSprite); bindings.add(slot, material); }
                    else bindings.addProperty(slot, blockSprite);
                }
                JsonObject blockModel = new JsonObject(); blockModel.addProperty("parent", geometry); blockModel.add("textures", bindings);
                nativeModel = prefix + "/model";
                compiler.generated(path(nativeModel, "models", ".json"), blockModel);
            }
            String state = null;
            if (setup.render().equals("block")) { state = reservations.allocate(block.getId()); nativeBlocks = true; }
            var descriptor = new BlockAsset(old.id(), old.model(), old.carrier(), old.particleBlock(), particleModel,
                    old.breakSound(), old.placeSound(), old.particleCount(), old.drops(), state, state != null ? nativeModel : null);
            assets.set(i, new BlockDefinition(descriptor, setup));
        }
        if (nativeBlocks) assets.add(reservations.snapshot());
        return ImportedResources.prepared(compiler.files);
    }

    private String geometry(String model, String requested) {
        if (requested != null) {
            if (!files.containsKey(path(requested, "models", ".json"))) throw new IllegalArgumentException("Missing geometry model: " + requested);
            return requested;
        }
        if (files.containsKey(path(model, "models", ".json"))) return model;
        JsonObject item = json(path(model, "items", ".json"));
        if (item != null && item.has("model")) {
            JsonObject root = item.getAsJsonObject("model");
            if (root.has("type") && root.get("type").getAsString().equals("minecraft:model")) return root.get("model").getAsString();
        }
        return null;
    }

    private void item(String id, String geometry) {
        item(id, geometry, false);
    }
    private void item(String id, String geometry, boolean internal) {
        String file = path(id, "items", ".json");
        if (files.containsKey(file)) {
            if (internal) throw new IllegalArgumentException("Internal block item conflicts with imported definition: " + id);
            return;
        }
        if (geometry == null) throw new IllegalArgumentException("No item definition or geometry model: " + id);
        JsonObject model = new JsonObject(); model.addProperty("type", "minecraft:model"); model.addProperty("model", geometry);
        JsonObject item = new JsonObject(); item.add("model", model);
        if (internal) item.addProperty("assetchannel:internal", true);
        generated(file, item);
    }

    private JsonObject textureBindings(String model, Set<String> visiting) {
        if (!visiting.add(model)) throw new IllegalArgumentException("Model parent cycle: " + model);
        JsonObject definition = json(path(model, "models", ".json"));
        JsonObject bindings = new JsonObject();
        if (definition != null) {
            if (definition.has("parent")) bindings = textureBindings(definition.get("parent").getAsString(), visiting);
            if (definition.has("textures")) for (var entry : definition.getAsJsonObject("textures").entrySet()) bindings.add(entry.getKey(), entry.getValue().deepCopy());
        }
        visiting.remove(model);
        return bindings;
    }

    private String blockSprite(String original) {
        Key key = Key.key(original);
        String alias = "assetchannel:generated/block_sprites/" + key.namespace() + "/" + key.value();
        JsonObject producer = null;
        // A deliberate world mapping takes priority over an item's mapping of the same legacy ID.
        JsonObject blockAtlas = json("minecraft/atlases/blocks.json");
        if (blockAtlas != null) for (var entry : blockAtlas.getAsJsonArray("sources"))
            if (covers(entry.getAsJsonObject(), key.asString())) { producer = entry.getAsJsonObject(); break; }
        if (producer == null) producer = spriteSources.get(key.asString());
        JsonObject source;
        if (producer != null && producer.get("type").getAsString().replace("minecraft:", "").equals("unstitch")) {
            source = producer.deepCopy();
            JsonArray regions = new JsonArray();
            for (var entry : producer.getAsJsonArray("regions")) {
                JsonObject region = entry.getAsJsonObject();
                if (Key.key(region.get("sprite").getAsString()).equals(key)) {
                    region = region.deepCopy(); region.addProperty("sprite", alias); regions.add(region);
                }
            }
            source.add("regions", regions);
        } else {
            String resource = key.asString();
            if (producer != null) {
                String type = producer.get("type").getAsString().replace("minecraft:", "");
                if (type.equals("single")) resource = producer.get("resource").getAsString();
                if (type.equals("directory")) {
                    String prefix = producer.has("prefix") ? producer.get("prefix").getAsString() : "";
                    resource = key.namespace() + ":" + producer.get("source").getAsString() + "/" + key.value().substring(prefix.length());
                }
            }
            source = new JsonObject(); source.addProperty("type", "minecraft:single"); source.addProperty("resource", resource); source.addProperty("sprite", alias);
        }
        JsonObject previous = spriteSources.putIfAbsent(alias, source);
        if (previous != null && !previous.equals(source)) throw new IllegalArgumentException("Conflicting block sprite binding: " + original);
        atlas("blocks", alias);
        return alias;
    }

    private void itemTextures(String id, Set<String> textures) {
        JsonObject item = json(path(id, "items", ".json"));
        if (item != null) itemModels(item, textures);
    }
    private void itemModels(JsonElement value, Set<String> textures) {
        if (value.isJsonArray()) { value.getAsJsonArray().forEach(v -> itemModels(v, textures)); return; }
        if (!value.isJsonObject()) return;
        var object = value.getAsJsonObject();
        if (object.has("type") && object.get("type").getAsString().equals("minecraft:model") && object.has("model"))
            modelTextures(object.get("model").getAsString(), textures, new HashSet<>());
        object.asMap().values().forEach(v -> itemModels(v, textures));
    }
    private void modelTextures(String model, Set<String> textures, Set<String> visiting) {
        if (!visiting.add(model)) throw new IllegalArgumentException("Model parent cycle: " + model);
        JsonObject definition = json(path(model, "models", ".json"));
        if (definition != null) {
            if (definition.has("textures")) for (JsonElement value : definition.getAsJsonObject("textures").asMap().values()) {
                String texture = value.isJsonObject() ? value.getAsJsonObject().get("sprite").getAsString() : value.getAsString();
                if (!texture.startsWith("#")) textures.add(Key.key(texture).asString());
            }
            if (definition.has("parent")) modelTextures(definition.get("parent").getAsString(), textures, visiting);
        }
        visiting.remove(model);
    }
    private String particleReference(String model, Set<String> visiting) {
        if (!visiting.add(model)) throw new IllegalArgumentException("Model parent cycle: " + model);
        JsonObject definition = json(path(model, "models", ".json"));
        if (definition == null) return null;
        if (definition.has("textures") && definition.getAsJsonObject("textures").has("particle"))
            return definition.getAsJsonObject("textures").get("particle").isJsonPrimitive()
                    ? definition.getAsJsonObject("textures").get("particle").getAsString()
                    : definition.getAsJsonObject("textures").getAsJsonObject("particle").get("sprite").getAsString();
        return definition.has("parent") ? particleReference(definition.get("parent").getAsString(), visiting) : null;
    }

    private void particleSource(String sprite, BlockDefinition.ParticleTexture particle) {
        JsonObject source = new JsonObject();
        source.addProperty("resource", particle.texture());
        if (particle.region() == null) {
            source.addProperty("type", "minecraft:single"); source.addProperty("sprite", sprite);
        } else {
            byte[] image = files.get(path(particle.texture(), "textures", ".png"));
            if (image == null) throw new IllegalArgumentException("particles.region requires an imported PNG: " + particle.texture());
            try {
                var png = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(image));
                if (png == null) throw new IllegalArgumentException("Unreadable particle texture: " + particle.texture());
                var region = particle.region();
                if ((long)region.get(0) + region.get(2) > png.getWidth() || (long)region.get(1) + region.get(3) > png.getHeight())
                    throw new IllegalArgumentException("particles.region is outside " + particle.texture() + " (" + png.getWidth() + "x" + png.getHeight() + ")");
                source.addProperty("type", "minecraft:unstitch"); source.addProperty("divisor_x", png.getWidth()); source.addProperty("divisor_y", png.getHeight());
                JsonObject crop = new JsonObject(); crop.addProperty("sprite", sprite);
                crop.addProperty("x", region.get(0)); crop.addProperty("y", region.get(1)); crop.addProperty("width", region.get(2)); crop.addProperty("height", region.get(3));
                JsonArray regions = new JsonArray(); regions.add(crop); source.add("regions", regions);
            } catch (java.io.IOException error) { throw new IllegalArgumentException("Cannot read particle texture: " + particle.texture(), error); }
        }
        spriteSources.put(Key.key(sprite).asString(), source);
    }

    private void atlas(String atlas, String texture) {
        texture = Key.key(texture).asString();
        String file = "minecraft/atlases/" + atlas + ".json";
        JsonObject document = json(file);
        if (document != null) for (JsonElement entry : document.getAsJsonArray("sources"))
            if (covers(entry.getAsJsonObject(), texture)) return;
        JsonObject source = spriteSources.get(texture);
        if (source == null) {
            if (!files.containsKey(path(texture, "textures", ".png"))) return; // Vanilla sources or an invalid reference diagnosed by validation.
            source = new JsonObject(); source.addProperty("type", "minecraft:single"); source.addProperty("resource", texture); source.addProperty("sprite", texture);
        }
        if (document == null) { document = new JsonObject(); document.add("sources", new JsonArray()); }
        JsonArray sources = document.getAsJsonArray("sources");
        JsonObject selected = source;
        if (sources.asList().stream().noneMatch(s -> s.equals(selected))) sources.add(source.deepCopy());
        write(file, document);
    }
    private boolean covers(JsonObject source, String sprite) {
        if (!source.has("type")) return false;
        String type = source.get("type").getAsString().replace("minecraft:", "");
        if (type.equals("single")) return Key.key(source.has("sprite") ? source.get("sprite").getAsString() : source.get("resource").getAsString()).asString().equals(sprite);
        if (type.equals("unstitch")) return source.getAsJsonArray("regions").asList().stream()
                .anyMatch(region -> Key.key(region.getAsJsonObject().get("sprite").getAsString()).asString().equals(sprite));
        if (type.equals("directory")) {
            Key key = Key.key(sprite);
            String prefix = source.has("prefix") ? source.get("prefix").getAsString() : "";
            if (!key.value().startsWith(prefix)) return false;
            String resource = key.namespace() + ":" + source.get("source").getAsString() + "/" + key.value().substring(prefix.length());
            return files.containsKey(path(resource, "textures", ".png"));
        }
        return false;
    }
    private void indexSource(JsonObject source) {
        if (!source.has("type")) return;
        String type = source.get("type").getAsString().replace("minecraft:", "");
        if (type.equals("single")) spriteSources.putIfAbsent(Key.key(source.has("sprite") ? source.get("sprite").getAsString() : source.get("resource").getAsString()).asString(), source.deepCopy());
        if (type.equals("unstitch")) for (var region : source.getAsJsonArray("regions"))
            spriteSources.putIfAbsent(Key.key(region.getAsJsonObject().get("sprite").getAsString()).asString(), source.deepCopy());
    }
    private JsonObject json(String path) {
        if (!files.containsKey(path)) return null;
        return documents.computeIfAbsent(path, key -> JsonParser.parseString(new String(files.get(key), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject());
    }
    private void generated(String file, JsonObject content) {
        if (files.containsKey(file)) throw new IllegalArgumentException("Generated block resource conflicts with imported file: " + file);
        write(file, content);
    }
    private void write(String file, JsonObject content) {
        documents.put(file, content);
        files.put(file, new GsonBuilder().create().toJson(content).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private static String path(String id, String category, String extension) {
        var key = Key.key(id);
        if (key.value().contains("..")) throw new IllegalArgumentException("Invalid resource reference: " + id);
        return key.namespace() + "/" + category + "/" + key.value() + extension;
    }
}
