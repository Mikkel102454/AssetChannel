package nu.miguel.assetChannel.util;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Moves the previous layout after preflighting every target; source media bytes are preserved. */
final class LegacyLayoutMigration {
    private record Move(Path source, Path target) {}

    static void migrate(Path root) throws IOException {
        Map<String, String> folders = new LinkedHashMap<>();
        folders.put("imports/pack", "packs/main");
        folders.put("imports/assets", "packs/main/assets");
        folders.put("blocks", "packs/main/definitions/blocks");
        folders.put("hud", "packs/main/definitions/hud");
        folders.put("icons", "packs/main/sources/icons");
        folders.put("font", "packs/main/sources/fonts");
        folders.put("sound", "packs/main/sources/sounds");
        folders.put("music", "packs/main/sources/music");
        folders.put("pack.zip", "generated/pack.zip");
        folders.put("pack", "generated/pack");
        folders.put("cache", "generated/cache");

        List<Move> plan = new ArrayList<>();
        Map<Path, Path> targets = new HashMap<>();
        for (var folder : folders.entrySet()) {
            Path source = root.resolve(folder.getKey());
            Path destination = root.resolve(folder.getValue());
            if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) continue;
            try (var paths = Files.walk(source)) {
                for (Path path : paths.sorted().toList()) {
                    if (Files.isSymbolicLink(path)) throw new IOException("Migration symlinks are unsupported: " + path);
                    Path target = destination.resolve(source.relativize(path));
                    for (Path ancestor = target; ancestor != null && ancestor.startsWith(root); ancestor = ancestor.getParent()) {
                        if (Files.isSymbolicLink(ancestor)) throw new IOException("Migration target is a symlink: " + ancestor);
                        if (!ancestor.equals(target) && Files.exists(ancestor) && !Files.isDirectory(ancestor))
                            throw new IOException("Migration target parent is not a folder: " + ancestor);
                    }
                    if (Files.isDirectory(path)) {
                        if (Files.exists(target) && !Files.isDirectory(target)) conflict(path, target);
                        continue;
                    }
                    if (!Files.isRegularFile(path)) throw new IOException("Unsupported migration file: " + path);
                    Path previous = targets.putIfAbsent(target, path);
                    if (previous != null && Files.mismatch(previous, path) != -1) conflict(path, target);
                    if (Files.exists(target) && (!Files.isRegularFile(target) || Files.mismatch(path, target) != -1))
                        conflict(path, target);
                    plan.add(new Move(path, target));
                }
            }
        }

        List<Move> moved = new ArrayList<>();
        List<Move> duplicates = new ArrayList<>();
        try {
            for (Move move : plan) {
                if (Files.exists(move.target())) {
                    if (Files.mismatch(move.source(), move.target()) != -1) conflict(move.source(), move.target());
                    duplicates.add(move);
                } else {
                    Files.createDirectories(move.target().getParent());
                    Files.move(move.source(), move.target());
                    moved.add(move);
                }
            }
        } catch (IOException error) {
            for (Move move : moved.reversed()) try {
                Files.createDirectories(move.source().getParent());
                Files.move(move.target(), move.source());
            } catch (IOException rollback) { error.addSuppressed(rollback); }
            throw error;
        }
        for (Move duplicate : duplicates) Files.delete(duplicate.source());
        for (String folder : folders.keySet()) removeEmptyFolders(root.resolve(folder));
        removeEmptyFolders(root.resolve("imports"));
    }

    private static void conflict(Path source, Path target) throws IOException {
        throw new IOException("Migration conflict: " + source + " -> " + target + "; resolve the differing files before restarting");
    }

    private static void removeEmptyFolders(Path root) throws IOException {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) continue;
                boolean empty;
                try (var children = Files.list(path)) { empty = children.findAny().isEmpty(); }
                if (empty) Files.delete(path);
            }
        }
    }
}
