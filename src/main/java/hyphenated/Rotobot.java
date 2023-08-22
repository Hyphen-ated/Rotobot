package hyphenated;

import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import hyphenated.commands.EndDraftCommand;
import hyphenated.commands.StartDraftCommand;
import hyphenated.commands.PickCommand;
import hyphenated.commands.UpdateScryfallCommand;
import hyphenated.json.ActiveDraft;
import hyphenated.messagelisteners.MoxfieldListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.gcardone.junidecode.Junidecode;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;


public class Rotobot {
    private static final Logger logger = LoggerFactory.getLogger(Rotobot.class);
    public static ConcurrentHashMap<String, String> cardsLowerToCaps = new ConcurrentHashMap<>();
    // key: channel id
    public static final ConcurrentHashMap<String, Draft> drafts = new ConcurrentHashMap<>();

    public static JsonDAO jsonDAO = new JsonDAO();

    public static void main(String[] args) throws Exception {
        getLegalCardsAndUpdateCapsMap(null);
        readAllDraftSheets();

        JDA api = JDABuilder.createDefault(Config.DISCORD_BOT_TOKEN, GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                .addEventListeners(
                        new StartDraftCommand(),
                        new EndDraftCommand(),
                        new PickCommand(),
                        new UpdateScryfallCommand(),
                        new MoxfieldListener())
                .build();

        api.updateCommands().addCommands(
                Commands.slash(StartDraftCommand.STARTDRAFT, "Start a new draft")
                        .addOption(OptionType.USER, "p1", "Player 1", true)
                        .addOption(OptionType.USER, "p2", "Player 2", true)
                        .addOption(OptionType.USER, "p3", "Player 3", true)
                        .addOption(OptionType.USER, "p4", "Player 4", true)
                        .addOption(OptionType.USER, "p5", "Player 5", true)
                        .addOption(OptionType.USER, "p6", "Player 6", true)
                        .addOption(OptionType.USER, "p7", "Player 7", true)
                        .addOption(OptionType.USER, "p8", "Player 8", true)
                        .addOption(OptionType.STRING, "name", "Name of the draft, e.g. dis99", true)
                        .addOption(OptionType.STRING, "format", "What cards are legal? e.g. vintage (editable in sheet later)"),

                Commands.slash(EndDraftCommand.CMD, "End the draft in the current channel"),

                Commands.slash(PickCommand.CMD, "Picks a card in the current draft")
                        .addOption(OptionType.STRING, "card", "The card to pick", true, true),

//                Commands.slash(PreloadCommand.CMD, "Preload one or more cards")
//                        .addOption(OptionType.STRING, "cards", "Cards to preload, separated by | if more than one. Replaces any previous preloads.", true, false),

                Commands.slash(UpdateScryfallCommand.CMD, "Download the latest scryfall data")
                        .setDefaultPermissions(DefaultMemberPermissions.DISABLED)
        ).queue();

    }

    // for these kinds of cards we want to ignore the part after the // in the name.
    // for other kinds, e.g. "Fire // Ice", we want to keep the whole thing as is
    public static final Set<String> layoutsToIgnoreBack = Sets.newHashSet(
            "transform",
            "flip",
            "adventure",
            "modal_dfc",
            "reversible_card");

    public synchronized static List<Card> getLegalCardsAndUpdateCapsMap(@Nullable String format) throws Exception {
        Gson gson = new Gson();
        JsonReader reader = new JsonReader(
                Files.newBufferedReader(Path.of(Config.SCRYFALL_DEFAULT_CARDS_PATH),
                                        StandardCharsets.UTF_8));

        ConcurrentHashMap<String, String> newCapsMap = new ConcurrentHashMap<>();

        reader.beginArray();
        List<Card> output = new ArrayList<>(30000);
        while (reader.hasNext()) {
            JsonObject cardJson = gson.fromJson(reader, JsonObject.class);
            String cardName = cardJson.get("name").getAsString();
            cardName = Junidecode.unidecode(cardName);

            String layout = cardJson.get("layout").getAsString();
            if (layoutsToIgnoreBack.contains(layout)) {
                cardName = extractFirstCardName(cardName);
            }

            if ("meld".equals(layout) || "token".equals(layout)) {
                // tokens are not legal. if there's a token with the same name as a real card and it's earlier in the
                // sf data, then it would be a problem without checking it here.
                continue;
            }

            String lowerName = Rotobot.simplifyName(cardName);
            if(newCapsMap.containsKey(lowerName)) {
                continue;
            }
            newCapsMap.put(lowerName, cardName);

            if(format != null) {
                String legality = cardJson.get("legalities").getAsJsonObject()
                        .get(format).getAsString();
                if ("legal".equals(legality) || "restricted".equals(legality)) {
                    JsonElement typeElem = cardJson.get("type_line");

                    JsonElement colorArray;
                    if (typeElem != null && typeElem.getAsString().contains("Land")) {
                        colorArray = cardJson.get("color_identity");
                    } else {
                        colorArray = cardJson.get("colors");
                    }

                    String colorCategory;
                    if (colorArray == null) {
                        colorCategory = "";
                    } else {
                        JsonArray arr = colorArray.getAsJsonArray();
                        if (arr.isEmpty()) {
                            colorCategory = "";
                        } else {
                            StringBuilder colors = new StringBuilder();
                            for (JsonElement e : arr) {
                                colors.append(e.getAsString());
                            }
                            colorCategory = colors.toString();
                        }
                    }
                    output.add(new Card(cardName, colorCategory));
                }
            }
        }
        reader.endArray();
        reader.close();
        Collections.sort(output);
        // newCapsMap contains lowercase -> properly cased mapping for ALL cards
        cardsLowerToCaps = newCapsMap;
        // return value contains only cards legal in this format
        return output;
    }

    private static String extractFirstCardName(String cardName) {
        int idx = cardName.indexOf(" // ");
        if (idx > -1) {
            return cardName.substring(0, idx);
        }
        throw new RuntimeException("Expected to find // in card: " + cardName);
    }

    private static void readAllDraftSheets() throws Exception{
        for(ActiveDraft activeDraft : jsonDAO.getActiveDrafts()) {
            Draft draft = GSheets.readFromSheet(activeDraft.sheetId);
            drafts.put(draft.channelId, draft);
        }
    }

    private static Pattern nonAlphanumeric = Pattern.compile("[^a-z0-9 ]");
    public static String simplifyName(@NotNull String name) {
        return nonAlphanumeric.matcher(name.toLowerCase(Locale.ROOT))
                              .replaceAll("");
    }

    public static String formatSheetUrl(String sheetId) {
        return "https://docs.google.com/spreadsheets/d/" + sheetId;
    }

    public static boolean userIsAdmin(SlashCommandInteractionEvent event) {
        if (StringUtils.isBlank(Config.ADMIN_ROLE_ID)) {
            return false;
        }
        User user = event.getUser();
        Guild guild = event.getGuild();
        if (guild == null) {
            return false;
        }
        Member member = guild.getMemberById(user.getId());
        if (member == null) {
            return false;
        }
        List<Role> roles = member.getRoles();
        return roles.stream().anyMatch(role -> role.getId().equals(Config.ADMIN_ROLE_ID));
    }

}
