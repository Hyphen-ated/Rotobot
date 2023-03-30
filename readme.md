## Setup

Set up your discord bot in the discord api: https://jda.wiki/using-jda/getting-started/

It needs privileged gateway intents: Message Content Intent, Read Messages/View Channels, Manage Messages

To join it to your server, be on https://discord.com/developers/applications/, go to OAuth2 -> URL Generator, pick "bot" and "applications.commands" and then visit that url.


Copy .env.template to .env and populate it with appropriate config values.

DISCORD_BOT_TOKEN: from the "Bot" page on the left of the discord link above

OWNER_TAG: your discord username with # and numbers at the end

ADMIN_ROLE_ID: on your server you should have an admin role for people who can start drafts. Right click that role and get its ID to put here

PROD: set this to true for your prod environment and false elsewhere. if it's false it lets you start a draft with multiples of the same user.

LOWEST_OTHER_ROLE: The bot creates a role for the players in each draft. Set this value to the name of the role in your roles list you want the draft roles to be created underneath. (i.e. immediately following in the roles list)

ACTIVE_DRAFTS_CATEGORY_ID: The bot makes a channel for each draft. Make a channel category for those drafts on your server and put its ID here.

Set up a google cloud project and create auth credentials for it as a desktop app: https://developers.google.com/sheets/api/quickstart/java

You also need to give it Google Drive permissions

Save the credentials json to /gsheet_credentials.json

Run it locally and you should get a browser popup to auth you. This will generate tokens/ which you then need to copy to prod.


