package com.github.ilpersi.BHBot;

import com.google.gson.Gson;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.remote.UnreachableBrowserException;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.MessageFormat;
import java.util.List;
import java.util.*;


public class BHBotUnity {

    private static final String PROGRAM_NAME = "BHBotUnity";
    static CueManager cues;
    private static String BHBotVersion;
    private static Properties gitProperties;
    static String screenshotPath = "./screenshots/";
    static boolean includeMachineNameInScreenshots = true;
    static BHBotUnityLogger logger;

    // static settings
    static boolean debugDetectionTimes = false;
    static boolean debugNullBounds = false;
    static boolean debugFindImage = false;
    // TODO understand if it is possible to differentiate log settings without making them static
    static String logBaseDir;
    static long logMaxDays;
    static Level logLevel;

    AdventureThread adventure;
    private int numFailedRestarts = 0; // in a row

    Settings settings = new Settings();
    Scheduler scheduler = new Scheduler();
    NotificationManager notificationManager;
    ExceptionManager excManager;
    String browserDriverAddress = "127.0.0.1:9515";
    String browserExePath = settings.useFirefox ? "C:\\Program Files\\Mozilla Firefox\\firefox.exe" : "C:\\Users\\" + System.getProperty("user.name") + "\\AppData\\Local\\Chromium\\Application\\chrome.exe";
    String browserDriverExePath = settings.useFirefox ? "./geckodriver" : "./chromedriver.exe";
    private Thread adventureThread;
    private Thread blockerThread;

    private BHBotUnity.State state; // at which stage of the game/menu are we currently?
    private BHBotUnity.State lastJoinedState = null;
    private final String LAST_JOINED_CSV = "./data/last_status.csv";

    private long lastStateChange = Misc.getTime();
    /**
     * Set it to true to end main loop and end program gracefully
     */
    boolean finished = false;
    boolean running = false;
    BrowserManager browser;

    long botStartTime;

    // When we do not have anymore gems to use this is true
    boolean noGemsToBribe = false;

    // currently used scheduling
    Settings.ActivitiesScheduleSetting currentScheduling = null;

    public static void main(String[] args) {
        BHBotUnity bot = new BHBotUnity();
        bot.notificationManager = new NotificationManager(bot);
        bot.excManager = new ExceptionManager(bot);

        String browserProfile = "";

        // We make sure that our configurationFactory is added to the list of configuration factories.
        System.setProperty("log4j.configurationFactory", "com.github.ilpersi.BHBot.BHBotUnityConfigurationFactory");
        // We enable the log4j2 debug output if we need to
        if (bot.settings.logPringStatusMessages) System.setProperty("log4j2.debug", "true");

        // manage command line options
        for (int i = 0; i < args.length; i++) { //select settings file to load
            switch (args[i]) {
                case "chromedriver", "chromedriverpath", "chromeDriverExePath", "geckoDriverExePath", "browserDriverExePath" -> {
                    bot.browserDriverExePath = args[i + 1];
                    i++;
                }  //change chrome driver port
                case "chromedriveraddress", "chromeDriverAddress", "geckoDriverAddress", "browserDriverAddress" -> {
                    bot.browserDriverAddress = args[i + 1];
                    i++;
                }
                case "chromium", "chromiumpath", "chromiumExePath", "firefoxExePath", "browserExePath" -> {
                    bot.browserExePath = args[i + 1];
                    i++;
                }  //start bot in idle mode
                case "init", "idle" -> //start bot in idle mode
                        Settings.configurationFile = "LOAD_IDLE_SETTINGS";
                case "settings", "configurationFile" -> {
                    Settings.configurationFile = args[i + 1];
                    i++;
                }
                case "userdatadir", "userDataDir", "browserProfile", "cacheDir" -> {
                    browserProfile = args[i + 1];
                    i++;
                }
            }
        }

        Settings.initialConfigurationFile = Settings.configurationFile;

        if ("LOAD_IDLE_SETTINGS".equals(Settings.configurationFile)) {
            bot.settings.setIdle();
        } else {
            /* if the specified setting file is "settings.ini", we check if the file exists
            if the file does not exist, we assume this is the first time the user is running the bot
             */
            try {
                bot.settings.load(Settings.configurationFile);
            } catch (FileNotFoundException e) {
                // We handle the default configuration file and we generate an empty one
                if ("settings.ini".equals(Settings.configurationFile)) {
                    try {
                        Settings.resetIniFile();
                    } catch (IOException ex) {
                        System.out.println("Error while creating settings.ini in main folder");
                        ex.printStackTrace();
                        return;
                    }

                    try {
                        bot.settings.load(Settings.configurationFile);
                    } catch (FileNotFoundException ex) {
                        System.out.println("It was impossible to find settings.ini, even after it has been created!");
                        return;
                    }
                    bot.settings.setIdle();

                } else {
                    System.out.println("It was impossible to find file " + Settings.configurationFile + ".");
                    return;
                }
            }
        }

        // We make sure to save the default schedulings, so they are never erased at reload
        bot.settings.defaultActivitiesSchedule = bot.settings.activitiesSchedule;

        // settings are now loaded
        debugDetectionTimes = bot.settings.debugDetectionTimes;
        debugFindImage = bot.settings.debugFindImage;
        debugNullBounds = bot.settings.debugNullBounds;
        logBaseDir = bot.settings.logBaseDir;
        logMaxDays = bot.settings.logMaxDays;
        logLevel = bot.settings.logLevel;

        // We only set a default value if not argument is provided
        if ("".equals(browserProfile)) {
            browserProfile = bot.settings.useFirefox ? "./firefox_cache" : "./chrome_profile";
        }

        logger = BHBotUnityLogger.create();

        // we need to initialize the CueManager after that we started log4j, so that the cue manager can use it to log
        cues = new CueManager();

        // As we have initialized the cues successfully we also build familiar MD5 details
        EncounterManager.buildMD5();

        // If any error is present after parsing the config file, we stop the bot
        if (bot.settings.wrongSettingLines.size() > 0) {
            for (String wrongLine : bot.settings.wrongSettingLines) {
                logger.fatal("It was impossible to parse the following setting line and it has been skipped: '" + wrongLine + "'! " +
                        "Please review your settings.ini file");
            }
            return;
        }

        // If any warning is present during the setting parsing, we raise it
        if (!bot.settings.warningSettingLInes.isEmpty()) {
            for (String warningLine : bot.settings.warningSettingLInes) {
                logger.warn(warningLine);
            }
        }

        Properties properties = new Properties();
        try {
            properties.load(BHBotUnity.class.getResourceAsStream("/pom.properties"));
            BHBotVersion = properties.getProperty("version");
        } catch (IOException e) {
            logger.error("Impossible to get pom.properties from jar", e);
            BHBotVersion = "UNKNOWN";
        }

        try {
            logger.info(PROGRAM_NAME + " v" + BHBotVersion + " build on " + new Date(Misc.classBuildTimeMillis()) + " started.");
        } catch (URISyntaxException e) {
            logger.info(PROGRAM_NAME + " v" + BHBotVersion + " started. Unknown build date.");
        }

        gitProperties = Misc.getGITInfo();
        logger.info(MessageFormat.format("GIT commit id: {0}  time: {1}", gitProperties.get("git.commit.id"), gitProperties.get("git.commit.time")));
        logger.info(String.format("Hostname: %s", Misc.getMachineName()));
        logger.info(String.format("Hosting OS: '%s'", System.getProperty("os.name")));
        logger.info(String.format("Java version: '%s'", System.getProperty("java.version")));

        /*if (!"UNKNOWN".equals(BHBotVersion)) {
            checkNewRelease();
        } else {
            logger.warn("Unknown BHBotVersion, impossible to check for updates.");
        }*/

        logger.info("Settings loaded from file");

        // We check for no longer supported settings
        if (!bot.settings.checkUnsupportedSettings()) return;

        bot.settings.checkDeprecatedSettings();
        bot.settings.sanitizeSetting();

        if (!bot.settings.username.equals("") && !bot.settings.username.equals("yourusername")) {
            logger.info("Character: " + bot.settings.username);
        }

        if (!bot.checkPaths()) return;

        bot.cleanScreenDumps();

        bot.botStartTime = Misc.getTime();

        // Scanner scanner = new Scanner(System.in);
        logger.debug("Opening InputThread on stdin ...");
        InputThread reader = new InputThread(System.in, logger);
        while (!bot.finished) {
            String line;
            try {
                while ((line = reader.readLine()) != null) {
                    bot.processCommand((line));
                    Misc.sleep(500);
                }
            } catch (java.io.IOException e) {
                logger.error("Impossible to read user input", e);
            }

            // When the current schedule is no longer valid, we exit from it
            if (bot.running && bot.currentScheduling != null && State.Main.equals(bot.getState()) && !bot.scheduler.isPaused()) {

                /*
                 * As DungeonThread in running in parallel to this thread it may happen that it is starting adventures
                 * while we are exiting from the current scheduling, to prevent this we check how long we entered in the
                 * current state
                 * */
                long currentStateDuratoin = Misc.getTime() - bot.lastStateChange;

                if (!bot.currentScheduling.isActive() && currentStateDuratoin > (Misc.Durations.SECOND * 10)) {
                    bot.running = false;
                    bot.stop();
                    bot.currentScheduling = null;
                    continue;
                }
            }

            // When the bot is not running, we check if an active schedule is available
            if (!bot.running) {
                if (bot.settings.activitiesSchedule.isEmpty()) {
                    BHBotUnity.logger.debug("Scheduling is empty, using default configuration.");
                    bot.browser = new BrowserManager(bot, browserProfile);
                    bot.running = true;
                    bot.scheduler.resetIdleTime(true);
                    bot.processCommand("start");
                    continue;
                } else {

                    BHBotUnity.logger.trace("Checking for available schedulings");
                    for (Settings.ActivitiesScheduleSetting s : bot.settings.activitiesSchedule) {
                        if (s.isActive()) {

                            // We check what Chrome Profile path to use
                            String chromeProfilePath = "".equals(s.chromeProfilePath) ? browserProfile : s.chromeProfilePath;

                            // We check what setting plan to use
                            if (!"".equals(s.settingsPlan)) {
                                Settings.configurationFile = "plans/" + s.settingsPlan + ".ini";
                            } else {
                                Settings.configurationFile = Settings.initialConfigurationFile;
                            }

                            // We load the settings
                            try {
                                bot.settings.load(Settings.configurationFile);
                            } catch (FileNotFoundException e) {
                                BHBotUnity.logger.error("It was impossible to load setting file for scheduling : " + s);
                                continue;
                            }

                            // We save the current scheduling
                            bot.currentScheduling = s;

                            bot.settings.checkDeprecatedSettings();
                            bot.settings.sanitizeSetting();
                            bot.reloadLogger();

                            bot.browser = new BrowserManager(bot, chromeProfilePath);
                            bot.running = true;
                            bot.scheduler.resetIdleTime(true);
                            bot.processCommand("start");
                            BHBotUnity.logger.info("Current scheduler is: " + s);
                            break;
                        }
                    }
                }
            }

            BHBotUnity.logger.trace("Main Thread Sleeping");
            Misc.sleep(500);

        }

        // We save cookies before we quit
        bot.browser.serializeCookies();

        bot.stop();
        reader.close();
        logger.info(PROGRAM_NAME + " has finished.");
    }

    private void stop() {
        if (adventureThread != null && adventureThread.isAlive()) {
            try {
                // wait for 10 seconds for the main thread to terminate
                logger.info("Waiting for dungeon thread to finish... (timeout=10s)");
                adventureThread.join(10 * Misc.Durations.SECOND);
            } catch (InterruptedException e) {
                logger.error("Error when joining Main Thread", e);
            }

            if (adventureThread.isAlive()) {
                logger.warn("Dungeon thread is still alive. Force stopping it now...");
                adventureThread.interrupt();
                try {
                    adventureThread.join(); // until thread stops
                } catch (InterruptedException e) {
                    logger.error("Error while force stopping", e);
                }
            }
        }

        if (blockerThread != null && blockerThread.isAlive()) {
            try {
                // wait for 10 seconds for the main thread to terminate
                logger.info("Waiting for blocker thread to finish... (timeout=10s)");
                blockerThread.join(10 * Misc.Durations.SECOND);
            } catch (InterruptedException e) {
                logger.error("Error when joining Blocker Thread", e);
            }

            if (blockerThread.isAlive()) {
                logger.warn("Blocker thread is still alive. Force stopping it now...");
                blockerThread.interrupt();
                try {
                    blockerThread.join(); // until thread stops
                } catch (InterruptedException e) {
                    logger.error("Error while force stopping", e);
                }
            }
        }

        if (browser != null) browser.close();
    }

    private void processCommand(String c) {
        String[] params = c.split(" ");
        switch (params[0]) {
            case "c": { // detect cost from screen
                browser.readScreen();
                int current = adventure.detectCost();
                logger.info("Detected cost: " + current);

                if (params.length > 1) {
                    int goal = Integer.parseInt(params[1]);
                    logger.info("Goal cost: " + goal);
                    boolean result = adventure.selectCost(current, goal);
                    logger.info("Cost change result: " + result);
                }
                break;
            }
            case "compare":
                adventure.cueDifference();
                break;
            case "cont-shot":
            case "contshot":
                String contFileName = "cont-shot";
                if (params.length > 1)
                    contFileName = params[1];

                long duration = Misc.Durations.SECOND * 5;
                if (params.length > 2)
                    duration = Long.parseLong(params[2]);

                int delay = 500;
                if (params.length > 3)
                    delay = Integer.parseInt(params[3]);

                logger.info("Saving continous shot '" + contFileName + "' with duration " + Misc.millisToHumanForm(duration) + " and delay " + Misc.millisToHumanForm((long) delay));
                Misc.saveContinuousShot(contFileName, duration, delay, browser);
                logger.info("Continous shot completed.");
                break;
            case "crash": {
                throw new RuntimeException("CRASH!");
            }
            case "cues":
                if (params.length < 2) {
                    logger.info("available sub commands are reload");
                    break;
                }

                if ("reload".equals(params[1])) {
                    if (params.length < 3) {
                        logger.error("No relative path has been specified.");
                    }

                    cues.reloadFromDisk(params[2]);

                    BHBotUnity.logger.info("Reloaded Cues from " + params[2]);
                }

                break;
            case "d": { // detect difficulty from screen
                int current = adventure.debugTGDifficulty();

                if (params.length > 1) {
                    int goal = Integer.parseInt(params[1]);
                    logger.info("Goal difficulty: " + goal);
                    int result = adventure.selectDifficulty(current, goal, BHBotUnity.cues.get("SelectDifficulty"), 1, true);
                    logger.info("Difficulty change result: " + result);
                }
                break;
            }
            case "debugds":
                MarvinSegment topChoiceDS = MarvinSegment.fromCue("TopChoice", Misc.Durations.SECOND, browser);
                if (topChoiceDS == null) break;

                final int dsOffset = 60;
                final int x1DSDiff = 90, y1DSDiff = 12, wDSDiff = -184, hDSDiff = -29;
                final ScrollBarManager dsSB = new ScrollBarManager(browser);

                if (dsSB.canScrollDown) {
                    do {
                        browser.readScreen();

                        topChoiceDS = MarvinSegment.fromCue("TopChoice", Misc.Durations.SECOND, browser);

                        if (topChoiceDS != null) {
                            // B&W Conversion
                            MarvinImage bwDSImg = new MarvinImage(browser.getImg());
                            bwDSImg.toBlackWhite(110);
                            bwDSImg.update();
                            BufferedImage bwDS = bwDSImg.getBufferedImage();

                            StringBuilder fileName = new StringBuilder("ds-");

                            // We get the five values available on monitor
                            for (int i = 0; i < 5; i++) {
                                if (i > 0) fileName.append("_");

                                BufferedImage topLvlBImg = bwDS.getSubimage(topChoiceDS.x1 + x1DSDiff, (topChoiceDS.y1 + y1DSDiff) + (dsOffset * i), topChoiceDS.width + wDSDiff, topChoiceDS.height + hDSDiff);

                                int diffSel = AdventureThread.readNumFromImg(topLvlBImg, "tg_diff_selection_17_", new HashSet<>(), false, false);
                                fileName.append(diffSel);
                            }

                            Misc.saveScreen(fileName.toString(), "debug-tg-ds", true, bwDS);
                        }

                        for (int i = 0; i < 5; i++) {
                            dsSB.scrollDown(500);
                            browser.moveMouseAway();
                        }


                    } while (!dsSB.isAtBottom());
                }
                break;
            case "do":
                switch (params[1]) {
                    case "baits":
                        // force fishing baits
                        logger.info("Forcing fishing baits collection...");
                        scheduler.doFishingBaitsImmediately = true;
                        break;
                    case "bounties":
                        // force bounties
                        logger.info("Forcing Bounty collection...");
                        scheduler.collectBountiesImmediately = true;
                        break;
                    case "dungeon":
                        // force dungeon (regardless of energy)
                        logger.info("Forcing dungeon...");
                        scheduler.doDungeonImmediately = true;
                        break;
                    case "expedition":
                        // force dungeon (regardless of energy)
                        logger.info("Forcing expedition...");
                        scheduler.doExpeditionImmediately = true;
                        break;
                    case "fishing":
                        // force fishing
                        logger.info("Forcing fishing...");
                        scheduler.doFishingImmediately = true;
                        break;
                    case "gauntlet":
                        logger.info("Forcing gauntlet...");
                        scheduler.doGauntletImmediately = true;
                        break;
                    case "gvg":
                        // force gvg
                        logger.info("Forcing GVG...");
                        scheduler.doGVGImmediately = true;
                        break;
                    case "invasion":
                        // force invasion
                        logger.info("Forcing invasion...");
                        scheduler.doInvasionImmediately = true;
                        break;
                    case "pvp":
                        // force pvp
                        logger.info("Forcing PVP...");
                        scheduler.doPVPImmediately = true;
                        break;
                    case "raid":
                        // force raid (if we have at least 1 shard though)
                        logger.info("Forcing raid...");
                        scheduler.doRaidImmediately = true;
                        break;
                    case "trials":
                        // force 1 run of gauntlet/trials (regardless of tokens)
                        logger.info("Forcing trials...");
                        scheduler.doTrialsImmediately = true;
                        break;
                    case "worldboss":
                        // force invasion
                        logger.info("Forcing World Boss...");
                        scheduler.doWorldBossImmediately = true;
                        break;
                    case "consumables":

                        break;
                    default:
                        logger.warn("Unknown dungeon : '" + params[1] + "'");
                        break;
                }
                break;
            case "dr", "diffrange":
                Bounds difficultyRangeBounds;
                final int x1Diff = 30, y1Diff = 5, wDiff = -58, hDiff = -29;
                MarvinSegment topChoice = MarvinSegment.fromCue("TopChoice", Misc.Durations.SECOND, browser);

                if (topChoice == null) break;

                final int yOffset = 60;
                difficultyRangeBounds = Bounds.fromWidthHeight(topChoice.x1 + x1Diff, topChoice.y1 + y1Diff, topChoice.width + wDiff, topChoice.height + hDiff);

                for (int i = 0; i < 5; i++) {
                    int posOffset = i * yOffset;
                    BufferedImage topRangeImg = browser.getImg().getSubimage(difficultyRangeBounds.x1, difficultyRangeBounds.y1 + posOffset, difficultyRangeBounds.width, difficultyRangeBounds.height);
                    MarvinImage im = new MarvinImage(topRangeImg);
                    im.toBlackWhite(110);
                    im.update();

                    int[] diffRange = adventure.readNumRangeFromImg(im.getBufferedImage(), "tg_diff_range_16_", new HashSet<>(), "hyphen", "-");
                    BHBotUnity.logger.debug("Detected difficulty range: " + Arrays.toString(diffRange));
                }

                break;
            case "ds":
            case "diffsel":
                browser.readScreen();
                final Bounds topLvlBounds = Bounds.fromWidthHeight(350, 145, 75, 26);
                final int diffOffset = 60;

                for (int i = 0; i < 5; i++) {
                    BufferedImage topLvlBImg = browser.getImg().getSubimage(topLvlBounds.x1, topLvlBounds.y1 + (diffOffset * i), topLvlBounds.width, topLvlBounds.height);
                    MarvinImage topLvlMImg = new MarvinImage(topLvlBImg);
                    topLvlMImg.toBlackWhite(110);
                    topLvlMImg.update();
                    int diffSel = AdventureThread.readNumFromImg(topLvlMImg.getBufferedImage(), "tg_diff_selection_17_", new HashSet<>(), false, false);
                    BHBotUnity.logger.debug("Detected difficulty selection: " + diffSel);
                }
                break;
            case "exit":
            case "quit":
            case "stop":
                finished = true;
                break;
            case "hide":
                browser.hideBrowser();
                settings.hideWindowOnRestart = true;
                break;
            case "loadsettings":
                String file = Settings.configurationFile;
                if (params.length > 1)
                    file = params[1];

                try {
                    settings.load(file);
                } catch (FileNotFoundException e) {
                    BHBotUnity.logger.error("It was impossible to find setting file: " + file + ".");
                    break;
                }

                settings.checkDeprecatedSettings();
                settings.sanitizeSetting();
                reloadLogger();
                break;
            case "menu":
                if (params.length <= 1) {
                    BHBotUnity.logger.error("Not enough parameters for menu command. Available commands are 'pos', 'screen'");
                    break;
                }

                switch (params[1]) {
                    case "pos", "positions" -> Misc.findScrollBarPositions(adventure.bot, false);
                    case "screen" -> Misc.findScrollBarPositions(adventure.bot, true);
                }

                break;
            case "pause":
                if (params.length > 1) {
                    int pauseDuration = Integer.parseInt(params[1]) * Misc.Durations.MINUTE;
                    scheduler.pause(pauseDuration);
                } else {
                    scheduler.pause();
                }
                break;
            case "plan":
                try {
                    settings.load("plans/" + params[1] + ".ini");
                } catch (FileNotFoundException e) {
                    BHBotUnity.logger.error("It was impossible to find plan plans/" + params[1] + ".ini" + "!");
                    break;
                }

                settings.checkDeprecatedSettings();
                settings.sanitizeSetting();
                reloadLogger();
                logger.info("Plan loaded from " + "<plans/" + params[1] + ".ini>.");
                break;
            case "pomessage":
                logger.info("This command is deprecated, use 'test notification [your_message]' instead");
                break;
            case "print":
                if (params.length < 2) {
                    BHBotUnity.logger.error("Missing parameters for print command: print familiars|version");
                    break;
                }

                switch (params[1]) {
                    case "config-file":
                        BHBotUnity.logger.info("Initial configuration file: " + Settings.initialConfigurationFile);
                        BHBotUnity.logger.info("Current configuration file: " + Settings.configurationFile);
                        break;
                    case "familiars":
                    case "familiar":
                    case "fam":
                        AdventureThread.printFamiliars();
                        break;
                    case "fam-md5":
                        if (params.length == 2)
                            EncounterManager.printMD5();
                        else if (params.length == 3)
                            EncounterManager.printMD5(params[2]);
                        else
                            BHBotUnity.logger.warn("USAGE: print fam-md5 [familiarName]");
                        break;
                    case "schedule":
                    case "scheduling":
                        if (settings.activitiesSchedule.size() == 0) {
                            BHBotUnity.logger.info("No scheduling are present at the moment.");
                        } else {
                            StringBuilder schedulingStr = new StringBuilder("Current available schedules are:\n");

                            for (Settings.ActivitiesScheduleSetting activityScheduling : settings.activitiesSchedule) {
                                schedulingStr.append(activityScheduling.equals(currentScheduling) ? "[X] " : "[ ] ")
                                        .append(activityScheduling).append("\n");
                            }

                            BHBotUnity.logger.info(schedulingStr.toString());
                        }
                        break;
                    case "screen-rect":
                        int minx = 0, miny = 0, maxx = 0, maxy = 0, cnt = 0;
                        GraphicsEnvironment environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
                        for (GraphicsDevice device : environment.getScreenDevices()) {

                            Rectangle bounds = device.getDefaultConfiguration().getBounds();
                            minx = Math.min(minx, bounds.x);
                            miny = Math.min(miny, bounds.y);
                            maxx = Math.max(maxx, bounds.x + bounds.width);
                            maxy = Math.max(maxy, bounds.y + bounds.height);

                            double scale = (double) device.getDisplayMode().getWidth() / (double) device.getDefaultConfiguration().getBounds().width;

                            cnt += 1;

                            Rectangle screenRect = new Rectangle(minx, miny, maxx - minx, maxy - miny);
                            BHBotUnity.logger.info("[Screen " + cnt + "] =>" + screenRect + " Scale => " + String.format("%.02f%%", scale));
                        }
                        break;
                    case "stats":
                    case "stat":
                    case "statistics":

                        StringBuilder aliveMsg = new StringBuilder();
                        aliveMsg.append("Current session statistics:\n\n");

                        for (State state : State.values()) {
                            if (adventure.counters.get(state).getTotal() > 0) {
                                aliveMsg.append(state.getName()).append(" ")
                                        .append(adventure.counters.get(state).successRateDesc())
                                        .append("\n");
                            }
                        }
                        logger.info(aliveMsg.toString());

                        break;
                    case "version":
                        try {
                            logger.info(PROGRAM_NAME + " v" + BHBotVersion + " build on " + new Date(Misc.classBuildTimeMillis()) + " started.");
                        } catch (URISyntaxException e) {
                            logger.info(PROGRAM_NAME + " v" + BHBotVersion + " started. Unknown build date.");
                        }

                        Properties gitPropertis = Misc.getGITInfo();
                        logger.info("GIT commit id: " + gitPropertis.get("git.commit.id") + "  time: " + gitPropertis.get("git.commit.time"));
                        break;
                    default:
                        logger.warn("Impossible to print : '" + params[1] + "'");
                        break;
                }
                break;
            case "resetini":
                try {
                    Settings.resetIniFile();
                } catch (IOException e) {
                    BHBotUnity.logger.error("It was impossible to reset ini file: " + Settings.configurationFile);
                }
                break;
            case "restart":
                adventure.restart(false);
                break;
            case "shot":
                String fileName = "shot";
                if (params.length > 1)
                    fileName = params[1];

                String filePath = adventure.bot.saveGameScreen(fileName);

                logger.info("Screenshot '" + fileName + "' saved as " + filePath + ".");
                break;
            case "start":
                adventure = new AdventureThread(this);
                adventureThread = new Thread(adventure, "AdventureThread");
                adventureThread.start();

                BlockerThread blocker = new BlockerThread(this);
                blockerThread = new Thread(blocker, "BlockerThread");
                blockerThread.start();
                break;
            case "softreset":
                adventure.softReset();
                break;
            case "readouts":
            case "resettimers":
                adventure.resetTimers();
                logger.info("Readout timers reset.");
                break;
            case "reload":
                settings.load();
                reloadLogger();
                logger.info("Settings reloaded from disk.");
                break;
            case "resume":
                scheduler.resume();
                break;
            case "sd":
            case "screen-dump":
                settings.dumpReadScreen = !settings.dumpReadScreen;
                logger.info("Screen dump is now " + (settings.dumpReadScreen ? "enabled" : "disabled") + ".");
                break;
            case "set": {
                List<String> list = new ArrayList<>();
                int i = c.indexOf(" ");
                if (i == -1)
                    return;
                list.add(c.substring(i + 1));
                settings.load(list, false, "command");
                settings.checkDeprecatedSettings();
                settings.sanitizeSetting();
                reloadLogger();
                logger.info("Settings updated manually: <" + list.get(0) + ">");
                break;
            }
            case "show":
                browser.showBrowser();
                settings.hideWindowOnRestart = false;
                break;
            case "test":

                if (params.length <= 1) {
                    BHBotUnity.logger.error("Not enough parameters for test command");
                    break;
                }

                switch (params[1]) {
                    case "ai", "autoignore" -> {
                        Boolean ignoreBoss = null;
                        Boolean ignoreShrines = null;
                        if (params.length > 2) {
                            switch (params[2].toLowerCase()) {
                                case "off", "0", "no", "do" -> ignoreBoss = false;
                                case "on", "1", "yes", "y" -> ignoreBoss = true;
                            }
                        }
                        if (params.length > 3) {
                            switch (params[3].toLowerCase()) {
                                case "off", "0", "no", "do" -> ignoreShrines = false;
                                case "on", "1", "yes", "y" -> ignoreShrines = true;
                            }
                        }
                        if (ignoreBoss == null) {
                            BHBotUnity.logger.warn("No value is set for ignoreBoss, setting it to false.");
                            ignoreBoss = true;
                        }
                        if (ignoreShrines == null) {
                            BHBotUnity.logger.warn("No value is set for ignoreShrines, setting it to false.");
                            ignoreShrines = false;
                        }
                        if (!adventure.shrineManager.updateShrineSettings(ignoreBoss, ignoreShrines)) {
                            logger.error("Something went wrong when checking auto ignore settings!");
                        }
                    }
                    case "dropdown" -> {
                        MarvinSegment Up = MarvinSegment.fromCue("DropDownUp", browser);
                        MarvinSegment Down = MarvinSegment.fromCue("DropDownDown", browser);

                        if (Up != null) {
                            BHBotUnity.logger.debug("Up arrow cue found: " + Up);
                        }
                        if (Down != null) {
                            BHBotUnity.logger.debug("Down arrow cue found: " + Down);
                        }
                    }
                    case "e", "expeditionread" -> adventure.expeditionReadTest();
                    case "notification" -> {
                        // We split on spaces so we re-build the original message
                        String notificationMessage = params.length > 2 ? String.join(" ", Arrays.copyOfRange(params, 2, params.length)) : "Test message from BHbot!";
                        notificationManager.sendTestNotification(notificationMessage);
                    }
                    case "runes" -> adventure.runeManager.detectEquippedMinorRunes(true, true);
                    case "tgdiff" ->
                            // Use this command to troubleshoot T/G difficulty in main window
                            adventure.debugTGDifficulty();
                    default -> BHBotUnity.logger.debug("Unknown test command: " + params[1]);
                }
                break;
            default:
                logger.warn("Unknown command: '" + c + "'");
                break;
        }
    }

    private boolean checkPaths() {
        String cuesPath = "./cues/";
        String dataPath = "./data/";

        File browserExe = new File(browserExePath);
        File browserDriverExe = new File(browserDriverExePath);
        File cuePath = new File(cuesPath);
        File screenPath = new File(screenshotPath);
        File dataFolder = new File(dataPath);

        if (!browserExe.exists()) {
            logger.fatal("Impossible to find browser executable in path " + browserExePath + ". Bot will be stopped!");
            return false;
        } else {
            try {
                logger.debug("Found Browsers in " + browserExe.getCanonicalPath());
            } catch (IOException e) {
                logger.error("Error while getting Canonical Path for Chromium", e);
            }
        }

        if (!browserDriverExe.exists()) {
            logger.fatal("Impossible to find browser driver executable in path " + browserDriverExePath + ". Bot will be stopped!");
            return false;
        } else {
            try {
                logger.debug("Found browser driver in " + browserDriverExe.getCanonicalPath());
            } catch (IOException e) {
                logger.error("Error while getting Canonical Path for browser driver", e);
            }
        }

        if (!screenPath.exists()) {
            if (!screenPath.mkdir()) {
                logger.fatal("Impossible to create screenshot folder in " + screenshotPath);
                return false;
            } else {
                try {
                    logger.info("Created screenshot folder in " + screenPath.getCanonicalPath());
                } catch (IOException e) {
                    logger.error("Error while getting Canonical Path for newly created screenshots", e);
                }
            }
        } else {
            try {
                logger.debug("Found screenshots in " + screenPath.getCanonicalPath());
            } catch (IOException e) {
                logger.error("Error while getting Canonical Path for screenshots", e);
            }
        }

        if (cuePath.exists() && !cuePath.isFile()) {
            try {
                logger.warn("Found cues in '" + cuePath.getCanonicalPath() +
                        "'. This folder is no longer required as all the cues are now part of the jar file.");
            } catch (IOException e) {
                logger.error("Error while checking cues folder", e);
            }
        }

        if (!dataFolder.exists()) {
            if (!dataFolder.mkdir()) {
                logger.fatal("Impossible to create data folder in " + dataPath);
                return false;
            } else {
                try {
                    logger.info("Created data folder in " + dataFolder.getCanonicalPath());
                } catch (IOException e) {
                    logger.error("Error while getting Canonical Path for newly created data path", e);
                }
            }
        } else {
            try {
                logger.debug("Found data in " + dataFolder.getCanonicalPath());
            } catch (IOException e) {
                logger.error("Error while getting Canonical Path for data", e);
            }
        }

        return true;
    }

    private void cleanScreenDumps() {
        // We make sure to clean the previous dumped read screens
        if (settings.dumpReadScreen) {
            logger.info("Cleaning screen dumps...");

            int dumpCnt = 0;
            int dumpErrors = 0;

            File screenDumpFile = new File("screenshots/screen-dump");

            if (screenDumpFile.exists()) {
                File[] screenDumpList = screenDumpFile.listFiles();

                if (screenDumpList != null) {
                    for (File file : screenDumpList) {
                        dumpCnt++;
                        if (!file.delete()) {
                            logger.debug("It was impossible to clean the screen dump named " + file.getAbsolutePath());
                            dumpCnt--;
                            dumpErrors++;
                        }
                    }
                }
            }

            logger.debug("Cleaned " + dumpCnt + " screen dumps.");

            if (dumpErrors > 0) {
                logger.warn(dumpErrors + " files were not deleted during the screen dumps cleanup.");
            }
        }
    }

    void reloadLogger() {
        ConfigurationFactory configFactory = new BHBotUnityConfigurationFactory();
        ConfigurationFactory.setConfigurationFactory(configFactory);
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        ctx.start(configFactory.getConfiguration(ctx, ConfigurationSource.NULL_SOURCE));
    }

    @SuppressWarnings("unused")
    private static void checkNewRelease() {

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/repos/ilpersi/BHBot/releases"))
                .build();

        HttpResponse<String> response = null;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            logger.error("Exception while getting latest version info from Git Hub", e);
        }

        Gson gson = new Gson();

        Double currentVersion = Double.parseDouble(BHBotVersion);

        if (response != null) {
            int statusCode = response.statusCode();
            if (statusCode != 200) {
                logger.error("GitHub version check failed with HTTP error code : " + statusCode);
                return;
            }

            GitHubRelease[] lastReleaseInfo;
            lastReleaseInfo = gson.fromJson(response.body(), GitHubRelease[].class);

            HashMap<String, String> updates = new HashMap<>();

            for (GitHubRelease release : lastReleaseInfo) {

                String tagName = release.tagName;
                String tagUrl = "https://api.github.com/repos/ilpersi/BHBot/git/refs/tags/" + tagName;

                request = HttpRequest.newBuilder()
                        .uri(URI.create(tagUrl))
                        .build();

                response = null;
                try {
                    response = client.send(request, HttpResponse.BodyHandlers.ofString());
                } catch (IOException | InterruptedException e) {
                    logger.error("Exception while getting tag ref info from Git Hub", e);
                }

                if (response != null) {
                    statusCode = response.statusCode();
                    if (statusCode != 200) {
                        logger.error("GitHub version check failed with HTTP error code : " + statusCode);
                        return;
                    }

                    GitHubTag lastReleaseTagInfo;
                    lastReleaseTagInfo = gson.fromJson(response.body(), GitHubTag.class);

                    Double onlineVersion = Double.parseDouble(release.tagName.replace("v", ""));

                    if (onlineVersion > currentVersion) {
                        updates.put(release.tagName, release.releaseNotes);
                    } else if (onlineVersion.equals(currentVersion)) {
                        if (updates.size() > 0) break;

                        if (lastReleaseTagInfo.object.sha.equals(gitProperties.get("git.commit.id"))) {
                            logger.debug("BHBot is running on the latest version.");
                        } else {
                            logger.warn("You are running on a bleeding edge version of BHBot and there may be bugs.");
                        }
                        return;
                    } else {
                        logger.warn("You are running on a bleeding edge version of BHBot and there may be bugs.");
                    }
                } else {
                    return;
                }

            }
            if (updates.size() > 0) {
                logger.warn("Your current version is " + updates.size() + " version behind the latest released one. Here is a list of updates:");

                for (Map.Entry<String, String> vu : updates.entrySet()) {
                    logger.warn(vu.getKey() + " updates:");
                    for (String line : vu.getValue().split("\n")) {
                        logger.warn(line);
                    }
                }

                Misc.sleep(5000);
            }
        }
    }

    synchronized State getState() {
        return state;
    }

    /**
     * Get last joined state. The method tries to be smart. If the current last joined state is null, the method reads
     * it from the state csv.
     *
     * @return The last joined state
     */
    synchronized State getLastJoinedState() {

        if (lastJoinedState == null) {
            lastJoinedState = State.Unknown;

            File stateCSV = new File(LAST_JOINED_CSV);

            if (stateCSV.exists()) {
                BufferedReader br;

                try {
                    br = new BufferedReader(new FileReader(stateCSV));

                    try {
                        String line = br.readLine();
                        String[] data = line.split(";");

                        if ("last_status".equals(data[0])) {
                            lastJoinedState = State.fromShortcut(data[1]);

                            if (lastJoinedState == null)
                                lastJoinedState = State.Unknown;
                        }
                        br.close();
                    } catch (IOException e) {
                        BHBotUnity.logger.error("Impossible to read file last status file: " + stateCSV.getAbsolutePath(), e);
                    }
                } catch (FileNotFoundException e) {
                    BHBotUnity.logger.error("Last status file not found in: " + stateCSV.getAbsolutePath(), e);
                }


            } else {
                lastJoinedState = State.Unknown;
            }
        }

        return lastJoinedState;
    }

    synchronized void setState(State state) {
        this.lastStateChange = Misc.getTime();
        this.state = state;
    }

    /**
     * This method is the setter for the lastJoinedState attribute
     *
     * @param state The state you want to set as the last valid one
     */
    synchronized void setLastJoinedState(State state) {
        this.lastJoinedState = state;
        this.saveLastJoinedToFile();
    }

    /**
     * As the bot may crash, the last joined state is also saved in a CSV file so that it is persistent.
     *
     */
    private void saveLastJoinedToFile() {
        BufferedWriter writer;
        try {
            writer = new BufferedWriter(new FileWriter(LAST_JOINED_CSV));
            writer.write(String.format("last_status;%s", lastJoinedState.getShortcut()));
            writer.close();
        } catch (IOException e) {
            BHBotUnity.logger.error("It was impossible to save last status to CSV file", e);
        }
    }

    /**
     * Takes screenshot of current game and saves it to disk to a file with a given prefix (date will be added, and optionally a number at the end of file name).
     * In case of failure, it will just ignore the error.
     *
     * @param prefix The string used to prefix the screenshot name
     * @return name of the path in which the screenshot has been saved (successfully or not)
     */
    synchronized String saveGameScreen(String prefix) {
        return Misc.saveScreen(prefix, null, BHBotUnity.includeMachineNameInScreenshots, browser.takeScreenshot(true));
    }

    synchronized String saveGameScreen(String prefix, BufferedImage img) {
        return Misc.saveScreen(prefix, null, BHBotUnity.includeMachineNameInScreenshots, img);
    }

    synchronized String saveGameScreen(String prefix, String subFolder) {
        return Misc.saveScreen(prefix, subFolder, BHBotUnity.includeMachineNameInScreenshots, browser.takeScreenshot(true));
    }

    void dumpCrashLog(Exception originalException) {
        // save screen shot:
        String file = saveGameScreen("crash", "errors");

        if (file == null) {
            logger.error("Impossible to create crash screenshot");
            return;
        }

        String stackTrace;
        if (originalException != null) {
            StringWriter sw = new StringWriter();
            originalException.printStackTrace(new PrintWriter(sw));
            stackTrace = sw.toString();
        } else {
            stackTrace = Misc.getStackTrace();
        }

        boolean savedST = Misc.saveTextFile(file.substring(0, file.length() - 4) + ".txt", stackTrace);
        if (!savedST) {
            logger.info("Impossible to save the stack trace in dumpCrashLog!");
        }

        notificationManager.sendCrashNotification("BHBot has crashed and a driver emergency restart has been performed!\n\n" + stackTrace, file);

    }

    /**
     * @param emergency         true in case something bad happened (some kind of an error for which we had to do a restart)
     * @param useDoNotShareLink is the bot running with do_not_share link enabled?
     */
    void restart(boolean emergency, boolean useDoNotShareLink) {
        restart(emergency, useDoNotShareLink, null);
    }

    void restart(boolean emergency, boolean useDoNotShareLink, Exception originalException) {
        final int MAX_NUM_FAILED_RESTARTS = 5;

        // take emergency screenshot (which will have the developer to debug the problem):
        if (emergency) {
            logger.warn("Doing driver emergency restart...");
            dumpCrashLog(originalException);
        }

        browser.cookiesLoaded = false;

        try {
            browser.restart(useDoNotShareLink);
        } catch (Exception e) {

            if (e instanceof NoSuchElementException)
                logger.warn("Problem: web element with id 'game' not found!");
            if (e instanceof UnreachableBrowserException) {
                logger.error("Impossible to connect to the bot.browser. Make sure chromedirver is started. Will retry in a few minutes... (sleeping)");
                Misc.sleep(5 * Misc.Durations.MINUTE);
                restart(true, useDoNotShareLink, e);
                return;
            }

            numFailedRestarts++;
            if (numFailedRestarts > MAX_NUM_FAILED_RESTARTS) {
                logger.fatal("Something went wrong with driver restart. Number of restarts exceeded " + MAX_NUM_FAILED_RESTARTS + ", this is why I'm aborting...");
                finished = true;
            } else {
                logger.error("Something went wrong with driver restart. Will retry in a few minutes... (sleeping)", e);
                Misc.sleep(5 * Misc.Durations.MINUTE);
                restart(true, useDoNotShareLink, e);
            }
            return;
        }

        browser.scrollGameIntoView();

        int counter = 0;
        boolean restart = false;
        while (true) {
            try {
                browser.readScreen();

                MarvinSegment seg = MarvinSegment.fromCue(cues.get("Login"), browser);
                browser.detectLoginFormAndHandleIt(seg);
            } catch (Exception e) {
                counter++;
                if (counter > 20) {
                    logger.error("Error: <" + e.getMessage() + "> while trying to detect and handle login form. Restarting...", e);
                    restart = true;
                    break;
                }

                Misc.sleep(10 * Misc.Durations.SECOND);
                continue;
            }
            break;
        }
        if (restart) {
            restart(true, useDoNotShareLink);
            return;
        }

        logger.info("Game element found. Starting to run bot..");

        setState(State.Loading);
        scheduler.resetIdleTime();
        scheduler.resume(); // in case it was paused
        numFailedRestarts = 0; // must be last line in this method!

        // we make sure that the shrinemanager is resetted at restart time and we
        // skip the initialization if idleMode is true
        adventure.settings = new SettingsManager(this, settings.idleMode);
        adventure.shrineManager = new AutoShrineManager(this, settings.idleMode);
        adventure.runeManager = new AutoRuneManager(this, settings.idleMode);
        adventure.encounterManager = new EncounterManager(this);
        adventure.reviveManager.reset();
        adventure.positionChecker = new AdventurePositionChecker();
        adventure.adventureSpeed = 1;
    }

    enum State {
        Dungeon("Dungeon", "d"),
        Expedition("Expedition", "e"),
        FishingBaits("Fishing Baits Popup", "a"),
        Gauntlet("Gauntlet", "g"),
        GVG("GVG", "v"),
        Invasion("Invasion", "i"),
        Loading("Loading...", "l"),
        Main("Main screen", "m"),
        PVP("PVP", "p"),
        Raid("Raid", "r"),
        Trials("Trials", "t"),
        WorldBoss("World Boss", "w"),
        RerunWorldBoss("World Boss Rerun", "wr"),
        RerunRaid("Raid Rerun", "rr"),
        RerunDungeon("Dungeon Rerun", "dr"),
        Unknown("Unknown Dungeon", "u");

        private final String name;
        private final String shortcut;

        State(String name, String shortcut) {
            this.name = name;
            this.shortcut = shortcut;
        }

        public String getName() {
            return name;
        }

        public String getShortcut() {
            return shortcut;
        }

        static State fromShortcut(String shortcut) {
            for (State state : State.values())
                if (state.shortcut != null && state.shortcut.equals(shortcut))
                    return state;
            return null;
        }
    }
}
