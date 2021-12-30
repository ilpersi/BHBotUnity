package com.github.ilpersi.BHBot;

import org.openqa.selenium.Dimension;
import org.openqa.selenium.Point;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.RasterFormatException;
import java.io.*;
import java.net.*;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BrowserManager {
    private static By byElement;

    private WebDriver driver;
    private Capabilities caps;
    private JavascriptExecutor jsExecutor;
    private WebElement game;
    private String doNotShareUrl = "";

    private BufferedImage img = null; // latest screen capture
    private final BHBot bot;

    private final String browserProfile;

    private long lastClickTime = Misc.getTime();

    private final String COOKIE_DAT_PATH_FORMAT = "./data/cookies_%s.dat";
    boolean cookiesLoaded = false;

    BrowserManager(BHBot bot, String browserProfile) {
        this.bot = bot;
        this.browserProfile = browserProfile;
    }

    boolean isDoNotShareUrl() {
        return !"".equals(doNotShareUrl);
    }

    private synchronized void connect()  {
        if (!bot.settings.useFirefox) {
            ChromeOptions options = new ChromeOptions();

            // will create this profile folder where chromedriver.exe is located!
            options.addArguments("user-data-dir=" + browserProfile);

            //set Chromium binary location
            options.setBinary(bot.browserExePath);

            if (bot.settings.autoStartChromeDriver) {
                System.setProperty("webdriver.chrome.driver", bot.browserDriverExePath);
            } else {
                BHBot.logger.info("chromedriver auto start is off, make sure it is started before running BHBot");
                if (System.getProperty("webdriver.chrome.driver", null) != null) {
                    System.clearProperty("webdriver.chrome.driver");
                }
            }

            /*
            * When we connect the driver, if we don't know the do_not_share_url and if the configs require it,
            * the bot will enable the logging of network events so that when it is fully loaded, it will be possible
            * to analyze them searching for the magic URL
            * */

            if (!isDoNotShareUrl() && bot.settings.useDoNotShareURL) {
                LoggingPreferences logPrefs = new LoggingPreferences();
                logPrefs.enable(LogType.PERFORMANCE, Level.ALL);
                // https://stackoverflow.com/a/56536604/1280443
                options.setCapability("goog:loggingPrefs", logPrefs);
                options.setCapability(CapabilityType.LOGGING_PREFS, logPrefs);
            }

            DesiredCapabilities capabilities = DesiredCapabilities.chrome();
            capabilities.setCapability("chrome.verbose", false);
            capabilities.setCapability(ChromeOptions.CAPABILITY, options);

            if (bot.settings.autoStartChromeDriver) {
                driver = new ChromeDriver(options);
                caps = ((ChromeDriver) driver).getCapabilities();
            } else {
                URL driverAddress = null;
                try {
                    driverAddress = new URL("http://" + bot.browserDriverAddress);
                } catch (MalformedURLException e) {
                    BHBot.logger.error("Malformed URL when connecting to Web driver: ", e);
                }
                driver = new RemoteWebDriver(driverAddress, capabilities);
                caps = ((RemoteWebDriver) driver).getCapabilities();
            }
        } else {
//            ProfilesIni profileIni = new ProfilesIni();
//            FirefoxProfile profile = profileIni.getProfile(browserProfile);

            FirefoxOptions options = new FirefoxOptions();
//            options.setProfile(profile);

            // Geckodriver is quite verbose so we redirect the log to null
            if (System.getProperty("os.name").contains("Windows"))
                System.setProperty(FirefoxDriver.SystemProperty.BROWSER_LOGFILE, "NUL");
            else if (System.getProperty("os.name").contains("Linux"))
                System.setProperty(FirefoxDriver.SystemProperty.BROWSER_LOGFILE, "/dev/null");
            else
                System.setProperty(FirefoxDriver.SystemProperty.BROWSER_LOGFILE, "logs/geckodriver.log");

            // We also try to minimize all the possible logs
            LoggingPreferences pref = new LoggingPreferences();
            pref.enable(LogType.BROWSER, Level.WARNING);
            pref.enable(LogType.CLIENT, Level.WARNING);
            pref.enable(LogType.DRIVER, Level.WARNING);
            pref.enable(LogType.PERFORMANCE, Level.WARNING);
            pref.enable(LogType.PROFILER, Level.WARNING);
            pref.enable(LogType.SERVER, Level.WARNING);

            //set Chromium binary location
            options.setBinary(bot.browserExePath);
            options.setCapability(CapabilityType.LOGGING_PREFS, pref);

            if (bot.settings.autoStartChromeDriver) {
                System.setProperty(GeckoDriverService.GECKO_DRIVER_EXE_PROPERTY, bot.browserDriverExePath);
            } else {
                BHBot.logger.info("geckodriver auto start is off, make sure it is started before running BHBot");
                if (System.getProperty(GeckoDriverService.GECKO_DRIVER_EXE_PROPERTY, null) != null) {
                    System.clearProperty(GeckoDriverService.GECKO_DRIVER_EXE_PROPERTY);
                }
            }

            /*
             * When we connect the driver, if we don't know the do_not_share_url and if the configs require it,
             * the bot will enable the logging of network events so that when it is fully loaded, it will be possible
             * to analyze them searching for the magic URL
             * */

            if (!isDoNotShareUrl() && bot.settings.useDoNotShareURL) {
                LoggingPreferences logPrefs = new LoggingPreferences();
                logPrefs.enable(LogType.PERFORMANCE, Level.ALL);
                // https://stackoverflow.com/a/56536604/1280443
                options.setCapability("goog:loggingPrefs", logPrefs);
                options.setCapability(CapabilityType.LOGGING_PREFS, logPrefs);
            }

            DesiredCapabilities capabilities = DesiredCapabilities.firefox();
            capabilities.setCapability(FirefoxOptions.FIREFOX_OPTIONS, options);

            if (bot.settings.autoStartChromeDriver) {
                driver = new FirefoxDriver(options);
                caps = ((FirefoxDriver) driver).getCapabilities();
            } else {
                URL driverAddress = null;
                try {
                    driverAddress = new URL("http://" + bot.browserDriverAddress);
                } catch (MalformedURLException e) {
                    BHBot.logger.error("Malformed URL when connecting to Web driver: ", e);
                }
                driver = new RemoteWebDriver(driverAddress, capabilities);
                caps = ((RemoteWebDriver) driver).getCapabilities();
            }
        }

        jsExecutor = (JavascriptExecutor) driver;

    }

    synchronized void restart(boolean useDoNotShareUrl) {
        if (useDoNotShareUrl) {
            Pattern regex = Pattern.compile("\"(https://.+?\\?DO_NOT_SHARE_THIS_LINK[^\"]+?)\"");
            for (LogEntry le : driver.manage().logs().get(LogType.PERFORMANCE)) {
                Matcher regexMatcher = regex.matcher(le.getMessage());
                if (regexMatcher.find()) {
                    BHBot.logger.debug("DO NOT SHARE URL found!");
                    doNotShareUrl = regexMatcher.group(1);
                    break;
                }
            }
        }

        try {
            if (driver != null) {
                // driver.close();
                driver.quit();
            }
            driver = null;
        } catch (Exception e) {
            BHBot.logger.error("Error while quitting from Chromium", e);
        }

        // disable some annoying INFO messages:
        Logger.getLogger("").setLevel(Level.WARNING);

        connect();

        if (bot.settings.hideWindowOnRestart)
            hideBrowser();
        if ("".equals(doNotShareUrl)) {
            String standardURL = "http://www.kongregate.com/games/Juppiomenz/bit-heroes";
            if (!standardURL.equals(driver.getCurrentUrl())) driver.navigate().to(standardURL);
            byElement = By.id("game");
        } else {
            if (!doNotShareUrl.equals(driver.getCurrentUrl())) driver.navigate().to(doNotShareUrl);
            byElement = By.xpath("//div[1]");
        }

        game = driver.findElement(byElement);

        int vw = Math.toIntExact((Long) jsExecutor.executeScript("return window.outerWidth - window.innerWidth + arguments[0];", game.getSize().width));
        int vh = Math.toIntExact((Long) jsExecutor.executeScript("return window.outerHeight - window.innerHeight + arguments[0];", game.getSize().height));
        if ("".equals(doNotShareUrl)) {
            driver.manage().window().setSize(new Dimension(vw + 50, vh + 30));
        } else {
            driver.manage().window().setSize(new Dimension(vw, vh));
        }
    }

    synchronized void close() {
        if (driver != null) {
            try {
                driver.close();
                driver.quit();
            } catch (org.openqa.selenium.NoSuchSessionException e) {
                BHBot.logger.debug("Error while closing driver in BrowserManager.");
            }
        }
    }

    synchronized void hideBrowser() {
        driver.manage().window().setPosition(new Point(-10000, 0)); // just to make sure
        BHBot.logger.info("Chrome window has been hidden.");
    }

    synchronized void showBrowser() {
        driver.manage().window().setPosition(new Point(0, 0));
        BHBot.logger.info("Chrome window has been restored.");
    }

    synchronized void scrollGameIntoView() {
        WebElement element = driver.findElement(byElement);

        String scrollElementIntoMiddle = "var viewPortHeight = Math.max(document.documentElement.clientHeight, window.innerHeight || 0);"
                + "var elementTop = arguments[0].getBoundingClientRect().top;"
                + "window.scrollBy(0, elementTop-(viewPortHeight/2));";

        jsExecutor.executeScript(scrollElementIntoMiddle, element);
        Misc.sleep(1000);
    }

    /**
     * Handles login screen (it shows seldom though. Perhaps because some cookie expired or something... anyway, we must handle it or else bot can't play the game anymore).
     */
    synchronized void detectLoginFormAndHandleIt(MarvinSegment seg) {
        if (seg == null)
            return;

        // open login popup window:
        jsExecutor.executeScript("active_user.activateInlineLogin(); return false;"); // I found this code within page source itself (it gets triggered upon clicking on some button)

        Misc.sleep(5000); // if we don't sleep enough, login form may still be loading and code bellow will not get executed!

        // fill in username:
        WebElement weUsername;
        try {
            weUsername = driver.findElement(By.cssSelector("body#play > div#lightbox > div#lbContent > div#kongregate_lightbox_wrapper > div#lightbox_form > div#lightboxlogin > div#new_session_shared_form > form > dl > dd > input#username"));
        } catch (NoSuchElementException e) {
            BHBot.logger.warn("Problem: username field not found in the login form (perhaps it was not loaded yet?)!");
            return;
        }
        weUsername.clear();
        weUsername.sendKeys(bot.settings.username);
        BHBot.logger.info("Username entered into the login form.");

        WebElement wePassword;
        try {
            wePassword = driver.findElement(By.cssSelector("body#play > div#lightbox > div#lbContent > div#kongregate_lightbox_wrapper > div#lightbox_form > div#lightboxlogin > div#new_session_shared_form > form > dl > dd > input#password"));
        } catch (NoSuchElementException e) {
            BHBot.logger.warn("Problem: password field not found in the login form (perhaps it was not loaded yet?)!");
            return;
        }
        wePassword.clear();
        wePassword.sendKeys(bot.settings.password);
        BHBot.logger.info("Password entered into the login form.");

        // press the "sign-in" button:
        WebElement btnSignIn;
        try {
            btnSignIn = driver.findElement(By.cssSelector("body#play > div#lightbox > div#lbContent > div#kongregate_lightbox_wrapper > div#lightbox_form > div#lightboxlogin > div#new_session_shared_form > form > dl > dt#signin > input"));
        } catch (NoSuchElementException e) {
            return;
        }
        btnSignIn.click();

        BHBot.logger.info("Signed-in manually (we were signed-out).");

        scrollGameIntoView();
    }

    synchronized BufferedImage takeScreenshot(boolean ofGame) {


        try {
            // we scroll the window to the game element
            jsExecutor.executeScript("arguments[0].scrollIntoView(true);", game);

            // we read the image as a byte array and later convert it to a BufferedImage
            byte[] imgBytes = ((TakesScreenshot)driver).getScreenshotAs(OutputType.BYTES);
            InputStream in  = new ByteArrayInputStream(imgBytes);
            BufferedImage bImageFromConvert = ImageIO.read(in);

            if (ofGame) {
                String listStr = (String) jsExecutor.executeScript("var rect = arguments[0].getBoundingClientRect();" +
                "return '' + parseInt(rect.left) + ',' + parseInt(rect.top) + ',' + parseInt(rect.width) + ',' + parseInt(rect.height)", game);
                String[] list = listStr.split(",");

                final int x = Math.max(Integer.parseInt(list[0]), 0);
                final int y = Math.max(Integer.parseInt(list[1]), 0);
                final int width = Integer.parseInt(list[2]);
                final int height = Integer.parseInt(list[3]);

                BufferedImage result;
                try {
                    result = bImageFromConvert.getSubimage(x, y, width, height);
                } catch (java.awt.image.RasterFormatException e) {
                    jsExecutor.executeScript("arguments[0].scrollIntoView(true);", game);
                    BHBot.logger.trace("Error when taking screenshot based on getBoundingClientRect()", e);
                    return new BufferedImage(800, 520, BufferedImage.TYPE_INT_RGB);
                }

                return result;
            }
            else
                return bImageFromConvert;
        } catch (StaleElementReferenceException e) {
            // sometimes the game element is not available, if this happen we just return an empty image
            BHBot.logger.debug("Stale image detected while taking a screenshot. Trying to reset game element.");

            // For more details about this line of code, have a look here: https://www.selenium.dev/exceptions/#stale_element_reference
            try {
                game = driver.findElement(byElement);
            } catch (Exception ex) {
                BHBot.logger.error("It was impossible to reset the game element! Generating an empty screenshot.");
            }

            return new BufferedImage(800, 520, BufferedImage.TYPE_INT_RGB);

        } catch (TimeoutException | IOException e) {
            // sometimes Chrome/Chromium crashes and it is impossible to take screenshots from it
            BHBot.logger.warn("Selenium could not take a screenshot. A monitor screenshot will be taken using AWT.", e);

            if (bot.settings.hideWindowOnRestart) showBrowser();

            java.awt.Rectangle screenRect = new java.awt.Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            BufferedImage screen;
            try {
                screen = new Robot().createScreenCapture(screenRect);
            } catch (AWTException ex) {
                BHBot.logger.error("Impossible to perform a monitor screenshot", ex);
                screen = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            }

            if (bot.settings.hideWindowOnRestart) hideBrowser();
            return screen;
        } catch (RasterFormatException e) {
            jsExecutor.executeScript("arguments[0].scrollIntoView(true);", game);
            throw e;
        } catch (RuntimeException e) {
            BHBot.logger.error("Runtime error when taking screenshot: ", e);
            restart(false);
            return new BufferedImage(800, 520, BufferedImage.TYPE_INT_RGB);
        }
    }

    /**
     * Moves mouse to position (0,0) in the 'game' element (so that it doesn't trigger any highlight popups or similar
     */
    synchronized void moveMouseAway() {
        moveMouseToPos(5, 5);
    }

    //moves mouse to XY location (for triggering hover text)

    synchronized void moveMouseToPos(int x, int y) {
        try {
            Point movePos = getChromeOffset(x, y);

            Actions act = new Actions(driver);
            act.moveToElement(game, movePos.x, movePos.y);
            act.perform();
        } catch (Exception e) {
            // do nothing
        }
    }

    /**
     * Performs a mouse click on the center of the given segment
     */
    synchronized void clickOnSeg(MarvinSegment seg) {
        clickInGame(seg.getCenterX(), seg.getCenterY());
    }

    synchronized void clickInGame(Point clickPoint) {
        clickInGame(clickPoint.x, clickPoint.y);
    }

    synchronized void clickInGame(int x, int y) {
        Point clickCoordinates = getChromeOffset(x, y);

        int CLICK_DELAY = 100;
        while ((Misc.getTime() - lastClickTime) < CLICK_DELAY) {
            Misc.sleep(CLICK_DELAY / 2);
            lastClickTime = Misc.getTime();
        }

        Actions act = new Actions(driver);
        act.moveToElement(game, clickCoordinates.x, clickCoordinates.y);
        act.perform();

        act = new Actions(driver);
        act.click();
        act.perform();

        // so that the mouse doesn't stay on the button, for example. Or else button will be highlighted and cue won't get detected!
        moveMouseAway();
    }

    /**
     * Will close the popup by clicking on the 'close' cue and checking that 'popup' cue is gone. It will repeat this operation
     * until either 'popup' cue is gone or timeout is reached. This method ensures that the popup is closed. Sometimes just clicking once
     * on the close button ('close' cue) doesn't work, since popup is still sliding down and we miss the button, this is why we need to
     * check if it is actually closed. This is what this method does.
     * <p>
     * Note that before entering into this method, caller had probably already detected the 'popup' cue (but not necessarily). <br>
     * Note: in case of failure, it will print it out.
     *
     * @return false in case it failed to close it (timed out).
     */
    synchronized boolean closePopupSecurely(Cue popup, Cue close) {
        MarvinSegment closeSeg, popupSeg;

        final int timeOutDuration = Misc.Durations.SECOND * 10;
        final int WAIT_DELAY = Misc.Durations.SECOND;

        closeSeg = MarvinSegment.fromCue(close, bot.browser);
        popupSeg = MarvinSegment.fromCue(popup, bot.browser);

        // make sure popup window is on the screen (or else wait until it appears):
        long timeOut = Misc.getTime() + timeOutDuration;
        while (popupSeg == null) {
            if (Misc.getTime() > timeOut) {
                BHBot.logger.error("Error: unable to close popup <" + popup + "> securely: popup cue not detected!");
                Misc.saveScreen("closePopupSecurely-popup-not-detected-" + popup.name, "errors", true, bot.browser.getImg());
                return false;
            }

            popupSeg = MarvinSegment.fromCue(popup, WAIT_DELAY, bot.browser);
        }

        timeOut = Misc.getTime() + timeOutDuration;
        // there is no more popup window, so we're finished!
        while (popupSeg != null) {
            if (closeSeg != null) {
                bot.browser.clickOnSeg(closeSeg);
                if (MarvinSegment.waitForNull(close, WAIT_DELAY, bot.browser)) return true;
            }

            if (Misc.getTime() > timeOut) {
                BHBot.logger.error("Error: unable to close popup <" + popup + "> securely: either close button < " + close + " > has not been detected or popup would not close!");
                BHBot.logger.error(Misc.getStackTrace());
                Misc.saveScreen("closePopupSecurely-close-error-" + popup.name + "-" + close.name, "errors", true, bot.browser.getImg());
                return false;
            }

            closeSeg = MarvinSegment.fromCue(close, WAIT_DELAY, bot.browser);
            popupSeg = MarvinSegment.fromCue(popup, WAIT_DELAY, bot.browser);
        }

        return true;
    }

    synchronized void readScreen() {
        readScreen(true);
    }

    /**
     * @param game if true, then screenshot of a WebElement will be taken that contains the flash game. If false, then simply a screenshot of a browser will be taken.
     */
    @SuppressWarnings("SameParameterValue")
    synchronized void readScreen(boolean game) {
        readScreen(0, game);
    }

    /**
     * First sleeps 'wait' milliseconds and then reads the screen. It's a handy utility method that does two things in one command.
     */
    synchronized void readScreen(int wait) {
        readScreen(wait, true);
    }

    /**
     * @param wait first sleeps 'wait' milliseconds and then reads the screen. It's a handy utility method that does two things in one command.
     * @param game if true, then screenshot of a WebElement will be taken that contains the flash game. If false, then simply a screenshot of a browser will be taken.
     */
    synchronized void readScreen(int wait, boolean game) {
        if (wait != 0)
            Misc.sleep(wait);
        img = takeScreenshot(game);

        // This setting should only be enabled for development purpose. Performance impact is very high.
        if (bot.settings.dumpReadScreen) Misc.saveScreen("screen-dump", "screen-dump", BHBot.includeMachineNameInScreenshots, img);
    }

    /**
     * This method is meant to be used for development purpose. In some situations you want to "fake" the readScreen result
     * with an hand-crafted image. If this is the case, this method is here to help with it.
     *
     * @param screenFilePath the path to the image to be used to load the screen
     */
    @SuppressWarnings("unused")
    void loadScreen(String screenFilePath) {
        File screenImgFile = new File(screenFilePath);

        if (screenImgFile.exists()) {
            BufferedImage screenImg = null;
            try {
                screenImg = ImageIO.read(screenImgFile);
            } catch (IOException e) {
                BHBot.logger.error("Error when loading game screen ", e);
            }

            img = screenImg;
        } else {
            BHBot.logger.error("Impossible to load screen file: " + screenImgFile.getAbsolutePath());
        }
    }

    synchronized public BufferedImage getImg() {
        if (img == null)
            readScreen();

        return img;
    }

    private int getChromeVersion() {
        String[] versionArray = caps.getVersion().split("\\.");
        return Integer.parseInt(versionArray[0]);
    }

    private Point getChromeOffset(int x, int y) {
        // As of Chrome 75, offsets are calculated from the center of the elements
        if (getChromeVersion() >= 75) {
            Dimension gameDimension = game.getSize();
            int gameCenterX = gameDimension.width / 2;
            int gameCenterY = gameDimension.height / 2;
            return new Point(x -gameCenterX, y - gameCenterY);
        } else {
            return new Point(x, y);
        }
    }

    void refresh() {
        driver.navigate().refresh();
        Misc.sleep(Misc.Durations.SECOND * 3);
        // As we refreshed, we make sure to point game to the newly loaded element
        game = driver.findElement(byElement);
    }

    void manageLogin() {

        // Once for every run we try to load saved cookies
        if (!cookiesLoaded) {
            cookiesLoaded = true;

            HashSet<Cookie> cookies = this.deserializeCookies();
            if (cookies.size() > 0){
                for (Cookie cookie: cookies) {
                    driver.manage().addCookie(cookie);
                }
                BHBot.logger.info("Loaded cookies from file " + String.format(COOKIE_DAT_PATH_FORMAT, bot.settings.username));
                refresh();
            }

            return;
        }

        WebElement btnClose;
        try {
            btnClose = driver.findElement(By.cssSelector("div#kongregate_lightbox_wrapper > div.header_bar > a.close_link"));
        } catch (NoSuchElementException e) {
            return;
        }

        if (bot.settings.username.length() > 0 && !bot.settings.username.equalsIgnoreCase("yourusername"))
            btnClose.click();
        else {
            BHBot.logger.warn("Login form detected and no username provided in the settings!");
            return;
        }

        // fill in username and password:
        WebElement weUsername;
        try {
            weUsername = driver.findElement(By.id("welcome_username"));
        } catch (NoSuchElementException e) {
            return;
        }
        weUsername.clear();
        weUsername.sendKeys(bot.settings.username);

        WebElement wePassword;
        try {
            wePassword = driver.findElement(By.id("welcome_password"));
        } catch (NoSuchElementException e) {
            return;
        }
        wePassword.clear();
        wePassword.sendKeys(bot.settings.password);

        // press the "sign-in" button:
        WebElement btnSignIn;
        try {
            btnSignIn = driver.findElement(By.id("welcome_box_sign_in_button"));
        } catch (NoSuchElementException e) {
            return;
        }
        btnSignIn.click();

        // To make sure that "loogin too many times" is correctly detected we need to pause for a while
        Misc.sleep(Misc.Durations.SECOND * 3);

        WebElement tooManyLogins;
        try {
            tooManyLogins = driver.findElement(By.xpath("//*[@id=\"lightboxlogin_message\"]"));
        } catch (NoSuchElementException e)  {
            BHBot.logger.info("Signed-in manually (sign-in prompt was open).");

            HashSet<Cookie> cookies = new HashSet<>(driver.manage().getCookies());
            this.serializeCookies(cookies);
            return;
        }

        BHBot.logger.debug("tooManyLogins != null TEXT: " + tooManyLogins.getText());
        if (tooManyLogins.getText().contains("login too many times")) {
            BHBot.logger.warn("Too many login attempts, pausing for 30 minutes");
            bot.scheduler.pause(Misc.Durations.MINUTE * bot.settings.tooManyLoginsTimer);
            driver.navigate().refresh();
        }

    }

    /**
     * This method will take care of serializing all the cookies in a data file
     *
     * @param cookies An HashSet containing all the cookies that need to be serialized
     */
    private void serializeCookies(HashSet<Cookie> cookies) {
        try {
            String datFileNane = String.format(COOKIE_DAT_PATH_FORMAT, bot.settings.username);
            FileOutputStream fileOut = new FileOutputStream(datFileNane);
            ObjectOutputStream oss = new ObjectOutputStream(fileOut);
            oss.writeObject(cookies);
        } catch (FileNotFoundException e) {
            BHBot.logger.debug("FileNotFoundException while dumping cookies.", e);
        } catch (IOException e) {
            BHBot.logger.debug("IOException while dumping cookies.", e);
        }
    }

    /**
     * This method will take care of de-serializing Cookies from a dat file
     *
     * @return An HashSet with all the deserialized Cookies
     */
    private HashSet<Cookie> deserializeCookies() {
        String datFileNane = String.format(COOKIE_DAT_PATH_FORMAT, bot.settings.username);

        File datFile = new File(datFileNane);
        if (datFile.exists()) {
            try {
                FileInputStream fileIn = new FileInputStream(datFileNane);
                ObjectInputStream ois = new ObjectInputStream(fileIn);

                //noinspection unchecked
                return (HashSet<Cookie>) ois.readObject();
            } catch (FileNotFoundException e) {
                BHBot.logger.debug("FileNotFoundException while deserializing cookies.", e);
            } catch (IOException e) {
                BHBot.logger.debug("IOException while deserializing cookies.", e);
            } catch (ClassNotFoundException e) {
                BHBot.logger.debug("ClassNotFoundException while deserializing cookies.", e);
            }
        }

        return new HashSet<>();
    }
}
