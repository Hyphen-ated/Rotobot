package hyphenated.commands;

import hyphenated.*;
import hyphenated.json.DraftJson;
import hyphenated.util.MySorensenDice;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class PickCommand extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(PickCommand.class);

    public static final String CMD = "pick";
    private static final Collection<Message.MentionType> userMentions =  Collections.singleton(Message.MentionType.USER);

    // 8 drafters times 46 rounds is 368 picks in a standard draft
    private static final int draftSize = 368;
    // the seat indexes 1 throuh 8, each 46 times, defining the pick order for the draft
    private static final int[] normalPickOrder;
    private static final int[] nycPickOrder;

    static {
        // normal drafts are snake pick order. seat 1 to 8, then 8 to 1, repeat until the end
        normalPickOrder = new int[draftSize];
        for (int round = 0; round < 23; ++round) {
            // first half of each "double round" of 16 picks goes from 1 to 8
            for (int i = 0; i < 8; ++i) {
                normalPickOrder[round*16 + i] = i + 1;
            }
            // second half goes from 8 to 1
            for (int i = 0; i < 8; ++i) {
                normalPickOrder[round*16 + 8 + i] = 8 - i;
            }
        }

        // nyc style is seat 8 to 1 for ONLY the first round of picks, then 1 to 8 for the whole rest of the draftd
        nycPickOrder = new int[draftSize];
        // first round
        for (int i = 0; i < 8; ++i) {
            nycPickOrder[i] = 8 - i;
        }
        // all other rounds
        for (int round = 1; round < 46; ++round) {
            for (int i = 0; i < 8; ++i) {
                nycPickOrder[round*8 + i] = i + 1;
            }
        }
        int x=0;
    }
    @Override
    public synchronized void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals(CMD)) {
            InteractionHook hook = event.getHook();
            event.deferReply(true).queue();

            // send a followup message later because editing the initial deferral reply doesn't allow mentions
            String reply = handleAndMakeReply(event);
            MessageCreateData mcd = new MessageCreateBuilder()
                    .setContent(reply)
                    .setAllowedMentions(userMentions)
                    .build();
            hook.sendMessage("Done").queue();
            hook.sendMessage(mcd).queue();
        }
    }

    private @Nullable String getSheetPick(List<List<String>> picks, int seat, int row) {
        List<String> playerPicks = picks.get(seat-1);
        if (playerPicks.size() <= row) {
            return null;
        }
        return playerPicks.get(row);
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
            String msg = "Unexpected error while refreshing the sheet before your pick";
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
        Draft.Player pickingPlayer = draft.players.get(usertag);

        int[] pickOrder;
        if (draft.snakeStyle == SnakeStyle.NORMAL) {
            pickOrder = normalPickOrder;
        } else if (draft.snakeStyle == SnakeStyle.NYC) {
            pickOrder = nycPickOrder;
        } else {
            return "Unrecognized snake style: " + draft.snakeStyle.name();
        }
        StringBuilder message = new StringBuilder();
        int firstChangedRow = -1;
        int firstChangedSeat = -1;
        int lastChangedPreloadRow = -1;
        // first, try to find where this pick itself goes
        int idx = 0;
        for (idx = 0; idx < draftSize; ++idx) {
            int seat = pickOrder[idx];
            int row = idx / 8; // integer division
            String sheetPick = getSheetPick(draft.picks, seat, row);
            if(StringUtils.isBlank(sheetPick)) {
                // we found the first open cell in the sheet
                if (pickingPlayer.seat != seat) {
                    return username + ", it's not your turn. Seat " + seat + " at row " + (row+1) + " needs to go";
                }
                firstChangedRow = row;
                firstChangedSeat = seat;
                draft.pickedCards.add(cardLower);
                draft.picks.get(seat-1).add(card);
                message.append(username + " picks [" + card + "](" + scryfallUrl(card) + ")");

                break;
            }
        }
        int currentPickIdx = idx;
        // second, check ALL subsequent cells. if any of them are nonempty, something is weird and we dont do preloads
        boolean laterCellsArePopulated = false;
        for (int i = currentPickIdx + 1; i < draftSize; ++i) {
            int seat = pickOrder[i];
            int row = i / 8; // integer division
            String sheetPick = getSheetPick(draft.picks, seat, row);
            if (!StringUtils.isBlank(sheetPick)) {
                laterCellsArePopulated = true;
                break;
            }
        }

        int preloadsProcessed = 0;
        @Nullable String nextPlayerId = null;
        if (laterCellsArePopulated) {
            message.append("\nThere are unexpected picks already placed AFTER this. Double-check the sheet.");
        } else {
            // third, check for preloads following the pick and process them
            for (int i = currentPickIdx + 1; i < draftSize; ++i) {
                int seat = pickOrder[i];
                int row = i / 8; // integer division
                String preloaderTag = draft.players.get(seat - 1);
                String preloaderId = draft.players.getValue(seat - 1).discordId;
                @Nullable List<String> playerPreloads = draftJson.preloads.get(preloaderTag);
                if (playerPreloads == null || playerPreloads.isEmpty()) {
                    // they have no preload, so we're done with preloads
                    // figure out who to ping, if anyone
                    int prevSeat = pickOrder[i-1];
                    if (prevSeat != seat) {
                        nextPlayerId = preloaderId;
                    }
                    break;
                } else {
                    String preloadCardGiven = playerPreloads.remove(0);
                    String preloadLower = Rotobot.simplifyName(preloadCardGiven);
                    String preloadCard = Rotobot.cardsLowerToCaps.get(preloadLower);
                    if (StringUtils.isBlank(preloadCard)) {
                        nextPlayerId = null;
                        message.append("\n<@" + preloaderId + "> preloaded \"" + preloadLower + "\" but now I don't recognize it as a magic card in the scryfall data (???)");
                        break;
                    }
                    if (!draft.legalCardsTrie.containsKey(preloadLower)) {
                        nextPlayerId = null;
                        message.append("\n<@" + preloaderId + "> preloaded \"" + preloadCard + "\" but it isn't legal in this draft (???)");
                        break;
                    }
                    if (draft.pickedCards.contains(preloadLower)) {
                        nextPlayerId = null;
                        message.append("\n<@" + preloaderId + "> preloaded \"" + preloadCard + "\" but it's been taken");
                        playerPreloads.clear(); //all their preloads are now invalid if they got sniped
                        break;
                    }
                    draft.pickedCards.add(preloadLower);
                    draft.picks.get(seat-1).add(preloadCard);
                    ++preloadsProcessed;
                    lastChangedPreloadRow = row;
                    message.append("\n" + preloaderTag + " preloaded [" + preloadCard + "](" + scryfallUrl(preloadCard) + ")");
                }
            }
        }

        try {
            if (lastChangedPreloadRow == -1) {
                // no preloads were processed. so just send one pick
                String cellCoord = cellCoord(firstChangedSeat, firstChangedRow);
                int updatedCells = GSheets.writePick(draft.sheetId, cellCoord, card);
                if (updatedCells != 1) {
                    return "The google sheets api said " + updatedCells + " cells were updated. It should be 1. There's a problem";
                }
            } else {
                List<List<Object>> cardArrays = new ArrayList<>();
                for (int i = firstChangedRow; i <= lastChangedPreloadRow; ++i) {
                    List<Object> cardRow = new ArrayList<>();
                    for (int j = 0; j < 8; ++j) {
                        String cardToSend = getSheetPick(draft.picks, j + 1, i);
                        if (cardToSend == null) {
                            cardRow.add(com.google.api.client.util.Data.NULL_STRING); // ridiculous nonsense for google sheets java api
                        } else {
                            cardRow.add(cardToSend);
                        }
                    }
                    cardArrays.add(cardRow);
                }

                // the 0th row is at "row 2" in the actual spreadsheet
                int firstChangedSheetRow = firstChangedRow + 2;
                int lastChangedSheetRow = lastChangedPreloadRow + 2;
                String cellsRange = "C" + firstChangedSheetRow + ":" + "J" + lastChangedSheetRow;

                int updatedCells = GSheets.writePickRows(draft.sheetId, cellsRange, cardArrays);
                int expectedUpdate = preloadsProcessed + 1;
                if (updatedCells != expectedUpdate) {
                    return "The google sheets api said " + updatedCells + " cells were updated. It should be " + expectedUpdate + ". There's a problem";
                }
            }
        } catch (Exception e) {
            String msg = "unexpected error while trying to write a pick to the sheet";
            logger.error(msg, e);
            return msg;
        }

        String maybeError = "";
        try {
            Rotobot.jsonDAO.setDraft(draftJson);
        } catch (Exception e) {
            String msg = " -- Error while trying to write processed preloads to disk. but the sheet was edited. ping hyphenated";
            logger.error(msg, e);
            maybeError = msg;
        }

        String sheetLink = "[sheet](<" + Rotobot.formatSheetUrl(draft.sheetId) + ">)";

        String suffix = "";
        if(!StringUtils.isBlank(nextPlayerId)) {
            suffix = " (next up: <@" + nextPlayerId + ">)";
        }
        if (!Config.PROD) {
            suffix += "(test: you are <@" + pickingPlayer.discordId + ">)";
        }

        return message + suffix + " (" + sheetLink + ")" + maybeError;
    }

    private static String cellCoord(int seat, int row) {
        char column = (char)((int)'B' + seat);
        int sheetRow = row + 2; // the 0th row is at "row 2" in the actual spreadsheet
        return String.format("%c%d", column, sheetRow);
    }

    public static String scryfallUrl(String cardname) {
        return "<https://scryfall.com/search?q=!\""
                + URLEncoder.encode(cardname, StandardCharsets.UTF_8)
                + "\">";
    }

    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        if (event.getName().equals(CMD) && event.getFocusedOption().getName().equals("card")) {
            autocompleteCard(event);
        }
    }

    public static void autocompleteCard(CommandAutoCompleteInteractionEvent event) {
        String channelId = event.getMessageChannel().getId();
        Draft draft = Rotobot.drafts.get(channelId);
        if (draft == null) {
            event.replyChoices(new Command.Choice("There's no draft in this channel", "There's no draft in this channel")).queue();
            return;
        }

        String value = event.getFocusedOption().getValue();
        if (StringUtils.isBlank(value)) {
            event.replyChoices();
            return;
        }
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
