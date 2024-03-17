# BHBotUnity

## Important Note March 17 2024
I've lost interest in the game and I am no longer updating the bot. The code logic should be ok, however all the cues need review/update to make this bot work correctly. I am making the code public just in case someone wants to pick the work where I left it.


## What is BHBotUnity?
BHBotUnity is a program that automates the [Bit Heroes](http://www.kongregate.com/games/juppiomenz/bit-heroes) game. It is a fork of my original [BHBot](https://github.com/ilpersi/BHBot) that supports the Unity engine release in 2021.
It is a non intrusive program that works by opening up a Browser window and controls the game by taking a screenshot every few seconds, detecting cues from the screenshot and
then simulating mouse clicks. BHBotUnity is a good example of how to create such bots to automate flash games or other browser games
(it can be easily adjusted to play other games).

Once configured and running the bot is designed to run unattended indefinitely.

## Features
This bot will automatically farm items, familiars and schematics running all current content:
* Dungeons
* World Bosses
* Raids
* Gauntlet / Trials
* PvP / GvG
* Expedition / Invasion

The level/difficulty for each activity can be defined in the settings file. The bot can also:

* Bribe specific familiars with gems
* Revive fallen party members
* Save shrines for use before the boss in Trials and Raids
* Switch runes based on activity
* Notify you on how it is doing and if any issue is present on different devices through [Pushover](https://github.com/ilpersi/BHBot/wiki/Pushover-integration-Documentation) or [Discord](https://github.com/ilpersi/BHBot/wiki/Discord-integration-Documentation)
* Use consumables to keep boosts running unattended
* Strip items for PvP/GvG
* Solo certain zones for bounty requirements
* Collect completed bounties
* Collect fishing baits
* Claim all weekly rewards
* Screenshot and close PMs
* Open skeleton chests
* Handle incomplete teams

If the bot detects a "Disconnected" dialog, it will pause its execution for an hour giving the user a chance to play manually.
Disconnects are usually result of another instance logging into the game. This is why bot pauses in case it detects it.

## Download & First time setup
As the cues require updates, there is no a packaged release of the bot. No support is provided and you can have a look at the old BHBot documentation to get an idea.

## Important

- While bot is running, do not interfere with the Chromium window that the bot has open via chromedriver. That means don't open menus and click around since that may confuse the bot and it could fail to do its tasks (which could lead to crashing it).
- If you want to continue using your computer while running the bot use, the 'hide' command to minimize the window. The bot clicks on certain buttons and cues and expects certain thing to pop up, and if they don't (due to user interaction), then it will fail to function properly. 
- If you are running the bot in Windows, you may want to run it under a separate account in order for it to not interfere with your work.

## Commands
Here is a list of most common commands used with the bot (must be typed in the console window, or, if you use web interface, in the
command input box):

- `do dungeon|expedition|gauntlet|gvg|pvp|raid|trials`: force the bot to perform a dungeon . Example: "do raid". Used for debugging purposes more or less (bot will automatically attempt dungeons).
- `hide`: hides Chromium window.
- `pause [mins]`: pauses bot's execution. Useful when you want to play the game yourself (either from the same Chromium window, or by starting another Chromium window). The bots is paused untill are resue command is issued or, if specified, for a number of minutes equal to _mins_
- `plan <plan_name>`: if you have different configurations you can use this command to swith between them. BHBot will look for file named <plan_name.ini> in the plans/ subfolder
- `pomessage [message]`: use this command to verify that the Pushover integration is correctly configured. Message parameter is optional and if not specified, a standard messabe will be sent.
- `print`: Using this command, you can print different informations regarding the bot
  - `familiars`: output the full list of supported familiars in the encounter management system
  - `version`: output the version of BHBot. This is is useful when reporting a bug
- `readouts`: will reset readout timers (and hence immediately commence reading out resources).
- `reload`: will reload the 'settings.ini' file from disk and apply any changes on-the-fly.
- `resetini`: will reset your current ini file to the default content.
- `restart`: restarts the chromedriver (closes Chromium and opens a fresh Chromium window). Use only when something goes wrong (should restart automatically after some time in that case though).
- `resume`: resumes bot's execution.
- `set`: sets a setting line, just like from a 'settings.ini' file. Example: "set raids 1 3 100", or "set difficulty 70". Note that this overwritten setting is NOT saved to the 'settings.ini' file! Once you issue <reload> command, it will get discharged.
- `shot [prefix]`: takes a screenshot of the game and saves it to 'shot.png'. If a _prefix_ is specified it will be used instead of the default _shot_ one.
- `show`: shows Chromium window again after it has been hidden.
- `stop`: stops the bot execution (may take a few seconds. Once it is stopped, the console will close automatically).

  
## Authors
BHBot was originally created by [Betalord](https://github.com/Betalord). On 29th of September 2017 (the 1st year anniversary of the Bit Heroes game) he quit the game and released the bot to the public. In December 2018 [Fortigate](https://github.com/Fortigate) picked up the development and from March 2019 [ilpersi](https://github.com/ilpersi) joined him to make the bot what it is today. In June 2019 the project ownership was tranferred to ilpersi, granting autonomy moving forwards. Starting from January 2021, ilpersi developed a private Unity fork.
