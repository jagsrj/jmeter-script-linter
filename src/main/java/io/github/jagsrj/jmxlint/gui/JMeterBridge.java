package io.github.jagsrj.jmxlint.gui;

import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.lang.reflect.Method;

/**
 * Thin reflective access to the few JMeter GUI APIs the plugin needs. Using reflection keeps the plugin
 * binary-compatible across JMeter 5.x versions and lets the core build without JMeter jars.
 * Nothing here is cached in static fields, so no JMeter GUI object is ever retained by the plugin.
 */
final class JMeterBridge {

    private JMeterBridge() {
    }

    private static Class<?> load(String name) throws ClassNotFoundException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try {
            return Class.forName(name, true, cl != null ? cl : JMeterBridge.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            return Class.forName(name);
        }
    }

    private static Object guiPackage() throws ReflectiveOperationException {
        return load("org.apache.jmeter.gui.GuiPackage").getMethod("getInstance").invoke(null);
    }

    private static Object call(Object target, String method) throws ReflectiveOperationException {
        Method m = target.getClass().getMethod(method);
        return m.invoke(target);
    }

    /** Absolute path of the currently open .jmx, or null when the plan was never saved. */
    static String testPlanFile() {
        try {
            Object gp = guiPackage();
            return gp == null ? null : (String) call(gp, "getTestPlanFile");
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    static boolean isDirty() {
        try {
            Object gp = guiPackage();
            return gp != null && Boolean.TRUE.equals(call(gp, "isDirty"));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    static Frame mainFrame() {
        try {
            Object gp = guiPackage();
            Object f = gp == null ? null : call(gp, "getMainFrame");
            return f instanceof Frame ? (Frame) f : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** Triggers JMeter's own File &gt; Save action (synchronously, must run on the EDT). */
    static void saveTestPlan(Object source) throws ReflectiveOperationException {
        Object router = load("org.apache.jmeter.gui.action.ActionRouter").getMethod("getInstance").invoke(null);
        router.getClass().getMethod("doActionNow", ActionEvent.class)
                .invoke(router, new ActionEvent(source, ActionEvent.ACTION_PERFORMED, "save"));
    }

    static String jmeterProperty(String name) {
        try {
            Object v = load("org.apache.jmeter.util.JMeterUtils").getMethod("getProperty", String.class)
                    .invoke(null, name);
            return v == null ? null : v.toString();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    static String jmeterBinDir() {
        try {
            Object v = load("org.apache.jmeter.util.JMeterUtils").getMethod("getJMeterBinDir").invoke(null);
            return v == null ? null : v.toString();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
