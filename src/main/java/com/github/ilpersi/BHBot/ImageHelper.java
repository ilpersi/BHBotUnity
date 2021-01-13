package com.github.ilpersi.BHBot;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class ImageHelper {
    static Set<Color> getImgColors(BufferedImage Img) {
        Set<Color> colors = new HashSet<>();
        for ( int x = 0; x < Img.getWidth(); x++ ) {
            for( int y = 0; y < Img.getHeight(); y++ ) {
                colors.add(new Color( Img.getRGB( x, y ) ));
            }
        }
        return colors;
    }

    static void printColors(Set<Color> raidColors) {
        StringBuilder colorOutStr = new StringBuilder();
        for (Color col: raidColors) {
            if (colorOutStr.length() > 0) colorOutStr.append(", ");

            colorOutStr.append("new Color (").append(col.getRed()).append(", ")
                    .append(col.getGreen()).append(", ")
                    .append(col.getBlue()).append(")");
        }
        System.out.println(colorOutStr.toString());
    }

    public static void main(String[] args) {
        String usage = "ImageHelp <imagePath>";

        if (args.length <1) {
            System.out.println(usage);
        } else {
            File imageFile = new File(args[0]);

            if (!imageFile.exists()) {
                System.out.println("File " + args[0] + " does not exist. Aborting.");
                return;
            }

            if (imageFile.isDirectory()) {
                System.out.println("Path " + args[0] + " is a directory. Aborting.");
                return;
            }

            BufferedImage image;

            try {
                image = ImageIO.read(imageFile);
            } catch (IOException e) {
                System.out.println("Error when loading image file.");
                return;
            }

            Set<Color> colors = getImgColors(image);

            printColors(colors);

        }
    }
}
