package com.github.ilpersi.BHBot;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Set;

/**
 * This is a stand-alone class to be used as an utility. The goal of this class is to provide a tool to work with
 * black and white images in a fast way.
 * As output of the tool, in the path specified in args[0], the tool will create a copy of the original files converted
 * in black and white scale. This is useful if you need to create new b&w images and you don't want to create them one
 * by one.
 * <p>
 * This tool can be used for other meaning also.
 */
@SuppressWarnings("unused")
public class BlackWhiteTools {
    public static void main(String[] args) {

        if (args.length > 0) {
            folderToBlackWhite(args[0]);
            // testWBPlayersTS(args[0]);
            // testInvasion(args[0]);
            // testWBTotalTS(args[0]);
            // testWBTier(args[0]);
            // generateWorldBossTierCues(args[0]);
            // generateWorldBossTierNumbers(args[0]);
        }
    }

    @SuppressWarnings("unused")
    static void folderToBlackWhite(String folderPath) {
        File imgFolder = new File(folderPath);

        if (imgFolder.exists() && imgFolder.isDirectory()) {
            File[] folderFiles = imgFolder.listFiles();
            if (folderFiles != null) {
                for (final File fileEntry : folderFiles) {
                    if (fileEntry.isDirectory()) {
                        folderToBlackWhite(fileEntry.getAbsolutePath());
                    } else if (!"BlackWhite_".equals(fileEntry.getName().substring(0, 11))) {
                        BufferedImage screenImg;
                        try {
                            screenImg = ImageIO.read(fileEntry);

                            MarvinImage origImg = new MarvinImage(screenImg);
                            /*
                             * ATTENTION IF YOU WANT THIS TO WORK CORRECTLY, REMEMBER TO CHECK THE PARAMETERS
                             * OF THE TOBLACKWHITE METHOD
                             */
                            // origImg.toBlackWhite(new Color(25, 25, 25), new Color(204, 204, 204), 204);
                            origImg.toBlackWhite(120);
                            origImg.update();
                            BufferedImage bwImage = origImg.getBufferedImage();

                            String fileName = "BlackWhite_" + fileEntry.getName();
                            String newFilePath = fileEntry.getAbsolutePath().replace(fileEntry.getName(), fileName);

                            File outputFile = new File(newFilePath);
                            if (outputFile.exists()) {
                                if (!outputFile.delete()) {
                                    System.out.println("Impossible to delete " + newFilePath);
                                } else {
                                    outputFile = new File(newFilePath);
                                }
                            }
                            ImageIO.write(bwImage, "png", outputFile);
                        } catch (IOException e) {
                            System.out.println("Error when loading game screen ");
                        }
                    }
                }
            }
        }
    }

    @SuppressWarnings("unused")
    static void testWBTotalTS(String screenPath) {
        File imgFile = new File(screenPath);

        if (imgFile.exists()) {
            BufferedImage screenImg;
            try {
                screenImg = ImageIO.read(imgFile);
                Bounds totalWBTS = Bounds.fromWidthHeight(595, 65, 100, 35);

                MarvinImage totalTSImg = new MarvinImage(screenImg.getSubimage(totalWBTS.x1, totalWBTS.y1, totalWBTS.width, totalWBTS.height));
                totalTSImg.toBlackWhite(new Color(25, 25, 25), new Color(255, 255, 255), 254);
                totalTSImg.update();
                BufferedImage totalTSSubImg = totalTSImg.getBufferedImage();

            } catch (IOException e) {
                System.out.println("Error when loading game screen ");
            }


        }
    }

    @SuppressWarnings("unused")
    static void testWBPlayersTS(String screenPath) {
        File imgFile = new File(screenPath);
        int invitesCnt = 4;

        if (imgFile.exists()) {
            BufferedImage screenImg;
            try {
                screenImg = ImageIO.read(imgFile);
                Bounds TSBound = Bounds.fromWidthHeight(184, 241, 84, 18);

                for (int partyMemberPos = 0; partyMemberPos < invitesCnt - 1; partyMemberPos++) {
                    MarvinImage subImg = new MarvinImage(screenImg.getSubimage(TSBound.x1, TSBound.y1 + (54 * partyMemberPos), TSBound.width, TSBound.height));
                    subImg.toBlackWhite(new Color(20, 20, 20), new Color(203, 203, 203), 203);
                    subImg.update();
                    BufferedImage subimagetestbw = subImg.getBufferedImage();

                }

            } catch (IOException e) {
                System.out.println("Error when loading game screen ");
            }


        }
    }

    /**
     * This method is used to test the B&W tools with the invasion final screen to read the level you reached
     *
     * @param screenPath Path to the screen to be used to check the invasion reading logic
     */
    @SuppressWarnings("unused")
    static void testInvasion(String screenPath) {
        File imgFile = new File(screenPath);

        if (imgFile.exists()) {
            BufferedImage screenImg;
            try {
                screenImg = ImageIO.read(imgFile);

                MarvinImage subImg = new MarvinImage(screenImg.getSubimage(375, 20, 55, 20));
                subImg.toBlackWhite(new Color(10, 11, 13), new Color(64, 64, 64), 0);
                subImg.update();
                BufferedImage subimagetestbw = subImg.getBufferedImage();

            } catch (IOException e) {
                System.out.println("Error when loading game screen ");
            }


        }
    }

    @SuppressWarnings("unused")
    static void testWBTier(String screenPath) {
        File imgFile = new File(screenPath);

        if (imgFile.exists()) {
            BufferedImage screenImg;
            try {
                screenImg = ImageIO.read(imgFile);

                MarvinImage subImg = new MarvinImage(screenImg.getSubimage(401, 209, 21, 19));
                subImg.toBlackWhite(new Color(25, 25, 25), new Color(255, 255, 255), 255);
                subImg.update();
                BufferedImage subimagetestbw = subImg.getBufferedImage();

            } catch (IOException e) {
                System.out.println("Error when loading game screen ");
            }


        }
    }

    static void generateWorldBossTierCues(String path) {
        folderToBlackWhite(path);

        int xOffset, yOffset, w, h;
        File imgFolder = new File(path);

        if (imgFolder.exists() && imgFolder.isDirectory()) {
            File[] folderFiles = imgFolder.listFiles();
            if (folderFiles != null) {

                // Offset to read the cue
                xOffset = 401;
                yOffset = 209;
                w = 21;
                h = 19;

                for (final File fileEntry : folderFiles) {
                    if (fileEntry.isDirectory()) {
                        folderToBlackWhite(fileEntry.getAbsolutePath());
                    } else if ("BlackWhite_".equals(fileEntry.getName().substring(0, 11)) &&
                            !"Cue_BlackWhite_".equals(fileEntry.getName().substring(0, 15))) {
                        BufferedImage screenImg;
                        try {
                            screenImg = ImageIO.read(fileEntry);

                            MarvinImage origImg = new MarvinImage(screenImg.getSubimage(xOffset, yOffset, w, h));
                            /*
                             * ATTENTION IF YOU WANT THIS TO WORK CORRECTLY, REMEMBER TO CHECK THE PARAMETERS
                             * OF THE TOBLACKWHITE METHOD
                             */
                            origImg.toBlackWhite(new Color(25, 25, 25), new Color(255, 255, 255), 255);
                            origImg.update();
                            BufferedImage bwImage = origImg.getBufferedImage();

                            String fileName = "Cue_" + fileEntry.getName();
                            String newFilePath = fileEntry.getAbsolutePath().replace(fileEntry.getName(), fileName);

                            File outputFile = new File(newFilePath);
                            if (outputFile.exists()) {
                                if (!outputFile.delete()) {
                                    System.out.println("Impossible to delete " + newFilePath);
                                } else {
                                    outputFile = new File(newFilePath);
                                }
                            }
                            ImageIO.write(bwImage, "png", outputFile);
                        } catch (IOException e) {
                            System.out.println("Error when loading game screen ");
                        }
                    }
                }
            }
        }
    }

    static void generateWorldBossTierNumbers(String path) {
        final Set<Color> colorWhiteList = Set.of(new Color(255, 255, 255));

        File imgFolder = new File(path);

        if (imgFolder.exists() && imgFolder.isDirectory()) {
            File[] folderFiles = imgFolder.listFiles();
            if (folderFiles != null) {

                for (final File fileEntry : folderFiles) {
                    if (fileEntry.isDirectory()) {
                        folderToBlackWhite(fileEntry.getAbsolutePath());
                    } else if ("Cue_BlackWhite_".equals(fileEntry.getName().substring(0, 15))) {
                        BufferedImage screenImg;
                        try {
                            screenImg = ImageIO.read(fileEntry);
                        } catch (IOException e) {
                            System.out.println("Error when reading B&W Cue.");
                            continue;
                        }

                        int minX = screenImg.getWidth();
                        int minY = screenImg.getHeight();
                        int maxY = 0;
                        int maxX = 0;

                        int[][] pixelMatrix = Misc.convertTo2D(screenImg);
                        for (int y = 0; y < screenImg.getHeight(); y++) {
                            for (int x = 0; x < screenImg.getWidth(); x++) {
                                // if (pixelMatrix[x][y] == familiarTxtColor) {
                                if (colorWhiteList.contains(new Color(pixelMatrix[x][y]))) {
                                    if (y < minY) minY = y;
                                    if (x < minX) minX = x;
                                    if (y > maxY) maxY = y;
                                    if (x > maxX) maxX = x;
                                } else {
                                    screenImg.setRGB(x, y, 0);
                                }
                            }
                        }
                        System.out.println(MessageFormat.format("{0} {1} {2} {3} {4}", fileEntry.getName(), minX, maxX, minY, maxY));
                        BufferedImage numberImg = screenImg.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);

                        String fileName = "Cropped_" + fileEntry.getName();
                        String newFilePath = fileEntry.getAbsolutePath().replace(fileEntry.getName(), fileName);

                        File outputFile = new File(newFilePath);
                        if (outputFile.exists()) {
                            if (!outputFile.delete()) {
                                System.out.println("Impossible to delete " + newFilePath);
                            }
                        }
                        outputFile = new File(newFilePath);
                        try {
                            ImageIO.write(numberImg, "png", outputFile);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }
}
