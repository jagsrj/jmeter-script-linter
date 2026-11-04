package io.github.jagsrj.jmxlint.rules;

import static io.github.jagsrj.jmxlint.core.Category.NAMING;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.jagsrj.jmxlint.core.Rule;
import io.github.jagsrj.jmxlint.core.RuleContext;
import io.github.jagsrj.jmxlint.core.Severity;
import io.github.jagsrj.jmxlint.core.SimpleRule;
import io.github.jagsrj.jmxlint.model.ElementTypes;
import io.github.jagsrj.jmxlint.model.JmxElement;
import io.github.jagsrj.jmxlint.model.TestPlanModel;

/** Naming convention rules. All patterns are overridable in the rules file. */
final class NamingRules {

    static final String DEFAULT_NAMES = "Test Plan,Thread Group,setUp Thread Group,tearDown Thread Group,"
            + "HTTP Request,JSR223 Sampler,BeanShell Sampler,Debug Sampler,JDBC Request,Java Request,TCP Sampler,"
            + "Flow Control Action,FTP Request,SMTP Sampler,OS Process Sampler,LDAP Request,JMS Publisher,"
            + "Transaction Controller,Simple Controller,Loop Controller,If Controller,While Controller,"
            + "Runtime Controller,Throughput Controller,Once Only Controller,Random Controller,Switch Controller,"
            + "ForEach Controller,Module Controller,Critical Section Controller,Interleave Controller,"
            + "Regular Expression Extractor,JSON Extractor,Boundary Extractor,CSS Selector Extractor,"
            + "XPath Extractor,XPath2 Extractor,JSON JMESPath Extractor,JSR223 PreProcessor,JSR223 PostProcessor,"
            + "JSR223 Assertion,JSR223 Timer,BeanShell PreProcessor,BeanShell PostProcessor,"
            + "bzm - Concurrency Thread Group,bzm - Arrivals Thread Group,jp@gc - Ultimate Thread Group,"
            + "jp@gc - Stepping Thread Group (deprecated),Open Model Thread Group";

    private NamingRules() {
    }

    static List<Rule> all() {
        List<Rule> r = new ArrayList<>();
        r.add(namePattern("NAMING_TEST_PLAN", "Test Plan name follows the naming convention",
                ElementTypes::isTestPlan, "^[A-Za-z][A-Za-z0-9 _.\\-]{2,79}$", "AccountOpening_Load_v1"));
        r.add(namePattern("NAMING_THREAD_GROUP", "Thread Group names follow TGnn_BusinessFlow",
                ElementTypes::isThreadGroup, "^(TG\\d{2}|SETUP|TEARDOWN)_[A-Za-z][A-Za-z0-9_]*$",
                "TG01_AccountOpening"));
        r.add(namePattern("NAMING_TRANSACTION", "Transaction Controller names follow Tnn_Action",
                ElementTypes::isTransaction, "^(?:[A-Za-z0-9]+_)?T\\d{2,3}_[A-Za-z][A-Za-z0-9_]*$",
                "T01_LaunchHomePage"));
        r.add(namePattern("NAMING_SAMPLER", "Sampler names are descriptive and contain only safe characters",
                ElementTypes::isSampler, "^[A-Za-z0-9][A-Za-z0-9 _.\\-]*$", "01_GET_HomePage"));
        r.add(defaultName());
        r.add(recordedName());
        r.add(variableNames());
        r.add(duplicateNames());
        r.add(unsafeCharacters());
        return r;
    }

    private static Rule namePattern(String id, String title, Predicate<JmxElement> selector, String defPattern,
                                    String defExample) {
        return new SimpleRule(id, NAMING, Severity.WARN, title, (TestPlanModel m, RuleContext ctx) -> {
            Pattern p = ctx.pattern("pattern", defPattern);
            int max = ctx.intParam("maxLength", 80);
            String example = ctx.param("example", defExample);
            for (JmxElement e : ctx.elements()) {
                if (!selector.test(e)) {
                    continue;
                }
                String n = e.name();
                if (p != null && !p.matcher(n).matches()) {
                    ctx.report(e, "Name '" + n + "' does not match the convention " + p.pattern(),
                            "Rename it following the convention, e.g. '" + example + "'.");
                } else if (max > 0 && n.length() > max) {
                    ctx.report(e, "Name is " + n.length() + " characters long (max " + max + ")",
                            "Shorten the name; long names clutter reports and dashboards.");
                }
            }
        });
    }

    private static Rule defaultName() {
        return new SimpleRule("NAMING_DEFAULT_NAME", NAMING, Severity.WARN,
                "Elements are renamed from their JMeter default name", (m, ctx) -> {
            Set<String> defaults = new HashSet<>();
            for (String s : ctx.listParam("defaultNames", DEFAULT_NAMES)) {
                defaults.add(s.toLowerCase(Locale.ROOT));
            }
            for (JmxElement e : ctx.elements()) {
                if (defaults.contains(e.name().trim().toLowerCase(Locale.ROOT))) {
                    ctx.report(e, "Element still has its default name '" + e.name() + "'",
                            "Give it a meaningful business name so results and logs are readable.");
                }
            }
        });
    }

    private static Rule recordedName() {
        return new SimpleRule("NAMING_RECORDED_NAME", NAMING, Severity.WARN,
                "Samplers do not keep auto-recorded names (URL paths, '-123' suffixes)", (m, ctx) -> {
            Pattern p = ctx.pattern("pattern", "^(/|https?://).*|.*-\\d+$");
            if (p == null) {
                return;
            }
            for (JmxElement e : ctx.elements()) {
                if (ElementTypes.isSampler(e) && p.matcher(e.name()).matches()) {
                    ctx.report(e, "Sampler name '" + e.name() + "' looks auto-recorded",
                            "Rename it to describe the business step, e.g. '02_POST_Login'.");
                }
            }
        });
    }

    private static Rule variableNames() {
        return new SimpleRule("NAMING_VARIABLE", NAMING, Severity.WARN,
                "Variable names (extractors, CSV, User Defined Variables) follow the convention", (m, ctx) -> {
            Pattern p = ctx.pattern("pattern", "^[A-Za-z_][A-Za-z0-9_]*$");
            if (p == null) {
                return;
            }
            for (JmxElement e : ctx.elements()) {
                for (String var : declaredVariables(e)) {
                    if (!var.isEmpty() && !ElementTypes.containsVariable(var) && !p.matcher(var).matches()) {
                        ctx.report(e, "Variable name '" + var + "' does not match " + p.pattern(),
                                "Use letters, digits and underscores only (e.g. camelCase or UPPER_SNAKE).");
                    }
                }
            }
        });
    }

    /** Variable names an element declares. */
    static List<String> declaredVariables(JmxElement e) {
        List<String> out = new ArrayList<>();
        String t = e.testClass();
        switch (t) {
            case "RegexExtractor":
                out.add(e.prop("RegexExtractor.refname").trim());
                break;
            case "BoundaryExtractor":
                out.add(e.prop("BoundaryExtractor.refname").trim());
                break;
            case "XPathExtractor":
                out.add(e.prop("XPathExtractor.refname").trim());
                break;
            case "XPath2Extractor":
                out.add(e.prop("XPathExtractor2.refname").trim());
                break;
            case "HtmlExtractor":
                out.add(e.prop("HtmlExtractor.refname").trim());
                break;
            case "JMESPathExtractor":
                out.add(e.prop("JMESExtractor.referenceName").trim());
                break;
            case "JSONPostProcessor":
                split(e.prop("JSONPostProcessor.referenceNames"), ";", out);
                break;
            case "CSVDataSet":
                split(e.prop("variableNames"), ",", out);
                break;
            case "TestPlan":
            case "Arguments":
                for (Map<String, String> item : e.items()) {
                    String n = item.get("Argument.name");
                    if (n != null) {
                        out.add(n.trim());
                    }
                }
                break;
            default:
                break;
        }
        return out;
    }

    private static void split(String value, String sep, List<String> out) {
        for (String s : value.split(Pattern.quote(sep))) {
            out.add(s.trim());
        }
    }

    private static Rule duplicateNames() {
        return new SimpleRule("NAMING_DUPLICATE", NAMING, Severity.WARN,
                "Sampler and transaction names are unique (duplicates merge in reports)", (m, ctx) -> {
            boolean perThreadGroup = "threadGroup".equalsIgnoreCase(ctx.param("scope", "testPlan"));
            List<String> exclude = ctx.listParam("excludeTypes", "DebugSampler");
            Map<String, JmxElement> seen = new HashMap<>();
            for (JmxElement e : ctx.elements()) {
                boolean candidate = (ElementTypes.isSampler(e) || ElementTypes.isTransaction(e))
                        && !exclude.contains(e.simpleType()) && !e.name().trim().isEmpty();
                if (!candidate) {
                    continue;
                }
                JmxElement tg = TestPlanModel.threadGroupOf(e);
                String key = (perThreadGroup && tg != null ? tg.path() + "\u0000" : "") + e.name().trim();
                JmxElement first = seen.get(key);
                if (first == null) {
                    seen.put(key, e);
                } else {
                    ctx.report(e, "Name '" + e.name() + "' is also used by " + first.path(),
                            "Make names unique so response times are not merged in reports.");
                }
            }
        });
    }

    private static Rule unsafeCharacters() {
        return new SimpleRule("NAMING_UNSAFE_CHARS", NAMING, Severity.WARN,
                "Names have no characters that break CSV/JTL/HTML reports or leading/trailing spaces", (m, ctx) -> {
            Pattern bad = ctx.pattern("forbiddenChars", "[,\"\\t\\r\\n<>|;]");
            for (JmxElement e : ctx.elements()) {
                if (!(ElementTypes.isSampler(e) || ElementTypes.isTransaction(e) || ElementTypes.isThreadGroup(e))) {
                    continue;
                }
                String n = e.name();
                if (!n.equals(n.trim())) {
                    ctx.report(e, "Name '" + n + "' has leading or trailing whitespace",
                            "Trim the name; whitespace creates look-alike entries in reports.");
                }
                if (bad != null) {
                    Matcher mt = bad.matcher(n);
                    if (mt.find()) {
                        ctx.report(e, "Name '" + n.trim() + "' contains the character '"
                                + printable(mt.group()) + "'", "Use only letters, digits, '_', '-', '.' and spaces.");
                    }
                }
            }
        });
    }

    private static String printable(String s) {
        return s.replace("\t", "\\t").replace("\r", "\\r").replace("\n", "\\n");
    }
}
