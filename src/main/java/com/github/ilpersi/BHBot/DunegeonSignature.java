package com.github.ilpersi.BHBot;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DunegeonSignature {
    @SuppressWarnings("DuplicatedCode")
    public static void main(String[] args) {

        final String zonesPath = "src/main/resources/" + "unitycuesources/dungeon/zones";

        File zonesPathFile = new File(zonesPath);

        if (!zonesPathFile.exists()) {
            System.out.println("Path does not exist: " + zonesPath);
            return;
        }

        if (!zonesPathFile.isDirectory()) {
            System.out.println("Path is not a directory: " + zonesPath);
            return;
        }

        Pattern zoneName = Pattern.compile("z(\\d{1,2})\\.png");

        for (final File zoneFile : zonesPathFile.listFiles()) {

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
                System.out.println("zoneSignatures.put(\"" + signature + "\", " + zoneNumber + ");");
            } else {
                System.out.println("Invalid file name "  + zoneFile.getName());
            }
        }
    }
}
