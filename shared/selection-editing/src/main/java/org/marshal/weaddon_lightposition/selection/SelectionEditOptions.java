package org.marshal.weaddon_lightposition.selection;

import java.util.Properties;

public record SelectionEditOptions(boolean enabled, int step, int fastStep) {
    public SelectionEditOptions {
        step = Math.clamp(step, 1, 64);
        fastStep = Math.clamp(fastStep, 1, 512);
    }
    public static SelectionEditOptions defaults() { return new SelectionEditOptions(false, 1, 8); }
    public static SelectionEditOptions load(Properties p) {
        return new SelectionEditOptions(Boolean.parseBoolean(p.getProperty("enabled", "false")), number(p, "step", 1), number(p, "fastStep", 8));
    }
    private static int number(Properties p, String name, int fallback) {
        try { return Integer.parseInt(p.getProperty(name, "")); } catch (NumberFormatException ex) { return fallback; }
    }
    public Properties properties() {
        Properties p = new Properties(); p.setProperty("enabled", Boolean.toString(enabled));
        p.setProperty("step", Integer.toString(step)); p.setProperty("fastStep", Integer.toString(fastStep)); return p;
    }
}
