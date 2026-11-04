package io.github.jagsrj.jmxlint.config;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import io.github.jagsrj.jmxlint.core.Severity;

/**
 * Rule configuration ("param file").
 *
 * <p>Defaults ship inside the jar ({@value #DEFAULT_RESOURCE}). A team rules file only needs the keys it
 * wants to change; everything else falls back to the defaults. Keys:</p>
 * <pre>
 *   RULE_ID.enabled  = true|false
 *   RULE_ID.severity = ERROR|WARN|INFO
 *   RULE_ID.&lt;param&gt; = value            (rule specific, e.g. NAMING_TRANSACTION.pattern)
 *   global.failOn    = ERROR|WARN|INFO|NONE
 *   global.includeDisabledElements = false
 * </pre>
 */
public final class LintConfig {

    public static final String DEFAULT_RESOURCE = "/io/github/jagsrj/jmxlint/jmx-lint-default.properties";

    /** System / JMeter property that points to a team rules file. */
    public static final String RULES_PROPERTY = "jmxlint.rules";

    /** Conventional file name looked up in JMETER_HOME/bin when no explicit file is given. */
    public static final String RULES_FILE_NAME = "jmx-lint.properties";

    private final Properties props;
    private final String source;
    private final Map<String, Pattern> patternCache = new ConcurrentHashMap<>();

    private LintConfig(Properties props, String source) {
        this.props = props;
        this.source = source;
    }

    public static LintConfig defaults() {
        return new LintConfig(loadDefaults(), "built-in defaults");
    }

    /** Defaults overlaid with the given file (null = defaults only). */
    public static LintConfig load(Path overrideFile) throws IOException {
        Properties p = loadDefaults();
        if (overrideFile == null) {
            return new LintConfig(p, "built-in defaults");
        }
        try (Reader r = new InputStreamReader(Files.newInputStream(overrideFile), StandardCharsets.UTF_8)) {
            Properties user = new Properties();
            user.load(r);
            p.putAll(user);
        }
        return new LintConfig(p, overrideFile.toAbsolutePath().toString());
    }

    /** Defaults overlaid with in-memory overrides (used by tests and embedding code). */
    public static LintConfig of(Properties overrides) {
        Properties p = loadDefaults();
        if (overrides != null) {
            p.putAll(overrides);
        }
        return new LintConfig(p, "custom");
    }

    private static Properties loadDefaults() {
        Properties p = new Properties();
        try (InputStream in = LintConfig.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (in != null) {
                try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    p.load(r);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read built-in rules " + DEFAULT_RESOURCE, e);
        }
        return p;
    }

    /** Text of the bundled default rules file (for --print-default-config). */
    public static String defaultFileText() throws IOException {
        try (InputStream in = LintConfig.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (in == null) {
                return "";
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    public String source() { return source; }

    public String get(String key, String defaultValue) {
        String v = props.getProperty(key);
        return v == null ? defaultValue : v.trim();
    }

    public boolean ruleEnabled(String ruleId) {
        return Boolean.parseBoolean(get(ruleId + ".enabled", "true"));
    }

    public Severity severity(String ruleId, Severity defaultValue) {
        return Severity.parse(get(ruleId + ".severity", null), defaultValue);
    }

    public String param(String ruleId, String key, String defaultValue) {
        return get(ruleId + "." + key, defaultValue);
    }

    public int intParam(String ruleId, String key, int defaultValue) {
        String v = param(ruleId, key, null);
        if (v == null || v.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(ruleId + "." + key + " must be a number but was '" + v + "'");
        }
    }

    /** Comma separated list parameter. */
    public List<String> listParam(String ruleId, String key, String defaultValue) {
        String v = param(ruleId, key, defaultValue);
        if (v == null || v.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        for (String s : v.split(",")) {
            if (!s.trim().isEmpty()) {
                out.add(s.trim());
            }
        }
        return out;
    }

    /** Regex parameter; empty value disables the check (returns null). */
    public Pattern pattern(String ruleId, String key, String defaultValue) {
        String v = param(ruleId, key, defaultValue);
        if (v == null || v.isEmpty()) {
            return null;
        }
        String cacheKey = ruleId + "." + key + "=" + v;
        Pattern p = patternCache.get(cacheKey);
        if (p == null) {
            try {
                p = Pattern.compile(v);
            } catch (PatternSyntaxException e) {
                throw new IllegalArgumentException("Invalid regex in " + ruleId + "." + key + ": " + e.getDescription()
                        + " (remember to double backslashes in .properties files)");
            }
            patternCache.put(cacheKey, p);
        }
        return p;
    }

    public boolean includeDisabledElements() {
        return Boolean.parseBoolean(get("global.includeDisabledElements", "false"));
    }

    /** Minimum severity that fails the build; null means never fail. */
    public Severity failOn() {
        String v = get("global.failOn", "ERROR");
        return "NONE".equalsIgnoreCase(v) ? null : Severity.parse(v, Severity.ERROR);
    }
}
