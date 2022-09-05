package hyphenated.commands;

import hyphenated.Draft;
import hyphenated.GSheets;
import hyphenated.Rotobot;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

public class PickCommand extends ListenerAdapter {
    public static final String CMD = "pick";
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

        try {
            // update the draft from the sheet to make sure it's all fresh
            draft = GSheets.readFromSheet(draft.sheetId);
            Rotobot.drafts.put(channelId, draft);
        } catch (Exception e) {
            return "Unexpected error while refreshing the sheet before your pick";
        }

        if (draft == null) {
            return "I don't know of a draft happening in this channel";
        }

        OptionMapping mapping = event.getOption("card");
        String card;
        String cardLower;
        if (mapping == null) {
            return "I couldn't get the card parameter from discord (???)";
        } else {
            String param = mapping.getAsString();
            card = Rotobot.cardsLowerToCaps.get(Rotobot.simplifyName(param));
            if (StringUtils.isBlank(card)) {
                return "I don't recognize " + param + " as a magic card in the scryfall data";
            }
            cardLower = Rotobot.simplifyName(card);
        }

        if (StringUtils.isBlank(card)) {
            return "You need to pick a card";
        } else if (!draft.legalCardsTrie.containsKey(cardLower)) {
            return card + " isn't legal in this draft";
        } else if (draft.pickedCards.contains(cardLower)) {
            return card + " was already picked";
        }
        String username = event.getUser().getName();
        String usertag = event.getUser().getAsTag();
        if (!draft.playerPickLists.containsKey(usertag)) {
            return username + ", you're not in this draft";
        }
        try {
            int playerPos = 0;
            for (String player : draft.playerPickLists.keySet()) {
                if(player.equals(usertag)) {
                    break;
                }
                ++playerPos;
            }
            char column = (char)((int)'C' + playerPos);
            int row = 2 + draft.playerPickLists.get(usertag).size();
            String cellCoord = String.format("%c%d", column, row);

            int updatedCells = GSheets.writePick(draft.sheetId, cellCoord, card);
            if (updatedCells != 1) {
                return "The google sheets api said " + updatedCells + " cells were updated. It should be 1. There's a problem";
            }

        } catch (Exception e) {
            e.printStackTrace(); // todo log
            return "unexpected error while trying to write a pick to the sheet";
        }

        return username + " picks " + card;
    }

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        if (event.getName().equals(CMD) && event.getFocusedOption().getName().equals("card")) {
            String channelId = event.getMessageChannel().getId();
            Draft draft = Rotobot.drafts.get(channelId);
            if (draft == null) {
                event.replyChoices(Collections.emptyList()).queue();
                return;
            }

            String value = event.getFocusedOption().getValue();
            String lowerValue = Rotobot.simplifyName(value);
            SortedMap<String, String> cards = draft.legalCardsTrie.prefixMap(lowerValue);
            List<Command.Choice> options = new ArrayList<>(25);
            int suggestionCount = 0;
            for(String name : cards.values()) {
                if(!draft.pickedCards.contains(Rotobot.simplifyName(name))) {
                    options.add(new Command.Choice(name, name));
                    ++suggestionCount;
                    if (suggestionCount >= 25) {
                        break;
                    }
                }
            }
            event.replyChoices(options).queue();
        }
    }
}
