package com.github.ilpersi.BHBot;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

class CueManager {
    /**
     * To decrease the memory footprint not all the cue are loaded at initialization time so when a cue is originally
     * added, only its data is saved and the real load is performed later when the cue is accessed for the first time.
     *
     * This class takes care of saving oll the required info of a cue before the first access is done
     */
    private static class CueData {
        private final String cuePath;
        private final Bounds cueBounds;

        CueData(String cuePath, Bounds cueBounds) {
            this.cuePath = cuePath;
            this.cueBounds = cueBounds;
        }
    }

    /**
     * This class is used when loading multiple Cues from a folder and it acts as a temporary class where to store information
     * before using the Cues in the folder (to count them, to add them to the global cue list, etc...)
     */
    static class CueDetails {
        final String name;
        final String path;

        CueDetails(String cueName, String cuePath) {
            this.name = cueName;
            this.path = cuePath;
        }
    }

    private Map<String, CueData> addedCues = new HashMap<>();
    private final Map<String, Cue> loadedCues = new HashMap<>();
    private final ClassLoader classLoader = CueManager.class.getClassLoader();

    CueManager() {
        buildCues();
    }

    private void addCue(String cueKey, String cuePath, Bounds cueBounds) {
        addedCues.put(cueKey, new CueData(cuePath, cueBounds));
    }

    Cue get(String cueKey) {
        if (!loadedCues.containsKey(cueKey)) {
            CueData cueData = addedCues.get(cueKey);

            // We always try to read the cue from the disk and fall back on resources
            BufferedImage cueImg;
            File cueFile = new File(cueData.cuePath);

            if (cueFile.exists() && ! cueFile.isDirectory()) {
                try {
                    cueImg = ImageIO.read(cueFile);
                } catch (IOException e) {
                    BHBotUnity.logger.error("Error when loading image file in CueManger.get", e);
                    return null;
                }
            } else {
                cueImg = loadImage(classLoader, cueData.cuePath);
            }

            loadedCues.put(cueKey, new Cue(cueKey, cueData.cuePath, cueImg, cueData.cueBounds));

            // once we loaded the cue, we don't need the data anymore
            addedCues.remove(cueKey);
        }

        return loadedCues.get(cueKey);
    }

    Cue getOrNull(String cueKey) {
        try {
            return get(cueKey);
        } catch (NullPointerException ex) {
            return null;
        }
    }

    int size () {
        return loadedCues.size();
    }

    static BufferedImage loadImage(ClassLoader classLoader, String f) {
        BufferedImage img = null;
        InputStream resourceURL = classLoader.getResourceAsStream(f);

        if (resourceURL == null) {
            String decodedURL = null;
            try {
                decodedURL = URLDecoder.decode(f, StandardCharsets.UTF_8.toString());
            } catch (UnsupportedEncodingException e) {
                BHBotUnity.logger.error("Error while decoding URL: ", e);
            }

            if (decodedURL != null) {
                resourceURL = classLoader.getResourceAsStream(decodedURL);
            }
            BHBotUnity.logger.trace("Encoded IMG URI is: " + decodedURL);
        }

        if (resourceURL != null) {
            try {
                img = ImageIO.read(resourceURL);
            } catch (IOException e) {
                BHBotUnity.logger.error("Error while loading Image", e);
            }
        } else {
            BHBotUnity.logger.error("Error with resource: " + f);
        }

        return img;
    }

    /**
     * Given an origin folder, this nethod will return cueDetails for all the cues that are part of that folder
     *
     * @param cuesPath The path where to search for PNG cues
     * @return  An ArrayList of CueDetails with name and path for each of the found Cue
     */
    static ArrayList<CueDetails> getCueDetailsFromPath(String cuesPath) {
        ArrayList<CueDetails> cueDetails = new ArrayList<>();

        // We make sure that the last char of the path is a folder separator
        if (!"/".equals(cuesPath.substring(cuesPath.length() - 1))) cuesPath += "/";

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        URL url = classLoader.getResource(cuesPath);
        if (url != null) { // Run from the IDE
            if ("file".equals(url.getProtocol())) {

                InputStream in = classLoader.getResourceAsStream(cuesPath);
                if (in == null) {
                    BHBotUnity.logger.error("Impossible to create InputStream in getCueDetails");
                    return cueDetails;
                }

                BufferedReader br = new BufferedReader(new InputStreamReader(in));
                String resource;

                while (true) {
                    try {
                        resource = br.readLine();
                        if (resource == null) break;
                    } catch (IOException e) {
                        BHBotUnity.logger.error("Error while reading resources in getCueDetails", e);
                        continue;
                    }
                    int dotPosition = resource.lastIndexOf('.');
                    String fileExtension = dotPosition > 0 ? resource.substring(dotPosition + 1) : "";
                    if ("png".equalsIgnoreCase(fileExtension)) {
                        String cueName = resource.substring(0, dotPosition);

                        CueDetails details = new CueDetails(cueName.toLowerCase(), cuesPath + resource);
                        cueDetails.add(details);
                    }
                }
            } else if ("jar".equals(url.getProtocol())) { // Run from JAR
                BHBotUnity.logger.debug("Reading JAR File for cues in path " + cuesPath);
                String path = url.getPath();
                String jarPath = path.substring(5, path.indexOf("!"));

                String decodedURL;
                try {
                    decodedURL = URLDecoder.decode(jarPath, StandardCharsets.UTF_8.name());
                } catch (UnsupportedEncodingException e) {
                    BHBotUnity.logger.error("Impossible to decode pat for jar: " + jarPath, e);
                    return cueDetails;
                }

                JarFile jar;
                try {
                    jar = new JarFile(decodedURL);
                } catch (IOException e) {
                    BHBotUnity.logger.error("Impossible to open JAR file : " + decodedURL, e);
                    return cueDetails;
                }

                Enumeration<JarEntry> entries = jar.entries();

                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (name.startsWith(cuesPath) && !cuesPath.equals(name)) {
                        URL resource = classLoader.getResource(name);

                        if (resource == null) continue;

                        String resourcePath = resource.toString();
                        BHBotUnity.logger.trace("resourcePath: " + resourcePath);
                        if (!resourcePath.contains("!")) {
                            BHBotUnity.logger.warn("Unexpected resource filename in load Cue Folder");
                            continue;
                        }

                        String[] fileDetails = resourcePath.split("!");
                        String resourceRelativePath = fileDetails[1];
                        BHBotUnity.logger.trace("resourceRelativePath : " + resourceRelativePath);
                        int lastSlashPosition = resourceRelativePath.lastIndexOf('/');
                        String fileName = resourceRelativePath.substring(lastSlashPosition + 1);

                        int dotPosition = fileName.lastIndexOf('.');
                        String fileExtension = dotPosition > 0 ? fileName.substring(dotPosition + 1) : "";
                        if ("png".equalsIgnoreCase(fileExtension)) {
                            String cueName = fileName.substring(0, dotPosition);

                            BHBotUnity.logger.trace("cueName: " + cueName.toLowerCase());

                            // resourceRelativePath begins with a '/' char and we want to be sure to remove it
                            CueDetails details = new CueDetails(cueName.toLowerCase(), resourceRelativePath.substring(1));
                            cueDetails.add(details);
                        }
                    }
                }

            }
        }
        
        return cueDetails;
    }

    @SuppressWarnings("SameParameterValue")
    int loadCueFolder(String cuesPath, String prefix, boolean stripCueStr, Bounds bounds) {
        int totalLoaded = 0;
        
        ArrayList<CueDetails> cueDetails = CueManager.getCueDetailsFromPath(cuesPath);
        if (cueDetails.size() > 0) {
            totalLoaded += cueDetails.size();

            for (CueDetails details : cueDetails) {
                String cueName = details.name;
                if (prefix != null) cueName = prefix + cueName;
                if (stripCueStr) cueName = cueName.replace("cue", "");

                addCue(cueName, details.path, bounds);
            }
        }

        return totalLoaded;
    }

    private void buildCues() {
        addCue("Main", "cues/cueMainScreen.png", new Bounds(90, 5, 100, 20));
        addCue("Login", "cues/cueLogin.png", new Bounds(270, 260, 330, 300)); // login window (happens seldom)
        addCue("AreYouThere", "cues/cueAreYouThere.png", Bounds.fromWidthHeight(250, 240, 300, 45));
        addCue("Yes", "cues/cueYes.png", null);

        addCue("Reconnect", "cues/cueReconnectButton.png", new Bounds(320, 330, 400, 360)); // used with "You have been disconnected" dialog and also with the "maintenance" dialog
        addCue("Reload", "cues/cueReload.png", new Bounds(320, 330, 360, 360)); // used in "There is a new update required to play" dialog (happens on Friday night)
        addCue("Maintenance", "cues/cueMaintenance.png", new Bounds(230, 200, 320, 250)); // cue for "Bit Heroes is currently down for maintenance. Please check back shortly!"
        addCue("Loading", "cues/cueLoading.png", new Bounds(315, 210, 330, 225)); // cue for "Loading" superimposed screen
        addCue("RecentlyDisconnected", "cues/cueRecentlyDisconnected.png", new Bounds(250, 195, 535, 320)); // cue for "You were recently disconnected from a dungeon. Do you want to continue the dungeon?" window
        addCue("UnableToConnect", "cues/cueUnableToConnect.png", new Bounds(245, 235, 270, 250)); // happens when some error occurs for which the flash app is unable to connect to the server. We must simply click on the "Reconnect" button in this case!
        addCue("GearCheck", "cues/cueGearCheck.png", Bounds.fromWidthHeight(244, 208, 314, 120));
        addCue("Selector", "cues/cueSelector.png", Bounds.fromWidthHeight(295, 55, 205, 45));
        addCue("LightBlueSelect", "cues/cueLightBlueSelect.png", Bounds.fromWidthHeight(255, 410, 90, 25));

        addCue("DailyRewards", "cues/cueDailyRewards.png", new Bounds(260, 45, 285, 75));
        addCue("Claim", "cues/cueClaim.png", null); // claim button, when daily rewards popup is open
        addCue("Items", "cues/cueItems.png", null); // used when we clicked "claim" on daily rewards popup. Used also with main menu ads.
        addCue("X", "cues/cueX.png", null); // "X" close button used with claimed daily rewards popup

        addCue("News", "cues/cueNewsPopup.png", new Bounds(345, 60, 365, 85)); // news popup
        addCue("Close", "cues/cueClose.png", null); // close button used with "News" popup, also when defeated in dungeon, etc.

        addCue("EnergyBar", "cues/cueEnergyBar.png", new Bounds(390, 0, 420, 20));
        addCue("TicketBar", "cues/cueTicketBar.png", new Bounds(540, 0, 770, 20));

        addCue("NotEnoughShards", "cues/cueNotEnoughShards.png", Bounds.fromWidthHeight(265, 215, 270, 70));

        // New Raid level detection logic
        addCue("Raid1Name", "cues/raid/r1Name.png", new Bounds(185, 340, 485, 395));// Raid 1 Name
        addCue("R1Only", "cues/cueR1Only.png", null); // cue for R1 type selected when R2 (and R3) is not open yet (in that case it won't show raid type selection buttons)
        addCue("Normal", "cues/cueNormal.png", null);
        addCue("Hard", "cues/cueHard.png", null);
        addCue("Heroic", "cues/cueHeroic.png", null);
        addCue("Accept", "cues/cueAccept.png", null);
        addCue("D4Accept", "cues/cueD4Accept.png", null);
        addCue("Cleared", "cues/cueCleared.png", new Bounds(208, 113, 608, 394)); // used for example when raid has been finished
        addCue("Rerun", "cues/cueRerun.png", null); // used for example when raid has been finished ("Cleared" popup)
        addCue("View", "cues/cueView.png", new Bounds(390, 415, 600, 486));
        addCue("Bribe", "cues/cueBribe.png", new Bounds(505, 305, 684, 375));
        addCue("SkeletonTreasure", "cues/cueSkeletonTreasure.png", new Bounds(185, 165, 295, 280)); // skeleton treasure found in dungeons (it's a dialog/popup cue)
        addCue("SkeletonNoKeys", "cues/cueSkeletonNoKeys.png", new Bounds(478, 318, 500, 348)); // red 0

        addCue("Open", "cues/cueOpen.png", null); // skeleton treasure open button
        addCue("Decline", "cues/cueDecline.png", null); // decline skeleton treasure button (found in dungeons), also with video ad treasures (found in dungeons)
        addCue("DeclineRed", "cues/cueDeclineRed.png", null); // decline persuation attempts
        addCue("Merchant", "cues/cueMerchant.png", null); // cue for merchant dialog/popup
        addCue("SettingsGear", "cues/cueSettingsGear.png", new Bounds(655, 450, 730, 515)); // settings button

        addCue("Team", "cues/cueTeam.png", null); // Team text part of pop-ups about teams
        addCue("TeamNotFull", "cues/cueTeamNotFull.png", new Bounds(230, 200, 330, 250)); // warning popup when some friend left you and your team is not complete anymore
        addCue("TeamNotOrdered", "cues/cueTeamNotOrdered.png", new Bounds(230, 190, 350, 250)); // warning popup when some guild member left and your GvG team is not complete anymore
        addCue("GuildLeaveConfirm", "cues/cueGuildLeaveConfirm.png", new Bounds(195, 105, 605, 395)); // GVG confirm
        addCue("DisabledBattles", "cues/cueDisabledBattles.png", new Bounds(240, 210, 560, 330)); // Disabled Battles Poppup

        addCue("No", "cues/cueNo.png", null); // cue for a blue "No" button used for example with "Your team is not full" dialog, or for "Replace consumable" dialog, etc. This is why we can't put concrete borders as position varies a lot.
        addCue("AutoTeam", "cues/cueAutoTeam.png", null); // "Auto" button that automatically assigns team (in raid, GvG, ...)
        addCue("Clear", "cues/cueClear.png", null); //clear team button

        addCue("AutoOn", "cues/cueAutoOn.png", new Bounds(740, 180, 785, 220)); // cue for auto pilot on
        addCue("AutoOff", "cues/cueAutoOff.png", new Bounds(740, 180, 785, 220)); // cue for auto pilot off
        addCue("Speed_Full", "cues/Speed_Full.png", new Bounds(7, 488, 65, 504)); // 3/3 speed bar in encounters
        addCue("Speed", "cues/Speed_Text.png", new Bounds(20, 506, 61, 518)); // speed text label in encounters


        addCue("Play", "cues/cuePlay.png", null); // cue for play button in trials/gauntlet window
        addCue("TokenBar", "cues/cueTokenBar.png", Bounds.fromWidthHeight(310, 40, 65, 55));
        addCue("CloseGreen", "cues/cueCloseGreen.png", null); // close button used with "You have been defeated" popup in gauntlet and also "Victory" window in gauntlet

        addCue("UhOh", "cues/cueUhoh.png", new Bounds(319, 122, 526, 184));
        addCue("ReviveAverage", "cues/cueReviveAverage.png", null);
        addCue("Purchase", "cues/cuePurchase.png", new Bounds(240, 240, 390, 280));

        addCue("GuildButton", "cues/cueGuildButton.png", new Bounds(500, 420, 590, 518));
        addCue("IgnoreCheck", "cues/cueIgnoreCheck.png", null);

        addCue("ZonesButton", "cues/cueZonesButton.png", new Bounds(105, 60, 125, 75));
        addCue("Zone1", "cues/cueZone1.png", Bounds.fromWidthHeight(290, 45, 220, 55));
        addCue("Zone2", "cues/cueZone2.png", null);
        addCue("Zone3", "cues/cueZone3.png", null);
        addCue("Zone4", "cues/cueZone4.png", null);
        addCue("Zone5", "cues/cueZone5.png", null);
        addCue("Zone6", "cues/cueZone6.png", null);
        addCue("Zone7", "cues/cueZone7.png", null);
        addCue("Zone8", "cues/cueZone8.png", Bounds.fromWidthHeight(305, 45, 185, 50));
        addCue("Zone9", "cues/cueZone9.png", null);
        addCue("Zone10", "cues/cueZone10.png", null);
        addCue("Zone11", "cues/cueZone11.png", null);
        addCue("Zone12", "cues/cueZone12.png", null);
        addCue("Zone13", "cues/cueZone13.png", Bounds.fromWidthHeight(250, 50, 300, 40));
        addCue("Enter", "cues/cueEnter.png", null); // "Enter" button found on d4 window
        addCue("NotEnoughEnergy", "cues/cueNotEnoughEnergy.png", new Bounds(260, 210, 290, 235)); // "Not enough Energy" popup cue

        addCue("PVP", "cues/cuePVP.png", new Bounds(0, 70, 40, 110)); // PVP icon in main screen
        addCue("Fight", "cues/cueFight.png", null); // fight button in PVP window
        addCue("PVPWindow", "cues/cuePVPWindow.png", null); // PVP window cue

        addCue("DialogRight", "cues/cueDialogRight.png", new Bounds(675, 205, 690, 250)); // cue for the dialog window (when arrow is at the right side of the window)
        addCue("DialogLeft", "cues/cueDialogLeft.png", new Bounds(100, 205, 125, 250)); // cue for the dialog window (when arrow is at the left side of the window)

        addCue("Switch", "cues/cueSwitch.png", new Bounds(0, 450, 100, 520)); //unused

        // GVG related:
        addCue("GVG", "cues/cueGVG.png", null); // main GVG button cue
        addCue("BadgeBar", "cues/cueBadgeBar.png", Bounds.fromWidthHeight(315, 40, 60, 55));
        addCue("GVGWindow", "cues/cueGVGWindow.png", new Bounds(260, 90, 280, 110)); // GVG window cue

        addCue("InGamePM", "cues/cueInGamePM.png", new Bounds(450, 330, 530, 380)); // note that the guild window uses the same cue! That's why it's important that user doesn't open guild window while bot is working!

        addCue("TrialsOrGauntletWindow", "cues/cueTrialsOrGauntletWindow.png", new Bounds(300, 30, 510, 105)); // cue for a trials/gauntlet window
        addCue("NotEnoughTokens", "cues/cueNotEnoughTokens.png", Bounds.fromWidthHeight(274, 228, 253, 79)); // cue to check for the not enough tokens popup

        addCue("DifficultyDropDown", "cues/cueDifficultyDropDown.png", new Bounds(260, 50, 550, 125)); // difficulty drop down menu cue
        addCue("DifficultyExpedition", "cues/cueDifficultyExpedition.png", null); // selected difficulty in trials/gauntlet window
        addCue("SelectDifficultyExpedition", "cues/cueSelectDifficultyExpedition.png", null);
        addCue("Cost", "cues/cueCost.png", new Bounds(400, 150, 580, 240)); // used both for PvP and Gauntlet/Trials costs. Note that bounds are very wide, because position of this cue in PvP is different from that in Gauntlet/Trials!
        addCue("SelectCost", "cues/cueSelectCost.png", new Bounds(555, 170, 595, 205)); // cue for select cost found in both PvP and Gauntlet/Trials windows. Note that bounds are wide, because position of this cue in PvP is different from that in Gauntlet/Trials!
        addCue("CostDropDown", "cues/cueCostDropDown.png", new Bounds(260, 45, 320, 70)); // cue for cost selection drop down window
        addCue("0", "cues/numbers/cue0.png", null);
        addCue("1", "cues/numbers/cue1.png", null);
        addCue("2", "cues/numbers/cue2.png", null);
        addCue("3", "cues/numbers/cue3.png", null);
        addCue("4", "cues/numbers/cue4.png", null);
        addCue("5", "cues/numbers/cue5.png", null);
        addCue("6", "cues/numbers/cue6.png", null);
        addCue("7", "cues/numbers/cue7.png", null);
        addCue("8", "cues/numbers/cue8.png", null);
        addCue("9", "cues/numbers/cue9.png", null);

        // Difficulty Tier
        addCue("hyphen", "cues/numbers/hyphen.png", null);

        // Invasion Level Numbers
        addCue("small0", "cues/numbers/small0.png", null);
        addCue("small1", "cues/numbers/small1.png", null);
        addCue("small2", "cues/numbers/small2.png", null);
        addCue("small3", "cues/numbers/small3.png", null);
        addCue("small4", "cues/numbers/small4.png", null);
        addCue("small5", "cues/numbers/small5.png", null);
        addCue("small6", "cues/numbers/small6.png", null);
        addCue("small7", "cues/numbers/small7.png", null);
        addCue("small8", "cues/numbers/small8.png", null);
        addCue("small9", "cues/numbers/small9.png", null);

        // T/G Gauntlet difficulty related
        addCue("ScrollerNone", "cues/cueScrollerNone.png", Bounds.fromWidthHeight(525, 120, 30, 330));


        // PvP strip related:
        addCue("StripScrollerTopPos", "cues/strip/cueStripScrollerTopPos.png", new Bounds(525, 140, 540, 370));
        addCue("StripEquipped", "cues/strip/cueStripEquipped.png", new Bounds(465, 180, 485, 200)); // the little "E" icon upon an equipped item (the top-left item though, we want to detect just that one)
        addCue("StripItemsTitle", "cues/strip/cueStripItemsTitle.png", new Bounds(335, 70, 360, 80));
        addCue("StripSelectorButton", "cues/strip/cueStripSelectorButton.png", new Bounds(450, 115, 465, 130));

        // filter titles:
        addCue("StripTypeBody", "cues/strip/cueStripTypeBody.png", new Bounds(460, 125, 550, 140));
        addCue("StripTypeHead", "cues/strip/cueStripTypeHead.png", new Bounds(460, 125, 550, 140));
        addCue("StripTypeMainhand", "cues/strip/cueStripTypeMainhand.png", new Bounds(460, 125, 550, 140));
        addCue("StripTypeOffhand", "cues/strip/cueStripTypeOffhand.png", new Bounds(460, 125, 550, 140));
        addCue("StripTypeNeck", "cues/strip/cueStripTypeNeck.png", new Bounds(460, 125, 550, 140));
        addCue("StripTypeRing", "cues/strip/cueStripTypeRing.png", new Bounds(460, 125, 550, 140));

        // consumables management related:
        addCue("BonusExp", "cues/cueBonusExp.png", new Bounds(100, 455, 370, 485)); // consumable icon in the main menu (when it's being used)
        addCue("BonusItem", "cues/cueBonusItem.png", new Bounds(100, 455, 370, 485));
        addCue("BonusGold", "cues/cueBonusGold.png", new Bounds(100, 455, 370, 485));
        addCue("BonusSpeed", "cues/cueBonusSpeed.png", new Bounds(100, 455, 370, 485));
        addCue("ConsumableExpAverage", "cues/cueConsumableExpAverage.png", null);
        addCue("ConsumableExpMajor", "cues/cueConsumableExpMajor.png", null);
        addCue("ConsumableItemAverage", "cues/cueConsumableItemAverage.png", null);
        addCue("ConsumableItemMajor", "cues/cueConsumableItemMajor.png", null);
        addCue("ConsumableSpeedAverage", "cues/cueConsumableSpeedAverage.png", null);
        addCue("ConsumableSpeedMajor", "cues/cueConsumableSpeedMajor.png", null);
        addCue("ConsumableGoldAverage", "cues/cueConsumableGoldAverage.png", null);
        addCue("ConsumableGoldMajor", "cues/cueConsumableGoldMajor.png", null);
        addCue("ConsumablePumkgor", "cues/cueConsumablePumkgor.png", new Bounds(150, 460, 205, 519)); // Special Halloween consumable
        addCue("ConsumableGingernaut", "cues/cueConsumableGingernaut.png", new Bounds(150, 460, 205, 519)); // Special Chrismast consumable
        addCue("ConsumableGreatFeast", "cues/cueConsumableGreatFeast.png", new Bounds(150, 460, 205, 519)); // Thanksgiving consumable
        addCue("ConsumableBroccoli", "cues/cueConsumableBroccoli.png", new Bounds(150, 460, 205, 519)); // Special Halloween consumable
        addCue("ConsumableCoco", "cues/cueConsumableCoco.png", new Bounds(150, 460, 205, 519)); // Special ?? consumable
        addCue("ConsumableTitle", "cues/cueConsumableTitle.png", new Bounds(280, 100, 310, 180)); // cue for title of the window that pops up when we want to consume a consumable. Note that vertical range is big here since sometimes is higher due to greater window size and sometimes is lower.
        addCue("FilterConsumables", "cues/cueFilterConsumables.png", new Bounds(460, 125, 550, 140)); // cue for filter button name
        addCue("LoadingInventoryIcon", "cues/cueLoadingInventoryIcon.png", null); // cue for loading animation for the icons inside inventory

        // rune management related:
        addCue("CharacterMenu", "cues/cueCharacter.png", Bounds.fromWidthHeight(0, 465, 60, 55));
        addCue("Runes", "cues/cueRunes.png", new Bounds(120, 450, 245, 495)); // runes button in profile
        addCue("RunesLayout", "cues/cueRunesLayout.png", new Bounds(340, 70, 460, 110)); // runes layout header
        addCue("RunesPicker", "cues/runes/cueRunesPicker.png", Bounds.fromWidthHeight(335, 115, 130, 55)); // rune picker
        addCue("RunesSwitch", "cues/cueRunesSwitch.png", new Bounds(320, 260, 480, 295)); // rune picker

        // All minor rune cues
        for (AutoRuneManager.MinorRune rune : AutoRuneManager.MinorRune.values()) {
            addCue(rune.getRuneCueName(), rune.getRuneCueFileName(), null);
            addCue(rune.getRuneSelectCueName(), rune.getRuneSelectCueFileName(), new Bounds(235, 185, 540, 350));
        }

        // invasion related:
        addCue("Invasion", "cues/cueInvasion.png", null);
        addCue("InvasionWindow", "cues/cueInvasionWindow.png", new Bounds(260, 90, 280, 110)); // GVG window cue

        // Expedition related:
        addCue("ExpeditionButton", "cues/cueExpeditionButton.png", null);
        addCue("Expedition1", "cues/expedition/cueExpedition1Hallowed.png", new Bounds(168, 34, 628, 108)); // Hallowed Expedtion Title
        addCue("Expedition2", "cues/expedition/cueExpedition2Inferno.png", new Bounds(200, 40, 600, 100)); //Inferno Expedition
        addCue("Expedition3", "cues/expedition/cueExpedition3Jammie.png", new Bounds(230, 40, 565, 100)); //Jammie Dimension
        addCue("Expedition4", "cues/expedition/cueExpedition4Idol.png", new Bounds(230, 40, 565, 100)); //Idol Dimension
        addCue("Expedition5", "cues/expedition/cueExpedition5BattleBards.png", new Bounds(230, 40, 565, 100)); //Battle Bards!
        addCue("PortalBorderLeaves", "cues/expedition/portalBorderLeaves.png", new Bounds(48, 447, 107, 503));

        //WorldBoss Related
        addCue("WorldBoss", "cues/worldboss/cueWorldBoss.png", Bounds.fromWidthHeight(5, 235, 40, 55));
        addCue("Start", "cues/cueStart.png", null);

        //fishing related
        addCue("FishingButton", "cues/cueFishingButton.png", null);
        addCue("Exit", "cues/cueExit.png", null);
        addCue("Fishing", "cues/cueFishing.png", new Bounds(720, 200, 799, 519));
        addCue("FishingClose", "cues/fishingClose.png", null);
        addCue("Trade", "cues/cueTrade.png", new Bounds(360, 443, 441, 468));
        addCue("Hall", "cues/cueHall.png", new Bounds(575, 455, 645, 480));
        addCue("GuildHallC", "cues/cueGuildHallC.png", new Bounds(750, 55, 792, 13));

        //Familiar bribing cues
        addCue("NotEnoughGems", "cues/cueNotEnoughGems.png", null); // used when not enough gems are available

        //AutoRevive cues
        addCue("Potions", "cues/autorevive/cuePotions.png", new Bounds(0, 370, 90, 460)); //Potions button
        addCue("NoPotions", "cues/autorevive/cueNoPotions.png", new Bounds(210, 190, 590, 350)); // The team does not need revive
        addCue("Restores", "cues/autorevive/cueRestores.png", new Bounds(145, 320, 655, 395)); // To identify revive and healing potions
        addCue("Revives", "cues/autorevive/cueRevives.png", new Bounds(145, 320, 655, 395)); // To identify revive potions
        addCue("MinorAvailable", "cues/autorevive/cueMinorAvailable.png", new Bounds(170, 205, 270, 300));
        addCue("AverageAvailable", "cues/autorevive/cueAverageAvailable.png", new Bounds(350, 205, 450, 300));
        addCue("MajorAvailable", "cues/autorevive/cueMajorAvailable.png", new Bounds(535, 205, 635, 300));
        addCue("SuperAvailable", "cues/autorevive/cueSuperAvailable.png", new Bounds(140, 150, 300, 200));
        addCue("UnitSelect", "cues/autorevive/cueUnitSelect.png", new Bounds(130, 20, 680, 95));
        addCue("ScrollerRightDisabled", "cues/autorevive/cueScrollerRightDisabled.png", Bounds.fromWidthHeight(646, 425, 18, 18));
        addCue("GravestoneHighlighted", "cues/autorevive/highlighted_gravestone.png", new Bounds(50, 230, 340, 400));

        //Items related cues
        addCue("ItemHer", "cues/items/cueItemHer.png", null); // Heroic Item border
        addCue("ItemLeg", "cues/items/cueItemLeg.png", null); // Legendary Item border
        addCue("ItemSet", "cues/items/cueItemSet.png", null); // Set Item border
        addCue("ItemMyt", "cues/items/cueItemMyt.png", null); // Mythical Item border
        //legendary
        addCue("Material_R11", "cues/items/material_r11.png", null);
        addCue("Material_R10", "cues/items/material_r10.png", null);
        addCue("Material_R9", "cues/items/material_r9.png", null);
        addCue("Material_R8", "cues/items/material_r8.png", null);
        addCue("Material_R7", "cues/items/material_r7.png", null);
        addCue("Material_R6", "cues/items/material_r6.png", null);
        addCue("Material_R5", "cues/items/material_r5.png", null);
        addCue("Material_R4", "cues/items/material_r4.png", null);
        addCue("Material_R3", "cues/items/material_r3.png", null);
        addCue("Material_R2", "cues/items/material_r2.png", null);
        //heroic
        addCue("HeroicSchematic", "cues/items/heroic_schematic.png", null);
        addCue("MicroChip", "cues/items/microchip.png", null);
        addCue("GoldCoin", "cues/items/goldcoin.png", null);
        addCue("DemonBlood", "cues/items/demon_blood.png", null);
        addCue("HobbitsFoot", "cues/items/hobbits_foot.png", null);
        addCue("MelvinChest", "cues/items/melvin_chest.png", null);
        addCue("NeuralNetRom", "cues/items/neural_net_rom.png", null);
        addCue("ScarlargSkin", "cues/items/scarlarg_skin.png", null);

        //weekly reward cues
        //these include the top of the loot window so they aren't triggered by the text in the activity panel
        addCue("PVP_Rewards", "cues/weeklyrewards/pvp.png", new Bounds(290, 130, 510, 160));
        addCue("Trials_Rewards", "cues/weeklyrewards/trials.png", new Bounds(290, 130, 510, 160));
        addCue("Trials_Rewards_Large", "cues/weeklyrewards/trials_large.png", new Bounds(290, 50, 510, 130));
        addCue("Gauntlet_Rewards", "cues/weeklyrewards/gauntlet.png", new Bounds(290, 130, 510, 160));
        addCue("Gauntlet_Rewards_Large", "cues/weeklyrewards/gauntlet_large.png", new Bounds(290, 50, 510, 130));
        addCue("GVG_Rewards", "cues/weeklyrewards/gvg.png", new Bounds(290, 130, 510, 160));
        addCue("Invasion_Rewards", "cues/weeklyrewards/invasion.png", new Bounds(290, 130, 510, 160));
        addCue("Expedition_Rewards", "cues/weeklyrewards/expedition.png", new Bounds(290, 130, 510, 160));
        addCue("Fishing_Rewards", "cues/weeklyrewards/fishing.png", new Bounds(290, 130, 510, 160));

        int newFamCnt = loadCueFolder("cues/familiars/01 Common", null, false, new Bounds(145, 50, 575, 125));
        newFamCnt += loadCueFolder("cues/familiars/02 Rare", null, false, new Bounds(145, 50, 575, 125));
        newFamCnt += loadCueFolder("cues/familiars/03 Epic", null, false, new Bounds(145, 50, 575, 125));
        newFamCnt += loadCueFolder("cues/familiars/04 Legendary", null, false, new Bounds(145, 50, 575, 125));
        BHBotUnity.logger.debug("Found " + newFamCnt + " familiar cues.");

        // We build Unity Cues on top of the standard ones
        updateUnityCues();
    }

    /**
     * This method is taking care of managing new cues for the Unity engine. The logic is as follow:
     * - new cues can be added using a new cueKey
     * - flash cues can be overwritten using the same cueKey
     */
    void updateUnityCues() {
        //region Autoshrine
        addCue("Settings", "unitycues/autoShrine/cueSettings.png", Bounds.fromWidthHeight(365, 105, 60, 80)); // settings menu
        addCue("IgnoreCheck", "unitycues/autoShrine/cueIgnoreCheck.png", null); // Green check used on the Ignore options
        //endregion

        //region AutoConsume
        addCue("BonusExp", "unitycues/autoConsume/cueBonusExp.png", Bounds.fromWidthHeight(200, 470, 190, 45)); // consumable icon in the main menu (when it's being used)
        addCue("BonusItem", "unitycues/autoConsume/cueBonusItem.png", Bounds.fromWidthHeight(200, 470, 190, 45));
        addCue("BonusGold", "unitycues/autoConsume/cueBonusGold.png", Bounds.fromWidthHeight(200, 470, 190, 45));
        addCue("BonusSpeed", "unitycues/autoConsume/cueBonusSpeed.png", Bounds.fromWidthHeight(200, 470, 190, 45));
        addCue("ConsumableTitle", "unitycues/autoConsume/cueConsumableTitle.png", Bounds.fromWidthHeight(305, 130, 195, 55));

        addCue("ConsumableExpMinor", "unitycues/autoConsume/consumables/cueConsumableExpMinor.png", null);
        addCue("ConsumableSpeedMinor", "unitycues/autoConsume/consumables/cueConsumableSpeedMinor.png", null);
        addCue("ConsumableGoldMinor", "unitycues/autoConsume/consumables/cueConsumableGoldMinor.png", null);
        addCue("ConsumableItemMinor", "unitycues/autoConsume/consumables/cueConsumableItemMinor.png", null);

        addCue("Filter", "unitycues/autoConsume/cueFilter.png", Bounds.fromWidthHeight(505, 90, 150, 60)); // Orange Filter button on character screen
        addCue("FilterTitle", "unitycues/autoConsume/cueFilterTitle.png", Bounds.fromWidthHeight(320, 110, 155, 70)); // Filter title after you click the orange button
        addCue("ConsumablesBtn", "unitycues/autoConsume/cueConsumablesBtn.png", Bounds.fromWidthHeight(405, 255, 205, 50)); // Consumables Button in filter menu
        addCue("ConsumableHaveFun", "unitycues/autoConsume/cueConsumableHaveFun.png", Bounds.fromWidthHeight(300, 70, 200, 50)); // Have Fun text on top of the consumable confirmation screen
        addCue("ConsumableDone", "unitycues/autoConsume/cueConsumableDone.png", Bounds.fromWidthHeight(340, 445, 120, 55)); // Green Done button after the consumable is used
        //endregion

        //region Blockers
        addCue("News", "unitycues/blockers/cueNewsPopup.png", Bounds.fromWidthHeight(345, 50, 110, 85)); // news popup
        addCue("UhOh", "unitycues/blockers/cueUhoh.png", Bounds.fromWidthHeight(325, 120, 150, 80)); // UH OH Popup title
        addCue("Disconnected", "unitycues/blockers/cueDisconnected.png", Bounds.fromWidthHeight(290, 220, 235, 90)); // cue for "You have been disconnected" popup
        addCue("Reconnect", "unitycues/blockers/cueReconnect.png", Bounds.fromWidthHeight(325, 335, 150, 55)); // used with "You have been disconnected" dialog and also with the "maintenance" dialog
        addCue("NewsClose", "unitycues/blockers/cueNewsClose.png", Bounds.fromWidthHeight(431, 448, 88, 28)); // close button used with "News" popup
        addCue("DailyRewards", "unitycues/blockers/cueDailyRewards.png", Bounds.fromWidthHeight(245, 40, 310, 80)); // Daily Reward Claim Screen
        addCue("Claim", "unitycues/blockers/cueClaim.png", Bounds.fromWidthHeight(340, 430, 120, 55)); // Daily Reward green claim button
        addCue("Items", "unitycues/blockers/cueItems.png", Bounds.fromWidthHeight(325, 105, 145, 80)); // Imes Cue for daily reward
        addCue("RecentlyDisconnected", "unitycues/blockers/cueRecentlyDisconnected.png", Bounds.fromWidthHeight(260, 195, 280, 135));
        addCue("NotInAGuild", "unitycues/blockers/cueNotInAGuild.png", Bounds.fromWidthHeight(255, 215, 290, 90)); // You are currently non in a Guild popup
        addCue("Connecting", "unitycues/blockers/cueConnecting.png", Bounds.fromWidthHeight(290, 215, 215, 85)); // Connecting to server popup
        //endregion

        //region Bounties
        addCue("Bounties", "unitycues/bounties/cueBounties.png", Bounds.fromWidthHeight(305, 55, 190, 55)); // Bounties dialog title
        addCue("Loot", "unitycues/bounties/cueLoot.png", Bounds.fromWidthHeight(495, 235, 100, 50)); // Green Loot button
        //endregion Bounties

        //region Character Menu
        addCue("StripSelectorButton", "unitycues/characterMenu/cueStripSelectorButton.png", Bounds.fromWidthHeight(445, 105, 255, 65));
        addCue("FilterConsumables", "unitycues/characterMenu/cueFilterConsumables.png", Bounds.fromWidthHeight(460, 110, 190, 50));
        addCue("StripItemsTitle", "unitycues/characterMenu/cueStripItemsTitle.png", Bounds.fromWidthHeight(460, 110, 190, 50));
        addCue("Runes", "unitycues/characterMenu/cueRunes.png", Bounds.fromWidthHeight(110, 460, 95, 50)); // The purple rune button in character menu.
        //endregion

        //region Common
        addCue("OrangeSelect", "unitycues/blockers/cueOrangeSelect.png", Bounds.fromWidthHeight(455, 410, 95, 30)); // Orange select used to choose the Unity engine
        addCue("Close", "unitycues/common/cueClose.png", null); // close button when defeated in dungeon, etc.
        addCue("X", "unitycues/common/cueX.png", null); // "X" close button used in many different places
        addCue("YesGreen", "unitycues/common/cueYesGreen.png", null); // used for example when raid has been finished ("Cleared" popup)
        addCue("Team", "unitycues/common/cueTeam.png", Bounds.fromWidthHeight(340, 120, 120, 80)); // Team text part of pop-ups about teams
        addCue("TeamNotFull", "unitycues/common/cueTeamNotFull.png", Bounds.fromWidthHeight(265, 205, 280, 120)); // warning popup when some friend left you and your team is not complete anymore
        addCue("No", "unitycues/common/cueNo.png", Bounds.fromWidthHeight(435, 335, 65, 50)); // cue for a blue "No" button used for example with "Your team is not full" dialog, or for "Replace consumable" dialog, etc. This is why we can't put concrete borders as position varies a lot.
        addCue("AutoTeam", "unitycues/common/cueAutoTeam.png", null); // "Auto" button that automatically assigns team (in raid, GvG, ...)
        addCue("Cleared", "unitycues/common/cueCleared.png", Bounds.fromWidthHeight(320, 120, 160, 80)); // used for example when raid has been finished
        addCue("TeamClear", "unitycues/common/cueTeamClear.png", Bounds.fromWidthHeight(313, 447, 108, 42)); //clear team button
        addCue("TeamAccept", "unitycues/common/cueTeamAccept.png", Bounds.fromWidthHeight(445, 440, 145, 55)); // raid accept button
        addCue("AreYouThere", "unitycues/common/cueAreYouThere.png", Bounds.fromWidthHeight(295, 225, 220, 80)); // Are you there popup
        addCue("Yes", "unitycues/common/cueYes.png", Bounds.fromWidthHeight(355, 335, 90, 55)); // Yes button on Are you there popup
        addCue("SpeedBar", "unitycues/common/cueSpeedBar.png", Bounds.fromWidthHeight(0, 455, 75, 60)); // Speed selection bar when you are in encounters
        addCue("SpeedTXT", "unitycues/common/cueSpeedTXT.png", Bounds.fromWidthHeight(5, 495, 65, 35)); // Speed text while in a fight
        addCue("ClearedRecap", "unitycues/common/cueClearedRecap.png", Bounds.fromWidthHeight(320, 55, 165, 50)); // Cleared message on top of the victory recap screen
        addCue("Rerun", "unitycues/common/cueRerun.png", Bounds.fromWidthHeight(86, 445, 641, 57)); // Green "Rerun" button on the victory recap screen
        addCue("Town", "unitycues/common/cueTown.png", Bounds.fromWidthHeight(365, 455, 210, 50)); // The Red "Town" button on the victory/defeat recap screen
        addCue("VictoryRecap", "unitycues/common/cueVictoryRecap.png", Bounds.fromWidthHeight(330, 55, 150, 55)); // Victory message when completing adventures
        addCue("DefeatRecap", "unitycues/common/cueDefeatRecap.png", Bounds.fromWidthHeight(335, 55, 135, 50)); // Defeat message when completing adventures
        addCue("WeeklyRewards", "unitycues/common/cueWeeklyRewards.png", Bounds.fromWidthHeight(205, 135, 395, 255)); // Weekly rewards gump
        addCue("TopChoice", "unitycues/common/cueTopChoice.png", null); // Top Choice border in selection windows
        //endregion Common

        //region Dungeon
        //region Dungeon cues
        addCue("EnergyBar", "unitycues/dungeon/cueEnergyBar.png", Bounds.fromWidthHeight(390, 0, 60, 50)); // The energy bar icon
        addCue("RightArrow", "unitycues/dungeon/cueRightArrow.png", Bounds.fromWidthHeight(720, 255, 40, 50)); // arrow used in quest screen to change zone
        addCue("LeftArrow", "unitycues/dungeon/cueLeftArrow.png", Bounds.fromWidthHeight(40, 255, 45, 50)); // arrow used in quest screen to change zone
        addCue("DungNormal", "unitycues/dungeon/cueDungNormal.png", Bounds.fromWidthHeight(135, 220, 145, 50));
        addCue("DungHard", "unitycues/dungeon/cueDungHard.png", Bounds.fromWidthHeight(330, 220, 145, 50));
        addCue("DungHeroic", "unitycues/dungeon/cueDungHeroic.png", Bounds.fromWidthHeight(525, 220, 145, 50));
        addCue("DungeonZones", "unitycues/dungeon/cueDungeonZones.png", Bounds.fromWidthHeight(90, 55, 130, 55));
        //endregion

        //region Dungeon zones
        addCue("Zone1", "unitycues/dungeon/zones/cueZone1.png", Bounds.fromWidthHeight(252, 53, 299, 39));
        addCue("Zone2", "unitycues/dungeon/zones/cueZone2.png", Bounds.fromWidthHeight(252, 53, 299, 39));
        addCue("Zone3", "unitycues/dungeon/zones/cueZone3.png", Bounds.fromWidthHeight(252, 53, 299, 39));
        addCue("Zone4", "unitycues/dungeon/zones/cueZone4.png", Bounds.fromWidthHeight(252, 53, 299, 39));
        addCue("Zone5", "unitycues/dungeon/zones/cueZone5.png", Bounds.fromWidthHeight(252, 53, 299, 39));
        addCue("Zone6", "unitycues/dungeon/zones/cueZone6.png", Bounds.fromWidthHeight(252, 53, 299, 39));
        //endregion
        //endregion

        //region Familiar
        // region Familiar encounters
        addCue("FamiliarEncounter", "unitycues/familiarEncounter/cueEncounter.png", Bounds.fromWidthHeight(130, 265, 45, 55));
        addCue("Persuade", "unitycues/familiarEncounter/cuePersuade.png", Bounds.fromWidthHeight(120, 315, 160, 55));
        addCue("Bribe", "unitycues/familiarEncounter/cueBribe.png", Bounds.fromWidthHeight(540, 315, 120, 55));
        addCue("DeclineRed", "unitycues/familiarEncounter/cueDeclineRed.png", Bounds.fromWidthHeight(240, 430, 135, 50)); // decline persuation attempts
        addCue("YouCurrentlyOwn", "unitycues/familiarEncounter/cueYouCurrentlyOwn.png", Bounds.fromWidthHeight(135, 395, 275, 40)); // You currently own text in familiar encounters
        //endregion
        //region Familiar bribing cues
        addCue("RareFamiliar", "unitycues/familiarEncounter/type/cue02RareFamiliar.png", Bounds.fromWidthHeight(527, 261, 158, 59)); // Rare Bribe cue
        addCue("CommonFamiliar", "unitycues/familiarEncounter/type/cue01CommonFamiliar.png", Bounds.fromWidthHeight(527, 261, 158, 59)); // Common Bribe cue
        addCue("EpicFamiliar", "unitycues/familiarEncounter/type/cue03EpicFamiliar.png", Bounds.fromWidthHeight(525, 265, 150, 55));
        addCue("LegendaryFamiliar", "unitycues/familiarEncounter/type/cue04LegendaryFamiliar.png", Bounds.fromWidthHeight(525, 265, 150, 55));
        //endregion
        //region Familiar folders
        int newFamCnt = loadCueFolder("unitycues/familiarEncounter/01 Common", null, false, new Bounds(145, 50, 575, 125));
        newFamCnt += loadCueFolder("unitycues/familiarEncounter/02 Rare", null, false, new Bounds(145, 50, 575, 125));
        newFamCnt += loadCueFolder("unitycues/familiarEncounter/03 Epic", null, false, new Bounds(145, 50, 575, 125));
        newFamCnt += loadCueFolder("unitycues/familiarEncounter/04 Legendary", null, false, new Bounds(145, 50, 575, 125));
        BHBotUnity.logger.debug("Found " + newFamCnt + " Unity familiar cues.");
        //endregion
        //endregion

        //region Merchant
        addCue("Merchant", "unitycues/merchant/cueMerchant.png", Bounds.fromWidthHeight(300, 105, 200, 70)); // cue for merchant dialog/popup
        addCue("MerchantDecline", "unitycues/merchant/cueMerchantDecline.png", Bounds.fromWidthHeight(410, 365, 135, 55)); // cue for merchant dialog/popup
        //endregion

        //region Main screen cues
        addCue("Main", "unitycues/mainScreen/cueMainScreen.png", Bounds.fromWidthHeight(60, 5, 140, 40)); // Gem cue used to identify the main screen
        addCue("Quest", "unitycues/mainScreen/cueQuestButton.png", Bounds.fromWidthHeight(10, 5, 50, 75)); // Quest icon used to open the dungeon menu.
        addCue("SettingsGear", "unitycues/mainScreen/cueSettingsGear.png", Bounds.fromWidthHeight(665, 450, 55, 65)); // settings button
        addCue("RaidButton", "unitycues/mainScreen/cueRaidButton.png", Bounds.fromWidthHeight(20, 295, 40, 65)); // Ruby icon used to open the raid menu.
        addCue("GorMenu", "unitycues/mainScreen/cueGorMenu.png", Bounds.fromWidthHeight(95, 465, 55, 60)); // Permagor menu close to the Character menu
        //endregion

        //region Numbers
        //region TG Difficulty selection 17px
        addCue("tg_diff_selection_17_0", "unitycues/numbers/tgDiffSelection17/tg_diff_selection_17_0.png", null);
        addCue("tg_diff_selection_17_1", "unitycues/numbers/tgDiffSelection17/tg_diff_selection_17_1.png", null);
        addCue("tg_diff_selection_17_2", "unitycues/numbers/tgDiffSelection17/tg_diff_selection_17_2.png", null);
        addCue("tg_diff_selection_17_3", "unitycues/numbers/tgDiffSelection17/tg_diff_selection_17_3.png", null);
        addCue("tg_diff_selection_17_4", "unitycues/numbers/tgDiffSelection17/tg_diff_selection_17_4.png", null);
        addCue("tg_diff_selection_17_5", "unitycues/numbers/tgDiffSelection17/tg_diff_selection_17_5.png", null);
        addCue("tg_diff_selection_17_6", "unitycues/numbers/tgDiffSelection17/tg_diff_selection_17_6.png", null);
        addCue("tg_diff_selection_17_7", "unitycues/numbers/tgDiffSelection17/tg_diff_selection_17_7.png", null);
        addCue("tg_diff_selection_17_8", "unitycues/numbers/tgDiffSelection17/tg_diff_selection_17_8.png", null);
        addCue("tg_diff_selection_17_9", "unitycues/numbers/tgDiffSelection17/tg_diff_selection_17_9.png", null);
        //endregion TG Difficulty selection 17px

        //region TG Difficulty range 16px
        addCue("tg_diff_range_16_0", "unitycues/numbers/tgDiffRange16/tg_diff_range_16_0.png", null);
        addCue("tg_diff_range_16_1", "unitycues/numbers/tgDiffRange16/tg_diff_range_16_1.png", null);
        addCue("tg_diff_range_16_2", "unitycues/numbers/tgDiffRange16/tg_diff_range_16_2.png", null);
        addCue("tg_diff_range_16_3", "unitycues/numbers/tgDiffRange16/tg_diff_range_16_3.png", null);
        addCue("tg_diff_range_16_4", "unitycues/numbers/tgDiffRange16/tg_diff_range_16_4.png", null);
        addCue("tg_diff_range_16_5", "unitycues/numbers/tgDiffRange16/tg_diff_range_16_5.png", null);
        addCue("tg_diff_range_16_6", "unitycues/numbers/tgDiffRange16/tg_diff_range_16_6.png", null);
        addCue("tg_diff_range_16_7", "unitycues/numbers/tgDiffRange16/tg_diff_range_16_7.png", null);
        addCue("tg_diff_range_16_8", "unitycues/numbers/tgDiffRange16/tg_diff_range_16_8.png", null);
        addCue("tg_diff_range_16_9", "unitycues/numbers/tgDiffRange16/tg_diff_range_16_9.png", null);
        addCue("tg_diff_range_16_hyphen", "unitycues/numbers/tgDiffRange16/tg_diff_range_16_hyphen.png", null);
        //endregion TG Difficulty range 16px

        // region TG Main Window Difficulty 10px
        addCue("tg_diff_win_10_0", "unitycues/numbers/tgDiffWindow10/tg_diff_win_10_0.png", null);
        addCue("tg_diff_win_10_1", "unitycues/numbers/tgDiffWindow10/tg_diff_win_10_1.png", null);
        addCue("tg_diff_win_10_2", "unitycues/numbers/tgDiffWindow10/tg_diff_win_10_2.png", null);
        addCue("tg_diff_win_10_3", "unitycues/numbers/tgDiffWindow10/tg_diff_win_10_3.png", null);
        addCue("tg_diff_win_10_4", "unitycues/numbers/tgDiffWindow10/tg_diff_win_10_4.png", null);
        addCue("tg_diff_win_10_5", "unitycues/numbers/tgDiffWindow10/tg_diff_win_10_5.png", null);
        addCue("tg_diff_win_10_6", "unitycues/numbers/tgDiffWindow10/tg_diff_win_10_6.png", null);
        addCue("tg_diff_win_10_7", "unitycues/numbers/tgDiffWindow10/tg_diff_win_10_7.png", null);
        addCue("tg_diff_win_10_8", "unitycues/numbers/tgDiffWindow10/tg_diff_win_10_8.png", null);
        addCue("tg_diff_win_10_9", "unitycues/numbers/tgDiffWindow10/tg_diff_win_10_9.png", null);
        // endregion TG Main Window Difficulty 10px

        // region TG Main Window Difficulty 11px
        addCue("tg_diff_win_11_0", "unitycues/numbers/tgDiffWindow11/tg_diff_win_11_0.png", null);
        addCue("tg_diff_win_11_1", "unitycues/numbers/tgDiffWindow11/tg_diff_win_11_1.png", null);
        addCue("tg_diff_win_11_2", "unitycues/numbers/tgDiffWindow11/tg_diff_win_11_2.png", null);
        addCue("tg_diff_win_11_3", "unitycues/numbers/tgDiffWindow11/tg_diff_win_11_3.png", null);
        addCue("tg_diff_win_11_4", "unitycues/numbers/tgDiffWindow11/tg_diff_win_11_4.png", null);
        addCue("tg_diff_win_11_5", "unitycues/numbers/tgDiffWindow11/tg_diff_win_11_5.png", null);
        addCue("tg_diff_win_11_6", "unitycues/numbers/tgDiffWindow11/tg_diff_win_11_6.png", null);
        addCue("tg_diff_win_11_7", "unitycues/numbers/tgDiffWindow11/tg_diff_win_11_7.png", null);
        addCue("tg_diff_win_11_8", "unitycues/numbers/tgDiffWindow11/tg_diff_win_11_8.png", null);
        addCue("tg_diff_win_11_9", "unitycues/numbers/tgDiffWindow11/tg_diff_win_11_9.png", null);
        // endregion TG Main Window Difficulty 11px

        // region WB Player TS
        addCue("wb_player_ts_0", "unitycues/numbers/wbPlayerTS/wb_player_ts_0.png", null);
        addCue("wb_player_ts_1", "unitycues/numbers/wbPlayerTS/wb_player_ts_1.png", null);
        addCue("wb_player_ts_2", "unitycues/numbers/wbPlayerTS/wb_player_ts_2.png", null);
        addCue("wb_player_ts_3", "unitycues/numbers/wbPlayerTS/wb_player_ts_3.png", null);
        addCue("wb_player_ts_4", "unitycues/numbers/wbPlayerTS/wb_player_ts_4.png", null);
        addCue("wb_player_ts_5", "unitycues/numbers/wbPlayerTS/wb_player_ts_5.png", null);
        addCue("wb_player_ts_6", "unitycues/numbers/wbPlayerTS/wb_player_ts_6.png", null);
        addCue("wb_player_ts_7", "unitycues/numbers/wbPlayerTS/wb_player_ts_7.png", null);
        addCue("wb_player_ts_8", "unitycues/numbers/wbPlayerTS/wb_player_ts_8.png", null);
        addCue("wb_player_ts_9", "unitycues/numbers/wbPlayerTS/wb_player_ts_9.png", null);
        // endregion WB Player TS

        // region WB Tiers
        addCue("wb_tier_0", "unitycues/numbers/wbTier/wb_tier_0.png", null);
        addCue("wb_tier_1", "unitycues/numbers/wbTier/wb_tier_1.png", null);
        addCue("wb_tier_2", "unitycues/numbers/wbTier/wb_tier_2.png", null);
        addCue("wb_tier_3", "unitycues/numbers/wbTier/wb_tier_3.png", null);
        addCue("wb_tier_4", "unitycues/numbers/wbTier/wb_tier_4.png", null);
        addCue("wb_tier_5", "unitycues/numbers/wbTier/wb_tier_5.png", null);
        addCue("wb_tier_6", "unitycues/numbers/wbTier/wb_tier_6.png", null);
        addCue("wb_tier_7", "unitycues/numbers/wbTier/wb_tier_7.png", null);
        addCue("wb_tier_8", "unitycues/numbers/wbTier/wb_tier_8.png", null);
        addCue("wb_tier_9", "unitycues/numbers/wbTier/wb_tier_9.png", null);
        // endregion

        // region WB Tier Buttons
        // These are used when selecting World Boss tier
        addCue("wb_tier_button_0", "unitycues/numbers/wbTierButton/wb_tier_button_0.png", null);
        addCue("wb_tier_button_1", "unitycues/numbers/wbTierButton/wb_tier_button_1.png", null);
        addCue("wb_tier_button_2", "unitycues/numbers/wbTierButton/wb_tier_button_2.png", null);
        addCue("wb_tier_button_3", "unitycues/numbers/wbTierButton/wb_tier_button_3.png", null);
        addCue("wb_tier_button_4", "unitycues/numbers/wbTierButton/wb_tier_button_4.png", null);
        addCue("wb_tier_button_5", "unitycues/numbers/wbTierButton/wb_tier_button_5.png", null);
        addCue("wb_tier_button_6", "unitycues/numbers/wbTierButton/wb_tier_button_6.png", null);
        addCue("wb_tier_button_7", "unitycues/numbers/wbTierButton/wb_tier_button_7.png", null);
        addCue("wb_tier_button_8", "unitycues/numbers/wbTierButton/wb_tier_button_8.png", null);
        addCue("wb_tier_button_9", "unitycues/numbers/wbTierButton/wb_tier_button_9.png", null);
        // endregion

        //region WB Total TS 16px
        addCue("wb_total_ts_16_0", "unitycues/numbers/wbTotalTS16/wb_total_ts_16_0.png", null); // WB Total TS 16px 0
        addCue("wb_total_ts_16_1", "unitycues/numbers/wbTotalTS16/wb_total_ts_16_1.png", null); // WB Total TS 16px 1
        addCue("wb_total_ts_16_2", "unitycues/numbers/wbTotalTS16/wb_total_ts_16_2.png", null); // WB Total TS 16px 2
        addCue("wb_total_ts_16_3", "unitycues/numbers/wbTotalTS16/wb_total_ts_16_3.png", null); // WB Total TS 16px 3
        addCue("wb_total_ts_16_4", "unitycues/numbers/wbTotalTS16/wb_total_ts_16_4.png", null); // WB Total TS 16px 4
        addCue("wb_total_ts_16_5", "unitycues/numbers/wbTotalTS16/wb_total_ts_16_5.png", null); // WB Total TS 16px 5
        addCue("wb_total_ts_16_6", "unitycues/numbers/wbTotalTS16/wb_total_ts_16_6.png", null); // WB Total TS 16px 6
        addCue("wb_total_ts_16_7", "unitycues/numbers/wbTotalTS16/wb_total_ts_16_7.png", null); // WB Total TS 16px 7
        addCue("wb_total_ts_16_8", "unitycues/numbers/wbTotalTS16/wb_total_ts_16_8.png", null); // WB Total TS 16px 8
        addCue("wb_total_ts_16_9", "unitycues/numbers/wbTotalTS16/wb_total_ts_16_9.png", null); // WB Total TS 16px 9
        // endregion

        //region WB Total TS 18px
        addCue("wb_total_ts_18_0", "unitycues/numbers/wbTotalTS18/wb_total_ts_18_0.png", null); // WB Total TS 18px 0
        addCue("wb_total_ts_18_1", "unitycues/numbers/wbTotalTS18/wb_total_ts_18_1.png", null); // WB Total TS 18px 1
        addCue("wb_total_ts_18_2", "unitycues/numbers/wbTotalTS18/wb_total_ts_18_2.png", null); // WB Total TS 18px 2
        addCue("wb_total_ts_18_3", "unitycues/numbers/wbTotalTS18/wb_total_ts_18_3.png", null); // WB Total TS 18px 3
        addCue("wb_total_ts_18_4", "unitycues/numbers/wbTotalTS18/wb_total_ts_18_4.png", null); // WB Total TS 18px 4
        addCue("wb_total_ts_18_5", "unitycues/numbers/wbTotalTS18/wb_total_ts_18_5.png", null); // WB Total TS 18px 5
        addCue("wb_total_ts_18_6", "unitycues/numbers/wbTotalTS18/wb_total_ts_18_6.png", null); // WB Total TS 18px 6
        addCue("wb_total_ts_18_7", "unitycues/numbers/wbTotalTS18/wb_total_ts_18_7.png", null); // WB Total TS 18px 7
        addCue("wb_total_ts_18_8", "unitycues/numbers/wbTotalTS18/wb_total_ts_18_8.png", null); // WB Total TS 18px 8
        addCue("wb_total_ts_18_9", "unitycues/numbers/wbTotalTS18/wb_total_ts_18_9.png", null); // WB Total TS 18px 9
        // endregion

        //region WB Total TS 20px
        addCue("wb_total_ts_20_0", "unitycues/numbers/wbTotalTS20/wb_total_ts_20_0.png", null); // WB Total TS 20px 0
        addCue("wb_total_ts_20_1", "unitycues/numbers/wbTotalTS20/wb_total_ts_20_1.png", null); // WB Total TS 20px 1
        addCue("wb_total_ts_20_2", "unitycues/numbers/wbTotalTS20/wb_total_ts_20_2.png", null); // WB Total TS 20px 2
        addCue("wb_total_ts_20_3", "unitycues/numbers/wbTotalTS20/wb_total_ts_20_3.png", null); // WB Total TS 20px 3
        addCue("wb_total_ts_20_4", "unitycues/numbers/wbTotalTS20/wb_total_ts_20_4.png", null); // WB Total TS 20px 4
        addCue("wb_total_ts_20_5", "unitycues/numbers/wbTotalTS20/wb_total_ts_20_5.png", null); // WB Total TS 20px 5
        addCue("wb_total_ts_20_6", "unitycues/numbers/wbTotalTS20/wb_total_ts_20_6.png", null); // WB Total TS 20px 6
        addCue("wb_total_ts_20_7", "unitycues/numbers/wbTotalTS20/wb_total_ts_20_7.png", null); // WB Total TS 20px 7
        addCue("wb_total_ts_20_8", "unitycues/numbers/wbTotalTS20/wb_total_ts_20_8.png", null); // WB Total TS 20px 8
        addCue("wb_total_ts_20_9", "unitycues/numbers/wbTotalTS20/wb_total_ts_20_9.png", null); // WB Total TS 20px 9
        // endregion
        //endregion

        //region Raid
        addCue("RaidPopup", "unitycues/raid/cueRaidPopup.png", Bounds.fromWidthHeight(310, 45, 60, 45)); // The raid near the bar
        addCue("RaidLevel", "unitycues/raid/cueRaidLevel.png", Bounds.fromWidthHeight(190, 430, 430, 30)); // selected raid type button cue
        addCue("cueRaidLevelEmpty", "unitycues/raid/cueRaidLevelEmpty.png", Bounds.fromWidthHeight(190, 430, 430, 30)); // unselected raid type button cue
        addCue("Raid1Name", "unitycues/raid/r1Name.png", Bounds.fromWidthHeight(175, 350, 300, 45));// Raid 1 Name, used whe R1 is the only unlocked raid
        addCue("RaidSummon", "unitycues/raid/cueRaidSummon.png", Bounds.fromWidthHeight(475, 350, 135, 55)); // Raid Summon button
        addCue("RaidNormal", "unitycues/raid/cueRaidNormal.png", Bounds.fromWidthHeight(135, 210, 145, 50)); // Normal difficulty Raid
        addCue("RaidHard", "unitycues/raid/cueRaidHard.png", Bounds.fromWidthHeight(330, 210, 145, 50)); // Hard difficulty Raid
        addCue("RaidHeroic", "unitycues/raid/cueRaidHeroic.png", Bounds.fromWidthHeight(525, 210, 145, 50)); // Heroic difficulty Raid
        //endregion

        //region T/G
        addCue("TokenBar", "unitycues/tierGauntlet/cueTokenBar.png", Bounds.fromWidthHeight(315, 40, 60, 55));
        addCue("Trials", "unitycues/tierGauntlet/cueTrials.png", new Bounds(0, 0, 40, 400)); // cue for trials button (note that as of 23.9.2017 they changed the button position to the right side of the screen and modified the glyph)
        addCue("Trials2", "unitycues/tierGauntlet/cueTrials2.png", new Bounds(720, 0, 770, 400)); // an alternative cue for trials (flipped horizontally, located on the right side of the screen). Used since 23.9.2017.
        addCue("Gauntlet", "unitycues/tierGauntlet/cueGauntlet.png", null); // cue for gauntlet button
        addCue("Gauntlet2", "unitycues/tierGauntlet/cueGauntlet2.png", null); // alternative cue for gauntlet button
        addCue("Difficulty", "unitycues/tierGauntlet/cueDifficulty.png", Bounds.fromWidthHeight(460, 340, 185, 95)); // T/G Difficulty combo box
        addCue("DifficultyDisabled", "unitycues/tierGauntlet/cueDifficultyDisabled.png", Bounds.fromWidthHeight(455, 340, 190, 100)); // Greyed out TG Difficulty drop down
        addCue("SelectDifficulty", "unitycues/tierGauntlet/cueSelectDifficulty.png", Bounds.fromWidthHeight(575, 365, 70, 65)); // Difficulty arrow pointing down
        //endregion

        //region WB
        addCue("WorldBossPopup", "unitycues/worldBoss/cueWorldBossPopup.png", Bounds.fromWidthHeight(310, 25, 65, 60)); // Xeal pop-up
        addCue("DarkBlueSummon", "unitycues/worldBoss/cueDarkBlueSummon.png", Bounds.fromWidthHeight(435, 435, 130, 55)); // WB Main menu summon button
        addCue("WorldBossTitle", "unitycues/worldBoss/cueWorldBossTitle.png", Bounds.fromWidthHeight(280, 75, 245, 55)); // WB Title in Main Menu
        addCue("LargeDarkBlueSummon", "unitycues/worldBoss/cueLargeDarkBlueSummon.png", Bounds.fromWidthHeight(480, 350, 125, 60)); // Big Blue Summon button in WB Screen 2
        addCue("WBIsPrivate", "unitycues/worldBoss/cueWBIsPrivate.png", Bounds.fromWidthHeight(310, 315, 65, 70)); // Private Flag for World Boss
        addCue("WorldBossTierDropDown", "unitycues/worldBoss/cueWorldBossTierDropDown.png", Bounds.fromWidthHeight(290, 185, 220, 70)); // Drop Down for Tier detection
        addCue("SmallDarkBlueSummon", "unitycues/worldBoss/cueSmallDarkBlueSummon.png", Bounds.fromWidthHeight(415, 380, 130, 50)); // Dark blue WB Summon button
        addCue("NotEnoughXeals", "unitycues/worldBoss/cueNotEnoughXeals.png", Bounds.fromWidthHeight(270, 215, 265, 95)); // Not enough xeals popup
        addCue("Unready", "unitycues/worldBoss/cueWorldBossUnready.png", null); // Red X in WB Lobby
        addCue("Invite", "unitycues/worldBoss/cueDarkBlueInvite.png", Bounds.fromWidthHeight(335, 330, 120, 45)); // Lobby member invite button
        addCue("DarkBlueStart", "unitycues/worldBoss/cueDarkBlueStart.png", Bounds.fromWidthHeight(325, 445, 110, 50)); // WB Dark blue start button
        addCue("WorldBossPlayerKick", "unitycues/worldBoss/cueWorldBossKick.png", Bounds.fromWidthHeight(410, 220, 45, 50)); // Lobby member kick button
        addCue("WorldBossSummonTitle", "unitycues/worldBoss/cueWorldBossSummonTitle.png", Bounds.fromWidthHeight(330, 105, 140, 50)); // WB Summon Title
        addCue("WorldBossPopupKick", "unitycues/worldBoss/cueWorldBossPopupKick.png", Bounds.fromWidthHeight(345, 130, 110, 55)); // WB Player Kick confirmation

        addCue("WorldBossDifficultyNormal", "unitycues/worldBoss/cueWorldBossDifficultyNormal.png", Bounds.fromWidthHeight(330, 280, 105, 35)); // WB Normal difficulty selected
        addCue("WorldBossDifficultyHard", "unitycues/worldBoss/cueWorldBossDifficultyHard.png", Bounds.fromWidthHeight(345, 280, 75, 35)); // WB Hard difficulty selected
        addCue("WorldBossDifficultyHeroic", "unitycues/worldBoss/cueWorldBossDifficultyHeroic.png", Bounds.fromWidthHeight(335, 280, 95, 35)); // WB Heroic difficulty selected

        addCue("cueWBSelectNormal", "unitycues/worldBoss/cueWBSelectNormal.png", Bounds.fromWidthHeight(330, 140, 115, 40)); // WB Normal difficulty selection
        addCue("cueWBSelectHard", "unitycues/worldBoss/cueWBSelectHard.png", Bounds.fromWidthHeight(345, 200, 85, 40)); // WB Hard difficulty selection
        addCue("cueWBSelectHeroic", "unitycues/worldBoss/cueWBSelectHeroic.png", Bounds.fromWidthHeight(335, 260, 105, 40)); // WB Heroic difficulty selection
        //endregion

        //region Treasure Chest
        addCue("SkeletonTreasure", "unitycues/treasureChest/cueSkeletonTreasure.png", Bounds.fromWidthHeight(205, 188, 87, 91)); // skeleton treasure found in dungeons (it's a dialog/popup cue)
        // TODO find the real cue in Unity Engine
        addCue("SkeletonNoKeys", "cues/cueSkeletonNoKeys.png", new Bounds(478, 318, 500, 348)); // red 0
        addCue("Open", "unitycues/treasureChest/cueOpen.png", null); // skeleton treasure open button
        addCue("Decline", "unitycues/treasureChest/cueDecline.png", Bounds.fromWidthHeight(405, 365, 145, 55)); // decline skeleton treasure button (found in dungeons), also with video ad treasures (found in dungeons)
        //endregion

        //region Weekly Rewards
        addCue("PVP_Rewards", "unitycues/weeklyRewards/pvp.png", Bounds.fromWidthHeight(350, 110, 100, 75));
        addCue("Trials_Rewards", "unitycues/weeklyRewards/trials.png", Bounds.fromWidthHeight(325, 110, 145, 75));
        addCue("GVG_Rewards", "unitycues/weeklyRewards/gvg.png", Bounds.fromWidthHeight(325, 110, 145, 75));
        addCue("Invasion_Rewards", "unitycues/weeklyRewards/invasion.png", Bounds.fromWidthHeight(305, 115, 185, 60));
        addCue("Fishing_Bait", "unitycues/weeklyRewards/fishing_bait.png", Bounds.fromWidthHeight(385, 220, 80, 55)); // Green fishing bait
        //endregion

        //region Settings
        addCue("settingsMusic", "unitycues/settings/cueSettingsMusic.png", Bounds.fromWidthHeight(340, 175, 35, 55));
        addCue("settingsSound", "unitycues/settings/cueSettingsSound.png", Bounds.fromWidthHeight(340, 240, 35, 55));
        addCue("settingsNotification", "unitycues/settings/cueSettingsNotification.png", Bounds.fromWidthHeight(160, 335, 250, 65));
        addCue("settingsWBReq", "unitycues/settings/cueSettingsWBReq.png", Bounds.fromWidthHeight(155, 280, 370, 65));
        addCue("settingsReducedFX", "unitycues/settings/cueSettingsReducedFX.png", Bounds.fromWidthHeight(155, 335, 300, 65));
        addCue("settingsBattleTXT", "unitycues/settings/cueSettingsBattleTXT.png", Bounds.fromWidthHeight(155, 265, 250, 75));
        addCue("settingsAnimations", "unitycues/settings/cueSettingsAnimations.png", Bounds.fromWidthHeight(155, 295, 220, 75));
        addCue("settingsMerchants", "unitycues/settings/cueSettingsMerchants.png", Bounds.fromWidthHeight(160, 315, 330, 70));
        addCue("settingsTips", "unitycues/settings/cueSettingsTips.png", Bounds.fromWidthHeight(160, 290, 335, 65)); // Tips when defeated setting.
        //end region

        //region scrollBar
        addCue("ScrollerAtTop", "unitycues/scrollBars/cueScrollerAtTop.png", null);
        addCue("ScrollerAtBottomSettings", "unitycues/scrollBars/cueScrollerAtBottomSettings.png", null);
        addCue("DropDownDown", "unitycues/scrollBars/cueDropDownDown.png", null); // down arrow in difficulty drop down menu (found in trials/gauntlet, for example)
        addCue("DropDownUp", "unitycues/scrollBars/cueDropDownUp.png", null); // The arrow pointing up in scroll bars.
        addCue("StripScrollerTopPos", "unitycues/scrollBars/cueStripScrollerTopPos.png", null); // Scroll bar top position for strip menu
        addCue("SettingsScrollerTopPos", "unitycues/scrollBars/cueSettingsScrollerTopPos.png", null); // Scroll bar top position for settings menu
        //endregion
    }

    /**
     * This method is intended to replace Cue(s) at runtime
     *
     * @param cueKey The Cue Key to override
     * @param cuePath The path to the image of the new Cue
     * @param cueBounds Bounds of the new Cue (if null, old bounds will be used
     */
    void overrideCueFromFile(String cueKey, String cuePath, @SuppressWarnings("SameParameterValue") Bounds cueBounds) {

        Bounds oldBounds;
        boolean isLoaded = false;

        if (loadedCues.containsKey(cueKey)) {
            oldBounds = loadedCues.get(cueKey).bounds;
            isLoaded = true;
        } else if (addedCues.containsKey(cueKey)) {
            oldBounds = addedCues.get(cueKey).cueBounds;
        } else {
            BHBotUnity.logger.info("No cue found to override, skipping.");
            return;
        }

        File newCueImgFile = new File(cuePath);
        if (!newCueImgFile.exists()) {
            BHBotUnity.logger.error("New Cue path does not exists.");
            return;
        }
        if (newCueImgFile.isDirectory()) {
            BHBotUnity.logger.error("New Cue is a directory, skipping.");
            return;
        }

        BufferedImage newCueImg;
        try {
            newCueImg = ImageIO.read(newCueImgFile);
        } catch (IOException e) {
            BHBotUnity.logger.error("Error when loading image file.", e);
            return;
        }

        loadedCues.put(cueKey, new Cue(cueKey, null, newCueImg, cueBounds != null ? cueBounds : oldBounds));
        if (!isLoaded) {
            addedCues.remove(cueKey);
        }
    }

    /**
     * Simplified version of overrideCueFromFile where no Bounds are required
     *
     * @param cueKey The Cue key to override
     * @param cuePath The Path to the new Cue image file
     */
    @SuppressWarnings("unused")
    void overrideCueFromFile(String cueKey, String cuePath) {
        overrideCueFromFile(cueKey, cuePath, null);
    }

    /**
     * Allows for hot reload of Cues. This is also intended to be used by developers
     *
     * @param relativePath The relative path where cues are stored in the disk
     */
    void reloadFromDisk(String relativePath) {
        if (!relativePath.endsWith("\\") || !relativePath.endsWith("/")) relativePath += "/";

        relativePath = relativePath.replace("\\", "/");

        Map<String, CueData> newAddedCues = new HashMap<>();

        for (Map.Entry<String, Cue> loadedCue : loadedCues.entrySet()) {
            Cue oldCue = loadedCue.getValue();

            String reloadPath = relativePath + oldCue.path;
            String newPath = new File(reloadPath).exists() ? reloadPath : oldCue.path;

            CueData newDetails = new CueData(newPath, oldCue.bounds);
            newAddedCues.put(loadedCue.getKey(), newDetails);
        }

        for (Map.Entry<String, CueData> addedCue : addedCues.entrySet()){
            CueData oldData = addedCue.getValue();

            String reloadPath = relativePath + oldData.cuePath;
            String newPath = new File(reloadPath).exists() ? reloadPath : oldData.cuePath;

            CueData newData = new CueData(newPath, oldData.cueBounds);

            newAddedCues.put(addedCue.getKey(), newData);
        }

        loadedCues.clear();
        addedCues = newAddedCues;
    }

}
