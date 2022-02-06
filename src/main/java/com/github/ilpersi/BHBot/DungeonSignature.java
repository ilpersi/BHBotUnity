package com.github.ilpersi.BHBot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DungeonSignature {

    private final BHBotUnity bot;
    private LinkedHashMap<String, Integer> zoneSignatures;
    final String JSON_FILE_PATH = "./data/dungeon_signatures.json";

    DungeonSignature(BHBotUnity bot) {
        this.zoneSignatures = new LinkedHashMap<>();
        this.bot = bot;
    }

    /**
     * @param signature the MD5 signature of the zone
     * @return An integer with the zone number
     */
    int zoneFromSignature(String signature) {
        if (this.zoneSignatures.size() == 0) {
            BHBotUnity.logger.debug("Loading signatures from JSON.");
            loadFromJSON();
        }

        Integer zone = this.zoneSignatures.getOrDefault(signature, 0);

        // JSON exists, but signatures changed
        if (zone == 0) {
            BHBotUnity.logger.debug("Signatures not up to date, rebuilding.");
            buildSignatures();
            this.saveToJson();

            zone = this.zoneSignatures.getOrDefault(this.getCurrentZoneSignature(), 0);
        }

        return zone;
    }

    /**
     * This method will load dungeon signature from the JSON file where they are saved. If the file does not exist,
     * the logic will create it.
     */
    private void loadFromJSON() {

        File signaturesFile = new File(JSON_FILE_PATH);

        if (!signaturesFile.exists()) {
            BHBotUnity.logger.debug("JSON file does not exist, creating it.");
            buildSignatures();
            this.saveToJson();
        } else {
            Gson gson = new Gson();
            JsonReader reader;

            try {
                reader = new JsonReader(new FileReader(JSON_FILE_PATH));
                this.zoneSignatures = gson.fromJson(reader, new TypeToken<LinkedHashMap<String, Integer>>(){}.getType());

                BHBotUnity.logger.debug("Loaded " + this.zoneSignatures.size() + " signatures from JSON.");
            } catch (FileNotFoundException e) {
                BHBotUnity.logger.error("It was impossible to read dungeon signatures from JSON file.", e);
            }

        }
    }

    /**
     * This method will take care of creating the signatures reading directly from the game window.
     * This method assumes that the dungeon window is opened.
     */
    private void buildSignatures() {
        BHBotUnity.logger.info("Building dungeon signatures.");
        final int CLICK_DELAY = 500;

        // We reset the signature hashmap
        this.zoneSignatures.clear();

        // We make sure the dungeon window is opened
        MarvinSegment seg = MarvinSegment.fromCue(BHBotUnity.cues.get("DungeonZones"), bot.browser);
        if (seg == null) {
            BHBotUnity.logger.error("Dungeon selection window is not opened. Impossible to build zone cues!");
            return;
        }

        // We scroll left until we are at zone 1
        seg = MarvinSegment.fromCue(BHBotUnity.cues.get("LeftArrow"), bot.browser);
        while (seg != null) {
            bot.browser.clickOnSeg(seg);
            seg = MarvinSegment.fromCue(BHBotUnity.cues.get("LeftArrow"), bot.browser);
        }

        bot.browser.readScreen(CLICK_DELAY);
        int zoneCount = 0;
        do {
            seg = MarvinSegment.fromCue(BHBotUnity.cues.get("RightArrow"), bot.browser);
            zoneCount++;

            String signature = this.getCurrentZoneSignature();
            this.zoneSignatures.put(signature, zoneCount);
            BHBotUnity.logger.debug(signature + " -> " + zoneCount);

            if (seg != null) {
                bot.browser.clickOnSeg(seg);
                bot.browser.readScreen(CLICK_DELAY);
            }

        } while (seg != null);
    }

    /**
     * This method will return the signature for the current zone. This method assumes that the quest selection window is opened.
     *
     * @return The MD5 hash for the current zone.
     */
    String getCurrentZoneSignature() {
        BufferedImage zoneSignatureImg = bot.browser.getImg().getSubimage(Misc.SIGNATURE_BOUNDS.x1, Misc.SIGNATURE_BOUNDS.y1, Misc.SIGNATURE_BOUNDS.width, Misc.SIGNATURE_BOUNDS.height);

        return Misc.imgToMD5(zoneSignatureImg);
    }

    /**
     * This method saves the current signatures to JSON file
     */
    private void saveToJson() {
        Gson gson = new GsonBuilder().setPrettyPrinting()
                .create();
        String jsonString = gson.toJson(this.zoneSignatures);

        BufferedWriter writer;
        try {
            writer = new BufferedWriter(new FileWriter(JSON_FILE_PATH));
            writer.write(jsonString);
            writer.close();
        } catch (IOException e) {
            BHBotUnity.logger.error("It was impossible to save dungeon signatures to JSON file", e);
        }
    }

    public static void main(String[] args) {
        buildFromFiles();
    }

    @SuppressWarnings("DuplicatedCode")
    static void buildFromFiles() {

        final String zonesPath = "cuebuilder/dungeon/zones/";

        File zonesPathFile = new File(zonesPath);

        if (!zonesPathFile.exists()) {
            System.out.println("Path does not exist: " + zonesPath);
            return;
        }

        if (!zonesPathFile.isDirectory()) {
            System.out.println("Path is not a directory: " + zonesPath);
            return;
        }

        Pattern zoneName = Pattern.compile("z(\\d{1,2})[^.]*\\.png");

        File[] signatureFiles = zonesPathFile.listFiles();

        if (null != signatureFiles) {
            for (final File zoneFile : signatureFiles) {

                Matcher zoneMatcher = zoneName.matcher(zoneFile.getName());

                if (zoneMatcher.find()) {
                    int zoneNumber = Integer.parseInt(zoneMatcher.group(1));

                    BufferedImage zoneFullImg;
                    try {
                        zoneFullImg = ImageIO.read(zoneFile);
                    } catch (IOException e) {
                        System.out.println("Error while loading image file: " + zoneFile.getAbsolutePath());
                        e.printStackTrace();
                        continue;
                    }

                    BufferedImage zoneSignatureImg = zoneFullImg.getSubimage(Misc.SIGNATURE_BOUNDS.x1, Misc.SIGNATURE_BOUNDS.y1, Misc.SIGNATURE_BOUNDS.width, Misc.SIGNATURE_BOUNDS.height);
                    String signature = Misc.imgToMD5(zoneSignatureImg);
                    System.out.println("zoneSignatures.put(\"" + signature + "\", " + zoneNumber + "); // " + zoneFile.getName());
                } else {
                    System.out.println("Invalid file name " + zoneFile.getName());
                }
            }
        }
    }
}
