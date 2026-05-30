package de.sirywell.pastepls.discord;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;

import de.sirywell.pastepls.config.AppConfig;
import de.sirywell.pastepls.config.PasteProvider;
import de.sirywell.pastepls.config.PasteServiceConfig;
import de.sirywell.pastepls.config.ResponseVisibility;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.context.MessageContextInteraction;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
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

    @Test
    void noSupportedAttachmentsReplyIsAlwaysEphemeral() {
        MessageContextInteraction interaction = mock(MessageContextInteraction.class);
        Message emptyMessage = mock(Message.class);
        ReplyCallbackAction replyAction = mock(ReplyCallbackAction.class);

        when(interaction.getName()).thenReturn(PasteDiscordListener.COMMAND_NAME);
        when(interaction.getTarget()).thenReturn(emptyMessage);
        when(emptyMessage.getAttachments()).thenReturn(List.of());
        when(interaction.deferReply()).thenReturn(replyAction);
        when(replyAction.setContent(anyString())).thenReturn(replyAction);
        when(replyAction.setEphemeral(anyBoolean())).thenReturn(replyAction);

        MessageContextInteractionEvent event = new MessageContextInteractionEvent(mock(JDA.class), 0L, interaction);
        listener(ResponseVisibility.PUBLIC).onMessageContextInteraction(event);

        verify(replyAction).setContent("No supported text attachments were found on the selected message.");
        verify(replyAction).setEphemeral(true);
        verify(replyAction).queue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void publicSuccessResponseUsesPublicFollowupAndDeletesPlaceholder() {
        InteractionHook hook = mock(InteractionHook.class);
        WebhookMessageCreateAction<Message> createAction = mock(WebhookMessageCreateAction.class);
        RestAction<Void> deleteAction = mock(RestAction.class);

        when(hook.sendMessage(anyString())).thenReturn(createAction);
        when(createAction.setEphemeral(anyBoolean())).thenReturn(createAction);
        when(hook.deleteOriginal()).thenReturn(deleteAction);
        doAnswer(inv -> {
            inv.<Consumer<Message>>getArgument(0).accept(null);
            return null;
        }).when(createAction).queue(any(), any());

        PasteDiscordListener.sendSuccessResponse(hook, ResponseVisibility.PUBLIC, "Uploaded attachment:\n- `test.txt`: https://pastes.dev/abc");

        verify(hook).sendMessage("Uploaded attachment:\n- `test.txt`: https://pastes.dev/abc");
        verify(createAction).setEphemeral(false);
        verify(hook).deleteOriginal();
        verify(hook, never()).editOriginal(anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void publicSuccessResponseFallsBackToEphemeralError() {
        InteractionHook hook = mock(InteractionHook.class);
        WebhookMessageCreateAction<Message> createAction = mock(WebhookMessageCreateAction.class);
        WebhookMessageEditAction<Message> editAction = mock(WebhookMessageEditAction.class);

        when(hook.sendMessage(anyString())).thenReturn(createAction);
        when(createAction.setEphemeral(anyBoolean())).thenReturn(createAction);
        when(hook.editOriginal(anyString())).thenReturn(editAction);
        doAnswer(inv -> {
            inv.<Consumer<Throwable>>getArgument(1).accept(new IllegalStateException("boom"));
            return null;
        }).when(createAction).queue(any(), any());

        PasteDiscordListener.sendSuccessResponse(hook, ResponseVisibility.PUBLIC, "Uploaded attachment:\n- `test.txt`: https://pastes.dev/abc");

        verify(hook).sendMessage("Uploaded attachment:\n- `test.txt`: https://pastes.dev/abc");
        verify(createAction).setEphemeral(false);
        verify(hook, never()).deleteOriginal();
        verify(hook).editOriginal(
            "Uploaded attachment:\n- `test.txt`: https://pastes.dev/abc\n\nThe public response could not be posted: boom"
        );
        verify(editAction).queue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void failureResponseEditsOriginalPlaceholder() {
        InteractionHook hook = mock(InteractionHook.class);
        WebhookMessageEditAction<Message> editAction = mock(WebhookMessageEditAction.class);

        when(hook.editOriginal(anyString())).thenReturn(editAction);

        PasteDiscordListener.sendFailureResponse(hook, "Upload failed: boom");

        verify(hook).editOriginal("Upload failed: boom");
        verify(editAction).queue();
        verify(hook, never()).sendMessage(anyString());
    }

    private static PasteDiscordListener listener(ResponseVisibility responseVisibility) {
        AppConfig config = new AppConfig(
            "token",
            java.util.OptionalLong.empty(),
            new PasteServiceConfig(PasteProvider.LUCKO, URI.create("https://api.pastes.dev/"), URI.create("https://pastes.dev/"), "agent"),
            responseVisibility,
            1_048_576,
            Duration.ofSeconds(20)
        );
        return new PasteDiscordListener(config, upload -> { throw new AssertionError("paste upload should not be called"); }, null);
    }
}
