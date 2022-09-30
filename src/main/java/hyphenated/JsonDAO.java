package hyphenated;

import com.google.api.client.json.Json;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import hyphenated.json.ActiveDraft;

import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class JsonDAO {
    private Gson gson;
    public JsonDAO() {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder = gsonBuilder.setPrettyPrinting();
        gson = gsonBuilder.create();
    }

    public synchronized List<ActiveDraft> getActiveDrafts() throws Exception {
        Type type = new TypeToken<ArrayList<ActiveDraft>>(){}.getType();
        List<ActiveDraft> drafts = gson.fromJson(new FileReader(Config.ACTIVE_DRAFTS_PATH, StandardCharsets.UTF_8), type);
        if (drafts == null) {
            return new ArrayList<>();
        }
        return drafts;
    }

    private synchronized void writeActiveDrafts(List<ActiveDraft> drafts) throws Exception {
        Type type = new TypeToken<List<ActiveDraft>>(){}.getType();
        FileWriter writer = new FileWriter(Config.ACTIVE_DRAFTS_PATH, StandardCharsets.UTF_8);
        gson.toJson(drafts, type, writer);
        writer.close();
    }

    public synchronized void addDraft(ActiveDraft draft) throws Exception {
        List<ActiveDraft> drafts = getActiveDrafts();
        drafts.add(draft);
        writeActiveDrafts(drafts);
    }

    public synchronized void preloadPicks(String sheetId, String playerTag, List<String> picks) throws Exception {
        List<ActiveDraft> drafts = getActiveDrafts();
        for (ActiveDraft draft : drafts) {
            if (sheetId.equals(draft.sheetId)) {
                draft.preloads.put(playerTag, picks);
            }
        }
        writeActiveDrafts(drafts);
    }

    public synchronized void deleteDraft(String sheetId) throws Exception{
        List<ActiveDraft> oldDrafts = getActiveDrafts();
        List<ActiveDraft> newDrafts = new ArrayList<>();
        for(ActiveDraft activeDraft : oldDrafts) {
            if(!activeDraft.sheetId.equals(sheetId)) {
                newDrafts.add(activeDraft);
            }
        }
        writeActiveDrafts(newDrafts);
    }
}
