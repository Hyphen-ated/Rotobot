package hyphenated;

import io.github.cdimascio.dotenv.Dotenv;

public class Config {
    public static final String SCRYFALL_DEFAULT_CARDS_PATH = "./scryfall-default-cards.json";
    public static final String ACTIVE_DRAFTS_PATH = "./active_drafts.json";
    public static final String DISCORD_BOT_TOKEN;
    public static final String OWNER_TAG;
    public static final boolean PROD;
    public static final String LOWEST_OTHER_ROLE;
    public static final String ACTIVE_DRAFTS_CATEGORY_ID;
    public static final String ADMIN_ROLE_ID;
    static {
        Dotenv dotenv = Dotenv.load();
        DISCORD_BOT_TOKEN = dotenv.get("DISCORD_BOT_TOKEN");
        OWNER_TAG = dotenv.get("OWNER_TAG");
        PROD = Boolean.parseBoolean(dotenv.get("PROD"));
        LOWEST_OTHER_ROLE = dotenv.get("LOWEST_OTHER_ROLE");
        ACTIVE_DRAFTS_CATEGORY_ID = dotenv.get("ACTIVE_DRAFTS_CATEGORY_ID");
        ADMIN_ROLE_ID = dotenv.get("ADMIN_ROLE_ID");
    }
}
