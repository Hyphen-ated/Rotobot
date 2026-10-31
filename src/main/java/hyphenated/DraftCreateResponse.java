package hyphenated;

public class DraftCreateResponse {
    public String sheetId;
    public String matchesTabGid;

    public DraftCreateResponse(String sheetId, String matchesTabGid) {
        this.sheetId = sheetId;
        this.matchesTabGid = matchesTabGid;
    }
}
