package nu.miguel.assetChannel.commands;

import org.bukkit.SoundCategory;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import static org.junit.jupiter.api.Assertions.*;

class CommandListenerTest {
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
