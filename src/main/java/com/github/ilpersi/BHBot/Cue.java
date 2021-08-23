package com.github.ilpersi.BHBot;

import java.awt.image.BufferedImage;

/**
 * @author Betalord
 */
public class Cue {
    public String name;
    String path;
    BufferedImage im;
    Bounds bounds;

    @SuppressWarnings("unused")
    public Cue(String name, String path, BufferedImage im) {
        this.name = name;
        this.im = im;
        bounds = null;
    }

    Cue(String name, String path, BufferedImage im, Bounds bounds) {
        this.name = name;
        this.path = path;
        this.im = im;
        this.bounds = bounds;
    }

    Cue(Cue cue, Bounds bounds) {
        this.name = cue.name;
        this.path = cue.path;
        this.im = cue.im;
        this.bounds = bounds;
    }

    @Override
    public String toString() {
        return "Cue [" +
                "name='" + name + '\'' +
                ", path='" + path + '\'' +
                ", bounds=" + bounds +
                ']';
    }
}
