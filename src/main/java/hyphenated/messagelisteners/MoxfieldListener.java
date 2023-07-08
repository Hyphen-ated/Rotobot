package hyphenated.messagelisteners;

import hyphenated.Rotobot;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.RestAction;

public class MoxfieldListener extends ListenerAdapter {
    @Override
    public void onMessageReceived(MessageReceivedEvent event)
    {
        if (event.getAuthor().isBot()) {
            return;
        }

        Message message = event.getMessage();
        MessageChannel channel = event.getChannel();
        if(!Rotobot.drafts.containsKey(channel.getId())) {
            return;
        }

        String content = message.getContentRaw();
        if (content.contains("https://www.moxfield.com/decks/"))
        {
            RestAction<Void> result = channel.pinMessageById(message.getIdLong());
            result.queue();
        }

    }
}
