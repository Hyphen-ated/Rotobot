package hyphenated.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PreloadCommand extends ListenerAdapter {
    public static final String CMD = "preload";
    private static final Logger logger = LoggerFactory.getLogger(PreloadCommand.class);

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals(CMD)) {
            event.reply("Use /pick now to do preloads").queue();
        }
    }
}
