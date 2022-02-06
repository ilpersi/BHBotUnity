package com.github.ilpersi.BHBot;

public class ExceptionManager {
    BHBotUnity bot;

    /**
     * Number of consecutive exceptions. We need to track it in order to detect crash loops that we must break by restarting the Chrome driver. Or else it could get into loop and stale.
     */
    int numConsecutiveException = 0;

    ExceptionManager (BHBotUnity bot) {
        this.bot = bot;
    }

    /**
     * @param e The exception that has to be managed by the Exception manager
     * @return true if the class was able to manage the exception. Please note that, whenever true is returned,
     *         the numConsecutiveException counter is increased by one and never reset to zero. Setting it to
     *         zero is up to the caller of this method. Once that the exception limit defined in
     *         MAX_CONSECUTIVE_EXCEPTIONS is reached, the bot will restart itself.
     */
    synchronized boolean manageException(Exception e) {

        numConsecutiveException++;
        int MAX_CONSECUTIVE_EXCEPTIONS = 10;
        if (numConsecutiveException > MAX_CONSECUTIVE_EXCEPTIONS) {
            numConsecutiveException = 0; // reset it
            BHBotUnity.logger.warn("Problem detected: number of consecutive exceptions is higher than " + MAX_CONSECUTIVE_EXCEPTIONS + ". This probably means we're caught in a loop. Restarting...");
            bot.restart(true, false, e);
            return false;
        }

        if (e instanceof org.openqa.selenium.WebDriverException && e.getMessage().startsWith("chrome not reachable")) {
            // this happens when user manually closes the Chrome window, for example
            BHBotUnity.logger.error("Error: chrome is not reachable! Restarting...", e);
            bot.restart(true, false, e);
            return true;
        } else if (e instanceof java.awt.image.RasterFormatException) {
            // not sure in what cases this happen, but it happens
            BHBotUnity.logger.error("Error: RasterFormatException. Attempting to re-align the window...", e);
            Misc.sleep(500);
            bot.browser.scrollGameIntoView();
            Misc.sleep(500);
            try {
                bot.browser.readScreen();
            } catch (Exception e2) {
                BHBotUnity.logger.error("Error: re-alignment failed(" + e2.getMessage() + "). Restarting...");
                bot.restart(true, false, e);
                return true;
            }
            BHBotUnity.logger.info("Realignment seems to have worked.");
            return true;
        } else if (e instanceof org.openqa.selenium.StaleElementReferenceException) {
            // this is a rare error, however it happens. See this for more info:
            // http://www.seleniumhq.org/exceptions/stale_element_reference.jsp
            BHBotUnity.logger.error("Error: StaleElementReferenceException. Restarting...", e);
            bot.restart(true, false, e);
            return true;
        } else if (e instanceof org.openqa.selenium.TimeoutException) {
            /* When we get time out errors it may be possible that the bot.browser has crashed, so it is impossible to take screenshots
             * For this reason we do a standard restart.
             */
            BHBotUnity.logger.error("Error: Selenium tiemout. Restarting...", e);
            bot.restart(true, false, e);
            return true;
        } else {
            // unknown error!
            BHBotUnity.logger.error("Unmanaged exception in main run loop", e);
            bot.restart(true, false, e);
        }

        bot.scheduler.resetIdleTime(true);

        return false;
    }
}
