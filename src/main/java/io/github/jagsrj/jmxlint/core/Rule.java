package io.github.jagsrj.jmxlint.core;

import io.github.jagsrj.jmxlint.model.TestPlanModel;

/** A single lint check. Implementations must be stateless so one instance can be reused safely. */
public interface Rule {

    String id();

    Category category();

    Severity defaultSeverity();

    /** One-line description of what the rule enforces. */
    String title();

    void check(TestPlanModel model, RuleContext ctx);
}
