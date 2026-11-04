package io.github.jagsrj.jmxlint.core;

public enum Category {
    NAMING("Naming conventions"),
    MEMORY("Memory & resource usage"),
    BEST_PRACTICE("Best practices"),
    SECURITY("Security");

    private final String label;

    Category(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
