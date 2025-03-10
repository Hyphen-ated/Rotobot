package hyphenated;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import hyphenated.json.DraftJson;

import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JsonDAO {
    private Gson gson;
    public JsonDAO() {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder = gsonBuilder.setPrettyPrinting();
        gson = gsonBuilder.create();
    }

    public synchronized List<DraftJson> getDraftJsons() throws Exception {
        Type type = new TypeToken<ArrayList<DraftJson>>(){}.getType();
        List<DraftJson> drafts = gson.fromJson(new FileReader(Config.ACTIVE_DRAFTS_PATH, StandardCharsets.UTF_8), type);
        if (drafts == null) {
            return new ArrayList<>();
        }
        return drafts;
    }

    public synchronized DraftJson getDraftJsonBySheetId(String sheetid) throws Exception {
        for (DraftJson draftJson : getDraftJsons()) {
            if (sheetid.equals(draftJson.sheetId)) {
                return draftJson;
            }
        }
        throw new IllegalStateException("sheetId not found in draft json file");
    }

    private synchronized void writeDraftJsons(List<DraftJson> drafts) throws Exception {
        Type type = new TypeToken<List<DraftJson>>(){}.getType();
        FileWriter writer = new FileWriter(Config.ACTIVE_DRAFTS_PATH, StandardCharsets.UTF_8);
        gson.toJson(drafts, type, writer);
        writer.close();
    }

    public synchronized void setDraft(DraftJson draft) throws Exception {
        List<DraftJson> drafts = getDraftJsons();
        for (int i = 0; i < drafts.size(); ++i) {
            DraftJson existing = drafts.get(i);
            if (existing.sheetId.equals(draft.sheetId)) {
                drafts.remove(i);
                break;
            }
        }
        drafts.add(draft);
        writeDraftJsons(drafts);
    }

    public synchronized void deleteDraft(String sheetId) throws Exception{
        List<DraftJson> oldDrafts = getDraftJsons();
        List<DraftJson> newDrafts = new ArrayList<>();
        for(DraftJson draftJson : oldDrafts) {
            if(!draftJson.sheetId.equals(sheetId)) {
                newDrafts.add(draftJson);
            }
        }
        writeDraftJsons(newDrafts);
    }

    public synchronized void setPlayerPreload(String sheetId, String playerTag, List<String> picks) throws Exception {
        List<DraftJson> drafts = getDraftJsons();
        for (DraftJson draft : drafts) {
            if (sheetId.equals(draft.sheetId)) {
                if (draft.preloads == null) {
                    draft.preloads = new HashMap<>();
                }
                draft.preloads.put(playerTag, picks);
            }
        }
        writeDraftJsons(drafts);
    }

    public synchronized Map<String, List<String>> getPreloads(String sheetId) throws Exception {
        List<DraftJson> drafts = getDraftJsons();
        for (DraftJson draft : drafts) {
            if (sheetId.equals(draft.sheetId)) {
                return draft.preloads;
            }
        }
        return new HashMap<>();
    }
}
