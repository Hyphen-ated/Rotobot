package hyphenated.commands;

import hyphenated.Draft;
import hyphenated.GSheets;
import hyphenated.util.MySorensenDice;
import hyphenated.Rotobot;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class PickCommand extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(PickCommand.class);

    public static final String CMD = "pick";
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals(CMD)) {
            String reply = handleAndMakeReply(event);
            MessageCreateData mcd = new MessageCreateBuilder()
                    .setContent(reply)
                    .setAllowedMentions(Collections.singleton(Message.MentionType.USER))
                    .build();
            event.reply(mcd).queue();
        }
    }

    public String handleAndMakeReply(SlashCommandInteractionEvent event) {
        String channelId = event.getMessageChannel().getId();
        Draft draft = Rotobot.drafts.get(channelId);
        String nextPlayerId = "";

        if (draft == null) {
            return "I don't know of a draft happening in this channel";
        }
        try {
            // update the draft from the sheet to make sure it's all fresh
            draft = GSheets.readFromSheet(draft.sheetId);
            Rotobot.drafts.put(channelId, draft);
        } catch (Exception e) {
            String msg = "Unexpected error while refreshing the sheet before your pick";
            logger.error(msg, e);
            return msg;
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
        if (!draft.players.containsKey(usertag)) {
            return username + ", you're not in this draft";
        }
        try {
            Draft.Player player = draft.players.get(usertag);

            char column = (char)((int)'B' + player.seat);
            int row = 2 + player.pickList.size();
            String cellCoord = String.format("%c%d", column, row);

            int updatedCells = GSheets.writePick(draft.sheetId, cellCoord, card);
            if (updatedCells != 1) {
                return "The google sheets api said " + updatedCells + " cells were updated. It should be 1. There's a problem";
            }

            int nextSeat;
            // if we had an even number of picks already, we're passing right
            if(player.pickList.size() % 2 == 0) {
                nextSeat = player.seat + 1;
            } else {
                nextSeat = player.seat - 1;
            }

            // people at the edges dont notify when they make the first of their doublepicks
            if(nextSeat >= 1 && nextSeat <= 8) {
                nextPlayerId = draft.players.getValue(nextSeat-1).discordId;
            }

        } catch (Exception e) {
            String msg = "unexpected error while trying to write a pick to the sheet";
            logger.error(msg, e);
            return msg;
        }

        String sheetLink = "[sheet](<https://docs.google.com/spreadsheets/d/" + draft.sheetId + ">)";

        String suffix = "";
        if(!StringUtils.isBlank(nextPlayerId)) {
            suffix = " (next up: <@" + nextPlayerId + ">)";
        }

        String scryfallUrl = "<https://scryfall.com/search?q=!\""
                + URLEncoder.encode(card, StandardCharsets.UTF_8)
                + "\">";
        return username + " picks [" + card + "](" + scryfallUrl + ") " + suffix + " (" + sheetLink + ")";
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
                    if (StringUtils.isBlank(name)) {
                        logger.warn("Name was unexpectedly blank. given value is \"" + value + "\"");
                        continue;
                    }
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
