package com.github.ilpersi.BHBot;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;

/**
 * This class has been copied from <br>
 * https://github.com/gabrielarchanjo/marvinproject/blob/master/marvinproject/dev/MarvinFramework/src/marvin/image/MarvinSegment.java <br>
 * It has been copied over in order to reduce dependency on the Marvin framework.
 *
 * @author Betalord
 */
public class MarvinSegment {

    int width;
    int height;
    int x1,
            x2,
            y1,
            y2;
    private final int area;

    private final static double INITIAL_DELAY = 100.0;
    private final static int MAX_DELAY = 350;
    private final static double COEFFICIENT = 24;

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

        if (BHBot.debugFindImage) {
            List<MarvinSegment> foundSegs = FindSubimage.findSubimage(src, cue.im, 1.0, false, false, x1, y1, x2, y2);
            seg = foundSegs.isEmpty() ? null : foundSegs.get(0);

            final int OFFSET = 5;
            final int newWidth = cue.im.getWidth() + src.getWidth() + OFFSET;
            final int newHeight = Math.max(cue.im.getHeight(), src.getHeight()) + OFFSET;
            Color highlight = foundSegs.isEmpty() ? Color.RED : Color.GREEN;
            String match = foundSegs.isEmpty() ? "NO-MATCH" : "MATCH";

            BufferedImage mergeImg = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
            Graphics g = mergeImg.getGraphics();

            g.drawImage(cue.im, OFFSET, OFFSET, null);
            g.drawImage(src, OFFSET + cue.im.getWidth(), OFFSET, null);

            foundSegs = FindSubimage.findSubimage(mergeImg, cue.im, 1.0, true, false, x1, y1, x2, y2);

            MarvinImage mainMarvinImg = new MarvinImage(mergeImg);
            mainMarvinImg.drawRect(x1 + OFFSET + cue.im.getWidth(), y1 + OFFSET, x2 - x1, y2 - y1, Color.BLUE);
            foundSegs.forEach((foundSeg) -> mainMarvinImg.drawRect(foundSeg.x1 + OFFSET + cue.im.getWidth(), foundSeg.y1 + OFFSET, foundSeg.width, foundSeg.height, 2, highlight));
            mainMarvinImg.update();

            Misc.saveScreen("" + cue.name + "-" + match, "debugFindImage", BHBot.includeMachineNameInScreenshots, mainMarvinImg.getBufferedImage());

        } else {
            seg = FindSubimage.findImage(src, cue.im, x1, y1, x2, y2);
        }

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
                String boundsFName = Misc.saveScreen("debugNullBounds_" + cueKey, "debug_null_bounds", BHBot.includeMachineNameInScreenshots, debugBounds.getBufferedImage());
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

        double delay = INITIAL_DELAY;
        double attemptCnt = 1.0;

        while (seg == null) {
            if ((Misc.getTime() - timer) >= timeout)
                break;

            browserManager.readScreen((int) delay, game);
            seg = findSubimage(browserManager.getImg(), cue, browserManager);

            attemptCnt += 1.0;
            delay += Math.pow(2.0, attemptCnt) * COEFFICIENT;

            if (delay > MAX_DELAY) delay = MAX_DELAY;

        }

        return seg;
    }

    /**
     * Will wait util a cue is no longer on screen
     *
     * @param cue The cue to search
     * @param timeout maximum time to wait until cue is null
     * @param browserManager The browser manager uset to perform the search
     * @return true if cue was not found otherwise false
     */
    @SuppressWarnings("SameParameterValue")
    static boolean waitForNull(Cue cue, int timeout, BrowserManager browserManager) {
        return waitForNull(cue, timeout, false, browserManager);
    }

    @SuppressWarnings("SameParameterValue")
    static boolean waitForNull(Cue cue, int timeout, Bounds bounds, BrowserManager browserManager) {
        cue = new Cue(cue, bounds);
        return waitForNull(cue, timeout, false, browserManager);
    }

    static boolean waitForNull(Cue cue, int timeout, @SuppressWarnings("SameParameterValue") boolean game, BrowserManager browserManager) {
        long timer = Misc.getTime();
        MarvinSegment seg = findSubimage(browserManager.getImg(), cue, browserManager);

        double delay = INITIAL_DELAY;
        double attemptCnt = 1.0;

        while (seg != null) {
            if ((Misc.getTime() - timer) >= timeout)
                return false;

            browserManager.readScreen((int) delay, game);
            seg = findSubimage(browserManager.getImg(), cue, browserManager);

            attemptCnt += 1.0;
            delay += Math.pow(2.0, attemptCnt) * COEFFICIENT;

            if (delay > MAX_DELAY) delay = MAX_DELAY;

        }

        return true;
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

    static MarvinSegment fromCue(String cueName, Bounds bounds, BrowserManager browserManager) {
        return fromCue(new Cue(BHBot.cues.get(cueName), bounds), 0, true, browserManager);
    }

    /**
     * This method will return a MarviSegment starting from two image files. Use this
     * method during development time to check that the Cues you created works fine.
     *
     * @param imageInFile The main image that will be used to search the Cue
     * @param subImageFile The Cue image you are looking for
     * @param bounds The bounds where to look for the sub Image
     * @return The first found Marvin Segment
     */
    static MarvinSegment fromFiles(File imageInFile, File subImageFile, Bounds bounds) {
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

        int startX = bounds == null ? 0 : bounds.x1;
        int startY = bounds == null ? 0 : bounds.y1;
        int endX = bounds == null ? 0 : bounds.x2;
        int endY = bounds == null ? 0 : bounds.y2;

        return FindSubimage.findImage(imageIn, subImage, startX, startY, endX, endY);
    }

    static MarvinSegment fromFiles(File imageInFile, File subImageFile) {
        return fromFiles(imageInFile, subImageFile, null);
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