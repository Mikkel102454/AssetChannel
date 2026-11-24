package nu.miguel.assetChannel.pack;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;

import nu.miguel.assetChannel.Main;
import nu.miguel.assetChannel.util.Config;
import nu.miguel.assetChannel.util.Logger;
import nu.miguel.assetChannel.util.PathResolver;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

public final class PackWebServer {

    private static final String PACK_FILE_NAME = "pack.zip";
    private static final UUID PACK_UUID = UUID.fromString("2c6f8b1d-45a8-4c8e-9b2e-4f5f7e2a1c11");

    public static boolean ownsPack(UUID id) {
        return PACK_UUID.equals(id);
    }

    private final Path packZipPath = PathResolver.getPackZipPath();

    private HttpServer httpServer;
    private ExecutorService executor;

    // snapshot of the current serving pack
    private final AtomicReference<PackSnapshot> snapshotRef =
            new AtomicReference<>(PackSnapshot.empty());

    public void startHttpServer() throws IOException {
        int port = (int) Config.get("overrides.pack.port", 25566);

        // get pack info (sha1)
        reloadPackSnapshot();

        httpServer = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        httpServer.createContext("/" + PACK_FILE_NAME, this::handlePackRequest);

        // thread pool to stpo 1 client from blocking
        executor =
                Executors.newFixedThreadPool(
                        Math.max(2, Runtime.getRuntime().availableProcessors()));
        httpServer.setExecutor(executor);

        httpServer.start();

        Logger.log(Level.INFO, "Serving /" + PACK_FILE_NAME + " on port " + port);
    }

    public void stopHttpServer() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    /**
     * Call this after you rebuild/replace pack.zip. It swaps the in-memory bytes + sha1 atomically.
     */
    public void reloadPackSnapshot() {
        try {
            if (!Files.exists(packZipPath)) {
                Logger.log(Level.WARNING, PACK_FILE_NAME + " not found at: " + packZipPath);
                return;
            }

            byte[] bytes = Files.readAllBytes(packZipPath);
            String sha1 = sha1Hex(bytes);

            PackSnapshot newSnapshot = new PackSnapshot(bytes, sha1);

            snapshotRef.set(newSnapshot);

            Logger.log(Level.INFO, "Loaded resource pack snapshot. SHA-1: " + sha1);

            Bukkit.getScheduler()
                    .runTask(
                            Main.plugin,
                            () -> {
                                for (Player player : Bukkit.getOnlinePlayers()) {
                                    Main.getInternalApi().setPackReady(player, false);
                                    givePlayerPack(player);
                                }
                            });

        } catch (Exception e) {
            Logger.log(Level.SEVERE, "Failed to reload resource pack: " + e.getMessage());
        }
    }

    private void handlePackRequest(HttpExchange ex) throws IOException {
        try (ex) {
            PackSnapshot snap = snapshotRef.get();
            if (!snap.isReady()) {
                ex.sendResponseHeaders(404, -1);
                return;
            }

            String method = ex.getRequestMethod();
            if (!method.equalsIgnoreCase("GET") && !method.equalsIgnoreCase("HEAD")) {
                ex.sendResponseHeaders(405, -1);
                return;
            }

            Headers h = ex.getResponseHeaders();
            h.set("Content-Type", "application/zip");
            h.set("Content-Disposition", "attachment; filename=\"" + PACK_FILE_NAME + "\"");
            h.set("Cache-Control", "no-store");
            h.set("Content-Length", String.valueOf(snap.bytes.length));

            if (method.equalsIgnoreCase("HEAD")) {
                ex.sendResponseHeaders(200, -1);
                return;
            }

            ex.sendResponseHeaders(200, snap.bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(snap.bytes);
                os.flush();
            }
        }
    }

    public void givePlayerPack(Player player) {
        PackSnapshot snap = snapshotRef.get();
        if (!snap.isReady()) {
            Logger.log(Level.WARNING, "Can't send pack: snapshot not ready.");
            return;
        }

        String host = (String) Config.get("overrides.pack.host", "127.0.0.1");
        int port = (int) Config.get("overrides.pack.port", 25566);
        boolean required = (boolean) Config.get("overrides.pack.required", true);
        boolean replace = (boolean) Config.get("overrides.pack.replace", true);

        URI uri = URI.create("http://" + host + ":" + port + "/" + PACK_FILE_NAME);

        ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(PACK_UUID, uri, snap.sha1);

        ResourcePackRequest request =
                ResourcePackRequest.resourcePackRequest()
                        .packs(info)
                        .prompt(
                                Component.text(
                                        "This server uses a resource pack. Please accept to play."))
                        .required(required)
                        .replace(replace)
                        .build();

        player.sendResourcePacks(request);
    }

    private static String sha1Hex(byte[] data) throws Exception {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] digest = sha1.digest(data);
        return HexFormat.of().formatHex(digest);
    }

    private static final class PackSnapshot {
        final byte[] bytes;
        final String sha1;

        private PackSnapshot(byte[] bytes, String sha1) {
            this.bytes = bytes;
            this.sha1 = sha1;
        }

        static PackSnapshot empty() {
            return new PackSnapshot(new byte[0], "");
        }

        boolean isReady() {
            return bytes != null && bytes.length > 0 && sha1 != null && !sha1.isEmpty();
        }
    }
}
