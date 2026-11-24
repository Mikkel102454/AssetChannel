package nu.miguel.assetChannel.commands;

import com.mojang.brigadier.context.CommandContext;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import nu.miguel.assetChannel.Main;
import nu.miguel.assetChannel.asset.Asset;
import nu.miguel.assetChannel.pack.PackDeveloper;

import org.bukkit.Bukkit;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class TextureCommand {
    private static final AtomicBoolean BUILDING = new AtomicBoolean();

    public static int generateTexturePack(CommandContext<CommandSourceStack> ctx) {
        if (!BUILDING.compareAndSet(false, true)) {
            ctx.getSource()
                    .getSender()
                    .sendRichMessage("<yellow>A resource-pack build is already running.");
            return 0;
        }
        var sender = ctx.getSource().getSender();
        sender.sendRichMessage(
                "<aqua>Resource-pack build started in the background. Current playback will"
                        + " continue.");
        Bukkit.getScheduler()
                .runTaskAsynchronously(
                        Main.plugin,
                        () -> {
                            try {
                                List<Asset> assets = PackDeveloper.createPack();
                                Bukkit.getScheduler()
                                        .runTask(
                                                Main.plugin,
                                                () -> {
                                                    try {
                                                        Main.getInternalApi().stopSessions();
                                                        Main.getInternalApi().loadAssets(assets);
                                                        Main.packWebServer.reloadPackSnapshot();
                                                        sender.sendRichMessage(
                                                                "<green>Resource pack generated and"
                                                                    + " published successfully.");
                                                    } finally {
                                                        BUILDING.set(false);
                                                    }
                                                });
                            } catch (Exception e) {
                                Main.plugin
                                        .getLogger()
                                        .log(
                                                java.util.logging.Level.SEVERE,
                                                "Resource-pack build failed",
                                                e);
                                Bukkit.getScheduler()
                                        .runTask(
                                                Main.plugin,
                                                () -> {
                                                    BUILDING.set(false);
                                                    sender.sendRichMessage(
                                                            "<red>Resource-pack build failed; the"
                                                                + " previous pack is still active: "
                                                                    + rootMessage(e));
                                                });
                            }
                        });
        return 1;
    }

    private static String rootMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null) root = root.getCause();
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }
}
