package nu.miguel.assetChannel.api;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Describes where a sound is emitted. */
public sealed interface SoundOrigin
        permits SoundOrigin.ListenerRelative,
                SoundOrigin.Fixed,
                SoundOrigin.EntityOrigin,
                SoundOrigin.Speakers {
    static ListenerRelative listenerRelative() {
        return ListenerRelative.INSTANCE;
    }

    static Fixed fixed(Location location) {
        Objects.requireNonNull(location, "location");
        World world = Objects.requireNonNull(location.getWorld(), "location world");
        return new Fixed(world, location.getX(), location.getY(), location.getZ());
    }

    static EntityOrigin entity(org.bukkit.entity.Entity entity) {
        Objects.requireNonNull(entity, "entity");
        return new EntityOrigin(entity.getUniqueId(), fixed(entity.getLocation()));
    }

    /**
     * Creates one logical origin backed by multiple physical speakers. Each listener hears only
     * their nearest available speaker, so overlapping ranges do not double-play.
     */
    static Speakers speakers(Collection<? extends SoundOrigin> origins) {
        return new Speakers(List.copyOf(origins));
    }

    enum ListenerRelative implements SoundOrigin {
        INSTANCE
    }

    record Fixed(World world, double x, double y, double z) implements SoundOrigin {
        public Fixed {
            Objects.requireNonNull(world, "world");
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                throw new IllegalArgumentException("Sound coordinates must be finite");
        }

        public Location location() {
            return new Location(world, x, y, z);
        }
    }

    record EntityOrigin(UUID entityId, Fixed fallback) implements SoundOrigin {
        public EntityOrigin {
            Objects.requireNonNull(entityId, "entityId");
            Objects.requireNonNull(fallback, "fallback");
        }
    }

    record Speakers(List<SoundOrigin> origins) implements SoundOrigin {
        public Speakers {
            origins = List.copyOf(Objects.requireNonNull(origins, "origins"));
            if (origins.isEmpty())
                throw new IllegalArgumentException("At least one speaker is required");
            if (origins.stream()
                    .anyMatch(origin ->
                            origin instanceof ListenerRelative || origin instanceof Speakers))
                throw new IllegalArgumentException(
                        "Speakers may contain only fixed or entity origins");
        }
    }
}
