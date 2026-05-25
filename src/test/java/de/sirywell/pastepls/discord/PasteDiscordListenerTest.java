package de.sirywell.pastepls.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.junit.jupiter.api.Test;

class PasteDiscordListenerTest {
    @Test
    void commandSupportsUserInstallsAndPrivateContexts() {
        CommandData command = PasteDiscordListener.createCommand();

        assertEquals("Upload to paste", command.getName());
        assertEquals(Command.Type.MESSAGE, command.getType());
        assertEquals(Set.of(IntegrationType.GUILD_INSTALL, IntegrationType.USER_INSTALL), command.getIntegrationTypes());
        assertEquals(
            Set.of(
                InteractionContextType.GUILD,
                InteractionContextType.BOT_DM,
                InteractionContextType.PRIVATE_CHANNEL
            ),
            command.getContexts()
        );
    }
}
