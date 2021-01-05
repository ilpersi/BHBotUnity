package com.github.ilpersi.BHBot;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

public class BlockerThread implements Runnable {
    BHBot bot;

    BlockerThread(BHBot bot) {
        this.bot = bot;
    }

    @Override
    public void run() {
        MarvinSegment seg;

        // We initialize the counter HasMap using the state as key
        for (BHBot.State state : BHBot.State.values()) {
            bot.dungeon.counters.put(state, new DungeonCounter(0, 0));
        }

        while (!bot.finished && bot.running) {
            try {
                bot.scheduler.process();
                if (bot.scheduler.isPaused()) {
                    Misc.sleep(500);
                    continue;
                }

                if (bot.finished || (!bot.running && BHBot.State.Main.equals(bot.getState()))) break;

                // If the current scheduling is no longer valid, as soon as we get in state Main we break so that the
                // Main Thread can switch to a new valid scheduling without issues
                if (bot.currentScheduling != null && !bot.currentScheduling.isActive() && BHBot.State.Main.equals(bot.getState())) {
                    BHBot.logger.debug("Inactive scheduling detected in BlockerThread.");
                    break;
                }

                // We wait for the cues to be loaded and for the browser to be working!
                if (BHBot.cues.size() == 0 || bot.browser.getImg() == null) {
                    Misc.sleep(Misc.Durations.SECOND);
                    continue;
                }

                bot.browser.readScreen();

                bot.notificationManager.sendAliveNotification();

                seg = MarvinSegment.fromCue("Selector", bot.browser);
                if (seg != null) {
                    String engineName = bot.settings.useUnityEngine ? "Unity" : "Flash";

                    String selectCueName = bot.settings.useUnityEngine ? "OrangeSelect" : "LightBlueSelect";

                    // We choose the engine based on the settings
                    seg = MarvinSegment.fromCue(selectCueName, bot.browser);

                    // Sometime the selector is there, but the select button is not so we check that
                    if (seg != null) {
                        BHBot.logger.info("Version selector detected. Choosing " + engineName + "...");
                        bot.browser.closePopupSecurely(BHBot.cues.get("Selector"), BHBot.cues.get(selectCueName));
                    }
                    continue;
                }

                seg = MarvinSegment.fromCue(BHBot.cues.get("UnableToConnect"), bot.browser);
                if (seg != null) {
                    BHBot.logger.info("'Unable to connect' dialog detected. Reconnecting...");
                    //noinspection DuplicatedCode
                    seg = MarvinSegment.fromCue(BHBot.cues.get("Reconnect"), 5 * Misc.Durations.SECOND, bot.browser);
                    bot.browser.clickOnSeg(seg);
                    bot.browser.readScreen(Misc.Durations.SECOND);
                    bot.setState(BHBot.State.Loading);
                    continue;
                }

                // check for "Bit Heroes is currently down for maintenance. Please check back shortly!" window:
                seg = MarvinSegment.fromCue(BHBot.cues.get("Maintenance"), bot.browser);
                if (seg != null) {
                    seg = MarvinSegment.fromCue(BHBot.cues.get("Reconnect"), 5 * Misc.Durations.SECOND, bot.browser);
                    bot.browser.clickOnSeg(seg);
                    BHBot.logger.info("Maintenance dialog dismissed.");
                    bot.browser.readScreen(Misc.Durations.SECOND);
                    bot.setState(BHBot.State.Loading);
                    continue;
                }

                // check for "You have been disconnected" dialog:
                MarvinSegment uhoh = MarvinSegment.fromCue(BHBot.cues.get("UhOh"), bot.browser);
                MarvinSegment dc = MarvinSegment.fromCue(BHBot.cues.get("Disconnected"), bot.browser);
                if (uhoh != null && dc != null) {
                    if (bot.scheduler.isUserInteracting || bot.scheduler.dismissReconnectOnNextIteration) {
                        bot.scheduler.isUserInteracting = false;
                        bot.scheduler.dismissReconnectOnNextIteration = false;
                        seg = MarvinSegment.fromCue(BHBot.cues.get("Reconnect"), 5 * Misc.Durations.SECOND, bot.browser);
                        bot.browser.clickOnSeg(seg);
                        BHBot.logger.info("Disconnected dialog dismissed (reconnecting).");
                        bot.browser.readScreen(Misc.Durations.SECOND);
                    } else {
                        bot.scheduler.isUserInteracting = true;
                        // probably user has logged in, that's why we got disconnected. Lets leave him alone for some time and then resume!
                        BHBot.logger.info("Disconnect has been detected. Probably due to user interaction. Sleeping for " + Misc.millisToHumanForm((long) bot.settings.reconnectTimer * Misc.Durations.MINUTE) + "...");
                        bot.scheduler.pause(bot.settings.reconnectTimer * Misc.Durations.MINUTE);
                    }
                    bot.setState(BHBot.State.Loading);
                    continue;
                }

                // TODO ensure this field is properly synchronized
                bot.scheduler.dismissReconnectOnNextIteration = false; // must be done after checking for "Disconnected" dialog!

                // check for "There is a new update required to play" and click on "Reload" button:
                seg = MarvinSegment.fromCue(BHBot.cues.get("Reload"), bot.browser);
                if (seg != null) {
                    bot.browser.clickOnSeg(seg);
                    BHBot.logger.info("Update dialog dismissed.");
                    bot.browser.readScreen(Misc.Durations.SECOND);
                    bot.setState(BHBot.State.Loading);
                    continue;
                }

                // check for "Are you still there?" popup:
                seg = MarvinSegment.fromCue(BHBot.cues.get("AreYouThere"), bot.browser);
                if (seg != null) {
                    bot.scheduler.restoreIdleTime();
                    seg = MarvinSegment.fromCue(BHBot.cues.get("Yes"), 2 * Misc.Durations.SECOND, bot.browser);
                    if (seg != null) {
                        bot.browser.clickOnSeg(seg);
                    }
                    else {
                        BHBot.logger.info("Problem: 'Are you still there?' popup detected, but 'Yes' button not detected. Ignoring...");
                        bot.browser.readScreen(Misc.Durations.SECOND);
                        continue;
                    }
                    bot.browser.readScreen(Misc.Durations.SECOND);
                    continue;
                }
                seg = MarvinSegment.fromCue(BHBot.cues.get("GearCheck"), bot.browser);
                if (seg != null) {
                    seg = MarvinSegment.fromCue(BHBot.cues.get("Close"), 2 * Misc.Durations.SECOND, bot.browser);
                    bot.browser.clickOnSeg(seg);
                    BHBot.logger.info("Gear check dismissed.");
                    bot.browser.readScreen(500);
                    continue;
                }

                if (!handlePM()) {
                    bot.restart(true, bot.browser.isDoNotShareUrl()); //*** problem: after a call to this, it will return to the main loop. It should call "continue" inside the main loop or else there could be other exceptions!
                    continue;
                }

                if (!handleWeeklyRewards()) {
                    bot.restart(true, false);
                    continue;
                }

                // check for daily rewards popup:
                seg = MarvinSegment.fromCue(BHBot.cues.get("DailyRewards"), bot.browser);
                if (seg != null) {
                    seg = MarvinSegment.fromCue(BHBot.cues.get("Claim"), 5 * Misc.Durations.SECOND, bot.browser);
                    if (seg != null) {
                        if ((bot.settings.screenshots.contains("d"))) {
                            BufferedImage reward = bot.browser.getImg().getSubimage(131, 136, 513, 283);
                            Misc.saveScreen("daily_reward", "rewards", reward);
                        }
                        bot.browser.clickOnSeg(seg);
                    } else {
                        BHBot.logger.error("Problem: 'Daily reward' popup detected, however could not detect the 'claim' button. Restarting...");
                        bot.restart(true, false);
                        continue; // may happen every while, rarely though
                    }

                    bot.browser.readScreen(5 * Misc.Durations.SECOND);
                    seg = MarvinSegment.fromCue(BHBot.cues.get("Items"), Misc.Durations.SECOND, bot.browser);
                    if (seg == null) {
                        // we must terminate this thread... something happened that should not (unexpected). We must restart the thread!
                        BHBot.logger.error("Error: there is no 'Items' dialog open upon clicking on the 'Claim' button. Restarting...");
                        bot.restart(true, false);
                        continue;
                    }
                    seg = MarvinSegment.fromCue(BHBot.cues.get("X"), bot.browser);
                    bot.browser.clickOnSeg(seg);
                    BHBot.logger.info("Daily reward claimed successfully.");
                    bot.browser.readScreen(2 * Misc.Durations.SECOND);

                    continue;
                }

                // check for "recently disconnected" popup:
                seg = MarvinSegment.fromCue(BHBot.cues.get("RecentlyDisconnected"), bot.browser);
                if (seg != null) {
                    seg = MarvinSegment.fromCue(BHBot.cues.get("YesGreen"), 2 * Misc.Durations.SECOND, bot.browser);
                    if (seg == null) {
                        BHBot.logger.error("Error: detected 'recently disconnected' popup but could not find 'Yes' button. Restarting...");
                        bot.restart(true, false);
                        continue;
                    }

                    bot.browser.clickOnSeg(seg);
                    if (bot.getState() == BHBot.State.Main || bot.getState() == BHBot.State.Loading) {
                        // we set this when we are not sure of what type of dungeon we are doing
                        bot.setState(bot.getLastJoinedState());
                    } else {
                        BHBot.logger.debug("RecentlyDisconnected status is: " + bot.getState());
                    }

                    BHBot.logger.info("'You were recently in a dungeon' dialog detected and confirmed. Resuming dungeon...");

                    // So that the screen reloads correctly
                    Misc.sleep(Misc.Durations.SECOND * 2);

                    /* We wait for the first of three possible conditions
                        1) Setting cue has been found
                        2) Guild cue has been found
                        3) We timed out after 5 minutes
                     */
                    long reconnectTimeout = Misc.getTime() + (Misc.Durations.MINUTE * 5);
                    MarvinSegment settingSeg = MarvinSegment.fromCue("SettingsGear", 500, bot.browser);
                    MarvinSegment guildSeg = MarvinSegment.fromCue(BHBot.cues.get("GuildButton"), 500, bot.browser);

                    while (guildSeg == null && settingSeg == null && Misc.getTime() <= reconnectTimeout) {
                        bot.browser.readScreen(500);
                        settingSeg = MarvinSegment.fromCue("SettingsGear", bot.browser);
                        guildSeg = MarvinSegment.fromCue(BHBot.cues.get("GuildButton"), bot.browser);
                    }

                    if (guildSeg == null && settingSeg == null) {
                        BHBot.logger.warn("'You were recently in a dungeon' reconnection timed-out");
                        bot.saveGameScreen("recently-disconnected-timeout", "errors");
                    }

                    bot.dungeon.shrineManager.updateShrineSettings(false, false); //in case we are stuck in a dungeon lets enable shrines/boss
                    continue;
                }

                // check for "News" popup:
                seg = MarvinSegment.fromCue(BHBot.cues.get("News"), bot.browser);
                if (seg != null) {
                    Cue CloseWithBounds = bot.settings.useUnityEngine ? BHBot.cues.get("NewsClose") : new Cue(BHBot.cues.get("Close"), Bounds.fromWidthHeight(425, 440, 100, 45));
                    seg = MarvinSegment.fromCue(CloseWithBounds, 2 * Misc.Durations.SECOND, bot.browser);
                    bot.browser.clickOnSeg(seg);
                    BHBot.logger.info("News popup dismissed.");
                    bot.browser.readScreen(2 * Misc.Durations.SECOND);

                    continue;
                }

                // Sometimes the game is presenting fishing baits at login
                if (!BHBot.State.FishingBaits.equals(bot.getState())) {
                    seg = MarvinSegment.fromCue("Fishing_Bait", 0, Bounds.fromWidthHeight(455, 190, 85, 90), bot.browser);
                    if (seg != null) {
                        BHBot.logger.debug("Fishing baits detected during login...");
                        if ((bot.settings.screenshots.contains("a"))) {
                            bot.saveGameScreen("fishing-baits", "fishing");
                        }
                        seg = MarvinSegment.fromCue(BHBot.cues.get("X"), 2 * Misc.Durations.SECOND, Bounds.fromWidthHeight(561, 123, 58, 58), bot.browser);
                        bot.browser.clickOnSeg(seg);
                        BHBot.logger.info("Correctly collected fishing baits.");
                        bot.dungeon.timeLastFishingBaitsCheck = Misc.getTime();
                        continue;
                    }
                }
            } catch (Exception e) {
                if (bot.excManager.manageException(e)) continue;
                bot.scheduler.resetIdleTime();
                continue;
            }

            bot.excManager.numConsecutiveException = 0; // reset exception counter
            bot.scheduler.restoreIdleTime(); // revert changes to idle time
            if (bot.finished || (!bot.running && BHBot.State.Main.equals(bot.getState()))) break; // skip sleeping if finished flag has been set or bot is not running!

            BHBot.logger.trace("Bocker Thread Sleeping");
            Misc.sleep(250);
        }

        BHBot.logger.info("Blocker thread stopped.");
    }

    /**
     * Will detect and handle (close) in-game private message (from the current screen capture). Returns true in case PM has been handled.
     */
    private boolean handlePM() {
        if (MarvinSegment.fromCue(BHBot.cues.get("InGamePM"), bot.browser) != null) {
            MarvinSegment seg = MarvinSegment.fromCue(BHBot.cues.get("X"), 5 * Misc.Durations.SECOND, bot.browser);
            if (seg == null) {
                BHBot.logger.error("Error: in-game PM window detected, but no close button found. Restarting...");
                return false;
            }

            try {
                String pmFileName = bot.saveGameScreen("pm", "pm");
                bot.notificationManager.sendPMNotification(pmFileName);
                bot.browser.clickOnSeg(seg);
            } catch (Exception e) {
                // ignore it
            }
        }
        return true;
    }

    private boolean handleWeeklyRewards() {
        // check for weekly rewards popup
        // (note that several, 2 or even 3 such popups may open one after another)
        MarvinSegment seg;
        if (bot.getState() == BHBot.State.Loading || bot.getState() == BHBot.State.Main) {
            bot.browser.readScreen();

            HashMap<String, Cue> weeklyRewards = new HashMap<>();
            weeklyRewards.put("PVP", BHBot.cues.get("PVP_Rewards"));
            weeklyRewards.put("Trials", BHBot.cues.get("Trials_Rewards"));
            weeklyRewards.put("Trials-XL", BHBot.cues.get("Trials_Rewards_Large"));
            weeklyRewards.put("Gauntlet", BHBot.cues.get("Gauntlet_Rewards"));
            weeklyRewards.put("Gauntlet-XL", BHBot.cues.get("Gauntlet_Rewards_Large"));
            weeklyRewards.put("Fishing", BHBot.cues.get("Fishing_Rewards"));
            weeklyRewards.put("Invasion", BHBot.cues.get("Invasion_Rewards"));
            weeklyRewards.put("Expedition", BHBot.cues.get("Expedition_Rewards"));
            weeklyRewards.put("GVG", BHBot.cues.get("GVG_Rewards"));

            for (Map.Entry<String, Cue> weeklyRewardEntry : weeklyRewards.entrySet()) {
                seg = MarvinSegment.fromCue(weeklyRewardEntry.getValue(), bot.browser);
                if (seg != null) {
                    BufferedImage reward = bot.browser.getImg();
                    seg = MarvinSegment.fromCue("X", 5 * Misc.Durations.SECOND, bot.browser);
                    if (seg != null) bot.browser.clickOnSeg(seg);
                    else {
                        BHBot.logger.error(weeklyRewardEntry.getKey() + " reward popup detected, however could not detect the X button. Restarting...");
                        return false;
                    }

                    BHBot.logger.info(weeklyRewardEntry.getKey() + " reward claimed successfully.");
                    if ((bot.settings.screenshots.contains("w"))) {
                        Misc.saveScreen(weeklyRewardEntry.getKey().toLowerCase() + "_reward", "rewards", reward);
                    }
                }
            }
        }

        return true;
    }
}
