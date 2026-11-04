package io.github.jagsrj.jmxlint.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/** The parsed test plan: a forest of {@link JmxElement}s (normally a single TestPlan root). */
public final class TestPlanModel {

    private final String source;
    private final List<JmxElement> roots = new ArrayList<>();
    private final List<JmxElement> all = new ArrayList<>();

    public TestPlanModel(String source) {
        this.source = source;
    }

    void add(JmxElement element, JmxElement parent) {
        if (parent == null) {
            roots.add(element);
        } else {
            parent.addChild(element);
        }
        all.add(element);
    }

    public String source() { return source; }

    public List<JmxElement> roots() { return Collections.unmodifiableList(roots); }

    /** Every element in document order, including disabled ones. */
    public List<JmxElement> all() { return Collections.unmodifiableList(all); }

    public JmxElement testPlan() {
        for (JmxElement e : roots) {
            if (ElementTypes.isTestPlan(e)) {
                return e;
            }
        }
        return roots.isEmpty() ? null : roots.get(0);
    }

    /** Nearest enclosing thread group, or null. */
    public static JmxElement threadGroupOf(JmxElement e) {
        return ElementTypes.isThreadGroup(e) ? e : e.ancestor(ElementTypes::isThreadGroup);
    }

    /**
     * JMeter scoping: config elements, timers, assertions etc. apply to an element when they are a child of the
     * element itself or a child of any of its ancestors. Only enabled elements count.
     */
    public static boolean inScope(JmxElement e, Predicate<JmxElement> predicate) {
        for (JmxElement a = e; a != null; a = a.parent()) {
            for (JmxElement c : a.children()) {
                if (c.isEnabled() && predicate.test(c)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Enabled descendants of e matching the predicate. */
    public static List<JmxElement> enabledDescendants(JmxElement e, Predicate<JmxElement> predicate) {
        List<JmxElement> out = new ArrayList<>();
        for (JmxElement d : e.descendants()) {
            if (d.isEffectivelyEnabled() && predicate.test(d)) {
                out.add(d);
            }
        }
        return out;
    }
}
