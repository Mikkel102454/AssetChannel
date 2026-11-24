package nu.miguel.assetChannel.pack;

import com.google.gson.*;

import nu.miguel.assetChannel.api.NativeBlockState;
import nu.miguel.assetChannel.asset.Asset;
import nu.miguel.assetChannel.texturepack.TexturePack;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Persistent allocations are server data, separate from disposable generated resources. */
final class BlockStateReservations {
    private final Path file;
    private final byte[] original;
    private final Map<String, String> blocks = new TreeMap<>();
    private final Map<String, String> owners = new HashMap<>();

    BlockStateReservations(Path data) {
        file = data.resolve("storage/block-states.json");
        try {
            original = Files.exists(file) ? Files.readAllBytes(file) : null;
            if (original != null) {
                var json =
                        JsonParser.parseString(
                                        new String(
                                                original, java.nio.charset.StandardCharsets.UTF_8))
                                .getAsJsonObject();
                if (json.get("version").getAsInt() != 1)
                    throw new IllegalArgumentException(
                            "Unsupported block-state storage version: " + file);
                for (var entry : json.getAsJsonObject("blocks").entrySet())
                    reserve(entry.getKey(), entry.getValue().getAsString());
            }
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("Cannot read block-state assignments: " + file, error);
        }
    }

    String reserve(String id, String state) {
        if (!id.matches("[a-z0-9._-]+"))
            throw new IllegalArgumentException("Invalid block-state storage ID: " + id);
        state = NativeBlockState.parse(state).asString();
        String previous = blocks.get(id);
        if (previous != null && !previous.equals(state))
            throw new IllegalArgumentException(
                    "Block "
                            + id
                            + " already owns "
                            + previous
                            + "; retain its state or use a new block ID");
        String owner = owners.putIfAbsent(state, id);
        if (owner != null && !owner.equals(id))
            throw new IllegalArgumentException(
                    "Block-state " + state + " is already reserved by " + owner);
        blocks.put(id, state);
        return state;
    }

    String allocate(String id) {
        if (blocks.containsKey(id)) return blocks.get(id);
        var pool = NativeBlockState.availableStates();
        int start = Math.floorMod(id.hashCode(), pool.size());
        for (int offset = 0; offset < pool.size(); offset++) {
            String candidate = pool.get((start + offset) % pool.size()).asString();
            if (!owners.containsKey(candidate)) return reserve(id, candidate);
        }
        throw new IllegalArgumentException(
                "All " + pool.size() + " native block states are reserved");
    }

    Asset snapshot() {
        JsonObject entries = new JsonObject();
        blocks.forEach(entries::addProperty);
        JsonObject json = new JsonObject();
        json.addProperty("version", 1);
        json.add("blocks", entries);
        byte[] next =
                new GsonBuilder()
                        .setPrettyPrinting()
                        .create()
                        .toJson(json)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return new Snapshot(file, original, next);
    }

    record Snapshot(Path file, byte[] original, byte[] next) implements Asset {
        @Override
        public String getId() {
            return "block-state-reservations";
        }

        @Override
        public boolean build(TexturePack pack) {
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp-" + UUID.randomUUID());
            try {
                byte[] current = Files.exists(file) ? Files.readAllBytes(file) : null;
                if (!Arrays.equals(current, original))
                    throw new IOException(
                            "Block-state assignments changed during the build; rebuild again");
                if (Arrays.equals(current, next)) return true;
                Files.createDirectories(file.getParent());
                Files.write(temporary, next, StandardOpenOption.CREATE_NEW);
                try {
                    Files.move(
                            temporary,
                            file,
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException error) {
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
                }
                return true;
            } catch (IOException error) {
                throw new IllegalStateException(
                        "Cannot save block-state assignments: " + file, error);
            } finally {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                }
            }
        }
    }
}
