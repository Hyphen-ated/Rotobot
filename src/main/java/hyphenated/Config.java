package hyphenated;

import io.github.cdimascio.dotenv.Dotenv;

public class Config {
    public static final String SCRYFALL_DEFAULT_CARDS_PATH = "./scryfall-default-cards.json";
    public static final String ACTIVE_DRAFTS_PATH = "./active_drafts.json";

    public static final String DISCORD_BOT_TOKEN;
    public static final String OWNER_TAG;

    static {
        Dotenv dotenv = Dotenv.load();
        DISCORD_BOT_TOKEN = dotenv.get("DISCORD_BOT_TOKEN");
        OWNER_TAG = dotenv.get("OWNER_TAG");
    }
}
