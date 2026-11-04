package io.github.jagsrj.jmxlint.rules;

import static io.github.jagsrj.jmxlint.core.Category.BEST_PRACTICE;
import static io.github.jagsrj.jmxlint.core.Category.SECURITY;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import io.github.jagsrj.jmxlint.core.Rule;
import io.github.jagsrj.jmxlint.core.RuleContext;
import io.github.jagsrj.jmxlint.core.Severity;
import io.github.jagsrj.jmxlint.core.SimpleRule;
import io.github.jagsrj.jmxlint.model.ElementTypes;
import io.github.jagsrj.jmxlint.model.JmxElement;
import io.github.jagsrj.jmxlint.model.TestPlanModel;

/** Script-design, workload-model and security best practices. */
final class BestPracticeRules {

    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

    private BestPracticeRules() {
    }

    static List<Rule> all() {
        List<Rule> r = new ArrayList<>();

        // ---------------------------------------------------------------- workload model
        r.add(new SimpleRule("BP_INFINITE_LOOP", BEST_PRACTICE, Severity.ERROR,
                "Thread Groups with infinite loops have a duration (scheduler) set", (m, ctx) -> {
            for (JmxElement e : ctx.elements()) {
                if (ElementTypes.isClassicThreadGroup(e) && "-1".equals(e.prop("LoopController.loops").trim())
                        && !e.boolProp("ThreadGroup.scheduler", false)) {
                    ctx.report(e, "Loop Count is Infinite and no Duration is set: the test never stops on its own",
                            "Tick 'Specify Thread lifetime' and set Duration, e.g. ${__P(duration,3600)}.");
                }
            }
        }));

        r.add(new SimpleRule("BP_RAMP_UP", BEST_PRACTICE, Severity.WARN,
                "Thread Groups with more than one thread have a ramp-up period", (m, ctx) -> {
            for (JmxElement e : ctx.elements()) {
                if (!ElementTypes.isClassicThreadGroup(e)) {
                    continue;
                }
                String threads = e.prop("ThreadGroup.num_threads").trim();
                String ramp = e.prop("ThreadGroup.ramp_time").trim();
                if (ElementTypes.isLiteralInteger(threads) && Integer.parseInt(threads) > 1
                        && ElementTypes.isLiteralInteger(ramp) && Integer.parseInt(ramp) == 0) {
                    ctx.report(e, threads + " threads start at once (ramp-up 0)",
                            "Set a ramp-up so load builds gradually and the system can warm up.");
                }
            }
        }));

        r.add(new SimpleRule("BP_PARAMETERIZE_LOAD", BEST_PRACTICE, Severity.INFO,
                "Thread count, ramp-up and duration are parameterized with ${__P()}", (m, ctx) -> {
            for (JmxElement e : ctx.elements()) {
                if (!ElementTypes.isClassicThreadGroup(e) || ElementTypes.isSetupOrTeardown(e)) {
                    continue;
                }
                List<String> hard = new ArrayList<>();
                addIfLiteral(e, "ThreadGroup.num_threads", "threads", hard);
                addIfLiteral(e, "ThreadGroup.ramp_time", "ramp-up", hard);
                if (e.boolProp("ThreadGroup.scheduler", false)) {
                    addIfLiteral(e, "ThreadGroup.duration", "duration", hard);
                }
                if (!hard.isEmpty()) {
                    ctx.report(e, "Hard-coded " + String.join(", ", hard),
                            "Use ${__P(threads,1)} style properties so the same script runs smoke, load and "
                                    + "stress tests from the command line / CI (-Jthreads=50).");
                }
            }
        }));

        r.add(new SimpleRule("BP_THINK_TIME", BEST_PRACTICE, Severity.WARN,
                "Thread Groups include think time (timers or Flow Control Action pauses)", (m, ctx) -> {
            for (JmxElement tg : ctx.elements()) {
                if (!ElementTypes.isThreadGroup(tg) || ElementTypes.isSetupOrTeardown(tg)
                        || TestPlanModel.enabledDescendants(tg, ElementTypes::isSampler).isEmpty()) {
                    continue;
                }
                boolean hasPause = !TestPlanModel.enabledDescendants(tg, d -> ElementTypes.isTimer(d)
                        || ElementTypes.isFlowControlAction(d)).isEmpty()
                        || TestPlanModel.inScope(tg, ElementTypes::isTimer);
                if (!hasPause) {
                    ctx.report(tg, "No timers: virtual users fire requests back-to-back",
                            "Add think time (e.g. Uniform/Gaussian Random Timer or a Flow Control Action) "
                                    + "or a throughput timer to model real user pacing.");
                }
            }
        }));

        r.add(new SimpleRule("BP_EMPTY_CONTAINER", BEST_PRACTICE, Severity.WARN,
                "Thread Groups and controllers contain at least one enabled sampler", (m, ctx) -> {
            for (JmxElement e : ctx.elements()) {
                boolean container = ElementTypes.isThreadGroup(e) || (ElementTypes.isController(e)
                        && !"ModuleController".equals(e.testClass()) && !"IncludeController".equals(e.testClass()));
                if (container && TestPlanModel.enabledDescendants(e, d -> ElementTypes.isSampler(d)
                        || ElementTypes.isFlowControlAction(d) || "ModuleController".equals(d.testClass())
                        || "IncludeController".equals(d.testClass())).isEmpty()) {
                    ctx.report(e, e.simpleType() + " has no enabled samplers", "Remove or disable it.");
                }
            }
        }));

        // ---------------------------------------------------------------- script design
        r.add(new SimpleRule("BP_MISSING_ASSERTION", BEST_PRACTICE, Severity.WARN,
                "Every sampler is validated by an assertion in scope", (m, ctx) -> {
            List<String> exclude = ctx.listParam("excludeTypes", "DebugSampler,JSR223Sampler,BeanShellSampler");
            for (JmxElement e : ctx.elements()) {
                if (ElementTypes.isSampler(e) && !exclude.contains(e.simpleType())
                        && !TestPlanModel.inScope(e, ElementTypes::isAssertion)) {
                    ctx.report(e, "No assertion in scope: a 200 OK error page would count as success",
                            "Add a Response Assertion / JSON Assertion that checks business content.");
                }
            }
        }));

        r.add(new SimpleRule("BP_TRANSACTION_INCLUDE_TIMERS", BEST_PRACTICE, Severity.WARN,
                "Transaction times exclude timers and pre/post processors", (m, ctx) -> {
            for (JmxElement e : ctx.elements()) {
                if (ElementTypes.isTransaction(e) && e.boolProp("TransactionController.includeTimers", true)) {
                    ctx.report(e, "Transaction includes timer/processor time, inflating response times",
                            "Untick 'Include duration of timer and pre-post processors in generated sample'.");
                }
            }
        }));

        r.add(new SimpleRule("BP_SAMPLER_OUTSIDE_TRANSACTION", BEST_PRACTICE, Severity.INFO,
                "Samplers are grouped under Transaction Controllers", (m, ctx) -> {
            List<String> exclude = ctx.listParam("excludeTypes", "DebugSampler,JSR223Sampler,BeanShellSampler");
            for (JmxElement e : ctx.elements()) {
                if (ElementTypes.isSampler(e) && !exclude.contains(e.simpleType())
                        && TestPlanModel.threadGroupOf(e) != null && e.ancestor(ElementTypes::isTransaction) == null) {
                    ctx.report(e, "Sampler is not inside a Transaction Controller",
                            "Wrap each business step in a Transaction Controller to report step-level SLAs.");
                }
            }
        }));

        r.add(new SimpleRule("BP_EXTRACTOR_DEFAULT", BEST_PRACTICE, Severity.INFO,
                "Extractors define a default value (e.g. NOT_FOUND)", (m, ctx) -> {
            for (JmxElement e : ctx.elements()) {
                String key = defaultValueKey(e);
                if (key != null && e.prop(key).trim().isEmpty()
                        && !e.boolProp("RegexExtractor.default_empty_value", false)) {
                    ctx.report(e, "No default value: a failed extraction silently reuses the old value",
                            "Set Default Value to e.g. NOT_FOUND and assert on it.");
                }
            }
        }));

        r.add(new SimpleRule("BP_IF_CONTROLLER_EXPRESSION", BEST_PRACTICE, Severity.WARN,
                "If Controllers use 'Interpret condition as variable expression'", (m, ctx) -> {
            for (JmxElement e : ctx.elements()) {
                if ("IfController".equals(e.testClass()) && !e.boolProp("IfController.useExpression", false)) {
                    ctx.report(e, "Condition is evaluated with JavaScript on every iteration (slow)",
                            "Tick 'Interpret Condition as Variable Expression' and use ${__groovy(...)} "
                                    + "or ${__jexl3(...)}.");
                }
            }
        }));

        r.add(new SimpleRule("BP_CSV_ABSOLUTE_PATH", BEST_PRACTICE, Severity.WARN,
                "CSV Data Set files use relative or parameterized paths", (m, ctx) -> {
            Pattern abs = Pattern.compile("^([A-Za-z]:[\\\\/]|/|\\\\\\\\).*");
            for (JmxElement e : ctx.elements()) {
                String f = e.prop("filename").trim();
                if (ElementTypes.isCsvDataSet(e) && !ElementTypes.containsVariable(f) && abs.matcher(f).matches()) {
                    ctx.report(e, "Absolute path '" + f + "' will not exist on other machines / load agents",
                            "Use a path relative to the .jmx, or ${__P(dataDir,data)}/file.csv.");
                }
            }
        }));

        r.add(new SimpleRule("BP_DISABLED_ELEMENTS", BEST_PRACTICE, Severity.INFO,
                "No disabled elements left in the committed script", (m, ctx) -> {
            for (JmxElement e : m.all()) {
                if (!e.isEnabled() && (e.parent() == null || e.parent().isEffectivelyEnabled())) {
                    ctx.report(e, "Element is disabled", "Delete dead elements to keep the script maintainable.");
                }
            }
        }));

        r.add(new SimpleRule("BP_TEST_PLAN_DESCRIPTION", BEST_PRACTICE, Severity.INFO,
                "Test Plan has a description in Comments", (m, ctx) -> {
            JmxElement tp = m.testPlan();
            if (tp != null && ElementTypes.isTestPlan(tp) && tp.comments().trim().isEmpty()) {
                ctx.report(tp, "Test Plan has no description",
                        "Describe the application, scenario, owner and required properties (-J...) in Comments.");
            }
        }));

        // ---------------------------------------------------------------- HTTP
        r.add(new SimpleRule("BP_HARDCODED_HOST", BEST_PRACTICE, Severity.WARN,
                "HTTP samplers do not hard-code the server name / URL", (m, ctx) -> {
            for (JmxElement e : ctx.elements()) {
                if (!ElementTypes.isHttpSampler(e)) {
                    continue;
                }
                String domain = e.prop("HTTPSampler.domain").trim();
                String path = e.prop("HTTPSampler.path").trim();
                if (!domain.isEmpty() && !ElementTypes.containsVariable(domain)) {
                    String what = IPV4.matcher(domain).matches() ? "IP address" : "host";
                    ctx.report(e, "Hard-coded " + what + " '" + domain + "'",
                            "Leave Server Name empty and set it once in HTTP Request Defaults as ${__P(host,...)}, "
                                    + "so the script can target any environment.");
                } else if (path.matches("(?i)^https?://.*") && !ElementTypes.containsVariable(path)) {
                    ctx.report(e, "Full URL hard-coded in Path", "Put the host in HTTP Request Defaults.");
                }
            }
        }));

        r.add(new SimpleRule("BP_HTTP_DEFAULTS", BEST_PRACTICE, Severity.INFO,
                "Plans with HTTP samplers use HTTP Request Defaults", (m, ctx) -> {
            boolean hasHttp = false;
            boolean hasDefaults = false;
            for (JmxElement e : ctx.elements()) {
                hasHttp |= ElementTypes.isHttpSampler(e);
                hasDefaults |= ElementTypes.isHttpDefaults(e);
            }
            if (hasHttp && !hasDefaults) {
                ctx.report(m.testPlan(), "No HTTP Request Defaults element",
                        "Add HTTP Request Defaults for protocol, host, port and timeouts.");
            }
        }));

        r.add(new SimpleRule("BP_COOKIE_MANAGER", BEST_PRACTICE, Severity.WARN,
                "Thread Groups with HTTP samplers have an HTTP Cookie Manager", (m, ctx) -> {
            for (JmxElement tg : ctx.elements()) {
                if (!ElementTypes.isThreadGroup(tg)) {
                    continue;
                }
                for (JmxElement s : TestPlanModel.enabledDescendants(tg, ElementTypes::isHttpSampler)) {
                    if (!TestPlanModel.inScope(s, ElementTypes::isCookieManager)) {
                        ctx.report(tg, "HTTP samplers run without an HTTP Cookie Manager in scope",
                                "Add an HTTP Cookie Manager (clear cookies each iteration) to handle sessions.");
                        break;
                    }
                }
            }
        }));

        r.add(new SimpleRule("BP_HTTP_TIMEOUTS", BEST_PRACTICE, Severity.WARN,
                "HTTP requests have connect and response timeouts", (m, ctx) -> {
            for (JmxElement tg : ctx.elements()) {
                if (!ElementTypes.isThreadGroup(tg)) {
                    continue;
                }
                int missing = 0;
                for (JmxElement s : TestPlanModel.enabledDescendants(tg, ElementTypes::isHttpSampler)) {
                    if (!hasTimeouts(s) && !TestPlanModel.inScope(s, d -> ElementTypes.isHttpDefaults(d)
                            && hasTimeouts(d))) {
                        missing++;
                    }
                }
                if (missing > 0) {
                    ctx.report(tg, missing + " HTTP request(s) have no connect/response timeout",
                            "Set Connect and Response timeouts in HTTP Request Defaults so hung calls cannot "
                                    + "block virtual users forever.");
                }
            }
        }));

        r.add(new SimpleRule("BP_KEEPALIVE", BEST_PRACTICE, Severity.WARN,
                "HTTP samplers use KeepAlive", (m, ctx) -> {
            for (JmxElement e : ctx.elements()) {
                if (ElementTypes.isHttpSampler(e) && !e.boolProp("HTTPSampler.use_keepalive", true)) {
                    ctx.report(e, "KeepAlive is off: a new TCP/TLS connection is opened for every request",
                            "Tick 'Use KeepAlive' to behave like a browser and avoid port exhaustion.");
                }
            }
        }));

        r.add(new SimpleRule("BP_HTTP_IMPLEMENTATION", BEST_PRACTICE, Severity.INFO,
                "HTTP samplers use the HttpClient4 implementation", (m, ctx) -> {
            for (JmxElement e : ctx.elements()) {
                if ((ElementTypes.isHttpSampler(e) || ElementTypes.isHttpDefaults(e))
                        && "Java".equalsIgnoreCase(e.prop("HTTPSampler.implementation").trim())) {
                    ctx.report(e, "Uses the basic 'Java' HTTP implementation",
                            "Use HttpClient4 (default) for proper connection pooling, cookies and TLS.");
                }
            }
        }));

        r.add(new SimpleRule("BP_UNCORRELATED_VALUE", BEST_PRACTICE, Severity.WARN,
                "Dynamic values (session ids, CSRF tokens, view state) are correlated, not hard-coded", (m, ctx) -> {
            Pattern names = ctx.pattern("namePattern",
                    "(?i).*(session|csrf|xsrf|token|viewstate|eventvalidation|nonce|requestverification).*");
            Pattern secret = ctx.config().pattern("SEC_HARDCODED_SECRET", "namePattern", SECRET_NAMES);
            int minLength = ctx.intParam("minValueLength", 8);
            if (names == null) {
                return;
            }
            for (JmxElement e : ctx.elements()) {
                for (Map<String, String> item : e.items()) {
                    String n = itemName(item);
                    String v = itemValue(item);
                    if (n.isEmpty() || v.length() < minLength || ElementTypes.containsVariable(v)) {
                        continue;
                    }
                    boolean dynamic = names.matcher(n).matches() || "cookie".equalsIgnoreCase(n);
                    if (dynamic && (secret == null || !secret.matcher(n).matches())) {
                        ctx.report(e, "'" + n + "' is sent with a recorded literal value",
                                "Correlate it: extract the value from the previous response and use ${var}.");
                    }
                }
            }
        }));

        // ---------------------------------------------------------------- security
        r.add(new SimpleRule("SEC_HARDCODED_SECRET", SECURITY, Severity.ERROR,
                "No hard-coded passwords, API keys or Authorization headers", BestPracticeRules::checkSecrets));
        return r;
    }

    static final String SECRET_NAMES =
            "(?i).*(password|passwd|pwd|secret|api[_-]?key|apikey|authorization|access[_-]?key).*";

    private static void checkSecrets(TestPlanModel m, RuleContext ctx) {
        Pattern names = ctx.pattern("namePattern", SECRET_NAMES);
        Pattern propKeys = ctx.pattern("propertyPattern", "(?i)(^|\\.)(password|passwd|pwd|secret|apikey|api_key)$");
        Pattern body = ctx.pattern("bodyPattern",
                "(?i)[\"']?(password|passwd|pwd|client_secret|api_?key)[\"']?\\s*[:=]\\s*[\"']?(?!\\$\\{)[^\"'&,\\s}]{3,}");
        for (JmxElement e : ctx.elements()) {
            boolean reported = false;
            for (Map<String, String> item : e.items()) {
                String n = itemName(item);
                String v = itemValue(item);
                String pwd = item.get("Authorization.password");
                if (pwd != null && !pwd.trim().isEmpty() && !ElementTypes.containsVariable(pwd)) {
                    ctx.report(e, "Authorization Manager contains a literal password",
                            "Load credentials from a CSV / ${__P(password)} / environment, never commit them.");
                    reported = true;
                } else if (names != null && !n.isEmpty() && names.matcher(n).matches() && !v.trim().isEmpty()
                        && !ElementTypes.containsVariable(v)) {
                    ctx.report(e, "'" + n + "' has a literal value",
                            "Parameterize secrets (CSV Data Set, ${__P(...)}, ${__env(...)}) and keep them out of git.");
                    reported = true;
                } else if (body != null && body.matcher(v).find()) {
                    ctx.report(e, "Request body contains a literal credential",
                            "Replace it with a variable fed from a CSV file or property.");
                    reported = true;
                }
                if (reported) {
                    break;
                }
            }
            if (reported || propKeys == null) {
                continue;
            }
            for (Map.Entry<String, String> p : e.props().entrySet()) {
                String v = p.getValue().trim();
                if (propKeys.matcher(p.getKey()).find() && !v.isEmpty() && !ElementTypes.containsVariable(v)) {
                    ctx.report(e, "Property '" + p.getKey() + "' has a literal value",
                            "Parameterize it with ${__P(...)} or ${__env(...)}.");
                    break;
                }
            }
            if (!reported && body != null && ElementTypes.isHttpSampler(e)
                    && body.matcher(e.prop("HTTPSampler.path")).find()) {
                ctx.report(e, "URL query string contains a literal credential", "Use a variable instead.");
            }
        }
    }

    static String itemName(Map<String, String> item) {
        for (String k : new String[]{"Argument.name", "Header.name", "_name"}) {
            String v = item.get(k);
            if (v != null && !v.trim().isEmpty()) {
                return v.trim();
            }
        }
        return "";
    }

    static String itemValue(Map<String, String> item) {
        for (String k : new String[]{"Argument.value", "Header.value", "Cookie.value", "value"}) {
            String v = item.get(k);
            if (v != null) {
                return v;
            }
        }
        return "";
    }

    private static boolean hasTimeouts(JmxElement e) {
        return !e.prop("HTTPSampler.connect_timeout").trim().isEmpty()
                && !e.prop("HTTPSampler.response_timeout").trim().isEmpty();
    }

    private static void addIfLiteral(JmxElement e, String key, String label, List<String> out) {
        String v = e.prop(key).trim();
        if (!v.isEmpty() && !ElementTypes.containsVariable(v)) {
            out.add(label + "=" + v);
        }
    }

    private static String defaultValueKey(JmxElement e) {
        switch (e.testClass()) {
            case "RegexExtractor": return "RegexExtractor.default";
            case "JSONPostProcessor": return "JSONPostProcessor.defaultValues";
            case "BoundaryExtractor": return "BoundaryExtractor.default";
            case "XPathExtractor": return "XPathExtractor.default";
            case "XPath2Extractor": return "XPathExtractor2.default";
            case "HtmlExtractor": return "HtmlExtractor.default";
            case "JMESPathExtractor": return "JMESExtractor.defaultValue";
            default: return null;
        }
    }
}
