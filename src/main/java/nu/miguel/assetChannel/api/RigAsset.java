package nu.miguel.assetChannel.api;

import java.util.*;

/**
 * Immutable Blockbench-space rig. Positions are pixels (16 pixels per block), rotations degrees.
 */
public record RigAsset(String id, List<Bone> bones, List<Part> parts, Map<String, Clip> clips) {
    public RigAsset {
        Objects.requireNonNull(id);
        bones = List.copyOf(bones);
        parts = List.copyOf(parts);
        clips = Map.copyOf(clips);
    }

    public record Vec(double x, double y, double z) {
        public Vec {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                throw new IllegalArgumentException("Non-finite vector");
        }

        public static final Vec ZERO = new Vec(0, 0, 0), ONE = new Vec(1, 1, 1);

        public Vec lerp(Vec b, double t) {
            return new Vec(x + (b.x - x) * t, y + (b.y - y) * t, z + (b.z - z) * t);
        }
    }

    public record Bone(String id, String name, String parent, Vec pivot, Vec rotation) {}

    public record Part(String bone, String model, Vec pivot, Vec rotation) {}

    public record Frame(
            double time,
            Vec value,
            String interpolation,
            Vec leftTime,
            Vec leftValue,
            Vec rightTime,
            Vec rightValue) {
        public Frame(double time, Vec value, String interpolation) {
            this(time, value, interpolation, Vec.ZERO, Vec.ZERO, Vec.ZERO, Vec.ZERO);
        }

        public Frame {
            if (!Double.isFinite(time) || time < 0)
                throw new IllegalArgumentException("Invalid keyframe time");
            Objects.requireNonNull(value);
        }
    }

    public record Channel(String bone, String property, List<Frame> frames) {
        public Channel {
            frames = frames.stream().sorted(Comparator.comparingDouble(Frame::time)).toList();
        }
    }

    public record Clip(String name, double length, String loop, List<Channel> channels) {
        public Clip {
            if (!Double.isFinite(length) || length <= 0)
                throw new IllegalArgumentException("Clip needs positive length");
            channels = List.copyOf(channels);
        }
    }
}
