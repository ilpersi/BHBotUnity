package com.github.ilpersi.BHBot;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

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
         * The internal name of the destination Cue
         */
        String destinationCueName;
        /**
         * The path of the destination Cue
         */
        String destinationCuePath;
        /**
         * Will the new cue merged to the existing one
         */
        boolean merge;

        public CueLocator(String containingScreenShotPath, Bounds cuePosition, Set<Color> colorWhiteList, String destinationCueName, String destinationCuePath, boolean merge) {
            this.containingScreenShotPath = containingScreenShotPath;
            this.cuePosition = cuePosition;
            this.colorWhiteList = colorWhiteList;
            this.destinationCueName = destinationCueName;
            this.destinationCuePath = destinationCuePath;
            this.merge = merge;
        }

        /**
         * This method will take care of extracting the Cue from the screenshot and save it to the destination path
         */
        void generateCue() {
            // File that contains the Cue to be extracted
            File screenShotFile = new File(this.containingScreenShotPath);
            if (!screenShotFile.exists() || screenShotFile.isDirectory() ) {
                System.out.println("Origin screenshot is not a file or it does not exist: " + screenShotFile.getAbsolutePath());
                return;
            }else if (!screenShotFile.getName().toLowerCase().endsWith(".png")) {
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

            // We create the destination Cue
            BufferedImage destCueImg = containingScreenshotImg.getSubimage(this.cuePosition.x1, this.cuePosition.y1, this.cuePosition.width, this.cuePosition.height);

            // If a white list is specified we only save the white listed pixel colors and we set the others as transparent
            if (this.colorWhiteList.size() > 0 ) {
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
                    System.out.println("Error while loading existing Cue image");
                    e.printStackTrace();
                    return;
                }

                if (destCueImg.getHeight() == existingCueImg.getHeight() && destCueImg.getHeight() == existingCueImg.getHeight()) {
                    BufferedImage mergedCueImg = CueCompare.pixelCompare(destCueImg, existingCueImg);

                    // If the merge was successful we override the destination Cue
                    if (mergedCueImg != null) destCueImg = mergedCueImg;
                } else {
                    System.out.println("It was impossible to merge '" + this.destinationCueName + "' from " + this.containingScreenShotPath + " due to different cue dimensions.");
                }
            }

            try {
                ImageIO.write(destCueImg, "png", destinationCueFile);
            } catch (IOException e) {
                System.out.println("Error while writing destination Cue image");
                e.printStackTrace();
                return;
            }

            MarvinSegment seg = MarvinSegment.fromFiles(screenShotFile, destinationCueFile);

            if (seg != null) {
                Bounds suggestedBounds = Bounds.fromMarvinSegment(seg, null);

                String boundsStr = suggestedBounds.getJavaCode(false, true).replace(";", "");

                String cueManager = "addCue(\"" + this.destinationCueName + "\", \"" + this.destinationCuePath + "\", " + boundsStr + "); // " + this.containingScreenShotPath;

                System.out.println(cueManager);
            }
        }

    }

    static void addCueLocatorByPattern(List<CueLocator> cueLocators, String containingPath, String PNGPattern, Bounds cuePosition, Set<Color> colorWhiteList,
                                       String destinationCueName, String destinationCuePath) {
        File containingPathFile = new File(containingPath);
        if (!containingPathFile.exists() || !containingPathFile.isDirectory()) {
            System.out.println("Invalid containing path: " + containingPath);
            return;
        }

        if (!containingPath.endsWith("/")) containingPath += "/";

        for (String PNGImgFileName : containingPathFile.list(new ImageFilter(PNGPattern))) {
            cueLocators.add(new CueLocator(containingPath + PNGImgFileName, cuePosition, colorWhiteList, destinationCueName, destinationCuePath, true));
        }
    }

    static void manageCueFiles() {
        List<CueLocator> cueLocators = new ArrayList<>();

        //region Blockers
        addCueLocatorByPattern(cueLocators, "cuebuilder/blockers", "news(.*)\\.png", Bounds.fromWidthHeight(358, 64, 83, 58),
                Set.of(), "News", "unitycues/blockers/cueNewsPopup.png");
        cueLocators.add(new CueLocator("cuebuilder/blockers/news.png", Bounds.fromWidthHeight(421, 447, 119, 32),
                Set.of(new Color(255, 255, 255)), "NewsClose", "unitycues/blockers/cueNewsClose.png", true));
        cueLocators.add(new CueLocator("cuebuilder/blockers/reconnect.png", Bounds.fromWidthHeight(336, 131, 129, 58),
                Set.of(), "UhOh", "unitycues/blockers/cueUhoh.png", true));
        addCueLocatorByPattern(cueLocators, "cuebuilder/blockers", "daily(.*)\\.png", Bounds.fromWidthHeight(259, 52, 282, 57),
                Set.of(), "DailyRewards", "unitycues/blockers/cueDailyRewards.png");
        addCueLocatorByPattern(cueLocators, "cuebuilder/blockers", "daily(.*)\\.png", Bounds.fromWidthHeight(353, 444, 97, 31),
                Set.of(), "Claim", "unitycues/blockers/cueClaim.png");
        addCueLocatorByPattern(cueLocators, "cuebuilder/blockers", "items(.*)\\.png", Bounds.fromWidthHeight(339, 117, 119, 58),
                Set.of(), "Items", "unitycues/blockers/cueItems.png");
        //endregion

        //region autoShrine
        cueLocators.add(new CueLocator("cuebuilder/autoShrine/settings.png", Bounds.fromWidthHeight(212, 299, 390, 68),
                Set.of(), "Settings", "unitycues/autoShrine/cueSettings.png", true));
        //endregion

        //region raid
        cueLocators.add(new CueLocator("cuebuilder/raid/raid-summon.png", Bounds.fromWidthHeight(485, 361, 112, 34),
                Set.of(new Color(255, 255, 255)), "RaidSummon", "unitycues/raid/cueRaidSummon.png", false));
        addCueLocatorByPattern(cueLocators, "cuebuilder/raid", "raid-diff(.*)\\.png", Bounds.fromWidthHeight(147, 222, 122, 27),
                Set.of(), "RaidNormal", "unitycues/raid/cueRaidNormal.png");
        addCueLocatorByPattern(cueLocators, "cuebuilder/raid", "raid-diff(.*)\\.png", Bounds.fromWidthHeight(340, 222, 122, 27),
                Set.of(), "RaidHard", "unitycues/raid/cueRaidHard.png");
        addCueLocatorByPattern(cueLocators, "cuebuilder/raid", "raid-diff(.*)\\.png", Bounds.fromWidthHeight(535, 222, 122, 27),
                Set.of(), "RaidHeroic", "unitycues/raid/cueRaidHeroic.png");
        //endregion

        //region dungeon
        addCueLocatorByPattern(cueLocators, "cuebuilder/dungeon", "dung-diff(.*)\\.png", Bounds.fromWidthHeight(147, 232, 122, 27),
                Set.of(), "DungNormal", "unitycues/dungeon/cueDungNormal.png");
        addCueLocatorByPattern(cueLocators, "cuebuilder/dungeon", "dung-diff(.*)\\.png", Bounds.fromWidthHeight(340, 232, 122, 27),
                Set.of(), "DungHard", "unitycues/dungeon/cueDungHard.png");
        addCueLocatorByPattern(cueLocators, "cuebuilder/dungeon", "dung-diff(.*)\\.png", Bounds.fromWidthHeight(535, 232, 122, 27),
                Set.of(), "DungHeroic", "unitycues/dungeon/cueDungHeroic.png");
        //endregion

        //region common
        cueLocators.add(new CueLocator("cuebuilder/raid/raid-team.png", Bounds.fromWidthHeight(202, 453, 87, 29),
                Set.of(new Color(255, 255, 255)), "AutoTeam", "unitycues/common/cueAutoTeam.png", false));
        cueLocators.add(new CueLocator("cuebuilder/raid/raid-team.png", Bounds.fromWidthHeight(326, 453, 87, 29),
                Set.of(new Color(255, 255, 255)), "TeamClear", "unitycues/common/cueTeamClear.png", false));
        cueLocators.add(new CueLocator("cuebuilder/raid/raid-team.png", Bounds.fromWidthHeight(465, 453, 115, 29),
                Set.of(new Color(255, 255, 255)), "TeamAccept", "unitycues/common/cueTeamAccept.png", false));
        addCueLocatorByPattern(cueLocators, "cuebuilder/common", "cleared(.*)\\.png", Bounds.fromWidthHeight(330, 132, 139, 56),
                Set.of(), "Cleared", "unitycues/common/cueCleared.png");
        cueLocators.add(new CueLocator("cuebuilder/common/cleared.png", Bounds.fromWidthHeight(303, 345, 61, 32),
                Set.of(), "YesGreen", "unitycues/common/cueYesGreen.png", true));
        cueLocators.add(new CueLocator("cuebuilder/common/solo.png", Bounds.fromWidthHeight(303, 345, 61, 32),
                Set.of(), "YesGreen", "unitycues/common/cueYesGreen.png", true));
        cueLocators.add(new CueLocator("cuebuilder/treasureChest/confirm-decline.png", Bounds.fromWidthHeight(303, 345, 61, 32),
                Set.of(), "YesGreen", "unitycues/common/cueYesGreen.png", true));
        addCueLocatorByPattern(cueLocators, "cuebuilder/common", "not-full(.*)\\.png", Bounds.fromWidthHeight(350, 130, 99, 58),
                Set.of(), "Team", "unitycues/common/cueTeam.png");
        addCueLocatorByPattern(cueLocators, "cuebuilder/common", "not-full(.*)\\.png", Bounds.fromWidthHeight(275, 217, 256, 97),
                Set.of(), "TeamNotFull", "unitycues/common/cueTeamNotFull.png");
        addCueLocatorByPattern(cueLocators, "cuebuilder/common", "not-full(.*)\\.png", Bounds.fromWidthHeight(445, 349, 45, 26),
                Set.of(), "No", "unitycues/common/cueNo.png");
        addCueLocatorByPattern(cueLocators, "cuebuilder/raid", "raid-team(.*)\\.png", Bounds.fromWidthHeight(204, 450, 84, 35),
                Set.of(), "AutoTeam", "unitycues/common/cueAutoTeam.png");
        addCueLocatorByPattern(cueLocators, "cuebuilder/raid", "raid-team(.*)\\.png", Bounds.fromWidthHeight(456, 451, 124, 32),
                Set.of(), "TeamAccept", "unitycues/common/cueTeamAccept.png");
        //endregion

        //region CueX
        cueLocators.add(new CueLocator("cuebuilder/raid/raid-summon.png", Bounds.fromWidthHeight(616, 97, 48, 48),
                Set.of(), "X", "unitycues/common/cueX.png", true));
        cueLocators.add(new CueLocator("cuebuilder/blockers/items_20210112.png", Bounds.fromWidthHeight(566, 129, 48, 48),
                Set.of(), "X", "unitycues/common/cueX.png", true));
        cueLocators.add(new CueLocator("cuebuilder/dungeon/dung-zone_20210115.png", Bounds.fromWidthHeight(706, 53, 48, 48),
                Set.of(), "X", "unitycues/common/cueX.png", true));
        //endregion

        //region Treasure Chest
        cueLocators.add(new CueLocator("cuebuilder/treasureChest/treasure.png", Bounds.fromWidthHeight(419, 377, 117, 31),
                Set.of(), "Decline", "unitycues/treasureChest/cueDecline.png", true));
        //endregion

        //region Familiar encounters
        addCueLocatorByPattern(cueLocators, "cuebuilder/familiarEncounter", "encounter(.*)\\.png", Bounds.fromWidthHeight(141, 275, 23, 31),
                Set.of(), "FamiliarEncounter", "unitycues/familiarEncounter/cueEncounter.png");
        addCueLocatorByPattern(cueLocators, "cuebuilder/familiarEncounter", "encounter(.*)\\.png", Bounds.fromWidthHeight(134, 327, 133, 30),
                Set.of(), "Persuade", "unitycues/familiarEncounter/cuePersuade.png");
        addCueLocatorByPattern(cueLocators, "cuebuilder/familiarEncounter", "encounter(.*)\\.png", Bounds.fromWidthHeight(553, 326, 94, 31),
                Set.of(), "Bribe", "unitycues/familiarEncounter/cueBribe.png");
        addCueLocatorByPattern(cueLocators, "cuebuilder/familiarEncounter", "encounter(.*)\\.png", Bounds.fromWidthHeight(253, 444, 108, 26),
                Set.of(), "DeclineRed", "unitycues/familiarEncounter/cueDeclineRed.png");
        addCueLocatorByPattern(cueLocators, "cuebuilder/familiarEncounter", "encounter_common(.*)\\.png", Bounds.fromWidthHeight(539, 276, 124, 30),
                Set.of(), "CommonFamiliar", "unitycues/familiarEncounter/type/cue01CommonFamiliar.png");
        addCueLocatorByPattern(cueLocators, "cuebuilder/familiarEncounter", "encounter_rare(.*)\\.png", Bounds.fromWidthHeight(539, 276, 124, 30),
                Set.of(), "RareFamiliar", "unitycues/familiarEncounter/type/cue02RareFamiliar.png");
        addCueLocatorByPattern(cueLocators, "cuebuilder/familiarEncounter", "encounter_epic(.*)\\.png", Bounds.fromWidthHeight(539, 276, 124, 30),
                Set.of(), "EpicFamiliar", "unitycues/familiarEncounter/type/cue03EpicFamiliar.png");
        //endregion

        for (CueLocator cueLoc : cueLocators) {
            cueLoc.generateCue();
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
        for (String raidImgFile : raidPath.list(new ImageFilter("raid-bar(.*)\\.png"))) {
            File raidBarFile = new File("barbuilder/raid/" + raidImgFile);

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
            int shard = DungeonThread.readResourceBarPercentage(seg, 100, Misc.BarOffsets.RAID.x, Misc.BarOffsets.RAID.y, 80, raidColors, raidImg);
            System.out.println("Raid bar is " + shard + "% full for image " + raidBarFile.getAbsolutePath());
        }

    }

    public static void main(String[] args) {
        manageCueFiles();
        manageRaidBar();
    }
}
