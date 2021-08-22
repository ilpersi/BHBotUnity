package com.github.ilpersi.BHBot;

public class CueTest {
    public static void main(String[] args) {
        final String usage = "CueTest <imageToSearchPat> <cueImagePath>";

        if (args.length <2) {
            System.out.println(usage);
        } else {
            System.out.println("Main image: " + args[0]);
            System.out.println("Cue  image: " + args[1]);

            MarvinSegment seg = MarvinSegment.fromFiles(args[0], args[1]);

            System.out.println();
            if (seg != null) {
                Bounds suggestedBounds = Bounds.fromMarvinSegment(seg, null);
                System.out.println("Cue found at: " + seg);
                System.out.println("Suggested Bounds: " + suggestedBounds.getJavaCode(true, false));
                System.out.println("Suggested Bounds.fromWidthHeight: " + suggestedBounds.getJavaCode(true, true));
            } else {
                System.out.println("Sub image not found.");
            }
        }
    }
}
