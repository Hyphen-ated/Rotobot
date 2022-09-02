package hyphenated;

import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

public class Rotobot {


    public static void main(String[] args) throws Exception {
        Dotenv dotenv = Dotenv.load();
        String token = dotenv.get("DISCORD_BOT_TOKEN");
        JDA api = JDABuilder.createDefault(token)
                .addEventListeners(new PickCommand())
                .build();
        api.updateCommands().addCommands(
                Commands.slash("pick", "Picks a card in the current draft")
                        .addOption(OptionType.STRING, "card", "The card to pick", true)
        ).queue();
    }
}
