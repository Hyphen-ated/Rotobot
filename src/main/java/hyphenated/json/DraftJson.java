package hyphenated.json;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DraftJson {
    public String name;
    public String sheetId;
    // playerTag -> caps card names
    public Map<String, List<String>> preloads = new HashMap<>();
}
