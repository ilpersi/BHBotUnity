package com.github.ilpersi.BHBot;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.List;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * This is a quick and dirty solution aimed at creating multiple Cues from an origin image file.
 * The goal of this class is to be on par with Kongregate changing Cues frequently
 */
public class CueBuilder {
    public static class ImageFilter implements FilenameFilter {

        private final Pattern PNGPattern;

        public ImageFilter(String PNGPattern) {
            if (!PNGPattern.endsWith("\\.png")) PNGPattern += "\\.png";

            this.PNGPattern = Pattern.compile(PNGPattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        }

        @Override
        public boolean accept(File dir, String name) {
            return this.PNGPattern.matcher(name).matches();
        }

    }

    /**
     * Internal class that has all the details of a Cue that needs to be created
     */
    static class CueLocator {

        /**
         * The Path containing the screenshot to be used to extract the Cue
         */
        String containingScreenShotPath;
        /**
         * The Bounds of the extracted Cue in the screenshot
         */
        Bounds cuePosition;
        /**
         * If a whitelist is present, only the pixel with a color in the list will be part of the extracted Cue Image
         */
        Set<Color> colorWhiteList;
        /**
         * If a transparent regions are present, the corresponding areas will be made transparent when loading the cue
         */
        Set<Bounds> transparentRegions;
        /**
         * The internal name of the destination Cue
         */
        String destinationCueName;
        /**
         * The path of the destination Cue
         */
        String destinationCuePath;
        /**
         * Will the new cue be merged to the existing one?
         */
        boolean merge;
        /**
         * Cue short description
         */
        String cueDescription;

        public CueLocator(String containingScreenShotPath, Bounds cuePosition, Set<Color> colorWhiteList,
                          Set<Bounds> transparentRegions, String destinationCueName,
                          String destinationCuePath, boolean merge, String cueDescription) {
            this.containingScreenShotPath = containingScreenShotPath;
            this.cuePosition = cuePosition;
            this.colorWhiteList = colorWhiteList;
            this.transparentRegions = transparentRegions;
            this.destinationCueName = destinationCueName;
            this.destinationCuePath = destinationCuePath;
            this.merge = merge;
            this.cueDescription = cueDescription;
        }

        /**
         * This method will take care of extracting the Cue from the screenshot and save it to the destination path
         */
        void generateCue() {
            // File that contains the Cue to be extracted
            File screenShotFile = new File(this.containingScreenShotPath);
            if (!screenShotFile.exists() || screenShotFile.isDirectory()) {
                System.out.println("Origin screenshot is not a file or it does not exist: " + screenShotFile.getAbsolutePath());
                return;
            } else if (!screenShotFile.getName().toLowerCase().endsWith(".png")) {
                System.out.println("Origin screenshot is not a PNG image: " + screenShotFile.getAbsolutePath());
                return;
            }

            // We load the original screenshot
            BufferedImage containingScreenshotImg;
            try {
                containingScreenshotImg = ImageIO.read(screenShotFile);
            } catch (IOException e) {
                System.out.println("Error while loading origin screenshot image");
                e.printStackTrace();
                return;
            }

            // Destination Cue File with checks
            File destinationCueFile = new File("src/main/resources/" + this.destinationCuePath);
            if (destinationCueFile.isDirectory()) {
                System.out.println("Destination Cue file path is a directory: " + destinationCueFile.getAbsolutePath());
                return;
            }

            // We make sure that the target directory for the Cue exists and if it does not exist, we create it.
            File destinationCueDirectory = new File(destinationCueFile.getAbsoluteFile().getParent());
            if (!destinationCueDirectory.exists()) {
                if (!destinationCueDirectory.mkdirs()) {
                    System.out.println("It was impossible to create Cue destination directory: " + destinationCueDirectory.getAbsolutePath());
                    return;
                }
            }

            // If transparent regions are present, we make sure they are transparent
            if (this.transparentRegions.size() > 0) {
                for (Bounds transaprentRegion : this.transparentRegions) {

                    for (int y = 0; y < containingScreenshotImg.getHeight(); y++) {
                        for (int x = 0; x < containingScreenshotImg.getWidth(); x++) {
                            if (x >= transaprentRegion.x1 && x <= transaprentRegion.x2
                                && y >= transaprentRegion.y1 && y <= transaprentRegion.y2) {
                                containingScreenshotImg.setRGB(x, y, 0);
                            }
                        }

                    }
                }
            }

            // We create the destination Cue
            BufferedImage destCueImg;
            if (this.cuePosition != null)
                destCueImg = containingScreenshotImg.getSubimage(this.cuePosition.x1, this.cuePosition.y1, this.cuePosition.width, this.cuePosition.height);
            else {
                // We clone the original image
                ColorModel cm = containingScreenshotImg.getColorModel();
                boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
                WritableRaster raster = containingScreenshotImg.copyData(null);
                destCueImg = new BufferedImage(cm, raster, isAlphaPremultiplied, null);
            }

            // If a white list is specified we only save the white listed pixel colors and we set the others as transparent
            if (this.colorWhiteList.size() > 0) {
                int minX = destCueImg.getWidth();
                int minY = destCueImg.getHeight();
                int maxY = 0;
                int maxX = 0;

                int[][] pixelMatrix = Misc.convertTo2D(destCueImg);
                for (int y = 0; y < destCueImg.getHeight(); y++) {
                    for (int x = 0; x < destCueImg.getWidth(); x++) {
                        // if (pixelMatrix[x][y] == familiarTxtColor) {
                        if (!this.colorWhiteList.contains(new Color(pixelMatrix[x][y]))) {
                            destCueImg.setRGB(x, y, 0);
                        } else {
                            if (y < minY) minY = y;
                            if (x < minX) minX = x;
                            if (y > maxY) maxY = y;
                            if (x > maxX) maxX = x;
                        }
                    }

                }

                int width = maxX > 0 ? maxX - minX + 1 : 0;
                int height = maxY > 0 ? maxY - minY + 1 : 0;

                destCueImg = destCueImg.getSubimage(minX, minY, width, height);
            }

            if (destinationCueFile.exists() && merge) {
                // We load the original screenshot
                BufferedImage existingCueImg;
                try {
                    existingCueImg = ImageIO.read(destinationCueFile);
                } catch (IOException e) {
                    System.out.println("Error while loading existing Cue image: " + destinationCueFile.getAbsolutePath());
                    existingCueImg = new BufferedImage(destCueImg.getWidth(), destCueImg.getHeight(), BufferedImage.TYPE_INT_RGB);
                }

                if (destCueImg.getHeight() == existingCueImg.getHeight() && destCueImg.getWidth() == existingCueImg.getWidth()) {
                    BufferedImage mergedCueImg = CueCompare.pixelCompare(destCueImg, existingCueImg);

                    // If the merge was successful we override the destination Cue
                    if (mergedCueImg != null) destCueImg = mergedCueImg;
                } else {
                    throw new RuntimeException("It was impossible to merge '" + this.destinationCueName + "' from " + this.containingScreenShotPath + " due to different cue dimensions.");
                }
            }

            try {
                ImageIO.write(destCueImg, "png", destinationCueFile);
            } catch (IOException e) {
                System.out.println("Error while writing destination Cue image");
                e.printStackTrace();
                return;
            }

            String cueManager;
            MarvinSegment seg = MarvinSegment.fromFiles(screenShotFile, destinationCueFile, this.cuePosition);

            if (seg != null) {
                Bounds suggestedBounds = Bounds.fromMarvinSegment(seg, null);

                String boundsStr = suggestedBounds.getJavaCode(false, true).replace(";", "");

                // We output the line of code to be used in CueManager so that i can be easily copy-pasted
                cueManager = MessageFormat.format("addCue(\"{0}\", \"{1}\", {2}); // {3} {4}",
                        this.destinationCueName, this.destinationCuePath, boundsStr, this.cueDescription, this.containingScreenShotPath);
            } else {
                cueManager = MessageFormat.format("addCue(\"{0}\", \"{1}\", null); // {2} {3}",
                        this.destinationCueName, this.destinationCuePath, this.cueDescription, this.containingScreenShotPath);
            }

            System.out.println(cueManager);
        }

    }

    /**
     * Use this method when you want to use multiple input files to generate the same Cue. The logic
     * will walk the containing path and add to the hashmap all the files matching the PNGPattern
     *  @param cueLocators        The destination array list where to add the matching cueLocators
     * @param containingPath     The path where the screenshots to build the Cue are located
     * @param PNGPattern         The pattern used to walk containingPath to find relevant source screenshots
     * @param cuePosition        Where to search for the Cue in the origin screenshots
     * @param colorWhiteList     The color whitelist
     * @param transparentRegions Regions that should be made transparent when loading/merging dues
     * @param destinationCueName The destination Cue name as used in CueManager
     * @param destinationCuePath Where the output Cue should be created
     * @param description        The cue description
     */
    static void addCueLocatorByPattern(List<CueLocator> cueLocators, String containingPath, String PNGPattern, Bounds cuePosition, Set<Color> colorWhiteList,
                                       Set<Bounds> transparentRegions, String destinationCueName, String destinationCuePath, String description) {
        File containingPathFile = new File(containingPath);
        if (!containingPathFile.exists() || !containingPathFile.isDirectory()) {
            System.out.println("Invalid containing path: " + containingPath);
            return;
        }

        if (!containingPath.endsWith("/")) containingPath += "/";

        ImageFilter PNGImageFilter = new ImageFilter(PNGPattern);
        String[] PNGPaths = containingPathFile.list(PNGImageFilter);

        if (PNGPaths != null) {
            for (String PNGImgFileName : PNGPaths) {
                cueLocators.add(new CueLocator(containingPath + PNGImgFileName, cuePosition, colorWhiteList,
                        transparentRegions, destinationCueName, destinationCuePath, true, description));
            }
        }
    }

    /**
     * This method is where Cues to be created must be added.
     */
    static void manageCueFiles(boolean printUnused) {
        List<CueLocator> cueLocators = new ArrayList<>();

        // HashMap used to group cueLocators by destination cueName
        HashMap<String, List<CueLocator>> cueLocatorsByDestFile = new HashMap<>();

        // HashSet to have a unique list of the used source screenshots
        HashSet<String> usedFilePaths = new HashSet<>();

        //region AutoConsume
        addCueLocatorByPattern(cueLocators, "cuebuilder/autoConsume", "inventory-1(.*)\\.png", Bounds.fromWidthHeight(472, 124, 164, 26),
                Set.of(), Set.of(), "FilterConsumables", "unitycues/characterMenu/cueFilterConsumables.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/autoConsume", "inventory-filter(.*)\\.png", Bounds.fromWidthHeight(472, 124, 164, 26),
                Set.of(), Set.of(), "StripItemsTitle", "unitycues/characterMenu/cueStripItemsTitle.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/autoConsume", "ConsumableTitle(.*)\\.png", Bounds.fromWidthHeight(318, 142, 171, 32),
                Set.of(), Set.of(), "ConsumableTitle", "unitycues/autoConsume/cueConsumableTitle.png", "");

        //region Consumable Inventory Cues
        addCueLocatorByPattern(cueLocators, "cuebuilder/autoConsume", "min_exp_(.*)\\.png", Bounds.fromWidthHeight(606, 204, 60, 29),
                Set.of(), Set.of(), "ConsumableExpMinor", "unitycues/autoConsume/consumables/cueConsumableExpMinor.png", "Tome of minor experience");
        addCueLocatorByPattern(cueLocators, "cuebuilder/autoConsume", "min_exp_(.*)(.*)\\.png", Bounds.fromWidthHeight(469, 204, 60, 29),
                Set.of(), Set.of(), "ConsumableSpeedMinor", "unitycues/autoConsume/consumables/cueConsumableSpeedMinor.png", "Minor Speed Kicks");
        addCueLocatorByPattern(cueLocators, "cuebuilder/autoConsume", "min_exp_(.*)\\.png", Bounds.fromWidthHeight(538, 204, 60, 29),
                Set.of(), Set.of(), "ConsumableGoldMinor", "unitycues/autoConsume/consumables/cueConsumableGoldMinor.png", "Minor Gold Find Potion");
        addCueLocatorByPattern(cueLocators, "cuebuilder/autoConsume", "min_exp_(.*)\\.png", Bounds.fromWidthHeight(469, 273, 60, 29),
                Set.of(), Set.of(), "ConsumableItemMinor", "unitycues/autoConsume/consumables/cueConsumableItemMinor.png", "Minor Item Find Scroll");
        //endregion

        //region Consumable Functional Cues
        addCueLocatorByPattern(cueLocators, "cuebuilder/autoConsume", "filter_btn_(.*)\\.png", Bounds.fromWidthHeight(518, 103, 126, 35),
                Set.of(), Set.of(), "Filter", "unitycues/autoConsume/cueFilter.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/autoConsume", "filter_consumables_o(.*)\\.png", Bounds.fromWidthHeight(333, 122, 132, 44),
                Set.of(), Set.of(), "FilterTitle", "unitycues/autoConsume/cueFilterTitle.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/autoConsume", "filter_consumables_o(.*)\\.png", Bounds.fromWidthHeight(417, 268, 182, 27),
                Set.of(), Set.of(), "ConsumablesBtn", "unitycues/autoConsume/cueConsumablesBtn.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/autoConsume", "consumable_done(.*)\\.png", Bounds.fromWidthHeight(353, 458, 94, 31),
                Set.of(), Set.of(), "ConsumableDone", "unitycues/autoConsume/cueConsumableDone.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/autoConsume", "consumable_done(.*)\\.png", Bounds.fromWidthHeight(310, 81, 180, 32),
                Set.of(new Color(255, 255, 255), new Color(206, 206, 206), new Color(116, 116, 116)),
                Set.of(), "ConsumableHaveFun", "unitycues/autoConsume/cueConsumableHaveFun.png", "");
        //endregion

        //endregion

        //region Auto Shrine
        addCueLocatorByPattern(cueLocators, "cuebuilder/autoShrine", "settings(.*)\\.png", Bounds.fromWidthHeight(376, 115, 39, 58),
                Set.of(), Set.of(), "Settings", "unitycues/autoShrine/cueSettings.png", "");
        //endregion

        //region Blockers
        addCueLocatorByPattern(cueLocators, "cuebuilder/blockers", "reconnect(.*)\\.png", Bounds.fromWidthHeight(338, 346, 127, 32),
                Set.of(), Set.of(), "Reconnect", "unitycues/blockers/cueReconnect.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/blockers", "news(.*)\\.png", Bounds.fromWidthHeight(358, 64, 83, 58),
                Set.of(), Set.of(), "News", "unitycues/blockers/cueNewsPopup.png", "");
        cueLocators.add(new CueLocator("cuebuilder/blockers/news.png", Bounds.fromWidthHeight(421, 447, 119, 32),
                Set.of(new Color(255, 255, 255)), Set.of(), "NewsClose", "unitycues/blockers/cueNewsClose.png", true, ""));
        addCueLocatorByPattern(cueLocators, "cuebuilder/blockers", "reconnect(.*)\\.png", Bounds.fromWidthHeight(336, 131, 129, 58),
                Set.of(), Set.of(), "UhOh", "unitycues/blockers/cueUhoh.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/blockers", "reconnect(.*)\\.png", Bounds.fromWidthHeight(300, 230, 212, 67),
                Set.of(), Set.of(), "Disconnected", "unitycues/blockers/cueDisconnected.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/blockers", "daily(.*)\\.png", Bounds.fromWidthHeight(259, 52, 282, 57),
                Set.of(), Set.of(), "DailyRewards", "unitycues/blockers/cueDailyRewards.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/blockers", "daily(.*)\\.png", Bounds.fromWidthHeight(353, 444, 97, 31),
                Set.of(), Set.of(), "Claim", "unitycues/blockers/cueClaim.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/blockers", "items(.*)\\.png", Bounds.fromWidthHeight(339, 117, 119, 58),
                Set.of(), Set.of(), "Items", "unitycues/blockers/cueItems.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/blockers", "recently_disc_(.*)\\.png", Bounds.fromWidthHeight(270, 208, 258, 109),
                Set.of(), Set.of(), "RecentlyDisconnected", "unitycues/blockers/cueRecentlyDisconnected.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/blockers", "not_in_guild_(.*)\\.png", Bounds.fromWidthHeight(267, 226, 264, 65),
                Set.of(), Set.of(), "NotInAGuild", "unitycues/blockers/cueNotInAGuild.png", "You are currently non in a Guild popup");
        addCueLocatorByPattern(cueLocators, "cuebuilder/blockers", "connecting_(.*)\\.png", Bounds.fromWidthHeight(301, 229, 194, 61),
                Set.of(), Set.of(), "Connecting", "unitycues/blockers/cueConnecting.png", "Connecting to server popup");
        //endregion

        //region Bounties
        addCueLocatorByPattern(cueLocators, "cuebuilder/bounties", "bounties_main_(.*)\\.png", Bounds.fromWidthHeight(319, 67, 164, 33),
                Set.of(), Set.of(), "Bounties", "unitycues/bounties/cueBounties.png", "Bounties dialog title");
        addCueLocatorByPattern(cueLocators, "cuebuilder/bounties", "bounties_main_(.*)\\.png", Bounds.fromWidthHeight(508, 245, 74, 27),
                Set.of(), Set.of(), "Loot", "unitycues/bounties/cueLoot.png", "Green Loot button");
        //endregion Bounties

        //region Common
        cueLocators.add(new CueLocator("cuebuilder/raid/raid-team.png", Bounds.fromWidthHeight(326, 453, 87, 29),
                Set.of(new Color(255, 255, 255)), Set.of(), "TeamClear", "unitycues/common/cueTeamClear.png", false, ""));
        addCueLocatorByPattern(cueLocators, "cuebuilder/common", "cleared_old(.*)\\.png", Bounds.fromWidthHeight(330, 132, 139, 56),
                Set.of(), Set.of(), "Cleared", "unitycues/common/cueCleared.png", "");
        cueLocators.add(new CueLocator("cuebuilder/common/cleared_old.png", Bounds.fromWidthHeight(303, 345, 61, 32),
                Set.of(), Set.of(), "YesGreen", "unitycues/common/cueYesGreen.png", true, ""));
        cueLocators.add(new CueLocator("cuebuilder/common/solo.png", Bounds.fromWidthHeight(303, 345, 61, 32),
                Set.of(), Set.of(), "YesGreen", "unitycues/common/cueYesGreen.png", true, ""));
        cueLocators.add(new CueLocator("cuebuilder/common/solo_2.png", Bounds.fromWidthHeight(303, 345, 61, 32),
                Set.of(), Set.of(), "YesGreen", "unitycues/common/cueYesGreen.png", true, ""));
        cueLocators.add(new CueLocator("cuebuilder/treasureChest/confirm-decline.png", Bounds.fromWidthHeight(303, 345, 61, 32),
                Set.of(), Set.of(), "YesGreen", "unitycues/common/cueYesGreen.png", true, ""));
        addCueLocatorByPattern(cueLocators, "cuebuilder/common", "not-full(.*)\\.png", Bounds.fromWidthHeight(350, 130, 99, 58),
                Set.of(), Set.of(), "Team", "unitycues/common/cueTeam.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/common", "not-full(.*)\\.png", Bounds.fromWidthHeight(275, 217, 256, 97),
                Set.of(), Set.of(), "TeamNotFull", "unitycues/common/cueTeamNotFull.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/common", "not-full(.*)\\.png", Bounds.fromWidthHeight(445, 349, 45, 26),
                Set.of(), Set.of(), "No", "unitycues/common/cueNo.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/raid", "raid-team(.*)\\.png", Bounds.fromWidthHeight(204, 450, 84, 35),
                Set.of(), Set.of(), "AutoTeam", "unitycues/common/cueAutoTeam.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/raid", "raid-team(.*)\\.png", Bounds.fromWidthHeight(456, 451, 124, 32),
                Set.of(), Set.of(), "TeamAccept", "unitycues/common/cueTeamAccept.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/common", "are-you-there(.*)\\.png", Bounds.fromWidthHeight(307, 236, 195, 57),
                Set.of(), Set.of(), "AreYouThere", "unitycues/common/cueAreYouThere.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/common", "are-you-there(.*)\\.png", Bounds.fromWidthHeight(367, 346, 66, 31),
                Set.of(), Set.of(), "Yes", "unitycues/common/cueYes.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/common", "defeat_(.*)\\.png", Bounds.fromWidthHeight(335, 130, 130, 58),
                Set.of(), Set.of(), "Defeat", "unitycues/common/cueDefeat.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/common", "speed_(.*)\\.png", Bounds.fromWidthHeight(10, 465, 54, 38),
                Set.of(), Set.of(), "SpeedBar", "unitycues/common/cueSpeedBar.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/common", "opnecharmenu-no-rune-button(.*)\\.png", Bounds.fromWidthHeight(124, 474, 71, 25),
                Set.of(), Set.of(), "Runes", "unitycues/characterMenu/cueRunes.png", "The purple rune button in character menu.");
        addCueLocatorByPattern(cueLocators, "cuebuilder/common", "speed(.*)\\.png", Bounds.fromWidthHeight(17, 507, 45, 13),
                Set.of(new Color(255, 255, 255)), Set.of(), "SpeedTXT", "unitycues/common/cueSpeedTXT.png", "Speed text while in a fight");
        addCueLocatorByPattern(cueLocators, "cuebuilder/common", "cleared_recap_(.*)\\.png", Bounds.fromWidthHeight(334, 68, 141, 27),
                Set.of(), Set.of(), "ClearedRecap", "unitycues/common/cueClearedRecap.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/common", "cleared_recap_(.*)\\.png", Bounds.fromWidthHeight(299, 465, 86, 26),
                Set.of(), Set.of(), "Rerun", "unitycues/common/cueRerun.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/common", "cleared_recap_(.*)\\.png", Bounds.fromWidthHeight(453, 466, 72, 26),
                Set.of(), Set.of(), "Town", "unitycues/common/cueTown.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/common", "victory_recap_(.*)\\.png", Bounds.fromWidthHeight(346, 73, 109, 17),
                Set.of(), Set.of(), "VictoryRecap", "unitycues/common/cueVictoryRecap.png", "Victory message when completing adventures");
        addCueLocatorByPattern(cueLocators, "cuebuilder/gvg", "gvg-victory_(.*)\\.png", Bounds.fromWidthHeight(350, 73, 109, 17),
                Set.of(), Set.of(), "VictoryRecap", "unitycues/common/cueVictoryRecap.png", "Victory message when completing adventures");

        addCueLocatorByPattern(cueLocators, "cuebuilder/common", "defeat_recap_(.*)\\.png", Bounds.fromWidthHeight(347, 67, 113, 28),
                Set.of(), Set.of(), "DefeatRecap", "unitycues/common/cueDefeatRecap.png", "Defeat message when completing adventures");

        Set<Bounds> weeklyTransparent = Set.of(Bounds.fromWidthHeight(296, 122, 215, 48), Bounds.fromWidthHeight(247, 193, 286, 153),
                Bounds.fromWidthHeight(550, 126, 58, 53), Bounds.fromWidthHeight(535, 194, 19, 148));
        addCueLocatorByPattern(cueLocators, "cuebuilder/bounties", "bounties_confirm_(.*)\\.png", Bounds.fromWidthHeight(219, 146, 367, 231),
                Set.of(), weeklyTransparent, "WeeklyRewards", "unitycues/common/cueWeeklyRewards.png", "Weekly rewards gump");

        Set<Bounds> choiceTransp = Set.of(Bounds.fromWidthHeight(266, 138, 247, 44));
        addCueLocatorByPattern(cueLocators, "cuebuilder/common", "top_choice_(.*)\\.png", Bounds.fromWidthHeight(260, 133, 258, 54),
                Set.of(), choiceTransp, "TopChoice", "unitycues/common/cueTopChoice.png", "Top Choice border in selection windows");

        addCueLocatorByPattern(cueLocators, "cuebuilder/tierGauntlet", "tg_cost_(.*)\\.png", Bounds.fromWidthHeight(521, 274, 58, 15),
                Set.of(), Set.of(), "Play", "unitycues/common/cuePlay.png", "Blue Play button.");
        addCueLocatorByPattern(cueLocators, "cuebuilder/gvg", "gvg-main_(.*)\\.png", Bounds.fromWidthHeight(522, 275, 58, 15),
                Set.of(), Set.of(), "Play", "unitycues/common/cuePlay.png", "Blue Play button.");
        addCueLocatorByPattern(cueLocators, "cuebuilder/invasion", "inv-main_(.*)\\.png", Bounds.fromWidthHeight(522, 274, 58, 15),
                Set.of(), Set.of(), "Play", "unitycues/common/cuePlay.png", "Blue Play button.");
        addCueLocatorByPattern(cueLocators, "cuebuilder/pvp", "pvp-main_(.*)\\.png", Bounds.fromWidthHeight(521, 269, 58, 15),
                Set.of(), Set.of(), "Play", "unitycues/common/cuePlay.png", "Blue Play button.");

        addCueLocatorByPattern(cueLocators, "cuebuilder/common", "not_enough_tokens_(.*)\\.png", Bounds.fromWidthHeight(272, 227, 251, 70),
                Set.of(), Set.of(), "NotEnoughTokens", "unitycues/common/cueNotEnoughTokens.png", "Not enough token popup.");

        final Set<Bounds> tgCostTransp = Set.of(Bounds.fromWidthHeight(518, 198, 50, 30), Bounds.fromWidthHeight(518, 201, 53, 24));
        addCueLocatorByPattern(cueLocators, "cuebuilder/tierGauntlet", "tg_cost_(.*)\\.png", Bounds.fromWidthHeight(524, 160, 86, 71),
                Set.of(), tgCostTransp, "Cost", "unitycues/common/cueCost.png", "Cost combo box for T/G, GVG, PVP, Invasion, Expedition");
        final Set<Bounds> gvgCostTransp = Set.of(Bounds.fromWidthHeight(519, 197, 50, 30), Bounds.fromWidthHeight(519, 200, 53, 24));
        addCueLocatorByPattern(cueLocators, "cuebuilder/gvg", "gvg-main_(.*)\\.png", Bounds.fromWidthHeight(525, 159, 86, 71),
                Set.of(), gvgCostTransp, "Cost", "unitycues/common/cueCost.png", "Cost combo box for T/G, GVG, PVP, Invasion, Expedition");
        final Set<Bounds> expCostTransp = Set.of(Bounds.fromWidthHeight(519, 194, 50, 30), Bounds.fromWidthHeight(519, 197, 53, 24));
        addCueLocatorByPattern(cueLocators, "cuebuilder/expedition", "exp-main_(.*)\\.png", Bounds.fromWidthHeight(525, 156, 86, 71),
                Set.of(), expCostTransp, "Cost", "unitycues/common/cueCost.png", "Cost combo box for T/G, GVG, PVP, Invasion, Expedition");
        final Set<Bounds> invCostTransp = Set.of(Bounds.fromWidthHeight(519, 198, 50, 30), Bounds.fromWidthHeight(519, 201, 53, 24));
        addCueLocatorByPattern(cueLocators, "cuebuilder/invasion", "inv-main_(.*)\\.png", Bounds.fromWidthHeight(525, 160, 86, 71),
                Set.of(), invCostTransp, "Cost", "unitycues/common/cueCost.png", "Cost combo box for T/G, GVG, PVP, Invasion, Expedition");
        final Set<Bounds> pvpCostTransp = Set.of(Bounds.fromWidthHeight(518, 192, 50, 30), Bounds.fromWidthHeight(518, 195, 53, 24));
        addCueLocatorByPattern(cueLocators, "cuebuilder/pvp", "pvp-main_(.*)\\.png", Bounds.fromWidthHeight(524, 154, 86, 71),
                Set.of(), pvpCostTransp, "Cost", "unitycues/common/cueCost.png", "Cost combo box for T/G, GVG, PVP, Invasion, Expedition");

        addCueLocatorByPattern(cueLocators, "cuebuilder/gvg", "gvg-main_(.*)\\.png", Bounds.fromWidthHeight(327, 53, 34, 32),
                Set.of(), Set.of(), "BadgeBar", "unitycues/common/cueBadgeBar.png", "Badge bar popup");
        addCueLocatorByPattern(cueLocators, "cuebuilder/invasion", "inv-main_(.*)\\.png", Bounds.fromWidthHeight(327, 53, 34, 32),
                Set.of(), Set.of(), "BadgeBar", "unitycues/common/cueBadgeBar.png", "Badge bar popup");
        addCueLocatorByPattern(cueLocators, "cuebuilder/expedition", "exp-portal_(.*)\\.png", Bounds.fromWidthHeight(327, 42, 34, 32),
                Set.of(), Set.of(), "BadgeBar", "unitycues/common/cueBadgeBar.png", "Badge bar popup");

        addCueLocatorByPattern(cueLocators, "cuebuilder/expedition", "exp-portal_(.*)\\.png", Bounds.fromWidthHeight(363, 455, 73, 15),
                Set.of(), Set.of(), "Enter", "unitycues/common/cueEnter.png", "Green Enter button found in Expedition and in some D4");
        addCueLocatorByPattern(cueLocators, "cuebuilder/gvg", "gvg-opponement_(.*)\\.png", Bounds.fromWidthHeight(576, 201, 53, 12),
                Set.of(), Set.of(), "Fight", "unitycues/common/cueFight.png", "PVP/GVG Fight Button");
        //endregion Common

        //region CueX
        cueLocators.add(new CueLocator("cuebuilder/raid/raid-summon.png", Bounds.fromLength(616, 97, 48),
                Set.of(), Set.of(), "X", "unitycues/common/cueX.png", true, ""));
        cueLocators.add(new CueLocator("cuebuilder/blockers/items_20210112.png", Bounds.fromLength(566, 129, 48),
                Set.of(), Set.of(), "X", "unitycues/common/cueX.png", true, ""));
        cueLocators.add(new CueLocator("cuebuilder/dungeon/dung-zone_20210115.png", Bounds.fromLength(706, 53, 48),
                Set.of(), Set.of(), "X", "unitycues/common/cueX.png", true, ""));
        cueLocators.add(new CueLocator("cuebuilder/common/dung-x_20210119.png", Bounds.fromLength(746, 8, 48),
                Set.of(), Set.of(), "X", "unitycues/common/cueX.png", true, ""));
        cueLocators.add(new CueLocator("cuebuilder/common/raid-x_vmi529938.contaboserver.net_20220105_13.png", Bounds.fromLength(616, 96, 48),
                Set.of(), Set.of(), "X", "unitycues/common/cueX.png", true, ""));
        cueLocators.add(new CueLocator("cuebuilder/common/reward-x_DESKTOP-6OOEROP_20211230.png", Bounds.fromLength(556, 128, 48),
                Set.of(), Set.of(), "X", "unitycues/common/cueX.png", true, ""));
        addCueLocatorByPattern(cueLocators, "cuebuilder/bounties", "bounties_main_(.*)\\.png", Bounds.fromLength(666, 62, 48),
                Set.of(), Set.of(), "X", "unitycues/common/cueX.png", "White X with red backgroud");

        //endregion

        //region Dungeon
        addCueLocatorByPattern(cueLocators, "cuebuilder/dungeon", "dung-diff(.*)\\.png", Bounds.fromWidthHeight(147, 232, 122, 27),
                Set.of(), Set.of(), "DungNormal", "unitycues/dungeon/cueDungNormal.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/dungeon", "dung-diff(.*)\\.png", Bounds.fromWidthHeight(340, 232, 122, 27),
                Set.of(), Set.of(), "DungHard", "unitycues/dungeon/cueDungHard.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/dungeon", "dung-diff(.*)\\.png", Bounds.fromWidthHeight(535, 232, 122, 27),
                Set.of(), Set.of(), "DungHeroic", "unitycues/dungeon/cueDungHeroic.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/dungeon/zones", "z\\d{2}(.*)\\.png", Bounds.fromWidthHeight(104, 67, 102, 30),
                Set.of(), Set.of(), "DungeonZones", "unitycues/dungeon/cueDungeonZones.png", "");
        //endregion

        //region Expedition
        addCueLocatorByPattern(cueLocators, "cuebuilder/expedition", "exp-jammie_(.*)\\.png", Bounds.fromWidthHeight(219, 60, 361, 23),
                Set.of(), Set.of(), "Expedition3", "unitycues/expedition/cueExpedition3Jammie.png", "Jammie Expedition Title");
        addCueLocatorByPattern(cueLocators, "cuebuilder/expedition", "exp-idol_(.*)\\.png", Bounds.fromWidthHeight(243, 60, 313, 23),
                Set.of(), Set.of(), "Expedition4", "unitycues/expedition/cueExpedition4Idol.png", "Idol Expedition Title");
        final Set<Bounds> expDiffTransp = Set.of(Bounds.fromWidthHeight(413, 393, 108, 28), Bounds.fromWidthHeight(410, 396, 114, 24));
        addCueLocatorByPattern(cueLocators, "cuebuilder/expedition", "exp-portal_(.*)\\.png", Bounds.fromWidthHeight(403, 366, 162, 69),
                Set.of(), expDiffTransp, "DifficultyExpedition", "unitycues/expedition/cueDifficultyExpedition.png", "Expedition Difficulty combo box");
        addCueLocatorByPattern(cueLocators, "cuebuilder/expedition", "exp-portal_(.*)\\.png", Bounds.fromWidthHeight(530, 366, 33, 69),
                Set.of(), Set.of(), "SelectDifficultyExpedition", "unitycues/expedition/cueSelectDifficultyExpedition.png", "Expedition Difficulty selection Arrow");
        //endregion Expedition

        //region Familiar Encounters
        addCueLocatorByPattern(cueLocators, "cuebuilder/familiarEncounter", "encounter(.*)\\.png", Bounds.fromWidthHeight(141, 275, 23, 31),
                Set.of(), Set.of(), "FamiliarEncounter", "unitycues/familiarEncounter/cueEncounter.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/familiarEncounter", "encounter(.*)\\.png", Bounds.fromWidthHeight(134, 327, 133, 30),
                Set.of(), Set.of(), "Persuade", "unitycues/familiarEncounter/cuePersuade.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/familiarEncounter", "encounter(.*)\\.png", Bounds.fromWidthHeight(553, 326, 94, 31),
                Set.of(), Set.of(), "Bribe", "unitycues/familiarEncounter/cueBribe.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/familiarEncounter", "encounter(.*)\\.png", Bounds.fromWidthHeight(253, 444, 108, 26),
                Set.of(), Set.of(), "DeclineRed", "unitycues/familiarEncounter/cueDeclineRed.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/familiarEncounter", "encounter_common(.*)\\.png", Bounds.fromWidthHeight(539, 276, 124, 30),
                Set.of(), Set.of(), "CommonFamiliar", "unitycues/familiarEncounter/type/cue01CommonFamiliar.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/familiarEncounter", "encounter_rare(.*)\\.png", Bounds.fromWidthHeight(539, 276, 124, 30),
                Set.of(), Set.of(), "RareFamiliar", "unitycues/familiarEncounter/type/cue02RareFamiliar.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/familiarEncounter", "encounter_epic(.*)\\.png", Bounds.fromWidthHeight(539, 276, 124, 30),
                Set.of(), Set.of(), "EpicFamiliar", "unitycues/familiarEncounter/type/cue03EpicFamiliar.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/familiarEncounter", "encounter_legendary(.*)\\.png", Bounds.fromWidthHeight(539, 276, 124, 30),
                Set.of(), Set.of(), "LegendaryFamiliar", "unitycues/familiarEncounter/type/cue04LegendaryFamiliar.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/familiarEncounter", "you_currently_own_(.*)\\.png", null,
                Set.of(), Set.of(), "YouCurrentlyOwn", "unitycues/familiarEncounter/cueYouCurrentlyOwn.png", "You currently own text in familiar encounters");
        addCueLocatorByPattern(cueLocators, "cuebuilder/familiarEncounter", "capture_success_(.*)\\.png", null,
                Set.of(), Set.of(), "CaptureSuccess", "unitycues/familiarEncounter/cueCaptureSuccess.png", "Capture Successful text in familiar encounters");
        //endregion

        //region GVG
        addCueLocatorByPattern(cueLocators, "cuebuilder/gvg", "gvg-main_(.*)\\.png", Bounds.fromWidthHeight(267, 107, 265, 21),
                Set.of(), Set.of(), "GVGWindow", "unitycues/gvg/cueGVGWindow.png", "GVG Main Window Title");
        addCueLocatorByPattern(cueLocators, "cuebuilder/gvg", "gvg-popup_(.*)\\.png", Bounds.fromWidthHeight(272, 210, 256, 104),
                Set.of(), Set.of(), "GuildLeaveConfirm", "unitycues/gvg/cueGuildLeaveConfirm.png", "GVG Guild warning popup");
        //endregion GVG

        //region Invasion
        addCueLocatorByPattern(cueLocators, "cuebuilder/invasion", "inv-main_(.*)\\.png", Bounds.fromWidthHeight(324, 107, 152, 21),
                Set.of(), Set.of(), "InvasionWindow", "unitycues/invasion/cueInvasionWindow.png", "Invasion Main Window Title");
        //endregion Invasion

        //region Merchant
        addCueLocatorByPattern(cueLocators, "cuebuilder/merchant", "merchant-title(.*)\\.png", Bounds.fromWidthHeight(311, 116, 175, 49),
                Set.of(), Set.of(), "Merchant", "unitycues/merchant/cueMerchant.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/merchant", "merchant-title(.*)\\.png", Bounds.fromWidthHeight(422, 378, 109, 29),
                Set.of(), Set.of(), "MerchantDecline", "unitycues/merchant/cueMerchantDecline.png", "");
        //endregion

        //region Main Menu
        addCueLocatorByPattern(cueLocators, "cuebuilder/mainScreen", "gor-menu(.*)\\.png", Bounds.fromWidthHeight(107, 477, 33, 34),
                Set.of(), Set.of(), "GorMenu", "unitycues/mainScreen/cueGorMenu.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/mainScreen", "gor-menu(.*)\\.png", Bounds.fromWidthHeight(676, 464, 34, 37),
                Set.of(), Set.of(), "SettingsGear", "unitycues/mainScreen/cueSettingsGear.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/mainScreen", "raid-menu(.*)\\.png", Bounds.fromWidthHeight(30, 308, 16, 40),
                Set.of(), Set.of(), "RaidButton", "unitycues/mainScreen/cueRaidButton.png", "Ruby icon used to open the raid menu.");
        addCueLocatorByPattern(cueLocators, "cuebuilder/mainScreen", "raid-menu(.*)\\.png", Bounds.fromWidthHeight(24, 15, 24, 51),
                Set.of(), Set.of(), "Quest", "unitycues/mainScreen/cueQuestButton.png", "Quest icon used to open the dungeon menu.");
        addCueLocatorByPattern(cueLocators, "cuebuilder/invasion", "inv-icon_(.*)\\.png", Bounds.fromWidthHeight(747, 243, 31, 44),
                Set.of(), Set.of(), "Invasion", "unitycues/mainScreen/cueInvasion.png", "Quest icon used to open the Invasion menu.");
        //endregion

        //region Numbers
        //region TG Difficulty selection 17px
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/tgDiffSelection17", "tg_diff_selection_17_0(.*)\\.png", null,
                Set.of(), Set.of(), "tg_diff_selection_17_0", "unitycues/numbers/tgDiffSelection17/tg_diff_selection_17_0.png", "TG Difficulty selection 17px 0");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/tgDiffSelection17", "tg_diff_selection_17_1(.*)\\.png", null,
                Set.of(), Set.of(), "tg_diff_selection_17_1", "unitycues/numbers/tgDiffSelection17/tg_diff_selection_17_1.png", "TG Difficulty selection 17px 1");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/tgDiffSelection17", "tg_diff_selection_17_2(.*)\\.png", null,
                Set.of(), Set.of(), "tg_diff_selection_17_2", "unitycues/numbers/tgDiffSelection17/tg_diff_selection_17_2.png", "TG Difficulty selection 17px 2");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/tgDiffSelection17", "tg_diff_selection_17_3(.*)\\.png", null,
                Set.of(), Set.of(), "tg_diff_selection_17_3", "unitycues/numbers/tgDiffSelection17/tg_diff_selection_17_3.png", "TG Difficulty selection 17px 3");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/tgDiffSelection17", "tg_diff_selection_17_4(.*)\\.png", null,
                Set.of(), Set.of(), "tg_diff_selection_17_4", "unitycues/numbers/tgDiffSelection17/tg_diff_selection_17_4.png", "TG Difficulty selection 17px 4");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/tgDiffSelection17", "tg_diff_selection_17_5(.*)\\.png", null,
                Set.of(), Set.of(), "tg_diff_selection_17_5", "unitycues/numbers/tgDiffSelection17/tg_diff_selection_17_5.png", "TG Difficulty selection 17px 5");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/tgDiffSelection17", "tg_diff_selection_17_6(.*)\\.png", null,
                Set.of(), Set.of(), "tg_diff_selection_17_6", "unitycues/numbers/tgDiffSelection17/tg_diff_selection_17_6.png", "TG Difficulty selection 17px 6");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/tgDiffSelection17", "tg_diff_selection_17_7(.*)\\.png", null,
                Set.of(), Set.of(), "tg_diff_selection_17_7", "unitycues/numbers/tgDiffSelection17/tg_diff_selection_17_7.png", "TG Difficulty selection 17px 7");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/tgDiffSelection17", "tg_diff_selection_17_8(.*)\\.png", null,
                Set.of(), Set.of(), "tg_diff_selection_17_8", "unitycues/numbers/tgDiffSelection17/tg_diff_selection_17_8.png", "TG Difficulty selection 17px 8");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/tgDiffSelection17", "tg_diff_selection_17_9(.*)\\.png", null,
                Set.of(), Set.of(), "tg_diff_selection_17_9", "unitycues/numbers/tgDiffSelection17/tg_diff_selection_17_9.png", "TG Difficulty selection 17px 9");
        //endregion TG Difficulty selection 17px

        //region TG Difficulty range 16px
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/tgDiffRange16", "tg_diff_range_16_0(.*)\\.png", null,
                Set.of(), Set.of(), "tg_diff_range_16_0", "unitycues/numbers/tgDiffRange16/tg_diff_range_16_0.png", "TG Difficulty range 16px 0");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/tgDiffRange16", "tg_diff_range_16_1(.*)\\.png", null,
                Set.of(), Set.of(), "tg_diff_range_16_1", "unitycues/numbers/tgDiffRange16/tg_diff_range_16_1.png", "TG Difficulty range 16px 1");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/tgDiffRange16", "tg_diff_range_16_2(.*)\\.png", null,
                Set.of(), Set.of(), "tg_diff_range_16_2", "unitycues/numbers/tgDiffRange16/tg_diff_range_16_2.png", "TG Difficulty range 16px 2");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/tgDiffRange16", "tg_diff_range_16_3(.*)\\.png", null,
                Set.of(), Set.of(), "tg_diff_range_16_3", "unitycues/numbers/tgDiffRange16/tg_diff_range_16_3.png", "TG Difficulty range 16px 3");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/tgDiffRange16", "tg_diff_range_16_4(.*)\\.png", null,
                Set.of(), Set.of(), "tg_diff_range_16_4", "unitycues/numbers/tgDiffRange16/tg_diff_range_16_4.png", "TG Difficulty range 16px 4");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/tgDiffRange16", "tg_diff_range_16_5(.*)\\.png", null,
                Set.of(), Set.of(), "tg_diff_range_16_5", "unitycues/numbers/tgDiffRange16/tg_diff_range_16_5.png", "TG Difficulty range 16px 5");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/tgDiffRange16", "tg_diff_range_16_6(.*)\\.png", null,
                Set.of(), Set.of(), "tg_diff_range_16_6", "unitycues/numbers/tgDiffRange16/tg_diff_range_16_6.png", "TG Difficulty range 16px 6");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/tgDiffRange16", "tg_diff_range_16_7(.*)\\.png", null,
                Set.of(), Set.of(), "tg_diff_range_16_7", "unitycues/numbers/tgDiffRange16/tg_diff_range_16_7.png", "TG Difficulty range 16px 7");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/tgDiffRange16", "tg_diff_range_16_8(.*)\\.png", null,
                Set.of(), Set.of(), "tg_diff_range_16_8", "unitycues/numbers/tgDiffRange16/tg_diff_range_16_8.png", "TG Difficulty range 16px 8");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/tgDiffRange16", "tg_diff_range_16_9(.*)\\.png", null,
                Set.of(), Set.of(), "tg_diff_range_16_9", "unitycues/numbers/tgDiffRange16/tg_diff_range_16_9.png", "TG Difficulty range 16px 9");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/tgDiffRange16", "tg_diff_range_16_hyphen(.*)\\.png", null,
                Set.of(), Set.of(), "tg_diff_range_16_hyphen", "unitycues/numbers/tgDiffRange16/tg_diff_range_16_hyphen.png", "TG Difficulty range 16px hyphen");
        //endregion TG Difficulty range 16px

        //region TG Main Window Difficulty & Cost 11px
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/tgDiffCostWindow11", "tg_diff_cost_win_11_0(.*)\\.png", null,
                Set.of(), Set.of(), "tg_diff_cost_win_11_0", "unitycues/numbers/tgDiffCostWindow11/tg_diff_cost_win_11_0.png", "TG Main Window Difficulty & Cost 11px 0");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/tgDiffCostWindow11", "tg_diff_cost_win_11_1(.*)\\.png", null,
                Set.of(), Set.of(), "tg_diff_cost_win_11_1", "unitycues/numbers/tgDiffCostWindow11/tg_diff_cost_win_11_1.png", "TG Main Window Difficulty & Cost 11px 1");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/tgDiffCostWindow11", "tg_diff_cost_win_11_2(.*)\\.png", null,
                Set.of(), Set.of(), "tg_diff_cost_win_11_2", "unitycues/numbers/tgDiffCostWindow11/tg_diff_cost_win_11_2.png", "TG Main Window Difficulty & Cost 11px 2");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/tgDiffCostWindow11", "tg_diff_cost_win_11_3(.*)\\.png", null,
                Set.of(), Set.of(), "tg_diff_cost_win_11_3", "unitycues/numbers/tgDiffCostWindow11/tg_diff_cost_win_11_3.png", "TG Main Window Difficulty & Cost 11px 3");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/tgDiffCostWindow11", "tg_diff_cost_win_11_4(.*)\\.png", null,
                Set.of(), Set.of(), "tg_diff_cost_win_11_4", "unitycues/numbers/tgDiffCostWindow11/tg_diff_cost_win_11_4.png", "TG Main Window Difficulty & Cost 11px 4");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/tgDiffCostWindow11", "tg_diff_cost_win_11_5(.*)\\.png", null,
                Set.of(), Set.of(), "tg_diff_cost_win_11_5", "unitycues/numbers/tgDiffCostWindow11/tg_diff_cost_win_11_5.png", "TG Main Window Difficulty & Cost 11px 5");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/tgDiffCostWindow11", "tg_diff_cost_win_11_6(.*)\\.png", null,
                Set.of(), Set.of(), "tg_diff_cost_win_11_6", "unitycues/numbers/tgDiffCostWindow11/tg_diff_cost_win_11_6.png", "TG Main Window Difficulty & Cost 11px 6");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/tgDiffCostWindow11", "tg_diff_cost_win_11_7(.*)\\.png", null,
                Set.of(), Set.of(), "tg_diff_cost_win_11_7", "unitycues/numbers/tgDiffCostWindow11/tg_diff_cost_win_11_7.png", "TG Main Window Difficulty & Cost 11px 7");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/tgDiffCostWindow11", "tg_diff_cost_win_11_8(.*)\\.png", null,
                Set.of(), Set.of(), "tg_diff_cost_win_11_8", "unitycues/numbers/tgDiffCostWindow11/tg_diff_cost_win_11_8.png", "TG Main Window Difficulty & Cost 11px 8");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/tgDiffCostWindow11", "tg_diff_cost_win_11_9(.*)\\.png", null,
                Set.of(), Set.of(), "tg_diff_cost_win_11_9", "unitycues/numbers/tgDiffCostWindow11/tg_diff_cost_win_11_9.png", "TG Main Window Difficulty & Cost 11px 9");
        //endregion TG Main Window Difficulty & Cost 11px

        //region WB Player TS
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbPlayerTS", "wb_player_ts_0(.*)\\.png", null,
                Set.of(), Set.of(), "wb_player_ts_0", "unitycues/numbers/wbPlayerTS/wb_player_ts_0.png", "WB Player TS 0");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbPlayerTS", "wb_player_ts_1(.*)\\.png", null,
                Set.of(), Set.of(), "wb_player_ts_1", "unitycues/numbers/wbPlayerTS/wb_player_ts_1.png", "WB Player TS 1");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbPlayerTS", "wb_player_ts_2(.*)\\.png", null,
                Set.of(), Set.of(), "wb_player_ts_2", "unitycues/numbers/wbPlayerTS/wb_player_ts_2.png", "WB Player TS 2");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbPlayerTS", "wb_player_ts_3(.*)\\.png", null,
                Set.of(), Set.of(), "wb_player_ts_3", "unitycues/numbers/wbPlayerTS/wb_player_ts_3.png", "WB Player TS 3");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbPlayerTS", "wb_player_ts_4(.*)\\.png", null,
                Set.of(), Set.of(), "wb_player_ts_4", "unitycues/numbers/wbPlayerTS/wb_player_ts_4.png", "WB Player TS 4");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbPlayerTS", "wb_player_ts_5(.*)\\.png", null,
                Set.of(), Set.of(), "wb_player_ts_5", "unitycues/numbers/wbPlayerTS/wb_player_ts_5.png", "WB Player TS 5");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbPlayerTS", "wb_player_ts_6(.*)\\.png", null,
                Set.of(), Set.of(), "wb_player_ts_6", "unitycues/numbers/wbPlayerTS/wb_player_ts_6.png", "WB Player TS 6");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbPlayerTS", "wb_player_ts_7(.*)\\.png", null,
                Set.of(), Set.of(), "wb_player_ts_7", "unitycues/numbers/wbPlayerTS/wb_player_ts_7.png", "WB Player TS 7");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbPlayerTS", "wb_player_ts_8(.*)\\.png", null,
                Set.of(), Set.of(), "wb_player_ts_8", "unitycues/numbers/wbPlayerTS/wb_player_ts_8.png", "WB Player TS 8");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbPlayerTS", "wb_player_ts_9(.*)\\.png", null,
                Set.of(), Set.of(), "wb_player_ts_9", "unitycues/numbers/wbPlayerTS/wb_player_ts_9.png", "WB Player TS 9");
        //endregion WB Player TS

        //region WB Tiers
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTiers", "wb_tier_0_(.*)\\.png", null,
                Set.of(), Set.of(), "wb_tier_0", "unitycues/numbers/wbTier/wb_tier_0.png", " WB Tier 0");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTiers", "wb_tier_1_(.*)\\.png", null,
                Set.of(), Set.of(), "wb_tier_1", "unitycues/numbers/wbTier/wb_tier_1.png", " WB Tier 1");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTiers", "wb_tier_2_(.*)\\.png", null,
                Set.of(), Set.of(), "wb_tier_2", "unitycues/numbers/wbTier/wb_tier_2.png", " WB Tier 2");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTiers", "wb_tier_3_(.*)\\.png", null,
                Set.of(), Set.of(), "wb_tier_3", "unitycues/numbers/wbTier/wb_tier_3.png", " WB Tier 3");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTiers", "wb_tier_4_(.*)\\.png", null,
                Set.of(), Set.of(), "wb_tier_4", "unitycues/numbers/wbTier/wb_tier_4.png", " WB Tier 4");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTiers", "wb_tier_5_(.*)\\.png", null,
                Set.of(), Set.of(), "wb_tier_5", "unitycues/numbers/wbTier/wb_tier_5.png", " WB Tier 5");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTiers", "wb_tier_6_(.*)\\.png", null,
                Set.of(), Set.of(), "wb_tier_6", "unitycues/numbers/wbTier/wb_tier_6.png", " WB Tier 6");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTiers", "wb_tier_7_(.*)\\.png", null,
                Set.of(), Set.of(), "wb_tier_7", "unitycues/numbers/wbTier/wb_tier_7.png", " WB Tier 7");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTiers", "wb_tier_8_(.*)\\.png", null,
                Set.of(), Set.of(), "wb_tier_8", "unitycues/numbers/wbTier/wb_tier_8.png", " WB Tier 8");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTiers", "wb_tier_9_(.*)\\.png", null,
                Set.of(), Set.of(), "wb_tier_9", "unitycues/numbers/wbTier/wb_tier_9.png", " jWB Tier 9");
        //endregion WT Tiers

        //region WB Tier Buttons
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTierButton", "wb_tier_button_0_(.*)\\.png", null,
                Set.of(), Set.of(), "wb_tier_button_0", "unitycues/numbers/wbTierButton/wb_tier_button_0.png", " WB Tier Button 0");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTierButton", "wb_tier_button_1_(.*)\\.png", null,
                Set.of(), Set.of(), "wb_tier_button_1", "unitycues/numbers/wbTierButton/wb_tier_button_1.png", " WB Tier Button 1");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTierButton", "wb_tier_button_2_(.*)\\.png", null,
                Set.of(), Set.of(), "wb_tier_button_2", "unitycues/numbers/wbTierButton/wb_tier_button_2.png", " WB Tier Button 2");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTierButton", "wb_tier_button_3_(.*)\\.png", null,
                Set.of(), Set.of(), "wb_tier_button_3", "unitycues/numbers/wbTierButton/wb_tier_button_3.png", " WB Tier Button 3");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTierButton", "wb_tier_button_4_(.*)\\.png", null,
                Set.of(), Set.of(), "wb_tier_button_4", "unitycues/numbers/wbTierButton/wb_tier_button_4.png", " WB Tier Button 4");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTierButton", "wb_tier_button_5_(.*)\\.png", null,
                Set.of(), Set.of(), "wb_tier_button_5", "unitycues/numbers/wbTierButton/wb_tier_button_5.png", " WB Tier Button 5");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTierButton", "wb_tier_button_6_(.*)\\.png", null,
                Set.of(), Set.of(), "wb_tier_button_6", "unitycues/numbers/wbTierButton/wb_tier_button_6.png", " WB Tier Button 6");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTierButton", "wb_tier_button_7_(.*)\\.png", null,
                Set.of(), Set.of(), "wb_tier_button_7", "unitycues/numbers/wbTierButton/wb_tier_button_7.png", " WB Tier Button 7");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTierButton", "wb_tier_button_8_(.*)\\.png", null,
                Set.of(), Set.of(), "wb_tier_button_8", "unitycues/numbers/wbTierButton/wb_tier_button_8.png", " WB Tier Button 8");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTierButton", "wb_tier_button_9_(.*)\\.png", null,
                Set.of(), Set.of(), "wb_tier_button_9", "unitycues/numbers/wbTierButton/wb_tier_button_9.png", " WB Tier Button 9");
        //endregion WT Tiers

        //region WB Total TS 16px
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTotalTS16", "wb_total_ts_16_0(.*)\\.png", null,
                Set.of(), Set.of(), "wb_total_ts_16_0", "unitycues/numbers/wbTotalTS16/wb_total_ts_16_0.png", "WB Total TS 16px 0");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTotalTS16", "wb_total_ts_16_1(.*)\\.png", null,
                Set.of(), Set.of(), "wb_total_ts_16_1", "unitycues/numbers/wbTotalTS16/wb_total_ts_16_1.png", "WB Total TS 16px 1");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTotalTS16", "wb_total_ts_16_2(.*)\\.png", null,
                Set.of(), Set.of(), "wb_total_ts_16_2", "unitycues/numbers/wbTotalTS16/wb_total_ts_16_2.png", "WB Total TS 16px 2");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTotalTS16", "wb_total_ts_16_3(.*)\\.png", null,
                Set.of(), Set.of(), "wb_total_ts_16_3", "unitycues/numbers/wbTotalTS16/wb_total_ts_16_3.png", "WB Total TS 16px 3");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTotalTS16", "wb_total_ts_16_4(.*)\\.png", null,
                Set.of(), Set.of(), "wb_total_ts_16_4", "unitycues/numbers/wbTotalTS16/wb_total_ts_16_4.png", "WB Total TS 16px 4");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTotalTS16", "wb_total_ts_16_5(.*)\\.png", null,
                Set.of(), Set.of(), "wb_total_ts_16_5", "unitycues/numbers/wbTotalTS16/wb_total_ts_16_5.png", "WB Total TS 16px 5");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTotalTS16", "wb_total_ts_16_6(.*)\\.png", null,
                Set.of(), Set.of(), "wb_total_ts_16_6", "unitycues/numbers/wbTotalTS16/wb_total_ts_16_6.png", "WB Total TS 16px 6");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTotalTS16", "wb_total_ts_16_7(.*)\\.png", null,
                Set.of(), Set.of(), "wb_total_ts_16_7", "unitycues/numbers/wbTotalTS16/wb_total_ts_16_7.png", "WB Total TS 16px 7");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTotalTS16", "wb_total_ts_16_8(.*)\\.png", null,
                Set.of(), Set.of(), "wb_total_ts_16_8", "unitycues/numbers/wbTotalTS16/wb_total_ts_16_8.png", "WB Total TS 16px 8");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTotalTS16", "wb_total_ts_16_9(.*)\\.png", null,
                Set.of(), Set.of(), "wb_total_ts_16_9", "unitycues/numbers/wbTotalTS16/wb_total_ts_16_9.png", "WB Total TS 16px 9");
        //endregion WB Total TS 16px

        //region WB Total TS 18px
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTotalTS18", "wb_total_ts_18_0(.*)\\.png", null,
                Set.of(), Set.of(), "wb_total_ts_18_0", "unitycues/numbers/wbTotalTS18/wb_total_ts_18_0.png", "WB Total TS 18px 0");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTotalTS18", "wb_total_ts_18_1(.*)\\.png", null,
                Set.of(), Set.of(), "wb_total_ts_18_1", "unitycues/numbers/wbTotalTS18/wb_total_ts_18_1.png", "WB Total TS 18px 1");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTotalTS18", "wb_total_ts_18_2(.*)\\.png", null,
                Set.of(), Set.of(), "wb_total_ts_18_2", "unitycues/numbers/wbTotalTS18/wb_total_ts_18_2.png", "WB Total TS 18px 2");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTotalTS18", "wb_total_ts_18_3(.*)\\.png", null,
                Set.of(), Set.of(), "wb_total_ts_18_3", "unitycues/numbers/wbTotalTS18/wb_total_ts_18_3.png", "WB Total TS 18px 3");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTotalTS18", "wb_total_ts_18_4(.*)\\.png", null,
                Set.of(), Set.of(), "wb_total_ts_18_4", "unitycues/numbers/wbTotalTS18/wb_total_ts_18_4.png", "WB Total TS 18px 4");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTotalTS18", "wb_total_ts_18_5(.*)\\.png", null,
                Set.of(), Set.of(), "wb_total_ts_18_5", "unitycues/numbers/wbTotalTS18/wb_total_ts_18_5.png", "WB Total TS 18px 5");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTotalTS18", "wb_total_ts_18_6(.*)\\.png", null,
                Set.of(), Set.of(), "wb_total_ts_18_6", "unitycues/numbers/wbTotalTS18/wb_total_ts_18_6.png", "WB Total TS 18px 6");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTotalTS18", "wb_total_ts_18_7(.*)\\.png", null,
                Set.of(), Set.of(), "wb_total_ts_18_7", "unitycues/numbers/wbTotalTS18/wb_total_ts_18_7.png", "WB Total TS 18px 7");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTotalTS18", "wb_total_ts_18_8(.*)\\.png", null,
                Set.of(), Set.of(), "wb_total_ts_18_8", "unitycues/numbers/wbTotalTS18/wb_total_ts_18_8.png", "WB Total TS 18px 8");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTotalTS18", "wb_total_ts_18_9(.*)\\.png", null,
                Set.of(), Set.of(), "wb_total_ts_18_9", "unitycues/numbers/wbTotalTS18/wb_total_ts_18_9.png", "WB Total TS 18px 9");
        //endregion WB Total TS 18px

        //region WB Total TS 20px
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTotalTS20", "wb_total_ts_20_0(.*)\\.png", null,
                Set.of(), Set.of(), "wb_total_ts_20_0", "unitycues/numbers/wbTotalTS20/wb_total_ts_20_0.png", "WB Total TS 20px 0");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTotalTS20", "wb_total_ts_20_1(.*)\\.png", null,
                Set.of(), Set.of(), "wb_total_ts_20_1", "unitycues/numbers/wbTotalTS20/wb_total_ts_20_1.png", "WB Total TS 20px 1");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTotalTS20", "wb_total_ts_20_2(.*)\\.png", null,
                Set.of(), Set.of(), "wb_total_ts_20_2", "unitycues/numbers/wbTotalTS20/wb_total_ts_20_2.png", "WB Total TS 20px 2");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTotalTS20", "wb_total_ts_20_3(.*)\\.png", null,
                Set.of(), Set.of(), "wb_total_ts_20_3", "unitycues/numbers/wbTotalTS20/wb_total_ts_20_3.png", "WB Total TS 20px 3");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTotalTS20", "wb_total_ts_20_4(.*)\\.png", null,
                Set.of(), Set.of(), "wb_total_ts_20_4", "unitycues/numbers/wbTotalTS20/wb_total_ts_20_4.png", "WB Total TS 20px 4");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTotalTS20", "wb_total_ts_20_5(.*)\\.png", null,
                Set.of(), Set.of(), "wb_total_ts_20_5", "unitycues/numbers/wbTotalTS20/wb_total_ts_20_5.png", "WB Total TS 20px 5");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTotalTS20", "wb_total_ts_20_6(.*)\\.png", null,
                Set.of(), Set.of(), "wb_total_ts_20_6", "unitycues/numbers/wbTotalTS20/wb_total_ts_20_6.png", "WB Total TS 20px 6");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTotalTS20", "wb_total_ts_20_7(.*)\\.png", null,
                Set.of(), Set.of(), "wb_total_ts_20_7", "unitycues/numbers/wbTotalTS20/wb_total_ts_20_7.png", "WB Total TS 20px 7");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTotalTS20", "wb_total_ts_20_8(.*)\\.png", null,
                Set.of(), Set.of(), "wb_total_ts_20_8", "unitycues/numbers/wbTotalTS20/wb_total_ts_20_8.png", "WB Total TS 20px 8");
        addCueLocatorByPattern(cueLocators, "cuebuilder/numbers/wbTotalTS20", "wb_total_ts_20_9(.*)\\.png", null,
                Set.of(), Set.of(), "wb_total_ts_20_9", "unitycues/numbers/wbTotalTS20/wb_total_ts_20_9.png", "WB Total TS 20px 9");
        //endregion WB Total TS 20px

        //endregion Numbers

        //region Raid
        addCueLocatorByPattern(cueLocators, "cuebuilder/raid", "raid_summon_(.*)\\.png", Bounds.fromWidthHeight(489, 362, 110, 31),
                Set.of(), Set.of(), "RaidSummon", "unitycues/raid/cueRaidSummon.png", "Raid Summon button");
        addCueLocatorByPattern(cueLocators, "cuebuilder/raid", "raid-diff(.*)\\.png", Bounds.fromWidthHeight(147, 222, 122, 27),
                Set.of(), Set.of(), "RaidNormal", "unitycues/raid/cueRaidNormal.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/raid", "raid-diff(.*)\\.png", Bounds.fromWidthHeight(340, 222, 122, 27),
                Set.of(), Set.of(), "RaidHard", "unitycues/raid/cueRaidHard.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/raid", "raid-diff(.*)\\.png", Bounds.fromWidthHeight(535, 222, 122, 27),
                Set.of(), Set.of(), "RaidHeroic", "unitycues/raid/cueRaidHeroic.png", "");
        //endregion

        //region Scroll Bars
        addCueLocatorByPattern(cueLocators, "cuebuilder/settings", "settings_01(.*)\\.png", Bounds.fromWidthHeight(614, 191, 18, 21),
                Set.of(), Set.of(), "ScrollerAtTop", "unitycues/scrollBars/cueScrollerAtTop.png", "");

        addCueLocatorByPattern(cueLocators, "cuebuilder/settings", "scroller_23(.*)\\.png", Bounds.fromWidthHeight(616, 368, 14, 24),
                Set.of(), Set.of(), "ScrollerAtBottomSettings", "unitycues/scrollBars/cueScrollerAtBottomSettings.png", "Cue used when a scroll bar is at the bottom position in the settings menu.");

        addCueLocatorByPattern(cueLocators, "cuebuilder/scrollBars", "dropdown_up-(.*)\\.png", null,
                Set.of(), Set.of(), "DropDownUp", "unitycues/scrollBars/cueDropDownUp.png", "The arrow pointing up in scroll bars.");
        addCueLocatorByPattern(cueLocators, "cuebuilder/scrollBars", "dropdown_down-(.*)\\.png", null,
                Set.of(), Set.of(), "DropDownDown", "unitycues/scrollBars/cueDropDownDown.png", "The arrow pointing down in scroll bars.");

        addCueLocatorByPattern(cueLocators, "cuebuilder/settings", "settings_01(.*)\\.png", Bounds.fromWidthHeight(614, 216, 18, 14),
                Set.of(), Set.of(), "SettingsScrollerTopPos", "unitycues/scrollBars/cueSettingsScrollerTopPos.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/settings", "settings_02(.*)\\.png", Bounds.fromWidthHeight(614, 216, 18, 14),
                Set.of(), Set.of(), "SettingsScrollerTopPos", "unitycues/scrollBars/cueSettingsScrollerTopPos.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/scrollBars", "strip_01(.*)\\.png", Bounds.fromWidthHeight(532, 147, 18, 14),
                Set.of(), Set.of(), "StripScrollerTopPos", "unitycues/scrollBars/cueStripScrollerTopPos.png", "");
        //endregion

        //region Settings
        addCueLocatorByPattern(cueLocators, "cuebuilder/settings", "settings_01(.*)\\.png", Bounds.fromWidthHeight(354, 188, 9, 31),
                Set.of(), Set.of(), "settingsMusic", "unitycues/settings/cueSettingsMusic.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/settings", "settings_01(.*)\\.png", Bounds.fromWidthHeight(354, 254, 9, 31),
                Set.of(), Set.of(), "settingsSound", "unitycues/settings/cueSettingsSound.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/settings", "scroller_03_(.*)\\.png", Bounds.fromWidthHeight(169, 342, 217, 41),
                Set.of(), Set.of(), "settingsNotification", "unitycues/settings/cueSettingsNotification.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/settings", "scroller_07_(.*)\\.png", Bounds.fromWidthHeight(170, 309, 333, 45),
                Set.of(), Set.of(), "settingsWBReq", "unitycues/settings/cueSettingsWBReq.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/settings", "scroller_10_(.*)(.*)\\.png", Bounds.fromWidthHeight(170, 256, 267, 39),
                Set.of(), Set.of(), "settingsReducedFX", "unitycues/settings/cueSettingsReducedFX.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/settings", "scroller_10_(.*)\\.png", Bounds.fromWidthHeight(170, 297, 208, 43),
                Set.of(), Set.of(), "settingsBattleTXT", "unitycues/settings/cueSettingsBattleTXT.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/settings", "scroller_11_(.*)\\.png", Bounds.fromWidthHeight(170, 326, 183, 39),
                Set.of(), Set.of(), "settingsAnimations", "unitycues/settings/cueSettingsAnimations.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/settings", "scroller_20_(.*)\\.png", Bounds.fromWidthHeight(170, 334, 295, 40),
                Set.of(), Set.of(), "settingsMerchants", "unitycues/settings/cueSettingsMerchants.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/settings", "scroller_23_(.*)\\.png", Bounds.fromWidthHeight(170, 303, 315, 40),
                Set.of(), Set.of(), "settingsTips", "unitycues/settings/cueSettingsTips.png", "Tips when defeated setting.");
        //endregion settings

        //region T/G
        addCueLocatorByPattern(cueLocators, "cuebuilder/tierGauntlet", "tokens-bar(.*)\\.png", Bounds.fromWidthHeight(325, 54, 36, 30),
                Set.of(), Set.of(), "TokenBar", "unitycues/tierGauntlet/cueTokenBar.png", "");

        final Set<Bounds> tgDiffTransp = Set.of(Bounds.fromWidthHeight(481, 383, 106, 31), Bounds.fromWidthHeight(478, 386, 112, 25));
        addCueLocatorByPattern(cueLocators, "cuebuilder/tierGauntlet", "tg_window_(.*)\\.png", Bounds.fromWidthHeight(471, 354, 162, 69),
                Set.of(), tgDiffTransp, "Difficulty", "unitycues/tierGauntlet/cueDifficulty.png", "T/G Difficulty combo box");
        addCueLocatorByPattern(cueLocators, "cuebuilder/tierGauntlet", "tg_difficulty_disabled_(.*)\\.png", Bounds.fromWidthHeight(468, 352, 167, 74),
                Set.of(), tgDiffTransp, "DifficultyDisabled", "unitycues/tierGauntlet/cueDifficultyDisabled.png", "Greyed out TG Difficulty drop down is");
        addCueLocatorByPattern(cueLocators, "cuebuilder/tierGauntlet", "tg_window_(.*)\\.png", Bounds.fromWidthHeight(587, 377, 45, 43),
                Set.of(), tgDiffTransp, "SelectDifficulty", "unitycues/tierGauntlet/cueSelectDifficulty.png", "Difficulty arrow pointing down");

        final Set<Bounds> tgCostSelectTransp = Set.of(Bounds.fromWidthHeight(298, 67, 207, 41),
                Bounds.fromWidthHeight(292, 70, 219, 12), Bounds.fromWidthHeight(292, 94, 219, 12));
        addCueLocatorByPattern(cueLocators, "cuebuilder/tierGauntlet", "tg_select_cost_(.*)\\.png", Bounds.fromWidthHeight(280, 62, 243, 51),
                Set.of(), tgCostSelectTransp, "CostDropDown", "unitycues/tierGauntlet/cueCostDropDown.png", "Cost selection window");

        addCueLocatorByPattern(cueLocators, "cuebuilder/tierGauntlet", "tg_difficulty_range_(.*)\\.png", Bounds.fromWidthHeight(292, 61, 219, 54),
                Set.of(), Set.of(), "DifficultyDropDown", "unitycues/tierGauntlet/cueDifficultyDropDown.png", "Difficulty text on top of difficulty selection.");
        //endregion

        //region Treasure Chest
        addCueLocatorByPattern(cueLocators, "cuebuilder/treasureChest", "treasure(.*)\\.png", Bounds.fromWidthHeight(416, 375, 127, 37),
                Set.of(), Set.of(), "Decline", "unitycues/treasureChest/cueDecline.png", "");
        //endregion

        //region WB
        // Misc buttons
        addCueLocatorByPattern(cueLocators, "cuebuilder/worldBoss", "xeal_bar_(.*)\\.png", Bounds.fromWidthHeight(321, 37, 40, 35),
                Set.of(), Set.of(), "WorldBossPopup", "unitycues/worldBoss/cueWorldBossPopup.png", "Xeal bar popup");
        addCueLocatorByPattern(cueLocators, "cuebuilder/worldBoss", "xeal_bar_(.*)\\.png", Bounds.fromWidthHeight(449, 447, 106, 31),
                Set.of(), Set.of(), "DarkBlueSummon", "unitycues/worldBoss/cueDarkBlueSummon.png", "WB Main menu summon button");
        addCueLocatorByPattern(cueLocators, "cuebuilder/worldBoss", "xeal_bar_(.*)\\.png", Bounds.fromWidthHeight(290, 87, 224, 32),
                Set.of(), Set.of(), "WorldBossTitle", "unitycues/worldBoss/cueWorldBossTitle.png", "WB Title in Main Menu");
        addCueLocatorByPattern(cueLocators, "cuebuilder/worldBoss", "wb_2_(.*)\\.png", Bounds.fromWidthHeight(490, 361, 105, 35),
                Set.of(), Set.of(), "LargeDarkBlueSummon", "unitycues/worldBoss/cueLargeDarkBlueSummon.png", "Big Blue Summon button in WB Screen 2");
        addCueLocatorByPattern(cueLocators, "cuebuilder/worldBoss", "wb_3_(.*)\\.png", Bounds.fromWidthHeight(322, 327, 42, 46),
                Set.of(), Set.of(), "WBIsPrivate", "unitycues/worldBoss/cueWBIsPrivate.png", "Private Flag for World Boss");
        addCueLocatorByPattern(cueLocators, "cuebuilder/worldBoss", "wb_difficulty_(.*)\\.png", Bounds.fromWidthHeight(428, 390, 104, 27),
                Set.of(), Set.of(), "SmallDarkBlueSummon", "unitycues/worldBoss/cueSmallDarkBlueSummon.png", "Dark blue WB Summon button");
        addCueLocatorByPattern(cueLocators, "cuebuilder/worldBoss", "wb_no_xeals_(.*)\\.png", Bounds.fromWidthHeight(282, 229, 239, 71),
                Set.of(), Set.of(), "NotEnoughXeals", "unitycues/worldBoss/cueNotEnoughXeals.png", "Not enough xeals popup");
        addCueLocatorByPattern(cueLocators, "cuebuilder/worldBoss", "wb_lobby_unready_(.*)\\.png", null,
                Set.of(), Set.of(), "Unready", "unitycues/worldBoss/cueWorldBossUnready.png", "Red X in WB Lobby");
        addCueLocatorByPattern(cueLocators, "cuebuilder/worldBoss", "wb_invite_(.*)\\.png", Bounds.fromWidthHeight(347, 340, 96, 23),
                Set.of(), Set.of(), "Invite", "unitycues/worldBoss/cueDarkBlueInvite.png", "Lobby member invite button");
        addCueLocatorByPattern(cueLocators, "cuebuilder/worldBoss", "wb_invite_(.*)\\.png", Bounds.fromWidthHeight(337, 457, 87, 28),
                Set.of(), Set.of(), "DarkBlueStart", "unitycues/worldBoss/cueDarkBlueStart.png", "WB Dark blue start button");
        addCueLocatorByPattern(cueLocators, "cuebuilder/worldBoss", "wb_kick_(.*)\\.png", Bounds.fromWidthHeight(423, 233, 22, 23),
                Set.of(), Set.of(), "WorldBossPlayerKick", "unitycues/worldBoss/cueWorldBossKick.png", "Lobby member kick button");
        addCueLocatorByPattern(cueLocators, "cuebuilder/worldBoss", "wb_summon_(.*)\\.png", Bounds.fromWidthHeight(342, 117, 115, 26),
                Set.of(), Set.of(), "WorldBossSummonTitle", "unitycues/worldBoss/cueWorldBossSummonTitle.png", "WB Summon Title");
        addCueLocatorByPattern(cueLocators, "cuebuilder/worldBoss", "wb_lobby_kick_popup_(.*)\\.png", Bounds.fromWidthHeight(359, 140, 84, 35),
                Set.of(), Set.of(), "WorldBossPopupKick", "unitycues/worldBoss/cueWorldBossPopupKick.png", "WB Player Kick confirmation");

        // Tier drop down
        Set<Color> TierDropDownColors = Set.of(new Color(0, 0, 0), new Color(70, 105, 134), new Color(124, 179, 221), new Color(112, 169, 214),
                new Color(70, 123, 161), new Color(86, 145, 190), new Color(116, 175, 223), new Color(115, 175, 223), new Color(133, 191, 235),
                new Color(112, 172, 222), new Color(113, 174, 222), new Color(95, 157, 207), new Color(173, 222, 255), new Color(99, 147, 183),
                new Color(87, 148, 195), new Color(59, 98, 131), new Color(108, 166, 212), new Color(166, 219, 255), new Color(99, 164, 218),
                new Color(84, 135, 174), new Color(111, 168, 213), new Color(110, 167, 213), new Color(101, 149, 184), new Color(102, 150, 185));
        addCueLocatorByPattern(cueLocators, "cuebuilder/worldBoss", "wb_3_(.*)\\.png", Bounds.fromWidthHeight(304, 199, 194, 42),
                TierDropDownColors, Set.of(), "WorldBossTierDropDown", "unitycues/worldBoss/cueWorldBossTierDropDown.png", "Drop Down for Tier detection");

        // Currently selected difficulty
        addCueLocatorByPattern(cueLocators, "cuebuilder/worldBoss", "wb_difficulty_1_(.*)\\.png", Bounds.fromWidthHeight(336, 287, 92, 20),
                Set.of(new Color(147, 158, 244)), Set.of(), "WorldBossDifficultyNormal", "unitycues/worldBoss/cueWorldBossDifficultyNormal.png", "WB Normal difficulty selected");
        addCueLocatorByPattern(cueLocators, "cuebuilder/worldBoss", "wb_difficulty_2_(.*)\\.png", Bounds.fromWidthHeight(336, 287, 92, 20),
                Set.of(new Color(255, 128, 125)), Set.of(), "WorldBossDifficultyHard", "unitycues/worldBoss/cueWorldBossDifficultyHard.png", "WB Hard difficulty selected");
        addCueLocatorByPattern(cueLocators, "cuebuilder/worldBoss", "wb_difficulty_3_(.*)\\.png", Bounds.fromWidthHeight(336, 287, 92, 20),
                Set.of(new Color(255, 255, 0)), Set.of(), "WorldBossDifficultyHeroic", "unitycues/worldBoss/cueWorldBossDifficultyHeroic.png", "WB Heroic difficulty selected");

        // New difficulty selection
        addCueLocatorByPattern(cueLocators, "cuebuilder/worldBoss", "wb_select_difficulty_(.*)\\.png", Bounds.fromWidthHeight(336, 146, 102, 24),
                Set.of(new Color(147, 158, 244)), Set.of(), "cueWBSelectNormal", "unitycues/worldBoss/cueWBSelectNormal.png", "WB Normal difficulty selection");
        addCueLocatorByPattern(cueLocators, "cuebuilder/worldBoss", "wb_select_difficulty_(.*)\\.png", Bounds.fromWidthHeight(355, 207, 64, 23),
                Set.of(new Color(255, 128, 125)), Set.of(), "cueWBSelectHard", "unitycues/worldBoss/cueWBSelectHard.png", "WB Hard difficulty selection");
        addCueLocatorByPattern(cueLocators, "cuebuilder/worldBoss", "wb_select_difficulty_(.*)\\.png", Bounds.fromWidthHeight(346, 268, 85, 21),
                Set.of(new Color(255, 255, 0)), Set.of(), "cueWBSelectHeroic", "unitycues/worldBoss/cueWBSelectHeroic.png", "WB Heroic difficulty selection");
        //endregion

        //region Weekly Rewards
        addCueLocatorByPattern(cueLocators, "cuebuilder/weeklyRewards", "pvp(.*)\\.png", Bounds.fromWidthHeight(361, 120, 75, 54),
                Set.of(), Set.of(), "PVP_Rewards", "unitycues/weeklyRewards/pvp.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/weeklyRewards", "trials(.*)\\.png", Bounds.fromWidthHeight(365, 68, 109, 21),
                Set.of(), Set.of(), "Trials_Rewards", "unitycues/weeklyRewards/trials.png", "Trial Reward popup");
        addCueLocatorByPattern(cueLocators, "cuebuilder/weeklyRewards", "gvg(.*)\\.png", Bounds.fromWidthHeight(336, 120, 123, 54),
                Set.of(), Set.of(), "GVG_Rewards", "unitycues/weeklyRewards/gvg.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/weeklyRewards", "invasion(.*)\\.png", Bounds.fromWidthHeight(315, 129, 165, 35),
                Set.of(), Set.of(), "Invasion_Rewards", "unitycues/weeklyRewards/invasion.png", "");
        addCueLocatorByPattern(cueLocators, "cuebuilder/weeklyRewards", "fishing_bait_(.*)\\.png", Bounds.fromWidthHeight(395, 230, 60, 33),
                Set.of(), Set.of(), "Fishing_Bait", "unitycues/weeklyRewards/fishing_bait.png", "Green fishing bait");
        addCueLocatorByPattern(cueLocators, "cuebuilder/weeklyRewards", "expedition_large_(.*)\\.png", Bounds.fromWidthHeight(309, 68, 181, 21),
                Set.of(), Set.of(), "Expedition_RewardsLarge", "unitycues/weeklyRewards/expedition-large.png", "Large Expedition popup title");
        addCueLocatorByPattern(cueLocators, "cuebuilder/weeklyRewards", "expedition_small_(.*)\\.png", Bounds.fromWidthHeight(322, 137, 155, 18),
                Set.of(), Set.of(), "Expedition_RewardsSmall", "unitycues/weeklyRewards/expedition-small.png", "Small Expedition popup title");
        //endregion


        // We group cueLocators by destination file so that we can run the generation in parallel without errors
        for (CueLocator cueLoc : cueLocators) {
            if (!cueLocatorsByDestFile.containsKey(cueLoc.destinationCuePath))
                cueLocatorsByDestFile.put(cueLoc.destinationCuePath, new ArrayList<>());

            cueLocatorsByDestFile.get(cueLoc.destinationCuePath).add(cueLoc);
        }

        // We reset the cues when we build them, so we do not have any merge issue
        for (String cuePath : cueLocatorsByDestFile.keySet()) {
            cuePath = "src/main/resources/" + cuePath;
            File cueFile = new File(cuePath);

            if (cueFile.exists()) {
                if (!cueFile.delete()) {
                    System.out.println("It was impossible to delete file \"" + cueFile.getAbsolutePath() + "\". Cue generation interrupted");
                    return;
                }
            } else {
                System.out.println("Destination file not found: " + cuePath);
            }
        }

        // We generate all the Cues and save the destination path in the dedicated HashSet
        cueLocatorsByDestFile.entrySet().parallelStream().forEach(
                (clList) -> clList.getValue().forEach(
                        (cl) -> {
                            usedFilePaths.add(cl.containingScreenShotPath);
                            cl.generateCue();
                        }
                )
        );

        // We print out the unused cue builder files
        if (printUnused) {
            try {
                Stream<Path> fileWalker = Files.walk(Paths.get("cuebuilder/"));

                fileWalker
                        .filter(p -> {
                            boolean isFile = Files.isRegularFile(p);
                            String fileName = p.toString().toLowerCase();

                            boolean isPNG = fileName.endsWith(".png");
                            return isFile && isPNG;
                        })
                        .forEach(p -> {
                            String relativePath = p.toFile().getPath();

                            // We make sure that the correct path separator is used
                            if (relativePath.contains("\\")) {
                                relativePath = relativePath.replace("\\", "/");
                            }

                            if (!usedFilePaths.contains(relativePath)) {
                                String msg = String.format("%s %s used to create Cues",
                                        relativePath, usedFilePaths.contains(relativePath) ? "is" : "is not");
                                System.out.println(msg);
                            }

                        });

                fileWalker.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    static void manageRaidBar() {
        // Currently known colors
        Set<Color> raidColors = new HashSet<>(
                Set.of(new Color(199, 79, 175), new Color(199, 79, 176),
                        new Color(147, 47, 118)));

        // Path to files with raid bar
        File raidPath = new File("barbuilder/raid");

        BufferedImage raidPopUp;
        try {
            raidPopUp = ImageIO.read(new File("src/main/resources/unitycues/raid/cueRaidPopup.png"));
        } catch (IOException e) {
            System.out.println("Errow while reading raid pop-up");
            e.printStackTrace();
            return;
        }

        // Loop on all the files
        ImageFilter raidBarFilter = new ImageFilter("raid-bar(.*)\\.png");
        String[] raidBarImages = raidPath.list(raidBarFilter);

        if (raidBarImages != null) {
            for (String raidImgFile : raidBarImages) {
                File raidBarFile = new File("barbuilder/raid/" + raidImgFile);

                //noinspection DuplicatedCode
                if (!raidBarFile.exists() || raidBarFile.isDirectory()) {
                    System.out.println("File " + raidBarFile.getAbsolutePath() + " is not a valid bar file");
                    continue;
                }

                BufferedImage raidImg;
                try {
                    raidImg = ImageIO.read(raidBarFile);
                } catch (IOException e) {
                    System.out.println("Exception while loading image" + raidBarFile.getAbsolutePath());
                    e.printStackTrace();
                    continue;
                }

                raidColors.addAll(ImageHelper.getImgColors(raidImg.getSubimage(361, 62, 80, 1)));
                System.out.println("Found colors for Raid:");
                ImageHelper.printColors(raidColors);

                MarvinSegment seg = FindSubimage.findImage(raidImg, raidPopUp, 0, 0, 0, 0);
                // As images can have different shat totals we use 100 so we get the percentage
                int shard = AdventureThread.readResourceBarPercentage(seg, 100, Misc.BarOffsets.RAID.x, Misc.BarOffsets.RAID.y, raidColors, raidImg);
                System.out.println("Raid bar is " + shard + "% full for image " + raidBarFile.getAbsolutePath());
            }
        }

    }

    static void manageEnergyBar() {
        Set<Color> energyColors = new HashSet<>();
        Set<Color> blackColors = new HashSet<>(Set.of(new Color(50, 51, 52)));

        // Path to files with raid bar
        File dungPath = new File("barbuilder/dungeon");

        BufferedImage energyPopUp;
        try {
            energyPopUp = ImageIO.read(new File("src/main/resources/unitycues/dungeon/cueEnergyBar.png"));
        } catch (IOException e) {
            System.out.println("Errow while reading energy pop-up");
            e.printStackTrace();
            return;
        }

        // Loop on all the files
        ImageFilter energyFilter = new ImageFilter("energy-bar(.*)\\.png");
        String[] energyBars = dungPath.list(energyFilter);

        if (energyBars != null) {
            for (String dungImgFile : energyBars) {
                File dungBarFile = new File("barbuilder/dungeon/" + dungImgFile);

                //noinspection DuplicatedCode
                if (!dungBarFile.exists() || dungBarFile.isDirectory()) {
                    System.out.println("File " + dungBarFile.getAbsolutePath() + " is not a valid bar file");
                    continue;
                }

                BufferedImage dungImg;
                try {
                    dungImg = ImageIO.read(dungBarFile);
                } catch (IOException e) {
                    System.out.println("Exception while loading image" + dungBarFile.getAbsolutePath());
                    e.printStackTrace();
                    continue;
                }

                ImageHelper.getImgColors(dungImg.getSubimage(438, 31, 80, 1)).forEach((col) -> {
                    if (!blackColors.contains(col)) energyColors.add(col);
                });

                System.out.println("Found colors for Energy:");
                ImageHelper.printColors(energyColors);

                MarvinSegment seg = FindSubimage.findImage(dungImg, energyPopUp, 0, 0, 0, 0);
                // As images can have different shat totals we use 100 so we get the percentage
                int energy = AdventureThread.readResourceBarPercentage(seg, 100, Misc.BarOffsets.DUNGEON.x, Misc.BarOffsets.DUNGEON.y, energyColors, dungImg);
                System.out.println("Energy bar is " + energy + "% full for image " + dungBarFile.getAbsolutePath());
            }
        }
    }

    static void manageTokenBar() {
        Set<Color> tokenColors = new HashSet<>();
        Set<Color> blackColors = new HashSet<>(Set.of(new Color(50, 51, 52)));

        // Path to files with raid bar
        File tokenPath = new File("barbuilder/token");

        BufferedImage tokenPopUp;
        try {
            tokenPopUp = ImageIO.read(new File("src/main/resources/unitycues/tierGauntlet/cueTokenBar.png"));
        } catch (IOException e) {
            System.out.println("Errow while reading token pop-up");
            e.printStackTrace();
            return;
        }

        // Loop on all the files
        ImageFilter tokenFilter = new ImageFilter("token-bar(.*)\\.png");
        String[] tokenBars = tokenPath.list(tokenFilter);

        if (tokenBars != null) {
            for (String TGImgFile : tokenBars) {
                File TGBarFile = new File("barbuilder/token/" + TGImgFile);

                //noinspection DuplicatedCode
                if (!TGBarFile.exists() || TGBarFile.isDirectory()) {
                    System.out.println("File " + TGBarFile.getAbsolutePath() + " is not a valid bar file");
                    continue;
                }

                BufferedImage dungImg;
                try {
                    dungImg = ImageIO.read(TGBarFile);
                } catch (IOException e) {
                    System.out.println("Exception while loading image" + TGBarFile.getAbsolutePath());
                    e.printStackTrace();
                    continue;
                }

                ImageHelper.getImgColors(dungImg.getSubimage(361, 77, 80, 1)).forEach((col) -> {
                    if (!blackColors.contains(col)) tokenColors.add(col);
                });

                System.out.println("Found colors for Energy:");
                ImageHelper.printColors(tokenColors);

                MarvinSegment seg = FindSubimage.findImage(dungImg, tokenPopUp, 0, 0, 0, 0);
                // As images can have different shat totals we use 100 so we get the percentage
                int energy = AdventureThread.readResourceBarPercentage(seg, 100, Misc.BarOffsets.TG.x, Misc.BarOffsets.TG.y, tokenColors, dungImg);
                System.out.println("Energy bar is " + energy + "% full for image " + TGBarFile.getAbsolutePath());
            }
        }
    }

    static void manageXealBar() {
        Set<Color> xealColors = new HashSet<>();
        Set<Color> blackColors = new HashSet<>(Set.of(new Color(50, 51, 52), new Color(8, 10, 11)));

        // Path to files with raid bar
        File xealPath = new File("barbuilder/xeal");

        BufferedImage xealPopUp;
        try {
            xealPopUp = ImageIO.read(new File("src/main/resources/unitycues/worldBoss/cueWorldBossPopup.png"));
        } catch (IOException e) {
            System.out.println("Errow while reading xeal pop-up");
            e.printStackTrace();
            return;
        }

        // Loop on all the files
        ImageFilter xealFilter = new ImageFilter("xeal_bar_(.*)\\.png");
        String[] xealBars = xealPath.list(xealFilter);

        if (xealBars != null) {
            for (String XealImgFile : xealBars) {
                File XealBarFile = new File("barbuilder/xeal/" + XealImgFile);

                //noinspection DuplicatedCode
                if (!XealBarFile.exists() || XealBarFile.isDirectory()) {
                    System.out.println("File " + XealBarFile.getAbsolutePath() + " is not a valid bar file");
                    continue;
                }

                BufferedImage dungImg;
                try {
                    dungImg = ImageIO.read(XealBarFile);
                } catch (IOException e) {
                    System.out.println("Exception while loading image" + XealBarFile.getAbsolutePath());
                    e.printStackTrace();
                    continue;
                }

                ImageHelper.getImgColors(dungImg.getSubimage(361, 63, 80, 1)).forEach((col) -> {
                    if (!blackColors.contains(col)) xealColors.add(col);
                });

                System.out.println("Found colors for Xeals:");
                ImageHelper.printColors(xealColors);

                MarvinSegment seg = FindSubimage.findImage(dungImg, xealPopUp, 0, 0, 0, 0);
                // As images can have different shat totals we use 100 so we get the percentage
                int xeal = AdventureThread.readResourceBarPercentage(seg, 100, Misc.BarOffsets.WB.x, Misc.BarOffsets.WB.y, xealColors, dungImg);
                System.out.println("Xeal bar is " + xeal + "% full for image " + XealBarFile.getAbsolutePath());
            }
        }

        // We calculate colors for the Tier DropDown Bar
        BufferedImage tiedDropDown;
        try {
            tiedDropDown = ImageIO.read(new File("barbuilder/xeal/cueWorldBossTierDropDown.png"));
            Set<Color> DropDownColors = ImageHelper.getImgColors(tiedDropDown);
            System.out.println("Found colors for Tier Drop Down:");
            ImageHelper.printColors(DropDownColors);

        } catch (IOException e) {
            System.out.println("Errow while reading xeal pop-up");
            e.printStackTrace();
        }
    }

    static void manageBadgeBar() {
        Set<Color> badgeColors = new HashSet<>();
        Set<Color> blackColors = new HashSet<>(Set.of(new Color(50, 51, 52), new Color(8, 10, 11)));

        // Path to files with raid bar
        File badgePath = new File("barbuilder/badge");

        BufferedImage badgePopUp;
        try {
            badgePopUp = ImageIO.read(new File("src/main/resources/unitycues/common/cueBadgeBar.png"));
        } catch (IOException e) {
            System.out.println("Errow while reading badge pop-up");
            e.printStackTrace();
            return;
        }

        // Loop on all the files
        ImageFilter badgeFilter = new ImageFilter("gvg-main_(.*)\\.png");
        String[] badgeBars = badgePath.list(badgeFilter);

        if (badgeBars != null) {
            for (String BadgeImgFile : badgeBars) {
                File BadgeBarFile = new File("barbuilder/badge/" + BadgeImgFile);

                //noinspection DuplicatedCode
                if (!BadgeBarFile.exists() || BadgeBarFile.isDirectory()) {
                    System.out.println("File " + BadgeBarFile.getAbsolutePath() + " is not a valid bar file");
                    continue;
                }

                BufferedImage dungImg;
                try {
                    dungImg = ImageIO.read(BadgeBarFile);
                } catch (IOException e) {
                    System.out.println("Exception while loading image" + BadgeBarFile.getAbsolutePath());
                    e.printStackTrace();
                    continue;
                }

                ImageHelper.getImgColors(dungImg.getSubimage(361, 61, 80, 1)).forEach((col) -> {
                    if (!blackColors.contains(col)) badgeColors.add(col);
                });

                System.out.println("Found colors for Badges:");
                ImageHelper.printColors(badgeColors);

                MarvinSegment seg = FindSubimage.findImage(dungImg, badgePopUp, 0, 0, 0, 0);
                // As images can have different shat totals we use 100 so we get the percentage
                int badge = AdventureThread.readResourceBarPercentage(seg, 100, Misc.BarOffsets.Badge.x, Misc.BarOffsets.Badge.y, badgeColors, dungImg);
                System.out.println("Badge bar is " + badge + "% full for image " + BadgeBarFile.getAbsolutePath());
            }
        }
    }

    static void manageTicketBar() {
        Set<Color> ticketColors = new HashSet<>();
        Set<Color> blackColors = new HashSet<>(Set.of(new Color(50, 51, 52), new Color(8, 10, 11)));

        // Path to files with pvp bar
        File ticketPath = new File("barbuilder/ticket");

        BufferedImage ticketPopUp;
        try {
            ticketPopUp = ImageIO.read(new File("src/main/resources/unitycues/pvp/cueTicketBar.png"));
        } catch (IOException e) {
            System.out.println("Errow while reading ticket pop-up");
            e.printStackTrace();
            return;
        }

        // Loop on all the files
        ImageFilter ticketFilter = new ImageFilter("ticket_bar_(.*)\\.png");
        String[] ticketBars = ticketPath.list(ticketFilter);

        if (ticketBars != null) {
            for (String TicketImgFile : ticketBars) {
                File TicketBarFile = new File("barbuilder/ticket/" + TicketImgFile);

                //noinspection DuplicatedCode
                if (!TicketBarFile.exists() || TicketBarFile.isDirectory()) {
                    System.out.println("File " + TicketBarFile.getAbsolutePath() + " is not a valid bar file");
                    continue;
                }

                BufferedImage ticketBarImg;
                try {
                    ticketBarImg = ImageIO.read(TicketBarFile);
                } catch (IOException e) {
                    System.out.println("Exception while loading image" + TicketBarFile.getAbsolutePath());
                    e.printStackTrace();
                    continue;
                }

                ImageHelper.getImgColors(ticketBarImg.getSubimage(590, 15, 80, 1)).forEach((col) -> {
                    if (!blackColors.contains(col)) ticketColors.add(col);
                });

                System.out.println("Found colors for Tickets:");
                ImageHelper.printColors(ticketColors);

                MarvinSegment seg = FindSubimage.findImage(ticketBarImg, ticketPopUp, 0, 0, 0, 0);
                // As images can have different shat totals we use 100 so we get the percentage
                int ticket = AdventureThread.readResourceBarPercentage(seg, 100, Misc.BarOffsets.PVP.x, Misc.BarOffsets.PVP.y, ticketColors, ticketBarImg);
                System.out.println("Ticket bar is " + ticket + "% full for image " + TicketBarFile.getAbsolutePath());
            }
        }
    }

    public static void main(String[] args) {
        boolean printUnused = false;

        for (String arg : args) {
            switch (arg) {
                case "pu", "-pu", "--print-unused" -> printUnused = true;
            }
        }

        manageCueFiles(printUnused);
        System.out.println("====== Raid bar ======");
        manageRaidBar();
        System.out.println("====== Dung bar ======");
        manageEnergyBar();
        System.out.println("====== Tokn bar ======");
        manageTokenBar();
        System.out.println("====== Xeal bar ======");
        manageXealBar();
        System.out.println("====== Badge bar ======");
        manageBadgeBar();
        System.out.println("====== Ticket bar ======");
        manageTicketBar();
    }
}
