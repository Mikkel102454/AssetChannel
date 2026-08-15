package nu.miguel.assetChannel.commands;

import org.bukkit.SoundCategory;
import org.junit.jupiter.api.Test;

import java.util.Locale;

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
}
