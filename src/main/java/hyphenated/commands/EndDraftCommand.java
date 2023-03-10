package hyphenated.commands;

import hyphenated.Config;
import hyphenated.Draft;
import hyphenated.Rotobot;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

public class EndDraftCommand extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(EndDraftCommand.class);
    public static final String CMD = "enddraft";
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals(CMD)) {
            if(!Rotobot.userIsAdmin(event)) {
                event.reply("Only admins can end a draft").queue();
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
            Draft draft = Rotobot.drafts.get(channelId);
            String sheetId = draft.sheetId;

            if(!Rotobot.drafts.containsKey(channelId)) {
                return "I don't see a draft happening in this channel";
            }

            Rotobot.jsonDAO.deleteDraft(sheetId);
            Rotobot.drafts.remove(channelId);
            return "Ended the draft in this channel. ( " + Rotobot.formatSheetUrl(sheetId) + " )";
        } catch (Exception e) {
            logger.error("Exception while ending a draft", e);
            return "Exception while ending a draft";
        }
    }

}