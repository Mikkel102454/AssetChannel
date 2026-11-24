package nu.miguel.assetChannel;

import nu.miguel.assetChannel.pack.PackWebServer;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

public class EventListener implements Listener {
    @EventHandler
    public void OnPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Main.getInternalApi().setPackReady(player, false);
        Main.packWebServer.givePlayerPack(player);
    }

    @EventHandler
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        if (!PackWebServer.ownsPack(event.getID())) return;
        Main.getInternalApi()
                .setPackReady(
                        event.getPlayer(),
                        event.getStatus()
                                == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Main.getInternalApi().onQuit(event.getPlayer());
    }
}
