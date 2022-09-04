package hyphenated.commands;

import hyphenated.Config;
import hyphenated.Rotobot;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class UpdateScryfallCommand extends ListenerAdapter {
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
                JSONObject detailsJson = new JSONObject(IOUtils.toString(scryfallEndpoint, StandardCharsets.UTF_8));
                URL fullDataUrl = getDownloadURL(detailsJson);
                if (fullDataUrl == null) {
                    event.getHook().sendMessage("Couldn't parse the bulk-data endpoint response").queue();
                    return;
                }
                FileUtils.copyURLToFile(fullDataUrl, new File(Config.SCRYFALL_DEFAULT_CARDS_PATH), 5000, 5000);
                long elapsedSecs = (System.currentTimeMillis() - startTime)/1000;
                String msg = String.format("Done updating scryfall data in %d seconds", elapsedSecs);
                event.getHook().sendMessage(msg).queue();

            } catch (Exception e) {
                event.getHook().sendMessage("Exception while updating: " + e.getMessage()).queue();
                e.printStackTrace(); //todo logger
            }
        }
    }

    private URL getDownloadURL(JSONObject detailsJson) throws MalformedURLException {
        JSONArray data = detailsJson.getJSONArray("data");
        for(int i = 0; i < data.length(); ++i) {
            JSONObject obj = data.getJSONObject(i);
            String type = obj.getString("type");
            if ("default_cards".equals(type)) {
                String downloadUrl = obj.getString("download_uri");
                return new URL(downloadUrl);
            }
        }
        return null;
    }
}
