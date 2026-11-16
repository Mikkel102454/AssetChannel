package nu.miguel.assetChannel.commands;

import org.bukkit.SoundCategory;
import org.junit.jupiter.api.Test;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;

import java.util.Locale;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import static org.junit.jupiter.api.Assertions.*;

class CommandListenerTest {
    @Test void commandTreeKeepsAssetCommandsWithoutTemporaryInventoryPreview() throws Exception {
        try (var arguments = org.mockito.Mockito.mockStatic(ArgumentTypes.class,
                invocation -> org.mockito.Mockito.mock(com.mojang.brigadier.arguments.ArgumentType.class))) {
            var listener = org.mockito.Mockito.mock(CommandListener.class, org.mockito.Mockito.CALLS_REAL_METHODS);
            var builder = CommandListener.class.getDeclaredMethod("modelCommand");
            builder.setAccessible(true);
            var root = (com.mojang.brigadier.tree.LiteralCommandNode<?>) builder.invoke(listener);
            assertNull(root.getChild("preview"));
            for (String command : java.util.List.of("model", "display", "block", "hud", "texturepack"))
                assertNotNull(root.getChild(command), command);
        }
    }
    @Test void modelIdArgumentConsumesNamespaceAndNestedPathWithoutQuotes() throws Exception {
        String id = "mcc:island_interface/navigator/crate_machine";
        var reader = new com.mojang.brigadier.StringReader(id);
        assertEquals(id, CommandListener.modelIdArgument().parse(reader));
        assertFalse(reader.canRead());
    }
    @Test void parsesEverySoundCategoryCaseInsensitively() {
        for (SoundCategory category : SoundCategory.values()) {
            assertEquals(category, CommandListener.parseSoundCategory(category.name().toLowerCase(Locale.ROOT)));
            assertEquals(category, CommandListener.parseSoundCategory(category.name()));
        }
    }

    @Test void rejectsUnknownSoundCategoryWithValidChoices() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> CommandListener.parseSoundCategory("enemy"));

        assertTrue(error.getMessage().contains("Unknown sound category 'enemy'"));
        assertTrue(error.getMessage().contains("hostile"));
        assertTrue(error.getMessage().contains("neutral"));
    }

    @Test void parsesHudVariableAssignmentsAsMiniMessageComponents() {
        var values=CommandListener.parseHudVariables("heading=<gold>Quest Started;body=Hello world");
        assertEquals("Quest Started",PlainTextComponentSerializer.plainText().serialize(values.get("heading")));
        assertEquals("Hello world",PlainTextComponentSerializer.plainText().serialize(values.get("body")));
    }

    @Test void rejectsMalformedAndDuplicateHudVariables() {
        assertThrows(IllegalArgumentException.class,()->CommandListener.parseHudVariables("heading"));
        assertThrows(IllegalArgumentException.class,()->CommandListener.parseHudVariables("heading=one;heading=two"));
        assertThrows(IllegalArgumentException.class,()->CommandListener.parseHudVariables("bad name=value"));
    }
}
