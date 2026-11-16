package nu.miguel.assetChannel.api;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeBlockStateTest {
    @Test void canonicalizesExplicitReservationsAndRejectsAmbiguousOrUnsupportedStates() {
        assertEquals("minecraft:note_block[instrument=pling,note=24,powered=false]",
                NativeBlockState.parse("note_block[powered=false,note=24,instrument=pling]").asString());
        for (String invalid : new String[]{"minecraft:stone", "note_block[note=1]",
                "note_block[instrument=pling,note=24,note=1,powered=false]",
                "note_block[instrument=pling,note=24,powered=yes]"})
            assertThrows(IllegalArgumentException.class, () -> NativeBlockState.parse(invalid), invalid);
    }

    @Test void oldBlockConstructorAndPersistentDefinitionSnapshotsRemainReadable() {
        var legacy = new BlockAsset("cube", "demo:cube", "minecraft:barrier", "minecraft:stone", null,
                "minecraft:block.stone.break", "minecraft:block.stone.place", 20, true);
        assertNull(legacy.blockState()); assertNull(legacy.blockModel());
        var restored = new Gson().fromJson("""
                {"id":"cube","model":"demo:cube","carrier":"minecraft:barrier","particleBlock":"minecraft:stone",
                "breakSound":"minecraft:block.stone.break","placeSound":"minecraft:block.stone.place","particleCount":20,"drops":true}
                """, BlockAsset.class);
        assertEquals(legacy, restored);
    }
}
