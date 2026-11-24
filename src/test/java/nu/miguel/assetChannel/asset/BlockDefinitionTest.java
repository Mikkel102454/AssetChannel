package nu.miguel.assetChannel.asset;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;

class BlockDefinitionTest {
    @TempDir Path root;

    @Test
    void simpleRenderDefaultsToModelParticlesAndRejectsUnknownModesOrInvalidRegions()
            throws Exception {
        Path file = root.resolve("simple.yml");
        Files.writeString(file, "render: block\nmodel: demo:warning\n");
        assertEquals(
                "demo:warning", BlockDefinition.load("warning", file).descriptor().particleModel());
        for (String invalid :
                new String[] {
                    "render: invalid\nmodel: demo:warning\n",
                    "render: block\n"
                            + "model: demo:warning\n"
                            + "particles: {texture: 'demo:sheet', region: [-1, 0, 16, 16]}\n",
                    "render: block\n"
                            + "model: demo:warning\n"
                            + "particles: {texture: 'demo:sheet', region: [0, 0, 16]}\n"
                }) {
            Files.writeString(file, invalid);
            assertThrows(
                    IllegalArgumentException.class, () -> BlockDefinition.load("warning", file));
        }
    }

    @Test
    void loadsNativeBlockStateAndGeometryWithoutChangingTheItemModel() throws Exception {
        Path file = root.resolve("native.yml");
        Files.writeString(
                file,
                "model: demo:cube\n"
                    + "block-state: minecraft:note_block[note=24,powered=false,instrument=pling]\n"
                    + "block-model: demo:blocks/cube\n");
        var block = BlockDefinition.load("warning_block", file).descriptor();
        assertEquals("demo:cube", block.model());
        assertEquals(
                "minecraft:note_block[instrument=pling,note=24,powered=false]",
                block.getClass().getMethod("blockState").invoke(block));
        assertEquals("demo:blocks/cube", block.getClass().getMethod("blockModel").invoke(block));
    }

    @Test
    void rejectsIncompleteOrInvalidNativeStateDefinitions() throws Exception {
        Path file = root.resolve("native.yml");
        for (String extra :
                new String[] {
                    "block-state: minecraft:note_block[instrument=pling,note=24,powered=false]\n",
                    "block-model: demo:cube\n",
                    "block-state: minecraft:note_block[instrument=pling,note=25,powered=false]\n"
                            + "block-model: demo:cube\n",
                    "block-state: minecraft:note_block[instrument=invalid,note=1,powered=false]\n"
                            + "block-model: demo:cube\n"
                }) {
            Files.writeString(file, "model: demo:cube\n" + extra);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> BlockDefinition.load("warning_block", file),
                    extra);
        }
    }

    @Test
    void loadsModelCarrierBreakSoundAndParticleAssets() throws Exception {
        Path file = root.resolve("block.yml");
        Files.writeString(
                file,
                "model: demo:cube\n"
                        + "carrier: minecraft:barrier\n"
                        + "break-sound: demo:crack\n"
                        + "particle-model: demo:shard\n"
                        + "drops: true\n");
        var block = BlockDefinition.load("demo.cube", file).descriptor();
        assertEquals("demo:cube", block.model());
        assertEquals("demo:shard", block.particleModel());
        assertEquals("demo:crack", block.breakSound());
        assertTrue(block.drops());
    }

    @Test
    void rejectsMissingModelAndUnboundedEffects() throws Exception {
        Path file = root.resolve("block.yml");
        Files.writeString(file, "carrier: minecraft:barrier\n");
        assertThrows(IllegalArgumentException.class, () -> BlockDefinition.load("test", file));
        Files.writeString(file, "model: demo:cube\nparticle-count: 999999\n");
        assertThrows(IllegalArgumentException.class, () -> BlockDefinition.load("test", file));
    }
}
