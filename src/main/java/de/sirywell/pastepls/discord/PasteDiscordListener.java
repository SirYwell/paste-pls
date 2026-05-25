package de.sirywell.pastepls.discord;

import de.sirywell.pastepls.attachment.AttachmentSelection;
import de.sirywell.pastepls.attachment.TextAttachmentPolicy;
import de.sirywell.pastepls.config.AppConfig;
import de.sirywell.pastepls.paste.PasteResult;
import de.sirywell.pastepls.paste.PasteService;
import de.sirywell.pastepls.paste.PasteUpload;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PasteDiscordListener extends ListenerAdapter {
    static final String COMMAND_NAME = "Upload to paste";

    private static final Logger LOGGER = LoggerFactory.getLogger(PasteDiscordListener.class);

    private final AppConfig config;
    private final PasteService pasteService;
    private final AttachmentDownloader attachmentDownloader;
    private final TextAttachmentPolicy textAttachmentPolicy;
    private final AtomicBoolean commandsRegistered = new AtomicBoolean();

    public PasteDiscordListener(AppConfig config, PasteService pasteService, AttachmentDownloader attachmentDownloader) {
        this.config = config;
        this.pasteService = pasteService;
        this.attachmentDownloader = attachmentDownloader;
        this.textAttachmentPolicy = new TextAttachmentPolicy();
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        if (!commandsRegistered.compareAndSet(false, true)) {
            return;
        }

        if (config.developmentGuildId().isPresent()) {
            Guild guild = event.getJDA().getGuildById(config.developmentGuildId().getAsLong());
            if (guild == null) {
                throw new IllegalStateException("Configured guild was not found: " + config.developmentGuildId().getAsLong());
            }
            guild.updateCommands().addCommands(createCommand()).queue(
                ignored -> LOGGER.info("Registered guild command '{}' in {}", COMMAND_NAME, guild.getId()),
                error -> LOGGER.error("Failed to register guild commands", error)
            );
            return;
        }

        event.getJDA().updateCommands().addCommands(createCommand()).queue(
            ignored -> LOGGER.info("Registered global command '{}'", COMMAND_NAME),
            error -> LOGGER.error("Failed to register global commands", error)
        );
    }

    @Override
    public void onMessageContextInteraction(@NotNull MessageContextInteractionEvent event) {
        if (!COMMAND_NAME.equals(event.getName())) {
            return;
        }

        List<EligibleAttachment> eligibleAttachments = event.getTarget().getAttachments().stream()
            .map(this::classify)
            .flatMap(Optional::stream)
            .toList();
        if (eligibleAttachments.isEmpty()) {
            event.reply("No supported text attachments were found on the selected message.")
                .setEphemeral(config.responseVisibility().ephemeral())
                .queue();
            return;
        }

        event.deferReply(config.responseVisibility().ephemeral()).queue(
            hook -> uploadAll(hook, eligibleAttachments),
            error -> LOGGER.error("Failed to defer paste response", error)
        );
    }

    private Optional<EligibleAttachment> classify(Message.Attachment attachment) {
        return textAttachmentPolicy.classify(attachment.getFileName(), attachment.getContentType(), attachment.getSize())
            .filter(selection -> attachment.getSize() <= config.attachmentMaxBytes())
            .map(selection -> new EligibleAttachment(attachment, selection));
    }

    private void uploadAll(InteractionHook hook, List<EligibleAttachment> eligibleAttachments) {
        List<CompletableFuture<String>> uploads = eligibleAttachments.stream()
            .map(this::uploadSingle)
            .toList();

        CompletableFuture.allOf(uploads.toArray(CompletableFuture[]::new))
            .thenApply(ignored -> uploads.stream().map(CompletableFuture::join).toList())
            .whenComplete((lines, error) -> {
                if (error != null) {
                    Throwable cause = rootCause(error);
                    LOGGER.warn("Failed to upload attachment", cause);
                    hook.editOriginal("Upload failed: " + cause.getMessage()).queue();
                    return;
                }

                hook.editOriginal(formatSuccess(lines, eligibleAttachments.size())).queue();
            });
    }

    private CompletableFuture<String> uploadSingle(EligibleAttachment eligibleAttachment) {
        Message.Attachment attachment = eligibleAttachment.attachment();
        AttachmentSelection selection = eligibleAttachment.selection();
        return attachmentDownloader.download(URI.create(attachment.getUrl()), config.attachmentMaxBytes())
            .thenCompose(bytes -> pasteService.upload(new PasteUpload(attachment.getFileName(), selection.language(), bytes)))
            .thenApply(result -> formatLine(attachment.getFileName(), result));
    }

    private static String formatSuccess(List<String> lines, int uploadedCount) {
        if (uploadedCount == 1) {
            return "Uploaded attachment:\n" + lines.getFirst();
        }
        return "Uploaded " + uploadedCount + " attachments:\n" + lines.stream().collect(Collectors.joining("\n"));
    }

    private static String formatLine(String fileName, PasteResult result) {
        return "- `%s`: %s".formatted(fileName, result.shareUrl());
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable instanceof CompletionException ? throwable.getCause() : throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    static CommandData createCommand() {
        return Commands.message(COMMAND_NAME)
            .setIntegrationTypes(IntegrationType.GUILD_INSTALL, IntegrationType.USER_INSTALL)
            .setContexts(
                InteractionContextType.GUILD,
                InteractionContextType.BOT_DM,
                InteractionContextType.PRIVATE_CHANNEL
            );
    }

    private record EligibleAttachment(Message.Attachment attachment, AttachmentSelection selection) {
    }
}
