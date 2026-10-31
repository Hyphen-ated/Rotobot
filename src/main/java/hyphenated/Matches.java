package hyphenated;

import java.util.List;

public class Matches {
    public static class Player {
        public boolean[] won = new boolean[8];
        public boolean[] lost = new boolean[8];
        public String record = "0-0";
        public String discordId;
    }
    public List<Player> players;
}
