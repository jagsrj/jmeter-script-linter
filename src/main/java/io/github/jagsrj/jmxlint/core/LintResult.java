package io.github.jagsrj.jmxlint.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Findings for one test plan. */
public final class LintResult {

    private final String source;
    private final List<Finding> findings;
    private final int elementCount;
    private final int rulesRun;

    public LintResult(String source, List<Finding> findings, int elementCount, int rulesRun) {
        this.source = source;
        this.findings = Collections.unmodifiableList(new ArrayList<>(findings));
        this.elementCount = elementCount;
        this.rulesRun = rulesRun;
    }

    public String source() { return source; }

    public List<Finding> findings() { return findings; }

    public int elementCount() { return elementCount; }

    public int rulesRun() { return rulesRun; }

    public Map<Severity, Integer> countBySeverity() {
        Map<Severity, Integer> m = new EnumMap<>(Severity.class);
        for (Severity s : Severity.values()) {
            m.put(s, 0);
        }
        for (Finding f : findings) {
            m.put(f.severity(), m.get(f.severity()) + 1);
        }
        return m;
    }

    public boolean hasFindingsAtLeast(Severity threshold) {
        for (Finding f : findings) {
            if (f.severity().atLeast(threshold)) {
                return true;
            }
        }
        return false;
    }
}
