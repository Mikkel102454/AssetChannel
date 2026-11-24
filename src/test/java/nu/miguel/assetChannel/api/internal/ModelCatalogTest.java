package nu.miguel.assetChannel.api.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nu.miguel.assetChannel.pack.ImportedResources;

import org.bukkit.Bukkit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.List;

class ModelCatalogTest {
    @TempDir Path root;

    @Test
    void internalParticleModelsAndCompatibilityAliasesRemainUsableButAreNotPublicItems()
            throws Exception {
        put(
                "demo/items/warning.json",
                "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:item/generated\"}}");
        put(
                "assetchannel/items/generated/blocks/warning/particles.json",
                "{\"assetchannel:internal\":true,\"model\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:item/generated\"}}");
        put(
                "demo/items/cube.json",
                "{\"assetchannel:internal\":true,\"model\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:item/generated\"}}");
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            var api = new AssetChannelApiImpl();
            api.loadAssets(List.of(ImportedResources.scan(root)));
            assertEquals(List.of("demo:warning"), api.models().stream().map(m -> m.id()).toList());
            assertTrue(api.model("assetchannel:generated/blocks/warning/particles").isPresent());
            assertTrue(api.model("demo:cube").isPresent());
        }
    }

    private void put(String name, String json) throws Exception {
        Path path = root.resolve(name);
        Files.createDirectories(path.getParent());
        Files.writeString(path, json);
    }
}
