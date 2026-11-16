package nu.miguel.assetChannel.asset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class BlockDefinitionTest {
    @TempDir Path root;
    @Test void loadsModelCarrierBreakSoundAndParticleAssets() throws Exception {
        Path file=root.resolve("block.yml");
        Files.writeString(file,"model: demo:cube\ncarrier: minecraft:barrier\nbreak-sound: demo:crack\nparticle-model: demo:shard\ndrops: true\n");
        var block=BlockDefinition.load("demo.cube",file).descriptor();
        assertEquals("demo:cube",block.model());
        assertEquals("demo:shard",block.particleModel());
        assertEquals("demo:crack",block.breakSound());
        assertTrue(block.drops());
    }
    @Test void rejectsMissingModelAndUnboundedEffects() throws Exception {
        Path file=root.resolve("block.yml"); Files.writeString(file,"carrier: minecraft:barrier\n");
        assertThrows(IllegalArgumentException.class,()->BlockDefinition.load("test",file));
        Files.writeString(file,"model: demo:cube\nparticle-count: 999999\n");
        assertThrows(IllegalArgumentException.class,()->BlockDefinition.load("test",file));
    }
}
