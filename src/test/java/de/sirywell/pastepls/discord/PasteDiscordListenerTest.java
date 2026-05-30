package de.sirywell.pastepls.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.sirywell.pastepls.config.AppConfig;
import de.sirywell.pastepls.config.PasteServiceConfig;
import de.sirywell.pastepls.config.PasteProvider;
import de.sirywell.pastepls.config.ResponseVisibility;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.context.MessageContextInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
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
        ReplyActionRecorder replyAction = new ReplyActionRecorder();
        MessageContextInteraction interaction = proxy(MessageContextInteraction.class, (proxy, method, args) -> switch (method.getName()) {
            case "getName" -> PasteDiscordListener.COMMAND_NAME;
            case "getTarget" -> emptyMessage();
            case "reply" -> {
                replyAction.content = (String) args[0];
                yield replyAction.proxy();
            }
            default -> defaultValue(method.getReturnType());
        });
        MessageContextInteractionEvent event = new MessageContextInteractionEvent(proxy(JDA.class, ignored()), 0L, interaction);
        PasteDiscordListener listener = listener(ResponseVisibility.PUBLIC);

        listener.onMessageContextInteraction(event);

        assertEquals("No supported text attachments were found on the selected message.", replyAction.content);
        assertEquals(Boolean.TRUE, replyAction.ephemeral.get());
        assertTrue(replyAction.queued);
    }

    @Test
    void publicSuccessResponseUsesPublicFollowupAndDeletesPlaceholder() {
        HookRecorder hook = new HookRecorder();

        PasteDiscordListener.sendSuccessResponse(hook.proxy(), ResponseVisibility.PUBLIC, "Uploaded attachment:\n- `test.txt`: https://pastes.dev/abc");

        assertEquals("Uploaded attachment:\n- `test.txt`: https://pastes.dev/abc", hook.sentMessage.get());
        assertEquals(Boolean.FALSE, hook.createEphemeral.get());
        assertTrue(hook.createQueued);
        assertTrue(hook.deleteQueued);
        assertEquals(null, hook.editedMessage.get());
    }

    @Test
    void publicSuccessResponseFallsBackToEphemeralError() {
        HookRecorder hook = new HookRecorder();
        hook.createFailure = new IllegalStateException("boom");

        PasteDiscordListener.sendSuccessResponse(hook.proxy(), ResponseVisibility.PUBLIC, "Uploaded attachment:\n- `test.txt`: https://pastes.dev/abc");

        assertEquals("Uploaded attachment:\n- `test.txt`: https://pastes.dev/abc", hook.sentMessage.get());
        assertTrue(hook.createQueued);
        assertFalse(hook.deleteQueued);
        assertEquals(
            "Uploaded attachment:\n- `test.txt`: https://pastes.dev/abc\n\nThe public response could not be posted: boom",
            hook.editedMessage.get()
        );
        assertTrue(hook.editQueued);
    }

    @Test
    void failureResponseEditsOriginalPlaceholder() {
        HookRecorder hook = new HookRecorder();

        PasteDiscordListener.sendFailureResponse(hook.proxy(), "Upload failed: boom");

        assertEquals("Upload failed: boom", hook.editedMessage.get());
        assertTrue(hook.editQueued);
        assertEquals(null, hook.sentMessage.get());
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

    private static Message emptyMessage() {
        return proxy(Message.class, (proxy, method, args) -> switch (method.getName()) {
            case "getAttachments" -> java.util.List.of();
            default -> defaultValue(method.getReturnType());
        });
    }

    private static InvocationHandler ignored() {
        return (proxy, method, args) -> defaultValue(method.getReturnType());
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler);
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == double.class) {
            return 0D;
        }
        if (returnType == float.class) {
            return 0F;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return null;
    }

    private static final class ReplyActionRecorder {
        private final AtomicReference<Boolean> ephemeral = new AtomicReference<>();
        private String content;
        private boolean queued;

        private ReplyCallbackAction proxy() {
            return proxy(ReplyCallbackAction.class, (proxy, method, args) -> switch (method.getName()) {
                case "setEphemeral" -> {
                    ephemeral.set((Boolean) args[0]);
                    yield proxy;
                }
                case "queue" -> {
                    queued = true;
                    yield null;
                }
                default -> defaultValue(method.getReturnType());
            });
        }
    }

    private static final class HookRecorder {
        private final AtomicReference<String> editedMessage = new AtomicReference<>();
        private final AtomicReference<String> sentMessage = new AtomicReference<>();
        private final AtomicReference<Boolean> createEphemeral = new AtomicReference<>();
        private boolean editQueued;
        private boolean createQueued;
        private boolean deleteQueued;
        private Throwable createFailure;

        private InteractionHook proxy() {
            return proxy(InteractionHook.class, (proxy, method, args) -> switch (method.getName()) {
                case "editOriginal" -> {
                    editedMessage.set((String) args[0]);
                    yield editAction();
                }
                case "sendMessage" -> {
                    sentMessage.set((String) args[0]);
                    yield createAction();
                }
                case "deleteOriginal" -> deleteAction();
                default -> defaultValue(method.getReturnType());
            });
        }

        @SuppressWarnings("unchecked")
        private WebhookMessageEditAction<Message> editAction() {
            return proxy(WebhookMessageEditAction.class, (proxy, method, args) -> switch (method.getName()) {
                case "queue" -> {
                    editQueued = true;
                    yield null;
                }
                default -> defaultValue(method.getReturnType());
            });
        }

        @SuppressWarnings("unchecked")
        private WebhookMessageCreateAction<Message> createAction() {
            return proxy(WebhookMessageCreateAction.class, (proxy, method, args) -> switch (method.getName()) {
                case "setEphemeral" -> {
                    createEphemeral.set((Boolean) args[0]);
                    yield proxy;
                }
                case "queue" -> {
                    createQueued = true;
                    if (args != null && args.length == 2 && createFailure != null) {
                        ((java.util.function.Consumer<? super Throwable>) args[1]).accept(createFailure);
                    } else if (args != null && args.length >= 1 && args[0] != null) {
                        ((java.util.function.Consumer<? super Message>) args[0]).accept(null);
                    }
                    yield null;
                }
                default -> defaultValue(method.getReturnType());
            });
        }

        @SuppressWarnings("unchecked")
        private RestAction<Void> deleteAction() {
            return proxy(RestAction.class, (proxy, method, args) -> switch (method.getName()) {
                case "queue" -> {
                    deleteQueued = true;
                    if (args != null && args.length >= 1 && args[0] != null) {
                        ((java.util.function.Consumer<? super Void>) args[0]).accept(null);
                    }
                    yield null;
                }
                default -> defaultValue(method.getReturnType());
            });
        }
    }
}
