package hyphenated.commands;

import hyphenated.Draft;
import hyphenated.GSheets;
import hyphenated.JsonDAO;
import hyphenated.Rotobot;
import hyphenated.json.DraftJson;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class PreloadCommand extends ListenerAdapter {
    public static final String CMD = "preload";
    public static final String LIST_SUBCOMMAND = "list";
    public static final String NONE_SUBCOMMAND = "none";
    private static final Logger logger = LoggerFactory.getLogger(PreloadCommand.class);

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals(CMD)) {
            event.deferReply(true).queue();
            String reply = handleAndMakeReply(event);
            event.getHook().sendMessage(reply).queue();
        }
    }

    public synchronized String handleAndMakeReply(SlashCommandInteractionEvent event) {
        String channelId = event.getMessageChannel().getId();
        Draft draft = Rotobot.drafts.get(channelId);
        if (draft == null) {
            return "I don't know of a draft happening in this channel";
        }
        try {
            // update the draft from the sheet to make sure it's all fresh
            draft = GSheets.readFromSheet(draft.sheetId);
            Rotobot.drafts.put(channelId, draft);
        } catch (Exception e) {
            String msg = "Unexpected error while refreshing the sheet before your preload";
            logger.error(msg, e);
            return msg;
        }

        DraftJson draftJson;
        try {
            draftJson = Rotobot.jsonDAO.getDraftJsonBySheetId(draft.sheetId);
        } catch (Exception e) {
            String msg = "Error while trying to load draft info from local disk";
            logger.error(msg, e);
            return msg;
        }

        String username = event.getUser().getName();
        String usertag = event.getUser().getAsTag();
        OptionMapping cardMapping = event.getOption("card");
        String firstCard = cardMapping.getAsString();
        if (firstCard.equalsIgnoreCase(LIST_SUBCOMMAND)) {
            if (draftJson.preloads != null) {
                List<String> playerPreloads = draftJson.preloads.get(usertag);
                if (playerPreloads != null && !playerPreloads.isEmpty()) {
                    String preloadNames = String.join("\n* ", draftJson.preloads.get(usertag));
                    return "Your preloads are: \n* " + preloadNames;
                }
            }
            return "You have no preloads";
        }

        if (!draft.players.containsKey(usertag)) {
            return username + ", you're not in this draft";
        }
        // this stays empty if we're undoing
        List<String> formattedPreloadNames = new ArrayList<>();

        List<String> preloadCards = new ArrayList<>();

        if (! firstCard.equalsIgnoreCase(NONE_SUBCOMMAND)) {
            addCardToPreloads(cardMapping, preloadCards);

            for (int i = 2; i <= 6; ++i) {
                cardMapping = event.getOption("card" + i);
                addCardToPreloads(cardMapping, preloadCards);
            }

            for (String card : preloadCards) {
                String cardLower = Rotobot.simplifyName(card);
                String foundCard = Rotobot.cardsLowerToCaps.get(cardLower);
                if (StringUtils.isBlank(foundCard)) {
                    return "I don't recognize '" + card + "' as a magic card in the scryfall data";
                }

                if (!draft.legalCardsTrie.containsKey(cardLower)) {
                    return card + " isn't legal in this draft";
                } else if (draft.pickedCards.contains(cardLower)) {
                    return card + " was already picked";
                }
                formattedPreloadNames.add(foundCard);
            }
        }

        try {
            String userTag = event.getUser().getAsTag();
            Rotobot.jsonDAO.setPlayerPreload(draft.sheetId, userTag, formattedPreloadNames);
            if (draftJson.preloads == null) {
                draftJson.preloads = new HashMap<>();
            }
            draftJson.preloads.put(userTag, formattedPreloadNames);
        } catch (Exception e) {
            logger.error("Couldn't write preload json for " + event.getUser().getName(), e);
            return "Error while trying to process your picks: " + e.getMessage();
        }
        if (formattedPreloadNames.isEmpty()) {
            return "Erased your previous preloads";
        } else {
            String preloadNames = String.join("\n* ", formattedPreloadNames);
            return "Your preloads are: \n* " + preloadNames;
        }
    }

    private static void addCardToPreloads(@Nullable OptionMapping cardMapping, List<String> preloadCards) {
        if (cardMapping != null) {
            String card = cardMapping.getAsString();
            if (!StringUtils.isBlank(card)) {
                preloadCards.add(card);
            }
        }
    }

    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        if (event.getName().equals(CMD) && event.getFocusedOption().getName().startsWith("card")) {
            String value = event.getFocusedOption().getValue();
            if (LIST_SUBCOMMAND.equalsIgnoreCase(value)) {
                event.replyChoice(LIST_SUBCOMMAND, LIST_SUBCOMMAND).queue();
                return;
            } else if (NONE_SUBCOMMAND.equalsIgnoreCase(value)) {
                event.replyChoice(NONE_SUBCOMMAND, NONE_SUBCOMMAND).queue();
                return;
            }
            PickCommand.autocompleteCard(event);
        }
    }
}
