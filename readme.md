##Setup
Set up your discord bot in the discord api: https://jda.wiki/using-jda/getting-started/

To join it to your server, be on https://discord.com/developers/applications/, go to OAuth2 -> URL Generator, pick "bot" and "applications.commands" and then visit that url.


Copy .env.template to .env, put in a discord bot token and your admin discord tag.

Set up a google cloud project and create auth credentials for it as a desktop app: https://developers.google.com/sheets/api/quickstart/java

Save the credentials json to /gsheet_credentials.json

Run it locally and you should get a browser popup to auth you. This will generate tokens/ which you then need to copy to prod.


