package io.github.jagsrj.jmxlint.rules;

import static io.github.jagsrj.jmxlint.core.Category.MEMORY;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.jagsrj.jmxlint.core.Rule;
import io.github.jagsrj.jmxlint.core.Severity;
import io.github.jagsrj.jmxlint.core.SimpleRule;
import io.github.jagsrj.jmxlint.model.ElementTypes;
import io.github.jagsrj.jmxlint.model.JmxElement;
import io.github.jagsrj.jmxlint.model.TestPlanModel;

/**
 * Memory-leak and resource-usage rules: things in a JMX that make the load generator's heap or Metaspace grow
 * during a long test, or leak threads / file handles / connections from scripts.
 */
final class MemoryRules {

    private static final Pattern JMETER_VAR = Pattern.compile("\\$\\{[^}]*\\}?");

    private MemoryRules() {
    }

    static List<Rule> all() {
        List<Rule> r = new ArrayList<>();

        r.add(new SimpleRule("MEM_FUNCTIONAL_MODE", MEMORY, Severity.ERROR,
                "Test Plan 'Functional Test Mode' is off (it stores every response)", (m, ctx) -> {
            for (JmxElement e : ctx.elements()) {
                if (ElementTypes.isTestPlan(e) && e.boolProp("TestPlan.functional_mode", false)) {
                    ctx.report(e, "Functional Test Mode is enabled: every request and response body is saved",
                            "Untick 'Functional Test Mode' on the Test Plan before running load.");
                }
            }
        }));

        r.add(new SimpleRule("MEM_VIEW_RESULTS_TREE", MEMORY, Severity.ERROR,
                "No enabled View Results Tree listener", (m, ctx) -> {
            for (JmxElement e : ctx.elements()) {
                if (ElementTypes.isResultCollector(e) && "ViewResultsFullVisualizer".equals(e.simpleGuiClass())) {
                    ctx.report(e, "View Results Tree keeps full request/response data in memory",
                            "Disable or delete it before load tests; use it only while debugging with 1 user.");
                }
            }
        }));

        r.add(new SimpleRule("MEM_GUI_LISTENER", MEMORY, Severity.WARN,
                "No in-memory GUI listeners (Summary/Aggregate/Graph/Table ...) in load scripts", (m, ctx) -> {
            List<String> allowed = ctx.listParam("allowedGuiClasses", "SimpleDataWriter");
            for (JmxElement e : ctx.elements()) {
                String gui = e.simpleGuiClass();
                if (ElementTypes.isResultCollector(e) && !"ViewResultsFullVisualizer".equals(gui)
                        && !allowed.contains(gui)) {
                    ctx.report(e, "GUI listener '" + gui + "' aggregates results in JMeter's heap",
                            "Run in CLI mode with -l results.jtl and build the HTML report (-e -o) "
                                    + "or use a Backend Listener (InfluxDB/Grafana).");
                }
            }
        }));

        r.add(new SimpleRule("MEM_SAVE_RESPONSE_DATA", MEMORY, Severity.ERROR,
                "Listeners do not save response / sampler data to results", (m, ctx) -> {
            for (JmxElement e : ctx.elements()) {
                if (!ElementTypes.isResultCollector(e)) {
                    continue;
                }
                if (e.boolProp("saveConfig.responseData", false)) {
                    ctx.report(e, "Listener saves full response data for every sample",
                            "Untick 'Save Response Data (XML)' in the listener's Configure dialog.");
                }
                if (e.boolProp("saveConfig.samplerData", false)) {
                    ctx.report(e, "Listener saves full sampler (request) data for every sample",
                            "Untick 'Save Sampler Data (XML)' in the listener's Configure dialog.");
                }
            }
        }));

        r.add(new SimpleRule("MEM_RESULT_SAVER", MEMORY, Severity.WARN,
                "No 'Save Responses to a file' listener in load scripts", (m, ctx) -> {
            for (JmxElement e : ctx.elements()) {
                if ("ResultSaver".equals(e.testClass())) {
                    ctx.report(e, "Save Responses to a file writes every response to disk",
                            "Disable it for load runs, or tick 'Save Failed Responses only'.");
                }
            }
        }));

        r.add(new SimpleRule("MEM_DEBUG_ELEMENT", MEMORY, Severity.WARN,
                "No enabled Debug Sampler / Debug PostProcessor", (m, ctx) -> {
            for (JmxElement e : ctx.elements()) {
                if ("DebugSampler".equals(e.testClass()) || "DebugPostProcessor".equals(e.testClass())) {
                    ctx.report(e, e.simpleType() + " dumps all variables/properties on every iteration",
                            "Disable debug elements before committing the script.");
                }
            }
        }));

        r.add(new SimpleRule("MEM_BEANSHELL", MEMORY, Severity.WARN,
                "No BeanShell elements or __BeanShell functions (use JSR223 + Groovy)", (m, ctx) -> {
            for (JmxElement e : ctx.elements()) {
                if (ElementTypes.isBeanShell(e)) {
                    ctx.report(e, "BeanShell is interpreted on every call and scales poorly under load",
                            "Replace with the matching JSR223 element using Groovy (script compilation cached).");
                } else if (anyValueContains(e, "${__BeanShell(")) {
                    ctx.report(e, "Uses the __BeanShell function", "Use ${__groovy(...)} instead.");
                }
            }
        }));

        r.add(new SimpleRule("MEM_JSR223_LANGUAGE", MEMORY, Severity.WARN,
                "JSR223 elements use Groovy", (m, ctx) -> {
            List<String> allowed = ctx.listParam("allowedLanguages", "groovy");
            for (JmxElement e : ctx.elements()) {
                String lang = e.prop("scriptLanguage").trim().toLowerCase(Locale.ROOT);
                if (ElementTypes.isJsr223(e) && !lang.isEmpty() && !allowed.contains(lang)) {
                    ctx.report(e, "JSR223 language is '" + lang + "'",
                            "Use Groovy: it is the only JSR223 language that supports compilation caching.");
                }
            }
        }));

        r.add(new SimpleRule("MEM_JSR223_NO_CACHE", MEMORY, Severity.WARN,
                "Inline JSR223 scripts have 'Cache compiled script' enabled", (m, ctx) -> {
            for (JmxElement e : ctx.elements()) {
                if (!ElementTypes.isJsr223(e) || !ElementTypes.scriptFile(e).isEmpty()
                        || ElementTypes.script(e).trim().isEmpty() || !e.hasProp("cacheKey")) {
                    continue;
                }
                String cache = e.prop("cacheKey").trim();
                if (cache.isEmpty() || "false".equalsIgnoreCase(cache)) {
                    ctx.report(e, "Script is recompiled on every execution (cache disabled)",
                            "Tick 'Cache compiled script if available'. Recompiling under load burns CPU and "
                                    + "fills Metaspace.");
                }
            }
        }));

        r.add(new SimpleRule("MEM_JSR223_INLINE_VARS", MEMORY, Severity.ERROR,
                "JSR223 scripts do not use ${...} inside the script body", (m, ctx) -> {
            for (JmxElement e : ctx.elements()) {
                if (!ElementTypes.isJsr223(e) || !ElementTypes.scriptFile(e).isEmpty()) {
                    continue;
                }
                Set<String> found = new LinkedHashSet<>();
                Matcher mt = JMETER_VAR.matcher(ElementTypes.script(e));
                while (mt.find() && found.size() < 3) {
                    found.add(mt.group());
                }
                if (!found.isEmpty()) {
                    ctx.report(e, "Script body contains " + String.join(", ", found)
                                    + ": each distinct value compiles a new class (Metaspace leak) and breaks caching",
                            "Pass values via the Parameters field or read them with vars.get(\"name\") / "
                                    + "props.get(\"name\"). For Groovy GStrings use $name, not ${name}.");
                }
            }
        }));

        r.add(scriptRule("MEM_SCRIPT_THREADS", Severity.WARN,
                "Scripts do not start their own threads, executors or timers",
                "new\\s+Thread\\s*\\(|Executors\\s*\\.\\s*new|new\\s+(java\\.util\\.)?Timer\\s*\\(|\\.parallelStream\\s*\\(",
                null,
                "Script starts threads/executors that may outlive the sampler",
                "Avoid custom threads; let JMeter's thread groups provide concurrency, or shut executors down."));

        r.add(scriptRule("MEM_SCRIPT_UNCLOSED_RESOURCE", Severity.WARN,
                "Scripts close files, readers, sockets and DB connections",
                "new\\s+(FileWriter|FileReader|FileInputStream|FileOutputStream|BufferedReader|BufferedWriter"
                        + "|PrintWriter|RandomAccessFile|Socket|Scanner)\\s*\\(|DriverManager\\s*\\.\\s*getConnection"
                        + "|\\.openConnection\\s*\\(|\\.openStream\\s*\\(",
                "\\.close\\s*\\(|\\.with(Writer|Reader|Closeable|PrintWriter|InputStream|OutputStream|Stream)\\b"
                        + "|\\.eachLine\\b|\\btry\\s*\\(",
                "Script opens a file/stream/socket/connection with no visible close()",
                "Use try-with-resources or Groovy withWriter/withReader so handles are released every iteration."));

        r.add(scriptRule("MEM_SCRIPT_GROWING_STATE", Severity.INFO,
                "Scripts do not keep growing shared state (props, static collections)",
                "props\\s*\\.\\s*put(Object)?\\s*\\(|vars\\s*\\.\\s*putObject\\s*\\(|static\\s+[\\w<>,\\s]*=\\s*new\\s+"
                        + "(java\\.util\\.)?(concurrent\\.)?(HashMap|ArrayList|LinkedList|ConcurrentHashMap|HashSet"
                        + "|LinkedHashMap|TreeMap)",
                null,
                "Script stores objects in props/vars/static collections",
                "Make sure keys are bounded (not per-iteration ids) and remove entries you no longer need; "
                        + "props live for the whole test."));

        r.add(new SimpleRule("MEM_EMBEDDED_NO_CACHE", MEMORY, Severity.WARN,
                "Embedded-resource download is paired with an HTTP Cache Manager", (m, ctx) -> {
            for (JmxElement e : ctx.elements()) {
                boolean http = ElementTypes.isHttpSampler(e) || ElementTypes.isHttpDefaults(e);
                if (http && e.boolProp("HTTPSampler.image_parser", false)
                        && !TestPlanModel.inScope(e, ElementTypes::isCacheManager)) {
                    ctx.report(e, "'Retrieve All Embedded Resources' is on without an HTTP Cache Manager in scope",
                            "Add an HTTP Cache Manager so static resources are cached like a real browser.");
                }
            }
        }));

        r.add(new SimpleRule("MEM_EXTRACT_ALL_MATCHES", MEMORY, Severity.INFO,
                "Extractors do not store all matches (-1) unless needed", (m, ctx) -> {
            String[] keys = {"RegexExtractor.match_number", "JSONPostProcessor.match_numbers",
                "BoundaryExtractor.match_number", "HtmlExtractor.match_number", "XPathExtractor2.matchNumber"};
            for (JmxElement e : ctx.elements()) {
                if (!ElementTypes.isExtractor(e)) {
                    continue;
                }
                for (String k : keys) {
                    if ("-1".equals(e.prop(k).trim())) {
                        ctx.report(e, "Match No. is -1: every match is stored as a separate variable",
                                "Use a specific match (1 or 0 for random) unless you loop over all values.");
                        break;
                    }
                }
            }
        }));
        return r;
    }

    /** Rule that flags script elements whose inline script matches a pattern and does not match a mitigation. */
    private static Rule scriptRule(String id, Severity sev, String title, String defPattern, String defMitigation,
                                   String message, String fix) {
        return new SimpleRule(id, MEMORY, sev, title, (m, ctx) -> {
            Pattern p = ctx.pattern("pattern", defPattern);
            Pattern mitigation = defMitigation == null ? null : ctx.pattern("mitigationPattern", defMitigation);
            if (p == null) {
                return;
            }
            for (JmxElement e : ctx.elements()) {
                if (!ElementTypes.isScriptElement(e)) {
                    continue;
                }
                String script = ElementTypes.script(e);
                Matcher mt = p.matcher(script);
                if (mt.find() && (mitigation == null || !mitigation.matcher(script).find())) {
                    ctx.report(e, message + " (found '" + mt.group().trim() + "')", fix);
                }
            }
        });
    }

    static boolean anyValueContains(JmxElement e, String needle) {
        for (String v : e.props().values()) {
            if (v.contains(needle)) {
                return true;
            }
        }
        for (Map<String, String> item : e.items()) {
            for (String v : item.values()) {
                if (v.contains(needle)) {
                    return true;
                }
            }
        }
        return false;
    }
}
