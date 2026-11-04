package io.github.jagsrj.jmxlint.core;

/** One problem found in a test plan. */
public final class Finding {

    private final String ruleId;
    private final Category category;
    private final Severity severity;
    private final String elementPath;
    private final String elementType;
    private final String message;
    private final String recommendation;

    public Finding(String ruleId, Category category, Severity severity, String elementPath, String elementType,
                   String message, String recommendation) {
        this.ruleId = ruleId;
        this.category = category;
        this.severity = severity;
        this.elementPath = elementPath == null ? "" : elementPath;
        this.elementType = elementType == null ? "" : elementType;
        this.message = message == null ? "" : message;
        this.recommendation = recommendation == null ? "" : recommendation;
    }

    public String ruleId() { return ruleId; }

    public Category category() { return category; }

    public Severity severity() { return severity; }

    public String elementPath() { return elementPath; }

    public String elementType() { return elementType; }

    public String message() { return message; }

    public String recommendation() { return recommendation; }

    @Override
    public String toString() {
        return severity + " " + ruleId + " [" + elementPath + "] " + message;
    }
}
