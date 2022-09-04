package hyphenated.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.apache.commons.lang3.StringUtils;

public class PickCommand extends ListenerAdapter {
    public static final String CMD = "pick";
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals(CMD)) {
            OptionMapping mapping = event.getOption("card");
            String card;
            if (mapping == null) {
                card = null;
            } else {
                card = mapping.getAsString();
            }
            String reply;
            if (StringUtils.isBlank(card)) {
                reply = "You need to pick a card";
            } else {
                reply = "Ah, " + card + ", nice choice.";
            }
            event.reply(reply).queue();
        }
    }
}
