package hyphenated.commands;

import hyphenated.Config;
import hyphenated.Rotobot;
import hyphenated.json.QueueJson;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

public class QueueCommands extends ListenerAdapter {
    public static final String QUEUE_CMD = "queue";
    public static final String IN_CMD = "in";
    public static final String OUT_CMD = "out";
    public static final String QUEUE_ADD_CMD = "queue-add";
    public static final String QUEUE_REMOVE_CMD = "queue-remove";
    public static final String QUEUE_CLEAR_CMD = "queue-clear";

    Set<String> CMDS = Set.of(QUEUE_CMD, IN_CMD, OUT_CMD, QUEUE_ADD_CMD, QUEUE_REMOVE_CMD, QUEUE_CLEAR_CMD);

    private static final Logger logger = LoggerFactory.getLogger(QueueCommands.class);
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (CMDS.contains(event.getName())) {
            event.deferReply().queue();
            String reply = handleAndMakeReply(event);
            event.getHook().sendMessage(reply).queue();
        }
    }

    public String handleAndMakeReply(SlashCommandInteractionEvent event) {
        try {
            String cmd = event.getName();
            String channelId = event.getMessageChannel().getId();

            GuildChannel channel = event.getGuildChannel();
            if (channel instanceof ThreadChannel) {
                channel = ((ThreadChannel)channel).getParentChannel();
            }

            if (channel instanceof ICategorizableChannel) {
                ICategorizableChannel ichannel = (ICategorizableChannel) channel;
                String categoryId = ichannel.getParentCategoryId();
                if (!categoryId.equals(Config.SIGNUPS_CATEGORY_ID)) {
                    return "This isn't a signup channel";
                }
            }

            String userTag = event.getUser().getAsTag();
            if (QUEUE_CMD.equals(cmd)) {
               List<QueueJson.QueueEntry> entries =  Rotobot.queueJsonDAO.getQueue(channelId);
               if (entries == null || entries.isEmpty()) {
                   return "Nobody is queued for a draft in this channel";
               }
               StringBuilder msg = new StringBuilder("Signup Queue:\n");
               int i = 1;
               long now = System.currentTimeMillis() / 1000;
               for (QueueJson.QueueEntry entry : entries) {
                   long secDiff = now - entry.signupTimeSeconds;
                   String timeLabel = "(today)";
                   if (secDiff > 86400 && secDiff < 172800) {
                       timeLabel = "(1 day)";
                   } else if (secDiff >= 172800) {
                       timeLabel = "(" + secDiff / 86400 + " days)";
                   }
                   msg.append(i + ": " + entry.userTag + " " + timeLabel + "\n");
                   ++i;
               }
               return msg.toString();

            } else if (IN_CMD.equals(cmd)) {
                int size = Rotobot.queueJsonDAO.addUser(channelId, userTag);
                return "You're in! The queue is at " + size;
            } else if (OUT_CMD.equals(cmd)) {
                int size = Rotobot.queueJsonDAO.removeUser(channelId, userTag);
                return "Seeya. The queue is at " + size;
            } else if (QUEUE_ADD_CMD.equals(cmd)) {
                OptionMapping mapping = event.getOption("user");
                User user = mapping.getAsUser();
                String targetTag = user.getAsTag();
                int size = Rotobot.queueJsonDAO.addUser(channelId, targetTag);
                return "Added " + targetTag + ". The queue is at " + size;
            } else if (QUEUE_REMOVE_CMD.equals(cmd)) {
                OptionMapping mapping = event.getOption("user");
                User user = mapping.getAsUser();
                String targetTag = user.getAsTag();
                int size = Rotobot.queueJsonDAO.removeUser(channelId, targetTag);
                return "Removed " + targetTag + ". The queue is at " + size;
            } else if (QUEUE_CLEAR_CMD.equals(cmd)) {
                Rotobot.queueJsonDAO.clearChannel(channelId);
                return "Cleared the queue";
            }

        } catch (Exception e) {
            String msg = "Unexpected error while trying to do a queue command";
            logger.error(msg, e);
            return msg;
        }
        return "unhandled queue command, oops";
    }


}
