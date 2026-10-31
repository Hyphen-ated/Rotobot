package hyphenated;

import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import hyphenated.commands.*;
import hyphenated.json.DraftJson;
import hyphenated.messagelisteners.MoxfieldListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.gcardone.junidecode.Junidecode;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
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

    public static DraftJsonDAO draftJsonDAO = new DraftJsonDAO();
    public static QueueJsonDAO queueJsonDAO = new QueueJsonDAO();

    public static void main(String[] args) throws Exception {
        getLegalCardsAndUpdateCapsMap(null);
        readAllDraftSheets();

        JDA api = JDABuilder.createDefault(Config.DISCORD_BOT_TOKEN, GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                .addEventListeners(
                        new EndDraftCommand(),
                        new MatchCommand(),
                        new MoxfieldListener(),
                        new PickCommand(),
                        new PreloadCommand(),
                        new PreloadsCommand(),
                        new QueueCommands(),
                        new SlashCommandLogger(),
                        new StartDraftCommand(),
                        new UpdateScryfallCommand()
                )
                // silence some warnings we don't care about for caching features we don't care about
                .disableCache(CacheFlag.VOICE_STATE, CacheFlag.EMOJI, CacheFlag.STICKER, CacheFlag.SCHEDULED_EVENTS)
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
                        .addOption(OptionType.STRING, "format", "What cards are legal? e.g. vintage (editable in sheet later)", false)
                        .addOption(OptionType.STRING, "snakestyle", "What system to use for determining pick order (use 'nyc' for the nyc group; blank is normal)", false),

                Commands.slash(EndDraftCommand.CMD, "End the draft in the current channel"),

                Commands.slash(PickCommand.CMD, "Picks a card in the current draft")
                        .addOption(OptionType.STRING, "card", "Card to pick/preload", true, true)
                        .addOption(OptionType.STRING, "card2", "Card to preload next", false, true)
                        .addOption(OptionType.STRING, "card3", "Card to preload next", false, true)
                        .addOption(OptionType.STRING, "card4", "Card to preload next", false, true)
                        .addOption(OptionType.STRING, "card5", "Card to preload next", false, true)
                        .addOption(OptionType.STRING, "card6", "Card to preload next", false, true),

                Commands.slash(PreloadCommand.CMD, "Stub for old command. Use /pick now to do preloads")
                                .addOption(OptionType.STRING, "stub", "just so you don't accidentally reveal your plans"),

                Commands.slash(PreloadsCommand.CMD, "Preloads category")
                        .addSubcommands(
                            new SubcommandData(PreloadsCommand.LIST_SUBCOMMAND, "List your current preloads"),
                            new SubcommandData(PreloadsCommand.CLEAR_SUBCOMMAND, "Clear your preload")),

                Commands.slash(UpdateScryfallCommand.CMD, "Download the latest scryfall data")
                        .setDefaultPermissions(DefaultMemberPermissions.DISABLED),
                Commands.slash(QueueCommands.QUEUE_CMD, "List the signup queue in the current channel"),
                Commands.slash(QueueCommands.IN_CMD, "Sign up for the draft queue in the current channel"),
                Commands.slash(QueueCommands.OUT_CMD, "Quit the draft queue in the current channel"),
                Commands.slash(QueueCommands.QUEUE_ADD_CMD, "Mod command to add someone else to the queue in the current channel")
                        .addOption(OptionType.USER, "user", "user to be added to the queue", true)
                        .setDefaultPermissions(DefaultMemberPermissions.DISABLED),
                Commands.slash(QueueCommands.QUEUE_REMOVE_CMD, "Mod command to remove someone else from the queue in the current channel")
                        .addOption(OptionType.USER, "user", "user to be removed from the queue", true)
                        .setDefaultPermissions(DefaultMemberPermissions.DISABLED),
                Commands.slash(QueueCommands.QUEUE_CLEAR_CMD, "Clear the queue in the current channel")
                        .setDefaultPermissions(DefaultMemberPermissions.DISABLED),

                Commands.slash(MatchCommand.CMD, "Interact with the matches tab in the sheet")
                        .addSubcommands(
                                new SubcommandData(MatchCommand.LINK_SUBCOMMAND, "Get link to the matches tab of the draft sheet"),
                                new SubcommandData(MatchCommand.PING_SUBCOMMAND, "Ping the players you still need to play"),
                                new SubcommandData(MatchCommand.RECORD_SUBCOMMAND, "Report your record"),
                                new SubcommandData(MatchCommand.STANDINGS_SUBCOMMAND, "Report the record of every player in the draft")
                        )
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

    // with our human knowledge we know we should override these colors for the "color analysis" tab,
    // according to rules that we can't easily formalize.
    public static final HashMap<String, String> manualColorCategoryByName = new HashMap<>();
    static {
        manualColorCategoryByName.put("Karakas", "");
        manualColorCategoryByName.put("Life // Death", "B");
    }

    public synchronized static List<Card> getLegalCardsAndUpdateCapsMap(@Nullable String format) throws Exception {
        Gson gson = new Gson();
        if (! Files.exists(Path.of(Config.SCRYFALL_DEFAULT_CARDS_PATH))) {
            logger.warn("There's no scryfall jsonl file so we don't have card data");
            return new ArrayList<>();
        }

        logger.info("Processing scryfall jsonl");

        ConcurrentHashMap<String, String> newCapsMap = new ConcurrentHashMap<>();
        List<Card> output = new ArrayList<>(30000);
        HashSet<String> fullNames = new HashSet<>(30000);
        try (BufferedReader fileReader = Files.newBufferedReader(Path.of(Config.SCRYFALL_DEFAULT_CARDS_PATH), StandardCharsets.UTF_8)) {
            String line;
            while ((line = fileReader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                JsonObject cardJson = gson.fromJson(line, JsonObject.class);

                Boolean reprint = cardJson.get("reprint").getAsBoolean();
                if (reprint) {
                    continue;
                }
                String cardName = cardJson.get("name").getAsString();
                cardName = Junidecode.unidecode(cardName);

                String setCode = cardJson.get("set").getAsString();

                String layout = cardJson.get("layout").getAsString();
                if (layoutsToIgnoreBack.contains(layout)) {
                    cardName = extractFirstCardName(cardName);
                }

                if (fullNames.contains(cardName)) {
                    continue;
                }

                if ("token".equals(layout)) {
                    // tokens are not legal. if there's a token with the same name as a real card and it's earlier in the
                    // sf data, then it would be a problem without checking it here.
                    continue;
                }

                if ("meld".equals(layout)) {
                    // if we are the back side of a meld card, we're not legal. but there's no clean easy check for that.
                    // we have to look at the "all_parts" and see if there's a meld_result with the same name.
                    if (meldCardIsBackSide(cardJson, cardName)) {
                        continue;
                    }
                }

                String lowerName = Rotobot.simplifyName(cardName);
                if (newCapsMap.containsKey(lowerName)) {
                    String existingName = newCapsMap.get(lowerName);
                    //logger.warn("simplified names are the same for two different names: " + existingName + " --- " + cardName);
                    // this happens for shit like "Rampant, Growth" vs "Rampant Growth"
                    // annoying for us because of the punctuation-stripped version I use for autocomplete

                    // so far, the goofy garbage version always has the extra punctuation in it
                    // so my hack for now is to keep the shorter one in the autocomplete
                    if (cardName.length() < existingName.length()) {
                        newCapsMap.put(lowerName, cardName);
                    }
                } else {
                    // normal case, first time seeing this kind of name
                    newCapsMap.put(lowerName, cardName);
                }
                fullNames.add(cardName);

                boolean legal = true;
                if (format != null) {
                    String legality = cardJson.get("legalities").getAsJsonObject()
                            .get(format).getAsString();
                    legal = ("legal".equals(legality) || "restricted".equals(legality));
                }

                if (legal) {
                    JsonElement typeElem = cardJson.get("type_line");
                    String colorCategory;
                    try {
                        if (manualColorCategoryByName.containsKey(cardName)) {
                            colorCategory = manualColorCategoryByName.get(cardName);
                        } else if (typeElem != null && typeElem.getAsString().contains("Land")) {
                            // land color identity is almost always a correct classification
                            JsonElement colorArray = cardJson.get("color_identity");
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
                        } else if (layout.equals("split") || layout.equals("adventure") || layout.equals("prepare")) {
                            // assume they realistically want to play all sides, so concat the mana costs
                            JsonArray cardFaces = cardJson.getAsJsonArray("card_faces");
                            String manaCost = cardFaces.get(0).getAsJsonObject().get("mana_cost").getAsString()
                                            + cardFaces.get(1).getAsJsonObject().get("mana_cost").getAsString();
                            colorCategory = colorCategoryFromManaCost(manaCost);
                        } else if (cardJson.has("card_faces")) {
                            // any other multiface thing, just take the front face
                            JsonArray cardFaces = cardJson.getAsJsonArray("card_faces");
                            String manaCost = cardFaces.get(0).getAsJsonObject().get("mana_cost").getAsString();
                            colorCategory = colorCategoryFromManaCost(manaCost);
                        } else {
                            // normal nonland single-face cards
                            String manaCost = cardJson.get("mana_cost").getAsString();
                            double cmc = cardJson.get("cmc").getAsDouble();
                            if (cmc >= 7 && "vintage".equals(format)) {
                                // realistically, in a VRD, almost nobody is hardcasting 7 drops. 6 drops, yeah once
                                // in a while. so assume that anything 7 or up is a cheat target
                                colorCategory = "";
                            } else {
                                colorCategory = colorCategoryFromManaCost(manaCost);
                            }
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("problem determining color for card: " + cardName, e);
                    }


                    JsonElement imageUris = cardJson.get("image_uris");
                    String imageUrl = "";
                    if (imageUris == null) {
                        JsonElement cardFaces = cardJson.get("card_faces");
                        if (cardFaces != null) {
                            JsonElement cardFace = cardFaces.getAsJsonArray().get(0);
                            imageUris = cardFace.getAsJsonObject().get("image_uris");
                        }
                    }
                    if (imageUris != null) {
                        imageUrl = imageUris.getAsJsonObject().get("normal").getAsString();
                    }
                    output.add(new Card(cardName, colorCategory, setCode, imageUrl));
                }
            }
        }
        Collections.sort(output);
        // newCapsMap contains lowercase -> properly cased mapping for ALL cards
        cardsLowerToCaps = newCapsMap;
        // return value contains only cards legal in this format
        return output;
    }


    // specifically avoid counting phyrexian or hybrid mana
    private static String colorCategoryFromManaCost(String manaCost) {
        StringBuilder builder = new StringBuilder();
        if (manaCost.contains("{W}")) {
            builder.append("W");
        }
        if (manaCost.contains("{U}")) {
            builder.append("U");
        }
        if (manaCost.contains("{B}")) {
            builder.append("B");
        }
        if (manaCost.contains("{R}")) {
            builder.append("R");
        }
        if (manaCost.contains("{G}")) {
            builder.append("G");
        }
        return builder.toString();
    }

    private static boolean meldCardIsBackSide(JsonObject cardJson, String cardName) {
        JsonArray all_parts = cardJson.getAsJsonArray("all_parts");
        if (all_parts == null) {
            throw new RuntimeException("A meld card didn't have 'all_parts' in the scryfall json");
        }
        for (JsonElement partElem : all_parts) {
            JsonObject part = partElem.getAsJsonObject();
            boolean isMeldResult = "meld_result".equals(part.get("component").getAsString());
            boolean isSameName = cardName.equals(part.get("name").getAsString());
            if (isMeldResult && isSameName) {
                return true;
            }
        }
        return false;
    }

    private static String extractFirstCardName(String cardName) {
        int idx = cardName.indexOf(" // ");
        if (idx > -1) {
            return cardName.substring(0, idx);
        }
        throw new RuntimeException("Expected to find // in card: " + cardName);
    }

    private static void readAllDraftSheets() throws Exception{
        logger.info("Reading all draft sheets");
        for(DraftJson draftJson : draftJsonDAO.getDraftJsons()) {
            Draft draft = GSheets.readFromSheet(draftJson.sheetId);
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
            logger.error("ADMIN_ROLE_ID is unset");
            return false;
        }
        Member member = event.getMember();
        if (member == null) {
            logger.warn("member is null when checking userIsAdmin");
            return false;
        }
        List<Role> roles = member.getRoles();
        return roles.stream().anyMatch(role -> role.getId().equals(Config.ADMIN_ROLE_ID));
    }

}
