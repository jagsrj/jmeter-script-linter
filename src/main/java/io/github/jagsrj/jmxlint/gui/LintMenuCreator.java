package io.github.jagsrj.jmxlint.gui;

import java.awt.Frame;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.MenuElement;
import javax.swing.SwingWorker;

import org.apache.jmeter.gui.plugin.MenuCreator;

import io.github.jagsrj.jmxlint.config.LintConfig;
import io.github.jagsrj.jmxlint.core.LintEngine;
import io.github.jagsrj.jmxlint.core.LintResult;

/**
 * Adds "Tools &gt; Lint Test Plan (JMX Lint)" to the JMeter GUI. JMeter discovers this class automatically
 * when the jar is in lib/ext (it scans for MenuCreator implementations with a public no-arg constructor).
 */
public class LintMenuCreator implements MenuCreator {

    static final String TITLE = "JMX Lint";

    @Override
    public JMenuItem[] getMenuItemsAtLocation(MENU_LOCATION location) {
        if (location != MENU_LOCATION.TOOLS) {
            return new JMenuItem[0];
        }
        JMenuItem item = new JMenuItem("Lint Test Plan (JMX Lint)");
        item.setMnemonic(KeyEvent.VK_L);
        item.addActionListener(e -> lintCurrentPlan(item));
        return new JMenuItem[]{item};
    }

    @Override
    public JMenu[] getTopLevelMenus() {
        return new JMenu[0];
    }

    @Override
    public boolean localeChanged(MenuElement menu) {
        return false;
    }

    @Override
    public void localeChanged() {
        // labels are not localized
    }

    /** Runs on the EDT; the lint itself runs on a SwingWorker so the GUI stays responsive. */
    static void lintCurrentPlan(Object source) {
        Frame owner = JMeterBridge.mainFrame();
        String file = JMeterBridge.testPlanFile();
        if (file == null || JMeterBridge.isDirty()) {
            Object[] options = file == null ? new Object[]{"Save and lint", "Cancel"}
                    : new Object[]{"Save and lint", "Lint last saved version", "Cancel"};
            int choice = JOptionPane.showOptionDialog(owner,
                    file == null ? "The test plan has not been saved yet. JMX Lint checks the saved .jmx file."
                            : "The test plan has unsaved changes.",
                    TITLE, JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
            if (choice == 0) {
                try {
                    JMeterBridge.saveTestPlan(source);
                } catch (ReflectiveOperationException | RuntimeException ex) {
                    error(owner, "Could not save the test plan: " + ex);
                    return;
                }
                file = JMeterBridge.testPlanFile();
                if (file == null || JMeterBridge.isDirty()) {
                    return; // user cancelled the save dialog
                }
            } else if (choice != 1 || file == null) {
                return;
            }
        }
        runLint(owner, Paths.get(file));
    }

    static void runLint(Frame owner, Path plan) {
        final LintConfig config;
        try {
            config = LintConfig.load(resolveRules(plan));
        } catch (IOException | IllegalArgumentException ex) {
            error(owner, "Could not load the JMX Lint rules file: " + ex.getMessage());
            return;
        }
        new SwingWorker<LintResult, Void>() {
            @Override
            protected LintResult doInBackground() {
                return new LintEngine(config).lint(plan);
            }

            @Override
            protected void done() {
                try {
                    LintResultDialog.show(owner, get(), config, () -> lintCurrentPlan(owner));
                } catch (Exception ex) {
                    error(owner, "JMX Lint failed: " + ex.getMessage());
                }
            }
        }.execute();
    }

    /**
     * Rules file lookup order: JMeter property jmxlint.rules, jmx-lint.properties next to the .jmx
     * (team file versioned with the scripts), JMETER_HOME/bin/jmx-lint.properties, built-in defaults.
     */
    static Path resolveRules(Path plan) {
        String prop = JMeterBridge.jmeterProperty(LintConfig.RULES_PROPERTY);
        if (prop == null || prop.trim().isEmpty()) {
            prop = System.getProperty(LintConfig.RULES_PROPERTY);
        }
        if (prop != null && !prop.trim().isEmpty()) {
            Path p = Paths.get(prop.trim());
            if (!Files.isRegularFile(p)) {
                throw new IllegalArgumentException("jmxlint.rules points to a missing file: " + p);
            }
            return p;
        }
        Path dir = plan.toAbsolutePath().getParent();
        if (dir != null && Files.isRegularFile(dir.resolve(LintConfig.RULES_FILE_NAME))) {
            return dir.resolve(LintConfig.RULES_FILE_NAME);
        }
        String bin = JMeterBridge.jmeterBinDir();
        if (bin != null && Files.isRegularFile(Paths.get(bin, LintConfig.RULES_FILE_NAME))) {
            return Paths.get(bin, LintConfig.RULES_FILE_NAME);
        }
        return null;
    }

    private static void error(Frame owner, String msg) {
        JOptionPane.showMessageDialog(owner, msg, TITLE, JOptionPane.ERROR_MESSAGE);
    }
}
