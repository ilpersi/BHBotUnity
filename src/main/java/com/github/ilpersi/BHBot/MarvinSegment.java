package com.github.ilpersi.BHBot;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;

/**
 * This class has been copied from <br>
 * https://github.com/gabrielarchanjo/marvinproject/blob/master/marvinproject/dev/MarvinFramework/src/marvin/image/MarvinSegment.java <br>
 * It has been copied over in order to reduce dependency on the Marvin framework.
 *
 * @author Betalord
 */
public class MarvinSegment {

    public int width;
    public int height;
    int x1,
            x2,
            y1,
            y2;
    private final int area;

    private static final HashSet<String> debugBoundsSet = new HashSet<>();

    MarvinSegment(int x1, int y1, int x2, int y2) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.width = (x2 - x1) + 1;
        this.height = (y2 - y1) + 1;
        this.area = this.width * this.height;
    }

    // https://stackoverflow.com/questions/297762/find-known-sub-image-in-larger-image
    static MarvinSegment findSubimage(BufferedImage src, Cue cue, BrowserManager browserManager) {
        long timer = Misc.getTime();

        MarvinSegment seg;

        // Offset for do_not_share url missplacement of cues
        int x1, x2, y1, y2;

        // If the do_not_share url is available, cue detection is considering the correct offset
        if (browserManager.isDoNotShareUrl()) {
            x1 = cue.bounds != null && cue.bounds.x1 > 0 ? cue.bounds.x1 - 1 : 0;
            x2 = cue.bounds != null && cue.bounds.x2 > 0 ? cue.bounds.x2 - 1 : 0;
            y1 = cue.bounds != null && cue.bounds.y1 > 0 ? cue.bounds.y1 - 3 : 0;
            y2 = cue.bounds != null && cue.bounds.y2 > 0 ? cue.bounds.y2 - 3 : 0;
        } else {
            x1 = cue.bounds != null ? cue.bounds.x1 : 0;
            x2 = cue.bounds != null ? cue.bounds.x2 : 0;
            y1 = cue.bounds != null ? cue.bounds.y1 : 0;
            y2 = cue.bounds != null ? cue.bounds.y2 : 0;
        }

        seg = FindSubimage.findImage(src, cue.im, x1, y1, x2, y2);

        //source.drawRect(seg.x1, seg.y1, seg.x2-seg.x1, seg.y2-seg.y1, Color.blue);
        //MarvinImageIO.saveImage(source, "window_out.png");
        if (BHBot.debugDetectionTimes) {
            BHBot.logger.debug("cue detection time: " + (Misc.getTime() - timer) + "ms (" + cue.name + ") [" + (seg != null ? "true" : "false") + "]");
        }

        if (cue.bounds == null && seg != null && BHBot.debugNullBounds) {

            Bounds suggestedBounds = Bounds.fromMarvinSegment(seg, 10);

            int suggestedX1 = suggestedBounds.x1;
            int suggestedY1 = suggestedBounds.y1;
            int suggestedWidth = suggestedBounds.width;
            int suggestedHeight = suggestedBounds.height;

            // we make sure we dont exceed the src width
            while ((suggestedX1 + suggestedWidth) > src.getWidth()) {
                suggestedWidth -= 1;
            }

            // we make sure we dont exceed the src height
            while ((suggestedY1 + suggestedHeight) > src.getHeight()) {
                suggestedHeight -= 1;
            }

            // we make sure not to exceed initial boundaries
            if (suggestedX1 < 0) suggestedX1 = 0;
            if (suggestedY1 < 0) suggestedY1 = 0;

            // Key used to check if we printed null bounds info before
            String cueKey = cue.name + "_" + suggestedX1 + "_" + suggestedWidth + "_" + suggestedY1 + "_" + suggestedHeight;

            // We try to minimize the output checking if we printed a similar cue previously
            if (!debugBoundsSet.contains(cueKey)) {
                debugBoundsSet.add(cueKey);

                // Found cue details
                String boundsDbgMsg = "Null bounds cue found " +
                        "cueName: '" + cue.name +
                        "' x1: " + seg.x1 +
                        " x2: " + seg.x2 +
                        " y1: " + seg.y1 +
                        " y2: " + seg.y2 +
                        " with: " + seg.width +
                        " height: " + seg.height;

                BHBot.logger.debug(boundsDbgMsg);

                // Suggested bounds ready for code copy/paste
                String suggestionBuilder = "Cue " + cue.name + "WithBounds = " +
                        "new Cue(BHBot.cues.get(\"" + cue.name + "\"), " + suggestedBounds.getJavaCode(false, true) + ");";
                BHBot.logger.debug(suggestionBuilder);

                // As screenshot of the found bounds on the image is saved in a dedicated folder
                MarvinImage debugBounds = new MarvinImage(src);
                debugBounds.drawRect(seg.x1, seg.y1, seg.width, seg.height, Color.BLUE);
                debugBounds.drawRect(suggestedX1, suggestedY1, suggestedWidth, suggestedHeight, 3, Color.GREEN);
                debugBounds.update();
                String boundsFName = Misc.saveScreen("debugNullBounds_" + cueKey, "debug_null_bounds", debugBounds.getBufferedImage());
                BHBot.logger.debug("Found bounds saved in: " + boundsFName);

                // To make the suggestion actionable we also print the stack trace so that developers can use it
                BHBot.logger.debug(Misc.getStackTrace());
            }

        }

        return seg;
    }

    /**
     * Will try (and retry) to detect cue from image until timeout is reached. May return null if cue has not been detected within given 'timeout' time. If 'timeout' is 0,
     * then it will attempt at cue detection only once and return the result immediately.
     */
    static MarvinSegment fromCue(Cue cue, int timeout, @SuppressWarnings("SameParameterValue") boolean game, BrowserManager browserManager) {
        long timer = Misc.getTime();
        MarvinSegment seg = findSubimage(browserManager.getImg(), cue, browserManager);

        int maxDelay = 500;
        double delay = 250.0;
        double attemptCnt = 1.0;

        while (seg == null) {
            if ((Misc.getTime() - timer) >= timeout)
                break;

            browserManager.readScreen((int) delay, game);
            seg = findSubimage(browserManager.getImg(), cue, browserManager);

            attemptCnt += 1.0;
            delay += Math.pow(2.0, attemptCnt) * 12.5;

            if (delay > maxDelay) delay = maxDelay;

        }

        return seg;
    }

    static MarvinSegment fromCue(Cue cue, BrowserManager browserManager) {
        return fromCue(cue, 0, true, browserManager);
    }

    static MarvinSegment fromCue(Cue cue, int timeout, Bounds bounds, BrowserManager browserManager) {
        return fromCue(new Cue(cue, bounds), timeout, true, browserManager);
    }

    static MarvinSegment fromCue(Cue cue, int timeout, BrowserManager browserManager) {
        return fromCue(cue, timeout, true, browserManager);
    }

    // Cue detection based on String
    static MarvinSegment fromCue(String cueName, BrowserManager browserManager) {
        return fromCue(BHBot.cues.get(cueName), 0, true, browserManager);
    }

    static MarvinSegment fromCue(String cueName, int timeout, Bounds bounds, BrowserManager browserManager) {
        return fromCue(new Cue(BHBot.cues.get(cueName), bounds), timeout, true, browserManager);
    }

    static MarvinSegment fromCue(String cueName, int timeout, BrowserManager browserManager) {
        return fromCue(BHBot.cues.get(cueName), timeout, true, browserManager);
    }

    /**
     * This method will return a MarviSegment starting from two image files. Use this
     * method during development time to check that the Cues you created works fine.
     *
     * @param imageInFile The main image that will be used to search the Cue
     * @param subImageFile The Cue image you are looking for
     * @return The first found Marvin Segment
     */
    static MarvinSegment fromFiles(File imageInFile, File subImageFile) {
        BufferedImage imageIn = null, subImage = null;

        try {
            imageIn = ImageIO.read(imageInFile);
        } catch (IOException e) {
            BHBot.logger.error("Error when loading image file.", e);
        }

        try {
            subImage = ImageIO.read(subImageFile);
        } catch (IOException e) {
            BHBot.logger.error("Error when loading sub-image file.", e);
        }

        return FindSubimage.findImage(imageIn, subImage, 0, 0, 0, 0);
    }

    static  MarvinSegment fromFiles(String imageInPath, String subImagePath) {
        File imageInFile = new File(imageInPath);
        File subImageFile = new File(subImagePath);

        if (!imageInFile.exists()) {
            BHBot.logger.error("Image in file does not exist.");
            return null;
        }

        if (!subImageFile.exists()) {
            BHBot.logger.error("Sub-image file does not exist.");
            return null;
        }

        return fromFiles(imageInFile, subImageFile);
    }

    public String toString() {
        return "{x1:" + x1 + ", x2:" + x2 + ", y1:" + y1 + ", y2:" + y2 + ", width:" + width + ", height:" + height + ", area:" + area + "}";
    }

    int getX1() {
        return x1;
    }

    int getCenterX() {
        return (this.x1 + this.x2) / 2;
    }

    int getCenterY() {
        return (this.y1 + this.y2) / 2;
    }

    Bounds getBounds() {
        return new Bounds(this.x1, this.y1, this.x2, this.y2);
    }
}