package com.github.ilpersi.BHBot;

import java.awt.image.BufferedImage;
import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Cue cue = (Cue) o;
        return name.equals(cue.name) && bounds.equals(cue.bounds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, bounds);
    }
}
