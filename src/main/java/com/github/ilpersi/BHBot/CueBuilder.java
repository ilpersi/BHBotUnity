package com.github.ilpersi.BHBot;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * This is a quick and dirty solution aimed at creating multiple Cues from an origin image file.
 * The goal of this class is to be on par with Kongregate changing Cues frequently
 */
public class CueBuilder {
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
            File screenShotFile = new File("src/main/resources/" + this.containingScreenShotPath);
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

            if (merge) {
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
                    System.out.println("It was impossible to merge " + this.destinationCueName + " due to different cue dimensions.");
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

                String cueManager = "addCue(\"" + this.destinationCueName + "\", \"" + this.destinationCuePath + "\", " + boundsStr + ");";

                System.out.println(cueManager);
            }
        }

    }

    public static void main(String[] args) {
        List<CueLocator> cueLocators = new ArrayList<>();

        //region Blockers
        cueLocators.add(new CueLocator("unitycuesources/blockers/news.png", Bounds.fromWidthHeight(358, 64, 83, 58),
                Set.of(), "News", "unitycues/blockers/cueNewsPopup.png", true));
        cueLocators.add(new CueLocator("unitycuesources/blockers/news.png", Bounds.fromWidthHeight(421, 447, 119, 32),
                Set.of(new Color(255, 255, 255)), "NewsClose", "unitycues/blockers/cueNewsClose.png", true));
        cueLocators.add(new CueLocator("unitycuesources/blockers/reconnect.png", Bounds.fromWidthHeight(336, 131, 129, 58),
                Set.of(), "UhOh", "unitycues/blockers/cueUhoh.png", true));
        //endregion

        //region autoShrine
        cueLocators.add(new CueLocator("unitycuesources/autoShrine/settings.png", Bounds.fromWidthHeight(212, 299, 390, 68),
                Set.of(), "Settings", "unitycues/autoShrine/cueSettings.png", true));
        //endregion

        //region raid
        cueLocators.add(new CueLocator("unitycuesources/raid/raid-summon.png", Bounds.fromWidthHeight(485, 361, 112, 34),
                Set.of(new Color(255, 255, 255)), "RaidSummon", "unitycues/raid/cueRaidSummon.png", false));
        cueLocators.add(new CueLocator("unitycuesources/raid/raid-diff.png", Bounds.fromWidthHeight(147, 222, 122, 27),
                Set.of(new Color(255, 255, 255)), "RaidNormal", "unitycues/raid/cueRaidNormal.png", false));
        cueLocators.add(new CueLocator("unitycuesources/raid/raid-diff.png", Bounds.fromWidthHeight(340, 222, 122, 27),
                Set.of(new Color(255, 255, 255)), "RaidHard", "unitycues/raid/cueRaidHard.png", false));
        cueLocators.add(new CueLocator("unitycuesources/raid/raid-diff.png", Bounds.fromWidthHeight(535, 222, 122, 27),
                Set.of(new Color(255, 255, 255)), "RaidHeroic", "unitycues/raid/cueRaidHeroic.png", false));
        //endregion

        //region common
        cueLocators.add(new CueLocator("unitycuesources/raid/raid-team.png", Bounds.fromWidthHeight(202, 453, 87, 29),
                Set.of(new Color(255, 255, 255)), "AutoTeam", "unitycues/common/cueAutoTeam.png", false));
        cueLocators.add(new CueLocator("unitycuesources/raid/raid-team.png", Bounds.fromWidthHeight(326, 453, 87, 29),
                Set.of(new Color(255, 255, 255)), "TeamClear", "unitycues/common/cueTeamClear.png", false));
        cueLocators.add(new CueLocator("unitycuesources/raid/raid-team.png", Bounds.fromWidthHeight(465, 453, 115, 29),
                Set.of(new Color(255, 255, 255)), "TeamAccept", "unitycues/common/cueTeamAccept.png", false));
        cueLocators.add(new CueLocator("unitycuesources/common/cleared.png", Bounds.fromWidthHeight(330, 132, 139, 56),
                Set.of(), "Cleared", "unitycues/common/cueCleared.png", true));
        cueLocators.add(new CueLocator("unitycuesources/common/cleared.png", Bounds.fromWidthHeight(303, 345, 61, 32),
                Set.of(), "YesGreen", "unitycues/common/cueYesGreen.png", true));
        //endregion

        //region CueX
        cueLocators.add(new CueLocator("unitycuesources/raid/raid-summon.png", Bounds.fromWidthHeight(616, 97, 48, 48),
                Set.of(), "X", "unitycues/common/cueX.png", true));
        //endregion

        //region Treasure Chest
        cueLocators.add(new CueLocator("unitycuesources/treasureChest/treasure.png", Bounds.fromWidthHeight(419, 377, 117, 31),
                Set.of(), "Decline", "unitycues/treasureChest/cueDecline.png", true));
        //endregion

        for (CueLocator cueLoc : cueLocators) {
            cueLoc.generateCue();
        }
    }
}
