package hyphenated.commands;

import hyphenated.Draft;
import hyphenated.GSheets;
import hyphenated.Matches;
import hyphenated.Rotobot;
import hyphenated.json.DraftJson;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MatchCommand extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(MatchCommand.class);

    public static final String CMD = "match";
    public static final String LINK_SUBCOMMAND = "link";
    public static final String PING_SUBCOMMAND = "ping";
    public static final String RECORD_SUBCOMMAND = "record";
    public static final String STANDINGS_SUBCOMMAND = "standings";

    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals(CMD)) {
            if (event.getSubcommandName().equals(PING_SUBCOMMAND)) {
                event.deferReply(false)
                        .setAllowedMentions(Collections.singleton(Message.MentionType.USER))
                        .queue();
            } else {
                event.deferReply(false).queue();
            }
            String reply = handleAndMakeReply(event);
            event.getHook().sendMessage(reply)
                    .setSuppressEmbeds(true).queue();
        }
    }

    public synchronized String handleAndMakeReply(SlashCommandInteractionEvent event) {
        String channelId = event.getMessageChannel().getId();
        String playerDiscordId = event.getUser().getId();
        Draft draft = Rotobot.drafts.get(channelId);
        if (draft == null) {
            return "I don't know of a draft happening in this channel";
        }

        DraftJson draftJson;
        try {
            draftJson = Rotobot.draftJsonDAO.getDraftJsonBySheetId(draft.sheetId);
        } catch (Exception e) {
            String msg = "Error while trying to load draft info from local disk";
            logger.error(msg, e);
            return msg;
        }
        if (StringUtils.isBlank(draftJson.matchesTabGid)) {
            return "Bot doesn't have the match data for this draft (too old probably)";
        }
        String matchesTabUrl = Rotobot.formatSheetUrl(draftJson.sheetId) + "?gid=" + draftJson.matchesTabGid;
        String subcommand = event.getSubcommandName();
        try {
            if (LINK_SUBCOMMAND.equalsIgnoreCase(subcommand)) {
                return "[Match records](" + matchesTabUrl + ")";
            } else if (PING_SUBCOMMAND.equalsIgnoreCase(subcommand)) {
                Matches matches = GSheets.readMatchesFromSheet(draft.sheetId);
                StringBuilder msg = new StringBuilder("Players left to play you: ");
                int playerIdx = findPlayerIdx(draft, playerDiscordId);
                Matches.Player matchPlayer = matches.players.get(playerIdx);
                for (int i = 0; i < 8; ++i) {
                    if (i == playerIdx) {
                        continue;
                    }
                    if (!matchPlayer.lost[i] && !matchPlayer.won[i]) {
                        msg.append("<@").append(draft.players.getValue(i).discordId).append("> ");
                    }
                }
                return msg.toString();
            } else if (STANDINGS_SUBCOMMAND.equals(subcommand)) {
                Matches matches = GSheets.readMatchesFromSheet(draft.sheetId);
                StringBuilder msg = new StringBuilder("\n");
                for (Matches.Player player : matches.players) {
                    msg.append("<@").append(player.discordId).append(">: ").append(player.record).append("\n");
                }
                return msg.toString();
            } else if (RECORD_SUBCOMMAND.equals(subcommand)) {
                Matches matches = GSheets.readMatchesFromSheet(draft.sheetId);
                List<String> wonVs = new ArrayList<>();
                List<String> lostVs = new ArrayList<>();
                List<String> toPlay = new ArrayList<>();

                Matches.Player player = getCurrentPlayerFromMatches(matches, playerDiscordId);
                for (int i = 0; i < 8; ++i) {
                    String targetDiscordId = matches.players.get(i).discordId;
                    String idTag = "<@" + targetDiscordId + ">";
                    if (player.won[i]) {
                        wonVs.add(idTag);
                    } else if (player.lost[i]) {
                        lostVs.add(idTag);
                    } else if (!playerDiscordId.equals(targetDiscordId)) {
                        toPlay.add(idTag);
                    }
                }
                if (wonVs.isEmpty()) {
                    wonVs.add("<nobody>");
                }
                if (lostVs.isEmpty()) {
                    lostVs.add("<nobody>");
                }
                if (toPlay.isEmpty()) {
                    toPlay.add("<nobody>");
                }

                return "\nYour record: " + player.record + "\nWon vs: " + String.join(", ", wonVs) + "\n" +
                        "Lost vs: " + String.join(", ", lostVs) + "\n" +
                        "To play: " + String.join(", ", toPlay);
            }
        } catch (Exception e) {
            String msg = "Error while processing command";
            logger.error(msg, e);
            return msg;
        }

//        String usertag = event.getUser().getAsTag();
//        if (!draft.players.containsKey(usertag)) {
//            return "You're not in this draft";
//        }

        logger.warn("Unhandled match subcommand: " + event.getSubcommandName());
        return "Unexpected error trying to handle this command";
    }

    @Nullable
    private static Matches.Player getCurrentPlayerFromMatches(Matches matches, String playerDiscordId) {
        for (int i = 0; i < 8; ++i) {
            Matches.Player player = matches.players.get(i);
            if (player.discordId.equals(playerDiscordId)) {
                return player;
            }
        }
        return null;
    }

    private static int findPlayerIdx(Draft draft, String playerDiscordId) throws Exception {
        for (int i = 0; i < 8; ++i) {
            String targetDiscordId = draft.players.getValue(i).discordId;
            if (playerDiscordId.equals(targetDiscordId)) {
                return i;
            }
        }
        throw new Exception("Player id not found in draft data");
    }
}
