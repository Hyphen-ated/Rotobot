package hyphenated.commands;

import hyphenated.*;
import hyphenated.json.ActiveDraft;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;


public class StartDraftCommand extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(StartDraftCommand.class);
    public static final String STARTDRAFT = "startdraft";
    public static final String STARTDRAFT_REACTIONS = "startdraft-reactions";
    private static final String TEMPLATE_SHEET_ID = "1pS6PFhn2gtshQM3pnFpDFqZcrj6q3nkUzMwo3EBPtek";
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals(STARTDRAFT)) {
            if(!Rotobot.userIsAdmin(event)) {
                event.reply("Only admins can start a draft").queue();
                return;
            }
            event.deferReply().queue();
            String maybeError = handle(event);
            if (!StringUtils.isBlank(maybeError)) {
                MessageCreateData mcd = new MessageCreateBuilder()
                        .setContent(maybeError)
                        .build();
                event.getHook().sendMessage(mcd).queue();
            }
        } else if (event.getName().equals(STARTDRAFT_REACTIONS)) {
            if(!Rotobot.userIsAdmin(event)) {
                event.reply("Only admins can start a draft").queue();
                return;
            }
            event.deferReply().queue();

        }
    }

    public String handle(SlashCommandInteractionEvent event) {
        try {
            Guild guild = event.getGuild();

            String format = "vintage";
            OptionMapping formatMapping = event.getOption("format");
            if (formatMapping != null) {
                format = formatMapping.getAsString();
            }

            List<String> playerTags = new ArrayList<>(8);
            List<String> playerIds = new ArrayList<>(8);
            List<Integer> indexes = new ArrayList<>(8);
            for (int i = 1; i <= 8; ++i) {
                indexes.add(i);
            }
            if (Config.PROD) {
                Collections.shuffle(indexes);
            }

            SnakeStyle snakeStyle = SnakeStyle.NORMAL;
            OptionMapping snakeStyleOpt = event.getOption("snakestyle");
            if (snakeStyleOpt != null) {
                String snakeStyleStr = snakeStyleOpt.getAsString();
                if (snakeStyleStr.equalsIgnoreCase("nyc")) {
                    snakeStyle = SnakeStyle.NYC;
                }
            }

            String draftName = event.getOption("name").getAsString();
            String draftNameDate = draftName + "-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            Role role = guild.createRole()
                    .setName(draftName)
                    .setMentionable(true)
                    .setHoisted(true)
                    .setColor(generateRandomColor())
                    .complete();

            if (!StringUtils.isBlank(Config.LOWEST_OTHER_ROLE)) {
                List<Role> roles = guild.modifyRolePositions().getCurrentOrder();
                int lowestOtherRoleIdx = 0;
                for (Role existingRole : roles) {
                    if (existingRole.getName().equals(Config.LOWEST_OTHER_ROLE)) {
                        break;
                    }
                    ++lowestOtherRoleIdx;
                }
                int targetRole = lowestOtherRoleIdx - 1;
                if (targetRole < 0) targetRole = 0;
                guild.modifyRolePositions(true)
                        .selectPosition(role)
                        .moveTo(targetRole)
                        .queue();
            }
            for (int j = 0; j < 8; ++j) {
                int i = indexes.get(j);
                OptionMapping mapping = event.getOption("p" + i);
                User user = mapping.getAsUser();
                String tag = user.getAsTag();
                String id = user.getId();

                if (playerTags.contains(tag)) {
                    if(Config.PROD) {
                        return "You specified a player twice: " + tag;
                    } else {
                        tag += i;
                    }
                }
                if (playerIds.contains(id)) {
                    if(Config.PROD) {
                        return "You specified a player twice (with this id): " + id;
                    } else {
                        id += i;
                    }
                }
                playerTags.add(tag);
                playerIds.add(id);

                guild.addRoleToMember(user, role).queue();
            }

            TextChannel channel = null;
            if (!StringUtils.isBlank(Config.ACTIVE_DRAFTS_CATEGORY_ID)) {
                Category parentCategory = guild.getCategoryById(Config.ACTIVE_DRAFTS_CATEGORY_ID);
                channel = guild
                        .createTextChannel(draftNameDate, parentCategory)
                        .complete();
            } else {
                channel = guild.createTextChannel(draftNameDate).complete();
            }
            String channelId = channel.getId();
            List<Card> legalCards = Rotobot.getLegalCardsAndUpdateCapsMap(format);

            String newSheetId = GSheets.createSheetCopy(TEMPLATE_SHEET_ID,
                    draftNameDate,
                    channelId,
                    snakeStyle,
                    playerTags,
                    playerIds,
                    legalCards);

            ActiveDraft activeDraft = new ActiveDraft();
            activeDraft.sheetId = newSheetId;
            activeDraft.name = draftName;
            Rotobot.jsonDAO.addDraft(activeDraft);

            HashSet<String> legalCardNameSet = new HashSet<>(legalCards.size());
            for (Card card : legalCards) {
                legalCardNameSet.add(card.name);
            }
            Draft newDraft = new Draft(
                    newSheetId,
                    channelId,
                    playerTags,
                    playerIds,
                    snakeStyle,
                    legalCardNameSet,
                    null);
            Rotobot.drafts.put(channelId, newDraft);

            String firstMessageStr = "New draft! <" + Rotobot.formatSheetUrl(newSheetId) + "> First up: <@" + playerIds.get(0) + ">\n"
                    + RulesDAO.getRules();

            MessageCreateData firstMcd = new MessageCreateBuilder()
                    .setContent(firstMessageStr)
                    .setAllowedMentions(Collections.singleton(Message.MentionType.USER))
                    .build();

            Message firstMessage = channel.sendMessage(firstMcd).complete();
            firstMessage.pin().queue();

            String reply = "Draft created in <#" +channelId + ">";
            MessageCreateData mcd = new MessageCreateBuilder()
                    .setContent(reply)
                    .build();
            event.getHook().sendMessage(mcd).queue();
            return null;

        } catch (Exception e) {
            logger.error("Exception while making a new draft", e);
            return "Exception while making a new draft";
        }
    }

    private static Color generateRandomColor() {
        // generate random hsl values (easier to constrain in the way i want)
        // then convert to rgb
        float h = ThreadLocalRandom.current().nextFloat();
        float s = ThreadLocalRandom.current().nextFloat();
        float l = ThreadLocalRandom.current().nextFloat();
        // hue needs no restrictions on its range
        s = s/5.0f + 0.4f; // evenly distribute between 0.4 and 0.6
        l = l/2.0f + 0.3f; // evenly distribute between 0.3 and 0.8

        float q = 0;

        if (l < 0.5)
            q = l * (1 + s);
        else
            q = (l + s) - (s * l);

        float p = 2 * l - q;

        float r = Math.max(0, HueToRGB(p, q, h + (1.0f / 3.0f)));
        float g = Math.max(0, HueToRGB(p, q, h));
        float b = Math.max(0, HueToRGB(p, q, h - (1.0f / 3.0f)));

        r = Math.min(r, 1.0f);
        g = Math.min(g, 1.0f);
        b = Math.min(b, 1.0f);

        return new Color((int)(255 * r), (int)(255 * g), (int)(255 * b));
    }

    // params between 0 and 1
    private static float HueToRGB(float p, float q, float h)
    {
        if (h < 0) h += 1;
        if (h > 1 ) h -= 1;
        if (6 * h < 1)
        {
            return p + ((q - p) * 6 * h);
        }
        if (2 * h < 1 )
        {
            return  q;
        }
        if (3 * h < 2)
        {
            return p + ( (q - p) * 6 * ((2.0f / 3.0f) - h) );
        }
        return p;
    }
}
