package nu.miguel.assetChannel.api;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SoundOriginTest {
    @Test void fixedOriginSnapshotsCoordinates() {
        World world = proxy(World.class, UUID.randomUUID(), null);
        Location location = new Location(world, 1.5, 2.5, 3.5);
        SoundOrigin.Fixed origin = SoundOrigin.fixed(location);
        location.setX(100);

        assertEquals(1.5, origin.x());
        assertEquals(world, origin.world());
        assertNotSame(origin.location(), origin.location());
    }

    @Test void entityOriginCapturesIdentityAndFallback() {
        UUID id = UUID.randomUUID();
        World world = proxy(World.class, UUID.randomUUID(), null);
        Location location = new Location(world, 4, 5, 6);
        Entity entity = proxy(Entity.class, id, location);

        SoundOrigin.EntityOrigin origin = SoundOrigin.entity(entity);

        assertEquals(id, origin.entityId());
        assertEquals(4, origin.fallback().x());
    }

    @Test void rejectsNonFiniteCoordinates() {
        World world = proxy(World.class, UUID.randomUUID(), null);
        assertThrows(IllegalArgumentException.class, () -> new SoundOrigin.Fixed(world, Double.NaN, 0, 0));
    }

    @Test void speakerGroupIsImmutableAndRejectsNestedOrRelativeOrigins() {
        World world = proxy(World.class, UUID.randomUUID(), null);
        SoundOrigin.Fixed first = SoundOrigin.fixed(new Location(world, 0, 0, 0));
        SoundOrigin.Fixed second = SoundOrigin.fixed(new Location(world, 10, 0, 0));
        List<SoundOrigin> input = new java.util.ArrayList<>(List.of(first, second));

        SoundOrigin.Speakers speakers = SoundOrigin.speakers(input);
        input.clear();

        assertEquals(List.of(first, second), speakers.origins());
        assertThrows(UnsupportedOperationException.class, () -> speakers.origins().clear());
        assertThrows(IllegalArgumentException.class, () -> SoundOrigin.speakers(List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> SoundOrigin.speakers(List.of(SoundOrigin.listenerRelative())));
        assertThrows(IllegalArgumentException.class, () -> SoundOrigin.speakers(List.of(speakers)));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, UUID id, Location location) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (object, method, args) -> switch (method.getName()) {
            case "getUniqueId", "getUID" -> id;
            case "getLocation" -> location == null ? null : location.clone();
            case "hashCode" -> System.identityHashCode(object);
            case "equals" -> object == args[0];
            case "toString" -> type.getSimpleName() + "Proxy";
            default -> defaultValue(method.getReturnType());
        });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        return 0D;
    }
}
