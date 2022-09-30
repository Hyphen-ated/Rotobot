package hyphenated.commands;

import hyphenated.Config;
import hyphenated.Draft;
import hyphenated.GSheets;
import hyphenated.Rotobot;
import hyphenated.json.ActiveDraft;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;


public class StartDraftCommand extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(StartDraftCommand.class);
    public static final String CMD = "startdraft";
    private static final String TEMPLATE_SHEET_ID = "1u2bxYp6fHkrDHIjN9dM2vUhL3pn9f85BEQN1A_yf-W0";
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals(CMD)) {
            User user = event.getUser();
            String tag = user.getAsTag();
            if(!tag.equals(Config.OWNER_TAG)) {
                event.reply("Only " + Config.OWNER_TAG + " can do this").queue();
                return;
            }

            event.deferReply().queue();
            String reply = handleAndMakeReply(event);
            MessageCreateData mcd = new MessageCreateBuilder()
                    .setContent(reply)
                    .setAllowedMentions(Collections.singleton(Message.MentionType.USER))
                    .build();
            event.getHook().sendMessage(mcd).queue();
        }
    }

    public String handleAndMakeReply(SlashCommandInteractionEvent event) {
        try {
            String channelId = event.getMessageChannel().getId();
            String name = event.getChannel().getName();

            String format = "vintage";
            OptionMapping formatMapping = event.getOption("format");
            if (formatMapping != null) {
                format = formatMapping.getAsString();
            }

            for(String existingChannelId : Rotobot.drafts.keySet()) {
                if (existingChannelId.equals(channelId)) {
                    return "A draft already exists in this channel ( " +
                            Rotobot.formatSheetUrl(Rotobot.drafts.get(existingChannelId).sheetId) + ")";
                }
            }

            List<String> playerTags = new ArrayList<>(8);
            List<String> playerIds = new ArrayList<>(8);
            List<Integer> indexes = new ArrayList<>(8);
            for(int i = 1; i <= 8; ++i) {
                indexes.add(i);
            }
            Collections.shuffle(indexes);

            for (int j = 0; j < 8; ++j) {
                int i = indexes.get(j);
                OptionMapping mapping = event.getOption("p" + i);
                User user = mapping.getAsUser();
                String tag = user.getAsTag();
                String id = user.getId();

                if (playerTags.contains(tag)) {
                    tag += i;
//                    return "You specified a player twice: " + tag;
                }
                if (playerIds.contains(id)) {
                    id += i;
//                    return "You specified a player twice (with this id): " + id;
                }
                playerTags.add(tag);
                playerIds.add(id);
            }

            List<String> legalCards = Rotobot.getLegalCardsAndUpdateCapsMap(format);

            String newSheetId = GSheets.createSheetCopy(TEMPLATE_SHEET_ID,
                    name,
                    channelId,
                    playerTags,
                    playerIds,
                    legalCards);

            ActiveDraft activeDraft = new ActiveDraft();
            activeDraft.sheetId = newSheetId;
            Rotobot.jsonDAO.addDraft(activeDraft);

            Draft newDraft = new Draft(
                    newSheetId,
                    channelId,
                    playerTags,
                    playerIds,
                    new HashSet<>(legalCards),
                    null);
            Rotobot.drafts.put(channelId, newDraft);

            return "[New draft!](" + Rotobot.formatSheetUrl(newSheetId) + ">) First up: <@" + playerIds.get(0) + ">";
        } catch (Exception e) {
            logger.error("Exception while making a new draft", e);
            return "Exception while making a new draft";
        }
    }

}