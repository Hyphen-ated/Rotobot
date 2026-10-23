This is a discord bot for helping run Magic: The Gathering rotisserie drafts. If you want to do your own rotisserie, we're happy to let you run your event in our discord server, which is probably easier than you running your own copy of the bot: https://discord.gg/AM3S4Q3NUV

----

The bot makes a copy of this draft spreadsheet for each new draft: https://docs.google.com/spreadsheets/d/1pS6PFhn2gtshQM3pnFpDFqZcrj6q3nkUzMwo3EBPtek/edit?usp=sharing

The bot is primarily just a nicer interface for manipulating this spreadsheet. If the bot has an outage, players can always fall back to doing everything manually.

### Features
`/pick` lets players select a card. It'll put the card in the sheet, announce the card in discord, and ping the next player in line. You can also give a list of cards and it will preload the later ones to be picked automatically the next time it's your turn, just to save time.

`/startdraft <list of 8 players>` lets an admin user create a new draft, which copies and populates the spreadsheet with currently-legal cards and the players' names and ids

`/update-scryfall` gets the latest bulk data dump from scryfall, which is somewhat large. Needed before /startdraft only if there's been any new sets released since the last update-scryfall

`/in and /out` commands let players join and leave a queue for the next draft (only in a signup channel).

`/queue` lists the currently signed up players, along with how long it's been since they signed up 

### Requirements

* Java 17+
* Gradle

### Setup

Set up your discord bot in the discord api: https://jda.wiki/using-jda/getting-started/

It needs privileged gateway intents: Message Content Intent, Read Messages/View Channels, Manage Messages, Pin Messages

To join it to your server, be on https://discord.com/developers/applications/, go to OAuth2 -> URL Generator, pick "bot" and "applications.commands" and then visit that url.


Copy .env.template to .env and populate it with appropriate config values.

DISCORD_BOT_TOKEN: from the "Bot" page on the left of the discord link above

OWNER_TAG: your discord username with # and numbers at the end

ADMIN_ROLE_ID: on your server you should have an admin role for people who can start drafts. Right click that role and get its ID to put here

PROD: set this to true for your prod environment and false elsewhere. if it's false it lets you start a draft with multiples of the same user.

LOWEST_OTHER_ROLE: The bot creates a role for the players in each draft. Set this value to the name of the role in your roles list you want the draft roles to be created underneath. (i.e. immediately following in the roles list)

ACTIVE_DRAFTS_CATEGORY_ID: The bot makes a channel for each draft. Make a channel category for those drafts on your server and put its ID here.

SIGNUPS_CATEGORY_ID: The bot treats any channels under this category as signup channels, where players can use the /in and /out commands to queue for the next draft

Set up a google cloud project and create auth credentials for it as a desktop app: https://developers.google.com/sheets/api/quickstart/java

You also need to give it Google Drive permissions

Save the credentials json to /gsheet_credentials.json

Run it locally and you should get a browser popup to auth you. This will generate tokens/ (which you then need to copy to prod if you have a separate prod).

### Running it
`gradle run` to run it locally

`gradle assemble` builds a jar file for you to copy to your prod server

`./runIt.sh` to run it on prod


