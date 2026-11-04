package io.github.jagsrj.jmxlint.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import io.github.jagsrj.jmxlint.config.LintConfig;
import io.github.jagsrj.jmxlint.model.JmxParser;
import io.github.jagsrj.jmxlint.model.TestPlanModel;
import io.github.jagsrj.jmxlint.rules.RuleRegistry;

/** Runs the enabled rules against a test plan. Stateless apart from its immutable config and rule list. */
public final class LintEngine {

    public static final String PARSE_ERROR = "PARSE_ERROR";
    public static final String RULE_FAILURE = "RULE_FAILURE";

    private static final Comparator<Finding> ORDER = Comparator
            .comparing(Finding::severity)
            .thenComparing(Finding::category)
            .thenComparing(Finding::ruleId)
            .thenComparing(Finding::elementPath);

    private final LintConfig config;
    private final List<Rule> rules;

    public LintEngine(LintConfig config) {
        this(config, RuleRegistry.all());
    }

    public LintEngine(LintConfig config, List<Rule> rules) {
        this.config = config;
        this.rules = Collections.unmodifiableList(new ArrayList<>(rules));
    }

    public List<Rule> rules() { return rules; }

    public LintConfig config() { return config; }

    /** Parses and lints a file. Parse failures are returned as a PARSE_ERROR finding instead of thrown. */
    public LintResult lint(Path file) {
        TestPlanModel model;
        try {
            model = new JmxParser().parse(file);
        } catch (IOException e) {
            List<Finding> f = new ArrayList<>();
            f.add(new Finding(PARSE_ERROR, Category.BEST_PRACTICE, Severity.ERROR, file.toString(), "",
                    "Could not parse file: " + e.getMessage(), "Open and re-save the plan in JMeter."));
            return new LintResult(file.toString(), f, 0, 0);
        }
        return lint(model);
    }

    public LintResult lint(TestPlanModel model) {
        RuleContext ctx = new RuleContext(config, model);
        int run = 0;
        for (Rule rule : rules) {
            if (!config.ruleEnabled(rule.id())) {
                continue;
            }
            run++;
            ctx.begin(rule, config.severity(rule.id(), rule.defaultSeverity()));
            try {
                rule.check(model, ctx);
            } catch (RuntimeException e) {
                // one broken rule (e.g. a bad regex in the rules file) must not hide the others
                ctx.findings().add(new Finding(RULE_FAILURE, rule.category(), Severity.ERROR, model.source(), "",
                        "Rule " + rule.id() + " failed: " + e.getMessage(),
                        "Check the parameters of " + rule.id() + " in your rules file."));
            }
        }
        List<Finding> sorted = new ArrayList<>(ctx.findings());
        sorted.sort(ORDER);
        return new LintResult(model.source(), sorted, model.all().size(), run);
    }
}
