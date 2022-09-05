package hyphenated.commands;

import hyphenated.Draft;
import hyphenated.GSheets;
import hyphenated.util.MySorensenDice;
import hyphenated.Rotobot;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

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

        if (draft == null) {
            return "I don't know of a draft happening in this channel";
        }
        try {
            // update the draft from the sheet to make sure it's all fresh
            draft = GSheets.readFromSheet(draft.sheetId);
            Rotobot.drafts.put(channelId, draft);
        } catch (Exception e) {
            return "Unexpected error while refreshing the sheet before your pick";
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
            SortedMap<String, String> cards;

            // try to find cards where the query string is a prefix of their name.
            // if we can't find any, then start chopping letters off the end of the query until we
            // get to a query that gives back some cards.
            int endIdx = lowerValue.length() + 1; // +1 immediately gets minused so we start with the full thing
            String prefix;
            do {
                --endIdx;
                prefix = lowerValue.substring(0, endIdx);
                cards = draft.legalCardsTrie.prefixMap(prefix);
            } while (cards.size() == 0);

            String trimmedChars = lowerValue.substring(endIdx);

            List<Command.Choice> options = new ArrayList<>(25);

            if(trimmedChars.length() == 0) {
                // we are in the "normal" case, the full query they gave is a prefix of some cards.
                int suggestionCount = 0;
                for (String name : cards.values()) {
                    if (!draft.pickedCards.contains(Rotobot.simplifyName(name))) {
                        options.add(new Command.Choice(name, name));
                        ++suggestionCount;
                        if (suggestionCount >= 25) {
                            break;
                        }
                    }
                }
            } else {
                // they typed something that doesn't match any cards
                MySorensenDice sd = new MySorensenDice(2);
                Map<String, Integer> trimmedProfile = sd.getProfile(trimmedChars);

                int capacity = 200;
                // some cards were prefixed with the first part of the query. rank them.
                PriorityQueue<Pair<Double, String>> queue = new PriorityQueue<> (capacity);
                for(String name : cards.values()) {
                    String lowerName = Rotobot.simplifyName(name);
                    if (draft.pickedCards.contains(lowerName)) {
                        continue;
                    }
                    // e.g. if the search was "lilli veil"
                    // and we're looking at the card "liliana of the veil"
                    // we trimmed the query to "lil" and now we want to compare
                    // "li veil" to "iana of the veil"
                    // right now we're using sorensen-dice on the letter bigrams
                    String nameSuffix = lowerName.substring(prefix.length());
                    Map<String, Integer> suffixProfile = sd.getProfile(nameSuffix);
                    double distance = 1 - sd.similarity(trimmedProfile, suffixProfile);
                    Pair<Double, String> pair = new ImmutablePair<>(distance, name);
                    queue.add(pair);
                }

                for(int i = 0; i < 25; ++i) {
                    Pair<Double, String> pair = queue.poll();
                    if (pair == null) {
                        break;
                    }
                    String name = pair.getValue();
                    options.add(new Command.Choice(name, name));
                }
            }
            event.replyChoices(options).queue();
        }
    }
}
