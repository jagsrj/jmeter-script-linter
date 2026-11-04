package io.github.jagsrj.jmxlint.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Immutable-after-parse view of one test element in a JMX file.
 * Holds only Strings (no DOM nodes), so the XML document can be garbage-collected right after parsing.
 */
public final class JmxElement {

    /** JMeter stores the "Comments" field of every element under this property name. */
    public static final String COMMENTS = "TestPlan.comments";

    private final String testClass;
    private final String guiClass;
    private final String name;
    private final boolean enabled;
    private final JmxElement parent;
    private final Map<String, String> props = new LinkedHashMap<>();
    private final List<Map<String, String>> items = new ArrayList<>();
    private final List<JmxElement> children = new ArrayList<>();

    JmxElement(String testClass, String guiClass, String name, boolean enabled, JmxElement parent) {
        this.testClass = testClass == null ? "" : testClass;
        this.guiClass = guiClass == null ? "" : guiClass;
        this.name = name == null ? "" : name;
        this.enabled = enabled;
        this.parent = parent;
    }

    void putProp(String key, String value) {
        if (key != null && !props.containsKey(key)) {
            props.put(key, value == null ? "" : value);
        }
    }

    void addItem(Map<String, String> item) {
        if (!item.isEmpty()) {
            items.add(Collections.unmodifiableMap(item));
        }
    }

    void addChild(JmxElement child) {
        children.add(child);
    }

    public String testClass() { return testClass; }

    /** Test class without package, e.g. "UltimateThreadGroup". */
    public String simpleType() {
        int i = testClass.lastIndexOf('.');
        return i >= 0 ? testClass.substring(i + 1) : testClass;
    }

    public String guiClass() { return guiClass; }

    public String simpleGuiClass() {
        int i = guiClass.lastIndexOf('.');
        return i >= 0 ? guiClass.substring(i + 1) : guiClass;
    }

    public String name() { return name; }

    public boolean isEnabled() { return enabled; }

    public JmxElement parent() { return parent; }

    public List<JmxElement> children() { return Collections.unmodifiableList(children); }

    public Map<String, String> props() { return Collections.unmodifiableMap(props); }

    /** Entries of collection properties (arguments, headers, cookies, credentials ...). */
    public List<Map<String, String>> items() { return Collections.unmodifiableList(items); }

    public boolean hasProp(String key) { return props.containsKey(key); }

    /** Property value or "" when absent. */
    public String prop(String key) {
        String v = props.get(key);
        return v == null ? "" : v;
    }

    public boolean boolProp(String key, boolean defaultValue) {
        String v = props.get(key);
        if (v == null || v.trim().isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(v.trim());
    }

    public String comments() { return prop(COMMENTS); }

    /** False when this element or any ancestor is disabled. */
    public boolean isEffectivelyEnabled() {
        for (JmxElement e = this; e != null; e = e.parent) {
            if (!e.enabled) {
                return false;
            }
        }
        return true;
    }

    public JmxElement ancestor(Predicate<JmxElement> predicate) {
        for (JmxElement e = parent; e != null; e = e.parent) {
            if (predicate.test(e)) {
                return e;
            }
        }
        return null;
    }

    /** All descendants, depth first (iterative, so deep trees cannot overflow the stack). */
    public List<JmxElement> descendants() {
        List<JmxElement> out = new ArrayList<>();
        Deque<JmxElement> stack = new ArrayDeque<>();
        for (int i = children.size() - 1; i >= 0; i--) {
            stack.push(children.get(i));
        }
        while (!stack.isEmpty()) {
            JmxElement e = stack.pop();
            out.add(e);
            for (int i = e.children.size() - 1; i >= 0; i--) {
                stack.push(e.children.get(i));
            }
        }
        return out;
    }

    /** Human readable tree path, e.g. "MyPlan / TG01_Login / T01_Launch / 01_GET_Home". */
    public String path() {
        Deque<String> parts = new ArrayDeque<>();
        for (JmxElement e = this; e != null; e = e.parent) {
            parts.push(e.name.isEmpty() ? "<" + e.simpleType() + ">" : e.name);
        }
        return String.join(" / ", parts);
    }

    @Override
    public String toString() {
        return simpleType() + "[" + name + "]";
    }
}
