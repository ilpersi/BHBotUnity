package com.github.ilpersi.BHBot;

import java.awt.*;
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

	/*public static MarvinSegment findImage(BufferedImage imageIn, BufferedImage subimage) {
		List<MarvinSegment> r = findSubimage(imageIn, subimage, 1.0, false, false, 0, 0, 0, 0);
		if (r.isEmpty())
			return null;
		else
			return r.get(0);
	}*/

    static MarvinSegment findImage(BufferedImage imageIn, BufferedImage subimage, int startX, int startY, int endX, int endY) {
        List<MarvinSegment> r = findSubimage(imageIn, subimage, 1.0, false, false, startX, startY, endX, endY);
        return r.isEmpty() ? null : r.get(0);
    }

    /**
     * @param imageIn                    imageIn
     * @param subimage                   subimage
     * @param similarity                 similarity
     * @param findAll                    findAll
     * @param treatTransparentAsObscured this is a special flag that is used rarely. When true, it will consider all transparent pixels from the 'subimage' as pixels that must be lower than 200 accumulative value in the 'imageIn'. We use it for example when detecting "Loading" superimposed message (and background is obscured, with white(255,255,255) having a value of 64,64,64, which is the maximum value with obscured background.
     * @param startX                     startX
     * @param startY                     startY
     * @param endX                       may be 0 (will be ignored in this case)
     * @param endY                       may be 0 (will be ignored in this case)
     * @return a list of found subimages
     */
    @SuppressWarnings("SameParameterValue")
    static List<MarvinSegment> findSubimage(BufferedImage imageIn, BufferedImage subimage, double similarity, boolean findAll, boolean treatTransparentAsObscured, int startX, int startY, int endX, int endY) {
        List<MarvinSegment> segments = new ArrayList<>();

        if (endX == 0) endX = imageIn.getWidth(); // endX was not set
        if (endY == 0) endY = imageIn.getHeight(); // endY was not set

        // It may be possible, using Bounds.fromWidthHeight that images close to the
        // sides, get over them. This is making sure that this never happens
        if (endX > imageIn.getWidth()) endX = imageIn.getWidth();
        if (endY > imageIn.getHeight()) endY = imageIn.getHeight();

        int subImagePixels = subimage.getWidth() * subimage.getHeight();
        boolean[][] processed = new boolean[imageIn.getWidth()][imageIn.getHeight()];

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
                    // subimage
                    if (y + subimage.getHeight() < imageIn.getHeight() && x + subimage.getWidth() < imageIn.getWidth()) {


                        outerLoop:
                        for (int i = 0; i < subimage.getHeight(); i++) {
                            for (int j = 0; j < subimage.getWidth(); j++) {

                                if (processed[x + j][y + i]) {
                                    match = false;
                                    break outerLoop;
                                }

                                Color c1 = new Color(imageIn.getRGB(x + j, y + i), true);

                                Color c2 = new Color(subimage.getRGB(j, i), true);

                                if (c2.getAlpha() == 0) {
                                    if (!treatTransparentAsObscured)
                                        continue; // we don't match transparent pixels!
                                    // treat transparent pixel as obscured background:
                                    int total = c1.getRed() + c1.getGreen() + c1.getBlue();
                                    if (total > 200) {
                                        notMatched++;

                                        if (notMatched > (1 - similarity) * subImagePixels) {
                                            match = false;
                                            break outerLoop;
                                        }
                                    }
                                } else if
                                (
                                        Math.abs(c1.getRed() - c2.getRed()) > 5 ||
                                                Math.abs(c1.getGreen() - c2.getGreen()) > 5 ||
                                                Math.abs(c1.getBlue() - c2.getBlue()) > 5
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
                        segments.add(new MarvinSegment(x, y, x + subimage.getWidth(), y + subimage.getHeight()));

                        if (!findAll) {
                            break mainLoop;
                        }

                        for (int i = 0; i < subimage.getHeight(); i++) {
                            for (int j = 0; j < subimage.getWidth(); j++) {
                                processed[x + j][y + i] = true;
                            }
                        }
                    }
                }
            }
        } catch (java.lang.ArrayIndexOutOfBoundsException e) {
            BHBot.logger.debug("ArrayIndexOutOfBounds Exception in FindSubimage", e);
            Misc.saveScreen("ArrayIndexOutOfBounds-In", "find-errors", BHBot.includeMachineNameInScreenshots, imageIn);
            Misc.saveScreen("ArrayIndexOutOfBounds-Sub", "find-errors", BHBot.includeMachineNameInScreenshots, subimage);
            BHBot.logger.debug(String.format("Image In  -> W: %d H: %d", imageIn.getWidth(), imageIn.getWidth()));
            BHBot.logger.debug(String.format("Image Sub -> W: %d H: %d", subimage.getWidth(), subimage.getWidth()));
            BHBot.logger.debug(String.format("startX: %d, startY: %d, endX: %d, endY: %d", startX, startY, endX, endY));
            BHBot.logger.debug(Misc.getStackTrace());
        }

        return segments;
    }

}