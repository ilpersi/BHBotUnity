package com.github.ilpersi.BHBot;

import java.util.ArrayList;
import java.util.HashSet;

public class SettingsManager {
    private final BHBotUnity bot;

    // This variable is used to store the current status of the settings
    private boolean initialized;

    // In configuration settings how should the click position be managed?
    enum ClickPosition {
        ABSOLUTE, // Absolute to the screen
        RELATIVE // Relative to a found Cue
    }

    // Record used to manage the desired configuration settings
    record SettingConfiguration(String cueName, ClickPosition clickPosition, int x, int y, int barPosition) {
    }

    SettingsManager(BHBotUnity bot, boolean skipInitialization) {
        this.bot = bot;

        if (skipInitialization) {
            this.initialized = true;
        }
    }

    void initialize() {
        if (!initialized) {
            BHBotUnity.logger.info("Initializing settings to desired configurations");
            checkBotSettings();
            initialized = true;
        }
    }

    /**
     * This method is taking care of setting the
     */
    void checkBotSettings() {
        if (openSettings(Misc.Durations.SECOND)) {

            final Bounds scrollAtBottomBounds = Bounds.fromWidthHeight(605, 355, 35, 45);
            final Bounds downArrowBounds = Bounds.fromWidthHeight(614, 375, 18, 19);
            final Bounds settingsArea = Bounds.fromWidthHeight(162, 162, 449, 259);

            final Cue scrollAtBottomCue = new Cue(BHBotUnity.cues.get("ScrollerAtBottomSettings"), scrollAtBottomBounds);
            final Cue downArrowCue = new Cue(BHBotUnity.cues.get("DropDownDown"), downArrowBounds);

            MarvinSegment downArrowSeg = MarvinSegment.fromCue(downArrowCue, Misc.Durations.SECOND, bot.browser);
            if (downArrowSeg == null) {
                BHBotUnity.logger.error("Impossible to find arrow down button in settings manager!");
                closeSettings();
                return;
            }

            ScrollBarManager settingsSB = new ScrollBarManager(bot.browser);

            // When Kong modifies/adds new settings, review this arraylist
            ArrayList<SettingConfiguration> settingConfigurations = new ArrayList<>();
            settingConfigurations.add(new SettingConfiguration("settingsMusic", ClickPosition.RELATIVE, 4, 5, 1));
            settingConfigurations.add(new SettingConfiguration("settingsSound", ClickPosition.RELATIVE, 4, 5, 1));
            settingConfigurations.add(new SettingConfiguration("settingsNotification", ClickPosition.RELATIVE, 20, 20, 3));
            settingConfigurations.add(new SettingConfiguration("settingsWBReq", ClickPosition.RELATIVE, 24, 24, 7));
            settingConfigurations.add(new SettingConfiguration("settingsReducedFX", ClickPosition.RELATIVE, 22, 22, 10));
            settingConfigurations.add(new SettingConfiguration("settingsBattleTXT", ClickPosition.RELATIVE, 22, 24, 10));
            settingConfigurations.add(new SettingConfiguration("settingsAnimations", ClickPosition.RELATIVE, 22, 21, 11));
            settingConfigurations.add(new SettingConfiguration("settingsMerchants", ClickPosition.RELATIVE, 20, 22, 21));
            settingConfigurations.add(new SettingConfiguration("settingsTips", ClickPosition.RELATIVE, 19, 21, 23));

            HashSet<String> alreadyFound = new HashSet<>();

            MarvinSegment bottomSeg;
            int menuPos = 1;

            // we search for all the setting in all bar positions (so we don't hard code positions)
            outer:
            do {
                if (menuPos > 1)
                    settingsSB.scrollDown(100);

                for (SettingConfiguration setting : settingConfigurations) {
                    // We get cueName and position details

                    // if the two collections have the same size, it means we do not need to search anymore
                    if (alreadyFound.size() == settingConfigurations.size()) {
                        BHBotUnity.logger.debug("All the desired settings are configured.");
                        break outer;
                    }

                    // if we found cueName already, we skip
                    if (alreadyFound.contains(setting.cueName)) continue;

                    // if we expect the setting on a different bar position, we skp
                    if (menuPos != setting.barPosition) continue;

                    // We make sure to search for the cue in the right screen area
                    Cue settingCue = new Cue(BHBotUnity.cues.get(setting.cueName), settingsArea);

                    // we search for the cue and if we find it, we click based on the settings
                    MarvinSegment settingSeg = MarvinSegment.fromCue(settingCue, Misc.Durations.SECOND, bot.browser);
                    if (settingSeg != null) {
                        int clickX, clickY;
                        if (ClickPosition.RELATIVE.equals(setting.clickPosition)) {
                            clickX = settingSeg.x1 + setting.x;
                            clickY = settingSeg.y1 + setting.y;
                        } else if (ClickPosition.ABSOLUTE.equals(setting.clickPosition)) {
                            clickX = setting.x;
                            clickY = setting.y;
                        } else {
                            // this should never happen, as the enum has only two options
                            BHBotUnity.logger.warn("Unknown click position logic while searching for settings in SettingManager.");
                            continue;
                        }

                        bot.browser.clickInGame(clickX, clickY);
                        // As there may be additional settings in the same page, we make sure to refresh the image
                        bot.browser.readScreen(Misc.Durations.SECOND / 2);

                        alreadyFound.add(setting.cueName);
                    } else {
                        BHBotUnity.logger.debug(String.format("Impossible to find %s at bar position %d", setting.cueName, setting.barPosition));
                    }
                }

                // We do not scroll down anymore if we found all settings already!
                if (alreadyFound.size() == settingConfigurations.size()) {
                    BHBotUnity.logger.debug("All the desired settings are configured.");
                    break;
                }

                menuPos += 1;
            } while (!settingsSB.isAtBottom());

            closeSettings();
        } else {
            BHBotUnity.logger.warn("SettingsManger could not open the settings menu!");
        }

    }

    /**
     * This method will take care of opening the settings menu. This method assumes that no other window is currently
     * opened and the character main screen is clean.
     *
     * @param delay How much time before we time out?
     * @return true if the settings menu was correctly opened, false otherwise.
     */
    boolean openSettings(@SuppressWarnings("SameParameterValue") int delay) {
        bot.browser.readScreen();

        MarvinSegment seg = MarvinSegment.fromCue(BHBotUnity.cues.get("SettingsGear"), delay, bot.browser);
        if (seg != null) {
            bot.browser.clickOnSeg(seg);
            seg = MarvinSegment.fromCue(BHBotUnity.cues.get("Settings"), delay, bot.browser);
            if (seg == null) {
                bot.saveGameScreen("open-settings-no-setting-menu", "errors");
            } else {
                // Let's wait a bit more for the setting menu to stay in the correct position.
                // Sometimes it bounces and this leads to errors
                MarvinSegment.fromCue(BHBotUnity.cues.get("Settings"), delay, bot.browser);
            }

            return seg != null;
        } else {
            BHBotUnity.logger.error("Impossible to find the settings button!");
            bot.saveGameScreen("open-settings-no-btn", "errors");
            return false;
        }
    }

    /**
     * This method will take cre of closing the settings menu.
     *
     * @return true if settings menu was correctly closed, false otherwise.
     */
    boolean closeSettings() {
        return bot.browser.closePopupSecurely(BHBotUnity.cues.get("Settings"), new Cue(BHBotUnity.cues.get("X"), new Bounds(608, 39, 711, 131)));
    }
}
