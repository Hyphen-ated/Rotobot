package hyphenated;

import org.apache.commons.collections4.Trie;
import org.apache.commons.collections4.trie.PatriciaTrie;

import java.util.*;

public class Draft {
    public String sheetId;
    // player names are discord "tags" e.g. Alice#1234
    public LinkedHashMap<String, List<String>> playerPickLists = new LinkedHashMap<>(8);
    // optimization so autocomplete can be faster. this is a copy of each of the 8 lists above
    public final Set<String> pickedCards = new HashSet<>();

    // key: lowercase name. value: caps name
    public final Trie<String, String> legalCardsTrie = new PatriciaTrie<>();

    public String channelId;
    public String channelName;


    public Draft(String sheetId, String channelId,  List<String> eightPlayers, Set<String> legalCards, List<List<String>> picks) {
        this.sheetId = sheetId;
        this.channelId = channelId;
        if (eightPlayers.size() != 8 || picks.size() != 8 ) {
            throw new RuntimeException("Bot doesn't support non-8 playercounts yet");
        }
        int i = 0;
        for (String player : eightPlayers) {
            List<String> playerPicks = picks.get(i);
            playerPickLists.put(player, playerPicks);
            pickedCards.addAll(playerPicks);
            ++i;
        }
        for(String card : legalCards) {
            String lowerCard = card.toLowerCase(Locale.ROOT);
            this.legalCardsTrie.put(lowerCard, Rotobot.cardsLowerToCaps.get(lowerCard));

        }
    }
}
