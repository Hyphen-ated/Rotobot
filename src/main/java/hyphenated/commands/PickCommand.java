package hyphenated.commands;

import hyphenated.Rotobot;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.stream.Stream;

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

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        if (event.getName().equals(CMD) && event.getFocusedOption().getName().equals("card")) {
            String value = event.getFocusedOption().getValue();
            SortedMap<String, String> cards = Rotobot.vintageCardTrie.prefixMap(value);
            List<Command.Choice> options = new ArrayList<>(25);
            int i = 0;
            for(String name : cards.values()) {
                options.add(new Command.Choice(name, name));
                ++i;
                if (i >= 25) {
                    break;
                }
            }

            event.replyChoices(options).queue();
        }
    }
}
