package nu.miguel.assetChannel.api;

import java.util.*;

/** Canonical note-block state used by explicit and automatically persisted reservations. */
public record NativeBlockState(String instrument, int note, boolean powered) {
    private static final Set<String> INSTRUMENTS =
            Set.of(
                    "harp",
                    "basedrum",
                    "snare",
                    "hat",
                    "bass",
                    "flute",
                    "bell",
                    "guitar",
                    "chime",
                    "xylophone",
                    "iron_xylophone",
                    "cow_bell",
                    "didgeridoo",
                    "bit",
                    "banjo",
                    "pling");

    public NativeBlockState {
        if (!INSTRUMENTS.contains(Objects.requireNonNull(instrument)) || note < 0 || note > 24)
            throw new IllegalArgumentException(
                    "Invalid reserved note-block instrument/note: " + instrument + "/" + note);
    }

    public static NativeBlockState parse(String state) {
        Objects.requireNonNull(state, "block-state");
        if (!state.matches("(?:minecraft:)?note_block\\[[a-z0-9_=,]+\\]"))
            throw new IllegalArgumentException(
                    "block-state must specify a minecraft:note_block instrument, note and powered"
                            + " value");
        Map<String, String> values = new TreeMap<>();
        for (String entry :
                state.substring(state.indexOf('[') + 1, state.length() - 1).split(",")) {
            String[] pair = entry.split("=", -1);
            if (pair.length != 2 || values.putIfAbsent(pair[0], pair[1]) != null)
                throw new IllegalArgumentException(
                        "Invalid or duplicate block-state property: " + entry);
        }
        if (!values.keySet().equals(Set.of("instrument", "note", "powered"))
                || !Set.of("true", "false").contains(values.get("powered")))
            throw new IllegalArgumentException(
                    "block-state requires instrument, note and powered exactly once");
        try {
            return new NativeBlockState(
                    values.get("instrument"),
                    Integer.parseInt(values.get("note")),
                    Boolean.parseBoolean(values.get("powered")));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(
                    "Invalid block-state note: " + values.get("note"), error);
        }
    }

    public Map<String, String> properties() {
        return Map.of(
                "instrument",
                instrument,
                "note",
                Integer.toString(note),
                "powered",
                Boolean.toString(powered));
    }

    public String asString() {
        return "minecraft:note_block[instrument="
                + instrument
                + ",note="
                + note
                + ",powered="
                + powered
                + "]";
    }

    /** Stable allocation pool for generated native block definitions. */
    public static List<NativeBlockState> availableStates() {
        List<NativeBlockState> states = new ArrayList<>();
        for (String instrument : new TreeSet<>(INSTRUMENTS))
            for (int note = 0; note <= 24; note++) {
                states.add(new NativeBlockState(instrument, note, false));
                states.add(new NativeBlockState(instrument, note, true));
            }
        return List.copyOf(states);
    }
}
