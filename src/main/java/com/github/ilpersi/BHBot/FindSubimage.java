package com.github.ilpersi.BHBot;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Copied from:
 * https://github.com/gabrielarchanjo/marvinproject/blob/ca299ca939b7d64c858d5fff8a6491ec5708719f/marvinproject/dev/MarvinPlugins/src/org/marvinproject/image/pattern/findSubimage/FindSubimage.java
 * <p>
 * Modified by Betalord.
 * <p>
 * endX and endY define last point where we will begin searching for a cue, and not the last point of bottom-right corner of the cue (but rather top-left corner of the cue)!
 *
 * @author Betalord
 */
class FindSubimage {

	static MarvinSegment findImage(BufferedImage imageIn, BufferedImage subImage, int startX, int startY, int endX, int endY) {
        List<MarvinSegment> r = findSubimage(imageIn, subImage, 1.0, false, false, startX, startY, endX, endY);
        return r.isEmpty() ? null : r.get(0);
    }

    /**
     * @param imageIn                    imageIn
     * @param subImage                   subImage
     * @param similarity                 similarity
     * @param findAll                    findAll
     * @param treatTransparentAsObscured this is a special flag that is used rarely. When true, it will consider all transparent pixels from the 'subImage' as pixels that must be lower than 200 accumulative value in the 'imageIn'. We use it for example when detecting "Loading" superimposed message (and background is obscured, with white(255,255,255) having a value of 64,64,64, which is the maximum value with obscured background.
     * @param startX                     startX
     * @param startY                     startY
     * @param endX                       may be 0 (will be ignored in this case)
     * @param endY                       may be 0 (will be ignored in this case)
     * @return a list of found subimages
     */
    @SuppressWarnings("SameParameterValue")
    static List<MarvinSegment> findSubimage(BufferedImage imageIn, BufferedImage subImage, double similarity, boolean findAll, boolean treatTransparentAsObscured, int startX, int startY, int endX, int endY) {
        List<MarvinSegment> segments = new ArrayList<>();

        int imgInWidth = imageIn.getWidth();
        int imgInHeight = imageIn.getHeight();

        int subImgWidth = subImage.getWidth();
        int subImgHeight = subImage.getHeight();

        if (endX == 0) endX = imgInWidth; // endX was not set
        if (endY == 0) endY = imgInHeight; // endY was not set

        // It may be possible, using Bounds.fromWidthHeight that images close to the
        // sides, get over them. This is making sure that this never happens
        if (endX > imgInWidth) endX = imgInWidth;
        if (endY > imgInHeight) endY = imgInHeight;

        int subImagePixels = subImgWidth * subImgHeight;
        boolean[][] processed = new boolean[imgInWidth][imgInHeight];

        int[] imageInRGB = imageIn.getRGB(0,0, imgInWidth, imgInHeight, null, 0, imgInWidth);
        int[] subImageRGB = subImage.getRGB(0,0, subImgWidth, subImgHeight, null, 0, subImgWidth);

        // Full image
        try {
            mainLoop:
            for (int y = startY; y < endY; y++) {
                for (int x = startX; x < endX; x++) {

                    if (processed[x][y]) {
                        continue;
                    }

                    int notMatched = 0;
                    boolean match = true;
                    // subImage
                    if (y + subImgHeight < imgInHeight && x + subImgWidth < imgInWidth) {

                        outerLoop:
                        for (int i = 0; i < subImgHeight; i++) {
                            for (int j = 0; j < subImgWidth; j++) {

                                if (processed[x + j][y + i]) {
                                    match = false;
                                    break outerLoop;
                                }

                                int imageInIndex = ((y + i)*imgInWidth)+(x + j);
                                // int c1Alpha = (imageInRGB[imageInIndex] >> 24) & 0xff;
                                int c1Red = (imageInRGB[imageInIndex] >> 16) & 0xff;
                                int c1Green = (imageInRGB[imageInIndex] >> 8) & 0xff;
                                int c1Blue = (imageInRGB[imageInIndex]) & 0xff;

                                int subImageIndex = (i*subImgWidth)+j;
                                int c2Alpha = (subImageRGB[subImageIndex] >> 24) & 0xff;
                                int c2Red = (subImageRGB[subImageIndex] >> 16) & 0xff;
                                int c2Green = (subImageRGB[subImageIndex] >> 8) & 0xff;
                                int c2Blue = (subImageRGB[subImageIndex]) & 0xff;

                                if (c2Alpha == 0) {
                                    if (!treatTransparentAsObscured)
                                        continue; // we don't match transparent pixels!
                                    // treat transparent pixel as obscured background:
                                    int total = c1Red + c1Green + c1Blue;
                                    if (total > 200) {
                                        notMatched++;

                                        if (notMatched > (1 - similarity) * subImagePixels) {
                                            match = false;
                                            break outerLoop;
                                        }
                                    }
                                } else if
                                (
                                        Math.abs(c1Red - c2Red) > 5 ||
                                                Math.abs(c1Green - c2Green) > 5 ||
                                                Math.abs(c1Blue - c2Blue) > 5
                                ) {
                                    notMatched++;

                                    if (notMatched > (1 - similarity) * subImagePixels) {
                                        match = false;
                                        break outerLoop;
                                    }
                                }
                            }
                        }
                    } else {
                        match = false;
                    }

                    if (match) {
                        segments.add(new MarvinSegment(x, y, x + subImgWidth, y + subImgHeight));

                        if (!findAll) {
                            break mainLoop;
                        }

                        for (int i = 0; i < subImgHeight; i++) {
                            for (int j = 0; j < subImgWidth; j++) {
                                processed[x + j][y + i] = true;
                            }
                        }
                    }
                }
            }
        } catch (java.lang.ArrayIndexOutOfBoundsException e) {
            BHBot.logger.debug("ArrayIndexOutOfBounds Exception in FindSubimage", e);
            Misc.saveScreen("ArrayIndexOutOfBounds-In", "find-errors", BHBot.includeMachineNameInScreenshots, imageIn);
            Misc.saveScreen("ArrayIndexOutOfBounds-Sub", "find-errors", BHBot.includeMachineNameInScreenshots, subImage);
            BHBot.logger.debug(String.format("Image In  -> W: %d H: %d", imgInWidth, imgInHeight));
            BHBot.logger.debug(String.format("Image Sub -> W: %d H: %d", subImgWidth, subImgHeight));
            BHBot.logger.debug(String.format("startX: %d, startY: %d, endX: %d, endY: %d", startX, startY, endX, endY));
            BHBot.logger.debug(Misc.getStackTrace());
        }

        return segments;
    }

}