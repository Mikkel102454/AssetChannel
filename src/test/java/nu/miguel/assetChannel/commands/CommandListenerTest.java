package nu.miguel.assetChannel.commands;

import static org.junit.jupiter.api.Assertions.*;

import io.papermc.paper.command.brigadier.argument.ArgumentTypes;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.SoundCategory;
import org.junit.jupiter.api.Test;

import java.util.Locale;

class CommandListenerTest {
    @Test
    void modelGiveRejectsInternalEffectItemsBeforeTouchingTheInventory() throws Exception {
        try (var arguments =
                        org.mockito.Mockito.mockStatic(
                                ArgumentTypes.class,
                                call ->
                                        org.mockito.Mockito.mock(
                                                com.mojang.brigadier.arguments.ArgumentType
                                                        .class));
                var main = org.mockito.Mockito.mockStatic(nu.miguel.assetChannel.Main.class)) {
            var api =
                    org.mockito.Mockito.mock(
                            nu.miguel.assetChannel.api.internal.AssetChannelApiImpl.class);
            main.when(nu.miguel.assetChannel.Main::getInternalApi).thenReturn(api);
            main.when(nu.miguel.assetChannel.Main::getAssetApi).thenReturn(api);
            var player = org.mockito.Mockito.mock(org.bukkit.entity.Player.class);
            var id = java.util.UUID.randomUUID();
            org.mockito.Mockito.when(player.getUniqueId()).thenReturn(id);
            org.mockito.Mockito.when(player.hasPermission("assetchannel.command.test"))
                    .thenReturn(true);
            org.mockito.Mockito.when(api.isPackReady(id)).thenReturn(true);
            String helper = "assetchannel:generated/blocks/warning/particles";
            org.mockito.Mockito.when(api.model(helper))
                    .thenReturn(
                            java.util.Optional.of(
                                    new nu.miguel.assetChannel.api.ModelAsset(
                                            helper,
                                            net.kyori.adventure.key.Key.key(helper),
                                            true)));
            var listener =
                    org.mockito.Mockito.mock(
                            CommandListener.class, org.mockito.Mockito.CALLS_REAL_METHODS);
            var builder = CommandListener.class.getDeclaredMethod("modelCommand");
            builder.setAccessible(true);
            var dispatcher =
                    new com.mojang.brigadier.CommandDispatcher<
                            io.papermc.paper.command.brigadier.CommandSourceStack>();
            dispatcher
                    .getRoot()
                    .addChild((com.mojang.brigadier.tree.CommandNode) builder.invoke(listener));
            var source =
                    org.mockito.Mockito.mock(
                            io.papermc.paper.command.brigadier.CommandSourceStack.class);
            org.mockito.Mockito.when(source.getSender()).thenReturn(player);
            assertEquals(0, dispatcher.execute("asset model give " + helper, source));
            org.mockito.Mockito.verify(player, org.mockito.Mockito.never()).getInventory();
        }
    }

    @Test
    void displayRemoveSuggestsLoadedStandaloneDisplayIdsAndExcludesManagedBlocks()
            throws Exception {
        try (var arguments =
                        org.mockito.Mockito.mockStatic(
                                ArgumentTypes.class,
                                call -> {
                                    var type =
                                            org.mockito.Mockito.mock(
                                                    com.mojang.brigadier.arguments.ArgumentType
                                                            .class);
                                    org.mockito.Mockito.when(
                                                    type.listSuggestions(
                                                            org.mockito.ArgumentMatchers.any(),
                                                            org.mockito.ArgumentMatchers.any()))
                                            .thenReturn(
                                                    com.mojang.brigadier.suggestion.Suggestions
                                                            .empty());
                                    return type;
                                });
                var main = org.mockito.Mockito.mockStatic(nu.miguel.assetChannel.Main.class);
                var bukkit = org.mockito.Mockito.mockStatic(org.bukkit.Bukkit.class)) {
            bukkit.when(org.bukkit.Bukkit::isPrimaryThread).thenReturn(true);
            var api =
                    org.mockito.Mockito.mock(
                            nu.miguel.assetChannel.api.internal.AssetChannelApiImpl.class);
            var manager = new nu.miguel.assetChannel.api.internal.WorldAssetManager(api);
            org.mockito.Mockito.when(api.worldAssets()).thenReturn(manager);
            main.when(nu.miguel.assetChannel.Main::getInternalApi).thenReturn(api);
            main.when(nu.miguel.assetChannel.Main::getAssetApi).thenReturn(api);
            var world = org.mockito.Mockito.mock(org.bukkit.World.class);
            bukkit.when(org.bukkit.Bukkit::getWorlds).thenReturn(java.util.List.of(world));
            var standalone = org.mockito.Mockito.mock(org.bukkit.entity.Display.class);
            var placed = org.mockito.Mockito.mock(org.bukkit.entity.Display.class);
            var key = new org.bukkit.NamespacedKey("assetchannel", "display_kind");
            for (var display : java.util.List.of(standalone, placed)) {
                org.mockito.Mockito.when(display.getUniqueId())
                        .thenReturn(java.util.UUID.randomUUID());
                org.mockito.Mockito.when(display.getPersistentDataContainer())
                        .thenReturn(
                                org.mockito.Mockito.mock(
                                        org.bukkit.persistence.PersistentDataContainer.class));
                org.mockito.Mockito.when(
                                display.getPersistentDataContainer()
                                        .has(key, org.bukkit.persistence.PersistentDataType.STRING))
                        .thenReturn(true);
            }
            org.mockito.Mockito.when(
                            standalone
                                    .getPersistentDataContainer()
                                    .get(key, org.bukkit.persistence.PersistentDataType.STRING))
                    .thenReturn("item");
            org.mockito.Mockito.when(
                            placed.getPersistentDataContainer()
                                    .get(key, org.bukkit.persistence.PersistentDataType.STRING))
                    .thenReturn("placed");
            org.mockito.Mockito.when(world.getEntitiesByClass(org.bukkit.entity.Display.class))
                    .thenReturn(java.util.List.of(standalone, placed));
            var listener =
                    org.mockito.Mockito.mock(
                            CommandListener.class, org.mockito.Mockito.CALLS_REAL_METHODS);
            var builder = CommandListener.class.getDeclaredMethod("modelCommand");
            builder.setAccessible(true);
            var dispatcher =
                    new com.mojang.brigadier.CommandDispatcher<
                            io.papermc.paper.command.brigadier.CommandSourceStack>();
            dispatcher
                    .getRoot()
                    .addChild((com.mojang.brigadier.tree.CommandNode) builder.invoke(listener));
            var source =
                    org.mockito.Mockito.mock(
                            io.papermc.paper.command.brigadier.CommandSourceStack.class,
                            org.mockito.Mockito.RETURNS_DEEP_STUBS);
            org.mockito.Mockito.when(source.getSender().hasPermission("assetchannel.command.test"))
                    .thenReturn(true);
            var suggestions =
                    dispatcher
                            .getCompletionSuggestions(
                                    dispatcher.parse("asset display remove ", source))
                            .join();
            assertEquals(
                    java.util.List.of(standalone.getUniqueId().toString()),
                    suggestions.getList().stream().map(s -> s.getText()).toList());
        }
    }

    @Test
    void commandTreeKeepsAssetCommandsWithoutTemporaryInventoryPreview() throws Exception {
        try (var arguments =
                org.mockito.Mockito.mockStatic(
                        ArgumentTypes.class,
                        invocation ->
                                org.mockito.Mockito.mock(
                                        com.mojang.brigadier.arguments.ArgumentType.class))) {
            var listener =
                    org.mockito.Mockito.mock(
                            CommandListener.class, org.mockito.Mockito.CALLS_REAL_METHODS);
            var builder = CommandListener.class.getDeclaredMethod("modelCommand");
            builder.setAccessible(true);
            var root = (com.mojang.brigadier.tree.LiteralCommandNode<?>) builder.invoke(listener);
            assertNull(root.getChild("preview"));
            for (String command :
                    java.util.List.of("model", "display", "block", "hud", "texturepack"))
                assertNotNull(root.getChild(command), command);
            for (String command : java.util.List.of("overlay", "blink", "clear"))
                assertNotNull(root.getChild("block").getChild(command), command);
            var dispatcher =
                    new com.mojang.brigadier.CommandDispatcher<
                            io.papermc.paper.command.brigadier.CommandSourceStack>();
            dispatcher.getRoot().addChild((com.mojang.brigadier.tree.CommandNode) root);
            var source =
                    org.mockito.Mockito.mock(
                            io.papermc.paper.command.brigadier.CommandSourceStack.class,
                            org.mockito.Mockito.RETURNS_DEEP_STUBS);
            org.mockito.Mockito.when(source.getSender().hasPermission("assetchannel.command.test"))
                    .thenReturn(true);
            var parsed = dispatcher.parse("asset block blink warning_block 5 200", source);
            assertFalse(parsed.getReader().canRead(), parsed.getReader().getRemaining());
            assertNotNull(parsed.getContext().getCommand());
        }
    }

    @Test
    void modelIdArgumentConsumesNamespaceAndNestedPathWithoutQuotes() throws Exception {
        String id = "mcc:island_interface/navigator/crate_machine";
        var reader = new com.mojang.brigadier.StringReader(id);
        assertEquals(id, CommandListener.modelIdArgument().parse(reader));
        assertFalse(reader.canRead());
    }

    @Test
    void parsesEverySoundCategoryCaseInsensitively() {
        for (SoundCategory category : SoundCategory.values()) {
            assertEquals(
                    category,
                    CommandListener.parseSoundCategory(category.name().toLowerCase(Locale.ROOT)));
            assertEquals(category, CommandListener.parseSoundCategory(category.name()));
        }
    }

    @Test
    void rejectsUnknownSoundCategoryWithValidChoices() {
        IllegalArgumentException error =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> CommandListener.parseSoundCategory("enemy"));

        assertTrue(error.getMessage().contains("Unknown sound category 'enemy'"));
        assertTrue(error.getMessage().contains("hostile"));
        assertTrue(error.getMessage().contains("neutral"));
    }

    @Test
    void parsesHudVariableAssignmentsAsMiniMessageComponents() {
        var values =
                CommandListener.parseHudVariables("heading=<gold>Quest Started;body=Hello world");
        assertEquals(
                "Quest Started",
                PlainTextComponentSerializer.plainText().serialize(values.get("heading")));
        assertEquals(
                "Hello world",
                PlainTextComponentSerializer.plainText().serialize(values.get("body")));
    }

    @Test
    void rejectsMalformedAndDuplicateHudVariables() {
        assertThrows(
                IllegalArgumentException.class, () -> CommandListener.parseHudVariables("heading"));
        assertThrows(
                IllegalArgumentException.class,
                () -> CommandListener.parseHudVariables("heading=one;heading=two"));
        assertThrows(
                IllegalArgumentException.class,
                () -> CommandListener.parseHudVariables("bad name=value"));
    }
}
