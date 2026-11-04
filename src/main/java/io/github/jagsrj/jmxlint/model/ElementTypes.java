package io.github.jagsrj.jmxlint.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Classification helpers based on the JMX testclass / guiclass attributes. */
public final class ElementTypes {

    private static final Set<String> CLASSIC_THREAD_GROUPS =
            new HashSet<>(Arrays.asList("ThreadGroup", "SetupThreadGroup", "PostThreadGroup"));

    private static final Set<String> EXTRACTORS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "RegexExtractor", "JSONPostProcessor", "BoundaryExtractor", "XPathExtractor",
            "XPath2Extractor", "HtmlExtractor", "JMESPathExtractor")));

    private ElementTypes() {
    }

    public static boolean isTestPlan(JmxElement e) {
        return "TestPlan".equals(e.testClass());
    }

    public static boolean isThreadGroup(JmxElement e) {
        return e.simpleType().endsWith("ThreadGroup");
    }

    public static boolean isClassicThreadGroup(JmxElement e) {
        return CLASSIC_THREAD_GROUPS.contains(e.testClass());
    }

    public static boolean isSetupOrTeardown(JmxElement e) {
        return "SetupThreadGroup".equals(e.testClass()) || "PostThreadGroup".equals(e.testClass());
    }

    /** Samplers that produce a result. Flow Control Action (TestAction) is deliberately excluded. */
    public static boolean isSampler(JmxElement e) {
        String t = e.simpleType();
        return t.endsWith("Sampler") || t.endsWith("SamplerProxy");
    }

    public static boolean isHttpSampler(JmxElement e) {
        String t = e.simpleType();
        return t.endsWith("HTTPSamplerProxy") || "HTTPSampler".equals(t) || "HTTPSampler2".equals(t);
    }

    public static boolean isFlowControlAction(JmxElement e) {
        return "TestAction".equals(e.testClass());
    }

    public static boolean isController(JmxElement e) {
        return e.simpleType().endsWith("Controller");
    }

    public static boolean isTransaction(JmxElement e) {
        return "TransactionController".equals(e.testClass());
    }

    public static boolean isAssertion(JmxElement e) {
        return e.simpleType().endsWith("Assertion");
    }

    public static boolean isTimer(JmxElement e) {
        return e.simpleType().endsWith("Timer");
    }

    /** Result collectors = listeners that keep results in memory / GUI. */
    public static boolean isResultCollector(JmxElement e) {
        return e.simpleType().endsWith("ResultCollector");
    }

    public static boolean isJsr223(JmxElement e) {
        return e.testClass().startsWith("JSR223");
    }

    public static boolean isBeanShell(JmxElement e) {
        return e.testClass().startsWith("BeanShell");
    }

    public static boolean isScriptElement(JmxElement e) {
        return isJsr223(e) || isBeanShell(e);
    }

    public static boolean isExtractor(JmxElement e) {
        return EXTRACTORS.contains(e.testClass());
    }

    public static boolean isHttpDefaults(JmxElement e) {
        return "ConfigTestElement".equals(e.testClass()) && e.guiClass().endsWith("HttpDefaultsGui");
    }

    public static boolean isCookieManager(JmxElement e) {
        return "CookieManager".equals(e.testClass());
    }

    public static boolean isCacheManager(JmxElement e) {
        return "CacheManager".equals(e.testClass());
    }

    public static boolean isCsvDataSet(JmxElement e) {
        return "CSVDataSet".equals(e.testClass());
    }

    /** Inline script body of a JSR223 / BeanShell element. */
    public static String script(JmxElement e) {
        if ("BeanShellSampler".equals(e.testClass())) {
            return e.prop("BeanShellSampler.query");
        }
        return e.prop("script");
    }

    /** External script file of a JSR223 / BeanShell element ("" when inline). */
    public static String scriptFile(JmxElement e) {
        if ("BeanShellSampler".equals(e.testClass())) {
            return e.prop("BeanShellSampler.filename").trim();
        }
        return e.prop("filename").trim();
    }

    public static boolean containsVariable(String s) {
        return s != null && s.contains("${");
    }

    public static boolean isLiteralInteger(String s) {
        return s != null && s.trim().matches("-?\\d+");
    }
}
