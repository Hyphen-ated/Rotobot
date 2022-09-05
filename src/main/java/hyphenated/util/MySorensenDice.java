package hyphenated.util;

import info.debatty.java.stringsimilarity.SorensenDice;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MySorensenDice extends SorensenDice {
    // I want to avoid repeatedly computing the profiles
    public final double similarity(Map<String, Integer> profile1, Map<String, Integer> profile2) {
        Set<String> union = new HashSet<String>();
        union.addAll(profile1.keySet());
        union.addAll(profile2.keySet());

        int inter = 0;

        for (String key : union) {
            if (profile1.containsKey(key) && profile2.containsKey(key)) {
                inter++;
            }
        }

        return 2.0 * inter / (profile1.size() + profile2.size());
    }

    public MySorensenDice(final int k) {
        super(k);
    }

    public MySorensenDice() {
        super();
    }
}
