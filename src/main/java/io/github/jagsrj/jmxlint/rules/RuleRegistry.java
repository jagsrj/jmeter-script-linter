package io.github.jagsrj.jmxlint.rules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.jagsrj.jmxlint.core.Rule;

/** All built-in rules. Rules are stateless, so a single shared immutable list is safe. */
public final class RuleRegistry {

    private static final List<Rule> ALL;

    static {
        List<Rule> r = new ArrayList<>();
        r.addAll(NamingRules.all());
        r.addAll(MemoryRules.all());
        r.addAll(BestPracticeRules.all());
        ALL = Collections.unmodifiableList(r);
    }

    private RuleRegistry() {
    }

    public static List<Rule> all() {
        return ALL;
    }
}
