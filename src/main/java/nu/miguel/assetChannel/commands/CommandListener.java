package nu.miguel.assetChannel.commands;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.Plugin;
import com.mojang.brigadier.arguments.*;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.EntitySelectorArgumentResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.FinePositionResolver;
import io.papermc.paper.math.FinePosition;
import nu.miguel.assetChannel.Main;
import nu.miguel.assetChannel.api.*;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.minimessage.MiniMessage;
import java.util.*;

public class CommandListener {
    // This is the final argument: greedyString preserves ':' and '/' without requiring quotes.
    static StringArgumentType modelIdArgument() { return StringArgumentType.greedyString(); }
    private static final List<String> SOUND_CATEGORIES = Arrays.stream(SoundCategory.values())
            .map(category -> category.name().toLowerCase(Locale.ROOT))
            .toList();

    public CommandListener(Plugin plugin) {
        LifecycleEventManager<Plugin> lifecycleEventManager = plugin.getLifecycleManager();

        lifecycleEventManager.registerEventHandler(LifecycleEvents.COMMANDS, commands -> {
            commands.registrar().register(modelCommand());
        });
    }

    private LiteralCommandNode<CommandSourceStack> modelCommand(){
        return Commands.literal("asset")
                .requires(ctx -> ctx.getSender().hasPermission("assetchannel.command.test"))
                .then(Commands.literal("texturepack")
                        .then(Commands.literal("generate")
                                .executes(TextureCommand::generateTexturePack))
                )
                .then(iconCommands())
                .then(fontCommands())
                .then(soundCommands())
                .then(musicCommands())
                .then(hudCommands())
                .then(worldDisplayCommands())
                .then(blockCommands())
                .then(Commands.literal("resource").then(Commands.literal("list").executes(c -> {
                    var resources=Main.getAssetApi().resources();
                    resources.stream().limit(50).forEach(resource->c.getSource().getSender().sendMessage(Component.text(resource.kind()+" "+resource.key().asString())));
                    c.getSource().getSender().sendMessage(Component.text(resources.size()+" resources (showing up to 50)."));return resources.size();
                })))
                .then(Commands.literal("model")
                        .then(Commands.literal("list").executes(c -> {
                            Main.getAssetApi().models().forEach(model -> c.getSource().getSender().sendMessage(Component.text(model.id())));
                            return Main.getAssetApi().models().size();
                        }))
                        .then(Commands.literal("give").then(Commands.argument("id", modelIdArgument())
                                .suggests((c,b) -> suggest(b, Main.getAssetApi().models().stream().map(ModelAsset::id).toList()))
                                .executes(c -> {
                                    if (!(c.getSource().getSender() instanceof Player player)) return playerOnly(c);
                                    if (!Main.getInternalApi().isPackReady(player.getUniqueId())) { player.sendRichMessage("<yellow>Current pack is not ready."); return 0; }
                                    var model = Main.getAssetApi().model(StringArgumentType.getString(c, "id")).filter(value -> !value.internal());
                                    if (model.isEmpty()) { player.sendRichMessage("<red>Unknown model."); return 0; }
                                    if (player.getInventory().firstEmpty() < 0) { player.sendRichMessage("<yellow>Make room in your inventory."); return 0; }
                                    player.getInventory().addItem(model.get().createItem(org.bukkit.Material.PAPER)); return 1;
                                }))))
                .build();

    }

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> worldDisplayCommands() {
        return Commands.literal("display")
                .then(Commands.literal("spawn").then(Commands.argument("id",modelIdArgument())
                        .suggests((c,b)->suggest(b,Main.getAssetApi().models().stream().map(ModelAsset::id).toList()))
                        .executes(c->{
                            if(!(c.getSource().getSender() instanceof Player player))return playerOnly(c);
                            if(!Main.getAssetApi().isPackReady(player)){player.sendRichMessage("<yellow>Wait for the pack to load.");return 0;}
                            try {
                                var location=player.getLocation().add(player.getLocation().getDirection().multiply(2)).add(0,1,0);
                                location.setPitch(0);
                                String id = StringArgumentType.getString(c,"id");
                                if (Main.getAssetApi().model(id).filter(value -> !value.internal()).isEmpty()) throw new IllegalArgumentException("Unknown model: " + id);
                                var display=Main.getAssetApi().spawnItemDisplay(location,id,DisplayOptions.defaults());
                                player.sendMessage(Component.text("Spawned display "+display.getUniqueId()));return 1;
                            }catch(IllegalArgumentException|IllegalStateException error){player.sendMessage(Component.text(error.getMessage()));return 0;}
                        })))
                .then(Commands.literal("remove").then(Commands.argument("uuid",ArgumentTypes.uuid())
                        .suggests((c,b) -> suggest(b, Main.getInternalApi().worldAssets().removableDisplays().stream().map(display -> display.getUniqueId().toString()).sorted().toList()))
                        .executes(c->{
                            try{return Main.getAssetApi().removeDisplay(c.getArgument("uuid",UUID.class))?1:0;}
                            catch(IllegalArgumentException error){c.getSource().getSender().sendMessage(Component.text(error.getMessage()));return 0;}
                        })))
                .then(Commands.literal("list").executes(c->{
                    var displays=Main.getInternalApi().worldAssets().removableDisplays();displays.forEach(display->c.getSource().getSender().sendMessage(Component.text(display.getUniqueId()+" "+display.getType())));return displays.size();
                }));
    }
    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> blockCommands() {
        return Commands.literal("block")
                .then(blockOverlayCommand(false))
                .then(blockOverlayCommand(true))
                .then(Commands.literal("clear").executes(c -> {
                    if (!(c.getSource().getSender() instanceof Player player)) return playerOnly(c);
                    var overlays = List.copyOf(Main.getAssetApi().blockOverlays(player));
                    overlays.forEach(BlockOverlay::close);
                    player.sendMessage(Component.text("Cleared " + overlays.size() + " block overlays."));
                    return overlays.size();
                }))
                .then(Commands.literal("list").executes(c->{Main.getAssetApi().blocks().forEach(block->c.getSource().getSender().sendMessage(Component.text(block.id()+" -> "+block.model())));return Main.getAssetApi().blocks().size();}))
                .then(Commands.literal("give").then(Commands.argument("id",modelIdArgument())
                        .suggests((c,b)->suggest(b,Main.getAssetApi().blocks().stream().map(BlockAsset::id).toList()))
                        .executes(c->{
                            if(!(c.getSource().getSender() instanceof Player player))return playerOnly(c);
                            if(!Main.getAssetApi().isPackReady(player)){player.sendRichMessage("<yellow>Wait for the pack to load.");return 0;}
                            if(player.getInventory().firstEmpty()<0){player.sendRichMessage("<yellow>Make room in your inventory.");return 0;}
                            try{player.getInventory().addItem(Main.getAssetApi().createBlockItem(StringArgumentType.getString(c,"id")));return 1;}
                            catch(IllegalArgumentException error){player.sendMessage(Component.text(error.getMessage()));return 0;}
                        })))
                .then(Commands.literal("remove").executes(c->{
                    if(!(c.getSource().getSender() instanceof Player player))return playerOnly(c);
                    var target=player.getTargetBlockExact(6);
                    if(target==null)return 0;
                    return Main.getAssetApi().removeBlock(target.getLocation(),false)?1:0;
                }));
    }

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> blockOverlayCommand(boolean blink) {
        var id = Commands.argument("id", StringArgumentType.word())
                .suggests((c, b) -> suggest(b, Main.getAssetApi().blocks().stream().filter(block -> block.blockState() != null).map(BlockAsset::id).toList()))
                .executes(c -> showBlockOverlay(c, blink, 5, 200));
        if (blink) id.then(Commands.argument("interval", IntegerArgumentType.integer(2, 200))
                .executes(c -> showBlockOverlay(c, true, IntegerArgumentType.getInteger(c, "interval"), 200))
                .then(Commands.argument("duration", IntegerArgumentType.integer(2, 1200))
                        .executes(c -> showBlockOverlay(c, true, IntegerArgumentType.getInteger(c, "interval"), IntegerArgumentType.getInteger(c, "duration")))));
        return Commands.literal(blink ? "blink" : "overlay").then(id);
    }

    private int showBlockOverlay(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, boolean blink, int interval, int duration) {
        if (!(context.getSource().getSender() instanceof Player player)) return playerOnly(context);
        var target = player.getTargetBlockExact(6);
        if (target == null) { player.sendMessage(Component.text("Look at a full block within six blocks.")); return 0; }
        try {
            String id = StringArgumentType.getString(context, "id");
            if (blink) Main.getInternalApi().blockOverlayManager().previewBlink(player, target.getLocation(), id, interval, duration);
            else Main.getAssetApi().showBlockOverlay(player, target.getLocation(), id);
            player.sendMessage(Component.text(blink ? "Blinking " + id + " for " + duration + " ticks." : "Showing " + id + "; /asset block clear restores the real block."));
            return 1;
        } catch (IllegalArgumentException | IllegalStateException error) {
            player.sendMessage(Component.text(error.getMessage())); return 0;
        }
    }

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> iconCommands() {
        var id = Commands.argument("id", StringArgumentType.word())
                .suggests((c, b) -> suggest(b, Main.getAssetApi().icons().stream().map(IconAsset::id).toList()));
        var test = Commands.literal("test").then(id.executes(this::testIcon));
        var info = Commands.literal("info").then(Commands.argument("id", StringArgumentType.word())
                .suggests((c, b) -> suggest(b, Main.getAssetApi().icons().stream().map(IconAsset::id).toList()))
                .executes(this::iconInfo));
        var list = Commands.literal("list").executes(c -> {
            Collection<IconAsset> icons = Main.getAssetApi().icons();
            if (icons.isEmpty()) c.getSource().getSender().sendRichMessage("<yellow>No icons are loaded.");
            for (IconAsset icon : icons) {
                c.getSource().getSender().sendMessage(Component.text(icon.character()).font(icon.font())
                        .append(Component.text(" " + icon.id() + " (" + icon.unicode() + ")")));
            }
            return icons.size();
        });
        return Commands.literal("icon").then(test).then(info).then(list);
    }

    private int testIcon(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "id");
        Optional<IconAsset> icon = Main.getAssetApi().icon(id);
        if (icon.isEmpty()) {
            context.getSource().getSender().sendRichMessage("<red>Unknown icon: " + id);
            return 0;
        }
        context.getSource().getSender().sendMessage(Component.text(icon.get().character()).font(icon.get().font()));
        return 1;
    }

    private int iconInfo(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "id");
        Optional<IconAsset> icon = Main.getAssetApi().icon(id);
        if (icon.isEmpty()) {
            context.getSource().getSender().sendRichMessage("<red>Unknown icon: " + id);
            return 0;
        }
        IconAsset value = icon.get();
        context.getSource().getSender().sendRichMessage("<aqua>" + value.id() + " <gray>unicode=<white>"
                + value.unicode() + " <gray>font=<white>" + value.font().asString()
                + " <gray>height=<white>" + value.height() + " <gray>ascent=<white>" + value.ascent());
        return 1;
    }

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> fontCommands() {
        return Commands.literal("font").then(Commands.literal("test")
                .then(Commands.argument("id", StringArgumentType.word()).suggests((c,b) -> suggest(b, Main.getAssetApi().fonts().stream().map(FontAsset::id).toList()))
                        .executes(c -> testFont(c, "AssetChannel font test: AaBb 123"))
                        .then(Commands.argument("text", StringArgumentType.greedyString()).executes(c -> testFont(c, StringArgumentType.getString(c, "text"))))))
                .then(Commands.literal("list").executes(c -> {
                    Collection<FontAsset> fonts = Main.getAssetApi().fonts();
                    if (fonts.isEmpty()) c.getSource().getSender().sendRichMessage("<yellow>No fonts are loaded.");
                    for (FontAsset font : fonts)
                        c.getSource().getSender().sendRichMessage("<aqua>" + font.id() + " <gray>(<white>" + font.key().asString() + "<gray>)");
                    return fonts.size();
                }));
    }

    private int testFont(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, String text) {
        if (!(context.getSource().getSender() instanceof Player player)) return playerOnly(context);
        String id = StringArgumentType.getString(context, "id");
        Optional<FontAsset> font = Main.getAssetApi().font(id);
        if (font.isEmpty()) { player.sendRichMessage("<red>Unknown font: " + id); return 0; }
        player.sendMessage(Component.text(text).font(font.get().key()));
        return 1;
    }

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> soundCommands() {
        var playCategory = Commands.argument("category", StringArgumentType.word())
                .suggests((c, b) -> suggest(b, SOUND_CATEGORIES))
                .executes(c -> playSound(c, 1, 1))
                .then(Commands.argument("volume", FloatArgumentType.floatArg(0)).executes(c -> playSound(c, FloatArgumentType.getFloat(c,"volume"), 1))
                        .then(Commands.argument("pitch", FloatArgumentType.floatArg(0.01f, 2)).executes(c -> playSound(c, FloatArgumentType.getFloat(c,"volume"), FloatArgumentType.getFloat(c,"pitch")))));
        var play = Commands.literal("play").then(Commands.argument("id", StringArgumentType.word())
                .suggests((c,b) -> suggest(b, Main.getAssetApi().sounds().stream().map(SoundAsset::id).toList()))
                .then(playCategory));
        var stop = Commands.literal("stop").then(Commands.argument("id", StringArgumentType.word())
                .suggests((c,b) -> { b.suggest("all"); return suggest(b, Main.getAssetApi().sounds().stream().map(SoundAsset::id).toList()); })
                .executes(this::stopSound)
                .then(Commands.argument("category", StringArgumentType.word())
                        .suggests((c, b) -> suggest(b, SOUND_CATEGORIES))
                        .executes(this::stopSoundInCategory)));
        return Commands.literal("sound").then(play).then(stop)
                .then(spatialSoundCommand("playat", false, false))
                .then(spatialSoundCommand("broadcastat", false, true))
                .then(spatialSoundCommand("playfrom", true, false))
                .then(spatialSoundCommand("broadcastfrom", true, true));
    }

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> spatialSoundCommand(
            String name, boolean entityOrigin, boolean broadcast) {
        var id = Commands.argument("id", StringArgumentType.word())
                .suggests((c,b) -> suggest(b, Main.getAssetApi().sounds().stream().map(SoundAsset::id).toList()));
        var category = Commands.argument("category", StringArgumentType.word())
                .suggests((c, b) -> suggest(b, SOUND_CATEGORIES));
        com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, ?> origin;
        if (entityOrigin) origin = Commands.argument("origin", ArgumentTypes.entity());
        else origin = Commands.argument("origin", ArgumentTypes.finePosition());
        origin.executes(c -> playSpatialSound(c, entityOrigin, broadcast, 1, 1))
                .then(Commands.argument("volume", FloatArgumentType.floatArg(0))
                        .executes(c -> playSpatialSound(c, entityOrigin, broadcast, FloatArgumentType.getFloat(c, "volume"), 1))
                        .then(Commands.argument("pitch", FloatArgumentType.floatArg(0.01f, 2))
                                .executes(c -> playSpatialSound(c, entityOrigin, broadcast,
                                        FloatArgumentType.getFloat(c, "volume"), FloatArgumentType.getFloat(c, "pitch")))));
        if (broadcast) category.then(origin);
        else category.then(Commands.argument("players", ArgumentTypes.players()).then(origin));
        id.then(category);
        return Commands.literal(name).then(id);
    }

    private int playSpatialSound(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c,
                                 boolean entityOrigin, boolean broadcast, float volume, float pitch) {
        try {
            SoundOrigin origin = resolveOrigin(c, entityOrigin);
            String id = StringArgumentType.getString(c, "id");
            SoundCategory category = soundCategory(c);
            if (broadcast) {
                BroadcastPlaybackResult result = Main.getAssetApi().broadcastSound(id, origin, category, volume, pitch);
                c.getSource().getSender().sendRichMessage(result.result() == PlaybackResult.PLAYED
                        ? "<green>Played for " + result.played().size() + " nearby players"
                            + (result.packNotReady().isEmpty() ? "." : " <yellow>(" + result.packNotReady().size() + " pack not ready)")
                        : "<red>Unable to play sound: " + result.result());
                return result.played().size();
            }
            List<Player> players = c.getArgument("players", PlayerSelectorArgumentResolver.class).resolve(c.getSource());
            int played = 0;
            for (Player player : players)
                if (Main.getAssetApi().playSound(player, id, origin, category, volume, pitch) == PlaybackResult.PLAYED) played++;
            c.getSource().getSender().sendRichMessage("<green>Played for " + played + " players.");
            return played;
        } catch (Exception e) {
            c.getSource().getSender().sendRichMessage("<red>" + e.getMessage());
            return 0;
        }
    }

    private SoundOrigin resolveOrigin(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c,
                                      boolean entityOrigin) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (entityOrigin) {
            List<Entity> entities = c.getArgument("origin", EntitySelectorArgumentResolver.class).resolve(c.getSource());
            if (entities.isEmpty()) throw new IllegalArgumentException("No source entity found");
            return SoundOrigin.entity(entities.getFirst());
        }
        FinePosition position = c.getArgument("origin", FinePositionResolver.class).resolve(c.getSource());
        return SoundOrigin.fixed(position.toLocation(c.getSource().getLocation().getWorld()));
    }

    private int playSound(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c, float volume, float pitch) {
        if (!(c.getSource().getSender() instanceof Player player)) return playerOnly(c);
        try {
            PlaybackResult result = Main.getAssetApi().playSound(player, StringArgumentType.getString(c,"id"),
                    soundCategory(c), volume, pitch);
            player.sendRichMessage(result == PlaybackResult.PLAYED ? "<green>Sound started." : "<red>Unable to play sound: " + result);
            return result == PlaybackResult.PLAYED ? 1 : 0;
        } catch (IllegalArgumentException e) {
            player.sendRichMessage("<red>" + e.getMessage());
            return 0;
        }
    }

    private int stopSound(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c) {
        if (!(c.getSource().getSender() instanceof Player player)) return playerOnly(c);
        String id = StringArgumentType.getString(c,"id");
        if (id.equalsIgnoreCase("all")) { Main.getAssetApi().stopAllSounds(player); return 1; }
        return Main.getAssetApi().stopSound(player, id) ? 1 : 0;
    }

    private int stopSoundInCategory(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c) {
        if (!(c.getSource().getSender() instanceof Player player)) return playerOnly(c);
        String id = StringArgumentType.getString(c,"id");
        if (id.equalsIgnoreCase("all")) { Main.getAssetApi().stopAllSounds(player); return 1; }
        try {
            return Main.getAssetApi().stopSound(player, id, soundCategory(c)) ? 1 : 0;
        } catch (IllegalArgumentException e) {
            player.sendRichMessage("<red>" + e.getMessage());
            return 0;
        }
    }

    private static SoundCategory soundCategory(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        return parseSoundCategory(StringArgumentType.getString(context, "category"));
    }

    static SoundCategory parseSoundCategory(String value) {
        try {
            return SoundCategory.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown sound category '" + value + "'. Valid categories: "
                    + String.join(", ", SOUND_CATEGORIES));
        }
    }

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> musicCommands() {
        var start = Commands.literal("start").then(Commands.argument("id", StringArgumentType.word())
                .suggests((c,b) -> suggest(b, Main.getAssetApi().soundtracks().stream().map(SoundtrackAsset::id).toList()))
                .then(Commands.argument("players", ArgumentTypes.players())
                        .executes(c -> startMusic(c, SessionSettings.defaults()))
                        .then(Commands.argument("loop", BoolArgumentType.bool()).executes(c -> startMusic(c, settings(c, false, false, 1)))
                                .then(Commands.argument("synchronized", BoolArgumentType.bool()).executes(c -> startMusic(c, settings(c, true, false, 1)))
                                        .then(Commands.argument("retain", BoolArgumentType.bool()).executes(c -> startMusic(c, settings(c, true, true, 1)))
                                                .then(Commands.argument("volume", FloatArgumentType.floatArg(0)).executes(c -> startMusic(c, settings(c, true, true, FloatArgumentType.getFloat(c,"volume"))))))))));
        var add = Commands.literal("add").then(Commands.argument("session", ArgumentTypes.uuid()).suggests(this::suggestSessions)
                .then(Commands.argument("players", ArgumentTypes.players()).executes(c -> changeMembers(c, true))));
        var remove = Commands.literal("remove").then(Commands.argument("session", ArgumentTypes.uuid()).suggests(this::suggestSessions)
                .then(Commands.argument("players", ArgumentTypes.players()).executes(c -> changeMembers(c, false))));
        var stop = Commands.literal("stop").then(Commands.argument("session", ArgumentTypes.uuid()).suggests(this::suggestSessions).executes(c -> {
            Optional<MusicSession> session = Main.getAssetApi().session(c.getArgument("session", UUID.class));
            session.ifPresent(MusicSession::stop); return session.isPresent() ? 1 : 0;
        }));
        var stages = Commands.literal("stages").then(Commands.argument("soundtrack", StringArgumentType.word())
                .suggests((c,b) -> suggest(b, Main.getAssetApi().soundtracks().stream().map(SoundtrackAsset::id).toList()))
                .executes(this::listStages));
        var stage = Commands.literal("stage").then(Commands.argument("session", ArgumentTypes.uuid()).suggests(this::suggestSessions)
                .then(Commands.argument("stage", StringArgumentType.word()).suggests(this::suggestStages)
                        .then(Commands.argument("transition", StringArgumentType.word())
                                .suggests((c,b) -> suggest(b, List.of("immediate", "session-boundary", "player-boundary")))
                                .executes(this::changeStage))));
        var origin = Commands.literal("origin").then(Commands.argument("session", ArgumentTypes.uuid()).suggests(this::suggestSessions)
                .then(Commands.literal("listener").executes(c -> setSessionOrigin(c, SoundOrigin.listenerRelative())))
                .then(Commands.literal("location").then(Commands.argument("origin", ArgumentTypes.finePosition())
                        .executes(c -> setSessionOrigin(c, resolveOrigin(c, false)))))
                .then(Commands.literal("entity").then(Commands.argument("origin", ArgumentTypes.entity())
                        .executes(c -> setSessionOrigin(c, resolveOrigin(c, true)))))
                .then(Commands.literal("speakers").then(Commands.argument("origins", ArgumentTypes.entities())
                        .executes(c -> setSessionOrigin(c, resolveSpeakers(c))))));
        return Commands.literal("music").then(start).then(add).then(remove).then(stop).then(stages).then(stage).then(origin)
                .then(Commands.literal("list").executes(c -> {
                    if (Main.getAssetApi().sessions().isEmpty()) c.getSource().getSender().sendRichMessage("<yellow>No active sessions.");
                    for (MusicSession s : Main.getAssetApi().sessions())
                        c.getSource().getSender().sendRichMessage("<aqua>"+s.id()+" <white>"+s.soundtrack().id()
                                +" <gray>stage=<white>"+s.stage()+(s.transitioning() ? " <yellow>(transition pending)" : "")
                                +" <gray>("+s.members().size()+" players)");
                    return Main.getAssetApi().sessions().size();
                }));
    }

    private SoundOrigin resolveSpeakers(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        List<Entity> entities = c.getArgument("origins", EntitySelectorArgumentResolver.class).resolve(c.getSource());
        return SoundOrigin.speakers(entities.stream().map(SoundOrigin::entity).toList());
    }

    private int setSessionOrigin(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c, SoundOrigin origin) {
        Optional<MusicSession> session = Main.getAssetApi().session(c.getArgument("session", UUID.class));
        if (session.isEmpty()) { c.getSource().getSender().sendRichMessage("<red>Unknown session."); return 0; }
        boolean changed = session.get().setOrigin(origin);
        String detail = origin instanceof SoundOrigin.Speakers speakers
                ? " <gray>(" + speakers.origins().size() + " simultaneous full-volume speakers)" : "";
        c.getSource().getSender().sendRichMessage(changed
                ? "<green>Music origin updated." + detail : "<red>Session is inactive.");
        return changed ? 1 : 0;
    }

    private int listStages(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c) {
        String id = StringArgumentType.getString(c, "soundtrack");
        Optional<SoundtrackAsset> soundtrack = Main.getAssetApi().soundtrack(id);
        if (soundtrack.isEmpty()) { c.getSource().getSender().sendRichMessage("<red>Unknown soundtrack: " + id); return 0; }
        SoundtrackAsset asset = soundtrack.get();
        c.getSource().getSender().sendRichMessage("<aqua>" + asset.id() + " <gray>stages (default: <white>" + asset.defaultStage() + "<gray>): <white>"
                + String.join("<gray>, <white>", asset.stages().keySet()));
        return asset.stages().size();
    }

    private int changeStage(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c) {
        Optional<MusicSession> session = Main.getAssetApi().session(c.getArgument("session", UUID.class));
        if (session.isEmpty()) { c.getSource().getSender().sendRichMessage("<red>Unknown session."); return 0; }
        StageTransitionSettings settings = switch (StringArgumentType.getString(c, "transition").toLowerCase(Locale.ROOT)) {
            case "immediate" -> StageTransitionSettings.immediate();
            case "session-boundary" -> StageTransitionSettings.nextSessionBoundary();
            case "player-boundary" -> StageTransitionSettings.nextPlayerBoundary();
            default -> null;
        };
        if (settings == null) { c.getSource().getSender().sendRichMessage("<red>Unknown transition mode."); return 0; }
        StageChangeResult result = session.get().setStage(StringArgumentType.getString(c, "stage"), settings);
        c.getSource().getSender().sendRichMessage(result == StageChangeResult.APPLIED || result == StageChangeResult.SCHEDULED
                ? "<green>Stage change: " + result : "<yellow>Stage change: " + result);
        return result == StageChangeResult.APPLIED || result == StageChangeResult.SCHEDULED ? 1 : 0;
    }

    private SessionSettings settings(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c, boolean hasSync, boolean hasRetain, float volume) {
        return new SessionSettings(BoolArgumentType.getBool(c,"loop"), hasSync && BoolArgumentType.getBool(c,"synchronized"),
                hasRetain && BoolArgumentType.getBool(c,"retain"), volume);
    }

    private int startMusic(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c, SessionSettings settings) {
        try {
            List<Player> players = c.getArgument("players", PlayerSelectorArgumentResolver.class).resolve(c.getSource());
            MusicSession session = Main.getAssetApi().startSoundtrack(StringArgumentType.getString(c,"id"), players, settings);
            c.getSource().getSender().sendRichMessage("<green>Started session <aqua>" + session.id());
            return 1;
        } catch (Exception e) { c.getSource().getSender().sendRichMessage("<red>"+e.getMessage()); return 0; }
    }

    private int changeMembers(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c, boolean add) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Optional<MusicSession> session = Main.getAssetApi().session(c.getArgument("session", UUID.class));
        if (session.isEmpty()) return 0;
        int changed = 0;
        for (Player p : c.getArgument("players", PlayerSelectorArgumentResolver.class).resolve(c.getSource()))
            if (add ? session.get().addPlayer(p) : session.get().removePlayer(p)) changed++;
        return changed;
    }

    private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestSessions(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c, com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
        return suggest(b, Main.getAssetApi().sessions().stream().map(s -> s.id().toString()).toList());
    }
    private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestStages(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c, com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
        try {
            return Main.getAssetApi().session(c.getArgument("session", UUID.class))
                    .map(session -> suggest(b, session.soundtrack().stages().keySet()))
                    .orElseGet(b::buildFuture);
        } catch (IllegalArgumentException ignored) {
            return b.buildFuture();
        }
    }
    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggest(com.mojang.brigadier.suggestion.SuggestionsBuilder b, Collection<String> values) {
        String remaining = b.getRemainingLowerCase();
        values.stream().filter(v -> v.startsWith(remaining)).forEach(b::suggest);
        return b.buildFuture();
    }
    private int playerOnly(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c) { c.getSource().getSender().sendRichMessage("<red>Only players can use this command."); return 0; }

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> hudCommands() {
        var template = Commands.argument("template", StringArgumentType.word())
                .suggests((c,b) -> suggest(b, Main.getAssetApi().huds().stream().map(HudAsset::id).toList()))
                .executes(c -> showHud(c, null, null, null, Map.of()))
                .then(Commands.literal("with").then(Commands.argument("variables", StringArgumentType.greedyString())
                        .executes(c -> showHudParsed(c, null, null, null))))
                .then(Commands.argument("slot", StringArgumentType.word())
                        .executes(c -> showHud(c, StringArgumentType.getString(c,"slot"), null, null, Map.of()))
                        .then(Commands.argument("priority", IntegerArgumentType.integer())
                                .executes(c -> showHud(c, StringArgumentType.getString(c,"slot"), IntegerArgumentType.getInteger(c,"priority"), null, Map.of()))
                                .then(Commands.argument("resume", BoolArgumentType.bool())
                                        .executes(c -> showHud(c, StringArgumentType.getString(c,"slot"), IntegerArgumentType.getInteger(c,"priority"), BoolArgumentType.getBool(c,"resume"), Map.of()))
                                        .then(Commands.argument("variables", StringArgumentType.greedyString())
                                                .executes(c -> showHudParsed(c, StringArgumentType.getString(c,"slot"), IntegerArgumentType.getInteger(c,"priority"), BoolArgumentType.getBool(c,"resume")))))));
        var show=Commands.literal("show").then(Commands.argument("players",ArgumentTypes.players()).then(template));
        var update=Commands.literal("update").then(Commands.argument("players",ArgumentTypes.players())
                .then(Commands.argument("slot",StringArgumentType.word())
                        .then(Commands.argument("variables",StringArgumentType.greedyString()).executes(this::updateHud))));
        var hide=Commands.literal("hide").then(Commands.argument("players",ArgumentTypes.players())
                .then(Commands.argument("slot",StringArgumentType.word()).executes(this::hideHud)));
        var active=Commands.literal("active").then(Commands.argument("players",ArgumentTypes.players()).executes(this::activeHuds));
        var list=Commands.literal("list").executes(this::listHuds);
        return Commands.literal("hud").then(show).then(update).then(hide).then(active).then(list);
    }

    private int showHud(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c,String slot,Integer priority,Boolean resume,Map<String,Component> variables) {
        try {
            List<Player> players=c.getArgument("players",PlayerSelectorArgumentResolver.class).resolve(c.getSource());
            HudRequest request=new HudRequest(variables,Optional.ofNullable(slot),Optional.ofNullable(priority),Optional.ofNullable(resume));
            String template=StringArgumentType.getString(c,"template");int accepted=0;
            Map<HudDisplayResult,Integer> results=new EnumMap<>(HudDisplayResult.class);
            for(Player player:players){HudDisplayResult result=Main.getAssetApi().showHud(player,template,request);results.merge(result,1,Integer::sum);if(result==HudDisplayResult.DISPLAYED||result==HudDisplayResult.QUEUED||result==HudDisplayResult.TEXT_FALLBACK)accepted++;}
            c.getSource().getSender().sendRichMessage((accepted>0?"<green>":"<red>")+"HUD result: <white>"+results);
            return accepted;
        } catch(Exception error){c.getSource().getSender().sendRichMessage("<red>Could not show HUD: "+error.getMessage());return 0;}
    }

    private int showHudParsed(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c,String slot,Integer priority,Boolean resume){
        try{return showHud(c,slot,priority,resume,parseHudVariables(StringArgumentType.getString(c,"variables")));}
        catch(IllegalArgumentException error){c.getSource().getSender().sendRichMessage("<red>Could not parse HUD variables: "+error.getMessage());return 0;}
    }

    private int updateHud(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c) {
        try {
            List<Player> players=c.getArgument("players",PlayerSelectorArgumentResolver.class).resolve(c.getSource());String slot=StringArgumentType.getString(c,"slot");
            Map<String,Component> variables=parseHudVariables(StringArgumentType.getString(c,"variables"));int updated=0;
            for(Player player:players){Optional<HudSession> session=Main.getAssetApi().hudSession(player,slot);if(session.isPresent()&&session.get().updateVariables(variables))updated++;}
            c.getSource().getSender().sendRichMessage(updated>0?"<green>Updated "+updated+" HUD session(s).":"<red>No matching HUD session accepted the variables.");return updated;
        }catch(Exception error){c.getSource().getSender().sendRichMessage("<red>Could not update HUD: "+error.getMessage());return 0;}
    }

    private int hideHud(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c) {
        try {int hidden=0;String slot=StringArgumentType.getString(c,"slot");for(Player player:c.getArgument("players",PlayerSelectorArgumentResolver.class).resolve(c.getSource()))if(Main.getAssetApi().hideHud(player,slot))hidden++;
            c.getSource().getSender().sendRichMessage(hidden>0?"<green>Hid HUD for "+hidden+" player(s).":"<yellow>No matching HUD sessions.");return hidden;
        }catch(Exception error){c.getSource().getSender().sendRichMessage("<red>Could not hide HUD: "+error.getMessage());return 0;}
    }

    private int activeHuds(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c) {
        try {Set<UUID> players=c.getArgument("players",PlayerSelectorArgumentResolver.class).resolve(c.getSource()).stream().map(Player::getUniqueId).collect(java.util.stream.Collectors.toSet());
            List<HudSession> found=Main.getAssetApi().hudSessions().stream().filter(s->players.contains(s.playerId())).toList();
            if(found.isEmpty())c.getSource().getSender().sendRichMessage("<yellow>No HUD sessions for those players.");
            for(HudSession s:found)c.getSource().getSender().sendRichMessage("<aqua>"+s.id()+" <white>"+s.templateId()+" <gray>player=<white>"+s.playerId()+" <gray>slot=<white>"+s.slot()+" <gray>surface=<white>"+s.surface()+" <gray>state=<white>"+s.state()+" <gray>remaining=<white>"+(s.remainingDuration().isZero()?"persistent":s.remainingDuration().toMillis()+"ms"));
            return found.size();
        }catch(Exception error){c.getSource().getSender().sendRichMessage("<red>Could not list HUD sessions: "+error.getMessage());return 0;}
    }

    private int listHuds(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c) {
        Collection<HudAsset> huds=Main.getAssetApi().huds();if(huds.isEmpty())c.getSource().getSender().sendRichMessage("<yellow>No HUD templates are loaded.");
        for(HudAsset hud:huds)c.getSource().getSender().sendRichMessage("<aqua>"+hud.id()+" <gray>surface=<white>"+hud.surface()+" <gray>slot=<white>"+hud.slot()+" <gray>priority=<white>"+hud.priority()+" <gray>variables=<white>"+String.join(",",hud.variables().keySet()));return huds.size();
    }

    static Map<String,Component> parseHudVariables(String input) {
        if(input==null||input.isBlank())return Map.of();Map<String,Component> result=new LinkedHashMap<>();MiniMessage mini=MiniMessage.miniMessage();
        for(String assignment:input.split(";",-1)){int equals=assignment.indexOf('=');if(equals<=0)throw new IllegalArgumentException("Variables must use name=value separated by semicolons");String name=assignment.substring(0,equals).trim();String value=assignment.substring(equals+1).trim();if(!name.matches("[a-zA-Z][a-zA-Z0-9_.-]*"))throw new IllegalArgumentException("Invalid variable name '"+name+"'");if(result.putIfAbsent(name,mini.deserialize(value))!=null)throw new IllegalArgumentException("Duplicate variable '"+name+"'");}
        return Map.copyOf(result);
    }
}
