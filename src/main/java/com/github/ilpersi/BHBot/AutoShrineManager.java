package com.github.ilpersi.BHBot;

import java.awt.image.BufferedImage;

public class AutoShrineManager {
    private final BHBotUnity bot;

    // this variables are used to store the current status of the settings
    private Boolean ignoreBoss;
    private Boolean ignoreShrines;
    private boolean initialized;
    private boolean usedInAdventure;

    // Used to log in/out timestamp for encounters
    private long outOfEncounterTimestamp;
    private long inEncounterTimestamp;

    AutoShrineManager(BHBotUnity bot, boolean skipInitialization) {
        this.bot = bot;
        this.initialized = skipInitialization;
        this.usedInAdventure = false;
    }

    void initialize() {
        if (!initialized) {
            outOfEncounterTimestamp = 0;
            inEncounterTimestamp = 0;

            BHBotUnity.logger.info("Initializing autoShrine to make sure it is disabled");
            if (!updateShrineSettings(false, false)) {
                BHBotUnity.logger.error("It was not possible to perform the autoShrine start-up check!");
            }
            initialized = true;
        }
    }

    boolean updateShrineSettings(Boolean ignoreBoss, Boolean ignoreShrines) {

        // We don't need to change settings as they are already as required
        if (initialized && ignoreBoss == this.ignoreBoss && ignoreShrines == this.ignoreShrines) {
            BHBotUnity.logger.debug("Skipping updateShrineSettings as no modification is required.");
            return true;
        }

        final int CHECK_DELAY = Misc.Durations.SECOND;

        if (bot.dungeon.settings.openSettings(Misc.Durations.SECOND * 5)) {

            // When the setting menu is initially opened it is bouncing so it may be possible that by the time openSettings returns, the menu goes downward and ignore checks
            // are not in the correct position. This should prevent that from happening
            bot.browser.readScreen(500);
            MarvinSegment.fromCue(BHBotUnity.cues.get("Settings"), CHECK_DELAY * 2, bot.browser);

            if ((this.ignoreBoss == null) || (ignoreBoss != this.ignoreBoss)) {
                Bounds ignoreBossBounds = Bounds.fromWidthHeight(165, 325, 55, 50);
                BufferedImage tmpSettingsMenu = bot.browser.getImg();
                MarvinSegment ignoreBossCheck = MarvinSegment.fromCue(BHBotUnity.cues.get("IgnoreCheck"), 0, ignoreBossBounds, bot.browser);

                BHBotUnity.logger.debug("ignoreBossCheck: " + (ignoreBossCheck==null ? "null" : "not null") + " ignoreBoss: " + ignoreBoss);

                if (ignoreBoss && ignoreBossCheck == null) {
                    BHBotUnity.logger.debug("Enabling Ignore Boss");
                    while (ignoreBossCheck == null) {
                        bot.browser.clickInGame(194, 366);
                        ignoreBossCheck = MarvinSegment.fromCue(BHBotUnity.cues.get("IgnoreCheck"), CHECK_DELAY, ignoreBossBounds, bot.browser);
                    }
                    this.ignoreBoss = true;
                } else if (!ignoreBoss && ignoreBossCheck != null) {
                    BHBotUnity.logger.debug("Disabling Ignore Boss");

                    do {
                        bot.browser.clickOnSeg(ignoreBossCheck);
                        MarvinSegment.waitForNull(BHBotUnity.cues.get("IgnoreCheck"), CHECK_DELAY * 2, ignoreBossBounds, bot.browser);
                        ignoreBossCheck = MarvinSegment.fromCue(BHBotUnity.cues.get("IgnoreCheck"), 0, ignoreBossBounds, bot.browser);
                    } while (ignoreBossCheck != null);
                    this.ignoreBoss = false;
                } else {
                    Misc.saveScreen("ignoreBossCheck-check-tmp", "debug", BHBotUnity.includeMachineNameInScreenshots, tmpSettingsMenu);
                    bot.saveGameScreen("ignoreBossCheck-check", "debug");
                }
            } else {
                BHBotUnity.logger.debug("Skipping ignoreBoss. Initialized is " + initialized + " and this.ignoreBoss =" + this.ignoreBoss + " && ignoreBoss= " + ignoreBoss);
            }

            // Sometimes if clicks are too close in time, the second one is ignored
            // thus we allow a small time delay between the two clicks
            bot.browser.readScreen(500);

            if ((this.ignoreShrines == null) || ignoreShrines != this.ignoreShrines) {
                Bounds ignoreShrineBounds = Bounds.fromWidthHeight(165, 370, 55, 50);
                MarvinSegment ignoreShrineCheck = MarvinSegment.fromCue(BHBotUnity.cues.get("IgnoreCheck"), 0, ignoreShrineBounds, bot.browser);

                if (ignoreShrines && ignoreShrineCheck == null) {
                    BHBotUnity.logger.debug("Enabling Ignore Shrine");
                    while (ignoreShrineCheck == null) {
                        bot.browser.clickInGame(194, 402);
                        ignoreShrineCheck = MarvinSegment.fromCue(BHBotUnity.cues.get("IgnoreCheck"), CHECK_DELAY, ignoreShrineBounds, bot.browser);
                    }

                    this.ignoreShrines = true;
                } else if (!ignoreShrines && ignoreShrineCheck != null) {
                    BHBotUnity.logger.debug("Disabling Ignore Shrine");

                    do {
                        bot.browser.clickOnSeg(ignoreShrineCheck);
                        MarvinSegment.waitForNull(BHBotUnity.cues.get("IgnoreCheck"), CHECK_DELAY * 2, ignoreShrineBounds, bot.browser);
                        ignoreShrineCheck = MarvinSegment.fromCue(BHBotUnity.cues.get("IgnoreCheck"), 0, ignoreShrineBounds, bot.browser);

                    } while (ignoreShrineCheck != null);

                    this.ignoreShrines = false;
                }
            } else {
                BHBotUnity.logger.debug("Skipping ignoreShrines. Initialized is " + initialized + " and this.ignoreShrines =" + this.ignoreShrines + " && ignoreShrines= " + ignoreShrines);
            }

            bot.browser.readScreen(Misc.Durations.SECOND);
            if (!bot.dungeon.settings.closeSettings()) {
                BHBotUnity.logger.warn("It was impossible to close settings menu when updating autoShrine settings.");
            }

            return true;
        } else {
            BHBotUnity.logger.warn("Impossible to open settings menu!");
            return false;
        }
    }

    void processAutoShrine() {
        MarvinSegment guildButtonSeg;
        long battleDelay = outOfEncounterTimestamp - inEncounterTimestamp;

        if ((bot.getState() == BHBotUnity.State.Raid && bot.settings.autoShrine.contains("r")) ||
                (bot.getState() == BHBotUnity.State.Trials && bot.settings.autoShrine.contains("t")) ||
                (bot.getState() == BHBotUnity.State.Expedition && bot.settings.autoShrine.contains("e")) ||
                (bot.getState() == BHBotUnity.State.Dungeon && bot.settings.autoShrine.contains("d"))) {

            BHBotUnity.logger.debug("Autoshrine battle delay: " + battleDelay);

            if (!usedInAdventure) {
                guildButtonSeg = MarvinSegment.fromCue(BHBotUnity.cues.get("GuildButton"), bot.browser);

                String ignoreShrineMsg = "";
                boolean disableIgnoreShrines = false;

                if (guildButtonSeg != null && battleDelay >= bot.settings.battleDelay) {
                    disableIgnoreShrines = true;
                    ignoreShrineMsg = bot.settings.battleDelay + "s since last encounter, disabling ignore shrines";
                } else if (guildButtonSeg != null && bot.settings.positionDelay > 0
                        && bot.dungeon.positionChecker.isSamePosition(bot.browser.getImg(), bot.settings.positionDelay)) {
                    disableIgnoreShrines = true;
                    ignoreShrineMsg = "Position has not changed for " + bot.settings.positionDelay + " seconds, disabling ignore shrines";
                }

                if (disableIgnoreShrines) {
                    bot.dungeon.setAutoOff(1000);

                    BHBotUnity.logger.autoshrine(ignoreShrineMsg);

                    if (!updateShrineSettings(true, false)) {
                        BHBotUnity.logger.error("Impossible to disable Ignore Shrines in handleAutoShrine!");
                        return;
                    }

                    //noinspection DuplicatedCode
                    bot.browser.readScreen(100);

                    bot.dungeon.setAutoOn(1000);

                    BHBotUnity.logger.autoshrine("Waiting " + bot.settings.shrineDelay + "s to disable ignore boss");
                    long timeToWait = Misc.getTime() + (battleDelay * Misc.Durations.SECOND);

                    if ((bot.getState() == BHBotUnity.State.Raid && bot.settings.autoBossRune.containsKey("r")) || (bot.getState() == BHBotUnity.State.Trials && bot.settings.autoBossRune.containsKey("t")) ||
                            (bot.getState() == BHBotUnity.State.Expedition && bot.settings.autoBossRune.containsKey("e")) || (bot.getState() == BHBotUnity.State.Dungeon && bot.settings.autoBossRune.containsKey("d"))) {

                        // TODO de-spagettify the boss rune feature
                        bot.dungeon.runeManager.handleMinorBossRunes();
                    }

                    while (Misc.getTime() < timeToWait) {
                        Misc.sleep(Misc.Durations.SECOND);
                    }

                    bot.dungeon.setAutoOff(1000);

                    if (!updateShrineSettings(false, false)) {
                        BHBotUnity.logger.error("Impossible to disable Ignore Boss in handleAutoShrine!");
                        return;
                    }

                    //noinspection DuplicatedCode,DuplicatedCode
                    bot.browser.readScreen(100);

                    bot.dungeon.setAutoOn(1000);

                    bot.scheduler.resetIdleTime(true);

                    usedInAdventure = true;
                }
            }
        }
    }

    void resetUsedInAdventure() {
        outOfEncounterTimestamp = 0;
        inEncounterTimestamp = 0;
        usedInAdventure = false;
    }

    public long getOutOfEncounterTimestamp() {
        return outOfEncounterTimestamp;
    }

    public void setOutOfEncounterTimestamp(long outOfEncounterTimestamp) {
        this.outOfEncounterTimestamp = outOfEncounterTimestamp;
    }

    public long getInEncounterTimestamp() {
        return inEncounterTimestamp;
    }

    public void setInEncounterTimestamp(long inEncounterTimestamp) {
        this.inEncounterTimestamp = inEncounterTimestamp;
    }
}
