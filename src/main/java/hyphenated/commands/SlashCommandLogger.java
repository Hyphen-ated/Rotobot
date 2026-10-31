package hyphenated.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SlashCommandLogger extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(SlashCommandLogger.class);

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        logger.info(
                "Command: {} {} {}",
                event.getChannel().getName(),
                event.getUser().getName(),
                event.getCommandString()
        );
    }
}
