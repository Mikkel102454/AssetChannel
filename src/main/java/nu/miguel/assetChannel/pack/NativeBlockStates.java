package nu.miguel.assetChannel.pack;

import com.google.gson.*;
import nu.miguel.assetChannel.api.*;
import nu.miguel.assetChannel.asset.*;
import nu.miguel.assetChannel.texturepack.TexturePack;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Generates mutually exclusive custom and vanilla models for resolved note-block reservations. */
final class NativeBlockStates {
    static void validate(List<Asset> assets) {
        List<BlockAsset> blocks = definitions(assets);
        if (blocks.isEmpty()) return;
        Map<String, String> states = new HashMap<>();
        List<ImportedResources> imports = assets.stream().filter(ImportedResources.class::isInstance).map(ImportedResources.class::cast).toList();
        for (BlockAsset block : blocks) {
            String previous = states.putIfAbsent(block.blockState(), block.id());
            if (previous != null) throw new IllegalArgumentException("Reserved block-state " + block.blockState() + " is used by both " + previous + " and " + block.id());
            String modelPath = resourcePath(block.blockModel(), "models", ".json");
            if (imports.stream().noneMatch(i -> i.hasFile(modelPath)))
                throw new IllegalArgumentException("Block " + block.id() + " references missing native geometry " + modelPath);
        }
        if (imports.stream().anyMatch(i -> i.hasFile("minecraft/blockstates/note_block.json")))
            throw new IllegalArgumentException("Reserved block states conflict with imported minecraft/blockstates/note_block.json");
    }

    static void build(TexturePack pack, List<Asset> assets) throws IOException {
        List<BlockAsset> blocks = definitions(assets);
        if (blocks.isEmpty()) return;
        JsonArray parts = new JsonArray();
        JsonArray exclusions = new JsonArray();
        for (BlockAsset block : blocks) {
            var state = NativeBlockState.parse(block.blockState());
            JsonObject when = new JsonObject();
            JsonArray alternatives = new JsonArray();
            new TreeMap<>(state.properties()).forEach((key, value) -> {
                when.addProperty(key, value);
                JsonObject different = new JsonObject(); different.addProperty(key, "!" + value); alternatives.add(different);
            });
            parts.add(part(when, block.blockModel()));
            JsonObject notThisState = new JsonObject(); notThisState.add("OR", alternatives); exclusions.add(notThisState);
        }
        JsonObject fallback = new JsonObject(); fallback.add("AND", exclusions);
        parts.add(part(fallback, "minecraft:block/note_block"));
        JsonObject definition = new JsonObject(); definition.add("multipart", parts);
        Path path = pack.getRootPath().resolve("assets/minecraft/blockstates/note_block.json");
        Files.createDirectories(path.getParent());
        Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(definition), StandardOpenOption.CREATE_NEW);
    }

    private static JsonObject part(JsonObject when, String model) {
        JsonObject apply = new JsonObject(); apply.addProperty("model", model);
        JsonObject part = new JsonObject(); part.add("when", when); part.add("apply", apply); return part;
    }
    private static List<BlockAsset> definitions(List<Asset> assets) {
        return assets.stream().filter(BlockDefinition.class::isInstance).map(BlockDefinition.class::cast)
                .map(BlockDefinition::descriptor).filter(b -> b.blockState() != null).sorted(Comparator.comparing(BlockAsset::id)).toList();
    }
    private static String resourcePath(String id, String category, String extension) {
        var key = net.kyori.adventure.key.Key.key(id);
        return key.namespace() + "/" + category + "/" + key.value() + extension;
    }
}
