package com.github.ilpersi.BHBot;

/**
 * @author Betalord
 */
class Bounds {
    final int x1, y1, x2, y2, width, height;

    Bounds(int x1, int y1, int x2, int y2) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.width = x2 - x1;
        this.height = y2 - y1;
    }

    /**
     * This is an helper method to create Bounds using width and height. This should be useful to create new Bounds
     * starting from the information that you can get from GIMP tool
     * @param x1 x position on the image
     * @param y1 y position on the image
     * @param width width of the Bounds
     * @param height height of the Bounds
     * @return a new Bounds object created using width and height instead of 4 points (x1, y1, x2, y2)
     */
    static Bounds fromWidthHeight(int x1, int y1, int width, int height) {
        return new Bounds(x1, y1, x1+width, y1+height);
    }

    /**
     * This method will create a Bounds object starting from a seg. This can be useful
     * at development time and in situations where you wat to get the Bounds in a fast
     * way without having to manually calculate them
     *
     * @param seg The starting MarvinSegment
     * @param margin The desired margin from the seg, if null it will default to 10
     * @return A Bounds object containing the seg
     */
    static Bounds fromMarvinSegment(MarvinSegment seg, Integer margin) {
        if (margin == null) margin = 10;

        int suggestedX1 = seg.x1 - margin;
        while ((suggestedX1 % 5) != 0) suggestedX1 -= 1;

        int suggestedY1 = seg.y1 - margin;
        while ((suggestedY1 % 5) != 0) suggestedY1 -= 1;

        int suggestedWidth = seg.x2 - suggestedX1 + margin;
        while ((suggestedWidth % 5) != 0) suggestedWidth += 1;

        int suggestedHeight = seg.y2 - suggestedY1 + margin;
        while ((suggestedHeight % 5) != 0) suggestedHeight += 1;

        // we make sure not to exceed initial boundaries
        if (suggestedX1 < 0) suggestedX1 = 0;
        if (suggestedY1 < 0) suggestedY1 = 0;

        return fromWidthHeight(suggestedX1, suggestedY1, suggestedWidth, suggestedHeight);
    }
}
