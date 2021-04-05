package com.github.ilpersi.BHBot;

public class AutoShrineManager {
    private final BHBot bot;

    // this variables are used to store the current status of the settings
    boolean ignoreBoss;
    boolean ignoreShrines;
    private boolean initialized;

    AutoShrineManager (BHBot bot, boolean skipInitialization) {
        this.bot = bot;

        if (skipInitialization) {
            this.initialized = true;
        }
    }

    void initialize() {
        if (!initialized) {
            BHBot.logger.info("Initializing autoShrine to make sure it is disabled");
            if (!updateShrineSettings(false, false)) {
                BHBot.logger.error("It was not possible to perform the autoShrine start-up check!");
            }
            initialized = true;
        }
    }

    boolean updateShrineSettings(boolean ignoreBoss, boolean ignoreShrines) {

        // We don't need to change settings as they are already as required
        if (initialized && ignoreBoss == this.ignoreBoss && ignoreShrines == this.ignoreShrines) {
            return true;
        }

        final int CHECK_DELAY = Misc.Durations.SECOND;

        if (bot.dungeon.settings.openSettings(Misc.Durations.SECOND * 5)) {
            if (!initialized || ignoreBoss != this.ignoreBoss) {
                Bounds ignoreBossBounds =  Bounds.fromWidthHeight(165, 325, 55, 50);
                MarvinSegment ignoreBossCheck = MarvinSegment.fromCue(BHBot.cues.get("IgnoreCheck"), 0, ignoreBossBounds, bot.browser);

                if (ignoreBoss && ignoreBossCheck == null) {
                    BHBot.logger.debug("Enabling Ignore Boss");
                    while (ignoreBossCheck == null) {
                        bot.browser.clickInGame(194, 366);
                        ignoreBossCheck = MarvinSegment.fromCue(BHBot.cues.get("IgnoreCheck"), CHECK_DELAY, ignoreBossBounds, bot.browser);
                    }
                } else if (!ignoreBoss && ignoreBossCheck != null) {
                    BHBot.logger.debug("Disabling Ignore Boss");
                    ignoreBossCheck = MarvinSegment.fromCue(BHBot.cues.get("IgnoreEmptyBox"), 0, ignoreBossBounds, bot.browser);
                    while (ignoreBossCheck == null) {
                        bot.browser.clickInGame(194, 366);
                        ignoreBossCheck = MarvinSegment.fromCue(BHBot.cues.get("IgnoreEmptyBox"), CHECK_DELAY, ignoreBossBounds, bot.browser);
                    }
                }
                this.ignoreBoss = ignoreBoss;
            }

            // Sometimes if clicks are too close in time, the second one is ignored
            // thus we allow a small time delay between the two clicks
            Misc.sleep(100);

            if (!initialized || ignoreShrines != this.ignoreShrines) {
                Bounds ignoreShrineBounds = Bounds.fromWidthHeight(165, 370, 55, 50);
                MarvinSegment ignoreShrineCheck = MarvinSegment.fromCue(BHBot.cues.get("IgnoreCheck"), 0, ignoreShrineBounds, bot.browser);

                if (ignoreShrines && ignoreShrineCheck == null) {
                    BHBot.logger.debug("Enabling Ignore Shrine");
                    while (ignoreShrineCheck == null) {
                        bot.browser.clickInGame(194, 402);
                        ignoreShrineCheck = MarvinSegment.fromCue(BHBot.cues.get("IgnoreCheck"), CHECK_DELAY, ignoreShrineBounds, bot.browser);
                    }
                } else if (!ignoreShrines && ignoreShrineCheck != null) {
                    BHBot.logger.debug("Disabling Ignore Shrine");
                    ignoreShrineCheck = MarvinSegment.fromCue(BHBot.cues.get("IgnoreEmptyBox"), 0, ignoreShrineBounds, bot.browser);
                    while (ignoreShrineCheck == null) {
                        bot.browser.clickInGame(194, 402);
                        ignoreShrineCheck = MarvinSegment.fromCue(BHBot.cues.get("IgnoreEmptyBox"), CHECK_DELAY, ignoreShrineBounds, bot.browser);
                    }
                }
                this.ignoreShrines = ignoreShrines;
            }

            bot.browser.readScreen(Misc.Durations.SECOND);
            if (!bot.dungeon.settings.closeSettings()) {
                BHBot.logger.warn("It was impossible to close settings menu when updating autoShrine settings.");
            }

            return true;
        } else {
            BHBot.logger.warn("Impossible to open settings menu!");
            return false;
        }
    }

    /*private void resetAutoButton() {
        // We disable and re-enable the auto feature
        MarvinSegment autoSeg = MarvinSegment.fromCue(BHBot.cues.get("AutoOn"), 500, bot.browser);

        if (autoSeg != null) {
            bot.browser.clickOnSeg(autoSeg);

            autoSeg = MarvinSegment.fromCue(BHBot.cues.get("AutoOff"), 5000, bot.browser);
            if (autoSeg != null) {
                bot.browser.clickOnSeg(autoSeg);
            } else {
                BHBot.logger.error("Impossible to find Auto Off button");
                bot.notificationManager.sendErrorNotification("Auto Shrine Error", "Impossible to find Auto Off button");
            }

        } else {
            BHBot.logger.error("Impossible to find Auto On button!");
            bot.notificationManager.sendErrorNotification("Auto Shrine Error", "Impossible to find Auto On button");
        }
    }*/

    void processAutoShrine(long battleDelay) {
        MarvinSegment guildButtonSeg;

        /* All the flags are already disabled, this means that the current dungeon has already
        *  used the autoShrine feature */
        if (!ignoreBoss && !ignoreShrines) {
            return;
        }

        if ((bot.getState() == BHBot.State.Raid && bot.settings.autoShrine.contains("r")) ||
                (bot.getState() == BHBot.State.Trials && bot.settings.autoShrine.contains("t")) ||
                (bot.getState() == BHBot.State.Expedition && bot.settings.autoShrine.contains("e"))  ||
                (bot.getState() == BHBot.State.Dungeon && bot.settings.autoShrine.contains("d"))) {

            BHBot.logger.debug("Autoshrine battle delay: " + battleDelay);

            guildButtonSeg = MarvinSegment.fromCue(BHBot.cues.get("GuildButton"), bot.browser);

            String ignoreShrineMsg = "";
            boolean disableIgnoreShrines = false;
            if (guildButtonSeg != null && battleDelay >= bot.settings.battleDelay) {
                disableIgnoreShrines = true;
                ignoreShrineMsg = bot.settings.battleDelay + "s since last encounter, disabling ignore shrines";
            } else if (guildButtonSeg != null && bot.settings.positionDelay > 0 &&
                    bot.dungeon.positionChecker.isSamePosition(bot.browser.getImg(), bot.settings.positionDelay)) {
                disableIgnoreShrines = true;
                ignoreShrineMsg = "Position has not changed for " + bot.settings.positionDelay + " seconds, disabling ignore shrines";
            }

            if (disableIgnoreShrines) {
                bot.dungeon.setAutoOff(1000);

                BHBot.logger.autoshrine(ignoreShrineMsg);

                if (!updateShrineSettings(true, false)) {
                    BHBot.logger.error("Impossible to disable Ignore Shrines in handleAutoShrine!");
                    return;
                }

                //noinspection DuplicatedCode
                bot.browser.readScreen(100);

                bot.dungeon.setAutoOn(1000);

                BHBot.logger.autoshrine("Waiting " + bot.settings.shrineDelay + "s to disable ignore boss");
                long timeToWait = Misc.getTime() + (battleDelay * Misc.Durations.SECOND);

                if ((bot.getState() == BHBot.State.Raid && bot.settings.autoBossRune.containsKey("r")) || (bot.getState() == BHBot.State.Trials && bot.settings.autoBossRune.containsKey("t")) ||
                        (bot.getState() == BHBot.State.Expedition && bot.settings.autoBossRune.containsKey("e")) || (bot.getState() == BHBot.State.Dungeon && bot.settings.autoBossRune.containsKey("d"))) {

                    // TODO de-spagettify the boss rune feature
                     bot.dungeon.runeManager.handleMinorBossRunes();
                }

                while (Misc.getTime() < timeToWait) {
                    Misc.sleep(Misc.Durations.SECOND);
                }

                bot.dungeon.setAutoOff(1000);

                if (!updateShrineSettings(false, false)) {
                    BHBot.logger.error("Impossible to disable Ignore Boss in handleAutoShrine!");
                    return;
                }

                //noinspection DuplicatedCode,DuplicatedCode
                bot.browser.readScreen(100);

                bot.dungeon.setAutoOn(1000);

                bot.scheduler.resetIdleTime(true);
            }
        }
    }
}
