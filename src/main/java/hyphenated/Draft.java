package hyphenated;

import org.apache.commons.collections4.Trie;
import org.apache.commons.collections4.map.LinkedMap;
import org.apache.commons.collections4.trie.PatriciaTrie;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class Draft {
    public static class Player {
        public List<String> pickList;
        public String discordId;
        public int seat; //unit indexed
    }

    public String sheetId;
    // player names are discord "tags" e.g. Alice#1234
    public LinkedMap<String, Player> players = new LinkedMap<>(8);
    // optimization so autocomplete can be faster with just one lookup. redundant with the cards in the players above
    public final Set<String> pickedCards = new HashSet<>();

    // key: lowercase name. value: caps name
    public final Trie<String, String> legalCardsTrie = new PatriciaTrie<>();

    public String channelId;
    public SnakeStyle snakeStyle;

    public Draft(String sheetId,
                 String channelId,
                 List<String> playerTags,
                 List<String> playerIds,
                 SnakeStyle snakeStyle,
                 Set<String> legalCards,
                 @Nullable List<List<String>> picks) {
        this.sheetId = sheetId;
        this.channelId = channelId;
        this.snakeStyle = snakeStyle;
        if (playerTags.size() != 8 || (picks != null && picks.size() != 8) || playerIds.size() != 8) {
            throw new RuntimeException("Bot doesn't support non-8 playercounts yet");
        }
        for(int i = 0; i < 8; ++i) {
            Player pObj = new Player();
            if (picks != null) {
                pObj.pickList = picks.get(i);
            }
            pObj.seat = i + 1;
            pObj.discordId = playerIds.get(i);
            String playerTag = playerTags.get(i);
            players.put(playerTag, pObj);

            if(pObj.pickList != null) {
                pickedCards.addAll(pObj.pickList);
            }
        }

        for(String card : legalCards) {
            String simpleCard = Rotobot.simplifyName(card);
            this.legalCardsTrie.put(simpleCard, Rotobot.cardsLowerToCaps.get(simpleCard));

        }
    }
}
