package hyphenated;

import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import hyphenated.commands.PickCommand;
import hyphenated.commands.UpdateScryfallCommand;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.gcardone.junidecode.Junidecode;
import org.apache.commons.collections4.Trie;
import org.apache.commons.collections4.trie.PatriciaTrie;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;


public class Rotobot {
    public static final Trie<String, String> vintageCardTrie = new PatriciaTrie<>();

    public static void main(String[] args) throws Exception {
        getLegalCardsFromScryfallData("vintage");

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

    public static List<String> getLegalCardsFromScryfallData(String format) throws Exception {
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
            String legality = cardJson.get("legalities").getAsJsonObject()
                                      .get(format).getAsString();
            if (!"legal".equals(legality) && !"restricted".equals(legality)) {
                continue;
            }

            String lowerName = cardName.toLowerCase(Locale.ROOT);
            if(vintageCardTrie.containsKey(lowerName)) {
                continue;
            }
            vintageCardTrie.put(lowerName, cardName);
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

}
