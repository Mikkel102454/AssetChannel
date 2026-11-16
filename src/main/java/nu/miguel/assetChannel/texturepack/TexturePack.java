package nu.miguel.assetChannel.texturepack;

import nu.miguel.assetChannel.util.Logger;
import nu.miguel.assetChannel.util.PathResolver;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.logging.Level;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.UUID;

public class TexturePack {
    static final int RESOURCE_PACK_FORMAT_MAJOR = 97;
    static final int RESOURCE_PACK_FORMAT_MINOR = 1;

    Path rootPath;
    Path assetPath;

    int packFormat = RESOURCE_PACK_FORMAT_MAJOR;
    String description = "AssetChannel resource pack";

    public TexturePack() {
        rootPath = PathResolver.getGeneratedPath().resolve("pack-staging-" + UUID.randomUUID());
        assetPath = rootPath.resolve("assets").resolve("assetchannel");

        try {
            Files.createDirectories(rootPath);
            Files.createDirectories(assetPath);

            createMetaData();

        } catch (IOException | UncheckedIOException e) {
            throw new IllegalStateException("Failed to initialize texture-pack staging directory", e);
        }
    }

    public void createMetaData() {
        try {
            File packMeta = new File(rootPath.toFile(), "pack.mcmeta");

            String content = metadata(packFormat, RESOURCE_PACK_FORMAT_MINOR, description);

            Files.writeString(packMeta.toPath(), content);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Path publish() {
        Path zipPath = PathResolver.getPackZipPath();
        Path temporaryZip = PathResolver.getGeneratedPath()
                .resolve("pack.zip.tmp-" + UUID.randomUUID());

        try {
            Files.deleteIfExists(temporaryZip);

            try (
                    ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(temporaryZip));
                    Stream<Path> files = Files.walk(rootPath)
            ) {
                files
                        .filter(Files::isRegularFile)
                        .forEach(file -> {
                            try {
                                String entryName = rootPath
                                        .relativize(file)
                                        .toString()
                                        .replace('\\', '/');

                                zipOut.putNextEntry(
                                        new ZipEntry(entryName)
                                );

                                Files.copy(file, zipOut);
                                zipOut.closeEntry();

                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        });
            }

            try {
                Files.move(temporaryZip, zipPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temporaryZip, zipPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            replaceDebugPack();
            return zipPath;

        } catch (IOException | UncheckedIOException e) {
            throw new RuntimeException("Failed to create pack.zip", e);
        } finally {
            try { Files.deleteIfExists(temporaryZip); }
            catch (IOException e) {
                Logger.log(Level.WARNING, "Could not clean temporary pack ZIP: " + e.getMessage());
            }
        }
    }

    static String metadata(int major, int minor, String description) {
        return """
                {
                  "pack": {
                    "min_format": [%d, %d],
                    "max_format": [%d, %d],
                    "description": "%s"
                  }
                }
                """.formatted(major, minor, major, minor, description);
    }

    private void replaceDebugPack() {
        Path published = PathResolver.getGeneratedPath().resolve("pack");
        Path old = PathResolver.getGeneratedPath().resolve("pack-old-" + UUID.randomUUID());
        try {
            if (Files.exists(published)) Files.move(published, old);
            Files.move(rootPath, published);
            deleteTree(old);
            rootPath = published;
            assetPath = rootPath.resolve("assets").resolve("assetchannel");
        } catch (IOException | UncheckedIOException e) {
            try {
                if (!Files.exists(published) && Files.exists(old)) Files.move(old, published);
            } catch (IOException rollback) {
                e.addSuppressed(rollback);
            }
            Logger.log(Level.WARNING, "Pack ZIP was published, but its debug directory could not be replaced: " + e.getMessage());
            deleteTree(rootPath);
        }
    }

    private static void deleteTree(Path root) {
        if (!Files.exists(root)) return;
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); }
                catch (IOException e) { throw new UncheckedIOException(e); }
            });
        } catch (IOException | UncheckedIOException e) {
            Logger.log(Level.WARNING, "Could not clean generated directory " + root.getFileName() + ": " + e.getMessage());
        }
    }

    public Path getRootPath() {
        return rootPath;
    }

    public int getPackFormat() {
        return packFormat;
    }

    public String getDescription() {
        return description;
    }

    public Path getAssetPath() {
        return assetPath;
    }

    public void discard() {
        deleteTree(rootPath);
    }
}
