package hyphenated.commands;

import hyphenated.*;
import hyphenated.json.ActiveDraft;
import net.dv8tion.jda.api.entities.*;
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
    public static final String CMD = "startdraft";
    private static final String TEMPLATE_SHEET_ID = "1u2bxYp6fHkrDHIjN9dM2vUhL3pn9f85BEQN1A_yf-W0";
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals(CMD)) {
            User user = event.getUser();
            String tag = user.getAsTag();
            if(!tag.equals(Config.OWNER_TAG)) {
                event.reply("Only " + Config.OWNER_TAG + " can do this").queue();
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

            String draftName = event.getOption("name").getAsString();
            String draftNameDate = draftName + "-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            Role role = guild.createRole()
                    .setName(draftName)
                    .setMentionable(true)
                    .setHoisted(true)
                    .setColor(new Color(randColorInt(), randColorInt(), randColorInt()))
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
            List<String> legalCards = Rotobot.getLegalCardsAndUpdateCapsMap(format);

            String newSheetId = GSheets.createSheetCopy(TEMPLATE_SHEET_ID,
                    draftNameDate,
                    channelId,
                    playerTags,
                    playerIds,
                    legalCards);

            ActiveDraft activeDraft = new ActiveDraft();
            activeDraft.sheetId = newSheetId;
            activeDraft.name = draftName;
            Rotobot.jsonDAO.addDraft(activeDraft);

            Draft newDraft = new Draft(
                    newSheetId,
                    channelId,
                    playerTags,
                    playerIds,
                    new HashSet<>(legalCards),
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

    private int randColorInt() {
        return ThreadLocalRandom.current().nextInt(30, 256);
    }
}