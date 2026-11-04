package io.github.jagsrj.jmxlint.core;

import java.util.function.BiConsumer;

import io.github.jagsrj.jmxlint.model.TestPlanModel;

/** Rule backed by a lambda; keeps rule definitions compact and stateless. */
public final class SimpleRule implements Rule {

    private final String id;
    private final Category category;
    private final Severity defaultSeverity;
    private final String title;
    private final BiConsumer<TestPlanModel, RuleContext> body;

    public SimpleRule(String id, Category category, Severity defaultSeverity, String title,
                      BiConsumer<TestPlanModel, RuleContext> body) {
        this.id = id;
        this.category = category;
        this.defaultSeverity = defaultSeverity;
        this.title = title;
        this.body = body;
    }

    @Override public String id() { return id; }

    @Override public Category category() { return category; }

    @Override public Severity defaultSeverity() { return defaultSeverity; }

    @Override public String title() { return title; }

    @Override
    public void check(TestPlanModel model, RuleContext ctx) {
        body.accept(model, ctx);
    }
}
