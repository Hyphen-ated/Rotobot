package hyphenated;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.Permission;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.*;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class GSheets {
    private static final String APPLICATION_NAME = "Rotobot";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";

    /**
     * Global instance of the scopes required by this quickstart.
     * If modifying these scopes, delete your previously saved tokens/ folder.
     */
    private static final List<String> SCOPES =
            List.of(SheetsScopes.SPREADSHEETS, DriveScopes.DRIVE);
    private static final String CREDENTIALS_FILE_PATH = "./gsheet_credentials.json";

    /**
     * Creates an authorized Credential object.
     *
     * @param HTTP_TRANSPORT The network HTTP Transport.
     * @return An authorized Credential object.
     * @throws IOException If the credentials.json file cannot be found.
     */
    private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT)
            throws IOException {
        // Load client secrets.
        FileInputStream in = new FileInputStream(CREDENTIALS_FILE_PATH);

        GoogleClientSecrets clientSecrets =
                GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in, StandardCharsets.UTF_8));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

    public static synchronized Draft readFromSheet(String sheetId) throws Exception {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        Sheets service =
                new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                        .setApplicationName(APPLICATION_NAME)
                        .build();

        // the player tags and ids and the channel id are adjacent, get them together
        final String playerRange = "engine!P2:X3";
        ValueRange playerResponse = service.spreadsheets().values()
                .get(sheetId, playerRange)
                .execute();
        List<List<Object>> playerValues = playerResponse.getValues();

        if (playerValues == null || playerValues.isEmpty()) {
            throw new RuntimeException("Player tags came back empty, this shouldn't happen");
        }

        List<Object> playerObjs = playerValues.get(0);
        List<String> playerTags = new ArrayList<>(8);
        for(int i = 0; i < 8; ++i) {
            Object playerObj = playerObjs.get(i);
            playerTags.add(playerObj.toString());
        }
        String channelId = playerObjs.get(8).toString();

        List<Object> playerIdObjs = playerValues.get(1);
        List<String> playerIds = new ArrayList<>(8);
        for(int i = 0; i < 8; ++i) {
            Object playerIdObj = playerIdObjs.get(i);
            playerIds.add(playerIdObj.toString());
        }

        final String legalityRange = "engine!A2:A";
        ValueRange legalityResponse = service.spreadsheets().values()
                .get(sheetId, legalityRange)
                .execute();
        List<List<Object>> legalityValues = legalityResponse.getValues();
        if (legalityValues == null || legalityValues.isEmpty()) {
            throw new RuntimeException("Legalities came back empty, this shouldn't happen");
        }
        HashSet<String> legalCards = new HashSet<>();
        for(List<Object> row : legalityValues) {
            Object obj = row.get(0);
            legalCards.add(obj.toString());
        }

        final String mainRange = "draft!C2:J47";
        ValueRange mainResponse = service.spreadsheets().values()
                .get(sheetId, mainRange)
                .execute();
        List<List<Object>> mainValues = mainResponse.getValues();

        List<List<String>> picks = new ArrayList<>();
        for(int i = 0; i < 8; ++i) {
            picks.add(new ArrayList<>());
        }

        if (mainValues != null) { // it's null during a new draft
            for (List<Object> row : mainValues) {
                int i = 0;
                for (Object obj : row) {
                    String pick = obj.toString();
                    if (!StringUtils.isBlank(pick)) {
                        picks.get(i).add(Rotobot.simplifyName(pick));
                    }
                    ++i;
                }
            }
        }

        return new Draft(sheetId, channelId, playerTags, playerIds, legalCards, picks);
    }

    public static synchronized int writePick(String sheetId, String cellCoord, String card) throws Exception {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        Sheets service =
                new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                        .setApplicationName(APPLICATION_NAME)
                        .build();

        ValueRange requestBody = new ValueRange();
        List<Object> innerList = new ArrayList<>();
        innerList.add(card);
        List<List<Object>> outerList = new ArrayList<>();
        outerList.add(innerList);
        requestBody.setValues(outerList);

        Sheets.Spreadsheets.Values.Update request =
                service.spreadsheets().values().update(sheetId, cellCoord, requestBody);
        request.setValueInputOption("RAW");
        UpdateValuesResponse response = request.execute();
        return response.getUpdatedCells();
    }

    public static synchronized String createSheetCopy(String templateId,
                                                      String draftName,
                                                      String channelId,
                                                      List<String> playerTags,
                                                      List<String> playerIds,
                                                      List<Card> legalCards) throws Exception {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        Drive driveService = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                .setApplicationName(APPLICATION_NAME)
                .build();

        com.google.api.services.drive.model.File nonUpdate = new com.google.api.services.drive.model.File();
        nonUpdate.setName(draftName);
        com.google.api.services.drive.model.File resp = driveService.files().copy(templateId, nonUpdate).execute();

        String destinationSpreadsheetId = resp.getId();

        Sheets service =
                new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                        .setApplicationName(APPLICATION_NAME)
                        .build();

        ValueRange engineRequestBody = new ValueRange();
        List<Object> tagList = new ArrayList<>(playerTags);
        tagList.add(channelId); // happens to come right after the tags

        List<Object> idList = new ArrayList<>(playerIds);

        List<List<Object>> outerList = new ArrayList<>();
        outerList.add(tagList);
        outerList.add(idList);
        engineRequestBody.setValues(outerList);

        Sheets.Spreadsheets.Values.Update engineRequest =
                service.spreadsheets().values().update(destinationSpreadsheetId,
                        "engine!P2:X3",
                        engineRequestBody);
        engineRequest.setValueInputOption("RAW");

        UpdateValuesResponse engineResponse = engineRequest.execute();
/////////////////////////////

        ValueRange namesRequestBody = new ValueRange();
        List<Object> namesList = new ArrayList<>();
        for(String tag : playerTags) {
            int hashIdx = tag.indexOf("#");
            if (hashIdx == -1) {
                namesList.add(tag);
            } else {
                namesList.add(tag.substring(0, hashIdx));
            }
        }
        List<List<Object>> namesOuterList = new ArrayList<>();
        namesOuterList.add(namesList);
        namesRequestBody.setValues(namesOuterList);

        Sheets.Spreadsheets.Values.Update namesRequest =
                service.spreadsheets().values().update(destinationSpreadsheetId,
                        "draft!C1:J1",
                        namesRequestBody);
        namesRequest.setValueInputOption("RAW");

        UpdateValuesResponse namesResponse = namesRequest.execute();
///////////////////////////

        ValueRange legalRequestBody = new ValueRange();
        List<List<Object>> legalOuterList = new ArrayList<>();
        for(Card card : legalCards) {
            List<Object> legalInnerList = new ArrayList<>();
            legalInnerList.add(card.name);
            legalInnerList.add(card.color);
            legalOuterList.add(legalInnerList);
        }
        legalRequestBody.setValues(legalOuterList);

        Sheets.Spreadsheets.Values.Update legalRequest =
                service.spreadsheets().values().update(destinationSpreadsheetId,
                        "engine!A2:B" + (legalCards.size()+1),
                        legalRequestBody);
        legalRequest.setValueInputOption("RAW");

        UpdateValuesResponse legalResponse = legalRequest.execute();
//////////////////////////

        Permission permission = new Permission();
        permission.setType("anyone");
        permission.setRole("writer");
        Permission permissionResponse =
                driveService.permissions().create(destinationSpreadsheetId, permission).execute();

        return destinationSpreadsheetId;
    }
}
