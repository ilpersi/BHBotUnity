package com.github.ilpersi.BHBot;

import java.awt.image.BufferedImage;

/**
 * Reusable class to take care of scroll bars
 */
class ScrollBarManager {

    enum barAction {
        NONE,
        SCROLL_UP,
        SCROLL_DOWN
    }

    private final BrowserManager browserManager;
    private final MarvinSegment arrowUp;
    private final MarvinSegment arrowDown;

    private Bounds topPosCheckBounds;
    private Bounds btmPosCheckBounds;
    private Bounds barBounds = null;

    private String topPosMD5 = null;
    private String btmPosMD5 = null;
    private String curPosMD5 = null;

    boolean canScrollDown = false;
    boolean canScrollUp = false;

    private barAction lastAction;
    private final int barMargin = 10;

    ScrollBarManager(BrowserManager browserManager) {
        this.browserManager = browserManager;
        this.arrowDown = MarvinSegment.fromCue("DropDownDown", browserManager);
        this.arrowUp = MarvinSegment.fromCue("DropDownUp", browserManager);
        this.lastAction = barAction.NONE;

        // We check that DropDownDown cue is there
        if (arrowDown == null) {
            BHBot.logger.error("ScrollBarManager not initialized correctly: DropDownDown cue not found!");
        } else {
            canScrollDown = true;
            btmPosCheckBounds = Bounds.fromWidthHeight(arrowDown.x1, arrowDown.y1 - barMargin, arrowDown.width, arrowDown.height + barMargin);
        }

        // We check that DropDownUp cue is there
        if (arrowUp == null) {
            BHBot.logger.error("ScrollBarManager not initialized correctly: DropDownUp cue not found!");
        } else {
            canScrollUp = true;
            topPosCheckBounds = Bounds.fromWidthHeight(this.arrowUp.x1, arrowUp.y1, arrowUp.width, arrowUp.height + barMargin);
        }

        // We get bounds for the whole bar
        if (arrowDown != null && arrowUp != null) {
            barBounds = new Bounds(arrowUp.x1, arrowUp.y1, arrowDown.x2, arrowDown.y2);
            BufferedImage barSubImg = this.browserManager.getImg().getSubimage(barBounds.x1, barBounds.y1, barBounds.width, barBounds.height);
            this.curPosMD5 = Misc.imgToMD5(barSubImg);
        }

    }

    private boolean isScrollLimit() {

        // If we can't scroll, we can't really tell if the bar has reached the possible limit
        if (!canScrollDown || !canScrollUp) return false;

        // We refresh the screen read
        browserManager.readScreen();

        // We get sub images for all the regions (full bar, top region, bottom region)
        BufferedImage barSubImg = this.browserManager.getImg().getSubimage(barBounds.x1, barBounds.y1, barBounds.width, barBounds.height);
        BufferedImage topSubImg = this.browserManager.getImg().getSubimage(topPosCheckBounds.x1, topPosCheckBounds.y1, topPosCheckBounds.width, topPosCheckBounds.height);
        BufferedImage btmSubImg = this.browserManager.getImg().getSubimage(btmPosCheckBounds.x1, btmPosCheckBounds.y1, btmPosCheckBounds.width, btmPosCheckBounds.height);

        // We get MD5 for all the images
        String newPosMD5 = Misc.imgToMD5(barSubImg);
        String newTopMD5 = Misc.imgToMD5(topSubImg);
        String newBtmMD5 = Misc.imgToMD5(btmSubImg);

        // If position string is null, we assume this is the first time this method is called
        if (this.curPosMD5 == null) {
            this.curPosMD5 = newPosMD5;
            this.topPosMD5 = newTopMD5;
            this.btmPosMD5 = newBtmMD5;
            return false;
        }

        // If the bar position signature is not the same, the bar has moved so we did not reach the limit
        if (!newPosMD5.equals(this.curPosMD5)) {
            this.curPosMD5 = newPosMD5;
            this.topPosMD5 = newTopMD5;
            this.btmPosMD5 = newBtmMD5;
            return false;
        }

        // Based on the last action we check if MD5 signatures are the same
        if (lastAction.equals(barAction.SCROLL_UP)) {
            if (newTopMD5.equals(topPosMD5)) {
                return true;
            } else {
                this.curPosMD5 = newPosMD5;
                this.topPosMD5 = newTopMD5;
                return false;
            }

        } else if (lastAction.equals(barAction.SCROLL_DOWN)) {
            if (newBtmMD5.equals(btmPosMD5)) {
                return true;
            } else {
                this.curPosMD5 = newPosMD5;
                this.btmPosMD5 = newBtmMD5;
                return false;
            }
        }

        return false;

    }

    boolean isAtBottom() {
        return lastAction.equals(barAction.SCROLL_DOWN) && isScrollLimit();
    }

    boolean isAtTop() {
        return lastAction.equals(barAction.SCROLL_UP) && isScrollLimit();
    }

    private void clickOnScroll(MarvinSegment scroll, int scrollDelay) {
        this.browserManager.clickOnSeg(scroll);
        this.browserManager.readScreen(scrollDelay); // So to stabilize the screen if required
    }

    void scrollUp(int scrollDelay) {
        this.clickOnScroll(this.arrowUp, scrollDelay);
        this.lastAction = barAction.SCROLL_UP;
    }

    void scrollDown(int scrollDelay) {
        this.clickOnScroll(this.arrowDown, scrollDelay);
        this.lastAction = barAction.SCROLL_DOWN;
    }

    boolean scrollToTop(Runnable scrollCallBack) {
        if (btmPosCheckBounds == null) return false;

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
