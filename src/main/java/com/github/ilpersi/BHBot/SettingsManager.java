package com.github.ilpersi.BHBot;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SettingsManager {
    private final BHBot bot;

    // this variables are used to store the current status of the settings
    private boolean initialized;

    SettingsManager (BHBot bot, boolean skipInitialization) {
        this.bot = bot;

        if (skipInitialization) {
            this.initialized = true;
        }
    }

    void initialize() {
        if (!initialized) {
            BHBot.logger.info("Initializing settings to desired configurations");
            checkBotSettings();
            initialized = true;
        }
    }

    private void checkBotSettings() {
        if (openSettings(Misc.Durations.SECOND)) {
//            Bounds scrolAtTopBounds = Bounds.fromWidthHeight(600, 180, 45, 45);
            final Bounds scrolAtBottomBounds = Bounds.fromWidthHeight(600, 360, 45, 45);
            final Bounds downArrowBounds = Bounds.fromWidthHeight(605, 365, 35, 40);

            final Cue scrolAtBottomCue = new Cue(BHBot.cues.get("ScrollerAtBottom"), scrolAtBottomBounds);
            final Cue downArrowCue = new Cue(BHBot.cues.get("DropDownDown"), downArrowBounds);

            MarvinSegment downArrowSeg = MarvinSegment.fromCue(downArrowCue, Misc.Durations.SECOND, bot.browser);
            if (downArrowSeg == null) {
                BHBot.logger.error("Impossible to find arrow down button in settings manager!");
                closeSettings();
                return;
            }

            // rel is for relative clicks and the x and y coords are relative to the coordinates where cue is found
            // abs is for absolute clicks and the x and y coords are absolute on the screen
            HashMap<String, String> settingConfigs = new HashMap<>();
            settingConfigs.put("settingsMusic", "rel:4;5:1");
            settingConfigs.put("settingsSound", "rel:4;5:1");
            settingConfigs.put("settingsNotification", "rel:20;20:3");
            settingConfigs.put("settingsWBReq", "rel:24;24:6");
            settingConfigs.put("settingsReducedFX", "rel:22;22:8");
            settingConfigs.put("settingsBattleTXT", "rel:23;25:9");
            settingConfigs.put("settingsAnimations", "rel:21;25:10");
            settingConfigs.put("settingsMerchants", "rel:20;22:19");

            // Regular expression to understand how the bot should click on settings based on the previous hashmap
            Pattern clickRegex = Pattern.compile("(?<click>rel|abs):(?<x>\\d+);(?<y>\\d+):(?<barPosition>\\d{1,2})");

            HashSet<String> alreadyFound = new HashSet<>();

            MarvinSegment bottomSeg;
            int menuPos = 1;

            // we search for all the setting in all bar positions (so we don't hard code positions)
            outer:
            do {
                for (Map.Entry<String, String> settingConf : settingConfigs.entrySet()) {
                    // We get cueName and position details
                    String cueName = settingConf.getKey();
                    String clickDetails = settingConf.getValue();

                    // if the two collections have the same size, it means we do not need to search anymore
                    if (alreadyFound.size() == settingConfigs.size()) {
                        BHBot.logger.debug("All the desired settings are configured.");
                        break outer;
                    }

                    // if we found cueName already, we skip
                    if (alreadyFound.contains(cueName)) continue;

                    // we get clicking details
                    Matcher clickMatcher = clickRegex.matcher(clickDetails);
                    if (clickMatcher.find()) {

                        // we extract info from reges groups
                        String clickType = clickMatcher.group("click");
                        int xPos = Integer.parseInt(clickMatcher.group("x"));
                        int yPos = Integer.parseInt(clickMatcher.group("y"));
                        int barPosition = Integer.parseInt(clickMatcher.group("barPosition"));

                        if (menuPos != barPosition) continue;

                        // we search for the cue and if we find it, we click based on the settings
                        MarvinSegment settingSeg = MarvinSegment.fromCue(BHBot.cues.get(cueName), bot.browser);
                        if (settingSeg != null) {
                            int clickX, clickY;
                            if ("rel".equalsIgnoreCase(clickType)) {
                                clickX = settingSeg.x1 + xPos;
                                clickY = settingSeg.y1 + yPos;
                            } else if ("abs".equalsIgnoreCase(clickType)) {
                                clickX = xPos;
                                clickY = yPos;
                            }
                            else {
                                // this should never happen, as it will not match the regex
                                BHBot.logger.warn("Unknown click position logic while searching for settings in SettingManager.");
                                continue;
                            }
                            bot.browser.clickInGame(clickX, clickY);

                            alreadyFound.add(cueName);
                        }

                    } else {
                        BHBot.logger.warn(MessageFormat.format("Wrong setting configuration: {0} -> {1}", cueName, clickDetails));
                    }

                }

                // We do not scroll down anymore if we found all settings already!
                if (alreadyFound.size() == settingConfigs.size()) {
                    BHBot.logger.debug("All the desired settings are configured.");
                    break;
                }

                bot.browser.clickOnSeg(downArrowSeg);
                bottomSeg = MarvinSegment.fromCue(scrolAtBottomCue, Misc.Durations.SECOND / 2, bot.browser);
                bot.browser.readScreen();
                menuPos += 1;
            } while (bottomSeg == null);

            closeSettings();
        } else {
            BHBot.logger.warn("SettingsManger could not open the settings menu!");
        }

    }

    boolean openSettings(@SuppressWarnings("SameParameterValue") int delay) {
        bot.browser.readScreen();

        MarvinSegment seg = MarvinSegment.fromCue(BHBot.cues.get("SettingsGear"), delay, bot.browser);
        if (seg != null) {
            bot.browser.clickOnSeg(seg);
            seg = MarvinSegment.fromCue(BHBot.cues.get("Settings"), delay, bot.browser);
            if (seg == null) bot.saveGameScreen("open-settings-no-setting-menu", "errors");
            return seg != null;
        } else {
            BHBot.logger.error("Impossible to find the settings button!");
            bot.saveGameScreen("open-settings-no-btn", "errors");
            return false;
        }
    }

    boolean closeSettings() {
        return bot.browser.closePopupSecurely(BHBot.cues.get("Settings"), new Cue(BHBot.cues.get("X"), new Bounds(608, 39, 711, 131)));

    }
}
