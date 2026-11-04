package io.github.jagsrj.jmxlint.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;

import io.github.jagsrj.jmxlint.config.LintConfig;
import io.github.jagsrj.jmxlint.core.Finding;
import io.github.jagsrj.jmxlint.core.LintResult;
import io.github.jagsrj.jmxlint.core.Severity;
import io.github.jagsrj.jmxlint.report.Reporters;

/** Non-modal results window. Disposed on close, so its table model and findings become garbage. */
final class LintResultDialog extends JDialog {

    private static final long serialVersionUID = 1L;
    private static final String[] COLUMNS = {"Severity", "Rule", "Element", "Issue"};

    private final transient LintResult result;

    private LintResultDialog(Frame owner, LintResult result, LintConfig config, Runnable rerun) {
        super(owner, LintMenuCreator.TITLE + " - " + new File(result.source()).getName(), false);
        this.result = result;
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        FindingsModel model = new FindingsModel(result.findings());
        JTable table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(false);
        TableRowSorter<FindingsModel> sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);
        table.getColumnModel().getColumn(0).setPreferredWidth(70);
        table.getColumnModel().getColumn(1).setPreferredWidth(220);
        table.getColumnModel().getColumn(2).setPreferredWidth(380);
        table.getColumnModel().getColumn(3).setPreferredWidth(520);
        table.getColumnModel().getColumn(0).setCellRenderer(new SeverityRenderer());

        JTextArea detail = new JTextArea(5, 80);
        detail.setEditable(false);
        detail.setLineWrap(true);
        detail.setWrapStyleWord(true);
        detail.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        table.getSelectionModel().addListSelectionListener(e -> {
            int view = table.getSelectedRow();
            if (view < 0) {
                detail.setText("");
                return;
            }
            Finding f = model.get(table.convertRowIndexToModel(view));
            detail.setText(f.severity() + "  " + f.ruleId() + "  (" + f.category().label() + ")\n"
                    + "Element: " + f.elementPath() + "  [" + f.elementType() + "]\n\n"
                    + f.message() + "\n\nHow to fix: " + f.recommendation());
            detail.setCaretPosition(0);
        });

        JComboBox<String> filter = new JComboBox<>(new String[]{"All", "ERROR", "WARN and above", "INFO only"});
        filter.addActionListener(e -> {
            final int sel = filter.getSelectedIndex();
            sorter.setRowFilter(sel == 0 ? null : new RowFilter<FindingsModel, Integer>() {
                @Override
                public boolean include(Entry<? extends FindingsModel, ? extends Integer> entry) {
                    Severity s = entry.getModel().get(entry.getIdentifier()).severity();
                    return sel == 1 ? s == Severity.ERROR : sel == 2 ? s.atLeast(Severity.WARN) : s == Severity.INFO;
                }
            });
        });

        Map<Severity, Integer> c = result.countBySeverity();
        JLabel summary = new JLabel(String.format("%d error(s), %d warning(s), %d info   |   %d elements, %d rules   "
                        + "|   rules: %s", c.get(Severity.ERROR), c.get(Severity.WARN), c.get(Severity.INFO),
                result.elementCount(), result.rulesRun(), config.source()));
        summary.setFont(summary.getFont().deriveFont(Font.BOLD));

        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        top.add(summary, BorderLayout.CENTER);
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        filterPanel.add(new JLabel("Show:"));
        filterPanel.add(filter);
        top.add(filterPanel, BorderLayout.EAST);

        JButton export = new JButton("Export report...");
        export.addActionListener(e -> export());
        JButton again = new JButton("Re-run");
        again.addActionListener(e -> {
            dispose();
            rerun.run();
        });
        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(export);
        buttons.add(again);
        buttons.add(close);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(table), new JScrollPane(detail));
        split.setResizeWeight(0.8);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(top, BorderLayout.NORTH);
        getContentPane().add(split, BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);
        setPreferredSize(new Dimension(1200, 650));
        pack();
        setLocationRelativeTo(owner);
        if (model.getRowCount() > 0) {
            table.setRowSelectionInterval(0, 0);
        } else {
            detail.setText("No issues found. Nice script!");
        }
    }

    static void show(Frame owner, LintResult result, LintConfig config, Runnable rerun) {
        new LintResultDialog(owner, result, config, rerun).setVisible(true);
    }

    private void export() {
        JFileChooser chooser = new JFileChooser(new File(result.source()).getParentFile());
        chooser.setDialogTitle("Export JMX Lint report");
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("Markdown (*.md)", "md"));
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("JSON (*.json)", "json"));
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("JUnit XML (*.xml)", "xml"));
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("Text (*.txt)", "txt"));
        String base = new File(result.source()).getName().replaceFirst("(?i)\\.jmx$", "");
        chooser.setSelectedFile(new File(chooser.getCurrentDirectory(), base + "-lint.md"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File f = chooser.getSelectedFile();
        String name = f.getName().toLowerCase(Locale.ROOT);
        Reporters.Format fmt = name.endsWith(".json") ? Reporters.Format.JSON
                : name.endsWith(".xml") ? Reporters.Format.JUNIT
                : name.endsWith(".txt") ? Reporters.Format.TEXT : Reporters.Format.MARKDOWN;
        try (Writer w = Files.newBufferedWriter(f.toPath(), StandardCharsets.UTF_8)) {
            w.write(Reporters.render(fmt, Collections.singletonList(result)));
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not write report: " + ex.getMessage(),
                    LintMenuCreator.TITLE, JOptionPane.ERROR_MESSAGE);
        }
    }

    private static final class FindingsModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private final transient List<Finding> rows;

        FindingsModel(List<Finding> rows) {
            this.rows = new ArrayList<>(rows);
        }

        Finding get(int row) {
            return rows.get(row);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 0 ? Severity.class : String.class;
        }

        @Override
        public Object getValueAt(int row, int column) {
            Finding f = rows.get(row);
            switch (column) {
                case 0: return f.severity();
                case 1: return f.ruleId();
                case 2: return f.elementPath();
                default: return f.message();
            }
        }
    }

    private static final class SeverityRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus,
                                                       int row, int column) {
            Component c = super.getTableCellRendererComponent(t, value, selected, focus, row, column);
            if (!selected) {
                Severity s = (Severity) value;
                c.setForeground(s == Severity.ERROR ? new Color(0xB0, 0x1E, 0x1E)
                        : s == Severity.WARN ? new Color(0xA0, 0x60, 0x00) : new Color(0x30, 0x60, 0xA0));
            }
            c.setFont(c.getFont().deriveFont(Font.BOLD));
            return c;
        }
    }
}
