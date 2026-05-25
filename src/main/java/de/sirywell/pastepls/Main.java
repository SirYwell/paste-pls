package de.sirywell.pastepls;

import de.sirywell.pastepls.config.AppConfig;
import de.sirywell.pastepls.discord.AttachmentDownloader;
import de.sirywell.pastepls.discord.PasteDiscordListener;
import de.sirywell.pastepls.paste.PasteService;
import de.sirywell.pastepls.paste.PasteServiceFactory;
import java.net.http.HttpClient;
import java.time.Duration;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.load();
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        PasteService pasteService = PasteServiceFactory.create(config, httpClient);
        AttachmentDownloader attachmentDownloader = new AttachmentDownloader(
            httpClient,
            config.httpRequestTimeout(),
            config.paste().userAgent()
        );

        JDABuilder.createDefault(config.discordToken())
            .setActivity(Activity.listening("right-clicked uploads"))
            .addEventListeners(new PasteDiscordListener(config, pasteService, attachmentDownloader))
            .build();

        LOGGER.info("paste-pls started");
    }
}
