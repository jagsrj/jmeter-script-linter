package io.github.jagsrj.jmxlint.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.jagsrj.jmxlint.config.LintConfig;
import io.github.jagsrj.jmxlint.model.JmxElement;
import io.github.jagsrj.jmxlint.model.TestPlanModel;

/**
 * Per-run context handed to each rule: rule parameters, the elements to inspect and the finding sink.
 * One context per lint run, so nothing is shared between runs.
 */
public final class RuleContext {

    /** Put "lint-ignore: RULE_ID[, RULE_ID]" or "lint-ignore: ALL" in an element's Comments field. */
    private static final Pattern SUPPRESS = Pattern.compile("(?i)lint-ignore\\s*:?\\s*([A-Za-z0-9_*,\\s]+)");

    private final LintConfig config;
    private final TestPlanModel model;
    private final List<Finding> findings = new ArrayList<>();
    private final List<JmxElement> elements;
    private Rule current;
    private Severity currentSeverity;

    RuleContext(LintConfig config, TestPlanModel model) {
        this.config = config;
        this.model = model;
        List<JmxElement> list = new ArrayList<>();
        boolean includeDisabled = config.includeDisabledElements();
        for (JmxElement e : model.all()) {
            if (includeDisabled || e.isEffectivelyEnabled()) {
                list.add(e);
            }
        }
        this.elements = list;
    }

    void begin(Rule rule, Severity severity) {
        this.current = rule;
        this.currentSeverity = severity;
    }

    List<Finding> findings() {
        return findings;
    }

    public LintConfig config() { return config; }

    public TestPlanModel model() { return model; }

    /** Elements rules should inspect (disabled ones are skipped unless global.includeDisabledElements=true). */
    public List<JmxElement> elements() { return elements; }

    public String param(String key, String defaultValue) {
        return config.param(current.id(), key, defaultValue);
    }

    public int intParam(String key, int defaultValue) {
        return config.intParam(current.id(), key, defaultValue);
    }

    public List<String> listParam(String key, String defaultValue) {
        return config.listParam(current.id(), key, defaultValue);
    }

    /** Compiled regex parameter, or null when configured empty (= check disabled). */
    public Pattern pattern(String key, String defaultValue) {
        return config.pattern(current.id(), key, defaultValue);
    }

    public void report(JmxElement element, String message, String recommendation) {
        if (element != null && isSuppressed(element, current.id())) {
            return;
        }
        findings.add(new Finding(current.id(), current.category(), currentSeverity,
                element == null ? model.source() : element.path(),
                element == null ? "" : element.simpleType(), message, recommendation));
    }

    static boolean isSuppressed(JmxElement element, String ruleId) {
        String comments = element.comments();
        if (comments.isEmpty()) {
            return false;
        }
        Matcher m = SUPPRESS.matcher(comments);
        while (m.find()) {
            for (String token : m.group(1).split("[,\\s]+")) {
                String t = token.trim().toUpperCase(Locale.ROOT);
                if (t.equals(ruleId) || "ALL".equals(t) || "*".equals(t)) {
                    return true;
                }
            }
        }
        return false;
    }
}
