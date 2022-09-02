package hyphenated;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.nio.file.Files;
import java.nio.file.Path;

public class Rotobot {

    private static final String TOKEN_PATH = "./token.txt";

    public static void main(String[] args) throws Exception {
        String token = Files.readString(Path.of(TOKEN_PATH));
        JDA api = JDABuilder.createDefault(token)
                .addEventListeners(new PickCommand())
                .build();
        api.updateCommands().addCommands(
                Commands.slash("pick", "Picks a card in the current draft")
                        .addOption(OptionType.STRING, "card", "The card to pick")
        ).queue();
    }
}
