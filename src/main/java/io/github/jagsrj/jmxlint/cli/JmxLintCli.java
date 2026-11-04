package io.github.jagsrj.jmxlint.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.github.jagsrj.jmxlint.config.LintConfig;
import io.github.jagsrj.jmxlint.core.LintEngine;
import io.github.jagsrj.jmxlint.core.LintResult;
import io.github.jagsrj.jmxlint.core.Rule;
import io.github.jagsrj.jmxlint.core.Severity;
import io.github.jagsrj.jmxlint.report.Reporters;

/**
 * Command line entry point, for CI pipelines and pre-commit hooks.
 *
 * <pre>java -jar jmx-lint.jar [options] &lt;file.jmx | directory&gt;...</pre>
 * Exit codes: 0 = clean, 1 = findings at/above the fail-on severity, 2 = usage or configuration error.
 */
public final class JmxLintCli {

    public static final int OK = 0;
    public static final int FINDINGS = 1;
    public static final int USAGE = 2;

    private static final String USAGE_TEXT = String.join("\n",
            "Usage: java -jar jmx-lint.jar [options] <file.jmx|directory>...",
            "",
            "Options:",
            "  -r, --rules <file>        Team rules file (.properties). Overrides the built-in defaults.",
            "                            Defaults to -Djmxlint.rules or ./jmx-lint.properties when present.",
            "  -f, --format <fmt>        text (default) | json | junit | markdown",
            "  -o, --output <file>       Write the report to a file instead of stdout",
            "      --fail-on <sev>       ERROR (default) | WARN | INFO | NONE",
            "      --list-rules          Print the rule catalogue and exit",
            "      --print-default-config Print the built-in rules file (start your team file from it)",
            "  -h, --help                Show this help",
            "",
            "Exit codes: 0 clean, 1 findings at/above --fail-on, 2 usage/config error");

    private JmxLintCli() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        Path rules = null;
        Reporters.Format format = Reporters.Format.TEXT;
        Path output = null;
        String failOn = null;
        boolean listRules = false;
        List<Path> inputs = new ArrayList<>();
        try {
            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                switch (a) {
                    case "-h":
                    case "--help":
                        out.println(USAGE_TEXT);
                        return OK;
                    case "-r":
                    case "--rules":
                        rules = Paths.get(value(args, ++i, a));
                        break;
                    case "-f":
                    case "--format":
                        format = Reporters.parseFormat(value(args, ++i, a));
                        break;
                    case "-o":
                    case "--output":
                        output = Paths.get(value(args, ++i, a));
                        break;
                    case "--fail-on":
                        failOn = value(args, ++i, a);
                        break;
                    case "--list-rules":
                        listRules = true;
                        break;
                    case "--print-default-config":
                        out.print(LintConfig.defaultFileText());
                        return OK;
                    default:
                        if (a.startsWith("-")) {
                            throw new IllegalArgumentException("Unknown option " + a);
                        }
                        inputs.add(Paths.get(a));
                }
            }
            LintConfig config = LintConfig.load(resolveRulesFile(rules));
            LintEngine engine = new LintEngine(config);
            if (listRules) {
                printRules(engine, config, out);
                return OK;
            }
            if (inputs.isEmpty()) {
                throw new IllegalArgumentException("No .jmx file or directory given");
            }
            Severity threshold = failOn == null ? config.failOn()
                    : ("NONE".equalsIgnoreCase(failOn) ? null : Severity.parse(failOn, Severity.ERROR));

            List<Path> files = collectJmx(inputs);
            if (files.isEmpty()) {
                throw new IllegalArgumentException("No .jmx files found in " + inputs);
            }
            List<LintResult> results = new ArrayList<>();
            for (Path f : files) {
                results.add(engine.lint(f)); // each model is dropped after linting; only findings are kept
            }
            String report = Reporters.render(format, results);
            if (output == null) {
                out.print(report);
            } else {
                if (output.toAbsolutePath().getParent() != null) {
                    Files.createDirectories(output.toAbsolutePath().getParent());
                }
                try (Writer w = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
                    w.write(report);
                }
                err.println("Report written to " + output.toAbsolutePath());
            }
            if (threshold == null) {
                return OK;
            }
            for (LintResult r : results) {
                if (r.hasFindingsAtLeast(threshold)) {
                    return FINDINGS;
                }
            }
            return OK;
        } catch (IllegalArgumentException | IOException e) {
            err.println("jmx-lint: " + e.getMessage());
            err.println("Run with --help for usage.");
            return USAGE;
        }
    }

    private static String value(String[] args, int i, String option) {
        if (i >= args.length) {
            throw new IllegalArgumentException("Missing value for " + option);
        }
        return args[i];
    }

    static Path resolveRulesFile(Path explicit) {
        if (explicit != null) {
            if (!Files.isRegularFile(explicit)) {
                throw new IllegalArgumentException("Rules file not found: " + explicit);
            }
            return explicit;
        }
        String prop = System.getProperty(LintConfig.RULES_PROPERTY);
        if (prop != null && !prop.trim().isEmpty()) {
            Path p = Paths.get(prop.trim());
            if (!Files.isRegularFile(p)) {
                throw new IllegalArgumentException("Rules file from -D" + LintConfig.RULES_PROPERTY + " not found: " + p);
            }
            return p;
        }
        Path local = Paths.get(LintConfig.RULES_FILE_NAME);
        return Files.isRegularFile(local) ? local : null;
    }

    static List<Path> collectJmx(List<Path> inputs) throws IOException {
        List<Path> files = new ArrayList<>();
        for (Path in : inputs) {
            if (Files.isDirectory(in)) {
                try (Stream<Path> s = Files.walk(in)) { // closed: Files.walk holds directory handles
                    files.addAll(s.filter(p -> Files.isRegularFile(p)
                                    && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jmx"))
                            .sorted().collect(Collectors.toList()));
                }
            } else if (Files.isRegularFile(in)) {
                files.add(in);
            } else {
                throw new IllegalArgumentException("File not found: " + in);
            }
        }
        return files;
    }

    private static void printRules(LintEngine engine, LintConfig config, PrintStream out) {
        out.println("Rules file: " + config.source());
        out.println();
        for (Rule r : engine.rules()) {
            out.printf("%-32s %-8s %-14s %s%n", r.id(),
                    config.ruleEnabled(r.id()) ? config.severity(r.id(), r.defaultSeverity()) : "OFF",
                    r.category().name(), r.title());
        }
    }
}
