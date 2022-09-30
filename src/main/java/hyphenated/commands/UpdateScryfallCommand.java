package hyphenated.commands;

import com.google.gson.*;
import hyphenated.Config;
import hyphenated.Rotobot;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class UpdateScryfallCommand extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(UpdateScryfallCommand.class);

    public static final String CMD = "update_scryfall";

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals(CMD)) {

            User user = event.getUser();
            String tag = user.getAsTag();
            if(!tag.equals(Config.OWNER_TAG)) {
                event.reply("Only " + Config.OWNER_TAG + " can do this").queue();
                return;
            }

            event.deferReply().queue();
            try {
                long startTime = System.currentTimeMillis();

                URL scryfallEndpoint = new URL("https://api.scryfall.com/bulk-data");
                JsonObject detailsJson = JsonParser.parseString(IOUtils.toString(scryfallEndpoint, StandardCharsets.UTF_8)).getAsJsonObject();
                URL fullDataUrl = getDownloadURL(detailsJson);
                if (fullDataUrl == null) {
                    event.getHook().sendMessage("Couldn't parse the bulk-data endpoint response").queue();
                    return;
                }
                FileUtils.copyURLToFile(fullDataUrl, new File(Config.SCRYFALL_DEFAULT_CARDS_PATH), 5000, 5000);
                Rotobot.getLegalCardsAndUpdateCapsMap(null);
                long elapsedSecs = (System.currentTimeMillis() - startTime)/1000;
                String msg = String.format("Done updating scryfall data in %d seconds", elapsedSecs);
                event.getHook().sendMessage(msg).queue();


            } catch (Exception e) {
                event.getHook().sendMessage("Exception while updating scryfall cards").queue();
                logger.error("Exception while updating scryfall cards", e);
            }
        }
    }

    private URL getDownloadURL(JsonObject detailsJson) throws MalformedURLException {
        JsonArray data = detailsJson.getAsJsonArray("data");
        for(int i = 0; i < data.size(); ++i) {
            JsonObject obj = data.get(i).getAsJsonObject();
            String type = obj.get("type").getAsString();
            if ("default_cards".equals(type)) {
                String downloadUrl = obj.get("download_uri").getAsString();
                return new URL(downloadUrl);
            }
        }
        return null;
    }
}
