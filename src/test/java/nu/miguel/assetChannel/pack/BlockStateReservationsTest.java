package nu.miguel.assetChannel.pack;

import nu.miguel.assetChannel.api.NativeBlockState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class BlockStateReservationsTest {
    @TempDir Path root;

    @Test void persistsAllocationsAndKeepsRemovedIdsReservedWhenHashesCollide() {
        assertEquals("an".hashCode(), "c0".hashCode());
        var first = new BlockStateReservations(root);
        String state = first.allocate("an");
        assertTrue(first.snapshot().build(null));
        var second = new BlockStateReservations(root);
        assertEquals(state, second.allocate("an"));
        assertNotEquals(state, second.allocate("c0"));
        assertThrows(IllegalArgumentException.class, () -> second.reserve("different", state));
    }

    @Test void adoptsExplicitAssignmentsAndRejectsExhaustionOrReassignment() {
        var states = NativeBlockState.availableStates();
        assertEquals(800, states.size());
        assertEquals(800, new java.util.HashSet<>(states).size());
        var reservations = new BlockStateReservations(root);
        String explicit = "minecraft:note_block[instrument=pling,note=24,powered=false]";
        reservations.reserve("warning", explicit);
        assertEquals(explicit, reservations.allocate("warning"));
        assertThrows(IllegalArgumentException.class, () -> reservations.reserve("warning", states.getFirst().asString()));
        for (int i = 0; i < states.size(); i++) if (!states.get(i).asString().equals(explicit)) reservations.reserve("block_" + i, states.get(i).asString());
        assertThrows(IllegalArgumentException.class, () -> reservations.allocate("one_more"));
    }

    @Test void neverOverwritesAssignmentsChangedDuringABuild() throws Exception {
        var reservations = new BlockStateReservations(root); reservations.allocate("warning");
        var snapshot = reservations.snapshot();
        Path file = root.resolve("storage/block-states.json"); Files.createDirectories(file.getParent());
        String external = "{\"version\":1,\"blocks\":{}}"; Files.writeString(file, external);
        assertThrows(IllegalStateException.class, () -> snapshot.build(null));
        assertEquals(external, Files.readString(file));
    }
}
