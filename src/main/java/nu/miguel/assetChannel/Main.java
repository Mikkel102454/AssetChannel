package nu.miguel.assetChannel;

import nu.miguel.assetChannel.commands.CommandListener;
import nu.miguel.assetChannel.pack.PackWebServer;
import nu.miguel.assetChannel.api.AssetChannelApi;
import nu.miguel.assetChannel.api.internal.AssetChannelApiImpl;
import nu.miguel.assetChannel.util.PathResolver;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import org.bukkit.plugin.ServicePriority;

import static nu.miguel.assetChannel.pack.PackDeveloper.createPack;

public final class Main extends JavaPlugin {
    public static Plugin plugin;
    public static FileConfiguration config;
    public static PackWebServer packWebServer;
    private static AssetChannelApiImpl assetApi;

    @Override
    public void onEnable() {
        plugin = this;

        saveDefaultConfig();
        config = getConfig();

        PathResolver.ensurePaths();

        assetApi = new AssetChannelApiImpl();
        getServer().getServicesManager().register(AssetChannelApi.class, assetApi, this, ServicePriority.Normal);

        new CommandListener(this);

        assetApi.loadAssets(createPack());
        getServer().getPluginManager().registerEvents(assetApi.worldAssets(), this);
        assetApi.worldAssets().initializeLoaded();

        packWebServer = new PackWebServer();
        try {
            packWebServer.startHttpServer();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        this.getServer().getPluginManager().registerEvents(new EventListener(), this);
    }

    @Override
    public void onDisable() {
        if (assetApi != null) {
            assetApi.stopSessions();
            assetApi.worldAssets().shutdown();
        }
        if (packWebServer != null) packWebServer.stopHttpServer();
        getServer().getServicesManager().unregisterAll(this);
    }

    public static AssetChannelApi getAssetApi() { return assetApi; }
    public static AssetChannelApiImpl getInternalApi() { return assetApi; }
}
