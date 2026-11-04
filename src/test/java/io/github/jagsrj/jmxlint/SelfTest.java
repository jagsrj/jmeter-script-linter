package io.github.jagsrj.jmxlint;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import io.github.jagsrj.jmxlint.cli.JmxLintCli;
import io.github.jagsrj.jmxlint.config.LintConfig;
import io.github.jagsrj.jmxlint.core.Finding;
import io.github.jagsrj.jmxlint.core.LintEngine;
import io.github.jagsrj.jmxlint.core.LintResult;
import io.github.jagsrj.jmxlint.core.Rule;
import io.github.jagsrj.jmxlint.core.Severity;
import io.github.jagsrj.jmxlint.model.JmxParser;
import io.github.jagsrj.jmxlint.report.Reporters;
import io.github.jagsrj.jmxlint.rules.RuleRegistry;

/**
 * Dependency-free test suite (runs in the offline build.sh and in Maven's test phase).
 * Usage: java ... SelfTest &lt;fixtures-dir&gt;
 */
public final class SelfTest {

    private static int passed;
    private static final List<String> FAILURES = new ArrayList<>();

    private SelfTest() {
    }

    public static void main(String[] args) throws Exception {
        Path dir = Paths.get(args.length > 0 ? args[0] : "src/test/resources");
        Path good = dir.resolve("good-plan.jmx");
        Path bad = dir.resolve("bad-plan.jmx");
        Path edge = dir.resolve("edge-cases.jmx");
        LintEngine engine = new LintEngine(LintConfig.defaults());

        // ---- clean script produces no findings
        LintResult goodResult = engine.lint(good);
        check("good plan has no findings", goodResult.findings().isEmpty(), goodResult.findings().toString());

        // ---- every rule is exercised by at least one fixture
        LintResult badResult = engine.lint(bad);
        LintResult edgeResult = engine.lint(edge);
        Set<String> fired = new HashSet<>();
        for (Finding f : badResult.findings()) {
            fired.add(f.ruleId());
        }
        for (Finding f : edgeResult.findings()) {
            fired.add(f.ruleId());
        }
        for (Rule r : RuleRegistry.all()) {
            check("rule fires on fixtures: " + r.id(), fired.contains(r.id()), "never triggered");
        }
        check("no internal rule failures", !fired.contains(LintEngine.RULE_FAILURE), fired.toString());

        // ---- specific expectations
        check("memory leak via ${} in JSR223 is an ERROR",
                has(badResult, "MEM_JSR223_INLINE_VARS", Severity.ERROR, "JSR223 PostProcessor"), "");
        check("literal password in raw JSON body is caught",
                has(edgeResult, "SEC_HARDCODED_SECRET", Severity.ERROR, "Recorded call-3"), "");
        check("setUp thread group is not asked for think time",
                !has(edgeResult, "BP_THINK_TIME", null, "SETUP_Data"), "");
        check("hard-coded IP reported as IP", messageContains(badResult, "BP_HARDCODED_HOST", "IP address"), "");

        // ---- suppression via element comments
        check("lint-ignore suppresses listed rules",
                !has(edgeResult, "NAMING_RECORDED_NAME", null, "Recorded call-3")
                        && !has(edgeResult, "BP_MISSING_ASSERTION", null, "Recorded call-3"), "");
        check("lint-ignore leaves other rules active",
                has(edgeResult, "SEC_HARDCODED_SECRET", null, "Recorded call-3"), "");

        // ---- param file: disable, change severity, change pattern
        Properties p = new Properties();
        p.setProperty("MEM_VIEW_RESULTS_TREE.enabled", "false");
        p.setProperty("BP_RAMP_UP.severity", "ERROR");
        p.setProperty("NAMING_THREAD_GROUP.pattern", "^Thread Group$");
        LintResult custom = new LintEngine(LintConfig.of(p)).lint(bad);
        check("rule can be disabled", !has(custom, "MEM_VIEW_RESULTS_TREE", null, ""), "");
        check("severity can be overridden", has(custom, "BP_RAMP_UP", Severity.ERROR, ""), "");
        check("pattern can be overridden", !has(custom, "NAMING_THREAD_GROUP", null, ""), "");

        Path tmpRules = Files.createTempFile("jmx-lint", ".properties");
        try {
            Files.write(tmpRules, "NAMING_SAMPLER.pattern=^\\\\d{2}_[A-Z]+_\\\\w+$\nglobal.failOn=NONE\n"
                    .getBytes(StandardCharsets.UTF_8));
            LintConfig fileCfg = LintConfig.load(tmpRules);
            check("rules file overlays defaults", fileCfg.ruleEnabled("BP_RAMP_UP")
                    && fileCfg.pattern("NAMING_SAMPLER", "pattern", null).matcher("01_GET_Home").matches(), "");
            check("global.failOn=NONE", fileCfg.failOn() == null, "");
        } finally {
            Files.deleteIfExists(tmpRules);
        }

        // ---- a broken regex disables only that rule
        Properties broken = new Properties();
        broken.setProperty("NAMING_SAMPLER.pattern", "([unclosed");
        LintResult br = new LintEngine(LintConfig.of(broken)).lint(bad);
        check("invalid regex reported as RULE_FAILURE", has(br, LintEngine.RULE_FAILURE, Severity.ERROR, ""), "");
        check("other rules still run after a failure", has(br, "BP_INFINITE_LOOP", null, ""), "");

        // ---- defaults file documents every rule
        LintConfig defaults = LintConfig.defaults();
        for (Rule r : RuleRegistry.all()) {
            check("defaults file has " + r.id(), defaults.get(r.id() + ".enabled", null) != null
                    && defaults.severity(r.id(), null) == r.defaultSeverity(), "missing or severity mismatch");
        }

        // ---- secure XML parsing (XXE)
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]>"
                + "<jmeterTestPlan><hashTree><TestPlan testclass=\"TestPlan\" testname=\"&e;\"/></hashTree>"
                + "</jmeterTestPlan>";
        boolean rejected;
        try {
            new JmxParser().parse(new ByteArrayInputStream(xxe.getBytes(StandardCharsets.UTF_8)), "xxe");
            rejected = false;
        } catch (IOException e) {
            rejected = true;
        }
        check("DOCTYPE / external entities rejected", rejected, "");
        Path notJmx = Files.createTempFile("not", ".jmx");
        try {
            Files.write(notJmx, "<html/>".getBytes(StandardCharsets.UTF_8));
            check("non-JMX file gives PARSE_ERROR", has(engine.lint(notJmx), LintEngine.PARSE_ERROR, null, ""), "");
        } finally {
            Files.deleteIfExists(notJmx);
        }

        // ---- reporters
        List<LintResult> both = Arrays.asList(badResult, goodResult);
        String json = Reporters.json(both);
        check("json report", json.trim().startsWith("{") && json.trim().endsWith("}")
                && json.contains("\"rule\": \"MEM_JSR223_INLINE_VARS\""), "");
        String junit = Reporters.junit(both);
        check("junit report", junit.contains("<testsuites") && junit.contains("<failure type=\"ERROR\"")
                && junit.contains("all rules passed"), "");
        check("markdown report", Reporters.markdown(both).contains("| Severity | Rule |"), "");

        // ---- CLI exit codes
        check("cli exit 0 on clean plan", cli(good.toString()) == JmxLintCli.OK, "");
        check("cli exit 1 on errors", cli(bad.toString()) == JmxLintCli.FINDINGS, "");
        check("cli --fail-on NONE", cli("--fail-on", "NONE", bad.toString()) == JmxLintCli.OK, "");
        check("cli exit 1 on WARN threshold", cli("--fail-on", "WARN", edge.toString()) == JmxLintCli.FINDINGS, "");
        check("cli exit 2 on bad option", cli("--nope") == JmxLintCli.USAGE, "");
        check("cli scans directories", cli("--fail-on", "NONE", "-f", "json", dir.toString()) == JmxLintCli.OK, "");

        // ---- the linter itself must not leak memory across runs (important inside a long-lived JMeter GUI)
        checkNoLeak(engine, bad);

        System.out.println();
        System.out.println("SelfTest: " + passed + " passed, " + FAILURES.size() + " failed");
        if (!FAILURES.isEmpty()) {
            for (String f : FAILURES) {
                System.out.println("  FAIL " + f);
            }
            System.exit(1);
        }
    }

    private static void checkNoLeak(LintEngine engine, Path plan) {
        for (int i = 0; i < 300; i++) {
            engine.lint(plan); // warm up JIT, pattern cache, class loading
        }
        long before = usedAfterGc();
        for (int i = 0; i < 3000; i++) {
            engine.lint(plan);
        }
        long after = usedAfterGc();
        long growthKb = (after - before) / 1024;
        System.out.println("  heap after warm-up " + before / 1024 + " KB, after 3000 more runs " + after / 1024
                + " KB (growth " + growthKb + " KB)");
        check("no heap growth across 3000 lint runs", growthKb < 2048, growthKb + " KB growth");
    }

    private static long usedAfterGc() {
        Runtime rt = Runtime.getRuntime();
        long used = Long.MAX_VALUE;
        for (int i = 0; i < 5; i++) {
            System.gc();
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            used = Math.min(used, rt.totalMemory() - rt.freeMemory());
        }
        return used;
    }

    private static int cli(String... args) {
        PrintStream sink = new PrintStream(new ByteArrayOutputStream());
        return JmxLintCli.run(args, sink, sink);
    }

    private static boolean has(LintResult r, String rule, Severity sev, String pathFragment) {
        for (Finding f : r.findings()) {
            if (f.ruleId().equals(rule) && (sev == null || f.severity() == sev)
                    && f.elementPath().contains(pathFragment)) {
                return true;
            }
        }
        return false;
    }

    private static boolean messageContains(LintResult r, String rule, String text) {
        for (Finding f : r.findings()) {
            if (f.ruleId().equals(rule) && f.message().contains(text)) {
                return true;
            }
        }
        return false;
    }

    private static void check(String name, boolean ok, String detail) {
        if (ok) {
            passed++;
            System.out.println("  ok   " + name);
        } else {
            FAILURES.add(name + (detail.isEmpty() ? "" : " -> " + detail));
            System.out.println("  FAIL " + name + (detail.isEmpty() ? "" : " -> " + detail));
        }
    }
}
