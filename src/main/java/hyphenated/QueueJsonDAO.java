package hyphenated;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import hyphenated.json.QueueJson;

import javax.annotation.Nullable;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class QueueJsonDAO {
    private Gson gson;
    public QueueJsonDAO() {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder = gsonBuilder.setPrettyPrinting();
        gson = gsonBuilder.create();
    }
    public synchronized QueueJson getQueues() throws Exception {
        QueueJson queues = gson.fromJson(new FileReader(Config.QUEUES_PATH, StandardCharsets.UTF_8), QueueJson.class);
        if (queues == null) {
            return new QueueJson();
        }
        return queues;
    }

    public synchronized List<QueueJson.QueueEntry> getQueue(String channelId) throws Exception {
        return getQueues().signupQueuesByChannelId.get(channelId);
    }

    public synchronized @Nullable List<QueueJson.QueueEntry> getUserIds(String channelId) throws Exception {
        QueueJson queues = getQueues();
        return queues.signupQueuesByChannelId.get(channelId);
    }

    public synchronized int addUser(String channelId, String userTag) throws Exception {
        QueueJson queues = getQueues();
        List<QueueJson.QueueEntry> queue = queues.signupQueuesByChannelId.get(channelId);
        if (queue == null) {
            queue = new ArrayList<>();
            queues.signupQueuesByChannelId.put(channelId, queue);
        }
        boolean presentAlready = false;
        for (QueueJson.QueueEntry entry : queue) {
            if (entry.userTag.equals(userTag)) {
                entry.signupTimeSeconds = System.currentTimeMillis() / 1000;
                presentAlready = true;
            }
        }
        if (!presentAlready) {
            queue.add(new QueueJson.QueueEntry(userTag, System.currentTimeMillis() / 1000));
        }
        writeQueueJsons(queues);
        return queue.size();
    }

    public synchronized int removeUser(String channelId, String userTag) throws Exception {
        QueueJson queues = getQueues();
        List<QueueJson.QueueEntry> queue = queues.signupQueuesByChannelId.get(channelId);
        if (queue == null) {
            return 0;
        }
        for (QueueJson.QueueEntry entry : queue) {
            if (entry.userTag.equals(userTag)) {
                queue.remove(entry);
                break;
            }
        }
        writeQueueJsons(queues);
        return queue.size();
    }

    public synchronized void clearChannel(String channelId) throws Exception {
        QueueJson queues = getQueues();
        queues.signupQueuesByChannelId.remove(channelId);
        writeQueueJsons(queues);
    }

    private synchronized void writeQueueJsons(QueueJson queues) throws Exception {
        FileWriter writer = new FileWriter(Config.QUEUES_PATH, StandardCharsets.UTF_8);
        gson.toJson(queues, QueueJson.class, writer);
        writer.close();
    }

}
