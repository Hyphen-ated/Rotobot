package hyphenated;

import hyphenated.commands.PickCommand;
import hyphenated.commands.UpdateScryfallCommand;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

public class Rotobot {


    public static void main(String[] args) throws Exception {
        JDA api = JDABuilder.createDefault(Config.DISCORD_BOT_TOKEN)
                .addEventListeners(new PickCommand())
                .addEventListeners(new UpdateScryfallCommand())
                .build();
        api.updateCommands().addCommands(
                Commands.slash(PickCommand.CMD, "Picks a card in the current draft")
                        .addOption(OptionType.STRING, "card", "The card to pick", true),
                Commands.slash(UpdateScryfallCommand.CMD, "Download the latest scryfall data")
                        .setDefaultPermissions(DefaultMemberPermissions.DISABLED)
        ).queue();
    }

}
