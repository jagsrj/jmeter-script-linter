package io.github.jagsrj.jmxlint.core;

import java.util.Locale;

/** Ordered from most to least severe. */
public enum Severity {
    ERROR, WARN, INFO;

    /** True when this severity is at least as severe as the threshold. */
    public boolean atLeast(Severity threshold) {
        return threshold != null && ordinal() <= threshold.ordinal();
    }

    public static Severity parse(String value, Severity defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        String v = value.trim().toUpperCase(Locale.ROOT);
        if ("WARNING".equals(v)) {
            return WARN;
        }
        try {
            return valueOf(v);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown severity '" + value + "' (use ERROR, WARN or INFO)");
        }
    }
}
