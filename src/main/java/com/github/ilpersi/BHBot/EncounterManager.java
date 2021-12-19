package com.github.ilpersi.BHBot;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EncounterManager {
    private final BHBot bot;
    static HashMap<String, FamiliarDetails> famMD5Table = new HashMap<>();

    public enum FamiliarType {
        ERROR("Error", 0),
        COMMON("Common", 1),
        RARE("Rare", 2),
        EPIC("Epic", 3),
        LEGENDARY("Legendary", 4);

        private final String name;
        private final int type;

        FamiliarType(String name, int type) {
            this.name = name;
            this.type = type;
        }

        public int getValue() {
            return this.type;
        }

        public String toString() {
            return this.name;
        }

    }

    public enum PersuationType {
        DECLINE("Decline"),
        PERSUADE("Persuasion"),
        BRIBE("Bribe");

        private final String name;

        PersuationType(String name) {
            this.name = name;
        }

        public String toString() {
            return this.name;
        }

    }

    static class BribeSettings {
        String familiarName;
        int toBribeCnt;

        BribeSettings() {
            this.familiarName = "";
            this.toBribeCnt = 0;
        }
    }

    static class FamiliarDetails {
        String name;
        FamiliarType type;

        FamiliarDetails(String familiarName, FamiliarType familiarType) {
            this.name = familiarName;
            this.type = familiarType;
        }
    }

    EncounterManager(BHBot bot) {
        this.bot = bot;
    }

    void processFamiliarEncounter() {
        MarvinSegment seg;

        BHBot.logger.autobribe("Familiar encountered");

        FamiliarType familiarLevel;
        if (MarvinSegment.fromCue(BHBot.cues.get("CommonFamiliar"), bot.browser) != null) {
            familiarLevel = FamiliarType.COMMON;
        } else if (MarvinSegment.fromCue(BHBot.cues.get("RareFamiliar"), bot.browser) != null) {
            familiarLevel = FamiliarType.RARE;
        } else if (MarvinSegment.fromCue(BHBot.cues.get("EpicFamiliar"), bot.browser) != null) {
            familiarLevel = FamiliarType.EPIC;
        } else if (MarvinSegment.fromCue(BHBot.cues.get("LegendaryFamiliar"), bot.browser) != null) {
            familiarLevel = FamiliarType.LEGENDARY;
        } else {
            familiarLevel = FamiliarType.ERROR; // error
        }

        PersuationType persuasion;
        BribeSettings bribeInfo = new BribeSettings();

        // Checking familiars setting takes time and a lot of cues verifications. We try to minimize the number of times
        // this is done
        boolean skipBribeNames = (bot.settings.bribeLevel > 0 && familiarLevel.getValue() >= bot.settings.bribeLevel) ||
                (bot.settings.familiars.size() == 0);

        if (!skipBribeNames) {
            bribeInfo = verifyBribeNames();
        }

        if ((bot.settings.bribeLevel > 0 && familiarLevel.getValue() >= bot.settings.bribeLevel) ||
                bribeInfo.toBribeCnt > 0) {
            persuasion = PersuationType.BRIBE;
        } else if ((bot.settings.persuasionLevel > 0 && familiarLevel.getValue() >= bot.settings.persuasionLevel)) {
            persuasion = PersuationType.PERSUADE;
        } else {
            persuasion = PersuationType.DECLINE;
        }

        // If we're set to bribe and we don't have gems, we default to PERSUASION
        if (persuasion == PersuationType.BRIBE && bot.noGemsToBribe) {
            persuasion = PersuationType.PERSUADE;
        }

        StringBuilder persuasionLog = new StringBuilder("familiar-");
        persuasionLog.append(familiarLevel.toString().toUpperCase()).append("-");
        persuasionLog.append(persuasion.toString().toUpperCase()).append("-");
        persuasionLog.append("attempt");

        // We save all the errors and persuasions based on settings
        if ((familiarLevel.getValue() >= bot.settings.familiarScreenshot) || familiarLevel == FamiliarType.ERROR) {
            Misc.saveScreen(persuasionLog.toString(), "familiars", BHBot.includeMachineNameInScreenshots, bot.browser.getImg());
        }

        // if (bot.settings.contributeFamiliars) {

        // We build the MD5 string for the current encounter
        Bounds familiarNameBounds = new Bounds(105, 60, 640, 105);
        BufferedImage famNameImg = EncounterManager.getFamiliarNameImg(bot.browser.getImg(), familiarLevel, familiarNameBounds);
        String famNameMD5 = Misc.imgToMD5(famNameImg);

        // We check if the familiar is known
        FamiliarDetails encounterDetails = EncounterManager.famMD5Table.getOrDefault(famNameMD5, null);
        if (encounterDetails == null) {
            // String unkMD5 = bot.saveGameScreen(familiarLevel.toString() + "-unknown-familiar", "unknown-familiars", famNameImg);
            // BHBot.logger.debug("MD5 familiar unknown: '" + famNameMD5 + "' saved as " + unkMD5);
            // we contribute unknown familiars

            // If we could not get the name, we are going to upload the full screen image.
            if (famNameImg == null) famNameImg = bot.browser.getImg();

            if (!Misc.contributeImage(famNameImg, persuasionLog.toString(), null, bot.settings.useUnityEngine)) {
                Misc.contributeImage(bot.browser.getImg(), persuasionLog.toString(), familiarNameBounds, bot.settings.useUnityEngine);
            }
        } else {
            BHBot.logger.debug(MessageFormat.format("MD5 familiar detected: {0}", encounterDetails.name));
        }
        // }

        // We attempt persuasion or bribe based on settings
        if (persuasion == PersuationType.BRIBE) {
            if (!bribeFamiliar()) {
                BHBot.logger.autobribe("Bribe attempt failed! Trying with persuasion...");
                if (persuadeFamiliar()) {
                    BHBot.logger.autobribe(MessageFormat.format("{0} persuasion attempted.", familiarLevel.toString().toUpperCase()));
                } else {
                    BHBot.logger.error("Impossible to persuade familiar, restarting...");
                    bot.restart(true, false);
                }
            } else {
                updateFamiliarCounter(bribeInfo.familiarName.toUpperCase());
            }
        } else if (persuasion == PersuationType.PERSUADE) {
            if (persuadeFamiliar()) {
                BHBot.logger.autobribe(MessageFormat.format("{0} persuasion attempted.", familiarLevel.toString().toUpperCase()));
            } else {
                BHBot.logger.error("Impossible to attempt persuasion, restarting.");
                bot.restart(true, false);
            }
        } else {
            seg = MarvinSegment.fromCue(BHBot.cues.get("DeclineRed"), 0, Bounds.fromWidthHeight(205, 420, 200, 65), bot.browser);
            if (seg != null) {
                bot.browser.closePopupSecurely(BHBot.cues.get("FamiliarEncounter"), BHBot.cues.get("DeclineRed"));

                Cue yesGreen = new Cue(BHBot.cues.get("YesGreen"), Bounds.fromWidthHeight(290, 330, 85, 60));
                if (bot.browser.closePopupSecurely(yesGreen, yesGreen)) {
                    BHBot.logger.autobribe(MessageFormat.format("{0} persuasion declined.", familiarLevel.toString().toUpperCase()));
                } else {
                    BHBot.logger.error("Impossible to find the yes-green button after decline, restarting...");
                    bot.restart(true, false);
                }
            } else {
                BHBot.logger.error("Impossible to find the decline button, restarting...");
                bot.restart(true, false);
            }
        }
    }

    /**
     * Will verify if in the current persuasion screen one of the bribeNames is present
     */
    private BribeSettings verifyBribeNames() {

        List<String> wrongNames = new ArrayList<>();
        BribeSettings result = new BribeSettings();
        String familiarName;
        int toBribeCnt;

        for (String familiarDetails : bot.settings.familiars) {
            // familiar details from settings
            String[] details = familiarDetails.toLowerCase().split(" ");
            familiarName = details[0];
            toBribeCnt = Integer.parseInt(details[1]);

            Cue familiarCue = BHBot.cues.getOrNull(familiarName);

            if (familiarCue != null) {
                if (toBribeCnt > 0) {
                    // The familiar screen is opened, we we search for cues without timeout
                    if (MarvinSegment.fromCue(familiarCue, bot.browser) != null) {
                        BHBot.logger.autobribe(MessageFormat.format("Detected familiar {0} as valid in familiars", familiarDetails));
                        result.toBribeCnt = toBribeCnt;
                        result.familiarName = familiarName;
                        break;

                    }

                } else {
                    BHBot.logger.warn(MessageFormat.format("Count for familiar {0} is 0! Temporary removing it form the settings...", familiarName));
                    wrongNames.add(familiarDetails);
                }
            } else {
                BHBot.logger.warn(MessageFormat.format("Impossible to find a cue for familiar {0}, it will be temporary removed from settings.", familiarName));
                wrongNames.add(familiarDetails);
            }
        }

        // If there is any error we update the settings
        for (String wrongName : wrongNames) {
            bot.settings.familiars.remove(wrongName);
        }

        return result;
    }

    /**
     * Bot will attempt to bribe the current encountered familiar.
     * Encounter window must be opened for this to work correctly
     *
     * @return true if bribe attempt is correctly performed, false otherwise
     */
    private boolean bribeFamiliar() {
        MarvinSegment seg = MarvinSegment.fromCue(BHBot.cues.get("Bribe"), bot.browser);
        BufferedImage tmpScreen = bot.browser.getImg();

        if (seg != null) {
            bot.browser.clickOnSeg(seg);

            // TODO Add Bounds for YesGreen
            seg = MarvinSegment.fromCue(BHBot.cues.get("YesGreen"), Misc.Durations.SECOND * 7, bot.browser);
            if (seg != null) {
                bot.browser.clickOnSeg(seg);
            } else {
                BHBot.logger.error("Impossible to find YesGreen in bribeFamiliar");
                return false;
            }

            // TODO Add Bounds for NotEnoughGems
            if (MarvinSegment.fromCue(BHBot.cues.get("NotEnoughGems"), Misc.Durations.SECOND * 5, bot.browser) != null) {
                BHBot.logger.warn("Not enough gems to attempt a bribe!");
                bot.noGemsToBribe = true;
                if (!bot.browser.closePopupSecurely(BHBot.cues.get("NotEnoughGems"), BHBot.cues.get("No"))) {
                    BHBot.logger.error("Impossible to close the Not Enough gems pop-up. Restarting...");
                    bot.restart(true, false);
                }
                return false;
            }
            bot.notificationManager.sendBribeNotification(tmpScreen);
            return true;
        }

        return false;
    }

    /**
     * Bot will attempt to persuade the current encountered familiar.
     * For this to work, the familiar window must be opened!
     *
     * @return true if persuasion is successfully performed, false otherwise
     */
    private boolean persuadeFamiliar() {

        MarvinSegment seg;
        seg = MarvinSegment.fromCue(BHBot.cues.get("Persuade"), bot.browser);
        if (seg != null) {

            bot.browser.clickOnSeg(seg); // seg = detectCue(cues.get("Persuade"))

            seg = MarvinSegment.fromCue(BHBot.cues.get("YesGreen"), Misc.Durations.SECOND * 5, Bounds.fromWidthHeight(245, 330, 165, 65), bot.browser);
            if (seg != null) {
                bot.browser.clickOnSeg(seg);
                return true;
            } else {
                BHBot.logger.error("Impossible to find the YesGreen button in persuadeFamiliar");
            }
        }
        return false;
    }

    private void updateFamiliarCounter(String fam) {
        String familiarToUpdate = "";
        String updatedFamiliar = "";

        for (String fa : bot.settings.familiars) { //cycle through array
            String fString = fa.toUpperCase().split(" ")[0]; //case sensitive for a match so convert to upper case
            int currentCounter = Integer.parseInt(fa.split(" ")[1]); //set the bribe counter to an int
            if (fam.equals(fString)) { //when match is found from the function
                familiarToUpdate = fa; //write current status to String
                currentCounter--; // decrease the counter
                updatedFamiliar = (fString.toLowerCase() + " " + currentCounter); //update new string with familiar name and decrease counter
            }
        }

        try {
            // input the file content to the StringBuffer "input"
            BufferedReader file = new BufferedReader(new FileReader("settings.ini"));
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
            if (inputStr.contains(familiarToUpdate)) {
                inputStr = inputStr.replace(familiarToUpdate, updatedFamiliar);
            }

            // write the string from memory over the existing file
            FileOutputStream fileOut = new FileOutputStream("settings.ini");
            fileOut.write(inputStr.getBytes());
            fileOut.close();

            bot.settings.load();  //reload the new settings file so the counter will be updated for the next bribe

        } catch (Exception e) {
            System.out.println("Problem writing to settings file");
        }
    }

    /**
     * This methods extract an image only containing the familiar name. The logic is based on the type of the familiar.
     * Once that the type is known, the name will be extracted using a specific value for the color
     *
     * @param screenImg      A Buffered Image containing the image
     * @param familiarType   What is the type of the familiar we are looking to find the name
     * @return A Buffered Image containing just the familiar name
     */
    static BufferedImage getFamiliarNameImg(BufferedImage screenImg, FamiliarType familiarType, Bounds nameBounds) {
        // int familiarTxtColor;
        Color familiarTxtCol;
        switch (familiarType) {
            case COMMON:
                familiarTxtCol = new Color(151, 255, 125);
                // familiarTxtColor = -6881668;
                break;
            case RARE:
                familiarTxtCol = new Color(147, 158, 244);
                // familiarTxtColor = -7168525;
                break;
            case EPIC:
                familiarTxtCol = new Color(255, 128, 125);
                // familiarTxtColor = -98436;
                break;
            case LEGENDARY:
                familiarTxtCol = new Color(255, 255, 0);
                // familiarTxtColor = -66048;
                break;
            case ERROR:
            default:
                familiarTxtCol = null;
                // familiarTxtColor = 0;
                break;
        }

        // if (familiarTxtColor == 0 ) return null;
        if (familiarTxtCol == null) return null;


        BufferedImage nameImgRect;
        if (nameBounds != null)
            nameImgRect = screenImg.getSubimage(nameBounds.x1, nameBounds.y1, nameBounds.width, nameBounds.height);
        else
            nameImgRect = screenImg;

        int minX = nameImgRect.getWidth();
        int minY = nameImgRect.getHeight();
        int maxY = 0;
        int maxX = 0;

        int[][] pixelMatrix = Misc.convertTo2D(nameImgRect);
        for (int y = 0; y < nameImgRect.getHeight(); y++) {
            for (int x = 0; x < nameImgRect.getWidth(); x++) {
                // if (pixelMatrix[x][y] == familiarTxtColor) {
                if (new Color(pixelMatrix[x][y]).equals(familiarTxtCol)) {
                    if (y < minY) minY = y;
                    if (x < minX) minX = x;
                    if (y > maxY) maxY = y;
                    if (x > maxX) maxX = x;
                } else {
                    nameImgRect.setRGB(x, y, 0);
                }
            }

        }

        // pixel comparison is 0 based while image size i 1 based
        int width = maxX > 0 ? maxX - minX + 1 : 0;
        int height = maxY > 0 ? maxY - minY + 1 : 0;

        BufferedImage result;
        try {
            result = nameImgRect.getSubimage(minX, minY, width, height);
        } catch (Exception e) {
            result = null;
        }

        return result;
    }

    /**
     * This will build the full list of MD5 for all the known familiars. This list will be used to manage bribing and
     * persuasions during encounters.
     */
    static void buildMD5() {

        //region Familiars MD5 Values
        //region COMMON familiars
        EncounterManager.famMD5Table.put("UNPCHOxJynGzDrS2PbfO4Q==", new FamiliarDetails("Batty", FamiliarType.COMMON));
        EncounterManager.famMD5Table.put("4QLlgvnJ8c6Hi79o09SXEA==", new FamiliarDetails("Booboo", FamiliarType.COMMON));
        EncounterManager.famMD5Table.put("80W8hPDRABjAn4y/au7XAg==", new FamiliarDetails("Candelabors", FamiliarType.COMMON));
        EncounterManager.famMD5Table.put("DUNhKhOqiFQgezxe/oAGfQ==", new FamiliarDetails("Candelabros", FamiliarType.COMMON));
        EncounterManager.famMD5Table.put("UJqZrK1NwzmYFu3Enyvngw==", new FamiliarDetails("Candelabros", FamiliarType.COMMON));
        EncounterManager.famMD5Table.put("ZTL9uESJonec/1JHbqTrkw==", new FamiliarDetails("Candelabros", FamiliarType.COMMON));
        EncounterManager.famMD5Table.put("gsJMhLJUOQIQxPw8Gr6LGQ==", new FamiliarDetails("Candelabros", FamiliarType.COMMON));
        EncounterManager.famMD5Table.put("tayeja2C4+0CO8GYK3uFPg==", new FamiliarDetails("Candelabros", FamiliarType.COMMON));
        EncounterManager.famMD5Table.put("kQbnDNT+WQ5WG0N412+CYQ==", new FamiliarDetails("Gak", FamiliarType.COMMON));
        EncounterManager.famMD5Table.put("ZLlgL/yqgmemx4eL03QNFQ==", new FamiliarDetails("Gak", FamiliarType.COMMON));
        EncounterManager.famMD5Table.put("43CT1fQvk6K5pIyJJox5cQ==", new FamiliarDetails("ProfOak", FamiliarType.COMMON));
        EncounterManager.famMD5Table.put("xkGTdGo7Ne8I+Cv0q72h6A==", new FamiliarDetails("Stumpie", FamiliarType.COMMON));
        EncounterManager.famMD5Table.put("vMWrwkdHhpm2ITVJe62uGQ==", new FamiliarDetails("Sugg", FamiliarType.COMMON));
        EncounterManager.famMD5Table.put("mIXAzButHqxTteoZpRsPGQ==", new FamiliarDetails("Sugg", FamiliarType.COMMON));
        EncounterManager.famMD5Table.put("MCrv+ZZAH+UqslKeF//fVA==", new FamiliarDetails("Terra", FamiliarType.COMMON));
        EncounterManager.famMD5Table.put("uU9W2WbL3/jXdEoLkE3R+Q==", new FamiliarDetails("Terra", FamiliarType.COMMON));
        EncounterManager.famMD5Table.put("OyDlQ/0AwFrAK+Mg8tAOJQ==", new FamiliarDetails("Tubbo", FamiliarType.COMMON));
        EncounterManager.famMD5Table.put("qqE+XKOmcjQawhMNpytVgg==", new FamiliarDetails("Uggs", FamiliarType.COMMON));
        EncounterManager.famMD5Table.put("MHskvmKyjvIgcsoU6EGVxg==", new FamiliarDetails("Uggs", FamiliarType.COMMON));
        //endregion

        //region RARE familiars
        EncounterManager.famMD5Table.put("JUt00pm9AwcvyqLqfwzbUw==", new FamiliarDetails("Bob", FamiliarType.RARE));
        EncounterManager.famMD5Table.put("39U/7JaT1Z5x0pd+FzbEFQ==", new FamiliarDetails("Chewy", FamiliarType.RARE));
        EncounterManager.famMD5Table.put("MB3W6wapyV3mO49iDdJPfw==", new FamiliarDetails("Findle", FamiliarType.RARE));
        EncounterManager.famMD5Table.put("hoB3qmpDjEEB0hBy+rwz8g==", new FamiliarDetails("Findle", FamiliarType.RARE));
        EncounterManager.famMD5Table.put("DJGsWnnnxVHETufD0eIgBg==", new FamiliarDetails("Findle", FamiliarType.RARE));
        EncounterManager.famMD5Table.put("fgzFQqY5npamn4sJpX9qUQ==", new FamiliarDetails("Grampz", FamiliarType.RARE));
        EncounterManager.famMD5Table.put("DjjvE7I59OrRj3MBIwMtUw==", new FamiliarDetails("Grampz", FamiliarType.RARE));
        EncounterManager.famMD5Table.put("Rww5h5df+e7o6Sv8rLDWBQ==", new FamiliarDetails("Marm", FamiliarType.RARE));
        EncounterManager.famMD5Table.put("8yYWadHRp5IsKm77wVbUJw==", new FamiliarDetails("Marm", FamiliarType.RARE));
        EncounterManager.famMD5Table.put("tx8GZ0uVHcQa952MbWJptw==", new FamiliarDetails("Marm", FamiliarType.RARE));
        EncounterManager.famMD5Table.put("yCtNI92TX05nLb2YC/9ZIA==", new FamiliarDetails("Marm", FamiliarType.RARE));
        EncounterManager.famMD5Table.put("C7dwGQo8jCNHskBzJJf53Q==", new FamiliarDetails("Opo", FamiliarType.RARE));
        EncounterManager.famMD5Table.put("pbhAbGekMoIvtGVZ6MjEGQ==", new FamiliarDetails("Roy", FamiliarType.RARE));
        EncounterManager.famMD5Table.put("TXKBBLJjAS0wPRHUMvmFuw==", new FamiliarDetails("Shrump", FamiliarType.RARE));
        //endregion

        //region EPIC familiars
        EncounterManager.famMD5Table.put("85S94YptyStkGch3cJu6cw==", new FamiliarDetails("Ahlpuch", FamiliarType.EPIC));
        EncounterManager.famMD5Table.put("+VDI0b+EoGCyyJiABpxt6A==", new FamiliarDetails("Blemb", FamiliarType.EPIC));
        EncounterManager.famMD5Table.put("wj/v0X2ZV8L2F3VIAu4Deg==", new FamiliarDetails("Blubber", FamiliarType.EPIC));
        EncounterManager.famMD5Table.put("EgLFmoFWj16J/Ycx2nFKwA==", new FamiliarDetails("Driffin", FamiliarType.EPIC));
        EncounterManager.famMD5Table.put("29mjb3Ov6JP2jvivqSKBTg==", new FamiliarDetails("Ferumar", FamiliarType.EPIC));
        EncounterManager.famMD5Table.put("OUtCwnWIkr/wOBbAAaKbLw==", new FamiliarDetails("Ferumar", FamiliarType.EPIC));
        EncounterManager.famMD5Table.put("UPQAH/bYXdEEpVdzuJtRQA==", new FamiliarDetails("Hanfarin", FamiliarType.EPIC));
        EncounterManager.famMD5Table.put("PVnNNMnMWKfuUhYkNS4WqQ==", new FamiliarDetails("Hanfarin", FamiliarType.EPIC));
        EncounterManager.famMD5Table.put("266sERj7qahFQ9qNpYMgCA==", new FamiliarDetails("Ioti", FamiliarType.EPIC));
        EncounterManager.famMD5Table.put("1TCVHeYpIVX7J/zaYvxR0g==", new FamiliarDetails("J3-17", FamiliarType.EPIC));
        EncounterManager.famMD5Table.put("KgW1thZ28vh7zcA1+68nfQ==", new FamiliarDetails("Krackers", FamiliarType.EPIC));
        EncounterManager.famMD5Table.put("BrjMMli0nsrVH6QW7pJiBA==", new FamiliarDetails("Krackers", FamiliarType.EPIC));
        EncounterManager.famMD5Table.put("bcGEVDy79nBcVk26nitM7w==", new FamiliarDetails("Neistall", FamiliarType.EPIC));
        EncounterManager.famMD5Table.put("hyTPJaC9xhfDieiz8pY50g==", new FamiliarDetails("Neistall", FamiliarType.EPIC));
        EncounterManager.famMD5Table.put("jaicWubS+gA5KDJHsxPHGQ==", new FamiliarDetails("Neistall", FamiliarType.EPIC));
        EncounterManager.famMD5Table.put("Vv6/Cyuyo8PdNKthFvvmNg==", new FamiliarDetails("Oevor", FamiliarType.EPIC));
        EncounterManager.famMD5Table.put("NOUFs1j5cnbMCMsYXkiBsg==", new FamiliarDetails("Rexie", FamiliarType.EPIC));
        EncounterManager.famMD5Table.put("KnIJ6EDLzKLFJOKEqzguWA==", new FamiliarDetails("Robomax-6000", FamiliarType.EPIC));
        EncounterManager.famMD5Table.put("7/5ivixnO8plz2MuEBNffA==", new FamiliarDetails("Shade", FamiliarType.EPIC));
        EncounterManager.famMD5Table.put("N8cP0P5PiKiyl6H+ZdtZlA==", new FamiliarDetails("Squib", FamiliarType.EPIC));
        EncounterManager.famMD5Table.put("zgK2KCaQiV4x72EwXx1d8g==", new FamiliarDetails("Tecoatl", FamiliarType.EPIC));
        EncounterManager.famMD5Table.put("KGTSijma925/zduJ1JsADg==", new FamiliarDetails("Tecoatl", FamiliarType.EPIC));
        EncounterManager.famMD5Table.put("8S62YdT81N04H71JUbS9Fw==", new FamiliarDetails("TheTriology", FamiliarType.EPIC));
        EncounterManager.famMD5Table.put("UkqkqMPXNrIrkmIIa1uuAw==", new FamiliarDetails("TheTriology", FamiliarType.EPIC));
        EncounterManager.famMD5Table.put("lj+l9bUYHn6MCb76q8pVAg==", new FamiliarDetails("Violace", FamiliarType.EPIC));
        EncounterManager.famMD5Table.put("SBynK4RkNSIIBvK0EdfGPQ==", new FamiliarDetails("Violace", FamiliarType.EPIC));
        EncounterManager.famMD5Table.put("x1B19CH026b+aVgx+VwS2Q==", new FamiliarDetails("Violace", FamiliarType.EPIC));
        //endregion

        //region LEGENDARY familiars
        EncounterManager.famMD5Table.put("WMfqJ7gWJwmZto6GKIbiQA==", new FamiliarDetails("Abernario", FamiliarType.LEGENDARY));
        EncounterManager.famMD5Table.put("60MCR0T/pKRXJnqQzNRG1g==", new FamiliarDetails("Astarot", FamiliarType.LEGENDARY));
        EncounterManager.famMD5Table.put("4TdkLS0eWWSTwa1tD8Bp7Q==", new FamiliarDetails("Astarot", FamiliarType.LEGENDARY));
        EncounterManager.famMD5Table.put("i90mq777qYo7MT6NmG1GVA==", new FamiliarDetails("Gobby", FamiliarType.LEGENDARY));
        EncounterManager.famMD5Table.put("IYuGa2ky9DEyXsRD9iq3/w==", new FamiliarDetails("Gobby", FamiliarType.LEGENDARY));
        EncounterManager.famMD5Table.put("F/NM+ZWXFtsA7nP4fLtEhg==", new FamiliarDetails("Kakunapac", FamiliarType.LEGENDARY));
        EncounterManager.famMD5Table.put("QT3sBIYLjF324QE9LzlHDA==", new FamiliarDetails("Neistall", FamiliarType.LEGENDARY));
        //endregion

        //endregion

        BHBot.logger.debug(MessageFormat.format("Loaded {0} familiars MD5 hashes.", EncounterManager.famMD5Table.size()));
    }

    /**
     * Print the full list of MD5 hashes
     */
    static void printMD5() {
        for (Map.Entry<String, EncounterManager.FamiliarDetails> famDetails : EncounterManager.famMD5Table.entrySet()) {
            BHBot.logger.debug(MessageFormat.format("MD5 ''{0}'' - > {1}", famDetails.getKey(), famDetails.getValue().name));
        }
    }

    /**
     * This method will search for famName in the MD5 hashmap and print the MD5 hash if found
     *
     * @param famName The name of the desired familiar
     */
    static void printMD5(String famName) {
        for (Map.Entry<String, EncounterManager.FamiliarDetails> famDetails : EncounterManager.famMD5Table.entrySet()) {
            if (famName.equalsIgnoreCase(famDetails.getValue().name)) {
                BHBot.logger.debug(MessageFormat.format("MD5 ''{0}'' - > {1}", famDetails.getKey(), famDetails.getValue().name));
                return;
            }
        }
        BHBot.logger.warn(MessageFormat.format("Familiar name not found: {0}", famName));
    }
}
