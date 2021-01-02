package com.github.ilpersi.BHBot;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

public class ImageHelper {
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

            Set<Color> colors = new LinkedHashSet<>();
            for ( int x = 0; x < image.getWidth(); x++ ) {
                for( int y = 0; y < image.getHeight(); y++ ) {
                    colors.add(new Color( image.getRGB( x, y ) ));
                }
            }

            StringBuilder colorOutStr = new StringBuilder();
            for (Color col: colors) {
                if (colorOutStr.length() > 0) colorOutStr.append(", ");

                colorOutStr.append("new Color (").append(col.getRed()).append(", ")
                        .append(col.getGreen()).append(", ")
                        .append(col.getBlue()).append(")");
            }

            System.out.println(colorOutStr.toString());

        }
    }
}
