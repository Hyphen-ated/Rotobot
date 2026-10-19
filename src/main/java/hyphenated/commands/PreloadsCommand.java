package hyphenated.commands;

import hyphenated.Draft;
import hyphenated.Rotobot;
import hyphenated.json.DraftJson;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;


public class PreloadsCommand extends ListenerAdapter {
    public static final String CMD = "preloads";
    public static final String LIST_SUBCOMMAND = "list";
    public static final String CLEAR_SUBCOMMAND = "clear";
    private static final Logger logger = LoggerFactory.getLogger(PreloadsCommand.class);

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

        DraftJson draftJson;
        try {
            draftJson = Rotobot.draftJsonDAO.getDraftJsonBySheetId(draft.sheetId);
        } catch (Exception e) {
            String msg = "Error while trying to load draft info from local disk";
            logger.error(msg, e);
            return msg;
        }

        String usertag = event.getUser().getAsTag();
        if (!draft.players.containsKey(usertag)) {
            return "You're not in this draft";
        }

        if (LIST_SUBCOMMAND.equalsIgnoreCase(event.getSubcommandName())) {
            if (draftJson.preloads != null) {
                List<String> playerPreloads = draftJson.preloads.get(usertag);
                if (playerPreloads != null && !playerPreloads.isEmpty()) {
                    if (playerPreloads.size() == 1) {
                        String card = playerPreloads.get(0);
                        return "Your preload is: [" + card + "](" + PickCommand.scryfallUrl(card) + ")";
                    } else {
                        StringBuilder preloadText = new StringBuilder();
                        preloadText.append("Your preloads are:\n");
                        for (String preload : playerPreloads) {
                            preloadText.append("\n* [" + preload + "](" + PickCommand.scryfallUrl(preload) + ")");
                        }
                        return preloadText.toString();
                    }
                }
            }
            return "You have no preloads";
        }

        if (CLEAR_SUBCOMMAND.equalsIgnoreCase(event.getSubcommandName())) {
            try {
                Rotobot.draftJsonDAO.setPlayerPreload(draft.sheetId, usertag, Collections.emptyList());
                draftJson.preloads.put(usertag, Collections.emptyList());
            } catch (Exception e) {
                logger.error("Couldn't clear preload json for " + event.getUser().getName(), e);
                return "Unexpected error while trying to clear your preloads";
            }
            return "Cleared your preloads";
        }
        logger.warn("Unhandled preload subcommand: " + event.getSubcommandName());
        return "Unexpected error trying to handle this command";
    }
}
