package com.github.ilpersi.BHBot;

import com.google.common.collect.Maps;
import org.openqa.selenium.Point;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.Comparator.comparing;

public class AdventureThread implements Runnable {

    private int globalShards;
    private int globalBadges;
    private int globalEnergy;
    private int globalXeals;
    private int globalTickets;
    private int globalTokens;

    // z(?<zone>\d{1,2})d(?<dungeon>[1234])
    // 
    // Options: Case insensitive; Exact spacing; Dot doesn’t match line breaks; ^$ don’t match at line breaks; Default line breaks
    // 
    // Match the character “z” literally (case insensitive) «z»
    // Match the regex below and capture its match into a backreference named “zone” (also backreference number 1) «(?<zone>\d{1,2})»
    //    Match a single character that is a “digit” (ASCII 0–9 only) «\d{1,2}»
    //       Between one and 2 times, as many times as possible, giving back as needed (greedy) «{1,2}»
    // Match the character “d” literally (case insensitive) «d»
    // Match the regex below and capture its match into a backreference named “dungeon” (also backreference number 2) «(?<dungeon>[1234])»
    //    Match a single character from the list “1234” «[1234]»
    private final Pattern dungeonRegex = Pattern.compile("z(?<zone>\\d{1,2})d(?<dungeon>[1234])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    @SuppressWarnings("FieldCanBeLocal")
    private final long MAX_IDLE_TIME = 15 * Misc.Durations.MINUTE;

    //private int potionsUsed = 0;
    private boolean isAdventureStarted = false;
    private long activityStartTime;
    private boolean isInFight = true;
    private boolean specialDungeon; //d4 check for closing properly when no energy
    private String expeditionFailsafePortal = "";
    private int expeditionFailsafeDifficulty = 0;
    private boolean rerunCurrentActivity = false;
    private boolean debugBootWarning = false; // Used together with debugBoot to make sure the user is warned one time only.

    private long kongMotionBugNexCheck;

    // Generic counters HashMap
    HashMap<BHBotUnity.State, AdventureCounter> counters = new HashMap<>();

    private long ENERGY_CHECK_INTERVAL = 5 * Misc.Durations.MINUTE;
    private long XEALS_CHECK_INTERVAL = 5 * Misc.Durations.MINUTE;
    private long TICKETS_CHECK_INTERVAL = 5 * Misc.Durations.MINUTE;
    private long TOKENS_CHECK_INTERVAL = 5 * Misc.Durations.MINUTE;
    @SuppressWarnings("FieldCanBeLocal")
    private final long SETTINGS_CHECK_INTERVAL = Misc.Durations.HOUR;
    @SuppressWarnings("FieldCanBeLocal")
    private final long BONUS_CHECK_INTERVAL = 5 * Misc.Durations.MINUTE;
    private long BADGES_CHECK_INTERVAL = 5 * Misc.Durations.MINUTE;

    private long timeLastEnergyCheck = 0; // when did we check for Energy the last time?
    private long timeLastXealsCheck = 0; // when did we check for Xeals the last time?
    private long timeLastShardsCheck = 0; // when did we check for Shards the last time?
    private long timeLastTicketsCheck = 0; // when did we check for Tickets the last time?
    private long timeLastTrialsTokensCheck = 0; // when did we check for trials Tokens the last time?
    private long timeLastGauntletTokensCheck = 0; // when did we check for gauntlet Tokens the last time?
    private long timeLastExpBadgesCheck = 0; // when did we check for badges the last time?
    private long timeLastInvBadgesCheck = 0; // when did we check for badges the last time?
    private long timeLastGVGBadgesCheck = 0; // when did we check for badges the last time?
    private long timeLastSettingsCheck = Misc.getTime(); // when did we check for settings the last time?
    private long timeLastBountyCheck = 0; // when did we check for bounties the last time?
    private long timeLastBonusCheck = 0; // when did we check for bonuses (active consumables) the last time?
    long timeLastFishingBaitsCheck = 0; // when did we check for fishing baits the last time?
    private long timeLastFishingCheck = 0; // when did we check for fishing last time?
    private long timeLastDailyGem = 0; // when did we check for daily gem screenshot last time?
    private long timeLastWeeklyGem = Misc.getTime(); // when did we check for weekly gem screenshot last time?

    BHBotUnity bot;
    AutoShrineManager shrineManager;
    AutoReviveManager reviveManager;
    AutoRuneManager runeManager;
    EncounterManager encounterManager;
    AdventurePositionChecker positionChecker;
    SettingsManager settings;

    DungeonSignature dungSignatures;

    private Iterator<String> activitysIterator;

    // Weekly Sunday screenshots cache
    HashMap<String, Boolean> sundayScreenshots = new HashMap<>();

    int adventureSpeed;

    // Used when reading numbers from screen
    private record NumberInfo(String value, int xPosition) {
        @Override
        public String toString() {
            return value;}
    }

    AdventureThread(BHBotUnity bot) {
        this.bot = bot;

        activitysIterator = bot.settings.activitiesEnabled.iterator();
        reviveManager = new AutoReviveManager(bot);
        dungSignatures = new DungeonSignature(this.bot);

        adventureSpeed = 1;
    }

    static void printFamiliars() {

        Set<String> uniqueFamiliars = new TreeSet<>();

        for (Map.Entry<String, EncounterManager.FamiliarDetails> familiarEntry: EncounterManager.famMD5Table.entrySet()) {
            uniqueFamiliars.add(familiarEntry.getValue().name());
        }

        StringBuilder familiarString = new StringBuilder();
        int currentFamiliar = 1;

        for (String familiar : uniqueFamiliars) {
            if (familiarString.length() > 0) familiarString.append(", ");
            if (currentFamiliar % 5 == 0) familiarString.append("\n");
            familiarString.append(familiar);
            currentFamiliar++;
        }

        BHBotUnity.logger.info(familiarString.toString());
    }

    void restart() {
        restart(true); // assume emergency restart
    }

    void restart(boolean emergency) {
        bot.restart(emergency, false); // assume emergency restart
    }

    public void run() {
        BHBotUnity.logger.info("Bot started successfully!");

        restart(false);

        while (!bot.finished && bot.running) {
            bot.scheduler.backupIdleTime();
            try {
                bot.scheduler.process();
                if (bot.scheduler.isPaused()) {
                    Misc.sleep(500);
                    continue;
                }

                if (bot.finished || (!bot.running && BHBotUnity.State.Main.equals(bot.getState()))) break;

                // If the current scheduling is no longer valid, as soon as we get in state Main we break so that the
                // Main Thread can switch to a new valid scheduling without interrupting adventures
                if (bot.currentScheduling != null && !bot.currentScheduling.isActive() && BHBotUnity.State.Main.equals(bot.getState())) {
                    BHBotUnity.logger.debug("Inactive scheduling detected in DungeonThread.");
                    break;
                }

                if (Misc.getTime() - bot.scheduler.getIdleTime() > MAX_IDLE_TIME) {
                    BHBotUnity.logger.warn("Idle time exceeded... perhaps caught in a loop? Restarting... (state=" + bot.getState() + ")");
                    bot.saveGameScreen("idle-timeout-error", "errors");

                    // Safety measure to avoid being stuck forever in dungeons
                    if (bot.getState() != BHBotUnity.State.Main && bot.getState() != BHBotUnity.State.Loading) {
                        if (!bot.settings.autoRuneDefault.isEmpty()) {
                            BHBotUnity.logger.info("Re-validating autoRunes");
                            if (!runeManager.detectEquippedMinorRunes(true, true)) {
                                BHBotUnity.logger.error("It was not possible to verify the equipped runes!");
                            }
                        }
                    }

                    bot.notificationManager.sendErrorNotification("Idle timer exceeded", "Idle time exceeded while state = " + bot.getState());

                    restart();
                    continue;
                }
                bot.scheduler.resetIdleTime();

                // bot.browser.moveMouseAway(); // just in case. Sometimes we weren't able to claim daily reward because mouse was in center and popup window obfuscated the claim button (see screenshot of that error!)
                MarvinSegment seg;
                bot.browser.readScreen();

                //Dungeon crash failsafe, this can happen if you crash and reconnect quickly, then get placed back in the dungeon with no reconnect dialogue
                if (bot.getState() == BHBotUnity.State.Loading) {
                    MarvinSegment autoOn = MarvinSegment.fromCue(BHBotUnity.cues.get("AutoOn"), bot.browser);
                    MarvinSegment autoOff = MarvinSegment.fromCue(BHBotUnity.cues.get("AutoOff"), bot.browser);
                    if (autoOn != null || autoOff != null) { //if we're in Loading state, with auto button visible, then we need to change state
                        bot.setState(bot.getLastJoinedState()); // we are not sure what type of dungeon we are doing
                        BHBotUnity.logger.warn("Possible dungeon crash, activating failsafe. Restore to last known status: " + bot.getState());
                        bot.saveGameScreen("dungeon-crash-failsafe", "errors");
                        shrineManager.resetUsedInAdventure();

                        // We only reset autoShrine if configurations require so
                        if (bot.settings.autoShrine.contains(bot.getState().getShortcut())) {
                            shrineManager.updateShrineSettings(false, false); //in case we are stuck in a dungeon lets enable shrines/boss
                        }
                        setAutoOff(Misc.Durations.SECOND);
                        bot.browser.readScreen();
                        setAutoOn(Misc.Durations.SECOND);
                        kongMotionBugNexCheck = Misc.getTime() + (Misc.Durations.MINUTE * 7);
                        continue;
                    }

                    // If you start in the fishing zone, you have the character dialog coming up
                    detectCharacterDialogAndHandleIt();
                }

                if (BHBotUnity.State.RerunRaid.equals(bot.getState())) {
                    // set up autoRune and autoShrine
                    handleAdventureConfiguration(BHBotUnity.State.Raid, false, null);
                    runeManager.reset();

                    // We change the state only after we performed all the configurations
                    bot.setState(BHBotUnity.State.Raid);
                    bot.setLastJoinedState(BHBotUnity.State.Raid);
                    BHBotUnity.logger.info("Raid rerun initiated!");
                    setAutoOn(Misc.Durations.SECOND);
                }

                // process dungeons of any kind (if we are in any):
                if (bot.getState() == BHBotUnity.State.Raid || bot.getState() == BHBotUnity.State.Trials || bot.getState() == BHBotUnity.State.Gauntlet || bot.getState() == BHBotUnity.State.Dungeon || bot.getState() == BHBotUnity.State.PVP || bot.getState() == BHBotUnity.State.GVG || bot.getState() == BHBotUnity.State.Invasion || bot.getState() == BHBotUnity.State.Expedition || bot.getState() == BHBotUnity.State.WorldBoss) {
                    processDungeon();
                    continue;
                }

                // check if we are in the main menu:
                seg = MarvinSegment.fromCue(BHBotUnity.cues.get("Main"), bot.browser);

                if (seg != null) {

                    /* The bot is now fully started, so based on the options we search the logs looking for the
                     * do_not_share url and if we find it, we save it for later usage
                     */
                    if (!bot.browser.isDoNotShareUrl() && bot.settings.useDoNotShareURL) {
                        bot.restart(false, true);
                        continue;
                    }

                    bot.setState(BHBotUnity.State.Main);

                    bot.notificationManager.sendStartUpnotification();

                    // weekly gem screenshot every Sunday and after or after a week the bot is running.
                    if (bot.settings.screenshots.contains("wg")) {
                        // The option is enabled and more than a week has passed
                        if ((Misc.getTime() - timeLastWeeklyGem) > Misc.Durations.WEEK) {
                            timeLastWeeklyGem = Misc.getTime();

                            BufferedImage gems = bot.browser.getImg().getSubimage(133, 16, 80, 14);
                            Misc.saveScreen("weekly-gems", "gems", BHBotUnity.includeMachineNameInScreenshots, gems);
                        } else {
                            // Less than a week has passed, we check if it is Sunday
                            if ("7".equals(new SimpleDateFormat("u").format(new Date()))) {
                                SimpleDateFormat sundayKeyFormat = new SimpleDateFormat("yyyyMMdd");
                                String sundayKey = sundayKeyFormat.format(new Date());

                                // if the date key is not there, it means that this Sunday we got no screenshot
                                if (!sundayScreenshots.getOrDefault(sundayKey, false)) {
                                    timeLastWeeklyGem = Misc.getTime();
                                    sundayScreenshots.put(sundayKey, true);

                                    BufferedImage gems = bot.browser.getImg().getSubimage(133, 16, 80, 14);
                                    Misc.saveScreen("weekly-gems", "gems", BHBotUnity.includeMachineNameInScreenshots, gems);
                                }
                            }
                        }
                    }

                    // daily gem screenshot
                    if ((bot.settings.screenshots.contains("dg")) && (Misc.getTime() - timeLastDailyGem) > Misc.Durations.DAY) {
                        timeLastDailyGem = Misc.getTime();

                        BufferedImage gems = bot.browser.getImg().getSubimage(133, 16, 80, 14);
                        Misc.saveScreen("daily-gems", "gems", BHBotUnity.includeMachineNameInScreenshots, gems); //else screenshot daily count
                    }

                    if (!bot.settings.debugBoot) {
                        settings.initialize();
                        // We reset the last time we checked settings
                        timeLastSettingsCheck = Misc.getTime();
                        // On some machines checking settings is slow, so we reset the idle timer
                        bot.scheduler.resetIdleTime();

                        shrineManager.initialize();
                        runeManager.initialize();
                    } else {
                        if (!debugBootWarning) {
                            BHBotUnity.logger.debug("Debug boot detected: autoRune, autoShrine, Settings not initialized.");
                            debugBootWarning = true;
                        }
                    }

                    // We periodically check settings to make sure they are properly configured
                    if (Misc.getTime() - timeLastSettingsCheck > SETTINGS_CHECK_INTERVAL) {
                        settings.checkBotSettings();
                        // We reset the last time we checked settings
                        timeLastSettingsCheck = Misc.getTime();
                        // On some machines checking settings is slow, so we reset the idle timer
                        bot.scheduler.resetIdleTime();
                    }

                    // check for bonuses:
                    if (bot.settings.autoConsume && (Misc.getTime() - timeLastBonusCheck > BONUS_CHECK_INTERVAL)) {
                        timeLastBonusCheck = Misc.getTime();
                        handleConsumables();
                        bot.scheduler.resetIdleTime();
                    }

                    String currentActivity = activitySelector(); //else select the activity to attempt
                    if (currentActivity != null) {
                        BHBotUnity.logger.debug("Checking activity: " + currentActivity);

                        //region Raid/Shards
                        if ("r".equals(currentActivity)) {
                            timeLastShardsCheck = Misc.getTime();

                            bot.browser.readScreen();
                            MarvinSegment raidBTNSeg = MarvinSegment.fromCue(BHBotUnity.cues.get("RaidButton"), bot.browser);

                            if (raidBTNSeg == null) { // if null, then raid button is transparent meaning that raiding is not enabled (we have not achieved it yet, for example)
                                bot.scheduler.restoreIdleTime();
                                continue;
                            }
                            bot.browser.clickOnSeg(raidBTNSeg);

                            seg = MarvinSegment.fromCue("RaidPopup", 5 * Misc.Durations.SECOND, bot.browser); // wait until the raid window opens
                            if (seg == null) {
                                BHBotUnity.logger.warn("Error: attempt at opening raid window failed. No window cue detected. Ignoring...");
                                bot.scheduler.restoreIdleTime();
                                // we make sure that everything that can be closed is actually closed to avoid idle timeout
                                bot.browser.closePopupSecurely(BHBotUnity.cues.get("X"), BHBotUnity.cues.get("X"));
                                continue;
                            }

                            final Set<Color> shardBarColors = Set.of(new Color(199, 79, 175), new Color(199, 79, 176), new Color(147, 47, 118));
                            int shards = readResourceBarPercentage(seg, bot.settings.maxShards, Misc.BarOffsets.RAID.x, Misc.BarOffsets.RAID.y, shardBarColors, bot.browser.getImg());

                            globalShards = shards;
                            BHBotUnity.logger.readout("Shards: " + shards + ", required: >" + bot.settings.minShards);

                            if (shards == -1) { // error
                                bot.scheduler.restoreIdleTime();
                                continue;
                            }

                            if ((shards == 0) || (!bot.scheduler.doRaidImmediately && (shards <= bot.settings.minShards || bot.settings.raids.size() == 0))) {
                                if (bot.scheduler.doRaidImmediately)
                                    bot.scheduler.doRaidImmediately = false; // reset it

                                bot.browser.readScreen();
                                seg = MarvinSegment.fromCue(BHBotUnity.cues.get("X"), Misc.Durations.SECOND, Bounds.fromWidthHeight(610, 90, 60, 60), bot.browser);
                                bot.browser.clickOnSeg(seg);
                                Misc.sleep(Misc.Durations.SECOND);

                                continue;

                            } else { // do the raiding!

                                if (bot.scheduler.doRaidImmediately)
                                    bot.scheduler.doRaidImmediately = false; // reset it

                                // set up autoRune and autoShrine
                                handleAdventureConfiguration(BHBotUnity.State.Raid, true, Bounds.fromWidthHeight(600, 80, 80, 80));

                                bot.browser.readScreen(Misc.Durations.SECOND);
                                bot.browser.clickOnSeg(raidBTNSeg);

                                Settings.AdventureSetting raidSetting = decideAdventureRandomly(bot.settings.raids);
                                if (raidSetting == null) {
                                    bot.settings.activitiesEnabled.remove("r");
                                    BHBotUnity.logger.error("It was impossible to choose a raid randomly, raids are disabled!");
                                    bot.notificationManager.sendErrorNotification("Raid Error", "It was impossible to choose a raid randomly, raids are disabled!");

                                    continue;
                                }

                                // We only rerun if round robin is disabled and rerun is enabled for current configuration
                                rerunCurrentActivity = raidSetting.rerun && !bot.settings.activitiesRoundRobin;

                                if (!handleRaidSelection(raidSetting.adventureZone, raidSetting.difficulty)) {
                                    restart();
                                    continue;
                                }

                                seg = MarvinSegment.fromCue(BHBotUnity.cues.get("RaidSummon"), 3 * Misc.Durations.SECOND, bot.browser);
                                if (seg == null) {
                                    BHBotUnity.logger.error("Raid Summon button not found");
                                    Misc.saveScreen("no-raid-summon-button", "errors", true, bot.browser.getImg());
                                    restart();
                                    continue;
                                }
                                bot.browser.clickOnSeg(seg);

                                // dismiss character dialog if it pops up:
                                bot.browser.readScreen();
                                detectCharacterDialogAndHandleIt();

                                Cue raidDifficultyCue = switch (raidSetting.difficulty) {
                                    case 1 -> BHBotUnity.cues.get("RaidNormal");
                                    case 2 -> BHBotUnity.cues.get("RaidHard");
                                    /*case 3,*/ default -> BHBotUnity.cues.get("RaidHeroic");
                                };

                                seg = MarvinSegment.fromCue(raidDifficultyCue, Misc.Durations.SECOND * 3, bot.browser);
                                bot.browser.clickOnSeg(seg);

                                //team selection screen
                                /* Solo-for-bounty code */
                                if (raidSetting.solo) { //if the level is soloable then clear the team to complete bounties
                                    Cue raidClearCue = BHBotUnity.cues.get("TeamClear");

                                    seg = MarvinSegment.fromCue(raidClearCue, Misc.Durations.SECOND * 3, bot.browser);
                                    if (seg != null) {
                                        BHBotUnity.logger.info("Attempting solo as per selected raid setting....");
                                        bot.browser.clickOnSeg(seg);
                                    } else {
                                        BHBotUnity.logger.error("Impossible to find clear button in Raid Team!");
                                        restart();
                                        continue;
                                    }
                                }

                                Cue acceptCue = BHBotUnity.cues.get("TeamAccept");
                                bot.browser.closePopupSecurely(acceptCue, acceptCue);

                                if (raidSetting.solo) {
                                    Cue yesGreen = new Cue(BHBotUnity.cues.get("YesGreen"), Bounds.fromWidthHeight(290, 330, 85, 60));

                                    if (!bot.browser.closePopupSecurely(BHBotUnity.cues.get("TeamNotFull"), yesGreen)) {
                                        BHBotUnity.logger.error("Impossible to find Yes button in Raid Team!");
                                        restart();
                                    }
                                } else {
                                    if (handleTeamMalformedWarning()) {
                                        BHBotUnity.logger.error("Team incomplete, doing emergency restart..");
                                        restart();
                                        continue;
                                    }
                                }

                                bot.setState(BHBotUnity.State.Raid);
                                bot.setLastJoinedState(BHBotUnity.State.Raid);
                                BHBotUnity.logger.info("Raid initiated!");
                                runeManager.reset();

                            }
                            continue;
                        } //endregion Raid/Shards

                        //region T/G/Tokens
                        if (bot.scheduler.doTrialsImmediately || bot.scheduler.doGauntletImmediately ||
                                ("t".equals(currentActivity)) || ("g".equals(currentActivity))) {
                            if ("t".equals(currentActivity)) timeLastTrialsTokensCheck = Misc.getTime();
                            if ("g".equals(currentActivity)) timeLastGauntletTokensCheck = Misc.getTime();

                            bot.browser.readScreen();

                            boolean trials;
                            seg = MarvinSegment.fromCue(BHBotUnity.cues.get("Trials"), bot.browser);
                            if (seg == null) seg = MarvinSegment.fromCue(BHBotUnity.cues.get("Trials2"), bot.browser);
                            trials = seg != null; // if false, then we will do gauntlet instead of trials

                            if (("g".equals(currentActivity) && trials) || ("t".equals(currentActivity) && !trials))
                                continue;

                            if (seg == null)
                                seg = MarvinSegment.fromCue(BHBotUnity.cues.get("Gauntlet"), bot.browser);
                            if (seg == null) {
                                seg = MarvinSegment.fromCue(BHBotUnity.cues.get("Gauntlet2"), 0, Bounds.fromWidthHeight(735, 235, 55, 180), bot.browser);
                            }
                            if (seg == null) {// trials/gauntlet button not visible (perhaps it is disabled?)
                                BHBotUnity.logger.warn("Gauntlet/Trials button not found");
                                bot.scheduler.restoreIdleTime();
                                continue;
                            }

                            bot.browser.clickOnSeg(seg);
                            MarvinSegment trialBTNSeg = seg;

                            // dismiss character dialog if it pops up:
                            bot.browser.readScreen(2 * Misc.Durations.SECOND);
                            detectCharacterDialogAndHandleIt();

                            bot.browser.readScreen();
                            seg = MarvinSegment.fromCue("TokenBar", 5 * Misc.Durations.SECOND, bot.browser);
                            final Set<Color> tokenBarColors = Set.of(new Color(17, 208, 226), new Color(1, 133, 146), new Color(1, 145, 158));
                            int tokens = readResourceBarPercentage(seg, bot.settings.maxTokens, Misc.BarOffsets.TG.x, Misc.BarOffsets.TG.y, tokenBarColors, bot.browser.getImg());
                            globalTokens = tokens;
                            BHBotUnity.logger.readout("Tokens: " + tokens + ", required: >" + bot.settings.minTokens + ", " +
                                    (trials ? "Trials" : "Gauntlet") + " cost: " + (trials ? bot.settings.costTrials : bot.settings.costGauntlet));

                            if (tokens < 0) { // error
                                BHBotUnity.logger.error("Impossible to read token bar, closing T/G window.");
                                bot.browser.closePopupSecurely(BHBotUnity.cues.get("TokenBar"), BHBotUnity.cues.get("X"));
                                bot.scheduler.restoreIdleTime();
                                continue;
                            }

                            if (((!bot.scheduler.doTrialsImmediately && !bot.scheduler.doGauntletImmediately) && (tokens <= bot.settings.minTokens)) || (tokens < (trials ? bot.settings.costTrials : bot.settings.costGauntlet))) {
                                bot.browser.readScreen();
                                seg = MarvinSegment.fromCue(BHBotUnity.cues.get("X"), Misc.Durations.SECOND, bot.browser);
                                bot.browser.clickOnSeg(seg);
                                bot.browser.readScreen(Misc.Durations.SECOND);

                                //if we have 1 token and need 5 we don't need to check every 10 minutes, this increases the timer so we start checking again when we are one token short
                                int tokenDifference = (trials ? bot.settings.costTrials : bot.settings.costGauntlet) - tokens; //difference between needed and current resource
                                if (tokenDifference > 1) {
                                    int increase = (tokenDifference - 1) * 45;
                                    TOKENS_CHECK_INTERVAL = (long) increase * Misc.Durations.MINUTE; //add 45 minutes to TOKENS_CHECK_INTERVAL for each token needed above 1
                                } else
                                    TOKENS_CHECK_INTERVAL = 10 * Misc.Durations.MINUTE; //if we only need 1 token check every 10 minutes

                                if (bot.scheduler.doTrialsImmediately) {
                                    bot.scheduler.doTrialsImmediately = false; // if we don't have resources to run we need to disable force it
                                } else if (bot.scheduler.doGauntletImmediately) {
                                    bot.scheduler.doGauntletImmediately = false;
                                }

                                continue;
                            } else {
                                // do the trials/gauntlet!

                                if (bot.scheduler.doTrialsImmediately) {
                                    bot.scheduler.doTrialsImmediately = false; // reset it
                                } else if (bot.scheduler.doGauntletImmediately) {
                                    bot.scheduler.doGauntletImmediately = false;
                                }

                                // set up autoRune and autoShrine
                                handleAdventureConfiguration(trials ? BHBotUnity.State.Trials : BHBotUnity.State.Gauntlet, true, null);

                                bot.browser.readScreen(Misc.Durations.SECOND);
                                bot.browser.clickOnSeg(trialBTNSeg);
                                bot.browser.readScreen(Misc.Durations.SECOND); //wait for window animation

                                // apply the correct difficulty
                                int targetDifficulty = trials ? bot.settings.difficultyTrials : bot.settings.difficultyGauntlet;

                                BHBotUnity.logger.info("Attempting " + (trials ? "trials" : "gauntlet") + " at level " + targetDifficulty + "...");

                                int difficulty = detectDifficulty(BHBotUnity.cues.get("Difficulty"));
                                if (difficulty == 0) { // error!
                                    BHBotUnity.logger.error("Due to an error#1 in difficulty detection, " + (trials ? "trials" : "gauntlet") + " will be skipped.");
                                    bot.browser.closePopupSecurely(BHBotUnity.cues.get("TokenBar"), BHBotUnity.cues.get("X"));
                                    continue;
                                }
                                if (difficulty != targetDifficulty) {
                                    BHBotUnity.logger.info("Detected " + (trials ? "trials" : "gauntlet") + " difficulty level: " + difficulty + ", settings level: " + targetDifficulty + ". Changing..");
                                    int result = selectDifficulty(difficulty, targetDifficulty, BHBotUnity.cues.get("SelectDifficulty"), BHBotUnity.cues.get("TokenBar"), 1, true);
                                    if (result == 0) { // error!

                                        BHBotUnity.logger.warn("Unable to change difficulty, usually because desired level is not unlocked. Running " + (trials ? "trials" : "gauntlet") + " at " + difficulty + ".");
                                        Misc.saveScreen("tg-difficulty-select", "errors", true, bot.browser.getImg());
                                        bot.notificationManager.sendErrorNotification("T/G Error", "Unable to change " + (trials ? "trials" : "gauntlet") + " difficulty to : " + targetDifficulty + " Running: " + difficulty + " instead.");

                                        // see if drop down menu is still open and close it:
                                        if (!bot.browser.closeAllPopups("TokenBar")) {
                                            bot.browser.readScreen(Misc.Durations.SECOND);
                                            tryClosingWindow(BHBotUnity.cues.get("DifficultyDropDown"));
                                            bot.browser.readScreen(3 * Misc.Durations.SECOND);
                                            // We do not close the window as we try with the current difficulty
                                            //tryClosingWindow(BHBotUnity.cues.get("TokenBar"));
                                        }

                                        // We update the setting file with the old difficulty level
                                        String settingName = trials ? "difficultyTrials" : "difficultyGauntlet";
                                        String original = settingName + " " + targetDifficulty;
                                        String updated = settingName + " " + difficulty;
                                        settingsUpdate(original, updated);

                                    } else if (result != targetDifficulty) {
                                        BHBotUnity.logger.warn(targetDifficulty + " is not available in " + (trials ? "trials" : "gauntlet") + " difficulty selection. Closest match is " + result + ".");

                                        // We update the setting file with the old difficulty level
                                        String settingName = trials ? "difficultyTrials" : "difficultyGauntlet";
                                        String original = settingName + " " + targetDifficulty;
                                        String updated = settingName + " " + result;
                                        settingsUpdate(original, updated);
                                    }
                                }

                                // select cost if needed:
                                bot.browser.readScreen(2 * Misc.Durations.SECOND); // wait for the popup to stabilize a bit
                                int cost = detectCost();
                                if (cost == 0) { // error!
                                    BHBotUnity.logger.error("Due to an error#1 in cost detection, " + (trials ? "trials" : "gauntlet") + " will be skipped.");
                                    bot.browser.closePopupSecurely(BHBotUnity.cues.get("TokenBar"), BHBotUnity.cues.get("X"));
                                    continue;
                                }
                                if (cost != (trials ? bot.settings.costTrials : bot.settings.costGauntlet)) {
                                    BHBotUnity.logger.info("Detected " + (trials ? "trials" : "gauntlet") + " cost: " + cost + ", settings cost is " + (trials ? bot.settings.costTrials : bot.settings.costGauntlet) + ". Changing it...");
                                    boolean result = selectCost(cost, (trials ? bot.settings.costTrials : bot.settings.costGauntlet));
                                    if (!result) { // error!
                                        BHBotUnity.logger.error("Due to an error#2 in cost selection, " + (trials ? "trials" : "gauntlet") + " will be skipped.");
                                        Misc.saveScreen("tg-cost-selection", "errors", true, bot.browser.getImg());
                                        bot.notificationManager.sendErrorNotification("T/G Error", "Unable to change " + (trials ? "trials" : "gauntlet") + " cost to : " + (trials ? bot.settings.costTrials : bot.settings.costGauntlet) + ".");

                                        // see if drop down menu is still open and close it:
                                        if (!bot.browser.closeAllPopups()) {
                                            bot.browser.readScreen(Misc.Durations.SECOND);
                                            tryClosingWindow(BHBotUnity.cues.get("CostDropDown"));
                                            bot.browser.readScreen(3 * Misc.Durations.SECOND);
                                            tryClosingWindow(BHBotUnity.cues.get("TokenBar"));
                                        }

                                        continue;
                                    }

                                    // We wait for the cost selector window to close
                                    MarvinSegment.fromCue("TokenBar", Misc.Durations.SECOND * 2, bot.browser);
                                    bot.browser.readScreen();
                                }

                                seg = MarvinSegment.fromCue(BHBotUnity.cues.get("Play"), 2 * Misc.Durations.SECOND, bot.browser);
                                if (seg == null) {
                                    BHBotUnity.logger.error("Error: Play button not found while trying to do " + (trials ? "trials" : "gauntlet") + ". Ignoring...");
                                    tryClosingWindow(BHBotUnity.cues.get("TokenBar"));
                                    continue;
                                }
                                bot.browser.clickOnSeg(seg);
                                bot.browser.readScreen(2 * Misc.Durations.SECOND);

                                Boolean notEnoughTokens = handleNotEnoughTokensPopup(false);
                                if (notEnoughTokens != null) {
                                    if (notEnoughTokens) {
                                        continue;
                                    }
                                } else {
                                    restart();
                                    continue;
                                }

                                // dismiss character dialog if it pops up:
                                detectCharacterDialogAndHandleIt();

                                //bot.browser.clickOnSeg(seg);
                                Cue AcceptWithBounds = new Cue(BHBotUnity.cues.get("TeamAccept"), Bounds.fromWidthHeight(445, 440, 145, 55));
                                bot.browser.closePopupSecurely(AcceptWithBounds, AcceptWithBounds);
                                bot.browser.readScreen(2 * Misc.Durations.SECOND);

                                // This is a Bit Heroes bug!
                                // On t/g main screen the token bar is wrongly full so it goes trough the "Play" button and
                                // then it fails on the team "Accept" button
                                notEnoughTokens = handleNotEnoughTokensPopup(true);
                                if (notEnoughTokens != null) {
                                    if (notEnoughTokens) {
                                        continue;
                                    }
                                } else {
                                    restart();
                                    continue;
                                }

                                Misc.sleep(3 * Misc.Durations.SECOND);

                                if (handleTeamMalformedWarning()) {
                                    BHBotUnity.logger.error("Team incomplete, doing emergency restart..");
                                    restart();
                                    continue;
                                } else {
                                    bot.setState(trials ? BHBotUnity.State.Trials : BHBotUnity.State.Gauntlet);
                                    bot.setLastJoinedState(trials ? BHBotUnity.State.Trials : BHBotUnity.State.Gauntlet);
                                    BHBotUnity.logger.info((trials ? "Trials" : "Gauntlet") + " initiated!");
                                    runeManager.reset();
                                }
                            }
                            continue;
                        } //endregion T/G/Tokens

                        //region Dungeon/Energy
                        if ("d".equals(currentActivity)) {
                            timeLastEnergyCheck = Misc.getTime();

                            bot.browser.readScreen();

                            final Set<Color> energyBarColors = Set.of(new Color(87, 133, 21), new Color(136, 197, 44));

                            seg = MarvinSegment.fromCue(BHBotUnity.cues.get("EnergyBar"), bot.browser);
                            int energy = readResourceBarPercentage(seg, 100, Misc.BarOffsets.DUNGEON.x, Misc.BarOffsets.DUNGEON.y, energyBarColors, bot.browser.getImg());
                            globalEnergy = energy;
                            BHBotUnity.logger.readout("Energy: " + energy + "%, required: >" + bot.settings.minEnergyPercentage + "%");

                            if (energy == -1) { // error
                                bot.scheduler.restoreIdleTime();
                                if (bot.scheduler.doDungeonImmediately)
                                    bot.scheduler.doDungeonImmediately = false; // reset it
                                continue;
                            }

                            if (!bot.scheduler.doDungeonImmediately && (energy <= bot.settings.minEnergyPercentage || bot.settings.dungeons.size() == 0)) {
                                Misc.sleep(Misc.Durations.SECOND);

                                //if we have 1 resource and need 5 we don't need to check every 10 minutes, this increases the timer so we start checking again when we are one under the check limit
                                int energyDifference = bot.settings.minEnergyPercentage - energy; //difference between needed and current resource
                                if (energyDifference > 1) {
                                    int increase = (energyDifference - 1) * 8;
                                    ENERGY_CHECK_INTERVAL = (long) increase * Misc.Durations.MINUTE; //add 8 minutes to the check interval for each energy % needed above 1
                                } else
                                    ENERGY_CHECK_INTERVAL = 10 * Misc.Durations.MINUTE; //if we only need 1 check every 10 minutes

                                continue;
                            } else {
                                // do the dungeon!

                                if (bot.scheduler.doDungeonImmediately)
                                    bot.scheduler.doDungeonImmediately = false; // reset it

                                // set up autoRune and autoShrine
                                handleAdventureConfiguration(BHBotUnity.State.Dungeon, false, null);

                                seg = MarvinSegment.fromCue(BHBotUnity.cues.get("Quest"), Misc.Durations.SECOND * 3, bot.browser);
                                if (seg == null) {
                                    Misc.saveScreen("no-quest-btn", "errors", BHBotUnity.includeMachineNameInScreenshots, bot.browser.getImg());
                                    BHBotUnity.logger.error("Impposible to find the quest button!");
                                    continue;
                                }

                                bot.browser.clickOnSeg(seg);
                                // We wait for the dungeon window to be open and to do so, we wait for the "Zones" button on the top left
                                seg = MarvinSegment.fromCue(BHBotUnity.cues.get("DungeonZones"), 10 * Misc.Durations.SECOND, bot.browser);
                                if (seg == null) {
                                    BHBotUnity.logger.error("It was impposible to open the dungeon window. Restarting....");
                                    restart();
                                    continue;
                                }

                                Settings.AdventureSetting dungeonSetting = decideAdventureRandomly(bot.settings.dungeons);
                                if (dungeonSetting == null) {
                                    bot.settings.activitiesEnabled.remove("d");
                                    BHBotUnity.logger.error("It was impossible to choose a dungeon randomly, dungeons are disabled!");
                                    bot.notificationManager.sendErrorNotification("Dungeon error", "It was impossible to choose a dungeon randomly, dungeons are disabled!");
                                    continue;
                                }

                                Matcher dungeonMatcher = dungeonRegex.matcher(dungeonSetting.adventureZone.toLowerCase());
                                if (!dungeonMatcher.find()) {
                                    BHBotUnity.logger.error("Wrong format in dungeon detected: '" + dungeonSetting.adventureZone + "'! It will be skipped...");
                                    bot.notificationManager.sendErrorNotification("Dungeon error", "Wrong dungeon format detected: " + dungeonSetting.adventureZone);
                                    // TODO close the dungeon window
                                    continue;
                                }

                                int goalZone = Integer.parseInt(dungeonMatcher.group("zone"));
                                int goalDungeon = Integer.parseInt(dungeonMatcher.group("dungeon"));

                                String difficultyName = (dungeonSetting.difficulty == 1 ? "Normal" : dungeonSetting.difficulty == 2 ? "Hard" : "Heroic");

                                BHBotUnity.logger.info("Attempting " + difficultyName + " z" + goalZone + "d" + goalDungeon);

                                // We wait for the zones button to be there
                                MarvinSegment.fromCue(BHBotUnity.cues.get("DungeonZones"), Misc.Durations.SECOND * 3, bot.browser);

                                String signature = dungSignatures.getCurrentZoneSignature();
                                int currentZone = dungSignatures.zoneFromSignature(signature);
                                if (currentZone == 0) {
                                    BHBotUnity.logger.error("Impossible to detect current selected zone!");

                                    BHBotUnity.logger.error("Found signature: " + signature);

                                    seg = MarvinSegment.fromCue("X", Bounds.fromWidthHeight(695, 40, 70, 70), bot.browser);
                                    if (seg != null) {
                                        bot.browser.clickOnSeg(seg);
                                    } else {
                                        BHBotUnity.logger.error("It was impossible to close the dungeon window, restarting...");
                                        restart();
                                    }

                                    continue;
                                }
                                int vec = goalZone - currentZone; // movement vector
                                // TODO Change te logic to remove clikInGame and useless delays
                                while (vec != 0) { // move to the correct zone
                                    if (vec > 0) {
                                        // note that moving to the right will fail in case player has not unlocked the zone yet!
                                        bot.browser.readScreen(Misc.Durations.SECOND); // wait for screen to stabilise
                                        seg = MarvinSegment.fromCue(BHBotUnity.cues.get("RightArrow"), bot.browser);
                                        if (seg == null) {
                                            BHBotUnity.logger.error("Right button not found, zone unlocked?");
                                            break; // happens for example when player hasn't unlock the zone yet
                                        }
                                        //coords are used as moving multiple screens would crash the bot when using the arrow cues
                                        bot.browser.clickInGame(740, 275);
                                        vec--;
                                    } else {
                                        Misc.sleep(500);
                                        //coords are used as moving multiple screens would crash the bot when using the arrow cues
                                        bot.browser.clickInGame(55, 275);
                                        vec++;
                                    }
                                }

                                bot.browser.readScreen(2 * Misc.Durations.SECOND);

                                // click on the dungeon:
                                Point p = getDungeonIconPos(goalZone, goalDungeon);
                                if (p == null) {
                                    bot.settings.activitiesEnabled.remove("d");
                                    BHBotUnity.logger.error("It was impossible to get icon position of dungeon z" + goalZone + "d" + goalDungeon + ". Dungeons are now disabled!");
                                    bot.notificationManager.sendErrorNotification("Dungeon error", "It was impossible to get icon position of dungeon z" + goalZone + "d" + goalDungeon + ". Dungeons are now disabled!");
                                    continue;
                                }

                                bot.browser.clickInGame(p);

                                // select difficulty (If D4 just hit enter):
                                if ((goalDungeon == 4) || (goalZone == 7 && goalDungeon == 3) || (goalZone == 8 && goalDungeon == 3)) { // D4, or Z7D3/Z8D3
                                    specialDungeon = true;
                                    seg = MarvinSegment.fromCue(BHBotUnity.cues.get("Enter"), 5 * Misc.Durations.SECOND, bot.browser);
                                } else { //else select appropriate difficulty
                                    Cue cueDifficulty = switch (dungeonSetting.difficulty) {
                                        case 1 -> BHBotUnity.cues.get("DungNormal");
                                        case 2 -> BHBotUnity.cues.get("DungHard");
                                        /*case 3,*/ default -> BHBotUnity.cues.get("DungHeroic");
                                    };

                                    seg = MarvinSegment.fromCue(cueDifficulty, 5 * Misc.Durations.SECOND, bot.browser);
                                }

                                if (seg == null) {
                                    bot.settings.activitiesEnabled.remove("d");
                                    BHBotUnity.logger.error("It was impossible to get difficulty button of dungeon z" + goalZone + "d" + goalDungeon + ". Dungeons are now disabled!");
                                    Misc.saveScreen("dungeon-no-difficulty-btn", "errors", true, bot.browser.getImg());
                                    bot.notificationManager.sendErrorNotification("Dungeon error", "It was impossible to get difficulty button of dungeon z" + goalZone + "d" + goalDungeon + ". Dungeons are now disabled!");
                                    bot.browser.closePopupSecurely(BHBotUnity.cues.get("X"), BHBotUnity.cues.get("X"));
                                    continue;
                                }

                                bot.browser.clickOnSeg(seg);

                                //team selection screen
                                /* Solo-for-bounty code */
                                if (dungeonSetting.solo) { //if the level is soloable then clear the team to complete bounties

                                    seg = MarvinSegment.fromCue("TeamClear", Misc.Durations.SECOND * 3, bot.browser);
                                    if (seg != null) {
                                        BHBotUnity.logger.info("Attempting solo as per selected dungeon setting....");
                                        bot.browser.clickOnSeg(seg);
                                    } else {
                                        BHBotUnity.logger.error("Impossible to find clear button in Dungeon Team!");
                                        restart();
                                        continue;
                                    }
                                }

                                Cue dungeonAccept = BHBotUnity.cues.get("TeamAccept");
                                bot.browser.closePopupSecurely(dungeonAccept, dungeonAccept);

                                if (dungeonSetting.solo) {
                                    Cue yesGreen = new Cue(BHBotUnity.cues.get("YesGreen"), Bounds.fromWidthHeight(290, 335, 85, 55));

                                    if (!bot.browser.closePopupSecurely(BHBotUnity.cues.get("TeamNotFull"), yesGreen)) {
                                        BHBotUnity.logger.error("Impossible to find Yes button in Dungeon Team!");
                                        restart();
                                    }
                                } else {
                                    if (handleTeamMalformedWarning()) {
                                        restart();
                                        continue;
                                    }
                                }

                                if (handleNotEnoughEnergyPopup()) {
                                    continue;
                                }

                                bot.setState(BHBotUnity.State.Dungeon);
                                bot.setLastJoinedState(BHBotUnity.State.Dungeon);
                                runeManager.reset();

                                BHBotUnity.logger.info("Dungeon <z" + goalZone + "d" + goalDungeon + "> " + (dungeonSetting.difficulty == 1 ? "normal" : dungeonSetting.difficulty == 2 ? "hard" : "heroic") + " initiated!");
                            }
                            continue;
                        } //endregion Dungeon/Energy

                        //region PVP/Tickets
                        if ("p".equals(currentActivity)) {
                            timeLastTicketsCheck = Misc.getTime();

                            bot.browser.readScreen();

                            int tickets = getTickets();
                            globalTickets = tickets;
                            BHBotUnity.logger.readout("Tickets: " + tickets + ", required: >" + bot.settings.minTickets + ", PVP cost: " + bot.settings.costPVP);

                            if (tickets == -1) { // error
                                bot.scheduler.restoreIdleTime();
                                continue;
                            }

                            if ((!bot.scheduler.doPVPImmediately && (tickets <= bot.settings.minTickets)) || (tickets < bot.settings.costPVP)) {
                                Misc.sleep(Misc.Durations.SECOND);

                                //if we have 1 resource and need 5 we don't need to check every 10 minutes, this increases the timer so we start checking again when we are one under the check limit
                                int ticketDifference = bot.settings.costPVP - tickets; //difference between needed and current resource
                                if (ticketDifference > 1) {
                                    int increase = (ticketDifference - 1) * 45;
                                    TICKETS_CHECK_INTERVAL = (long) increase * Misc.Durations.MINUTE; //add 45 minutes to the check interval for each ticket needed above 1
                                } else
                                    TICKETS_CHECK_INTERVAL = 10 * Misc.Durations.MINUTE; //if we only need 1 check every 10 minutes

                                continue;
                            } else {
                                // do the pvp!

                                if (bot.scheduler.doPVPImmediately)
                                    bot.scheduler.doPVPImmediately = false; // reset it

                                //configure activity runes
                                handleAdventureConfiguration(BHBotUnity.State.PVP, false, null);

                                BHBotUnity.logger.info("Attempting PVP...");
                                stripDown(bot.settings.pvpstrip);

                                seg = MarvinSegment.fromCue(BHBotUnity.cues.get("PVP"), bot.browser);
                                if (seg == null) {
                                    BHBotUnity.logger.warn("PVP button not found. Skipping PVP...");
                                    dressUp(bot.settings.pvpstrip);
                                    continue; // should not happen though
                                }
                                bot.browser.clickOnSeg(seg);

                                // select cost if needed:
                                bot.browser.readScreen(2 * Misc.Durations.SECOND); // wait for the popup to stabilize a bit
                                int cost = detectCost();
                                if (cost == 0) { // error!
                                    BHBotUnity.logger.error("Due to an error#1 in cost detection, PVP will be skipped.");
                                    bot.browser.closePopupSecurely(BHBotUnity.cues.get("PVPWindow"), BHBotUnity.cues.get("X"));
                                    dressUp(bot.settings.pvpstrip);
                                    continue;
                                }
                                if (cost != bot.settings.costPVP) {
                                    BHBotUnity.logger.info("Detected PVP cost: " + cost + ", settings cost is " + bot.settings.costPVP + ". Changing..");
                                    boolean result = selectCost(cost, bot.settings.costPVP);
                                    if (!result) { // error!
                                        // see if drop down menu is still open and close it:
                                        bot.browser.readScreen(Misc.Durations.SECOND);
                                        tryClosingWindow(BHBotUnity.cues.get("CostDropDown"));
                                        bot.browser.readScreen(5 * Misc.Durations.SECOND);
                                        seg = MarvinSegment.fromCue(BHBotUnity.cues.get("PVPWindow"), 15 * Misc.Durations.SECOND, bot.browser);
                                        if (seg != null)
                                            bot.browser.closePopupSecurely(BHBotUnity.cues.get("PVPWindow"), BHBotUnity.cues.get("X"));
                                        BHBotUnity.logger.error("Due to an error#2 in cost selection, PVP will be skipped.");
                                        dressUp(bot.settings.pvpstrip);
                                        continue;
                                    }
                                }

                                seg = MarvinSegment.fromCue(BHBotUnity.cues.get("Play"), 5 * Misc.Durations.SECOND, bot.browser);
                                bot.browser.clickOnSeg(seg);
                                bot.browser.readScreen(2 * Misc.Durations.SECOND);

                                // dismiss character dialog if it pops up:
                                detectCharacterDialogAndHandleIt();

                                Bounds pvpOpponentBounds = opponentSelector(bot.settings.pvpOpponent);
                                String opponentName = (bot.settings.pvpOpponent == 1 ? "1st" : bot.settings.pvpOpponent == 2 ? "2nd" : bot.settings.pvpOpponent == 3 ? "3rd" : "4th");
                                BHBotUnity.logger.info("Selecting " + opponentName + " opponent");
                                seg = MarvinSegment.fromCue("Fight", 5 * Misc.Durations.SECOND, pvpOpponentBounds, bot.browser);
                                if (seg == null) {
                                    BHBotUnity.logger.error("Imppossible to find the Fight button in the PVP screen, restarting!");
                                    restart();
                                    continue;
                                }
                                bot.browser.clickOnSeg(seg);

                                bot.browser.readScreen();
                                seg = MarvinSegment.fromCue("Accept", 5 * Misc.Durations.SECOND, new Bounds(430, 430, 630, 500), bot.browser);
                                if (seg == null) {
                                    BHBotUnity.logger.error("Impossible to find the Accept button in the PVP screen, restarting");
                                    restart();
                                    continue;
                                }
                                bot.browser.closePopupSecurely(BHBotUnity.cues.get("Accept"), BHBotUnity.cues.get("Accept"));
                                //bot.browser.clickOnSeg(seg);

                                if (handleTeamMalformedWarning()) {
                                    BHBotUnity.logger.error("Team incomplete, doing emergency restart..");
                                    restart();
                                    continue;
                                } else {
                                    bot.setState(BHBotUnity.State.PVP);
                                    bot.setLastJoinedState(BHBotUnity.State.PVP);
                                    BHBotUnity.logger.info("PVP initiated!");
                                }
                            }
                            continue;
                        } //endregion PVP/Tickets

                        //region Badges
                        if (("v".equals(currentActivity)) || ("i".equals(currentActivity)) || ("e".equals(currentActivity))) {

                            String checkedActivity = currentActivity;

                            if ("v".equals(currentActivity)) timeLastGVGBadgesCheck = Misc.getTime();
                            if ("i".equals(currentActivity)) timeLastInvBadgesCheck = Misc.getTime();
                            if ("e".equals(currentActivity)) timeLastExpBadgesCheck = Misc.getTime();

                            bot.browser.readScreen();

                            BadgeEvent badgeEvent = BadgeEvent.None;
                            MarvinSegment badgeBtn = null;

                            HashMap<Cue, BadgeEvent> badgeEvents = new HashMap<>();
                            badgeEvents.put(BHBotUnity.cues.get("ExpeditionButton"), BadgeEvent.Expedition);
                            badgeEvents.put(BHBotUnity.cues.get("GVG"), BadgeEvent.GVG);
                            badgeEvents.put(BHBotUnity.cues.get("Invasion"), BadgeEvent.Invasion);

                            for (Map.Entry<Cue, BadgeEvent> event : badgeEvents.entrySet()) {
                                badgeBtn = MarvinSegment.fromCue(event.getKey(), bot.browser);
                                if (badgeBtn != null) {
                                    badgeEvent = event.getValue();
                                    seg = badgeBtn;
                                    break;
                                }
                            }


                            if (badgeEvent == BadgeEvent.None) { // GvG/invasion button not visible (perhaps this week there is no GvG/Invasion/Expedition event?)
                                bot.scheduler.restoreIdleTime();
                                BHBotUnity.logger.debug("No badge event found, skipping");
                                continue;
                            }

                            if (badgeEvent == BadgeEvent.Expedition) currentActivity = "e";
                            if (badgeEvent == BadgeEvent.Invasion) currentActivity = "i";
                            if (badgeEvent == BadgeEvent.GVG) currentActivity = "v";

                            if (!currentActivity.equals(checkedActivity)) { //if checked activity and chosen activity don't match we skip
                                continue;
                            }

                            bot.browser.clickOnSeg(seg);
                            Misc.sleep(2 * Misc.Durations.SECOND);

                            detectCharacterDialogAndHandleIt(); // needed for invasion

                            bot.browser.readScreen();

                            seg = MarvinSegment.fromCue("BadgeBar", 5 * Misc.Durations.SECOND, bot.browser);
                            final Set<Color> badgeBarColors = Set.of(new Color (17, 208, 226), new Color (1, 133, 146), new Color (17, 198, 215), new Color (17, 201, 218), new Color (18, 205, 223));
                            int badges = readResourceBarPercentage(seg, bot.settings.maxBadges, Misc.BarOffsets.Badge.x, Misc.BarOffsets.Badge.y, badgeBarColors, bot.browser.getImg());

                            globalBadges = badges;
                            BHBotUnity.logger.readout("Badges: " + badges + ", required: >" + bot.settings.minBadges + ", " + badgeEvent.toString() + " cost: " +
                                    (badgeEvent == BadgeEvent.GVG ? bot.settings.costGVG : badgeEvent == BadgeEvent.Invasion ? bot.settings.costInvasion : bot.settings.costExpedition));

                            if (badges == -1) { // error
                                bot.scheduler.restoreIdleTime();
                                continue;
                            }

                            //region GVG
                            if (BadgeEvent.GVG.equals(badgeEvent)) {
                                if ((!bot.scheduler.doGVGImmediately && (badges <= bot.settings.minBadges)) || (badges < bot.settings.costGVG)) {

                                    //if we have 1 resource and need 5 we don't need to check every 10 minutes, this increases the timer so we start checking again when we are one under the check limit
                                    int badgeDifference = bot.settings.costGVG - badges; //difference between needed and current resource
                                    if (badgeDifference > 1) {
                                        int increase = (badgeDifference - 1) * 45;
                                        BADGES_CHECK_INTERVAL = (long) increase * Misc.Durations.MINUTE; //add 45 minutes to the check interval for each ticket needed above 1
                                    } else
                                        BADGES_CHECK_INTERVAL = 10 * Misc.Durations.MINUTE; //if we only need 1 check every 10 minutes

                                    bot.browser.readScreen();
                                    seg = MarvinSegment.fromCue(BHBotUnity.cues.get("X"), Misc.Durations.SECOND, bot.browser);
                                    bot.browser.clickOnSeg(seg);
                                    Misc.sleep(Misc.Durations.SECOND);
                                    continue;
                                } else {
                                    // do the GVG!

                                    if (bot.scheduler.doGVGImmediately)
                                        bot.scheduler.doGVGImmediately = false; // reset it

                                    // set up autoRune and autoShrine
                                    handleAdventureConfiguration(BHBotUnity.State.GVG, true, null);
                                    bot.browser.readScreen(Misc.Durations.SECOND);
                                    bot.browser.clickOnSeg(badgeBtn);

                                    BHBotUnity.logger.info("Attempting GVG...");

                                    if (bot.settings.gvgstrip.size() > 0) {
                                        // If we need to strip down for GVG, we need to close the GVG gump and open it again
                                        bot.browser.closePopupSecurely(BHBotUnity.cues.get("GVGWindow"), BHBotUnity.cues.get("X"));
                                        stripDown(bot.settings.gvgstrip);
                                        bot.browser.clickOnSeg(badgeBtn);
                                    }

                                    // select cost if needed:
                                    bot.browser.readScreen(2 * Misc.Durations.SECOND); // wait for the popup to stabilize a bit
                                    int cost = detectCost();
                                    if (cost == 0) { // error!
                                        BHBotUnity.logger.error("Due to an error#1 in cost detection, GVG will be skipped.");
                                        bot.browser.closePopupSecurely(BHBotUnity.cues.get("GVGWindow"), BHBotUnity.cues.get("X"));
                                        continue;
                                    }
                                    if (cost != bot.settings.costGVG) {
                                        BHBotUnity.logger.info("Detected GVG cost: " + cost + ", settings cost is " + bot.settings.costGVG + ". Changing..");
                                        boolean result = selectCost(cost, bot.settings.costGVG);
                                        if (!result) { // error!
                                            // see if drop down menu is still open and close it:
                                            bot.browser.readScreen(Misc.Durations.SECOND);
                                            tryClosingWindow(BHBotUnity.cues.get("CostDropDown"));
                                            bot.browser.readScreen(5 * Misc.Durations.SECOND);
                                            seg = MarvinSegment.fromCue(BHBotUnity.cues.get("GVGWindow"), 15 * Misc.Durations.SECOND, bot.browser);
                                            if (seg != null)
                                                bot.browser.closePopupSecurely(BHBotUnity.cues.get("GVGWindow"), BHBotUnity.cues.get("X"));
                                            BHBotUnity.logger.error("Due to an error#2 in cost selection, GVG will be skipped.");
                                            dressUp(bot.settings.gvgstrip);
                                            continue;
                                        }
                                    }

                                    Bounds gvgPlayBounds = Bounds.fromWidthHeight(510, 265, 80, 35);
                                    seg = MarvinSegment.fromCue(BHBotUnity.cues.get("Play"), 5 * Misc.Durations.SECOND, gvgPlayBounds, bot.browser);
                                    bot.browser.clickOnSeg(seg);
                                    bot.browser.readScreen(2 * Misc.Durations.SECOND);

                                    // Sometimes, before the reset, battles are disabled
                                    Boolean disabledBattles = handleDisabledBattles();
                                    if (disabledBattles == null) {
                                        restart();
                                        continue;
                                    } else if (disabledBattles) {
                                        bot.browser.readScreen();
                                        bot.browser.closePopupSecurely(BHBotUnity.cues.get("GVGWindow"), BHBotUnity.cues.get("X"));
                                        continue;
                                    }

                                    //On initial GvG run you'll get a warning about not being able to leave guild, this will close that
                                    if (handleGuildLeaveConfirm()) {
                                        restart();
                                        continue;
                                    }

                                    Bounds gvgOpponentBounds = opponentSelector(bot.settings.gvgOpponent);
                                    String opponentName = switch (bot.settings.gvgOpponent) {
                                        case 1 -> "1st";
                                        case 2 -> "2nd";
                                        case 3 -> "3rd";
                                        case 4 -> "4th";
                                        default ->
                                                throw new IllegalStateException("Unexpected gvgOpponent value: " + bot.settings.gvgOpponent);
                                    };
                                    BHBotUnity.logger.info("Selecting " + opponentName + " opponent");
                                    seg = MarvinSegment.fromCue(BHBotUnity.cues.get("Fight"), 5 * Misc.Durations.SECOND, gvgOpponentBounds, bot.browser);
                                    if (seg == null) {
                                        BHBotUnity.logger.error("Imppossible to find the Fight button in the GvG screen, restarting!");
                                        restart();
                                        continue;
                                    }
                                    bot.browser.clickOnSeg(seg);
                                    bot.browser.readScreen();
                                    Misc.sleep(Misc.Durations.SECOND);

                                    Bounds acceptBounds = Bounds.fromWidthHeight(445, 440, 145, 55);
                                    Cue gvgAccept = new Cue(BHBotUnity.cues.get("TeamAccept"), acceptBounds);

                                    seg = MarvinSegment.fromCue(gvgAccept, 5 * Misc.Durations.SECOND, bot.browser);
                                    if (seg == null) {
                                        BHBotUnity.logger.error("Imppossible to find the Accept button in the GvG screen, restarting!");
                                        restart();
                                        continue;
                                    }
                                    //bot.browser.clickOnSeg(seg);
                                    bot.browser.closePopupSecurely(gvgAccept, gvgAccept);
                                    Misc.sleep(Misc.Durations.SECOND);

                                    if (handleTeamMalformedWarning()) {
                                        BHBotUnity.logger.error("Team incomplete, doing emergency restart..");
                                        restart();
                                        continue;
                                    } else {
                                        bot.setState(BHBotUnity.State.GVG);
                                        bot.setLastJoinedState(BHBotUnity.State.GVG);
                                        BHBotUnity.logger.info("GVG initiated!");
                                    }
                                }
                                continue;
                            } //endregion GVG
                            //region Invasion
                            else if (badgeEvent == BadgeEvent.Invasion) {
                                if ((!bot.scheduler.doInvasionImmediately && (badges <= bot.settings.minBadges)) || (badges < bot.settings.costInvasion)) {

                                    //if we have 1 resource and need 5 we don't need to check every 10 minutes, this increases the timer so we start checking again when we are one under the check limit
                                    int badgeDifference = bot.settings.costGVG - badges; //difference between needed and current resource
                                    if (badgeDifference > 1) {
                                        int increase = (badgeDifference - 1) * 45;
                                        BADGES_CHECK_INTERVAL = (long) increase * Misc.Durations.MINUTE; //add 45 minutes to the check interval for each ticket needed above 1
                                    } else
                                        BADGES_CHECK_INTERVAL = 10 * Misc.Durations.MINUTE; //if we only need 1 check every 10 minutes

                                    bot.browser.readScreen();
                                    seg = MarvinSegment.fromCue(BHBotUnity.cues.get("X"), Misc.Durations.SECOND, bot.browser);
                                    bot.browser.clickOnSeg(seg);
                                    Misc.sleep(Misc.Durations.SECOND);
                                    continue;
                                } else {
                                    // do the invasion!

                                    if (bot.scheduler.doInvasionImmediately)
                                        bot.scheduler.doInvasionImmediately = false; // reset it

                                    // set up autoRune and autoShrine
                                    handleAdventureConfiguration(BHBotUnity.State.Invasion, true, null);
                                    bot.browser.readScreen(Misc.Durations.SECOND);
                                    bot.browser.clickOnSeg(badgeBtn);

                                    BHBotUnity.logger.info("Attempting invasion...");

                                    // select cost if needed:
                                    bot.browser.readScreen(2 * Misc.Durations.SECOND); // wait for the popup to stabilize a bit
                                    int cost = detectCost();
                                    if (cost == 0) { // error!
                                        BHBotUnity.logger.error("Due to an error#1 in cost detection, invasion will be skipped.");
                                        bot.browser.closePopupSecurely(BHBotUnity.cues.get("InvasionWindow"), BHBotUnity.cues.get("X"));
                                        continue;
                                    }
                                    if (cost != bot.settings.costInvasion) {
                                        BHBotUnity.logger.info("Detected invasion cost: " + cost + ", settings cost is " + bot.settings.costInvasion + ". Changing..");
                                        boolean result = selectCost(cost, bot.settings.costInvasion);
                                        if (!result) { // error!
                                            // see if drop down menu is still open and close it:
                                            bot.browser.readScreen(Misc.Durations.SECOND);
                                            tryClosingWindow(BHBotUnity.cues.get("CostDropDown"));
                                            bot.browser.readScreen(5 * Misc.Durations.SECOND);
                                            seg = MarvinSegment.fromCue(BHBotUnity.cues.get("InvasionWindow"), 15 * Misc.Durations.SECOND, bot.browser);
                                            if (seg != null)
                                                bot.browser.closePopupSecurely(BHBotUnity.cues.get("InvasionWindow"), BHBotUnity.cues.get("X"));
                                            BHBotUnity.logger.error("Due to an error#2 in cost selection, invasion will be skipped.");
                                            continue;
                                        }
                                    }

                                    seg = MarvinSegment.fromCue(BHBotUnity.cues.get("Play"), 5 * Misc.Durations.SECOND, Bounds.fromWidthHeight(510, 260, 80, 40), bot.browser);
                                    if (seg == null) {
                                        BHBotUnity.logger.error("Unable to find the Play button in the Invasion screen, restarting!");
                                        restart();
                                        continue;
                                    }
                                    bot.browser.clickOnSeg(seg);

                                    Cue invAccept = new Cue(BHBotUnity.cues.get("TeamAccept"), Bounds.fromWidthHeight(445, 440, 145, 55));
                                    seg = MarvinSegment.fromCue(invAccept, 10 * Misc.Durations.SECOND, bot.browser);
                                    if (seg == null) {
                                        BHBotUnity.logger.error("Unable to find the Accept button in the Invasion screen, restarting!");
                                        restart();
                                        continue;
                                    }
                                    //bot.browser.clickOnSeg(seg);
                                    bot.browser.closePopupSecurely(invAccept, invAccept);
                                    Misc.sleep(2 * Misc.Durations.SECOND);

                                    if (handleTeamMalformedWarning()) {
                                        BHBotUnity.logger.error("Team incomplete, doing emergency restart..");
                                        restart();
                                        continue;
                                    } else {
                                        bot.setState(BHBotUnity.State.Invasion);
                                        bot.setLastJoinedState(BHBotUnity.State.Invasion);
                                        BHBotUnity.logger.info("Invasion initiated!");
                                    }
                                }
                                continue;
                            } //endregion Invasion
                            //region Expedition
                            else if (badgeEvent == BadgeEvent.Expedition) {

                                if ((!bot.scheduler.doExpeditionImmediately && (badges <= bot.settings.minBadges)) || (badges < bot.settings.costExpedition)) {

                                    //if we have 1 resource and need 5 we don't need to check every 10 minutes, this increases the timer so we start checking again when we are one under the check limit
                                    int badgeDifference = bot.settings.costGVG - badges; //difference between needed and current resource
                                    if (badgeDifference > 1) {
                                        int increase = (badgeDifference - 1) * 45;
                                        BADGES_CHECK_INTERVAL = (long) increase * Misc.Durations.MINUTE; //add 45 minutes to the check interval for each ticket needed above 1
                                    } else
                                        BADGES_CHECK_INTERVAL = 10 * Misc.Durations.MINUTE; //if we only need 1 check every 10 minutes

                                    seg = MarvinSegment.fromCue(BHBotUnity.cues.get("X"), bot.browser);
                                    bot.browser.clickOnSeg(seg);
                                    Misc.sleep(2 * Misc.Durations.SECOND);
                                    continue;
                                } else {
                                    // do the expedition!

                                    if (bot.scheduler.doExpeditionImmediately)
                                        bot.scheduler.doExpeditionImmediately = false; // reset it

//                                    if (bot.settings.costExpedition > badges) {
//                                        BHBot.logger.info("Target cost " + bot.settings.costExpedition + " is higher than available badges " + badges + ". Expedition will be skipped.");
//                                        seg = MarvinSegment.fromCue(BHBot.cues.get("X"), bot.browser);
//                                        bot.browser.clickOnSeg(seg);
//                                        Misc.sleep(2 * Misc.Durations.SECOND);
//                                        continue;
//                                    }

                                    // set up autoRune and autoShrine
                                    handleAdventureConfiguration(BHBotUnity.State.Expedition, true, null);

                                    bot.browser.readScreen(Misc.Durations.SECOND);
                                    bot.browser.clickOnSeg(badgeBtn);
                                    bot.browser.readScreen(Misc.Durations.SECOND * 2);

                                    BHBotUnity.logger.info("Attempting expedition...");

                                    bot.browser.readScreen(Misc.Durations.SECOND * 2);
                                    int cost = detectCost();
                                    if (cost == 0) { // error!
                                        BHBotUnity.logger.error("Due to an error#1 in cost detection, Expedition cost will be skipped.");
                                        bot.browser.closePopupSecurely(BHBotUnity.cues.get("ExpeditionWindow"), BHBotUnity.cues.get("X"));
                                        continue;
                                    }

                                    if (cost != bot.settings.costExpedition) {
                                        BHBotUnity.logger.info("Detected Expedition cost: " + cost + ", settings cost is " + bot.settings.costExpedition + ". Changing..");
                                        boolean result = selectCost(cost, bot.settings.costExpedition);
                                        if (!result) { // error!
                                            // see if drop down menu is still open and close it:
                                            bot.browser.readScreen(Misc.Durations.SECOND);
                                            tryClosingWindow(BHBotUnity.cues.get("CostDropDown"));
                                            bot.browser.readScreen(5 * Misc.Durations.SECOND);
                                            seg = MarvinSegment.fromCue(BHBotUnity.cues.get("X"), bot.browser);
                                            bot.browser.clickOnSeg(seg);
                                            Misc.sleep(2 * Misc.Durations.SECOND);
                                            BHBotUnity.logger.error("Due to an error in cost selection, Expedition will be skipped.");
                                            continue;
                                        }
                                        bot.browser.readScreen(Misc.Durations.SECOND * 2);
                                    }

                                    seg = MarvinSegment.fromCue(BHBotUnity.cues.get("Play"), 2 * Misc.Durations.SECOND, bot.browser);
                                    bot.browser.clickOnSeg(seg);
                                    bot.browser.readScreen(2 * Misc.Durations.SECOND);

                                    //Select Expedition and write portal to a variable
                                    String randomExpedition = bot.settings.expeditions.next();
                                    if (randomExpedition == null) {
                                        bot.settings.activitiesEnabled.remove("e");
                                        BHBotUnity.logger.error("It was impossible to randomly choose an expedition. Expeditions are disabled.");
                                        bot.notificationManager.sendErrorNotification("Expedition error", "It was impossible to randomly choose an expedition. Expeditions are disabled.");
                                        continue;
                                    }

                                    String[] expedition = randomExpedition.split(" ");
                                    String targetPortal = expedition[0];
                                    int targetDifficulty = Integer.parseInt(expedition[1]);

                                    // if exped difficulty isn't a multiple of 5 we reduce it
                                    int difficultyModule = targetDifficulty % 5;
                                    if (difficultyModule != 0) {
                                        BHBotUnity.logger.warn(targetDifficulty + " is not a multiplier of 5! Rounding it to " + (targetDifficulty - difficultyModule) + "...");
                                        targetDifficulty -= difficultyModule;
                                    }
                                    // If difficulty is lesser that 5, we round it
                                    if (targetDifficulty < 5) {
                                        BHBotUnity.logger.warn("Expedition difficulty can not be smaller than 5, rounding it to 5.");
                                        targetDifficulty = 5;
                                    }

                                    bot.browser.readScreen();
                                    int currentExpedition;
                                    if (MarvinSegment.fromCue(BHBotUnity.cues.get("Expedition1"), bot.browser) != null) {
                                        currentExpedition = 1;
                                    } else if (MarvinSegment.fromCue(BHBotUnity.cues.get("Expedition2"), bot.browser) != null) {
                                        currentExpedition = 2;
                                    } else if (MarvinSegment.fromCue(BHBotUnity.cues.get("Expedition3"), bot.browser) != null) {
                                        currentExpedition = 3;
                                    } else if (MarvinSegment.fromCue(BHBotUnity.cues.get("Expedition4"), bot.browser) != null) {
                                        currentExpedition = 4;
                                    } else if (MarvinSegment.fromCue("Expedition5", bot.browser) != null) {
                                        currentExpedition = 5;
                                    } else {
                                        bot.settings.activitiesEnabled.remove("e");
                                        BHBotUnity.logger.error("It was impossible to get the current expedition type!");
                                        bot.notificationManager.sendErrorNotification("Expedition error", "It was impossible to get the current expedition type. Expeditions are now disabled!");
                                        Misc.saveScreen("unknown-expedition", "errors/expedition", true, bot.browser.getImg());

                                        bot.browser.readScreen();
                                        seg = MarvinSegment.fromCue(BHBotUnity.cues.get("X"), Misc.Durations.SECOND, bot.browser);
                                        if (seg != null) bot.browser.clickOnSeg(seg);
                                        bot.browser.readScreen(2 * Misc.Durations.SECOND);
                                        continue;
                                    }

                                    String portalName = getExpeditionPortalName(currentExpedition, targetPortal);
                                    BHBotUnity.logger.info("Attempting " + targetPortal + " " + portalName + " Portal at difficulty " + targetDifficulty);

                                    //write current portal and difficulty to global values for difficultyFailsafe
                                    expeditionFailsafePortal = targetPortal;
                                    expeditionFailsafeDifficulty = targetDifficulty;

                                    // click on the chosen portal:
                                    Point p = getExpeditionIconPos(currentExpedition, targetPortal);
                                    if (p == null) {
                                        bot.settings.activitiesEnabled.remove("e");
                                        BHBotUnity.logger.error("It was impossible to get portal position for " + portalName + ". Expeditions are now disabled!");
                                        bot.notificationManager.sendErrorNotification("Expedition error", "It was impossible to get portal position for " + portalName + ". Expeditions are now disabled!");

                                        bot.browser.readScreen();
                                        seg = MarvinSegment.fromCue(BHBotUnity.cues.get("X"), Misc.Durations.SECOND, bot.browser);
                                        if (seg != null) bot.browser.clickOnSeg(seg);
                                        bot.browser.readScreen(2 * Misc.Durations.SECOND);
                                        continue;
                                    }

                                    bot.browser.clickInGame(p);

                                    // select difficulty if needed:
                                    int difficulty = detectDifficulty(BHBotUnity.cues.get("DifficultyExpedition"));
                                    if (difficulty == 0) { // error!
                                        BHBotUnity.logger.warn("Due to an error in difficulty detection, Expedition will be skipped.");
                                        seg = MarvinSegment.fromCue(BHBotUnity.cues.get("X"), bot.browser);
                                        while (seg != null) {
                                            bot.browser.clickOnSeg(seg);
                                            bot.browser.readScreen(2 * Misc.Durations.SECOND);
                                            seg = MarvinSegment.fromCue(BHBotUnity.cues.get("X"), bot.browser);
                                        }
                                        continue;
                                    }

                                    if (difficulty != targetDifficulty) {
                                        BHBotUnity.logger.info("Detected Expedition difficulty level: " + difficulty + ", settings level is " + targetDifficulty + ". Changing..");
                                        int result = selectDifficulty(difficulty, targetDifficulty, BHBotUnity.cues.get("SelectDifficultyExpedition"), BHBotUnity.cues.get("BadgeBar"), 5, false);
                                        if (result == 0) { // error!
                                            // see if drop down menu is still open and close it:
                                            bot.browser.readScreen(Misc.Durations.SECOND);
                                            tryClosingWindow(BHBotUnity.cues.get("DifficultyDropDown"));
                                            bot.browser.readScreen(5 * Misc.Durations.SECOND);
                                            BHBotUnity.logger.warn("Unable to change difficulty, usually because desired level is not unlocked. Running Expedition at " + difficulty + ".");
                                            bot.notificationManager.sendErrorNotification("Expedition Error", "Unable to change expedtion difficulty to : " + targetDifficulty + " Running: " + difficulty + " instead.");

                                            // We update the file with the old difficulty level
                                            String original = expeditionFailsafePortal + " " + targetDifficulty;
                                            String updated = expeditionFailsafePortal + " " + difficulty;
                                            settingsUpdate(original, updated);

                                        } else if (result != targetDifficulty) {
                                            BHBotUnity.logger.warn(targetDifficulty + " is not available. Running Expedition at the closest match " + result + ".");

                                            // We update the file with the old difficulty level
                                            String original = expeditionFailsafePortal + " " + targetDifficulty;
                                            String updated = expeditionFailsafePortal + " " + result;
                                            settingsUpdate(original, updated);
                                        }
                                    }

                                    //click Green Enter button
                                    seg = MarvinSegment.fromCue(BHBotUnity.cues.get("Enter"), 2 * Misc.Durations.SECOND, bot.browser);
                                    bot.browser.clickOnSeg(seg);

                                    //click Accept
                                    Cue expeditionAccept = new Cue(BHBotUnity.cues.get("TeamAccept"), Bounds.fromWidthHeight(445, 440, 145, 55));

                                    seg = MarvinSegment.fromCue(expeditionAccept, 3 * Misc.Durations.SECOND, bot.browser);
                                    if (seg != null) {
                                        //bot.browser.clickOnSeg(seg);
                                        bot.browser.closePopupSecurely(expeditionAccept, expeditionAccept);
                                    } else {
                                        BHBotUnity.logger.error("No accept button for expedition team!");
                                        bot.saveGameScreen("expedtion-no-accept", "errors");
                                        restart();
                                    }

                                    if (handleTeamMalformedWarning()) {
                                        BHBotUnity.logger.error("Team incomplete, doing emergency restart..");
                                        restart();
                                        continue;
                                    } else {
                                        bot.setState(BHBotUnity.State.Expedition);
                                        bot.setLastJoinedState(BHBotUnity.State.Expedition);
                                        BHBotUnity.logger.info(portalName + " portal initiated!");
                                        runeManager.reset();
                                    }

                                    if (handleGuildLeaveConfirm()) {
                                        restart();
                                        continue;
                                    }
                                }
                                continue;
                            }
                            //endregion Expedition
                            else {
                                // do neither gvg nor invasion nor expedition
                                seg = MarvinSegment.fromCue(BHBotUnity.cues.get("X"), bot.browser);
                                bot.browser.clickOnSeg(seg);
                                Misc.sleep(2 * Misc.Durations.SECOND);
                                continue;
                            }
                        } //endregion Badges

                        //region WorldBoss
                        if ("w".equals(currentActivity)) {
                            timeLastXealsCheck = Misc.getTime();

                            bot.browser.readScreen();
                            MarvinSegment wbBTNSeg = MarvinSegment.fromCue(BHBotUnity.cues.get("WorldBoss"), bot.browser);
                            if (wbBTNSeg == null) {
                                bot.scheduler.resetIdleTime();
                                BHBotUnity.logger.error("World Boss button not found");
                                continue;
                            }
                            bot.browser.clickOnSeg(wbBTNSeg);

                            bot.browser.readScreen();
                            detectCharacterDialogAndHandleIt(); //clear dialogue

                            seg = MarvinSegment.fromCue("WorldBossPopup", 5 * Misc.Durations.SECOND, bot.browser); // wait until the raid window opens
                            if (seg == null) {
                                BHBotUnity.logger.warn("Error: attempt at opening world boss window failed. No window cue detected. Ignoring...");
                                bot.scheduler.restoreIdleTime();
                                // we make sure that everything that can be closed is actually closed to avoid idle timeout
                                Misc.saveScreen("no-wb-window", "errors", true, bot.browser.getImg());
                                bot.browser.closePopupSecurely(BHBotUnity.cues.get("X"), BHBotUnity.cues.get("X"));
                                continue;
                            }

                            final Set<Color> xealBarColors = Set.of(new Color (0, 86, 208), new Color (0, 77, 190), new Color (0, 138, 255));
                            int xeals = readResourceBarPercentage(seg, bot.settings.maxXeals, Misc.BarOffsets.WB.x, Misc.BarOffsets.WB.y, xealBarColors, bot.browser.getImg());

                            globalXeals = xeals;
                            BHBotUnity.logger.readout("Xeals: " + xeals + ", required: >" + bot.settings.minXeals);

                            if (xeals == -1) { // error
                                if (bot.scheduler.doWorldBossImmediately)
                                    bot.scheduler.doWorldBossImmediately = false; // reset it
                                bot.scheduler.restoreIdleTime();
                                continue;
                            } else if ((xeals == 0) || (!bot.scheduler.doWorldBossImmediately && (xeals <= bot.settings.minXeals || bot.settings.worldBossSettings.size() == 0))) {
                                if (bot.scheduler.doWorldBossImmediately)
                                    bot.scheduler.doWorldBossImmediately = false; // reset it

                                int xealDifference = bot.settings.minXeals - xeals; //difference between needed and current resource
                                if (xealDifference > 1) {
                                    int increase = (xealDifference - 1) * 45;
                                    XEALS_CHECK_INTERVAL = (long) increase * Misc.Durations.MINUTE; //add 45 minutes to the check interval for each xeal needed above 1
                                } else
                                    XEALS_CHECK_INTERVAL = 10 * Misc.Durations.MINUTE; //if we only need 1 check every 10 minutes

                                bot.browser.closePopupSecurely(BHBotUnity.cues.get("WorldBossPopup"), BHBotUnity.cues.get("X"));
                                continue;

                            } else {
                                // do the WorldBoss!
                                if (bot.scheduler.doWorldBossImmediately)
                                    bot.scheduler.doWorldBossImmediately = false; // reset it

                                Settings.WorldBossSetting wbSetting = bot.settings.worldBossSettings.next();
                                if (wbSetting == null) {
                                    BHBotUnity.logger.error("No World Boss setting found! Disabling World Boss");
                                    bot.settings.activitiesEnabled.remove("w");
                                    seg = MarvinSegment.fromCue(BHBotUnity.cues.get("X"), Misc.Durations.SECOND, bot.browser);
                                    bot.browser.clickOnSeg(seg);
                                    bot.browser.readScreen(Misc.Durations.SECOND);
                                    continue;
                                }

                                if (!checkWorldBossInput(wbSetting)) {
                                    BHBotUnity.logger.warn("Invalid world boss settings detected, World Boss will be skipped");
                                    seg = MarvinSegment.fromCue(BHBotUnity.cues.get("X"), Misc.Durations.SECOND, bot.browser);
                                    bot.browser.clickOnSeg(seg);
                                    bot.browser.readScreen(Misc.Durations.SECOND);
                                    continue;
                                }

                                // set up autoRune and autoShrine
                                handleAdventureConfiguration(BHBotUnity.State.WorldBoss, true, null);

                                //We re-open the wb window
                                if (bot.settings.autoRune.containsKey("w")) {
                                    bot.browser.readScreen(Misc.Durations.SECOND);
                                    bot.browser.clickOnSeg(wbBTNSeg);
                                }

                                WorldBoss wbType = WorldBoss.fromLetter(String.valueOf(wbSetting.type));
                                if (wbType == null) {
                                    BHBotUnity.logger.error("Unkwon World Boss type: " + wbSetting.type + ". Disabling World Boss");
                                    bot.settings.activitiesEnabled.remove("w");
                                    restart();
                                    continue;
                                }

                                //new settings loading
                                String worldBossDifficultyText = wbSetting.difficulty == 1 ? "Normal" : wbSetting.difficulty == 2 ? "Hard" : "Heroic";

                                if (!wbSetting.solo) {
                                    BHBotUnity.logger.info("Attempting " + worldBossDifficultyText + " T" + wbSetting.tier + " " + wbType.getName() + ". Lobby timeout is " + Misc.millisToHumanForm((long) wbSetting.timer * 1000L) + ".");
                                } else {
                                    BHBotUnity.logger.info("Attempting " + worldBossDifficultyText + " T" + wbSetting.tier + " " + wbType.getName() + " Solo");
                                }

                                bot.browser.readScreen();
                                seg = MarvinSegment.fromCue("DarkBlueSummon", Misc.Durations.SECOND, bot.browser);
                                if (seg != null) {
                                    bot.browser.clickOnSeg(seg);
                                } else {
                                    BHBotUnity.logger.error("Impossible to find dark blue summon in world boss.");

                                    bot.saveGameScreen("wb-no-dark-blue-summon", "errors");
                                    bot.notificationManager.sendErrorNotification("World Boss error", "Impossible to find blue summon.");

                                    bot.browser.closePopupSecurely(BHBotUnity.cues.get("WorldBossTitle"), BHBotUnity.cues.get("X"));
                                    continue;
                                }
                                bot.browser.readScreen(2 * Misc.Durations.SECOND); //wait for screen to stablise

                                //world boss type selection
                                if (!handleWorldBossSelection(wbType)) {
                                    BHBotUnity.logger.error("Impossible to change select the desired World Boss. Restarting...");
                                    restart();
                                    continue;
                                }

                                seg = MarvinSegment.fromCue("LargeDarkBlueSummon", 4 * Misc.Durations.SECOND, bot.browser);
                                bot.browser.clickOnSeg(seg); //selected world boss

                                seg = MarvinSegment.fromCue(BHBotUnity.cues.get("WBIsPrivate"), Misc.Durations.SECOND * 3, bot.browser);
                                if (!wbSetting.solo) {
                                    if (seg != null) {
                                        BHBotUnity.logger.info("Unchecking private lobby");
                                        bot.browser.clickOnSeg(seg);
                                    }
                                } else {
                                    if (seg == null) {
                                        BHBotUnity.logger.info("Enabling private lobby for solo World Boss");
                                        Misc.sleep(500);
                                        bot.browser.clickInGame(340, 350);
                                        bot.browser.readScreen(500);
                                    }
                                }

                                //world boss tier selection

                                int currentTier = detectWorldBossTier();
                                // TODO  When currentTier == 0, an error with reading number happened!

                                if (currentTier != wbSetting.tier) {
                                    BHBotUnity.logger.info("T" + currentTier + " detected, changing to T" + wbSetting.tier);
                                    Misc.sleep(500);
                                    if (!changeWorldBossTier(wbSetting.tier)) {
                                        restart();
                                        continue;
                                    }
                                }

                                //world boss difficulty selection

                                int currentDifficulty = detectWorldBossDifficulty();
                                // TODO When currentDifficulty == 0, an error with reading difficulty happened
                                String currentDifficultyName = (currentDifficulty == 1 ? "Normal" : currentDifficulty == 2 ? "Hard" : "Heroic");
                                String settingsDifficultyName = (wbSetting.difficulty == 1 ? "Normal" : wbSetting.difficulty == 2 ? "Hard" : "Heroic");
                                if (currentDifficulty != wbSetting.difficulty) {
                                    BHBotUnity.logger.info(currentDifficultyName + " detected, changing to " + settingsDifficultyName);
                                    changeWorldBossDifficulty(wbSetting.difficulty);
                                }

                                seg = MarvinSegment.fromCue("SmallDarkBlueSummon", Misc.Durations.SECOND * 3, bot.browser);
                                bot.browser.clickOnSeg(seg); //accept current settings

                                boolean insufficientXeals = handleNotEnoughXealsPopup();
                                if (insufficientXeals) {
                                    continue;
                                }

                                BHBotUnity.logger.info("Starting lobby: " + wbType.getName() + " has " + wbType.getPartySize() + " party members");

                                //wait for lobby to fill with a timer
                                if (!wbSetting.solo) {
                                    // How many invites do we expect for this WB?
                                    int inviteCnt = wbType.getPartySize() - 1;

                                    // Invite and unready buttons bounds are dinamically calculated based on the WB party member
                                    Bounds inviteBounds = Bounds.fromWidthHeight(330, 217, 127, 54 * inviteCnt);
                                    Bounds unreadyBounds = Bounds.fromWidthHeight(177, 217, 24, 54 * inviteCnt);

                                    // we assume we did not start the WB
                                    boolean lobbyTimeout = true;

                                    // Timings
                                    long startTime = Misc.getTime();
                                    long cutOffTime = startTime + (wbSetting.timer * Misc.Durations.SECOND);
                                    long nextUpdateTime = startTime + (15 * Misc.Durations.SECOND);

                                    // Temporary string used to make sure we don't save 10000000s of screenshots when debugWBTS is enabled
                                    String lastSavedName = "";

                                    // this is long running loop and we want to be sure that it is interrupted when the bot needs to quit
                                    cutOffLoop:
                                    while (Misc.getTime() < cutOffTime && bot.running && !bot.finished) {

                                        // When a pause command is issued, we get out of the WB lobby
                                        if (bot.scheduler.isPaused()) {
                                            BHBotUnity.logger.info("Pause detected, exiting from World Boss loby.");
                                            break;
                                        }

                                        // we make sure to update the screen image as FindSubimage.findSubimage is using a static image
                                        // at the same time we also wait 500ms so to ease CPU consumption
                                        bot.browser.readScreen(500);

                                        // Array used to save party members TS
                                        int[] playersTS = new int[inviteCnt];

                                        // We read the current total TS
                                        int totalTS = 0;
                                        if (wbSetting.minimumTotalTS > 0) {
                                            // We refresh the screen to be sure we get the most up to date TS values
                                            bot.browser.readScreen();
                                            totalTS = getWorldBossTotalTS(bot.browser.getImg());

                                            // If readNumFromImg has errors it will return 0, so we make sure this is not the case
                                            if (totalTS > 0 && totalTS >= wbSetting.minimumTotalTS) {

                                                // We need to check that the current party members are ready
                                                List<MarvinSegment> unreadySegs = FindSubimage.findSubimage(bot.browser.getImg(), BHBotUnity.cues.get("Unready").im, 1.0, true, false, unreadyBounds.x1, unreadyBounds.y1, unreadyBounds.x2, unreadyBounds.y2);

                                                if (unreadySegs.isEmpty()) {
                                                    BHBotUnity.logger.info("TS for lobby is " + totalTS + ". " + wbSetting.minimumTotalTS + " requirement reached in " + Misc.millisToHumanForm(Misc.getTime() - startTime));
                                                    lobbyTimeout = false;
                                                    saveDebugWBTSScreen(totalTS, playersTS, lastSavedName);
                                                    break;
                                                } else {
                                                    continue;
                                                }
                                            }

                                        }

                                        List<MarvinSegment> inviteSegs = FindSubimage.findSubimage(bot.browser.getImg(), BHBotUnity.cues.get("Invite").im, 1.0, true, false, inviteBounds.x1, inviteBounds.y1, inviteBounds.x2, inviteBounds.y2);
                                        // At least one person joined the lobby
                                        if (inviteSegs.size() < inviteCnt) {


                                            if (wbSetting.minimumPlayerTS > 0) {
                                                // We refresh the screen to be sure we get the most up to date TS values
                                                bot.browser.readScreen();
                                                System.arraycopy(getWorldBossPlayersTS(inviteCnt, bot.browser.getImg()), 0, playersTS, 0, inviteCnt);
                                                // playersTS = getWorldBossPlayersTS(inviteCnt);

                                                for (int partyMemberPos = 0; partyMemberPos < inviteCnt; partyMemberPos++) {

                                                    int playerTS = playersTS[partyMemberPos];

                                                    if (playerTS < 1) {
                                                        // Player position one is you, the first party member is position two
                                                        BHBotUnity.logger.trace("It was impossible to read WB player TS for player position " + partyMemberPos + 2);
                                                    } else {
                                                        if (playerTS < wbSetting.minimumPlayerTS) {
                                                            BHBotUnity.logger.info("Player " + (partyMemberPos + 2) + " TS is lower than required minimum: " + playerTS + "/" + wbSetting.minimumPlayerTS);

                                                            // We kick the player if we need to
                                                            Bounds kickBounds = Bounds.fromWidthHeight(412, 222 + (54 * partyMemberPos), 43, 42);
                                                            seg = MarvinSegment.fromCue("WorldBossPlayerKick", 2 * Misc.Durations.SECOND, kickBounds, bot.browser);
                                                            if (seg == null) {
                                                                BHBotUnity.logger.error("Impossible to find kick button for party member " + (partyMemberPos + 2) + ".");
                                                                continue cutOffLoop;
                                                            } else {
                                                                bot.browser.clickOnSeg(seg);
                                                                seg = MarvinSegment.fromCue("WorldBossPopupKick", 5 * Misc.Durations.SECOND, bot.browser);
                                                                if (seg == null) {
                                                                    bot.saveGameScreen("wb-no-player-kick", "wb-ts-error");
                                                                    BHBotUnity.logger.error("Impossible to find player kick confirm popup");
                                                                    restart();
                                                                    break cutOffLoop;
                                                                } else {
                                                                    bot.browser.closePopupSecurely(BHBotUnity.cues.get("WorldBossPopupKick"), new Cue(BHBotUnity.cues.get("YesGreen"), Bounds.fromWidthHeight(260, 340, 130, 40)));
                                                                }
                                                            }
                                                            continue cutOffLoop;
                                                        }
                                                    }

                                                }
                                            }
                                        }

                                        if (inviteSegs.isEmpty()) {
                                            List<MarvinSegment> unreadySegs = FindSubimage.findSubimage(bot.browser.getImg(), BHBotUnity.cues.get("Unready").im, 1.0, true, false, unreadyBounds.x1, unreadyBounds.y1, unreadyBounds.x2, unreadyBounds.y2);

                                            if (unreadySegs.isEmpty()) {
                                                BHBotUnity.logger.info("Lobby filled and ready in " + Misc.millisToHumanForm(Misc.getTime() - startTime));
                                                lobbyTimeout = false;
                                                if (bot.settings.debugWBTS) {
                                                    bot.browser.readScreen();
                                                    totalTS = getWorldBossTotalTS(bot.browser.getImg());
                                                    System.arraycopy(getWorldBossPlayersTS(inviteCnt, bot.browser.getImg()), 0, playersTS, 0, inviteCnt);
                                                }
                                                saveDebugWBTSScreen(totalTS, playersTS, lastSavedName);
                                                break;
                                            }
                                        }

                                        if (Misc.getTime() >= nextUpdateTime) {

                                            // If debugWBTS is enabled, we make sure to read updated numbers
                                            if (bot.settings.debugWBTS) {
                                                bot.browser.readScreen();
                                                totalTS = getWorldBossTotalTS(bot.browser.getImg());
                                                // playersTS = getWorldBossPlayersTS(inviteCnt);
                                                System.arraycopy(getWorldBossPlayersTS(inviteCnt, bot.browser.getImg()), 0, playersTS, 0, inviteCnt);
                                            }

                                            if (totalTS > 0) {
                                                BHBotUnity.logger.debug("Total lobby TS is " + totalTS);
                                            }
                                            BHBotUnity.logger.info("Waiting for full ready team. Time out in " + Misc.millisToHumanForm(cutOffTime - Misc.getTime()));
                                            nextUpdateTime = Misc.getTime() + (15 * Misc.Durations.SECOND);
                                            bot.scheduler.resetIdleTime(true);
                                            lastSavedName = saveDebugWBTSScreen(totalTS, playersTS, lastSavedName);
                                        }
                                    }

                                    if (lobbyTimeout) {
                                        BHBotUnity.logger.info("Lobby timed out, returning to main screen.");
                                        // we say we checked (interval - 1) minutes ago, so we check again in a minute
                                        timeLastXealsCheck = Misc.getTime() - ((XEALS_CHECK_INTERVAL) - Misc.Durations.MINUTE);
                                        closeWorldBoss();
                                    } else {
                                        BHBotUnity.logger.debug("After lobby full");
                                        // bot.browser.readScreen();
                                        MarvinSegment segStart = MarvinSegment.fromCue(BHBotUnity.cues.get("DarkBlueStart"), 2 * Misc.Durations.SECOND, bot.browser);
                                        BHBotUnity.logger.debug("After Initial button search");
                                        if (segStart != null) {
                                            while (segStart != null) {
                                                BHBotUnity.logger.debug("WB before closePopupSecurely(BHBot.cues.get(\"DarkBlueStart\")");
                                                bot.browser.closePopupSecurely(BHBotUnity.cues.get("DarkBlueStart"), BHBotUnity.cues.get("DarkBlueStart")); //start World Boss
                                                BHBotUnity.logger.debug("WB after closePopupSecurely(BHBot.cues.get(\"DarkBlueStart\")");
                                                // bot.browser.readScreen();
                                                seg = MarvinSegment.fromCue(BHBotUnity.cues.get("TeamNotFull"), 2 * Misc.Durations.SECOND, bot.browser); //check if we have the team not full screen an clear it
                                                BHBotUnity.logger.debug("Team not full result " + seg);
                                                if (seg != null) {
                                                    bot.browser.readScreen(2 * Misc.Durations.SECOND); //wait for animation to finish
                                                    bot.browser.clickInGame(330, 360); //yesgreen cue has issues so we use XY to click on Yes
                                                    BHBotUnity.logger.debug("After team not full click");
                                                }

                                                segStart = MarvinSegment.fromCue(BHBotUnity.cues.get("DarkBlueStart"), 2 * Misc.Durations.SECOND, null, bot.browser);
                                            }
                                            BHBotUnity.logger.info(worldBossDifficultyText + " T" + wbSetting.tier + " " + wbType.getName() + " started!");
                                            bot.setState(BHBotUnity.State.WorldBoss);
                                            bot.setLastJoinedState(BHBotUnity.State.WorldBoss);
                                        } else { //generic error / unknown action restart
                                            BHBotUnity.logger.error("Something went wrong while attempting to start the World Boss, restarting");
                                            bot.saveGameScreen("wb-no-start-button", "errors");
                                            restart();
                                        }
                                    }
                                } else {
                                    bot.browser.readScreen();
                                    MarvinSegment segStart = MarvinSegment.fromCue(BHBotUnity.cues.get("DarkBlueStart"), 5 * Misc.Durations.SECOND, bot.browser);
                                    if (segStart != null) {
                                        bot.browser.clickOnSeg(segStart); //start World Boss
                                        Misc.sleep(2 * Misc.Durations.SECOND); //wait for dropdown animation to finish
                                        seg = MarvinSegment.fromCue(BHBotUnity.cues.get("YesGreen"), 2 * Misc.Durations.SECOND, Bounds.fromWidthHeight(290, 345, 70, 45), bot.browser); //clear empty team prompt
                                        //click anyway this cue has issues
                                        if (seg == null) {
                                            Misc.sleep(500);
                                        } else {
                                            bot.browser.clickOnSeg(seg);
                                        }
                                        bot.browser.clickInGame(330, 360); //yesgreen cue has issues so we use pos to click on Yes as a backup
                                        BHBotUnity.logger.info(worldBossDifficultyText + " T" + wbSetting.tier + " " + wbType.getName() + " Solo started!");
                                        bot.setState(BHBotUnity.State.WorldBoss);
                                        bot.setLastJoinedState(BHBotUnity.State.WorldBoss);
                                        continue;
                                    }
                                    continue;
                                }
                            }
                            continue;
                        } //endregion WorldBoss

                        //region Bounties activity
                        if ("b".equals(currentActivity)) {
                            timeLastBountyCheck = Misc.getTime();

                            if (bot.scheduler.collectBountiesImmediately) {
                                bot.scheduler.collectBountiesImmediately = false; //disable collectImmediately again if its been activated
                            }
                            BHBotUnity.logger.info("Checking for completed bounties");

                            bot.browser.clickInGame(130, 440);

                            seg = MarvinSegment.fromCue(BHBotUnity.cues.get("Bounties"), Misc.Durations.SECOND * 5, bot.browser);
                            if (seg != null) {
                                bot.browser.readScreen();
                                seg = MarvinSegment.fromCue(BHBotUnity.cues.get("Loot"), Misc.Durations.SECOND * 5, new Bounds(505, 245, 585, 275), bot.browser);
                                while (seg != null) {
                                    bot.browser.clickOnSeg(seg);
                                    seg = MarvinSegment.fromCue(BHBotUnity.cues.get("WeeklyRewards"), Misc.Durations.SECOND * 5, new Bounds(190, 100, 615, 400), bot.browser);
                                    if (seg != null) {
                                        Bounds xBounds = Bounds.fromWidthHeight(550, 120, 63, 70);
                                        Cue cueX = new Cue(BHBotUnity.cues.get("X"), xBounds);

                                        seg = MarvinSegment.fromCue(cueX, 5 * Misc.Durations.SECOND, bot.browser);
                                        if (seg != null) {
                                            if ((bot.settings.screenshots.contains("b"))) {
                                                Misc.saveScreen("bounty-loot", "rewards/bounty", true, bot.browser.getImg());
                                            }
                                            bot.browser.clickOnSeg(seg);
                                            BHBotUnity.logger.info("Collected bounties");
                                            Misc.sleep(Misc.Durations.SECOND * 2);
                                        } else {
                                            BHBotUnity.logger.error("Error when collecting bounty items, restarting...");
                                            bot.saveGameScreen("bounties-error-collect", "errors");
                                            restart();
                                        }
                                    } else {
                                        BHBotUnity.logger.error("Error finding bounty item dialog, restarting...");
                                        bot.saveGameScreen("bounties-error-item", "errors");
                                        restart();
                                    }

                                    seg = MarvinSegment.fromCue(BHBotUnity.cues.get("Loot"), Misc.Durations.SECOND * 5, new Bounds(505, 245, 585, 275), bot.browser);
                                }

                                seg = MarvinSegment.fromCue(BHBotUnity.cues.get("X"), 5 * Misc.Durations.SECOND, bot.browser);
                                if (seg != null) {
                                    bot.browser.clickOnSeg(seg);
                                } else {
                                    BHBotUnity.logger.error("Impossible to close the bounties dialog, restarting...");
                                    bot.saveGameScreen("bounties-error-closing", "errors");
                                    restart();
                                }
                            } else {
                                BHBotUnity.logger.error("Impossible to detect the Bounties dialog, restarting...");
                                bot.saveGameScreen("bounties-error-dialog", "errors");
                                restart();
                            }
                            bot.browser.readScreen(Misc.Durations.SECOND * 2);
                            continue;
                        } //endregion Bounties

                        //region Baits
                        if ("a".equals(currentActivity)) {
                            timeLastFishingBaitsCheck = Misc.getTime();

                            if (bot.scheduler.doFishingBaitsImmediately) {
                                bot.scheduler.doFishingBaitsImmediately = false; //disable collectImmediately again if its been activated
                            }

                            handleFishingBaits();

                            // handleFishingBaits() changes the State to FishingBaits
                            bot.setState(BHBotUnity.State.Main);
                            continue;
                        } //endregion Baits

                        //region Fishing
                        if ("f".equals(currentActivity)) {
                            timeLastFishingCheck = Misc.getTime();

                            if (bot.scheduler.doFishingImmediately) {
                                bot.scheduler.doFishingImmediately = false; //disable collectImmediately again if its been activated
                            }

                            if ((Misc.getTime() - timeLastFishingBaitsCheck) > Misc.Durations.DAY) { //if we haven't collected bait today we need to do that first
                                handleFishingBaits();

                                // handleFishingBaits() changes the State to FishingBaits
                                bot.setState(BHBotUnity.State.Main);
                            }

                            boolean botPresent = new File("bh-fisher.jar").exists();
                            if (!botPresent) {
                                BHBotUnity.logger.warn("bh-fisher.jar not found in root directory, fishing disabled.");
                                BHBotUnity.logger.warn("For information on configuring fishing check the wiki page on github");
                                bot.settings.activitiesEnabled.remove("f");
                                return;
                            } else {
                                handleFishing();
                            }
                            continue;
                        } //endregion Fishing

                    } else {
                        // If we don't have any activity to perform, we reset the idle timer check
                        bot.scheduler.resetIdleTime(true);
                    }

                } // main screen processing
            } catch (Exception e) {
                BHBotUnity.logger.debug("Exception in Dungeon Thread.", e);
                BHBotUnity.logger.debug(Misc.getStackTrace());
                if (bot.excManager.manageException(e)) continue;
                bot.scheduler.restoreIdleTime();
                continue;
            }

            // well, we got through all the checks. Means that nothing much has happened. So lets sleep for a second in order to not make processing too heavy...
            bot.excManager.numConsecutiveException = 0; // reset exception counter
            bot.scheduler.restoreIdleTime(); // revert changes to idle time
            if (bot.finished || (!bot.running && BHBotUnity.State.Main.equals(bot.getState())))
                break; // skip sleeping if finished flag has been set or the bot is not running!

            BHBotUnity.logger.trace("Dungeon Thread Sleeping");
            if (BHBotUnity.State.Main.equals(bot.getState()) || BHBotUnity.State.Loading.equals(bot.getState())) {
                Misc.sleep(500);
            } else {
                // While we are in a dungeon we want a faster main loop
                Misc.sleep(50);
            }
        } // main while loop

        BHBotUnity.logger.info("Dungeon thread stopped.");
    }

    private String activitySelector() {

        if (bot.scheduler.doRaidImmediately) {
            return "r";
        } else if (bot.scheduler.doDungeonImmediately) {
            return "d";
        } else if (bot.scheduler.doWorldBossImmediately) {
            return "w";
        } else if (bot.scheduler.doTrialsImmediately) {
            return "t";
        } else if (bot.scheduler.doGauntletImmediately) {
            return "g";
        } else if (bot.scheduler.doPVPImmediately) {
            return "p";
        } else if (bot.scheduler.doInvasionImmediately) {
            return "i";
        } else if (bot.scheduler.doGVGImmediately) {
            return "v";
        } else if (bot.scheduler.doExpeditionImmediately) {
            return "e";
        } else if (bot.scheduler.collectBountiesImmediately) {
            return "b";
        } else if (bot.scheduler.doFishingBaitsImmediately) {
            return "a";
        } else if (bot.scheduler.doFishingImmediately) {
            return "f";
        }

        //return null if no matches
        if (!bot.settings.activitiesEnabled.isEmpty()) {

            String activity;

            if (!bot.settings.activitiesRoundRobin) {
                activitysIterator = null;
                activitysIterator = bot.settings.activitiesEnabled.iterator(); //reset the iterator
            }

            //loop through in defined order, if we match activity and timer we select the activity
            while (activitysIterator.hasNext()) {

                try {
                    activity = activitysIterator.next(); //set iterator to string for .equals()
                } catch (ConcurrentModificationException e) {
                    activitysIterator = bot.settings.activitiesEnabled.iterator();
                    activity = activitysIterator.next();
                }

                if (activity.equals("r") && ((Misc.getTime() - timeLastShardsCheck) > (long) (15 * Misc.Durations.MINUTE))) {
                    return "r";
                } else if ("d".equals(activity) && ((Misc.getTime() - timeLastEnergyCheck) > ENERGY_CHECK_INTERVAL)) {
                    return "d";
                } else if ("w".equals(activity) && ((Misc.getTime() - timeLastXealsCheck) > XEALS_CHECK_INTERVAL)) {
                    return "w";
                } else if ("t".equals(activity) && ((Misc.getTime() - timeLastTrialsTokensCheck) > TOKENS_CHECK_INTERVAL)) {
                    return "t";
                } else if ("g".equals(activity) && ((Misc.getTime() - timeLastGauntletTokensCheck) > TOKENS_CHECK_INTERVAL)) {
                    return "g";
                } else if ("p".equals(activity) && ((Misc.getTime() - timeLastTicketsCheck) > TICKETS_CHECK_INTERVAL)) {
                    return "p";
                } else if ("i".equals(activity) && ((Misc.getTime() - timeLastInvBadgesCheck) > BADGES_CHECK_INTERVAL)) {
                    return "i";
                } else if ("v".equals(activity) && ((Misc.getTime() - timeLastGVGBadgesCheck) > BADGES_CHECK_INTERVAL)) {
                    return "v";
                } else if ("e".equals(activity) && ((Misc.getTime() - timeLastExpBadgesCheck) > BADGES_CHECK_INTERVAL)) {
                    return "e";
                } else if ("b".equals(activity) && ((Misc.getTime() - timeLastBountyCheck) > (long) Misc.Durations.HOUR)) {
                    return "b";
                } else if ("a".equals(activity) && ((Misc.getTime() - timeLastFishingBaitsCheck) > (long) Misc.Durations.DAY)) {
                    return "a";
                } else if ("f".equals(activity) && ((Misc.getTime() - timeLastFishingCheck) > (long) Misc.Durations.DAY)) {
                    return "f";
                }
            }

            // If we reach this point activityIterator.hasNext() is false
            if (bot.settings.activitiesRoundRobin) {
                activitysIterator = bot.settings.activitiesEnabled.iterator();
            }

        }
        return null;
    }

    /**
     * This will handle dialog that open up when you encounter a boss for the first time, for example, or open a raid window or trials window for the first time, etc.
     */
    private void detectCharacterDialogAndHandleIt() {
        MarvinSegment right;
        MarvinSegment left;
        int steps = 0;

        while (true) {
            bot.browser.readScreen();

            right = MarvinSegment.fromCue(BHBotUnity.cues.get("DialogRight"), bot.browser);
            left = MarvinSegment.fromCue(BHBotUnity.cues.get("DialogLeft"), bot.browser);

            //if we don't find either exit
            if (left == null && right == null) break;

            // if we find left or right click them
            if (left != null) bot.browser.clickOnSeg(left);
            if (right != null) bot.browser.clickOnSeg(right);

            steps++;
            Misc.sleep(Misc.Durations.SECOND);
        }

        if (steps > 0)
            BHBotUnity.logger.info("Character dialog dismissed.");
    }

    /**
     * Returns number of tickets left (for PvP) in interval [0..10]. Returns -1 in case it cannot read number of tickets for some reason.
     */
    private int getTickets() {
        MarvinSegment seg;

        seg = MarvinSegment.fromCue(BHBotUnity.cues.get("TicketBar"), bot.browser);

        if (seg == null) // this should probably not happen
            return -1;

        int left = seg.x2 + 1;
        int top = seg.y1 + 6;

        final Color full = new Color(226, 42, 81);

        int value = 0;
        int maxTickets = bot.settings.maxTickets;

        // ticket bar is 80 pixels long (however last two pixels will have "medium" color and not full color (it's so due to shading))
        for (int i = 0; i < 78; i++) {
            value = i;
            Color col = new Color(bot.browser.getImg().getRGB(left + i, top));

            if (!col.equals(full))
                break;
        }

        value = value + 2; //add the last 2 pixels to get an accurate count
        return Math.round(value * (maxTickets / 77.0f)); // scale it to interval [0..10]
    }

    /**
     * Generi bar percentage reader.
     *
     * @param barLocator The MarvinSegment used to find the resource bar on the screen
     * @param maxResourceCnt Max count of the current resources
     * @param xOffset x offset. Based on the x2 of the barLocator parameter
     * @param yOffset y offset. Based on the y1 of the barLocator parameter
     * @param barColors A set of valid colors. If colors in this set are found, the bar percentage is increased
     * @param barImgRead The image used to read the bar percentage.
     * @return An integeger representing the available resources based on maxResourceCnt
     */
    static int readResourceBarPercentage(MarvinSegment barLocator, int maxResourceCnt, int xOffset, int yOffset, Set<Color> barColors, BufferedImage barImgRead) {
        if (barLocator == null) return -1;

        int left = barLocator.x2 + xOffset;
        int top = barLocator.y1 + yOffset;

        int value = 0;

        // Bar length is 80 pixels
        for (int i = 0; i < 80; i++) {
            value = i;
            Color col = new Color(barImgRead.getRGB(left + i, top));

            if (!barColors.contains(col)) break;
        }

        // the i index is 0 based
        value += 1;

        // the bar length is 80 pixels
        return (value * maxResourceCnt) / 80;
    }

    /**
     * Processes any kind of dungeon: <br>
     * - normal dungeon <br>
     * - raid <br>
     * - trial <br>
     * - gauntlet <br>
     * - world boss <br>
     */
    private void processDungeon() {
        MarvinSegment seg;
        bot.browser.readScreen();

        //region Start Time checks
        if (!isAdventureStarted) {
            activityStartTime = TimeUnit.MILLISECONDS.toSeconds(Misc.getTime());
            BHBotUnity.logger.debug(bot.getState().getName() + " start time: " + activityStartTime);
            isInFight = false; //true is in encounter, false is out of encounter
            positionChecker.resetStartPos();
            shrineManager.resetUsedInAdventure();
            shrineManager.setOutOfEncounterTimestamp(TimeUnit.MILLISECONDS.toSeconds(Misc.getTime()));
            shrineManager.setInEncounterTimestamp(TimeUnit.MILLISECONDS.toSeconds(Misc.getTime()));
            isAdventureStarted = true;

            // Every 7 minutes we check for the Kongregate motion bug
            kongMotionBugNexCheck = Misc.getTime() + (Misc.Durations.MINUTE * 7);
        }
        //endregion

        //region Kongregate motion bug
        if (Misc.getTime() >= kongMotionBugNexCheck && !isInFight) {
            bot.saveGameScreen("motion-error", "errors");
            Cue dungCueX = new Cue(BHBotUnity.cues.get("X"), Bounds.fromWidthHeight(735, 0, 70, 75));
            seg = MarvinSegment.fromCue(dungCueX, Misc.Durations.SECOND * 2, bot.browser);
            if (seg == null) {
                bot.saveGameScreen("motion-error-no-x", "errors");
                BHBotUnity.logger.error("You have been ported out of a dungeon. Restarting...");
                restart();
                return;
            }

            shrineManager.resetUsedInAdventure();
            BHBotUnity.logger.warn("Potential Kongregate bug detected, refreshing page.");
            bot.browser.refresh();
            Misc.sleep(Misc.Durations.MINUTE);
            kongMotionBugNexCheck = Misc.getTime() + (Misc.Durations.MINUTE * 7);
            shrineManager.setOutOfEncounterTimestamp(TimeUnit.MILLISECONDS.toSeconds(Misc.getTime()));
            return;
        }
        //endregion

        // long activityDuration = (TimeUnit.MILLISECONDS.toSeconds(Misc.getTime()) - activityStartTime);

        /*
         * Encounter detection code
         * We use guild button visibility to detect whether we are in combat
         */
        //region isInFight Detection
        MarvinSegment guildButtonSeg = MarvinSegment.fromCue(BHBotUnity.cues.get("GuildButton"), bot.browser);
        if (guildButtonSeg != null) {
            shrineManager.setOutOfEncounterTimestamp(TimeUnit.MILLISECONDS.toSeconds(Misc.getTime()));
            if (isInFight) {
                BHBotUnity.logger.trace("Updating idle time (Out of combat)");
                bot.scheduler.resetIdleTime(true);
                isInFight = false;

                // as we got into a fight we also update the kong bug counter
                kongMotionBugNexCheck = Misc.getTime() + (Misc.Durations.MINUTE * 7);
            }
        } else {
            shrineManager.setInEncounterTimestamp(TimeUnit.MILLISECONDS.toSeconds(Misc.getTime()));
            if (!isInFight) {
                BHBotUnity.logger.trace("Updating idle time (In combat)");
                bot.scheduler.resetIdleTime(true);
                isInFight = true;
                positionChecker.resetStartPos();

                // as we got out of a fight we also update the kong bug counter
                kongMotionBugNexCheck = Misc.getTime() + (Misc.Durations.MINUTE * 7);
            }
        }
        //endregion

        /*
         *  handleLoot code
         *  It's enabled in these activities to try and catch real-time loot drops, as the loot window automatically closes
         */
        //region Handle Loot
        if (bot.getState() == BHBotUnity.State.Raid || bot.getState() == BHBotUnity.State.Dungeon || bot.getState() == BHBotUnity.State.Expedition || bot.getState() == BHBotUnity.State.Trials) {
            handleLoot();
        }
        //endregion

        /*
         * autoRune Code
         */
        //region Auto Rune
        if (bot.settings.autoBossRune.containsKey(bot.getState().getShortcut()) && !isInFight) {
            runeManager.handleAutoBossRune(shrineManager.getOutOfEncounterTimestamp(), shrineManager.getInEncounterTimestamp());
        }
        //endregion

        /*
         * autoShrine Code
         */
        //region Auto Shrine
        if (bot.settings.autoShrine.contains(bot.getState().getShortcut()) && !isInFight) {
            shrineManager.processAutoShrine();
        }
        //endregion

        /*
         * autoRevive code
         * This also handles re-enabling auto
         */
        //region Auto Revive
        seg = MarvinSegment.fromCue(BHBotUnity.cues.get("AutoOff"), bot.browser);
        if (seg != null) {
            handleAutoOff();
        }
        //endregion

        /*
         * autoBribe/Persuasion code
         */
        //region Familiar Encounter
        if ((bot.getState() == BHBotUnity.State.Raid || bot.getState() == BHBotUnity.State.Dungeon) && isInFight) {
            seg = MarvinSegment.fromCue(BHBotUnity.cues.get("YouCurrentlyOwn"), bot.browser);
            if (seg != null) {
                encounterManager.processFamiliarEncounter();
            }
        }
        //endregion

        /*
         *  Skeleton key code
         *  encounterStatus is set to true as the window obscures the guild icon
         */
        //region Skeleton Key
        seg = MarvinSegment.fromCue(BHBotUnity.cues.get("SkeletonTreasure"), bot.browser);
        if (seg != null) {
            if (handleSkeletonKey()) {
                restart();
            }
        }
        //endregion

        // If you use Firefox, as there is no way to use an existing profile,
        // speed is set to 1x everytime you (re-)start the browser
        //region Speed check
        seg = MarvinSegment.fromCue(BHBotUnity.cues.get("SpeedTXT"), bot.browser);
        if (isInFight && seg != null) {
            handleAdventureSpeed();
        }

        //endregion

        /*
         *   Merchant offer check
         *   Not super common so we check every 5 seconds
         */
        //region Merchant Offer
        if (isInFight) {
            seg = MarvinSegment.fromCue(BHBotUnity.cues.get("Merchant"), bot.browser);
            if (seg != null) {
                seg = MarvinSegment.fromCue(BHBotUnity.cues.get("MerchantDecline"), bot.browser);
                if (seg != null) {
                    bot.browser.clickOnSeg(seg);
                } else BHBotUnity.logger.error("Merchant 'decline' cue not found");

                bot.browser.readScreen(Misc.Durations.SECOND);
                seg = MarvinSegment.fromCue(BHBotUnity.cues.get("YesGreen"), 5 * Misc.Durations.SECOND, Bounds.fromWidthHeight(290, 330, 85, 60), bot.browser);
                if (seg != null) {
                    bot.browser.clickOnSeg(seg);
                } else BHBotUnity.logger.error("Merchant 'yes' cue not found");
            }
        }
        //endregion

        /*
         *   Character dialogue check
         *   This is a one time event per account instance, so we don't need to check it very often
         *   encounterStatus is set to true as the dialogue obscures the guild icon
         */
        //region Character dialouge
        if (isInFight && (bot.getState() == BHBotUnity.State.Dungeon || bot.getState() == BHBotUnity.State.Raid)) {
            detectCharacterDialogAndHandleIt();
        }
        //endregion


        /*
         * The recap screen is also appearing during all the encounters, so to make sure that we are facing the final one,
         * we also search for the "Rerun" button
         */
        //region Cleared
        if (BHBotUnity.State.Raid.equals(bot.getState()) || BHBotUnity.State.Dungeon.equals(bot.getState())
                || BHBotUnity.State.Expedition.equals(bot.getState()) ||  BHBotUnity.State.Trials.equals(bot.getState())) {

            seg = MarvinSegment.fromCue(BHBotUnity.cues.get("ClearedRecap"), bot.browser);
            if (seg != null) {

                //Calculate activity stats
                counters.get(bot.getState()).increaseVictories();
                long activityRuntime = Misc.getTime() - activityStartTime * 1000; //get elapsed time in milliseconds
                String runtime = Misc.millisToHumanForm(activityRuntime);
                counters.get(bot.getState()).increaseVictoriesDuration(activityRuntime);
                String runtimeAvg = Misc.millisToHumanForm(counters.get(bot.getState()).getVictoryAverageDuration());
                //return stats
                BHBotUnity.logger.info(bot.getState().getName() + " #" + counters.get(bot.getState()).getTotal() + " completed. Result: Victory");
                BHBotUnity.logger.stats(bot.getState().getName() + " " + counters.get(bot.getState()).successRateDesc());
                BHBotUnity.logger.stats("Victory run time: " + runtime + ". Average: " + runtimeAvg + ".");

                //handle SuccessThreshold
                handleSuccessThreshold(bot.getState());

                resetAppropriateTimers();
                reviveManager.reset();

                if (BHBotUnity.State.Raid.equals(bot.getState()) && rerunCurrentActivity) {
                    setAutoOff(1000);

                    Cue raidRerun = new Cue(BHBotUnity.cues.get("Rerun"), Bounds.fromWidthHeight(285, 455, 110, 50));

                    bot.browser.closePopupSecurely(BHBotUnity.cues.get("ClearedRecap"), raidRerun);

                    // We are out of shards, so we get back to Main
                    seg = MarvinSegment.fromCue("NotEnoughShards", Misc.Durations.SECOND * 3, bot.browser);
                    if (seg != null) {

                        bot.browser.closePopupSecurely(BHBotUnity.cues.get("NotEnoughShards"), BHBotUnity.cues.get("No"));

                        rerunCurrentActivity = false;
                        bot.setState(BHBotUnity.State.Main);
                        return;
                    }

                    bot.setState(BHBotUnity.State.RerunRaid);
                } else {

                    if (bot.settings.useLegacyAdventureClose) {
                        Bounds townBounds = switch (bot.getState()) {
                            case Raid, Dungeon -> Bounds.fromWidthHeight(440, 455, 95, 50);
                            case Trials, Gauntlet -> Bounds.fromWidthHeight(365, 455, 95, 50);
                            default -> null;
                        };

                        //close 'cleared' popup
                        bot.browser.readScreen(Misc.Durations.SECOND); // The pop-up is bouncing let's wait for it to stabilize
                        Cue cueTown = new Cue(BHBotUnity.cues.get("Town"), townBounds);
                        bot.browser.closePopupSecurely(BHBotUnity.cues.get("ClearedRecap"), cueTown);

                        // close the activity window to return us to the main screen
                        if (!BHBotUnity.State.Expedition.equals(bot.getState())) {
                            bot.browser.readScreen(3 * Misc.Durations.SECOND); //wait for slide-in animation to finish

                            Cue XWithBounds;
                            Bounds xBounds;
                            switch (bot.getState()) {
                                case WorldBoss -> xBounds = Bounds.fromWidthHeight(640, 75, 60, 60);
                                case Raid -> xBounds = Bounds.fromWidthHeight(605, 85, 70, 70);
                                case Dungeon -> xBounds = Bounds.fromWidthHeight(695, 40, 70, 75);
                                case Trials, Gauntlet -> xBounds = Bounds.fromWidthHeight(615, 85, 70, 70);
                                default -> xBounds = null;
                            }

                            XWithBounds = new Cue(BHBotUnity.cues.get("X"), xBounds);

                            bot.browser.closePopupSecurely(XWithBounds, XWithBounds);
                        } else {
                            //For Expedition we need to close 3 windows (Exped/Portal/Team) to return to main screen
                            bot.browser.closePopupSecurely(BHBotUnity.cues.get("Enter"), BHBotUnity.cues.get("X"));
                            bot.browser.closePopupSecurely(BHBotUnity.cues.get("PortalBorderLeaves"), BHBotUnity.cues.get("X"));
                            bot.browser.closePopupSecurely(BHBotUnity.cues.get("BadgeBar"), BHBotUnity.cues.get("X"));
                        }
                    } else {
                        bot.browser.closeAllPopups("Main", 15, Misc.Durations.SECOND);
                    }

                    bot.setState(BHBotUnity.State.Main); // reset state
                }

                return;
            }
        }
        //endregion

        /*
         *  Check for the 'Victory' screen and handle post-activity tasks
         */
        //region Victory
        if (BHBotUnity.State.WorldBoss.equals(bot.getState()) || BHBotUnity.State.Gauntlet.equals(bot.getState())
                || BHBotUnity.State.Invasion.equals(bot.getState()) || BHBotUnity.State.PVP.equals(bot.getState())
                || BHBotUnity.State.GVG.equals(bot.getState())) {

            seg = MarvinSegment.fromCue(BHBotUnity.cues.get("VictoryRecap"), bot.browser);
            if (seg != null) {

                //Calculate activity stats
                counters.get(bot.getState()).increaseVictories();
                long activityRuntime = Misc.getTime() - activityStartTime * 1000; //get elapsed time in milliseconds
                String runtime = Misc.millisToHumanForm(activityRuntime);
                counters.get(bot.getState()).increaseVictoriesDuration(activityRuntime);
                String runtimeAvg = Misc.millisToHumanForm(counters.get(bot.getState()).getVictoryAverageDuration());
                //return stats
                BHBotUnity.logger.info(bot.getState().getName() + " #" + counters.get(bot.getState()).getTotal() + " completed. Result: Victory");
                BHBotUnity.logger.stats(bot.getState().getName() + " " + counters.get(bot.getState()).successRateDesc());
                BHBotUnity.logger.stats("Victory run time: " + runtime + ". Average: " + runtimeAvg + ".");

                //handle SuccessThreshold
                handleSuccessThreshold(bot.getState());

                //check for loot drops and send via Pushover/Screenshot
                handleLoot();

                if (bot.settings.useLegacyAdventureClose) {
                    Bounds townBounds = switch (bot.getState()) {
                        case Gauntlet -> Bounds.fromWidthHeight(320, 420, 160, 65);
                        case WorldBoss -> Bounds.fromWidthHeight(502, 459, 133, 38);
                        case GVG -> Bounds.fromWidthHeight(365, 455, 95, 50);
                        default -> null;
                    };

                    // If we are here the close button should be there
                    seg = MarvinSegment.fromCue(BHBotUnity.cues.get("Town"), 2 * Misc.Durations.SECOND, townBounds, bot.browser);
                    if (seg != null) {
                        bot.browser.clickOnSeg(seg);
                    } else {
                        BHBotUnity.logger.error("Victory pop-up error while performing " + bot.getState().getName() + "! Restarting the bot.");
                        restart();
                        return;
                    }

                    // close the activity window to return us to the main screen
                    bot.browser.readScreen(3 * Misc.Durations.SECOND); //wait for slide-in animation to finish

                    Bounds xBounds = switch (bot.getState()) {
                        case WorldBoss -> Bounds.fromWidthHeight(637, 80, 64, 61);
                        case GVG -> Bounds.fromWidthHeight(615, 90, 70, 70);
                        default -> null;
                    };

                    Cue XWithBounds = new Cue(BHBotUnity.cues.get("X"), xBounds);

                    bot.browser.closePopupSecurely(XWithBounds, BHBotUnity.cues.get("X"));
                } else {
                    bot.browser.closeAllPopups("Main", 15, Misc.Durations.SECOND);
                }

                //last few post activity tasks
                resetAppropriateTimers();
                reviveManager.reset();
                if (bot.getState() == BHBotUnity.State.GVG) dressUp(bot.settings.gvgstrip);
                if (bot.getState() == BHBotUnity.State.PVP) dressUp(bot.settings.pvpstrip);

                //return to main state
                bot.setState(BHBotUnity.State.Main); // reset state
                return;
            }
        }
        //endregion

        /*
         *  Check for the 'Defeat' dialogue and handle post-activity tasks
         *  Most activities have custom tasks on defeat
         */
        //region Defeat
        seg = MarvinSegment.fromCue(BHBotUnity.cues.get("DefeatRecap"), bot.browser);
        if (seg != null) {

            //Calculate activity stats
            counters.get(bot.getState()).increaseDefeats();
            long activityRuntime = Misc.getTime() - activityStartTime * 1000; //get elapsed time in milliseconds
            String runtime = Misc.millisToHumanForm(activityRuntime);
            counters.get(bot.getState()).increaseDefeatsDuration(activityRuntime);
            String runtimeAvg = Misc.millisToHumanForm(counters.get(bot.getState()).getDefeatAverageDuration());

            BHBotUnity.logger.warn(bot.getState().getName() + " #" + counters.get(bot.getState()).getTotal() + " completed. Result: Defeat.");
            BHBotUnity.logger.stats(bot.getState().getName() + " " + counters.get(bot.getState()).successRateDesc());
            BHBotUnity.logger.stats("Defeat run time: " + runtime + ". Average: " + runtimeAvg + ".");

            //check for invasion loot drops and send via Pushover/Screenshot
            /*if (BHBotUnity.State.Invasion.equals(bot.getState())) {
                handleLoot();
            }*/

            // Difficulty failsafe logic
            if (bot.getState().equals(BHBotUnity.State.Expedition) && bot.settings.difficultyFailsafe.containsKey("e")) {
                //Handle difficultyFailsafe for Expedition
                // The key is the difficulty decrease, the value is the minimum level
                Map.Entry<Integer, Integer> expedDifficultyFailsafe = bot.settings.difficultyFailsafe.get("e");
                int levelOffset = expedDifficultyFailsafe.getKey();
                int minimumLevel = expedDifficultyFailsafe.getValue();

                // We check that the level offset for expedition is a multiplier of 5
                int levelOffsetModule = levelOffset % 5;
                if (levelOffsetModule != 0) {
                    int newLevelOffset = levelOffset + (5 - levelOffsetModule);
                    BHBotUnity.logger.warn("Level offset " + levelOffset + " is not multiplier of 5, rounding it to " + newLevelOffset);
                    bot.settings.difficultyFailsafe.put("e", Maps.immutableEntry(newLevelOffset, minimumLevel));
                }

                // We calculate the new difficulty
                int newExpedDifficulty = expeditionFailsafeDifficulty - levelOffset;

                // We check that the new difficulty is not lower than the minimum
                if (newExpedDifficulty < minimumLevel) newExpedDifficulty = minimumLevel;
                if (newExpedDifficulty < 5) newExpedDifficulty = 5;

                // If the new difficulty is different from the current one, we update the ini setting
                if (newExpedDifficulty != expeditionFailsafeDifficulty) {
                    String original = expeditionFailsafePortal + " " + expeditionFailsafeDifficulty;
                    String updated = expeditionFailsafePortal + " " + newExpedDifficulty;
                    settingsUpdate(original, updated);
                }
            } else if (BHBotUnity.State.Trials.equals(bot.getState()) && bot.settings.difficultyFailsafe.containsKey("t")) {
                // Difficulty failsafe for trials
                // The key is the difficulty decrease, the value is the minimum level
                Map.Entry<Integer, Integer> trialDifficultyFailsafe = bot.settings.difficultyFailsafe.get("t");
                int levelOffset = trialDifficultyFailsafe.getKey();
                int minimumLevel = trialDifficultyFailsafe.getValue();

                // We calculate the new difficulty
                int newTrialDifficulty = bot.settings.difficultyTrials - levelOffset;

                // We check that the new difficulty is not lower than the minimum
                if (newTrialDifficulty < minimumLevel) newTrialDifficulty = minimumLevel;

                // If the new difficulty is different from the current one, we update the ini setting
                if (newTrialDifficulty != bot.settings.difficultyTrials) {
                    String original = "difficultyTrials " + bot.settings.difficultyTrials;
                    String updated = "difficultyTrials " + newTrialDifficulty;
                    settingsUpdate(original, updated);
                }
            } else if (BHBotUnity.State.Gauntlet.equals(bot.getState()) && bot.settings.difficultyFailsafe.containsKey("g")) {
                // Difficulty failsafe for Gauntlet
                // The key is the difficulty decrease, the value is the minimum level
                Map.Entry<Integer, Integer> gauntletDifficultyFailsafe = bot.settings.difficultyFailsafe.get("g");
                int levelOffset = gauntletDifficultyFailsafe.getKey();
                int minimumLevel = gauntletDifficultyFailsafe.getValue();

                // We calculate the new difficulty
                int newGauntletDifficulty = bot.settings.difficultyGauntlet - levelOffset;

                // We check that the new difficulty is not lower than the minimum
                if (newGauntletDifficulty < minimumLevel) newGauntletDifficulty = minimumLevel;

                // If the new difficulty is different from the current one, we update the ini setting
                if (newGauntletDifficulty != bot.settings.difficultyGauntlet) {
                    String original = "difficultyGauntlet " + bot.settings.difficultyGauntlet;
                    String updated = "difficultyGauntlet " + newGauntletDifficulty;
                    settingsUpdate(original, updated);
                }
            }

            resetAppropriateTimers();
            reviveManager.reset();

            if (bot.settings.useLegacyAdventureClose) {
                Bounds townBounds = switch (bot.getState()) {
                    case WorldBoss -> Bounds.fromWidthHeight(426, 460, 132, 36);
                    case Dungeon -> Bounds.fromWidthHeight(351, 458, 133, 40);
                    case Trials, Gauntlet, GVG, Invasion -> Bounds.fromWidthHeight(365, 455, 95, 50);
                    default -> null;
                };

                seg = MarvinSegment.fromCue("Town", 3 * Misc.Durations.SECOND, townBounds, bot.browser);

                if (seg != null) {
                    bot.browser.clickOnSeg(seg);
                } else {
                    Misc.saveScreen("defeat-pop-up-" + bot.getState(), "errors/defeatPopUp", BHBotUnity.includeMachineNameInScreenshots, bot.browser.getImg());
                    BHBotUnity.logger.warn("Problem: 'Defeat' popup detected but no 'Town' button detected in " + bot.getState().getName() + ".");
                    if (bot.getState() == BHBotUnity.State.PVP) dressUp(bot.settings.pvpstrip);
                    if (bot.getState() == BHBotUnity.State.GVG) dressUp(bot.settings.gvgstrip);
                    return;
                }

                // If tips are enabled we make sure that the dialog is correctly closed
                detectCharacterDialogAndHandleIt();

                if (bot.getState() != BHBotUnity.State.Expedition) {

                    Bounds xBounds = switch (bot.getState()) {
                        case Dungeon -> Bounds.fromWidthHeight(678, 37, 96, 90);
                        case WorldBoss -> Bounds.fromWidthHeight(639, 81, 63, 58);
                        case Trials, Gauntlet -> Bounds.fromWidthHeight(615, 85, 70, 70);
                        case GVG, Invasion -> Bounds.fromWidthHeight(615, 90, 70, 70);
                        default -> null;
                    };

                    Cue xButton = new Cue(BHBotUnity.cues.get("X"), xBounds);

                    //Close the activity window to return us to the main screen
                    bot.browser.readScreen(3 * Misc.Durations.SECOND); //wait for slide-in animation to finish
                    bot.browser.closePopupSecurely(xButton, BHBotUnity.cues.get("X"));
                } else {
                    //For Expedition we need to close 3 windows (Exped/Portal/Team) to return to main screen
                    bot.browser.closePopupSecurely(BHBotUnity.cues.get("Enter"), BHBotUnity.cues.get("X"));
                    bot.browser.closePopupSecurely(BHBotUnity.cues.get("PortalBorderLeaves"), BHBotUnity.cues.get("X"));
                    bot.browser.closePopupSecurely(BHBotUnity.cues.get("BadgeBar"), BHBotUnity.cues.get("X"));
                }
            } else {
                if (!bot.browser.closeAllPopups("Main", 8, Misc.Durations.SECOND)) {
                    // If tips are enabled we make sure that the dialog is correctly closed
                    detectCharacterDialogAndHandleIt();
                    bot.browser.closeAllPopups("Main", 8, Misc.Durations.SECOND);
                }
            }

            // We make sure to dress up
            if (BHBotUnity.State.PVP.equals(bot.getState()) && bot.settings.pvpstrip.size() > 0) dressUp(bot.settings.pvpstrip);
            if (BHBotUnity.State.GVG.equals(bot.getState()) && bot.settings.gvgstrip.size() > 0) dressUp(bot.settings.gvgstrip);

            // We make sure to disable autoShrine when defeated
            if (BHBotUnity.State.Trials.equals(bot.getState()) || BHBotUnity.State.Raid.equals(bot.getState())
                    || BHBotUnity.State.Expedition.equals(bot.getState())) {

                bot.browser.readScreen(Misc.Durations.SECOND);
                if (!shrineManager.updateShrineSettings(false, false)) {
                    BHBotUnity.logger.error("Impossible to disable autoShrine after defeat! Restarting..");
                    restart();
                }

                runeManager.reset();
                bot.browser.readScreen(Misc.Durations.SECOND * 2);
            }

            bot.setState(BHBotUnity.State.Main); // reset state
            return;
        }
        //endregion

        // at the end of processDungeon, we revert idle time change (in order for idle detection to function properly):
        bot.scheduler.restoreIdleTime();
    }


    /**
     * This method will take care of handling treasure chests found in raid and dungeons
     *
     * @return true if anny error happens, false on success
     */
    private boolean handleSkeletonKey() {
        MarvinSegment seg;

        // Let's check if we have skeleton keys or not
        seg = MarvinSegment.fromCue(BHBotUnity.cues.get("SkeletonNoKeys"), 2 * Misc.Durations.SECOND, bot.browser);

        final String declineMessage = seg != null ? "No skeleton keys, skipping.." : "Skeleton treasure found, declining.";
        final String acceptMessage = bot.settings.openSkeleton == 1 ? "Skeleton treasure found, attempting to use key" : "Raid Skeleton treasure found, attempting to use key";

        Bounds declineBounds = Bounds.fromWidthHeight(411, 373, 134, 39);
        Bounds greenYesBounds = Bounds.fromWidthHeight(290, 335, 85, 55);
        Bounds openBounds = Bounds.fromWidthHeight(276, 340, 114, 43);

        // we don't have skeleton keys or setting does not allow us to open chests
        if (seg != null || bot.settings.openSkeleton == 0) {
            BHBotUnity.logger.info(declineMessage);
            seg = MarvinSegment.fromCue(BHBotUnity.cues.get("Decline"), 5 * Misc.Durations.SECOND, declineBounds, bot.browser);
            if (seg != null) {
                bot.browser.clickOnSeg(seg);
                seg = MarvinSegment.fromCue(BHBotUnity.cues.get("YesGreen"), 5 * Misc.Durations.SECOND, greenYesBounds, bot.browser);
                if (seg != null) {
                    bot.browser.clickOnSeg(seg);
                } else {
                    Misc.saveScreen("treasure-decline-no-yes", "errors", BHBotUnity.includeMachineNameInScreenshots, bot.browser.getImg());
                    BHBotUnity.logger.error("Impossible to find yes button after decline in handleSkeletonKey");
                    bot.notificationManager.sendErrorNotification("Treasure chest error", "Skeleton Chest gump without YES button");
                    return true;
                }
            } else {
                Misc.saveScreen("treasure-no-decline", "errors", BHBotUnity.includeMachineNameInScreenshots, bot.browser.getImg());
                BHBotUnity.logger.error("Impossible to find Decline button in handleSkeletonKey");
                bot.notificationManager.sendErrorNotification("Treasure chest error", "Skeleton Chest gump without DECLINE button");
                return true;
            }
            return false;
        } else if (bot.settings.openSkeleton == 1 || (bot.settings.openSkeleton == 2 && bot.getState() == BHBotUnity.State.Raid)) {
            // Open all & Raid only
            BHBotUnity.logger.info(acceptMessage);
            seg = MarvinSegment.fromCue(BHBotUnity.cues.get("Open"), 5 * Misc.Durations.SECOND, openBounds, bot.browser);
            if (seg != null) {
                bot.browser.clickOnSeg(seg);
                seg = MarvinSegment.fromCue(BHBotUnity.cues.get("YesGreen"), 5 * Misc.Durations.SECOND, greenYesBounds, bot.browser);
                if (seg != null) {
                    bot.browser.clickOnSeg(seg);
                    if ((bot.settings.screenshots.contains("s"))) {
                        bot.saveGameScreen("skeleton-contents", "rewards");
                    }
                    return false;
                } else {
                    BHBotUnity.logger.error("Impossible to find yes button after open in handleSkeletonKey");
                    bot.saveGameScreen("treasure-open no-yes", "errors");
                    bot.notificationManager.sendErrorNotification("Treasure chest error", "Skeleton Chest gump without YES button");
                    return true;
                }
            } else {
                BHBotUnity.logger.error("Open button not found, restarting");
                bot.saveGameScreen("skeleton-treasure-no-open", "errors");
                bot.notificationManager.sendErrorNotification("Treasure chest error", "Skeleton Chest gump without OPEN button");
                return true;
            }

        } else {
            BHBotUnity.logger.info("Skeleton treasure found, declining.");
            seg = MarvinSegment.fromCue(BHBotUnity.cues.get("Decline"), 5 * Misc.Durations.SECOND, declineBounds, bot.browser);
            if (seg != null) {
                bot.browser.clickOnSeg(seg);
                seg = MarvinSegment.fromCue(BHBotUnity.cues.get("YesGreen"), 5 * Misc.Durations.SECOND, greenYesBounds, bot.browser);
                if (seg != null) {
                    bot.browser.clickOnSeg(seg);
                    return false;
                } else {
                    Misc.saveScreen("treasure-no-settings-decline-no-yes", "errors", BHBotUnity.includeMachineNameInScreenshots, bot.browser.getImg());
                    BHBotUnity.logger.error("Impossible to find yes button after decline with no settings in handleSkeletonKey");
                    bot.notificationManager.sendErrorNotification("Treasure chest error", "Skeleton Chest gump without YES button");
                    return true;
                }
            } else {
                Misc.saveScreen("treasure-no-settings-no-decline", "errors", BHBotUnity.includeMachineNameInScreenshots, bot.browser.getImg());
                BHBotUnity.logger.error("Impossible to find decline with no settings button in handleSkeletonKey");
                bot.notificationManager.sendErrorNotification("Treasure chest error", "Skeleton Chest gump without DECLINE button");
                return true;
            }
        }
    }

    private void handleAutoOff() {
        MarvinSegment seg;

        // Auto Revive is disabled, we re-enable Auto on Dungeon
        if ((bot.settings.autoRevive.size() == 0) || (bot.getState() != BHBotUnity.State.Trials && bot.getState() != BHBotUnity.State.Gauntlet
                && bot.getState() != BHBotUnity.State.Raid && bot.getState() != BHBotUnity.State.Expedition)) {
            BHBotUnity.logger.debug("AutoRevive disabled, reenabling auto.. State = '" + bot.getState() + "'");
            setAutoOn(0);
            bot.scheduler.resetIdleTime(true);
            return;
        }

        // if everyone dies autoRevive attempts to revive people on the defeat screen, this should prevent that
        seg = MarvinSegment.fromCue(BHBotUnity.cues.get("DefeatRecap"), Misc.Durations.SECOND, bot.browser);
        if (seg != null) {
            BHBotUnity.logger.autorevive("Defeat screen, skipping revive check");
            setAutoOn(Misc.Durations.SECOND);
            bot.browser.readScreen(Misc.Durations.SECOND);
            bot.scheduler.resetIdleTime(true);
            return;
        }

        seg = MarvinSegment.fromCue(BHBotUnity.cues.get("VictoryRecap"), 500, bot.browser);
        if (seg != null) {
            BHBotUnity.logger.autorevive("Victory popup, skipping revive check");
            setAutoOn(Misc.Durations.SECOND);

            seg = MarvinSegment.fromCue(BHBotUnity.cues.get("CloseGreen"), 2 * Misc.Durations.SECOND, bot.browser); // after enabling auto again the bot would get stuck at the victory screen, this should close it
            if (seg != null)
                bot.browser.clickOnSeg(seg);
            else {
                BHBotUnity.logger.warn("Problem: 'Victory' window has been detected, but no 'Close' button. Ignoring...");
                return;
            }
            bot.scheduler.resetIdleTime(true);
            return;
        }

        reviveManager.processAutoRevive();

        setAutoOn(Misc.Durations.SECOND);
        bot.scheduler.resetIdleTime(true);

        // after reviving we update encounter timestamp as it wasn't updating from processDungeon
        shrineManager.setInEncounterTimestamp(TimeUnit.MILLISECONDS.toSeconds(Misc.getTime()));

    }

    private void closeWorldBoss() {
        if (!bot.browser.closePopupSecurely(BHBotUnity.cues.get("DarkBlueStart"), new Cue(BHBotUnity.cues.get("X"), Bounds.fromWidthHeight(700, 50, 55, 60)))) {
            BHBotUnity.logger.error("first x Error returning to main screen from World Boss, restarting");
        }

        Cue yesGreenWB = new Cue(BHBotUnity.cues.get("YesGreen"), Bounds.fromWidthHeight(290, 330, 85, 60));
        if (!bot.browser.closePopupSecurely(yesGreenWB, yesGreenWB)) {
            BHBotUnity.logger.error("yesgreen Error returning to main screen from World Boss, restarting");
        }

        if (!bot.browser.closePopupSecurely(BHBotUnity.cues.get("WorldBossTitle"), new Cue(BHBotUnity.cues.get("X"), Bounds.fromWidthHeight(640, 75, 55, 55)))) {
            BHBotUnity.logger.error("second x Error returning to main screen from World Boss, restarting");
        }
    }

    private void settingsUpdate(String string, String updatedString) {

        try {
            // input the file content to the StringBuffer "input"
            BufferedReader file = new BufferedReader(new FileReader(Settings.configurationFile));
            String line;
            StringBuilder inputBuffer = new StringBuilder();

            //print lines to string with linebreaks
            while ((line = file.readLine()) != null) {
                inputBuffer.append(line);
                inputBuffer.append(System.getProperty("line.separator"));
            }
            String inputStr = inputBuffer.toString(); //load lines to string
            file.close();

            //find containing string and update with the output string from the function above
            if (inputStr.contains(string)) {
                inputStr = inputStr.replace(string, updatedString);
                BHBotUnity.logger.info("Replaced '" + string + "' with '" + updatedString + "' in " + Settings.configurationFile);
            } else BHBotUnity.logger.error("Error finding string: " + string);

            // write the string from memory over the existing file
            FileOutputStream fileOut = new FileOutputStream(Settings.configurationFile);
            fileOut.write(inputStr.getBytes());
            fileOut.close();

            bot.settings.load();  //reload the new settings file so the counter will be updated for the next bribe

        } catch (Exception e) {
            System.out.println("Problem writing to settings file");
        }
    }

    /**
     * @param z and integer with the desired zone.
     * @param d and integer with the desired dungeon.
     * @return null in case dungeon parameter is malformed (can even throw an exception)
     */
    private Point getDungeonIconPos(int z, int d) {
        if (z < 1 || z > 13) return null;
        if (d < 1 || d > 4) return null;

        switch (z) {
            case 1: // zone 1
                switch (d) {
                    case 1:
                        return new Point(240, 350);
                    case 2:
                        return new Point(580, 190);
                    case 3:
                        return new Point(660, 330);
                    case 4:
                        return new Point(410, 230);
                }
                break;
            case 2: // zone 2
                switch (d) {
                    case 1:
                        return new Point(215, 270);
                    case 2:
                        return new Point(550, 150);
                    case 3:
                        return new Point(515, 380);
                    case 4:
                        return new Point(400, 270);
                }
                break;
            case 3: // zone 3
                switch (d) {
                    case 1:
                        return new Point(145, 200);
                    case 2:
                        return new Point(430, 300);
                    case 3:
                        return new Point(565, 375);
                    case 4:
                        return new Point(570, 170);
                }
                break;
            case 4: // zone 4
                switch (d) {
                    case 1:
                        return new Point(300, 400);
                    case 2:
                        return new Point(260, 200);
                    case 3:
                        return new Point(650, 200);
                    case 4:
                        return new Point(400, 270);
                }
                break;
            case 5: // zone 5
                switch (d) {
                    case 1:
                        return new Point(150, 200);
                    case 2:
                        return new Point(410, 380);
                    case 3:
                        return new Point(630, 240);
                    case 4:
                        return new Point(550, 150);
                }
                break;
            case 6: // zone 6
                switch (d) {
                    case 1:
                        return new Point(150, 220);
                    case 2:
                        return new Point(500, 400);
                    case 3:
                        return new Point(550, 120);
                    case 4:
                        return new Point(400, 270);
                }
                break;
            case 7: // zone 7
                switch (d) {
                    case 1:
                        return new Point(215, 315);
                    case 2:
                        return new Point(570, 165);
                    case 3:
                        return new Point(400, 290);
                    case 4:
                        BHBotUnity.logger.warn("Zone 7 only has 3 dungeons, falling back to z7d2");
                        return new Point(650, 400);
                }
                break;
            case 8: // zone 8
                switch (d) {
                    case 1:
                        return new Point(570, 170);
                    case 2:
                        return new Point(650, 390);
                    case 3:
                        return new Point(250, 370);
                    case 4:
                        BHBotUnity.logger.warn("Zone 8 only has 3 dungeons, falling back to z8d2");
                        return new Point(570, 340);
                }
                break;
            case 9:
                switch (d) {
                    case 1:
                        return new Point(310, 165);
                    case 2:
                        return new Point(610, 190);
                    case 3:
                        return new Point(375, 415);
                    case 4:
                        BHBotUnity.logger.warn("Zone 9 only has 3 dungeons, falling back to z9d2");
                        return new Point(610, 190);
                }
                break;
            case 10:
                switch (d) {
                    case 1:
                        return new Point(468, 389);
                    case 2:
                        return new Point(428, 261);
                    case 3:
                        return new Point(145, 200);
                    case 4:
                        return new Point(585, 167);
                }
                break;
            case 11:
                switch (d) {
                    case 1:
                        return new Point(345, 408);
                    case 2:
                        return new Point(205, 160);
                    case 3:
                        return new Point(670, 205);
                    case 4:
                        BHBotUnity.logger.warn("Zone 11 only has 3 dungeons, falling back to z11d2");
                        return new Point(205, 160);
                }
            case 12:
                switch (d) {
                    case 1:
                        return new Point(567, 413);
                    case 2:
                        return new Point(460, 150);
                    case 3:
                        return new Point(560, 400);
                    case 4:
                        return new Point(405, 290);
                }
            case 13:
                switch (d) {
                    case 1:
                        return new Point(610, 346);
                    case 2:
                        return new Point(445, 202);
                    case 3:
                        return new Point(255, 295);
                    case 4:
                        return new Point(160, 145);
                }
        }

        return null;
    }

    /**
     * Function to return the name of the portal for console output
     */
    private String getExpeditionPortalName(int currentExpedition, String targetPortal) {
        if (currentExpedition > 5) {
            BHBotUnity.logger.error("Unexpected expedition int in getExpeditionPortalName: " + currentExpedition);
            return null;
        }

        if (!"p1".equals(targetPortal) && !"p2".equals(targetPortal)
                && !"p3".equals(targetPortal) && !"p4".equals(targetPortal)) {
            BHBotUnity.logger.error("Unexpected target portal in getExpeditionPortalName: " + targetPortal);
            return null;
        }

        return switch (currentExpedition) {
            case 1 -> // Hallowed Dimension
                    switch (targetPortal) {
                        case "p1" -> "Googarum's";
                        case "p2" -> "Svord's";
                        case "p3" -> "Twimbos";
                        case "p4" -> "X5-T34M's";
                        default -> null;
                    };
            case 2 -> // Inferno dimension
                    switch (targetPortal) {
                        case "p1" -> "Raleib's";
                        case "p2" -> "Blemo's";
                        case "p3" -> "Gummy's";
                        case "p4" -> "Zarlocks";
                        default -> null;
                    };
            case 3 -> switch (targetPortal) {
                case "p1" -> "Zorgo Crossing";
                case "p2" -> "Yackerz Tundra";
                case "p3" -> "Vionot Sewer";
                case "p4" -> "Grampa Hef's Heart";
                default -> null;
            };
            case 4 -> // Idol dimension
                    switch (targetPortal) {
                        case "p1" -> "Blublix";
                        case "p2" -> "Mowhi";
                        case "p3" -> "Wizbot";
                        case "p4" -> "Astamus";
                        default -> null;
                    };
            case 5 -> // Battle Bards!
                    switch (targetPortal) {
                        case "p1" -> "Hero Fest";
                        case "p2" -> "Burning Fam";
                        case "p3" -> "Melvapaloozo";
                        case "p4" -> "Bitstock";
                        default -> null;
                    };
            default -> null;
        };
    }

    /**
     * This method will return you the position of the desired portal or null if the portal could not be found.
     * To understand if portals are enabled, the method checks for pixel color in specific locations
     *
     * @param targetPortal in standard format, e.g. "h4/i4".
     * @return null in case dungeon parameter is malformed (can even throw an exception)
     */
    private Point getExpeditionIconPos(int currentExpedition, String targetPortal) {
        // Sanity check on portal length
        if (targetPortal.length() != 2) {
            BHBotUnity.logger.error("targetPortal length Mismatch in getExpeditionIconPos");
            return null;
        }

        // Sanity check on portal code
        if (!"p1".equals(targetPortal) && !"p2".equals(targetPortal)
                && !"p3".equals(targetPortal) && !"p4".equals(targetPortal)) {
            BHBotUnity.logger.error("Unexpected target portal in getExpeditionIconPos: " + targetPortal);
            return null;
        }

        // We transform portal code to int
        int portalInt = switch (targetPortal) {
            case "p1" -> 1;
            case "p2" -> 2;
            case "p3" -> 3;
            case "p4" -> 4;
            default -> 0;
        };

        // we check for white border to understand if the portal is enabled
        Point[] portalCheck = new Point[4];
        Point[] portalPosition = new Point[4];
        Color[] colorCheck = new Color[4];
        boolean[] portalEnabled = new boolean[4];

        if (currentExpedition == 1) { // Hallowed

            portalCheck[0] = new Point(190, 146); //Googarum
            portalCheck[1] = new Point(484, 205); //Svord
            portalCheck[2] = new Point(328, 339); //Twimbo
            portalCheck[3] = new Point(641, 345); //X5-T34M

            portalPosition[0] = new Point(200, 200); //Googarum
            portalPosition[1] = new Point(520, 220); //Svord
            portalPosition[2] = new Point(360, 360); //Twimbo
            portalPosition[3] = new Point(650, 380); //X5-T34M

            colorCheck[0] = Color.WHITE;
            colorCheck[1] = Color.WHITE;
            colorCheck[2] = Color.WHITE;
            colorCheck[3] = Color.WHITE;
        } else if (currentExpedition == 2) { // Inferno
            portalCheck[0] = new Point(185, 206); // Raleib
            portalCheck[1] = new Point(570, 209); // Blemo
            portalCheck[2] = new Point(383, 395); // Gummy
            portalCheck[3] = new Point(381, 265); // Zarlock

            portalPosition[0] = new Point(200, 195); // Raleib
            portalPosition[1] = new Point(600, 195); // Blemo
            portalPosition[2] = new Point(420, 405); // Gummy
            portalPosition[3] = new Point(420, 270); // Zarlock

            colorCheck[0] = Color.WHITE;
            colorCheck[1] = Color.WHITE;
            colorCheck[2] = Color.WHITE;
            colorCheck[3] = Color.WHITE;
        } else if (currentExpedition == 3) { // Jammie
            portalCheck[0] = new Point(145, 187); // Zorgo
            portalCheck[1] = new Point(309, 289); // Yackerz
            portalCheck[2] = new Point(474, 343); // Vionot
            portalCheck[3] = new Point(621, 370); // Grampa

            portalPosition[0] = new Point(170, 200); // Zorgo
            portalPosition[1] = new Point(315, 260); // Yackerz
            portalPosition[2] = new Point(480, 360); // Vinot
            portalPosition[3] = new Point(635, 385); // Grampa

            colorCheck[0] = Color.WHITE;
            colorCheck[1] = Color.WHITE;
            colorCheck[2] = Color.WHITE;
            colorCheck[3] = Color.WHITE;
        } else if (currentExpedition == 4) { // Idol
            portalCheck[0] = new Point(370, 141); // Blublix
            portalCheck[1] = new Point(229, 372); // Mowhi
            portalCheck[2] = new Point(535, 351); // Wizbot
            portalCheck[3] = new Point(371, 323); // Astamus

            portalPosition[0] = new Point(400, 165); // Blublix
            portalPosition[1] = new Point(243, 385); // Mowhi
            portalPosition[2] = new Point(562, 375); // Wizbot
            portalPosition[3] = new Point(400, 318); // Astamus

            colorCheck[0] = Color.WHITE;
            colorCheck[1] = Color.WHITE;
            colorCheck[2] = Color.WHITE;
            colorCheck[3] = new Color(251, 201, 126);
        } else { // Battle Bards!
            portalCheck[0] = new Point(387, 152); // Hero Fest
            portalCheck[1] = new Point(253, 412); // Burning Fam
            portalCheck[2] = new Point(568, 418); // Melvapaloozo
            portalCheck[3] = new Point(435, 306); // Bitstock

            portalPosition[0] = new Point(402, 172); // Hero Fest
            portalPosition[1] = new Point(240, 371); // Burning Fam
            portalPosition[2] = new Point(565, 383); // Melvapaloozo
            portalPosition[3] = new Point(396, 315); // Bitstock

            colorCheck[0] = Color.WHITE;
            colorCheck[1] = Color.WHITE;
            colorCheck[2] = new Color(255, 254, 255); //Melvapaloozo is one bit off pure white for some reason
            colorCheck[3] = Color.WHITE;
        }

        // We check which of the portals are enabled
        for (int i = 0; i <= 3; i++) {
            if (!bot.browser.isDoNotShareUrl()) {
                Color col = new Color(bot.browser.getImg().getRGB(portalCheck[i].x, portalCheck[i].y));
                portalEnabled[i] = col.equals(colorCheck[i]);
            } else {
                Color col = new Color(bot.browser.getImg().getRGB(portalCheck[i].x - 1, portalCheck[i].y - 3));
                portalEnabled[i] = col.equals(colorCheck[i]);
            }

        }

        if (portalEnabled[portalInt - 1]) {
            return portalPosition[portalInt - 1];
        }

        // If the desired portal is not enabled, we try to find the highest enabled one
        String portalName = getExpeditionPortalName(currentExpedition, targetPortal);
        int i = 3;
        while (i >= 0) {
            if (portalEnabled[i]) {
                BHBotUnity.logger.warn(portalName + " is not available! Falling back on p" + (i + 1) + "...");
                return portalPosition[i];
            }
            i--; //cycle down through 4 - 1 until we return an activated portal
        }

        return null;
    }

    /**
     * Check world boss inputs are valid
     **/
    private boolean checkWorldBossInput(Settings.WorldBossSetting wbSetting) {
        final long MAX_TIMER = 600;

        WorldBoss wb = WorldBoss.fromLetter(String.valueOf(wbSetting.type));

        //check name
        if (wb == null) {
            BHBotUnity.logger.error("Invalid world boss name, check settings file");
            return false;
        }

        //check tier
        if (wbSetting.tier < wb.minTier || wbSetting.tier > wb.maxTier) {
            BHBotUnity.logger.error("Invalid world boss tier for " + wb.getName() + ", must be between " + wb.getMinTier() + " and " + wb.getMaxTier());
            return false;
        }

        //warn user if timer is over 5 minutes
        if (wbSetting.timer > MAX_TIMER) {
            BHBotUnity.logger.warn("Warning: Timer longer than " + Misc.millisToHumanForm(MAX_TIMER * 1000));
            return false;
        }
        return true;
    }

    /**
     * Returns a random adventure configuration. The logic takes care of giving priority to exact days configuration over the * ones
     *
     * @return a Settings.AdventureSetting element to be used
     */
    private Settings.AdventureSetting decideAdventureRandomly(List<Settings.AdventureSetting> startList) {
        RandomCollection<Settings.AdventureSetting> randomRaid = new RandomCollection<>();

        // We create a random collection that is specific for the current day
        String todayNum = new SimpleDateFormat("u").format(new Date());
        for (Settings.AdventureSetting setting : startList) {
            if (setting.weekDay.contains(todayNum)) randomRaid.add(setting.chanceToRun, setting);
        }

        if (randomRaid.size() > 0) return randomRaid.next();

        // We create a random collection
        for (Settings.AdventureSetting setting : startList) {
            if (setting.weekDay.contains("*")) randomRaid.add(setting.chanceToRun, setting);
        }

        if (randomRaid.size() > 0) return randomRaid.next();

        return null;
    }

    void expeditionReadTest() {
        String expedition = bot.settings.expeditions.next();
        if (expedition != null) {
            expedition = expedition.split(" ")[0];
            BHBotUnity.logger.info("Expedition chosen: " + expedition);
        }
    }

    /**
     * Note: world boss window must be open for this to work!
     * <p>
     * Returns false in case it failed.
     */
    private boolean handleWorldBossSelection(WorldBoss desiredWorldBoss) {

        MarvinSegment seg;

        // we refresh the screen
        bot.browser.readScreen(Misc.Durations.SECOND);

        int wbUnlocked = 0;
        int desiredWB = desiredWorldBoss.getNumber();

        // we get the grey dots on the raid selection popup
        List<MarvinSegment> wbDotsList = FindSubimage.findSubimage(bot.browser.getImg(), BHBotUnity.cues.get("cueRaidLevelEmpty").im, 1.0, true, false, 0, 0, 0, 0);
        // we update the number of unlocked raids
        wbUnlocked += wbDotsList.size();

        // A  temporary variable to save the position of the current selected raid
        int selectedWBX1;

        seg = MarvinSegment.fromCue(BHBotUnity.cues.get("RaidLevel"), bot.browser);
        if (seg != null) {
            wbUnlocked += 1;
            selectedWBX1 = seg.getX1();
            wbDotsList.add(seg);
        } else {
            BHBotUnity.logger.error("Impossible to detect the currently selected green cue!");
            return false;
        }

        WorldBoss unlockedWB = WorldBoss.fromNumber(wbUnlocked);
        if (unlockedWB == null) {
            BHBotUnity.logger.error("Unknown unlocked World Boss integer: " + wbUnlocked);
            return false;
        }

        BHBotUnity.logger.debug("Detected: WB " + unlockedWB.getName() + " unlocked");

        if (wbUnlocked < desiredWB) {
            BHBotUnity.logger.warn("World Boss selected in settings (" + desiredWorldBoss.getName() + ") is higher than world boss unlocked, running highest available (" + unlockedWB.getName() + ")");
            desiredWB = wbUnlocked;
        }

        // we sort the list of dots, using the x1 coordinate
        wbDotsList.sort(comparing(MarvinSegment::getX1));

        int selectedWB = 0;
        for (MarvinSegment raidDotSeg : wbDotsList) {
            selectedWB++;
            if (raidDotSeg.getX1() == selectedWBX1) break;
        }

        WorldBoss wbSelected = WorldBoss.fromNumber(selectedWB);
        if (wbSelected == null) {
            BHBotUnity.logger.error("Unknown selected World Boss integer: " + wbUnlocked);
            return false;
        }

        BHBotUnity.logger.debug("WB selected is " + wbSelected.getName());

        if (selectedWB == 0) { // an error!
            BHBotUnity.logger.error("It was impossible to determine the currently selected raid!");
            return false;
        }

        if (selectedWB != desiredWB) {
            // we need to change the raid type!
            BHBotUnity.logger.info("Changing from WB " + wbSelected.getName() + " to WB " + desiredWorldBoss.getName());
            // we click on the desired cue
            bot.browser.clickOnSeg(wbDotsList.get(desiredWB - 1));
        }

        return true;
    }

    /**
     * Note: raid window must be open for this to work!
     * <p>
     * Returns false in case it failed.
     */
    private boolean handleRaidSelection(String desiredRaidZone, int difficulty) {

        int desiredRaid = Integer.parseInt(desiredRaidZone);

        MarvinSegment seg;

        // we refresh the screen
        bot.browser.readScreen(Misc.Durations.SECOND);

        int raidUnlocked = 0;
        // we get the grey dots on the raid selection popup
        List<MarvinSegment> raidDotsList = FindSubimage.findSubimage(bot.browser.getImg(), BHBotUnity.cues.get("cueRaidLevelEmpty").im, 1.0, true, false, 0, 0, 0, 0);
        // we update the number of unlocked raids
        raidUnlocked += raidDotsList.size();

        // Is only R1 unlocked?
        boolean onlyR1 = false;
        if (raidUnlocked == 0 && MarvinSegment.fromCue(BHBotUnity.cues.get("Raid1Name"), bot.browser) != null) {
            raidUnlocked += 1;
            onlyR1 = true;
        }

        // A  temporary variable to save the position of the current selected raid
        int selectedRaidX1 = 0;

        // we look for the the currently selected raid, the green dot
        if (!onlyR1) {
            seg = MarvinSegment.fromCue(BHBotUnity.cues.get("RaidLevel"), bot.browser);
            if (seg != null) {
                raidUnlocked += 1;
                selectedRaidX1 = seg.getX1();
                raidDotsList.add(seg);
            } else {
                BHBotUnity.logger.error("Impossible to detect the currently selected green cue!");
                return false;
            }
        }

        BHBotUnity.logger.debug("Detected: R" + raidUnlocked + " unlocked");

        if (raidUnlocked < desiredRaid) {
            BHBotUnity.logger.warn("Raid selected in settings (R" + desiredRaidZone + ") is higher than raid level unlocked, running highest available (R" + raidUnlocked + ")");
            desiredRaid = raidUnlocked;
        }

        BHBotUnity.logger.info("Attempting R" + desiredRaidZone + " " + (difficulty == 1 ? "Normal" : difficulty == 2 ? "Hard" : "Heroic"));

        // we sort the list of dots, using the x1 coordinate
        raidDotsList.sort(comparing(MarvinSegment::getX1));

        int selectedRaid = 0;
        if (!onlyR1) {
            for (MarvinSegment raidDotSeg : raidDotsList) {
                selectedRaid++;
                if (raidDotSeg.getX1() == selectedRaidX1) break;
            }
        } else {
            selectedRaid = 1;
        }

        BHBotUnity.logger.debug("Raid selected is R" + selectedRaid);

        if (selectedRaid == 0) { // an error!
            BHBotUnity.logger.error("It was impossible to determine the currently selected raid!");
            return false;
        }

        if (!onlyR1 && (selectedRaid != desiredRaid)) {
            // we need to change the raid type!
            BHBotUnity.logger.info("Changing from R" + selectedRaid + " to R" + desiredRaidZone);

            // TODO fix kong selection bug
            if (desiredRaid >= 2) {
                bot.browser.clickInGame(645, 302);
            }

            // we click on the desired cue
            bot.browser.clickOnSeg(raidDotsList.get(desiredRaid - 1));
        }

        return true;
    }

    private void handleAdventureConfiguration(BHBotUnity.State state, boolean closeActivityWindow, Bounds xButtonBounds) {

        if (closeActivityWindow) {
            if (bot.settings.autoShrine.contains(state.getShortcut())
                    || bot.settings.autoRune.containsKey(state.getShortcut())
                    || bot.settings.autoBossRune.containsKey(state.getShortcut())) {

                BHBotUnity.logger.debug("Closing adventure window for " + state.getName());
                tryClosingAdventureWindow(xButtonBounds);
            }
        }

        //autoshrine
        if (bot.settings.autoShrine.contains(state.getShortcut())) {
            BHBotUnity.logger.info("Configuring autoShrine for " + state.getName());
            if (!shrineManager.updateShrineSettings(true, true)) {
                BHBotUnity.logger.error("Impossible to configure autoShrine for " + state.getName());
            }
        }

        //autoBossRune
        if (bot.settings.autoBossRune.containsKey(state.getShortcut()) && !bot.settings.autoShrine.contains(state.getShortcut())) { //if autoshrine disabled but autobossrune enabled
            BHBotUnity.logger.info("Configuring autoBossRune for " + state.getName());
            if (!shrineManager.updateShrineSettings(true, false)) {
                BHBotUnity.logger.error("Impossible to configure autoBossRune for " + state.getName());
            }
        }

        //activity runes
        runeManager.processAutoRune(state);

    }

    /**
     * Handles popup that tells you that your team is not complete. Happens when some friend left you.
     * This method will attempt to click on "Auto" button to refill your team.
     * Note that this can happen in raid and GvG only, since in other games (PvP, Gauntlet/Trials) you can use only familiars.
     * In GvG, on the other hand, there is additional dialog possible (which is not possible in raid): "team not ordered" dialog.
     *
     * @return true in case emergency restart is needed.
     */
    private boolean handleTeamMalformedWarning() {

        // We look for the team text on top of the text pop-up
        MarvinSegment seg = MarvinSegment.fromCue(BHBotUnity.cues.get("Team"), Misc.Durations.SECOND * 3, bot.browser);
        if (seg == null) {
            return false;
        }

        if (MarvinSegment.fromCue(BHBotUnity.cues.get("TeamNotFull"), Misc.Durations.SECOND, bot.browser) != null || MarvinSegment.fromCue(BHBotUnity.cues.get("TeamNotOrdered"), Misc.Durations.SECOND, bot.browser) != null) {
            bot.browser.readScreen(Misc.Durations.SECOND);
            seg = MarvinSegment.fromCue(BHBotUnity.cues.get("No"), 2 * Misc.Durations.SECOND, bot.browser);
            if (seg == null) {
                BHBotUnity.logger.error("Error: 'Team not full/ordered' window detected, but no 'No' button found. Restarting...");
                return true;
            }
            bot.browser.closePopupSecurely(BHBotUnity.cues.get("No"), BHBotUnity.cues.get("No"));
            bot.browser.readScreen();

            seg = MarvinSegment.fromCue(BHBotUnity.cues.get("AutoTeam"), 2 * Misc.Durations.SECOND, bot.browser);
            if (seg == null) {
                BHBotUnity.logger.error("Error: 'Team not full/ordered' window detected, but no 'Auto' button found. Restarting...");
                return true;
            }
            bot.browser.clickOnSeg(seg);

            bot.browser.readScreen();
            Cue acceptTeam = BHBotUnity.cues.get("TeamAccept");
            seg = MarvinSegment.fromCue(acceptTeam, 2 * Misc.Durations.SECOND, bot.browser);
            if (seg == null) {
                BHBotUnity.logger.error("Error: 'Team not full/ordered' window detected, but no 'Accept' button found. Restarting...");
                return true;
            }

            String message = "'Team not full/ordered' dialog detected and handled - team has been auto assigned!";

            bot.notificationManager.sendErrorNotification("Team auto assigned", message);

            //bot.browser.clickOnSeg(seg);
            bot.browser.closePopupSecurely(acceptTeam, acceptTeam);

            BHBotUnity.logger.info(message);
        }

        return false; // all OK
    }

    private boolean handleGuildLeaveConfirm() {
        bot.browser.readScreen();
        if (MarvinSegment.fromCue(BHBotUnity.cues.get("GuildLeaveConfirm"), Misc.Durations.SECOND * 3, bot.browser) != null) {
            Misc.sleep(500); // in case popup is still sliding downward
            bot.browser.readScreen();
            MarvinSegment seg = MarvinSegment.fromCue(BHBotUnity.cues.get("YesGreen"), 10 * Misc.Durations.SECOND, bot.browser);
            if (seg == null) {
                BHBotUnity.logger.error("Error: 'Guild Leave Confirm' window detected, but no 'Yes' green button found. Restarting...");
                return true;
            }
            bot.browser.clickOnSeg(seg);
            Misc.sleep(2 * Misc.Durations.SECOND);

            BHBotUnity.logger.info("'Guild Leave' dialog detected and handled!");
        }

        return false; // all ok
    }

    private Boolean handleDisabledBattles() {
        bot.browser.readScreen();
        if (MarvinSegment.fromCue(BHBotUnity.cues.get("DisabledBattles"), Misc.Durations.SECOND * 3, bot.browser) != null) {
            Misc.sleep(500); // in case popup is still sliding downward
            bot.browser.readScreen();
            MarvinSegment seg = MarvinSegment.fromCue(BHBotUnity.cues.get("Close"), 10 * Misc.Durations.SECOND, bot.browser);
            if (seg == null) {
                BHBotUnity.logger.error("Error: 'Disabled battles' popup detected, but no 'Close' blue button found. Restarting...");
                return null;
            }
            bot.browser.clickOnSeg(seg);
            Misc.sleep(2 * Misc.Durations.SECOND);

            BHBotUnity.logger.info("'Disabled battles' popup detected and handled!");
            return true;
        }

        return false; // all ok, battles are enabled
    }

    /**
     * Will check if "Not enough energy" popup is open. If it is, it will automatically close it and close all other windows
     * until it returns to the main screen.
     *
     * @return true in case popup was detected and closed.
     */
    private boolean handleNotEnoughEnergyPopup() {
        MarvinSegment seg = MarvinSegment.fromCue(BHBotUnity.cues.get("NotEnoughEnergy"), Misc.Durations.SECOND * 3, bot.browser);
        if (seg != null) {
            // we don't have enough energy!
            BHBotUnity.logger.warn("Problem detected: insufficient energy to attempt dungeon. Cancelling...");
            bot.browser.closePopupSecurely(BHBotUnity.cues.get("NotEnoughEnergy"), BHBotUnity.cues.get("No"));

            bot.browser.closePopupSecurely(BHBotUnity.cues.get("AutoTeam"), BHBotUnity.cues.get("X"));

            // if D4 close the dungeon info window, else close the char selection screen
            if (specialDungeon) {
                seg = MarvinSegment.fromCue(BHBotUnity.cues.get("X"), 5 * Misc.Durations.SECOND, bot.browser);
                if (seg != null)
                    bot.browser.clickOnSeg(seg);
                specialDungeon = false;
            } else {
                // close difficulty selection screen:
                bot.browser.closePopupSecurely(new Cue(BHBotUnity.cues.get("Normal"), Bounds.fromWidthHeight(155, 225, 110, 40)), BHBotUnity.cues.get("X"));
            }

            // close zone view window:
            bot.browser.closePopupSecurely(BHBotUnity.cues.get("ZonesButton"), BHBotUnity.cues.get("X"));

            return true;
        } else {
            return false;
        }
    }

    /**
     * Will check if "Not enough xeals" popup is open. If it is, it will automatically close it and close all other windows
     * until it returns to the main screen.
     *
     * @return true in case popup was detected and closed.
     */
    private boolean handleNotEnoughXealsPopup() {
        MarvinSegment seg = MarvinSegment.fromCue("NotEnoughXeals", Misc.Durations.SECOND * 3, bot.browser);
        if (seg != null) {
            // we don't have enough xeals!
            BHBotUnity.logger.warn("Problem detected: insufficient xeals to attempt Wold Boss. Cancelling...");
            bot.browser.closePopupSecurely(BHBotUnity.cues.get("NotEnoughXeals"), BHBotUnity.cues.get("No"));

            bot.browser.closePopupSecurely(BHBotUnity.cues.get("WorldBossSummonTitle"), BHBotUnity.cues.get("X"));

            bot.browser.closePopupSecurely(BHBotUnity.cues.get("WorldBossTitle"), BHBotUnity.cues.get("X"));

            return true;
        } else {
            return false;
        }
    }

    /**
     * Will check if "Not enough tokens" popup is open. If it is, it will automatically close it and close all other windows
     * until it returns to the main screen.
     *
     * @return null if error, true in case popup was detected and closed, false otherwise.
     */
    private Boolean handleNotEnoughTokensPopup(boolean closeTeamWindow) {
        MarvinSegment seg = MarvinSegment.fromCue("NotEnoughTokens", bot.browser);

        if (seg != null) {
            BHBotUnity.logger.warn("Not enough token popup detected! Closing trial window.");

            if (!bot.browser.closePopupSecurely(BHBotUnity.cues.get("NotEnoughTokens"), BHBotUnity.cues.get("No"))) {
                BHBotUnity.logger.error("Impossible to close the 'Not Enough Tokens' pop-up window. Restarting");
                return null;
            }

            if (closeTeamWindow) {
                // This is generic so we are not sure about accept button posion
                Cue teamAccept = new Cue(BHBotUnity.cues.get("TeamAccept"), null);
                if (!bot.browser.closePopupSecurely(teamAccept, BHBotUnity.cues.get("X"))) {
                    BHBotUnity.logger.error("Impossible to close the team window when no tokens are available. Restarting");
                    return null;
                }
            }

            if (!bot.browser.closePopupSecurely(BHBotUnity.cues.get("TokenBar"), BHBotUnity.cues.get("X"))) {
                BHBotUnity.logger.error("Impossible to close the 'TokenBar' window. Restarting");
                return null;
            }

            return true;
        }
        return false;
    }

    /**
     * This method will handle the success threshold based on the state
     *
     * @param state the State used to check the success threshold
     */
    private void handleSuccessThreshold(BHBotUnity.State state) {

        // We only handle Trials and Gautlets
        if (bot.getState() != BHBotUnity.State.Gauntlet && bot.getState() != BHBotUnity.State.Trials) return;

        BHBotUnity.logger.debug("Victories in a row for " + state + " is " + counters.get(bot.getState()).getVictoriesInARow());

        // We make sure that we have a setting for the current state
        if (bot.settings.successThreshold.containsKey(bot.getState().getShortcut())) {
            Map.Entry<Integer, Integer> successThreshold = bot.settings.successThreshold.get(bot.getState().getShortcut());
            int minimumVictories = successThreshold.getKey();
            int lvlIncrease = successThreshold.getValue();

            if (counters.get(bot.getState()).getVictoriesInARow() >= minimumVictories) {
                if ("t".equals(bot.getState().getShortcut()) || "g".equals(bot.getState().getShortcut())) {
                    int newDifficulty;
                    String original, updated;

                    if ("t".equals(bot.getState().getShortcut())) {
                        newDifficulty = bot.settings.difficultyTrials + lvlIncrease;
                        original = "difficultyTrials " + bot.settings.difficultyTrials;
                        updated = "difficultyTrials " + newDifficulty;
                    } else { // Gauntlets
                        newDifficulty = bot.settings.difficultyGauntlet + lvlIncrease;
                        original = "difficultyGauntlet " + bot.settings.difficultyGauntlet;
                        updated = "difficultyGauntlet " + newDifficulty;
                    }

                    settingsUpdate(original, updated);
                }
            }
        }
    }

    /**
     * Make sure that adventures run at the best possible speed. Altough this code looks a bit weird, this is the only
     * possible alternative as the speed cue is somehow transparent and the yellow arrows are of a different color on every run
     */
    private void handleAdventureSpeed() {

        // Speed is already at maximum
        if (adventureSpeed == 3) return;

        // Screen regions we expect to be yellow
        Bounds boundsSpeed1X = Bounds.fromLength(23, 495, 2);
        Bounds boundsSpeed2X = Bounds.fromLength(37, 495, 2);
        Bounds boundsSpeed3X = Bounds.fromLength(50, 495, 2);

        // We refresh the screen
        bot.browser.readScreen();
        BufferedImage speedImg = bot.browser.getImg();

        // As we do not know the exact color of the yellow, we get colors from a region and store them in a set
        int[] pixels1X = speedImg.getRGB(boundsSpeed1X.x1, boundsSpeed1X.y1, boundsSpeed1X.width, boundsSpeed1X.height, null, 0, boundsSpeed1X.width);
        Set<Integer> speedActive = Arrays.stream(pixels1X).boxed().collect(Collectors.toSet());

        Set<Integer> intersection;

        if (adventureSpeed < 2) {
            intersection = new HashSet<>(speedActive);

            // We get colors for 2X speed region
            int[] pixels2X = speedImg.getRGB(boundsSpeed2X.x1, boundsSpeed2X.y1, boundsSpeed2X.width, boundsSpeed2X.height, null, 0, boundsSpeed2X.width);
            Set<Integer> speed2X = Arrays.stream(pixels2X).boxed().collect(Collectors.toSet());

            // We intersect speed1X with speed 2X and check the size of the intersection
            intersection.retainAll(speed2X);

            // If there is no intersection, it means that 2X speed is disabled
            if (intersection.size() == 0) {
                BHBotUnity.logger.debug("Speed set to 2X");
                bot.browser.clickInGame(boundsSpeed2X.x1, boundsSpeed2X.y1);
            }

            adventureSpeed = 2;
        }

        if (adventureSpeed < 3) {
            intersection = new HashSet<>(speedActive);

            // We also check 3X speed
            int[] pixels3X = speedImg.getRGB(boundsSpeed3X.x1, boundsSpeed3X.y1, boundsSpeed3X.width, boundsSpeed3X.height, null, 0, boundsSpeed3X.width);
            Set<Integer> speed3X = Arrays.stream(pixels3X).boxed().collect(Collectors.toSet());

            intersection.retainAll(speed3X);

            if (intersection.size() == 0) {
                BHBotUnity.logger.debug("Speed set to 3X");
                bot.browser.clickInGame(boundsSpeed3X.x1, boundsSpeed3X.y1);
            }

            adventureSpeed = 3;
        }
    }

    /**
     * Reads number from a given image
     *
     * @param im The BufferedImage to read the number from
     * @param numberPrefix The prefix used to load the cues to read the numbers. It is possible to provide more than one
     *                     prefix separating them using the comma
     * @param intToSkip It may be possible that not all the numbers are available for a specific Cue set, to avoid
     *                  NullPointerException specify the numbers you want to skip
     * @param breakOnMatch When more than one prefix is passed in numberPrefix parameter, if this is set to true, as soon
     *                     as one match is found with one prefix, the logic will not check the remaining prefixes.
     * @param logEmptyResults If you want to troubleshoot number reading, set this to true and when the read number has
     *                        no value, a picutre of the input im will be save in the debug screenshot folder
     * @return The value of the read number or 0 if it was not possible to read the number
     */
    static int readNumFromImg(BufferedImage im, String numberPrefix, Set<Integer> intToSkip, boolean breakOnMatch, boolean logEmptyResults) {
        // You can have multiple prefixes separated by a comma
        String[] prefixes = numberPrefix.split(",");
        List<NumberInfo> nums = new ArrayList<>();

        for (String prefix: prefixes) {
            for (int i = 9; i >= 0; i--) {
                if (intToSkip.contains(i)) continue;
                List<MarvinSegment> list = FindSubimage.findSubimage(im, BHBotUnity.cues.get(prefix + "" + i).im, 1.0, true, false, 0, 0, 0, 0);
                //BHBot.logger.info("DEBUG difficulty detection: " + i + " - " + list.size());
                for (MarvinSegment s : list) {
                    nums.add(new NumberInfo(Integer.toString(i), s.x1));
                }
            }

            // The current prefix is the correct one, so we do not check the remaining ones
            if (nums.size() > 0 && breakOnMatch) {
                break;
            }
        }

        // order list horizontally:
        nums = nums.stream()
                .sorted(Comparator.comparing(NumberInfo::xPosition))
                .collect(Collectors.toList());

        int result = 0;

        if (nums.size() > 0 ) {
            StringBuilder resultStr = new StringBuilder();
            nums.forEach(resultStr::append);
            result = Integer.parseInt(resultStr.toString());
        }

        if (logEmptyResults && (nums.size() == 0 || result == 0)) {
            BHBotUnity.logger.debug(Misc.getStackTrace());
            BHBotUnity.logger.debug("Empty number from readNumFromImg im = " + im + ", numberPrefix = " + numberPrefix + ", intToSkip = " + intToSkip);
            Misc.saveScreen("readNumFromImg-empty", "debug/readNumFromImg", true, im);
        }

        return result;
    }

    /**
     * Given a image containing a range of values in this format <value1><separator><value2>, this method will
     * read the image and return the integer representation of <value1> and <value2>.
     *
     * @param im                  a BufferedImage containing the range. The image must be converted in Black & White scale.
     * @param numberPrefix        The prefix used to read number cues. This depends on how cues have been defined
     * @param intToSkip           Should we skip any number from the range read?
     * @param rangeSeparatorName  The name of the separator cue
     * @param rangeSeparatorValue What character will be used to represent the separator internally in the method?
     * @return An integer array of two values containing the minimum and maximum values for the range.
     * In case of error an empty array is returned and you have to check this in your own code.
     */
    @SuppressWarnings("SameParameterValue")
    int[] readNumRangeFromImg(BufferedImage im, String numberPrefix, HashSet<Integer> intToSkip, String rangeSeparatorName, String rangeSeparatorValue) {

        List<NumberInfo> nums = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            if (intToSkip.contains(i)) continue;
            List<MarvinSegment> list = FindSubimage.findSubimage(im, BHBotUnity.cues.get(numberPrefix + "" + i).im, 1.0, true, false, 0, 0, 0, 0);
            //BHBot.logger.info("DEBUG difficulty detection: " + i + " - " + list.size());
            for (MarvinSegment s : list) {
                nums.add(new NumberInfo(Integer.toString(i), s.x1));
            }
        }

        // No numbers have been found
        if (nums.size() == 0)
            return new int[]{}; // error

        // We take care of the separator
        List<MarvinSegment> list = FindSubimage.findSubimage(im, BHBotUnity.cues.get(numberPrefix + "" + rangeSeparatorName).im, 1.0, true, false, 0, 0, 0, 0);
        //BHBot.logger.info("DEBUG difficulty detection: " + i + " - " + list.size());

        if (list.size() == 0) {
            BHBotUnity.logger.error("No separator character found in readNumRangeFromImg!");
            return new int[]{};
        } else if (list.size() > 1) {
            BHBotUnity.logger.error("More than one separator character found in readNumRangeFromImg!");
            return new int[]{};
        }

        for (MarvinSegment s : list) {
            nums.add(new NumberInfo(rangeSeparatorValue, s.x1));
        }

        // order list horizontally:
        nums = nums.stream()
                    .sorted(Comparator.comparing(NumberInfo::xPosition))
                    .collect(Collectors.toList());

        StringBuilder result = new StringBuilder();
        nums.forEach(result::append);

        String[] rangesStr = result.toString().split(rangeSeparatorValue);

        return new int[]{Integer.parseInt(rangesStr[0]), Integer.parseInt(rangesStr[1])};
    }

    /**
     * Detects selected difficulty in trials/gauntlet/expedition window. <br>
     * NOTE: Trials/gauntlet/expedtion window must be open for this to work! <br>
     *
     * @param difficulty The Cue to be used to search for the difficulty integer
     * @return 0 in case of an error, or the selected difficulty level instead.
     */
    int detectDifficulty(Cue difficulty) {

        // TODO Remember to remove this!!
        debugDifficulty();

        bot.browser.readScreen(2 * Misc.Durations.SECOND); // note that sometimes the cue will be gray (disabled) since the game is fetching data from the server - in that case we'll have to wait a bit

        MarvinSegment seg = MarvinSegment.fromCue(difficulty, bot.browser);
        if (seg == null) {
            Misc.saveScreen("detect-difficulty-disabled", "errors", BHBotUnity.includeMachineNameInScreenshots, bot.browser.getImg());
            seg = MarvinSegment.fromCue(BHBotUnity.cues.get("DifficultyDisabled"), bot.browser);
            if (seg != null) { // game is still fetching data from the server... we must wait a bit!
                Misc.sleep(5 * Misc.Durations.SECOND);
                seg = MarvinSegment.fromCue(difficulty, 20 * Misc.Durations.SECOND, bot.browser);
            }
        }
        if (seg == null) {
            BHBotUnity.logger.error("Error: unable to detect difficulty selection box!");
            bot.saveGameScreen("difficulty_error_" + bot.getState().getShortcut(), "errors");
            return 0; // error
        }

        // We get the  region with the difficulty number
        BufferedImage numImg = bot.browser.getImg().getSubimage(seg.x1 + 26, seg.y1 + 32, 70, 25);

        // We transform it in B&W using available customMax
        MarvinImage im = new MarvinImage(numImg, "PNG");
        im.toBlackWhite(110);
        im.update();

        BufferedImage imb = im.getBufferedImage();

        return readNumFromImg(imb, "tg_diff_cost_win_11_", Set.of(), false, false);
    }

    /* World boss reading and changing section */
    private int detectWorldBossTier() {
        int xOffset, yOffset, w, h;
        MarvinSegment tierDropDown;

        if (!bot.browser.isDoNotShareUrl()) {
            xOffset = 401;
            yOffset = 209;
        } else {
            xOffset = 400;
            yOffset = 207;
        }
        w = 21;
        h = 19;

        tierDropDown = MarvinSegment.fromCue("WorldBossTierDropDown", Misc.Durations.SECOND * 2, bot.browser); // For tier drop down menu

        if (tierDropDown == null) {
            BHBotUnity.logger.error("Error: unable to detect world boss difficulty selection box in detectWorldBossTier!");
            return 0; // error
        }

        MarvinImage im = new MarvinImage(bot.browser.getImg().getSubimage(xOffset, yOffset, w, h));

        // make it white-gray (to facilitate cue recognition):
        im.toBlackWhite(new Color(25, 25, 25), new Color(255, 255, 255), 255);
        im.update();

        BufferedImage imb = im.getBufferedImage();

        return readNumFromImg(imb, "wb_tier_", Set.of(), false, true);
    }

    /**
     * This method takes care of managing the correct WB tier selection
     *
     * @param targetTier The desired tier for the World Boss
     * @return true for success, false if an error happens
     */
    private boolean changeWorldBossTier(int targetTier) {
        MarvinSegment seg;
        bot.browser.readScreen(Misc.Durations.SECOND); //wait for screen to stabilize
        seg = MarvinSegment.fromCue(BHBotUnity.cues.get("WorldBossTierDropDown"), 2 * Misc.Durations.SECOND, bot.browser);

        if (seg == null) {
            BHBotUnity.logger.error("Error: unable to detect world boss difficulty selection box in changeWorldBossTier!");
            bot.saveGameScreen("change_wb_error", "errors");
            return false;
        }

        bot.browser.clickOnSeg(seg);
        bot.browser.readScreen(2 * Misc.Durations.SECOND); //wait for screen to stabilize

        // We detect what is the top available tier. This may be different based on player level and unlocked zones
        Bounds topTierBounds = Bounds.fromWidthHeight(409, 146, 27, 26);
        MarvinImage topTierImg = new MarvinImage(bot.browser.getImg().getSubimage(topTierBounds.x1, topTierBounds.y1, topTierBounds.width, topTierBounds.height));
        topTierImg.toBlackWhite(new Color(25, 25, 25), new Color(255, 255, 255), 255);
        topTierImg.update();
        int topAvailableTier = readNumFromImg(topTierImg.getBufferedImage(), "wb_tier_button_", Set.of(), false, true);

        if (topAvailableTier == 0) {
            BHBotUnity.logger.error("Impossible to detect maximum available tier in World Boss");
            bot.saveGameScreen("wb_max_tier", "errors");
            return false;
        }

        BHBotUnity.logger.debug("Detected top available tier is: " + topAvailableTier);

        // The bounds for the WB tier selection
        Bounds tiersBounds = Bounds.fromWidthHeight(264, 136, 251, 60);

        // Offset between the different tiers buttons
        int tierOffset = 60;

        // position on X axis is independent from tier
        int clickX = tiersBounds.x1 + (tiersBounds.width / 2);

        // Used to understand how many times we should click on bar down/up cue
        int tierDiff = topAvailableTier - targetTier;

        // Used to check if we should scroll
        Function<Integer, Boolean> scrollCheck = tierDiff < 0 ? tierDiffArg -> tierDiffArg < 0 : tierDiffArg -> tierDiffArg > 4;

        // Used to change the tierDiff value during iterations
        Function<Integer, Integer> tierDiffUpdate = tierDiff < 0 ? tierDiffArg -> tierDiffArg + 1 : tierDiffArg -> tierDiffArg - 1;

        // Used to identify the correct cue to scroll
        String cueName = tierDiff < 0 ? "DropDownUp" : "DropDownDown";

        seg = MarvinSegment.fromCue(BHBotUnity.cues.get(cueName), bot.browser);
        if (seg == null) {
            BHBotUnity.logger.error("Error: unable to detect " + cueName + " in World Boss Tier selection");
            bot.saveGameScreen("wb_tier_" + cueName, "errors");
            return false;
        }

        while (scrollCheck.apply(tierDiff)) {
            bot.browser.clickOnSeg(seg);
            tierDiff = tierDiffUpdate.apply(tierDiff);
        }

        int clickY = tiersBounds.y1 + (tierOffset * tierDiff) + (tiersBounds.height / 2);

        bot.browser.clickInGame(clickX, clickY);

        return true;
    }

    private int detectWorldBossDifficulty() {
        bot.browser.readScreen();

        if (MarvinSegment.fromCue(BHBotUnity.cues.get("WorldBossDifficultyNormal"), Misc.Durations.SECOND, bot.browser) != null) {
            return 1;
        } else if (MarvinSegment.fromCue(BHBotUnity.cues.get("WorldBossDifficultyHard"), Misc.Durations.SECOND, bot.browser) != null) {
            return 2;
        } else if (MarvinSegment.fromCue(BHBotUnity.cues.get("WorldBossDifficultyHeroic"), Misc.Durations.SECOND, bot.browser) != null) {
            return 3;
        } else return 0;
    }

    private void changeWorldBossDifficulty(int target) {

        bot.browser.readScreen(Misc.Durations.SECOND); //screen stabilising
        bot.browser.clickInGame(480, 300); //difficulty button
        bot.browser.readScreen(Misc.Durations.SECOND); //screen stabilising

        Cue difficultySelection;

        if (target == 1) {
            difficultySelection = BHBotUnity.cues.get("cueWBSelectNormal");
        } else if (target == 2) {
            difficultySelection = BHBotUnity.cues.get("cueWBSelectHard");
        } else if (target == 3) {
            difficultySelection = BHBotUnity.cues.get("cueWBSelectHeroic");
        } else {
            BHBotUnity.logger.error("Wrong target value in changeWorldBossDifficulty, defult to normal!");
            difficultySelection = BHBotUnity.cues.get("cueWBSelectNormal");
        }

        MarvinSegment seg = MarvinSegment.fromCue(difficultySelection, Misc.Durations.SECOND * 2, bot.browser);
        if (seg != null) {
            bot.browser.clickOnSeg(seg);
        } else {
            BHBotUnity.logger.error("Impossible to detect desired difficulty in changeWorldBossDifficulty!");
            restart();
        }
    }

    /**
     * Get the Total World Boss TS. This method assumes that the WB Lobby is opened and screen read is up to date with it
     *
     * @return The total TS found value, 0 if errors
     * @param lobbyScreen The image to be used to read the WB Total TS
     */
    private int getWorldBossTotalTS(BufferedImage lobbyScreen) {
        final Bounds totalWBTS = Bounds.fromWidthHeight(602, 67, 88, 36);
        MarvinImage totalTSImg = new MarvinImage(lobbyScreen.getSubimage(totalWBTS.x1, totalWBTS.y1, totalWBTS.width, totalWBTS.height));
        totalTSImg.toBlackWhite(120);
        totalTSImg.update();
        BufferedImage totalTSSubImg = totalTSImg.getBufferedImage();

        /*if (bot.settings.debugWBTS) {
            MarvinImage debugImg = new MarvinImage(lobbyScreen);
            debugImg.drawRect(totalWBTS.x1, totalWBTS.y1, totalWBTS.width, totalWBTS.height, 2, Color.BLUE);
            debugImg.update();
            Misc.saveScreen("debug-total-ts", "wb-ts-debug", BHBot.includeMachineNameInScreenshots, debugImg.getBufferedImage());
        }*/

        // We start from 20 intentionally: as soon a match is found, readNumFromImg will stop checking the remaining prefixes
        return readNumFromImg(totalTSSubImg, "wb_total_ts_20_,wb_total_ts_18_,wb_total_ts_16_", Set.of(), true, false);
    }

    /**
     * Get the TS for each World Boss lobby member
     *
     * @param inviteCnt The lobby size
     * @param lobbyScreen The image to be used to read the WB Total TS
     * @return An array of int with TS for each party member
     */
    private int[] getWorldBossPlayersTS(int inviteCnt, BufferedImage lobbyScreen) {
        int[] results = new int[inviteCnt];
        final Bounds TSBound = Bounds.fromWidthHeight(184, 244, 84, 18);

        // We convert the lobby screen to black and white
        MarvinImage toBlackAndWhite = new MarvinImage(lobbyScreen);
        toBlackAndWhite.toBlackWhite(120);
        toBlackAndWhite.update();

        // Black and white BufferedImage
        BufferedImage BlackAndWhiteTS = toBlackAndWhite.getBufferedImage();

        // Only used if debugWBTS is true
        // MarvinImage debugWTSImg = new MarvinImage(BlackAndWhiteTS);

        for (int partyMemberPos = 0; partyMemberPos < inviteCnt; partyMemberPos++) {
            final int y = TSBound.y1 + (54 * partyMemberPos);

            BufferedImage tsSubImg = BlackAndWhiteTS.getSubimage(TSBound.x1, y, TSBound.width, TSBound.height);
            int playerTS = readNumFromImg(tsSubImg, "wb_player_ts_", Set.of(), false, false);
            results[partyMemberPos] = playerTS;

            /*if (bot.settings.debugWBTS) {
                debugWTSImg.drawRect(TSBound.x1, y, TSBound.width, TSBound.height, 2, Color.BLUE);
            }*/
        }

        /*if (bot.settings.debugWBTS) {
            debugWTSImg.update();
            Misc.saveScreen("debug-player-ts", "wb-ts-debug", BHBot.includeMachineNameInScreenshots, debugWTSImg.getBufferedImage());
        }*/

        return results;
    }

    /**
     * Changes difficulty level in expedition window.
     * Note: for this to work, expedition window must be open!
     *
     * @return 0 in case of error, newDifficulty if everything was ok, another integer if for any reason the desired
     * level could not be set. Caller will have to check this in its own code.
     */
    int selectDifficulty(int oldDifficulty, int newDifficulty, Cue difficultyCue, Cue toCloseCue, int step, boolean useDifficultyRanges) {
        if (oldDifficulty == newDifficulty)
            return newDifficulty; // no change

        MarvinSegment seg = MarvinSegment.fromCue(difficultyCue, 2 * Misc.Durations.SECOND, bot.browser);
        if (seg == null) {
            BHBotUnity.logger.error("Error: unable to detect 'select difficulty' button while trying to change difficulty level!");
            return 0; // error
        }

        if (toCloseCue == null)
            bot.browser.clickOnSeg(seg);
        else
            bot.browser.closePopupSecurely(toCloseCue, difficultyCue);

        bot.browser.readScreen(5 * Misc.Durations.SECOND);

        if (useDifficultyRanges) {
            return selectDifficultyFromRange(newDifficulty);
        }

        return selectDifficultyFromDropDown(newDifficulty, 0, step);
    }

    /**
     * Changes difficulty level in trials/gauntlet window using the two step choice: first selecting the range and then
     * choosing the closed matched difficulty.
     * <p>
     * Note: for this to work, trials/gauntlet window needs to be opened!
     *
     * @param newDifficulty The desired target difficulty
     * @return 0 in case of error, newDifficulty if everything was ok, another integer if for any reason the desired
     * level could not be set. Caller will have to check this in its own code.
     */
    private int selectDifficultyFromRange(int newDifficulty) {

        // offset to read different ranges
        final int yOffset = 60;

        // Color definition for B&W conversion
        final int bwTreshold = 110;

        // Scroller cues
        ScrollBarManager difficultyRangeSB = new ScrollBarManager(bot.browser);
        // Cue scrollerAtTop = new Cue(BHBot.cues.get("ScrollerAtTop"), Bounds.fromWidthHeight(520, 115, 35, 90));
        // Scroller max clicks
        final int MAX_CLICKS = 30;

        // We make sure the scroll is at the top position. This is just failsafe in all the tests this was always the default behavior
        difficultyRangeSB.scrollToTop(null);

        bot.browser.readScreen(Misc.Durations.SECOND);

        // Bounds for difficulty range. The position can change, so we look for the border, and we dynamically apply it
        Bounds difficultyRangeBounds;
        final int x1Diff = 30, y1Diff = 5, wDiff = -58, hDiff = -29;
        MarvinSegment topChoice = MarvinSegment.fromCue("TopChoice", Misc.Durations.SECOND, bot.browser);
        if (topChoice == null) {
            BHBotUnity.logger.error("Error: unable to find top choice border in trials/gauntlet difficulty range drop-down menu!");
            bot.saveGameScreen("select_difficulty_range_top_choice", "errors");
            return 0;
        } else {
            difficultyRangeBounds = Bounds.fromWidthHeight(topChoice.x1 + x1Diff, topChoice.y1 + y1Diff, topChoice.width + wDiff, topChoice.height + hDiff);
        }

        int cntAttempt = 0;
        int rangeCnt = 0;
        int minDifficulty;
        do {
            // we could not move the scroller at the bottom position
            if (cntAttempt > MAX_CLICKS) {
                BHBotUnity.logger.error("It was impossible to move the scroller to the bottom position for the difficulty.");
                return 0;
            }

            // We read ranges five at time, so we scroll down when we are done.
            // We also use this to calculate the right difficulty range to read
            int rangePos = rangeCnt % 5;

            // we need to click on the bottom arrow to have new ranges on monitor
            if (rangePos == 0 && rangeCnt > 0) {

                if (!difficultyRangeSB.canScrollDown) {
                    BHBotUnity.logger.error("Error: unable to detect down arrow in trials/gauntlet difficulty range drop-down menu!");
                    bot.saveGameScreen("select_difficulty_range_arrow_down", "errors");
                    return 0;
                }

                for (int barPos = 0; barPos < 5; barPos++) {
                    difficultyRangeSB.scrollDown(Misc.Durations.SECOND / 2);
                    if (difficultyRangeSB.isAtBottom()) break;
                }

                // As we scrolled down, we update the position of the top choice border
                topChoice = MarvinSegment.fromCue("TopChoice", Misc.Durations.SECOND, bot.browser);
                if (topChoice == null) {
                    BHBotUnity.logger.error("Error: unable to find top choice border in trials/gauntlet difficulty range drop-down menu!");
                    bot.saveGameScreen("select_difficulty_range_top_choice", "errors");
                    return 0;
                } else {
                    difficultyRangeBounds = Bounds.fromWidthHeight(topChoice.x1 + x1Diff, topChoice.y1 + y1Diff, topChoice.width + wDiff, topChoice.height + hDiff);
                }
            }

            // We use rangePos to read the right difficulty range
            int posOffset = rangePos * yOffset;
            BufferedImage topRangeImg = bot.browser.getImg().getSubimage(difficultyRangeBounds.x1, difficultyRangeBounds.y1 + posOffset, difficultyRangeBounds.width, difficultyRangeBounds.height);
            MarvinImage im = new MarvinImage(topRangeImg);
            im.toBlackWhite(110);
            im.update();

            int[] diffRange = readNumRangeFromImg(im.getBufferedImage(), "tg_diff_range_16_", new HashSet<>(), "hyphen", "-");
            BHBotUnity.logger.debug("Detected difficulty range: " + Arrays.toString(diffRange));
            if (diffRange.length != 2) {
                BHBotUnity.logger.error("It was impossible to read the top difficulty range");
                return 0;
            }

            // We save difficulty bounds for readability’s sake
            int rangeMinDifficulty = diffRange[0], rangeMaxDifficulty = diffRange[1];
            minDifficulty = rangeMinDifficulty;

            // new difficulty out of range, we only check it on the first iteration
            if (rangeCnt == 0) {
                if (newDifficulty > rangeMaxDifficulty) {
                    BHBotUnity.logger.warn("New difficulty " + newDifficulty + " is bigger than maximum available difficulty: " + rangeMaxDifficulty + ". Using maximum difficulty.");
                    newDifficulty = rangeMaxDifficulty;
                }
            }

            // we've found the right range and we click it!
            if (newDifficulty >= rangeMinDifficulty && newDifficulty <= rangeMaxDifficulty) {
                bot.browser.clickInGame((difficultyRangeBounds.x1 + difficultyRangeBounds.width / 2), (difficultyRangeBounds.y1 + posOffset + difficultyRangeBounds.height / 2));

                // We wait for the difficulty selection to come out
                bot.browser.readScreen(Misc.Durations.SECOND);

                ScrollBarManager difficultySB = new ScrollBarManager(bot.browser);

                // Bounds of the top difficulty value
                final Bounds topLvlBounds = Bounds.fromWidthHeight(350, 145, 75, 26);

                /*
                 * In higher tiers difficulty ranges are non continuous and the difference between the values increase.
                 * Low difficulties have steps of 1, then this increase to 5 and finally also to 10. As this appear to be
                 * dynamic, the step between the difficulties is calculated everytime reading from screen the two top
                 * most values in the difficulty popup
                 * */

                // Top most difficulty value
                BufferedImage topLvlBImg = bot.browser.getImg().getSubimage(topLvlBounds.x1, topLvlBounds.y1, topLvlBounds.width, topLvlBounds.height);
                MarvinImage topLvlMImg = new MarvinImage(topLvlBImg);
                topLvlMImg.toBlackWhite(bwTreshold);
                topLvlMImg.update();
                int topLvl = readNumFromImg(topLvlMImg.getBufferedImage(), "tg_diff_selection_17_", Set.of(), false, false);
                if (topLvl == 0) {
                    BHBotUnity.logger.error("Impossible to read difficulty range top level.");
                    return 0;
                }

                // Second difficulty value
                BufferedImage secondLvlBImg = bot.browser.getImg().getSubimage(topLvlBounds.x1, topLvlBounds.y1 + yOffset, topLvlBounds.width, topLvlBounds.height);
                MarvinImage secondLvlMImg = new MarvinImage(secondLvlBImg);
                secondLvlMImg.toBlackWhite(bwTreshold);
                secondLvlMImg.update();
                int secondLvl = readNumFromImg(secondLvlMImg.getBufferedImage(), "tg_diff_selection_17_", Set.of(), false, false);
                if (secondLvl == 0) {
                    BHBotUnity.logger.error("Impossible to read difficulty range second level.");
                    return 0;
                }

                // Difficulty step value
                int lvlStep = topLvl - secondLvl;
                BHBotUnity.logger.debug("Difficulty step is: " + lvlStep);

                // We calculate all the possible values and back-fill them in an array list so that we can use them later
                List<Integer> possibleDifficulties = new ArrayList<>();
                int startDifficulty = rangeMaxDifficulty;
                while (startDifficulty >= rangeMinDifficulty) {
                    possibleDifficulties.add(startDifficulty);

                    startDifficulty -= lvlStep;
                }

                // It is not always possible to get to the exact desired difficulty, so the best match is chosen
                // The absolute value of the difference is used to check the closest match
                int distance = Math.abs(possibleDifficulties.get(0) - newDifficulty);
                int idx = 0;
                for (int i = 1; i < possibleDifficulties.size(); i++) {
                    int cdistance = Math.abs(possibleDifficulties.get(i) - newDifficulty);
                    if (cdistance <= distance) {
                        idx = i;
                        distance = cdistance;
                    }
                }

                int matchedDifficulty = possibleDifficulties.get(idx);
                if (matchedDifficulty - newDifficulty != 0) {
                    BHBotUnity.logger.info("The closest match to " + newDifficulty + " is " + matchedDifficulty);
                }

                // We have it on screen, so we can click on it!
                if (idx < 5) {
                    bot.browser.clickInGame(topLvlBounds.x1 + topLvlBounds.width / 2, topLvlBounds.y1 + (yOffset * idx) + topLvlBounds.height / 2);
                    return matchedDifficulty;
                } else {
                    // We check that the arrow down on the scroller is there
                    if (!difficultySB.canScrollDown) {
                        BHBotUnity.logger.error("Error: unable to detect down arrow in trials/gauntlet second step difficulty range drop-down menu!");
                        bot.saveGameScreen("select_difficulty_range_2nd_step_arrow_down", "errors");
                        return 0;
                    }

                    /*
                     * First five difficulty difficulties are on screen, we start to scroll down and we use the possibleDifficulties
                     * ArrayList that we created before to check that the position we currently are at is the one of the
                     * matched difficulty we found earlier
                     * */
                    for (int idxI = 5; idxI < possibleDifficulties.size(); idxI++) {
                        // TODO Kongregate bug, sometimes you are prompted back to T/G main window. Try to think of a fix
                        difficultySB.scrollDown(500);

                        if (possibleDifficulties.get(idxI) == matchedDifficulty) {
                            // We can finally click on the difficulty value!!
                            bot.browser.clickInGame(topLvlBounds.x1 + topLvlBounds.width / 2, topLvlBounds.y1 + (yOffset * 4) + topLvlBounds.height / 2);
                            return matchedDifficulty;
                        }
                    }
                }
            }

            cntAttempt++;
            rangeCnt++;
        } while (minDifficulty != 1);

        return 0;
    }

    /**
     * Internal routine. Difficulty drop down must be open for this to work!
     * Note that it closes the drop-down when it is done (except if an error occurred). However there is a close
     * animation and the caller must wait for it to finish.
     *
     * @return false on error (caller must do restart() if he gets false as a result from this method)
     */
    private int selectDifficultyFromDropDown(int newDifficulty, int recursionDepth, int step) {
        // horizontal position of the 5 buttons:
        final int posx = 390;
        // vertical positions of the 5 buttons:
        final int[] posy = new int[]{170, 230, 290, 350, 410};

        if (recursionDepth > 3) {
            BHBotUnity.logger.error("Error: Selecting difficulty level from the drop-down menu ran into an endless loop!");
            bot.saveGameScreen("select_difficulty_recursion", "errors");
            tryClosingWindow(); // clean up after our selves (ignoring any exception while doing it)
            return 0;
        }

        MarvinSegment seg;

        // the first (upper most) of the 5 buttons in the drop-down menu. Note that every while a "tier x" is written bellow it, so text is higher up (hence we need to scan a larger area)
        MarvinImage subm = new MarvinImage(bot.browser.getImg().getSubimage(350, 150, 70, 35));
        subm.toBlackWhite(new Color(25, 25, 25), new Color(255, 255, 255), 254);
        subm.update();
        BufferedImage sub = subm.getBufferedImage();
        int num = readNumFromImg(sub, "", Set.of(), false, false);
//		BHBot.logger.info("num = " + Integer.toString(num));
        if (num == 0) {
            BHBotUnity.logger.error("Error: unable to read difficulty level from a drop-down menu!");
            bot.saveGameScreen("select_difficulty_read", "errors");
            tryClosingWindow(); // clean up after our selves (ignoring any exception while doing it)
            return 0;
        }

        int move = (newDifficulty - num) / step; // if negative, we have to move down (in dropdown/numbers), or else up
//		BHBot.logger.info("move = " + Integer.toString(move));

        if (move >= -4 && move <= 0) {
            // we have it on screen. Let's select it!
            bot.browser.clickInGame(posx, posy[Math.abs(move)]); // will auto-close the drop down (but it takes a second or so, since it's animated)
            return newDifficulty;
        }

        // scroll the drop-down until we reach our position:
        // recursively select new difficulty
        //*** should we increase this time?
        if (move > 0) {
            // move up
            seg = MarvinSegment.fromCue(BHBotUnity.cues.get("DropDownUp"), bot.browser);
            if (seg == null) {
                BHBotUnity.logger.error("Error: unable to detect up arrow in trials/gauntlet/expedition difficulty drop-down menu!");
                bot.saveGameScreen("select_difficulty_arrow_up", "errors");
                bot.browser.clickInGame(posx, posy[0]); // regardless of the error, click on the first selection in the drop-down, so that we don't need to re-scroll entire list next time we try!
                return 0;
            }
            for (int i = 0; i < move; i++) {
                bot.browser.clickOnSeg(seg);
            }
            // OK, we should have a target value on screen now, in the first spot. Let's click it!
        } else {
            // move down
            seg = MarvinSegment.fromCue(BHBotUnity.cues.get("DropDownDown"), bot.browser);
            if (seg == null) {
                BHBotUnity.logger.error("Error: unable to detect down arrow in trials/gauntlet/expedition difficulty drop-down menu!");
                bot.saveGameScreen("select_difficulty_arrow_down", "errors");
                bot.browser.clickInGame(posx, posy[0]); // regardless of the error, click on the first selection in the drop-down, so that we don't need to re-scroll entire list next time we try!
                return 0;
            }
            int moves = Math.abs(move) - 4;
//			BHBot.logger.info("Scrolls to 60 = " + Integer.toString(moves));
            for (int i = 0; i < moves; i++) {
                bot.browser.clickOnSeg(seg);
            }
            // OK, we should have a target value on screen now, in the first spot. Let's click it!
        }
        bot.browser.readScreen(5 * Misc.Durations.SECOND); //*** should we increase this time?
        return selectDifficultyFromDropDown(newDifficulty, recursionDepth + 1, step); // recursively select new difficulty
    }

    /**
     * This method detects the select cost in PvP/GvG/Trials/Gauntlet window. <p>
     * <p>
     * Note: PvP cost has different position from GvG/Gauntlet/Trials. <br>
     * Note: PvP/GvG/Trials/Gauntlet window must be open in order for this to work!
     *
     * @return 0 in case of an error, or cost value in interval [1..5]
     */
    int detectCost() {

        // TODO Remember to remove this!
        debugCost();

        MarvinSegment seg = MarvinSegment.fromCue(BHBotUnity.cues.get("Cost"), 15 * Misc.Durations.SECOND, bot.browser);
        if (seg == null) {
            BHBotUnity.logger.error("Error: unable to detect cost selection box!");
            bot.saveGameScreen("cost_selection", "errors");
            return 0; // error
        }

        final int xOffset = 3, yOffset = 41, w = 31, h = 22;


        // We get the  region with the cost number
        BufferedImage numImg = bot.browser.getImg().getSubimage(seg.x1 + xOffset, seg.y1 + yOffset, w, h);

        // We transform it in B&W using available customMax
        MarvinImage im = new MarvinImage(numImg, "PNG");
        im.toBlackWhite(110);
        im.update();

        BufferedImage imb = im.getBufferedImage();

        return readNumFromImg(imb, "tg_diff_cost_win_11_", Set.of(0, 6, 7, 8, 9), false, false);
    }

    /**
     * Changes cost in PvP, GvG, or Trials/Gauntlet window. <br>
     * Note: for this to work, PvP/GvG/Trials/Gauntlet window must be open!
     *
     * @return false in case of an error (unable to change cost).
     */
    boolean selectCost(int oldCost, int newCost) {
        if (oldCost == newCost)
            return true; // no change

        if (newCost > 5){
            BHBotUnity.logger.warn("Maximum value for cost is 5. Found " + newCost);
            newCost = 5;
        }

        if (newCost < 1) {
            BHBotUnity.logger.warn("Minimum allowed cost is 1. Found " + newCost);
            newCost = 1;
        }

        final int xOffset = 65, yOffset = 60;
        MarvinSegment seg = MarvinSegment.fromCue(BHBotUnity.cues.get("Cost"), 5 * Misc.Durations.SECOND, bot.browser);
        if (seg == null) {
            BHBotUnity.logger.error("Error: unable to detect 'select cost' button while trying to change cost!");
            return false; // error
        }

        bot.browser.clickInGame(seg.x1 + xOffset, seg.y1 + yOffset);

        MarvinSegment.fromCue("CostDropDown", 5 * Misc.Durations.SECOND, bot.browser); // wait for the cost selection popup window to open

        // horizontal position of the 5 buttons:
        final int posx = 390;
        // vertical positions of the 5 buttons:
        final int[] posy = new int[]{170, 230, 290, 350, 410};

        bot.browser.clickInGame(posx, posy[newCost - 1]); // will auto-close the drop down (but it takes a second or so, since it's animated)
        Misc.sleep(2 * Misc.Durations.SECOND);

        return true;
    }

    /**
     * Will try to click on "X" button of the currently open popup window. On error, it will ignore it. <br>
     * NOTE: This method does not re-read screen before (or after) cue detection!
     */
    private void tryClosingWindow() {
        tryClosingWindow(null);
    }

    /**
     * Will try to click on "X" button of the currently open popup window that is identified by the 'windowCue'. It will ignore any errors. <br>
     * NOTE: This method does not re-read screen before (or after) cue detection!
     */
    private void tryClosingWindow(Cue windowCue) {
        try {
            MarvinSegment seg;
            if (windowCue != null) {
                seg = MarvinSegment.fromCue(windowCue, bot.browser);
                if (seg == null)
                    return;
            }
            seg = MarvinSegment.fromCue(BHBotUnity.cues.get("X"), bot.browser);
            if (seg != null)
                bot.browser.clickOnSeg(seg);
        } catch (Exception e) {
            BHBotUnity.logger.error("Error in tryClosingWindow", e);
        }
    }

    private void tryClosingAdventureWindow(Bounds xButtonBounds) {
        bot.browser.readScreen();
        MarvinSegment seg = MarvinSegment.fromCue(BHBotUnity.cues.get("X"), Misc.Durations.SECOND * 2, xButtonBounds, bot.browser);

        if (seg != null) bot.browser.clickOnSeg(seg);
    }

    /**
     * @return -1 on error
     */
    private int detectEquipmentFilterScrollerPos() {
        final int[] yScrollerPositions = {146, 164, 181, 199, 217, 235, 252, 270, 288, 306, 323, 341}; // top scroller positions

        MarvinSegment seg = MarvinSegment.fromCue(BHBotUnity.cues.get("StripScrollerTopPos"), 2 * Misc.Durations.SECOND, bot.browser);
        if (seg == null) {
            return -1;
        }
        int pos = seg.y1;

        return Misc.findClosestMatch(yScrollerPositions, pos);
    }

    /**
     * Will strip character down (as a preparation for the PvP battle) of items passed as parameters to this method.
     * Note that before calling this method, game must be in the main method!
     *
     * @param type which item type should we equip/unequip
     * @param dir  direction - either strip down or dress up
     */
    private void strip(EquipmentType type, StripDirection dir) {
        MarvinSegment seg;

        // click on the character menu button (it's a bottom-left button with your character image on it):
        if (openCharacterMenu()) return;

        seg = MarvinSegment.fromCue(BHBotUnity.cues.get("StripSelectorButton"), 10 * Misc.Durations.SECOND, bot.browser);
        if (seg == null) {
            BHBotUnity.logger.error("Error: unable to detect equipment filter button! Skipping...");
            return;
        }

        // now lets see if the right category is already selected:
        seg = MarvinSegment.fromCue(type.getCue(), 500, bot.browser);
        if (seg == null) {
            // OK we need to manually select the correct category!
            seg = MarvinSegment.fromCue(BHBotUnity.cues.get("StripSelectorButton"), bot.browser);
            bot.browser.clickOnSeg(seg);

            MarvinSegment.fromCue(BHBotUnity.cues.get("StripItemsTitle"), 10 * Misc.Durations.SECOND, bot.browser); // waits until "Items" popup is detected
            bot.browser.readScreen(500); // to stabilize sliding popup a bit

            int scrollerPos = detectEquipmentFilterScrollerPos();
//			BHBot.logger.info("Scroller Pos = " + Integer.toString(scrollerPos));
            if (scrollerPos == -1) {
                BHBotUnity.logger.warn("Problem detected: unable to detect scroller position in the character window (location #1)! Skipping strip down/up...");
                return;
            }

            int[] yButtonPositions = {170, 230, 290, 350, 410}; // center y positions of the 5 buttons
            int xButtonPosition = 390;

            if (scrollerPos < type.minPos()) {
                // we must scroll down!
                int move = type.minPos() - scrollerPos;
                seg = MarvinSegment.fromCue(BHBotUnity.cues.get("DropDownDown"), 5 * Misc.Durations.SECOND, bot.browser);
                for (int i = 0; i < move; i++) {
                    bot.browser.clickOnSeg(seg);
                    scrollerPos++;
                }
            } else { // bestIndex > type.maxPos
                // we must scroll up!
                int move = scrollerPos - type.minPos();
                seg = MarvinSegment.fromCue(BHBotUnity.cues.get("DropDownUp"), 5 * Misc.Durations.SECOND, bot.browser);
                for (int i = 0; i < move; i++) {
                    bot.browser.clickOnSeg(seg);
                    scrollerPos--;
                }
            }

            // make sure scroller is in correct position now:
            bot.browser.readScreen(500); // so that the scroller stabilizes a bit
            int newScrollerPos = detectEquipmentFilterScrollerPos();
            int counter = 0;
            while (newScrollerPos != scrollerPos) {
                if (counter > 3) {
                    BHBotUnity.logger.warn("Problem detected: unable to adjust scroller position in the character window (scroller position: " + newScrollerPos + ", should be: " + scrollerPos + ")! Skipping strip down/up...");
                    return;
                }
                bot.browser.readScreen(Misc.Durations.SECOND);
                newScrollerPos = detectEquipmentFilterScrollerPos();
                counter++;
            }
            bot.browser.clickInGame(xButtonPosition, yButtonPositions[type.getButtonPos() - scrollerPos]);
            // clicking on the button will close the window automatically... we just need to wait a bit for it to close
            MarvinSegment.fromCue(BHBotUnity.cues.get("StripSelectorButton"), 5 * Misc.Durations.SECOND, bot.browser); // we do this just in order to wait for the previous menu to reappear
        }

        waitForInventoryIconsToLoad(); // first of all, lets make sure that all icons are loaded

        // now deselect/select the strongest equipment in the menu:

        seg = MarvinSegment.fromCue(BHBotUnity.cues.get("StripEquipped"), 500, bot.browser); // if "E" icon is not found, that means that some other item is equipped or that no item is equipped
        boolean equipped = seg != null; // is strongest item equipped already?

        // position of top-left item (which is the strongest) is (490, 210)
        if (dir == StripDirection.StripDown) {
            bot.browser.clickInGame(490, 210);
        }
        if (!equipped) // in case item was not equipped, we must click on it twice, first time to equip it, second to unequip it. This could happen for example when we had some weaker item equipped (or no item equipped).
            bot.browser.clickInGame(490, 210);

        // OK, we're done, lets close the character menu window:
        bot.browser.closePopupSecurely(BHBotUnity.cues.get("StripSelectorButton"), BHBotUnity.cues.get("X"));
    }

    private void stripDown(List<String> striplist) {
        if (striplist.size() == 0)
            return;

        StringBuilder list = new StringBuilder();
        for (String type : striplist) {
            list.append(EquipmentType.letterToName(type)).append(", ");
        }
        list = new StringBuilder(list.substring(0, list.length() - 2));
        BHBotUnity.logger.info("Stripping down for PvP/GVG (" + list + ")...");

        for (String type : striplist) {
            strip(EquipmentType.letterToType(type), StripDirection.StripDown);
        }
    }

    private void dressUp(List<String> striplist) {
        if (striplist.size() == 0)
            return;

        StringBuilder list = new StringBuilder();
        for (String type : striplist) {
            list.append(EquipmentType.letterToName(type)).append(", ");
        }
        list = new StringBuilder(list.substring(0, list.length() - 2));
        BHBotUnity.logger.info("Dressing back up (" + list + ")...");

        // we reverse the order so that we have to make less clicks to dress up equipment
        Collections.reverse(striplist);
        for (String type : striplist) {
            strip(EquipmentType.letterToType(type), StripDirection.DressUp);
        }
        Collections.reverse(striplist);
    }

    /**
     * Daily collection of fishing baits!
     */
    private void handleFishingBaits() {
        bot.setState(BHBotUnity.State.FishingBaits);
        MarvinSegment seg;

        seg = MarvinSegment.fromCue(BHBotUnity.cues.get("Fishing"), Misc.Durations.SECOND * 5, bot.browser);
        if (seg != null) {
            bot.browser.clickOnSeg(seg);
            Misc.sleep(Misc.Durations.SECOND); // we allow some seconds as maybe the reward popup is sliding down

            detectCharacterDialogAndHandleIt();

            seg = MarvinSegment.fromCue(BHBotUnity.cues.get("WeeklyRewards"), Misc.Durations.SECOND * 5, bot.browser);
            if (seg != null) {
                seg = MarvinSegment.fromCue(BHBotUnity.cues.get("X"), 5 * Misc.Durations.SECOND, bot.browser);
                if (seg != null) {
                    if ((bot.settings.screenshots.contains("a"))) {
                        bot.saveGameScreen("fishing-baits", "fishing");
                    }
                    bot.browser.clickOnSeg(seg);
                    BHBotUnity.logger.info("Correctly collected fishing baits");
                    bot.browser.readScreen(Misc.Durations.SECOND * 2);
                } else {
                    BHBotUnity.logger.error("Something weng wrong while collecting fishing baits, restarting...");
                    bot.saveGameScreen("fishing-error-baits", "errors");
                    restart();
                }
            }

            seg = MarvinSegment.fromCue(BHBotUnity.cues.get("X"), 5 * Misc.Durations.SECOND, bot.browser);
            if (seg != null) {
                bot.browser.clickOnSeg(seg);
                Misc.sleep(Misc.Durations.SECOND * 2);
                bot.browser.readScreen();
            } else {
                BHBotUnity.logger.error("Something went wrong while closing the fishing dialog, restarting...");
                bot.saveGameScreen("fishing-error-closing", "errors");
                restart();
            }

        } else {
            BHBotUnity.logger.warn("Impossible to find the fishing button");
        }
        bot.browser.readScreen(Misc.Durations.SECOND * 2);
    }

    private boolean consumableReplaceCheck() {
        int coloursFound = 0;

        boolean foundGreen = false;
        boolean foundBlue = false;
        boolean foundRedFaded = false;
        boolean foundYellow = false;
        boolean foundRed = false;

        bot.browser.readScreen();
        BufferedImage consumableTest = bot.browser.getImg().getSubimage(258, 218, 311, 107);

        Color green = new Color(151, 255, 125);
        Color blue = new Color(147, 158, 244);
        Color redFaded = new Color(255, 128, 125); //faded red on 75% boosts
        Color yellow = new Color(255, 255, 0);
        Color red = new Color(255, 0, 72);

        for (int y = 0; y < consumableTest.getHeight(); y++) {
            for (int x = 0; x < consumableTest.getWidth(); x++) {
                if (!foundGreen && new Color(consumableTest.getRGB(x, y)).equals(green)) {
                    foundGreen = true;
                    coloursFound++;
                } else if (!foundBlue && new Color(consumableTest.getRGB(x, y)).equals(blue)) {
                    foundBlue = true;
                    coloursFound++;
                } else if (!foundRedFaded && new Color(consumableTest.getRGB(x, y)).equals(redFaded)) {
                    foundRedFaded = true;
                    coloursFound++;
                } else if (!foundYellow && new Color(consumableTest.getRGB(x, y)).equals(yellow)) {
                    foundYellow = true;
                    coloursFound++;
                } else if (!foundRed && new Color(consumableTest.getRGB(x, y)).equals(red)) {
                    foundRed = true;
                    coloursFound++;
                }

                if (coloursFound > 1) {
                    BHBotUnity.logger.info("Replace Consumables text found, skipping");
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * We must be in main menu for this to work!
     */
    private void handleConsumables() {
        if (!bot.settings.autoConsume || bot.settings.consumables.size() == 0) // consumables management is turned off!
            return;

        MarvinSegment seg;

        boolean exp = MarvinSegment.fromCue(BHBotUnity.cues.get("BonusExp"), bot.browser) != null;
        boolean item = MarvinSegment.fromCue(BHBotUnity.cues.get("BonusItem"), bot.browser) != null;
        boolean speed = MarvinSegment.fromCue(BHBotUnity.cues.get("BonusSpeed"), bot.browser) != null;
        boolean gold = MarvinSegment.fromCue(BHBotUnity.cues.get("BonusGold"), bot.browser) != null;

        // Special consumables
        /*if (MarvinSegment.fromCue(BHBot.cues.get("ConsumablePumkgor"), bot.browser) != null || MarvinSegment.fromCue(BHBot.cues.get("ConsumableBroccoli"), bot.browser) != null
                || MarvinSegment.fromCue(BHBot.cues.get("ConsumableGreatFeast"), bot.browser) != null || MarvinSegment.fromCue(BHBot.cues.get("ConsumableGingernaut"), bot.browser) != null
                || MarvinSegment.fromCue(BHBot.cues.get("ConsumableCoco"), bot.browser) != null) {
            exp = true;
            item = true;
            speed = true;
            gold = true;
            // BHBot.logger.info("Special consumable detected, skipping all the rest...");
        }*/

        EnumSet<ConsumableType> duplicateConsumables = EnumSet.noneOf(ConsumableType.class); // here we store consumables that we wanted to consume now but we have detected they are already active, so we skipped them (used for error reporting)
        EnumSet<ConsumableType> consumables = EnumSet.noneOf(ConsumableType.class); // here we store consumables that we want to consume now
        for (String s : bot.settings.consumables)
            consumables.add(ConsumableType.getTypeFromName(s));
        //BHBot.logger.info("Testing for following consumables: " + Misc.listToString(consumables));

        if (exp) {
            consumables.remove(ConsumableType.EXP_MINOR);
            consumables.remove(ConsumableType.EXP_AVERAGE);
            consumables.remove(ConsumableType.EXP_MAJOR);
        }

        if (item) {
            consumables.remove(ConsumableType.ITEM_MINOR);
            consumables.remove(ConsumableType.ITEM_AVERAGE);
            consumables.remove(ConsumableType.ITEM_MAJOR);
        }

        if (speed) {
            consumables.remove(ConsumableType.SPEED_MINOR);
            consumables.remove(ConsumableType.SPEED_AVERAGE);
            consumables.remove(ConsumableType.SPEED_MAJOR);
        }

        if (gold) {
            consumables.remove(ConsumableType.GOLD_MINOR);
            consumables.remove(ConsumableType.GOLD_AVERAGE);
            consumables.remove(ConsumableType.GOLD_MAJOR);
        }

        // so now we have only those consumables in the 'consumables' list that we actually need to consume right now!

        if (consumables.isEmpty()) // we don't need to do anything!
            return;

        // OK, try to consume some consumables!
        BHBotUnity.logger.info("Trying to consume some consumables (" + Misc.listToString(consumables) + ")...");

        // click on the character menu button (it's a bottom-left button with your character image on it):
        if (openCharacterMenu()) {
            BHBotUnity.logger.warn("AutoConsume functionality was not able to open the Character Menu");
            return;
        }

        seg = MarvinSegment.fromCue(BHBotUnity.cues.get("Filter"), 5 * Misc.Durations.SECOND, bot.browser);
        if (seg == null) {
            BHBotUnity.logger.warn("Error: unable to detect orange filter button! Skipping...");
            bot.browser.closePopupSecurely(BHBotUnity.cues.get("X"), BHBotUnity.cues.get("X"));
            return;
        }

        // We click the filter button
        bot.browser.clickOnSeg(seg);
        MarvinSegment.waitForNull(BHBotUnity.cues.get("Filter"), Misc.Durations.SECOND, bot.browser);

        // now let's select the <Consumables> button from the filter selection
        bot.browser.closePopupSecurely(BHBotUnity.cues.get("FilterTitle"), BHBotUnity.cues.get("ConsumablesBtn"));

        // now consume the consumable(s):
        bot.browser.readScreen(500); // to stabilize window a bit

        Bounds consumableBounds = new Bounds(450, 165, 670, 460); // detection area (where consumables icons are visible)

        /*
         ******** IMPORTANT ********
         * The is no easy way to understand when the scroll bar reached the bottom position using a scrollbar cue, so
         * what we do instead is to take a sub image of the bottom part of the character menu and check everytime we
         * scroll if it has changed
         ********/
        bot.browser.readScreen();
        Bounds bottomArea = Bounds.fromWidthHeight(461, 416, 213, 27);
        BufferedImage subImg =  bot.browser.getImg().getSubimage(bottomArea.x1, bottomArea.y1, bottomArea.width, bottomArea.height);
        String bottomSignature = Misc.imgToMD5(subImg);

        MarvinSegment DropDownDown = null;

        while (!consumables.isEmpty()) {
            // waitForInventoryIconsToLoad(); // first of all, lets make sure that all icons are loaded
            for (Iterator<ConsumableType> i = consumables.iterator(); i.hasNext(); ) {
                ConsumableType c = i.next();
                seg = MarvinSegment.fromCue(new Cue(c.getInventoryCue(), consumableBounds), bot.browser);
                if (seg != null) {
                    // OK we found the consumable icon! Lets click it...
                    bot.browser.clickOnSeg(seg);
                    MarvinSegment.fromCue(BHBotUnity.cues.get("ConsumableTitle"), 5 * Misc.Durations.SECOND, bot.browser); // wait for the consumable popup window to appear
                    bot.browser.readScreen(500); // wait for sliding popup to stabilize a bit

                    // It may be possible that another (special) consumable is already there, we check this
                    if (!consumableReplaceCheck()) {
                        // don't consume the consumable... it's already in use!
                        BHBotUnity.logger.warn("\"Replace consumable\" dialog detected for (" + c.getName() + "). Skipping...");
                        duplicateConsumables.add(c);
                        bot.browser.closePopupSecurely(BHBotUnity.cues.get("ConsumableTitle"), BHBotUnity.cues.get("No"));
                    } else {
                        // consume the consumable:
                        bot.browser.closePopupSecurely(BHBotUnity.cues.get("ConsumableTitle"), BHBotUnity.cues.get("YesGreen"));
                    }

                    // We close the consumable confirmation window
                    bot.browser.closePopupSecurely(BHBotUnity.cues.get("ConsumableHaveFun"), BHBotUnity.cues.get("ConsumableDone"));

                    MarvinSegment.fromCue(BHBotUnity.cues.get("Filter"), 5 * Misc.Durations.SECOND, bot.browser); // we do this just in order to wait for the previous menu to reappear
                    i.remove();
                }
            }

            if (!consumables.isEmpty()) {
                // lets scroll down, we only search for the arrow once
                if (DropDownDown == null)
                    DropDownDown = MarvinSegment.fromCue(BHBotUnity.cues.get("DropDownDown"), 5 * Misc.Durations.SECOND, Bounds.fromWidthHeight(665, 420, 40, 35), bot.browser);

                // We were not able to find the down arrow
                if (DropDownDown == null) {
                    BHBotUnity.logger.error("It was impossible to find the Scroll Down arrow, no consumable has been used.");
                    Misc.saveScreen("handleconsumables-no-scroll-down", "errors", true, bot.browser.getImg());
                    bot.browser.closePopupSecurely(BHBotUnity.cues.get("Filter"), BHBotUnity.cues.get("X"));
                    return;
                }

                for (int i = 0; i < 4; i++) { //the menu has 4 rows so we move to the next four rows and check again
                    bot.browser.clickOnSeg(DropDownDown);
                }

                // We check the bottom signature
                bot.browser.readScreen(Misc.Durations.SECOND); // so that the scroller stabilizes a bit
                subImg =  bot.browser.getImg().getSubimage(bottomArea.x1, bottomArea.y1, bottomArea.width, bottomArea.height);
                String newSignature = Misc.imgToMD5(subImg);

                if (bottomSignature.equals(newSignature)) {
                    break; // there is nothing we can do anymore... we've scrolled to the bottom and haven't found the icon(s). We obviously don't have the required consumable(s)!
                } else {
                    bottomSignature = newSignature;
                }

            }
        }

        // OK, we're done, lets close the character menu window:
        boolean result = bot.browser.closePopupSecurely(BHBotUnity.cues.get("Filter"), BHBotUnity.cues.get("X"));
        if (!result) {
            BHBotUnity.logger.warn("Done. Error detected while trying to close character window. Ignoring...");
            return;
        }

        if (!consumables.isEmpty()) {
            BHBotUnity.logger.warn("Some consumables were not found (out of stock?) so were not consumed. These are: " + Misc.listToString(consumables) + ".");

            for (ConsumableType c : consumables) {
                bot.settings.consumables.remove(c.getName());
            }

            BHBotUnity.logger.warn("The following consumables have been removed from auto-consume list: " + Misc.listToString(consumables) + ". In order to reactivate them, reload your settings.ini file using 'reload' command.");
        } else {
            if (!duplicateConsumables.isEmpty())
                BHBotUnity.logger.info("Done. Some of the consumables have been skipped since they are already in use: " + Misc.listToString(duplicateConsumables));
            else
                BHBotUnity.logger.info("Done. Desired consumables have been successfully consumed.");
        }
    }

    /**
     * Will make sure all the icons in the inventory have been loaded.
     */
    private void waitForInventoryIconsToLoad() {
        Bounds bounds = new Bounds(450, 165, 670, 460); // detection area (where inventory icons are visible)
        MarvinSegment seg;
        Cue cue = new Cue(BHBotUnity.cues.get("LoadingInventoryIcon"), bounds);

        int counter = 0;
        seg = MarvinSegment.fromCue(cue, bot.browser);
        while (seg != null) {
            bot.browser.readScreen(Misc.Durations.SECOND);

            seg = MarvinSegment.fromCue(BHBotUnity.cues.get("StripSelectorButton"), bot.browser);
            if (seg == null) {
                BHBotUnity.logger.error("Error: while detecting possible loading of inventory icons, inventory cue has not been detected! Ignoring...");
                return;
            }

            seg = MarvinSegment.fromCue(cue, bot.browser);
            counter++;
            if (counter > 100) {
                BHBotUnity.logger.error("Error: loading of icons has been detected in the inventory screen, but it didn't finish in time. Ignoring...");
                return;
            }
        }
    }

    /**
     * Will reset readout timers.
     */
    void resetTimers() {
        timeLastExpBadgesCheck = 0;
        timeLastInvBadgesCheck = 0;
        timeLastGVGBadgesCheck = 0;
        timeLastEnergyCheck = 0;
        timeLastXealsCheck = 0;
        timeLastShardsCheck = 0;
        timeLastTicketsCheck = 0;
        timeLastTrialsTokensCheck = 0;
        timeLastGauntletTokensCheck = 0;
        timeLastBonusCheck = 0;
        timeLastFishingCheck = 0;
        timeLastFishingBaitsCheck = 0;
    }

    /* This will only reset timers for activities we still have resources to run */
    /* This saves cycling through the list of all activities to run every time we finish one */
    /* It's also useful for other related settings to be reset on activity finish */
    private void resetAppropriateTimers() {
        isAdventureStarted = false;
        specialDungeon = false;

        /*
            In this section we check if we are able to run the activity again and if so reset the timer to 0
            else we wait for the standard timer until we check again
         */

        if (((globalShards - 1) >= bot.settings.minShards) && bot.getState() == BHBotUnity.State.Raid) {
            timeLastShardsCheck = 0;
        }

        if (((globalBadges - bot.settings.costExpedition) >= bot.settings.costExpedition) && bot.getState() == BHBotUnity.State.Expedition) {
            timeLastExpBadgesCheck = 0;
        }

        if (((globalBadges - bot.settings.costInvasion) >= bot.settings.costInvasion) && bot.getState() == BHBotUnity.State.Invasion) {
            timeLastInvBadgesCheck = 0;
        }

        if (((globalBadges - bot.settings.costGVG) >= bot.settings.costGVG && bot.getState() == BHBotUnity.State.GVG)) {
            timeLastGVGBadgesCheck = 0;
        }

        if (((globalEnergy - 10) >= bot.settings.minEnergyPercentage) && bot.getState() == BHBotUnity.State.Dungeon) {
            timeLastEnergyCheck = 0;
        }

        if (((globalXeals - 1) >= bot.settings.minXeals) && bot.getState() == BHBotUnity.State.WorldBoss) {
            timeLastXealsCheck = 0;
        }

        if (((globalTickets - bot.settings.costPVP) >= bot.settings.costPVP) && bot.getState() == BHBotUnity.State.PVP) {
            timeLastTicketsCheck = 0;
        }

        if (((globalTokens - bot.settings.costTrials) >= bot.settings.costTrials) && bot.getState() == BHBotUnity.State.Trials) {
            timeLastTrialsTokensCheck = 0;
        }

        if (((globalTokens - bot.settings.costGauntlet) >= bot.settings.costGauntlet && bot.getState() == BHBotUnity.State.Gauntlet)) {
            timeLastGauntletTokensCheck = 0;
        }
    }

    private Bounds opponentSelector(int opponent) {

        if (bot.settings.pvpOpponent < 1 || bot.settings.pvpOpponent > 4) {
            //if setting outside 1-4th opponents we default to 1st
            BHBotUnity.logger.warn("pvpOpponent must be between 1 and 4, defaulting to first opponent");
            bot.settings.pvpOpponent = 1;
            return new Bounds(544, 188, 661, 225); //1st opponent
        }

        if (bot.settings.gvgOpponent < 1 || bot.settings.gvgOpponent > 4) {
            //if setting outside 1-4th opponents we default to 1st
            BHBotUnity.logger.warn("gvgOpponent must be between 1 and 4, defaulting to first opponent");
            bot.settings.gvgOpponent = 1;
            return new Bounds(544, 188, 661, 225); //1st opponent
        }

        return switch (opponent) {
            case 1 -> Bounds.fromWidthHeight(552, 193, 102, 27); // 1st opponent
            case 2 -> Bounds.fromWidthHeight(552, 247, 102, 27); // 2nd opponent
            case 3 -> Bounds.fromWidthHeight(552, 301, 102, 27); // 3rd opponent
            case 4 -> Bounds.fromWidthHeight(552, 355, 102, 27); // 4th opponent
            default -> Bounds.fromWidthHeight(552, 193, 102, 27); // 1st opponent
        };
    }

    void softReset() {
        bot.setState(BHBotUnity.State.Main);
        resetTimers();
    }

    private void handleFishing() {
        MarvinSegment seg;

        seg = MarvinSegment.fromCue(BHBotUnity.cues.get("Fishing"), Misc.Durations.SECOND * 5, bot.browser);
        if (seg != null) {

            //we make sure that the window is visible
            bot.browser.showBrowser();

            bot.browser.clickOnSeg(seg);
            Misc.sleep(Misc.Durations.SECOND); // we allow some seconds as maybe the reward popup is sliding down

            detectCharacterDialogAndHandleIt();

            int fishingTime = 10 + (bot.settings.baitAmount * 15); //pause for around 15 seconds per bait used, plus 10 seconds buffer

            bot.browser.readScreen();

            seg = MarvinSegment.fromCue(BHBotUnity.cues.get("Play"), Misc.Durations.SECOND * 5, bot.browser);
            if (seg != null) {
                bot.browser.clickOnSeg(seg);
            }

            seg = MarvinSegment.fromCue(BHBotUnity.cues.get("Start"), Misc.Durations.SECOND * 20, bot.browser);
            if (seg != null) {
                try {
                    BHBotUnity.logger.info("Pausing for " + fishingTime + " seconds to fish");
                    bot.scheduler.pause();

                    Process fisher = Runtime.getRuntime().exec("cmd /k \"cd DIRECTORY & java -jar bh-fisher.jar\" " + bot.settings.baitAmount);
                    if (!fisher.waitFor(fishingTime, TimeUnit.SECONDS)) { //run and wait for fishingTime seconds
                        bot.scheduler.resume();
                    }

                } catch (IOException | InterruptedException ex) {
                    BHBotUnity.logger.error("Can't start bh-fisher.jar", ex);
                }

            } else BHBotUnity.logger.info("start not found");

            if (!closeFishingSafely()) {
                BHBotUnity.logger.error("Error closing fishing, restarting..");
                restart();
            }

            bot.browser.readScreen(Misc.Durations.SECOND);
            if (bot.settings.enterGuildHall) enterGuildHall();

            if (bot.settings.hideWindowOnRestart) bot.browser.hideBrowser();
        }

    }

    private boolean closeFishingSafely() {
        MarvinSegment seg;
        bot.browser.readScreen();

        seg = MarvinSegment.fromCue(BHBotUnity.cues.get("Trade"), Misc.Durations.SECOND * 3, bot.browser);
        if (seg != null) {
            bot.browser.clickOnSeg(seg);
        }

        seg = MarvinSegment.fromCue(BHBotUnity.cues.get("X"), Misc.Durations.SECOND * 3, bot.browser);
        if (seg != null) {
            bot.browser.clickOnSeg(seg);
        }

        seg = MarvinSegment.fromCue(BHBotUnity.cues.get("FishingClose"), 3 * Misc.Durations.SECOND, bot.browser);
        if (seg != null) {
            bot.browser.clickOnSeg(seg);
        }

        seg = MarvinSegment.fromCue(BHBotUnity.cues.get("GuildButton"), Misc.Durations.SECOND * 5, bot.browser);
        //else not
        return seg != null; //if we can see the guild button we are successful

    }

    private void enterGuildHall() {
        MarvinSegment seg;

        seg = MarvinSegment.fromCue(BHBotUnity.cues.get("GuildButton"), Misc.Durations.SECOND * 5, bot.browser);
        if (seg != null) {
            bot.browser.clickOnSeg(seg);
        }

        seg = MarvinSegment.fromCue(BHBotUnity.cues.get("Hall"), Misc.Durations.SECOND * 5, bot.browser);
        if (seg != null) {
            bot.browser.clickOnSeg(seg);
        }
    }

    // TODO Review this metod to work with Unity Engine
    private void handleLoot() {
        MarvinSegment seg;
        BufferedImage victoryPopUpImg = bot.browser.getImg();

        if (bot.notificationManager.shouldNotify()) {

            String droppedItemMessage;
            Bounds victoryDropArea = new Bounds(453, 290, 247, 153);

            //linkedHashMap so we iterate from mythic > heroic
            LinkedHashMap<String, Cue> itemTier = new LinkedHashMap<>();
            itemTier.put("m", BHBotUnity.cues.get("ItemMyt"));
            itemTier.put("s", BHBotUnity.cues.get("ItemSet"));
            itemTier.put("l", BHBotUnity.cues.get("ItemLeg"));
            itemTier.put("h", BHBotUnity.cues.get("ItemHer"));

            for (Map.Entry<String, Cue> item : itemTier.entrySet()) {
                if (bot.settings.poNotifyDrop.contains(item.getKey()) || bot.settings.discordNotifyDrop.contains(item.getKey())) {
                    seg = FindSubimage.findImage(victoryPopUpImg, item.getValue().im, victoryDropArea.x1, victoryDropArea.y1, victoryDropArea.x2, victoryDropArea.y2);
                    if (seg != null) {
                        // so we don't get legendary crafting materials in raids triggering handleLoot
                        if ((item.getKey().equals("l")) && (restrictedCues(victoryPopUpImg, seg.getBounds()))) return;

                        // this is so we only get Coins, Crafting Materials and Schematics for heroic items
                        if (item.getKey().equals("h") && (!allowedCues(victoryPopUpImg, seg.getBounds()))) return;

                        // we get a mouse over screen of the item if possible
                        if (bot.getState() != BHBotUnity.State.Raid && bot.getState() != BHBotUnity.State.Dungeon && bot.getState() != BHBotUnity.State.Expedition && bot.getState() != BHBotUnity.State.Trials) {
                            //the window moves too fast in these events to mouseOver
                            bot.browser.moveMouseToPos(seg.getCenterX(), seg.getCenterY());
                            bot.browser.readScreen();
                            victoryPopUpImg = bot.browser.getImg();
                            bot.browser.moveMouseAway();
                        }

                        String tierName = getItemTier(item.getKey());
                        droppedItemMessage = tierName + " item dropped!";
                        BHBotUnity.logger.debug(droppedItemMessage);
                        if (bot.settings.victoryScreenshot) {
                            Misc.saveScreen(bot.getState() + "-" + tierName.toLowerCase(), "loot", BHBotUnity.includeMachineNameInScreenshots, victoryPopUpImg);
                        }

                        bot.notificationManager.sendDropNotification(tierName + " item drop in " + bot.getState(), droppedItemMessage, victoryPopUpImg);
                        break;
                    }
                }
            }
        }

    }

    // TODO Merge with ItemGrade Enum
    private String getItemTier(String tier) {
        return switch (tier) {
            case "m" -> "Mythic";
            case "s" -> "Set";
            case "l" -> "Legendary";
            case "h" -> "Heroic";
            default -> "unknown_tier";
        };
    }

    /**
     * Events that use badges as "fuel".
     */
    public enum BadgeEvent {
        None,
        GVG,
        Expedition,
        Invasion
    }

    public enum EquipmentType {
        Mainhand("StripTypeMainhand"),
        Offhand("StripTypeOffhand"),
        Head("StripTypeHead"),
        Body("StripTypeBody"),
        Neck("StripTypeNeck"),
        Ring("StripTypeRing");

        private final String cueName;

        EquipmentType(String cueName) {
            this.cueName = cueName;
        }

        public static String letterToName(String s) {
            return switch (s) {
                case "m" -> "mainhand";
                case "o" -> "offhand";
                case "h" -> "head";
                case "b" -> "body";
                case "n" -> "neck";
                case "r" -> "ring";
                default -> "unknown_item";
            };
        }

        public static EquipmentType letterToType(String s) {
            return switch (s) {
                case "m" -> Mainhand;
                case "o" -> Offhand;
                case "h" -> Head;
                case "b" -> Body;
                case "n" -> Neck;
                case "r" -> Ring;
                default -> null; // should not happen!
            };
        }

//		public int maxPos() {
////			return Math.min(6 + ordinal(), 10);
////		}

        /**
         * Returns equipment filter button cue (it's title cue actually)
         */
        public Cue getCue() {
            return BHBotUnity.cues.get(cueName);
        }

        public int minPos() {
            return 4 + ordinal();
        }

        public int getButtonPos() {
            return 8 + ordinal();
        }
    }

    public enum StripDirection {
        StripDown,
        DressUp
    }

    private enum ConsumableType {
        EXP_MINOR("exp_minor", "ConsumableExpMinor"), // experience tome
        EXP_AVERAGE("exp_average", "ConsumableExpAverage"),
        EXP_MAJOR("exp_major", "ConsumableExpMajor"),

        ITEM_MINOR("item_minor", "ConsumableItemMinor"), // item find scroll
        ITEM_AVERAGE("item_average", "ConsumableItemAverage"),
        ITEM_MAJOR("item_major", "ConsumableItemMajor"),

        GOLD_MINOR("gold_minor", "ConsumableGoldMinor"), // item find scroll
        GOLD_AVERAGE("gold_average", "ConsumableGoldAverage"),
        GOLD_MAJOR("gold_major", "ConsumableGoldMajor"),

        SPEED_MINOR("speed_minor", "ConsumableSpeedMinor"), // speed kicks
        SPEED_AVERAGE("speed_average", "ConsumableSpeedAverage"),
        SPEED_MAJOR("speed_major", "ConsumableSpeedMajor");

        private final String name;
        private final String inventoryCue;

        ConsumableType(String name, String inventoryCue) {
            this.name = name;
            this.inventoryCue = inventoryCue;
        }

        public static ConsumableType getTypeFromName(String name) {
            for (ConsumableType type : ConsumableType.values())
                if (type.name.equals(name))
                    return type;
            return null;
        }

        /**
         * Returns name as it appears in e.g. settings.ini.
         */
        public String getName() {
            return name;
        }

        /**
         * Returns image cue from inventory window
         */
        public Cue getInventoryCue() {
            return BHBotUnity.cues.get(inventoryCue);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public enum ItemGrade {
        COMMON("Common", 1),
        RARE("Rare", 2),
        EPIC("Epic", 3),
        LEGENDARY("Legendary", 4),
        MYTHIC("Mythic", 5);

        private final String name;
        private final int value;

        ItemGrade(String name, int value) {
            this.name = name;
            this.value = value;
        }

        public static ItemGrade getGradeFromValue(int value) {
            for (ItemGrade grade : ItemGrade.values())
                if (grade.value == value)
                    return grade;
            return null;
        }

        public int getValue() {
            return value;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * This Enum is used to group together all the information related to the World Boss
     */
    enum WorldBoss {
        Orlag("o", "Orlag Clan", 1, 3, 12, 5),
        Netherworld("n", "Netherworld", 2, 3, 13, 3),
        Melvin("m", "Melvin", 3, 10, 11, 4),
        Ext3rmin4tion("3", "3xt3rmin4tion", 4, 10, 11, 3),
        BrimstoneSyndicate("b", "Brimstone Syndicate", 5, 11, 12, 3),
        TitansAttack("t", "Titans Attack", 6, 11, 14, 3),
        IgnitedAbyss("i", "The Ignited Abyss", 7, 13, 14, 3),
        Unknown("?", "Unknown", 8, 13, 100, 1);

        private final String letter;
        private final String Name;
        private final int number;
        private final int minTier;
        private final int maxTier;
        private final int partySize;

        /**
         * @param letter    the shortcut letter used in settings.ini
         * @param Name      the real name of the World Boss
         * @param number    the World Boss number counting from left to right starting at 1
         * @param minTier   the minimum tier required to join the World Boss
         * @param maxTier   the maximum tier you are allowed to join for the World Boss
         * @param partySize the party size of the World Boss
         */
        WorldBoss(String letter, String Name, int number, int minTier, int maxTier, int partySize) {
            this.letter = letter;
            this.Name = Name;
            this.number = number;
            this.minTier = minTier;
            this.maxTier = maxTier;
            this.partySize = partySize;
        }

        String getLetter() {
            return letter;
        }

        String getName() {
            return Name;
        }

        int getNumber() {
            return number;
        }

        int getMinTier() {
            return minTier;
        }

        int getMaxTier() {
            return maxTier;
        }

        int getPartySize() {
            return partySize;
        }

        /*int[] getYScrollerPositions() {
            return yScrollerPositions;
        }*/

        static WorldBoss fromLetter(String Letter) {
            for (WorldBoss wb : WorldBoss.values()) {
                if (wb.getLetter().equals(Letter)) return wb;
            }
            return null;
        }

        static WorldBoss fromNumber(int number) {
            for (WorldBoss wb : WorldBoss.values()) {
                if (wb.getNumber() == number) return wb;
            }
            return null;
        }
    }

    void cueDifference() { //return similarity % between two screenshots taken 3 seconds apart
        bot.browser.readScreen();
        BufferedImage img1 = bot.browser.getImg();
        Misc.sleep(2500);
        bot.browser.readScreen();
        BufferedImage img2 = bot.browser.getImg();
        CueCompare.imageDifference(img1, img2, 0.8, 0, 800, 0, 520);
    }

    /*
     * Returns true if it finds a defined Cue
     * You can input with getSegBounds to only search the found item area
     */

    private boolean restrictedCues(BufferedImage victoryPopUpImg, Bounds foundArea) {
        MarvinSegment seg;
        HashMap<String, Cue> restrictedCues = new HashMap<>();
        restrictedCues.put("Sand Clock", BHBotUnity.cues.get("Material_R11"));
        restrictedCues.put("Monster Cell", BHBotUnity.cues.get("Material_R10"));
        restrictedCues.put("Power Stone", BHBotUnity.cues.get("Material_R9"));
        restrictedCues.put("Fire Blossom", BHBotUnity.cues.get("Material_R8"));
        restrictedCues.put("Crubble", BHBotUnity.cues.get("Material_R7"));
        restrictedCues.put("Beanstalk", BHBotUnity.cues.get("Material_R6"));
        restrictedCues.put("Luminous Stone", BHBotUnity.cues.get("Material_R5"));
        restrictedCues.put("Rombit", BHBotUnity.cues.get("Material_R4"));
        restrictedCues.put("Dubloon", BHBotUnity.cues.get("Material_R3"));
        restrictedCues.put("Hyper Shard", BHBotUnity.cues.get("Material_R2"));

        for (Map.Entry<String, Cue> cue : restrictedCues.entrySet()) {
            seg = FindSubimage.findImage(victoryPopUpImg, cue.getValue().im, foundArea.x1, foundArea.y1, foundArea.x2, foundArea.y2);
            if (seg != null) {
                BHBotUnity.logger.debug("Legendary: " + cue.getKey() + " found, skipping handleLoot");
                return true;
            }
        }
        return false;
    }

    /*
     * Returns true if it finds a defined Cue
     * You can input with getSegBounds to only search the found item area
     */

    private boolean allowedCues(BufferedImage victoryPopUpImg, Bounds foundArea) {
        MarvinSegment seg;

        //so we aren't triggered by Skeleton Key heroic cue
        MarvinSegment treasure = MarvinSegment.fromCue(BHBotUnity.cues.get("SkeletonTreasure"), bot.browser);
        if (treasure != null) {
            return false;
        }

        HashMap<String, Cue> allowedCues = new HashMap<>();
        allowedCues.put("Gold Coin", BHBotUnity.cues.get("GoldCoin"));
        allowedCues.put("Heroic Schematic", BHBotUnity.cues.get("HeroicSchematic"));
        allowedCues.put("Microprocessing Chip", BHBotUnity.cues.get("MicroChip"));
        allowedCues.put("Demon Blood", BHBotUnity.cues.get("DemonBlood"));
        allowedCues.put("Hobbit's Foot", BHBotUnity.cues.get("HobbitsFoot"));
        allowedCues.put("Melvin Chest", BHBotUnity.cues.get("MelvinChest"));
        allowedCues.put("Neural Net Rom", BHBotUnity.cues.get("NeuralNetRom"));
        allowedCues.put("Scarlarg Skin", BHBotUnity.cues.get("ScarlargSkin"));

        for (Map.Entry<String, Cue> cue : allowedCues.entrySet()) {
            seg = FindSubimage.findImage(victoryPopUpImg, cue.getValue().im, foundArea.x1, foundArea.y1, foundArea.x2, foundArea.y2);
            if (seg != null) {
                BHBotUnity.logger.debug(cue.getKey() + " found!");
                return true;
            }
        }
        return false;
    }

    String saveDebugWBTSScreen(int totalTS, int[] playersTS, String lastSavedName) {
        if (bot.settings.debugWBTS) {
            // To ease debug we put the TS values in the file name
            StringBuilder fileNameTS = new StringBuilder();
            fileNameTS.append("wb-")
                    .append(counters.get(BHBotUnity.State.WorldBoss).getTotal() + 1)
                    .append("-T").append(String.format("%,d", totalTS));

            for (int iPartyMember = 0; iPartyMember < playersTS.length; iPartyMember++) {
                fileNameTS.append("-").append(iPartyMember + 1).append("P").append(String.format("%,d", playersTS[iPartyMember]));
            }

            String finalFileName = fileNameTS.toString();
            if (!lastSavedName.equals(finalFileName)) {

                // We convert the image to B&W before we save it, so troubleshooting will be faster.
                MarvinImage toBlackAndWhite = new MarvinImage(bot.browser.getImg());
                toBlackAndWhite.toBlackWhite(120);
                toBlackAndWhite.update();

                Misc.saveScreen(fileNameTS.toString(), "wb-ts-debug", BHBotUnity.includeMachineNameInScreenshots, toBlackAndWhite.getBufferedImage());
            }
            return finalFileName;
        }

        return "";
    }

    void setAutoOff(int timeout) {
        MarvinSegment autoSeg = MarvinSegment.fromCue(BHBotUnity.cues.get("AutoOn"), timeout, bot.browser);

        if (autoSeg != null) {
            bot.browser.clickOnSeg(autoSeg);
        }
    }

    void setAutoOn(int timeout) {
        MarvinSegment autoSeg = MarvinSegment.fromCue(BHBotUnity.cues.get("AutoOff"), timeout, bot.browser);

        if (autoSeg != null) {
            bot.browser.clickOnSeg(autoSeg);
        }
    }

    /**
     * Takes care of opening the Character Menu (the one on the bottom left with your face in it)
     *
     * @return true if it was not possible to open the character menu
     */
    boolean openCharacterMenu() {
        // As it is not possible to build a cue for the character menu, we aim at the Raid icon that is the closest thing to it
        MarvinSegment seg = MarvinSegment.fromCue(BHBotUnity.cues.get("RaidButton"), bot.browser);
        if (seg == null) {
            Misc.saveScreen("opnecharmenu-no-raid-menu", "errors", BHBotUnity.includeMachineNameInScreenshots, bot.browser.getImg());
            BHBotUnity.logger.warn("Error: unable to detect raid! Skipping...");
            BHBotUnity.logger.debug(Misc.getStackTrace());
            return true;
        }

        bot.browser.clickInGame(seg.x1 + 20, seg.y1 + 130);

        seg = MarvinSegment.fromCue(BHBotUnity.cues.get("Runes"), 5 * Misc.Durations.SECOND, bot.browser);
        if (seg == null) {
            Misc.saveScreen("opnecharmenu-no-rune-button", "errors", BHBotUnity.includeMachineNameInScreenshots, bot.browser.getImg());
            BHBotUnity.logger.warn("Error: unable to detect runes button, char menu not opened! Skipping...");
            BHBotUnity.logger.debug(Misc.getStackTrace());
            return true;
        }

        return false;
    }

    /**
     * Used internally to make sure that the difficulty detection fro T/G is working as expected.
     * This method assumes that the main T/G window with the difficulty dropdown is opened.
     *
     * @return The found difficulty number.
     */
    int debugDifficulty() {
        String detectedDifficulty = "TG";

        bot.browser.readScreen();

        // We check that the difficulty screen is opened
        MarvinSegment seg = MarvinSegment.fromCue(BHBotUnity.cues.get("Difficulty"), bot.browser);
        if (seg == null) {

            seg = MarvinSegment.fromCue(BHBotUnity.cues.get("DifficultyExpedition"), bot.browser);

            if (seg == null) {
                BHBotUnity.logger.warn("Impossible to find difficulty cue. Make sure that the T/G or Expedition Window is opened.");
                return 0;
            } else {
                detectedDifficulty = "Expedition";
            }
        }

        Bounds suggestedBounds = Bounds.fromMarvinSegment(seg, null);
        BHBotUnity.logger.debug(detectedDifficulty + " difficulty Cue found at: " + seg);
        BHBotUnity.logger.debug("Suggested Bounds: " + suggestedBounds.getJavaCode(true, false));
        BHBotUnity.logger.debug("Suggested Bounds.fromWidthHeight: " + suggestedBounds.getJavaCode(true, true));

        BufferedImage numImg = bot.browser.getImg().getSubimage(seg.x1 + 26, seg.y1 + 32, 70, 25);

        MarvinImage toBlackAndWhite = new MarvinImage(numImg, "PNG");
        toBlackAndWhite.toBlackWhite(110);
        toBlackAndWhite.update();

        BufferedImage imb = toBlackAndWhite.getBufferedImage();
        int result = AdventureThread.readNumFromImg(imb, "tg_diff_cost_win_11_", Set.of(), true, false);

        // We save the image and the read difficulty for troubleshooting purpose
        String tgDiffFileName = detectedDifficulty.toLowerCase() + "_diff_" + result;
        String diffFile = Misc.saveScreen(tgDiffFileName, "debug/difficulty", true, numImg);
        BHBotUnity.logger.debug("Detected difficulty is: " + result);
        BHBotUnity.logger.debug("Image saved to: " + diffFile);

        return result;
    }

    int debugCost() {
        // Calculation offsets
        final int xOffset = 3, yOffset = 41, w = 31, h = 22;

        bot.browser.readScreen();
        // We make sure to search for the cost Cue in all the screen (TG position is different from GVG and so on...)
        Cue costCue = new Cue(BHBotUnity.cues.get("Cost"), null);
        MarvinSegment seg = MarvinSegment.fromCue(costCue, 15 * Misc.Durations.SECOND, bot.browser);
        if (seg == null) {
            BHBotUnity.logger.error("Error: unable to detect cost selection box!");
            bot.saveGameScreen("cost_selection", "errors");
            return 0; // error
        }

        Bounds suggestedBounds = Bounds.fromMarvinSegment(seg, null);
        BHBotUnity.logger.debug("Cost Cue found at: " + seg);
        BHBotUnity.logger.debug("Suggested Bounds: " + suggestedBounds.getJavaCode(true, false));
        BHBotUnity.logger.debug("Suggested Bounds.fromWidthHeight: " + suggestedBounds.getJavaCode(true, true));

        // We get the  region with the cost number
        BufferedImage numImg = bot.browser.getImg().getSubimage(seg.x1 + xOffset, seg.y1 + yOffset, w, h);

        // We transform it in B&W using available customMax
        MarvinImage im = new MarvinImage(numImg, "PNG");
        im.toBlackWhite(110);
        im.update();

        BufferedImage imb = im.getBufferedImage();

        int result = readNumFromImg(imb, "tg_diff_cost_win_11_", Set.of(0, 6, 7, 8, 9), false, true);

        // We save the image and the read difficulty for troubleshooting purpose
        String costFileName = "cost_" + result;
        String costFile = Misc.saveScreen(costFileName, "debug/cost", true, numImg);
        BHBotUnity.logger.debug("Detected cost is: " + result);
        BHBotUnity.logger.debug("Image saved to: " + costFile);

        return result;
    }

}
