package hyphenated.commands;

import hyphenated.Draft;
import hyphenated.GSheets;
import hyphenated.Rotobot;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;


// TODO this is not done.
public class PreloadCommand extends ListenerAdapter {
    public static final String CMD = "preload";

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals(CMD)) {
            String reply = handleAndMakeReply(event);
            event.reply(reply).queue();
        }
    }

    public String handleAndMakeReply(SlashCommandInteractionEvent event) {
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
            return "Unexpected error while refreshing the sheet before your preload";
        }

        String username = event.getUser().getName();
        String usertag = event.getUser().getAsTag();
        if (!draft.players.containsKey(usertag)) {
            return username + ", you're not in this draft";
        }

        OptionMapping mapping = event.getOption("cards");

        List<String> foundCards = new ArrayList<>();
        if (mapping == null) {
            return "I couldn't get the cards parameter from discord (???)";
        } else {
            String param = mapping.getAsString();
            if (StringUtils.isBlank(param)) {
                return "You need to pick what card(s) to preload (replacing any old preloads). Put | between the names if you want more than one.";
            }
            int cardPos = 1;
            for (String card : param.split("\\s*\\|\\s*")) {
                String cardLower = Rotobot.simplifyName(card);
                String foundCard = Rotobot.cardsLowerToCaps.get(cardLower);
                if (StringUtils.isBlank(foundCard)) {
                    return "I don't recognize the card in position " + cardPos + " as a magic card in the scryfall data";
                }

                if (!draft.legalCardsTrie.containsKey(cardLower)) {
                    return "The card at position " + cardPos + " isn't legal in this draft";
                } else if (draft.pickedCards.contains(cardLower)) {
                    return "The card at position " + cardPos + " was already picked";
                }

                foundCards.add(foundCard);
            }
        }
        try {
            Rotobot.jsonDAO.preloadPicks(draft.sheetId, event.getUser().getAsTag(), foundCards);
        } catch (Exception e) {
            //todo log
            return "Error while trying to process your picks: " + e.getMessage();
        }
        return "TODO";
    }
}
