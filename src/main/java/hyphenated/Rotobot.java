package hyphenated;

import com.google.common.collect.Sets;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import hyphenated.commands.PickCommand;
import hyphenated.commands.UpdateScryfallCommand;
import hyphenated.json.ActiveDraft;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.gcardone.junidecode.Junidecode;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;


public class Rotobot {
    private static final Logger logger = LoggerFactory.getLogger(Rotobot.class);
    public static final HashMap<String, String> cardsLowerToCaps = new HashMap<>();
    // key: channel id
    public static final ConcurrentHashMap<String, Draft> drafts = new ConcurrentHashMap<>();



    public static void main(String[] args) throws Exception {

        readAllScryfallCardNames();
        readAllDraftSheets();

        JDA api = JDABuilder.createDefault(Config.DISCORD_BOT_TOKEN)
                .addEventListeners(new PickCommand())
                .addEventListeners(new UpdateScryfallCommand())
                .build();
        api.updateCommands().addCommands(
                Commands.slash(PickCommand.CMD, "Picks a card in the current draft")
                        .addOption(OptionType.STRING, "card", "The card to pick", true, true),

                Commands.slash(UpdateScryfallCommand.CMD, "Download the latest scryfall data")
                        .setDefaultPermissions(DefaultMemberPermissions.DISABLED)
        ).queue();

    }

    // for these kinds of cards we want to ignore the part after the // in the name.
    // for other kinds, e.g. "Fire // Ice", we want to keep the whole thing as is
    static final Set<String> layoutsToIgnoreBack = Sets.newHashSet(
            "transform",
            "flip",
            "adventure",
            "modal_dfc");

    public static List<String> readAllScryfallCardNames() throws Exception {
        Gson gson = new Gson();
        JsonReader reader = new JsonReader(
                Files.newBufferedReader(Path.of(Config.SCRYFALL_DEFAULT_CARDS_PATH),
                                        StandardCharsets.UTF_8));

        reader.beginArray();

        while (reader.hasNext()) {
            JsonObject cardJson = gson.fromJson(reader, JsonObject.class);
            String cardName = cardJson.get("name").getAsString();
            cardName = Junidecode.unidecode(cardName);
            String layout = cardJson.get("layout").getAsString();
            if (layoutsToIgnoreBack.contains(layout)) {
                cardName = extractFirstCardName(cardName);
            }
//            String legality = cardJson.get("legalities").getAsJsonObject()
//                                      .get(format).getAsString();
//            if (!"legal".equals(legality) && !"restricted".equals(legality)) {
//                continue;
//            }

            String lowerName = Rotobot.simplifyName(cardName);
            if(cardsLowerToCaps.containsKey(lowerName)) {
                continue;
            }
            cardsLowerToCaps.put(lowerName, cardName);
        }
        reader.endArray();
        reader.close();

        return null;
    }

    private static String extractFirstCardName(String cardName) {
        int idx = cardName.indexOf(" // ");
        if (idx > -1) {
            return cardName.substring(0, idx);
        }
        throw new RuntimeException("Expected to find // in card: " + cardName);
    }

    private static void readAllDraftSheets() throws Exception{
        Gson gson = new Gson();
        Type type = new TypeToken<ArrayList<ActiveDraft>>(){}.getType();
        List<ActiveDraft> activeDrafts = gson.fromJson(new FileReader(Config.ACTIVE_DRAFTS_PATH, StandardCharsets.UTF_8), type);
        for(ActiveDraft activeDraft : activeDrafts) {
            Draft draft = GSheets.readFromSheet(activeDraft.sheetId);
            drafts.put(draft.channelId, draft);
        }
    }

    private static Pattern nonAlphanumeric = Pattern.compile("[^a-z0-9 ]");
    public static String simplifyName(@NotNull String name) {
        return nonAlphanumeric.matcher(name.toLowerCase(Locale.ROOT))
                              .replaceAll("");
    }
}
