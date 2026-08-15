package nu.miguel.assetChannel.api.internal;

import nu.miguel.assetChannel.api.SoundOrigin;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SpeakerSelectionTest {
    @Test void selectsExactlyTheNearestSpeakerInTheListenersWorld() {
        World world = world();
        SoundOrigin.Fixed first = SoundOrigin.fixed(new Location(world, 0, 64, 0));
        SoundOrigin.Fixed second = SoundOrigin.fixed(new Location(world, 20, 64, 0));
        SoundOrigin group = SoundOrigin.speakers(List.of(first, second));

        SoundOrigin selected = new AssetChannelApiImpl().selectOrigin(group, new Location(world, 14, 64, 0));

        assertSame(second, selected);
    }

    @Test void ignoresSpeakersInOtherWorldsAndReturnsNullWhenNoneAreAvailable() {
        World firstWorld = world();
        World secondWorld = world();
        SoundOrigin.Fixed speaker = SoundOrigin.fixed(new Location(firstWorld, 0, 64, 0));

        assertNull(new AssetChannelApiImpl().selectOrigin(
                SoundOrigin.speakers(List.of(speaker)), new Location(secondWorld, 0, 64, 0)));
    }

    @Test void convertsGroupedEntitySpeakerToCoordinatesForClientPlayback() {
        World world = world();
        SoundOrigin.Fixed fallback = SoundOrigin.fixed(new Location(world, 8, 64, 3));
        SoundOrigin.EntityOrigin marker = new SoundOrigin.EntityOrigin(UUID.randomUUID(), fallback);

        SoundOrigin selected = new AssetChannelApiImpl().selectOrigin(
                SoundOrigin.speakers(List.of(marker)), new Location(world, 10, 64, 3));

        SoundOrigin.Fixed fixed = assertInstanceOf(SoundOrigin.Fixed.class, selected);
        assertEquals(8, fixed.x());
        assertEquals(64, fixed.y());
        assertEquals(3, fixed.z());
    }

    @Test void returnsEverySameWorldSpeakerEvenWhenInitiallyFarAway() {
        World world = world();
        SoundOrigin.Fixed first = SoundOrigin.fixed(new Location(world, 0, 64, 0));
        SoundOrigin.Fixed second = SoundOrigin.fixed(new Location(world, 10, 64, 0));
        SoundOrigin.Fixed outOfRange = SoundOrigin.fixed(new Location(world, 40, 64, 0));

        List<SoundOrigin> available = new AssetChannelApiImpl().speakersInWorld(
                SoundOrigin.speakers(List.of(first, second, outOfRange)), world);

        assertEquals(List.of(first, second, outOfRange), available);
    }

    @SuppressWarnings("unchecked")
    private static World world() {
        UUID id = UUID.randomUUID();
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (object, method, args) -> switch (method.getName()) {
                    case "getUID" -> id;
                    case "hashCode" -> System.identityHashCode(object);
                    case "equals" -> object == args[0];
                    case "toString" -> "WorldProxy";
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
