package com.github.ilpersi.BHBot;

import java.awt.image.BufferedImage;

/**
 * Reusable class to take care of scroll bars
 */
class ScrollBarManager {
    private final BrowserManager browserManager;
    private final MarvinSegment DropDownUp;
    private final MarvinSegment DropDownDown;

    private Bounds topPosCheckBounds;
    private Bounds bottomPosCheckBounds;

    private String currentPositionString = null;

    boolean canScrollDown = false;
    boolean canScrollUp = false;

    ScrollBarManager(BrowserManager browserManager) {
        this.browserManager = browserManager;
        this.DropDownDown = MarvinSegment.fromCue("DropDownDown", browserManager);
        this.DropDownUp = MarvinSegment.fromCue("DropDownUp", browserManager);

        // We check that DropDownDown cue is there
        if (DropDownDown == null) {
            BHBot.logger.error("ScrollBarManager not initialized correctly: DropDownDown cue not found!");
        } else {
            canScrollDown = true;
            bottomPosCheckBounds = Bounds.fromWidthHeight(DropDownDown.x1 + 10, DropDownDown.y1, DropDownDown.width, DropDownDown.height + 10);
        }

        // We check that DropDownUp cue is there
        if (DropDownUp == null) {
            BHBot.logger.error("ScrollBarManager not initialized correctly: DropDownUp cue not found!");
        } else {
            canScrollUp = true;
            topPosCheckBounds = Bounds.fromWidthHeight(this.DropDownUp.x1, DropDownUp.y1, DropDownUp.width, DropDownUp.height + 10);
        }

    }

    boolean isAtBottom() {
        BufferedImage barSubImg = this.browserManager.getImg().getSubimage(bottomPosCheckBounds.x1, bottomPosCheckBounds.y1, bottomPosCheckBounds.width, bottomPosCheckBounds.height);

        if (this.currentPositionString == null) {
            browserManager.readScreen();
            this.currentPositionString = Misc.imgToMD5(barSubImg);
            return false;
        }

        if (this.currentPositionString.equals(Misc.imgToMD5(barSubImg))) {
            this.currentPositionString = null;
            return true;
        }

        return false;
    }

    boolean isAtTop() {
        BufferedImage barSubImg = this.browserManager.getImg().getSubimage(topPosCheckBounds.x1, topPosCheckBounds.y1, topPosCheckBounds.width, topPosCheckBounds.height);

        if (this.currentPositionString == null) {
            browserManager.readScreen();
            this.currentPositionString = Misc.imgToMD5(barSubImg);
            return false;
        }


        if (this.currentPositionString.equals(Misc.imgToMD5(barSubImg))) {
            this.currentPositionString = null;
            return true;
        }

        return false;
    }

    private void clickOnScroll(MarvinSegment scroll, int scrollDelay) {
        this.browserManager.clickOnSeg(scroll);
        this.browserManager.readScreen(scrollDelay); // So to stabilize the screen if required
    }

    void scrollUp(int scrollDelay) {
        this.clickOnScroll(this.DropDownUp, scrollDelay);
    }

    void scrollDown(int scrollDelay) {
        this.clickOnScroll(this.DropDownDown, scrollDelay);
    }

    boolean scrollToTop(Runnable scrollCallBack) {
        if (bottomPosCheckBounds == null) return false;

        // We scroll up until image signature is always the same
        do {
            this.scrollUp(Misc.Durations.SECOND);
            if (scrollCallBack != null) scrollCallBack.run();
        } while (!isAtTop());

        return true;
    }

    boolean scrollToBottom(Runnable scrollCallBack) {
        if (topPosCheckBounds == null) return false;

        // We scroll down until image signature is always the same
        do {
            this.scrollDown(Misc.Durations.SECOND);
            if (scrollCallBack != null) scrollCallBack.run();
        } while (!isAtBottom());

        return true;

    }
}
