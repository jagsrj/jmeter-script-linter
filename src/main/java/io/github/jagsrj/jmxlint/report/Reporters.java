package io.github.jagsrj.jmxlint.report;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.jagsrj.jmxlint.core.Finding;
import io.github.jagsrj.jmxlint.core.LintResult;
import io.github.jagsrj.jmxlint.core.Rule;
import io.github.jagsrj.jmxlint.core.Severity;

/** Renders lint results as text, JSON, JUnit XML (CI test reports) or Markdown (PR comments). */
public final class Reporters {

    public enum Format { TEXT, JSON, JUNIT, MARKDOWN }

    private Reporters() {
    }

    public static Format parseFormat(String s) {
        String v = s.trim().toUpperCase(Locale.ROOT);
        if ("MD".equals(v)) {
            return Format.MARKDOWN;
        }
        try {
            return Format.valueOf(v);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown format '" + s + "' (text, json, junit, markdown)");
        }
    }

    public static String render(Format format, List<LintResult> results) {
        switch (format) {
            case JSON: return json(results);
            case JUNIT: return junit(results);
            case MARKDOWN: return markdown(results);
            default: return text(results);
        }
    }

    // ------------------------------------------------------------------ text
    public static String text(List<LintResult> results) {
        StringBuilder sb = new StringBuilder();
        int[] totals = new int[Severity.values().length];
        for (LintResult r : results) {
            sb.append("== ").append(r.source()).append(" (").append(r.elementCount()).append(" elements, ")
                    .append(r.rulesRun()).append(" rules)\n");
            if (r.findings().isEmpty()) {
                sb.append("   No issues found.\n");
            }
            for (Finding f : r.findings()) {
                totals[f.severity().ordinal()]++;
                sb.append(String.format("  %-5s %-30s %s%n", f.severity(), f.ruleId(), f.elementPath()));
                sb.append("        ").append(f.message()).append('\n');
                if (!f.recommendation().isEmpty()) {
                    sb.append("        -> ").append(f.recommendation()).append('\n');
                }
            }
            sb.append('\n');
        }
        sb.append(String.format("Summary: %d file(s), %d error(s), %d warning(s), %d info%n", results.size(),
                totals[Severity.ERROR.ordinal()], totals[Severity.WARN.ordinal()], totals[Severity.INFO.ordinal()]));
        return sb.toString();
    }

    // ------------------------------------------------------------------ json
    public static String json(List<LintResult> results) {
        StringBuilder sb = new StringBuilder("{\n  \"files\": [");
        for (int i = 0; i < results.size(); i++) {
            LintResult r = results.get(i);
            sb.append(i == 0 ? "\n" : ",\n").append("    {\"source\": ").append(q(r.source()))
                    .append(", \"elements\": ").append(r.elementCount())
                    .append(", \"rulesRun\": ").append(r.rulesRun()).append(", \"summary\": {");
            Map<Severity, Integer> c = r.countBySeverity();
            sb.append("\"ERROR\": ").append(c.get(Severity.ERROR)).append(", \"WARN\": ").append(c.get(Severity.WARN))
                    .append(", \"INFO\": ").append(c.get(Severity.INFO)).append("}, \"findings\": [");
            for (int j = 0; j < r.findings().size(); j++) {
                Finding f = r.findings().get(j);
                sb.append(j == 0 ? "\n" : ",\n").append("      {\"rule\": ").append(q(f.ruleId()))
                        .append(", \"severity\": ").append(q(f.severity().name()))
                        .append(", \"category\": ").append(q(f.category().name()))
                        .append(", \"element\": ").append(q(f.elementPath()))
                        .append(", \"elementType\": ").append(q(f.elementType()))
                        .append(", \"message\": ").append(q(f.message()))
                        .append(", \"recommendation\": ").append(q(f.recommendation())).append('}');
            }
            sb.append(r.findings().isEmpty() ? "]}" : "\n    ]}");
        }
        sb.append(results.isEmpty() ? "]\n}\n" : "\n  ]\n}\n");
        return sb.toString();
    }

    private static String q(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (ch < 0x20) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
            }
        }
        return sb.append('"').toString();
    }

    // ------------------------------------------------------------------ junit
    /** One test suite per file; each finding at/above WARN is a failed test case, INFO findings are skipped. */
    public static String junit(List<LintResult> results) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<testsuites name=\"jmx-lint\">\n");
        for (LintResult r : results) {
            Map<Severity, Integer> c = r.countBySeverity();
            int tests = Math.max(1, r.findings().size());
            sb.append("  <testsuite name=\"").append(x(r.source())).append("\" tests=\"").append(tests)
                    .append("\" failures=\"").append(c.get(Severity.ERROR) + c.get(Severity.WARN))
                    .append("\" skipped=\"").append(c.get(Severity.INFO)).append("\">\n");
            if (r.findings().isEmpty()) {
                sb.append("    <testcase classname=\"jmx-lint\" name=\"all rules passed\"/>\n");
            }
            for (Finding f : r.findings()) {
                sb.append("    <testcase classname=\"").append(x(f.ruleId())).append("\" name=\"")
                        .append(x(f.elementPath())).append("\">");
                String body = x(f.message() + (f.recommendation().isEmpty() ? "" : "\nFix: " + f.recommendation()));
                if (f.severity() == Severity.INFO) {
                    sb.append("<skipped message=\"").append(x(f.message())).append("\"/>");
                } else {
                    sb.append("<failure type=\"").append(f.severity()).append("\" message=\"").append(x(f.message()))
                            .append("\">").append(body).append("</failure>");
                }
                sb.append("</testcase>\n");
            }
            sb.append("  </testsuite>\n");
        }
        return sb.append("</testsuites>\n").toString();
    }

    private static String x(String s) {
        StringBuilder sb = new StringBuilder();
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '&': sb.append("&amp;"); break;
                case '"': sb.append("&quot;"); break;
                case '\'': sb.append("&apos;"); break;
                default:
                    if (ch >= 0x20 || ch == '\n' || ch == '\t' || ch == '\r') {
                        sb.append(ch);
                    }
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ markdown
    public static String markdown(List<LintResult> results) {
        StringBuilder sb = new StringBuilder("# JMX Lint report\n\n");
        for (LintResult r : results) {
            Map<Severity, Integer> c = r.countBySeverity();
            sb.append("## `").append(r.source()).append("`\n\n")
                    .append("**").append(c.get(Severity.ERROR)).append("** errors, **")
                    .append(c.get(Severity.WARN)).append("** warnings, **")
                    .append(c.get(Severity.INFO)).append("** info\n\n");
            if (r.findings().isEmpty()) {
                sb.append("No issues found.\n\n");
                continue;
            }
            sb.append("| Severity | Rule | Element | Issue | Fix |\n|---|---|---|---|---|\n");
            for (Finding f : r.findings()) {
                sb.append("| ").append(f.severity()).append(" | ").append(f.ruleId()).append(" | ")
                        .append(md(f.elementPath())).append(" | ").append(md(f.message())).append(" | ")
                        .append(md(f.recommendation())).append(" |\n");
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String md(String s) {
        return s.replace("|", "\\|").replace("\r", " ").replace("\n", " ");
    }

    /** Markdown table of the rule catalogue (used to generate docs/RULES.md). */
    public static String ruleCatalogue(List<Rule> rules, io.github.jagsrj.jmxlint.config.LintConfig cfg) {
        StringBuilder sb = new StringBuilder("| Rule | Category | Default severity | Checks that |\n|---|---|---|---|\n");
        for (Rule r : rules) {
            sb.append("| `").append(r.id()).append("` | ").append(r.category().label()).append(" | ")
                    .append(cfg.severity(r.id(), r.defaultSeverity())).append(" | ").append(md(r.title()))
                    .append(" |\n");
        }
        return sb.toString();
    }
}
