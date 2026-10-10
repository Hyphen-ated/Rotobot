package hyphenated.json;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QueueJson {
    public QueueJson() {
        this.signupQueuesByChannelId = new HashMap<>();
    }

    public static class QueueEntry {
        public String userId;
        public long signupTimeSeconds;

        public QueueEntry(String userId, long signupTimeSeconds) {
            this.userId = userId;
            this.signupTimeSeconds = signupTimeSeconds;
        }
    }
    public Map<String, List<QueueEntry>> signupQueuesByChannelId;
}
