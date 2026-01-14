package org.slb4j.ext.swing;

import org.jspecify.annotations.Nullable;
import org.slb4j.ConsoleCode;
import org.slb4j.LogFilter;
import org.slb4j.LogLevel;
import org.slb4j.LogPattern;
import org.slb4j.ext.LogBuffer;
import org.slb4j.ext.LogEntry;
import org.slb4j.ext.LogEntryFilter;
import org.slb4j.ext.LogPane;
import org.slb4j.ext.LogPaneTexts;
import org.slb4j.filter.LogLevelFilter;
import org.slb4j.filter.LoggerNameFilter;
import org.slb4j.filter.MessageTextFilter;

import org.slb4j.SLB4J;
import org.slb4j.filter.LoggerNamePrefixFilter;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.Locale;
import java.util.Objects;

/**
 * SwingLogPane is a custom Swing component that provides a log viewer with filtering
 * and search capabilities. It consists of a JTable for displaying log entries and a
 * JTextArea for showing details of the selected log entry.
 */
public class SwingLogPane extends JPanel implements LogPane {

    /**
     * A default instance of {@link LogPaneTexts} that provides predefined text values
     * used to label various components of the log pane user interface.
     */
    public static final LogPaneTexts DEFAULT_TEXTS = LogPaneTexts.of(
            "Up",
            "Down",
            "Clear",
            "Level",
            "Logger",
            "Text",
            "Search",
            "Time",
            "Level",
            "Logger",
            "Message"
    );

    private final LogBuffer logBuffer;
    private final JTable table;
    private final LogTableModel tableModel;
    private final TableRowSorter<LogTableModel> sorter;
    private final JTextArea details;
    private final LogPattern pattern = LogPattern.DEFAULT_PATTERN;
    @SuppressWarnings("StringBufferField")
    private final StringBuilder buffer = new StringBuilder(4096);
    private boolean autoScroll = true;
    private boolean darkMode = false;

    private static final java.awt.Color LIGHT_BG = java.awt.Color.WHITE;
    private static final java.awt.Color LIGHT_FG = java.awt.Color.BLACK;
    private static final java.awt.Color DARK_BG = new java.awt.Color(43, 43, 43);
    private static final java.awt.Color DARK_FG = new java.awt.Color(187, 187, 187);

    private static final java.util.Map<LogLevel, java.awt.Color> LIGHT_COLORS = java.util.Map.of(
            LogLevel.ERROR, new java.awt.Color(139, 0, 0), // darkred
            LogLevel.WARN, java.awt.Color.RED,
            LogLevel.INFO, new java.awt.Color(0, 0, 139), // darkblue
            LogLevel.DEBUG, java.awt.Color.BLACK,
            LogLevel.TRACE, java.awt.Color.DARK_GRAY
    );

    private static final java.util.Map<LogLevel, java.awt.Color> DARK_COLORS = java.util.Map.of(
            LogLevel.ERROR, java.awt.Color.RED,
            LogLevel.WARN, new java.awt.Color(255, 69, 0), // orangered
            LogLevel.INFO, java.awt.Color.WHITE,
            LogLevel.DEBUG, java.awt.Color.DARK_GRAY,
            LogLevel.TRACE, new java.awt.Color(105, 105, 105) // dimgray
    );

    public SwingLogPane() {
        this(LogBuffer.DEFAULT_CAPACITY);
    }

    public SwingLogPane(int bufferSize) {
        this(createBuffer(bufferSize));
    }

    public SwingLogPane(LogBuffer logBuffer) {
        this(logBuffer, DEFAULT_TEXTS);
    }

    public SwingLogPane(LogBuffer logBuffer, LogPaneTexts texts) {
        super(new BorderLayout());
        this.logBuffer = Objects.requireNonNull(logBuffer);

        tableModel = new LogTableModel(logBuffer);
        table = new JTable(tableModel);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);

        details = new JTextArea();
        details.setEditable(false);
        details.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JScrollPane tableScrollPane = new JScrollPane(table);
        JScrollPane detailsScrollPane = new JScrollPane(details);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScrollPane, detailsScrollPane);
        splitPane.setDividerLocation(300);
        add(splitPane, BorderLayout.CENTER);

        // ToolBar
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);

        JComboBox<LogLevel> cbLogLevel = new JComboBox<>(LogLevel.values());
        cbLogLevel.setSelectedItem(LogLevel.INFO);
        JTextField tfLoggerName = new JTextField(15);
        JTextField tfMessageContent = new JTextField(15);

        Runnable updateFilter = () -> {
            LogLevel level = (LogLevel) cbLogLevel.getSelectedItem();
            String loggerText = tfLoggerName.getText().toLowerCase(Locale.ROOT).strip();
            String messageText = tfMessageContent.getText();

            LogFilter filter = level == null ? LogFilter.allPass() : new LogLevelFilter("Filter level", level);
            if (!loggerText.isEmpty()) {
                filter = filter.andThen(new LoggerNameFilter("loggerName", name -> name.toLowerCase(Locale.ROOT).contains(loggerText)));
            }
            if (!messageText.isEmpty()) {
                filter = filter.andThen(new MessageTextFilter("messageContent", text -> text.contains(messageText)));
            }

            final LogFilter finalFilter = filter;
            sorter.setRowFilter(new RowFilter<>() {
                @Override
                public boolean include(Entry<? extends LogTableModel, ? extends Integer> entry) {
                    LogEntry logEntry = entry.getModel().getEntry(entry.getIdentifier());
                    return logEntry != null && LogEntryFilter.forFilter(finalFilter).test(logEntry);
                }
            });
        };

        cbLogLevel.addActionListener(e -> updateFilter.run());
        tfLoggerName.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {updateFilter.run();}
        });
        tfMessageContent.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {updateFilter.run();}
        });

        toolBar.add(new JLabel(texts.labelFilterLogLevel()));
        toolBar.add(cbLogLevel);
        toolBar.addSeparator();
        toolBar.add(new JLabel(texts.labelFilterLogger()));
        toolBar.add(tfLoggerName);
        toolBar.addSeparator();
        toolBar.add(new JLabel(texts.labelFilterLogMessage()));
        toolBar.add(tfMessageContent);
        toolBar.addSeparator();

        JTextField tfSearchText = new JTextField(15);
        JButton btnSearchUp = new JButton(texts.textSearchUp());
        JButton btnSearchDown = new JButton(texts.textSearchDown());

        Runnable searchUp = () -> searchAction(tfSearchText.getText(), true);
        Runnable searchDown = () -> searchAction(tfSearchText.getText(), false);

        tfSearchText.addActionListener(e -> searchDown.run());
        btnSearchUp.addActionListener(e -> searchUp.run());
        btnSearchDown.addActionListener(e -> searchDown.run());

        toolBar.add(new JLabel(texts.labelSearchText()));
        toolBar.add(tfSearchText);
        toolBar.add(btnSearchUp);
        toolBar.add(btnSearchDown);
        toolBar.addSeparator();

        JButton btnClear = new JButton(texts.textClear());
        btnClear.addActionListener(e -> logBuffer.clear());
        toolBar.add(btnClear);

        add(toolBar, BorderLayout.NORTH);

        // Columns
        setupColumns(texts);

        // Selection listener
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int viewRow = table.getSelectedRow();
                if (viewRow >= 0) {
                    int modelRow = table.convertRowIndexToModel(viewRow);
                    LogEntry entry = tableModel.getEntry(modelRow);
                    details.setText(formatLogEntry(entry));
                    autoScroll = false;
                } else {
                    details.setText("");
                    autoScroll = isScrolledToBottom();
                }
            }
        });

        // Autoscroll logic
        tableModel.addTableModelListener(e -> {
            if (autoScroll) {
                SwingUtilities.invokeLater(() -> {
                    int rowCount = table.getRowCount();
                    if (rowCount > 0) {
                        table.scrollRectToVisible(table.getCellRect(rowCount - 1, 0, true));
                    }
                });
            }
        });

        tableScrollPane.getVerticalScrollBar().addAdjustmentListener(e -> {
            if (!e.getValueIsAdjusting()) {
                autoScroll = isScrolledToBottom() && table.getSelectedRow() == -1;
            }
        });

        updateFilter.run();
    }

    private void setupColumns(LogPaneTexts texts) {
        TableColumn colTime = table.getColumnModel().getColumn(0);
        colTime.setHeaderValue(texts.headerTimeColumn());
        colTime.setCellRenderer(new LogEntryRenderer(new LogPattern.DateEntry("HH:mm:ss,SSS")));
        int timeWidth = getColumnWidth("88:88:88,888") + 10;
        colTime.setPreferredWidth(timeWidth);
        colTime.setMinWidth(timeWidth);
        colTime.setMaxWidth(timeWidth);

        TableColumn colLevel = table.getColumnModel().getColumn(1);
        colLevel.setHeaderValue(texts.headerLevelColumn());
        colLevel.setCellRenderer(new LogEntryRenderer(new LogPattern.LevelEntry(0, 0, false)));
        int levelWidth = getColumnWidth("SEVERE") + 10;
        colLevel.setPreferredWidth(levelWidth);
        colLevel.setMinWidth(levelWidth);
        colLevel.setMaxWidth(levelWidth);

        TableColumn colLogger = table.getColumnModel().getColumn(2);
        colLogger.setHeaderValue(texts.headerLoggerColumn());
        colLogger.setCellRenderer(new LogEntryRenderer(new LogPattern.LoggerEntry(0, 0, true, 0)));
        colLogger.setPreferredWidth(150);

        TableColumn colMessage = table.getColumnModel().getColumn(3);
        colMessage.setHeaderValue(texts.headerMessageColumn());
        colMessage.setCellRenderer(new LogEntryRenderer(new LogPattern.MessageEntry(0, 0, false)));
        colMessage.setPreferredWidth(500);
    }

    private int getColumnWidth(String sampleText) {
        Font font = table.getFont();
        java.awt.font.FontRenderContext frc = new java.awt.font.FontRenderContext(null, true, true);
        return (int) font.getStringBounds(sampleText, frc).getWidth();
    }

    private boolean isScrolledToBottom() {
        Rectangle visibleRect = table.getVisibleRect();
        return visibleRect.y + visibleRect.height >= table.getHeight();
    }

    private void searchAction(String text, boolean up) {
        if (text == null || text.isEmpty()) return;
        String lowercaseText = text.toLowerCase(Locale.ROOT);
        int rowCount = table.getRowCount();
        if (rowCount == 0) return;

        int startRow = table.getSelectedRow();
        int step = up ? -1 : 1;

        for (int i = 0; i < rowCount; i++) {
            int row = Math.floorMod(startRow + step * (i + 1), rowCount);
            int modelRow = table.convertRowIndexToModel(row);
            LogEntry entry = tableModel.getEntry(modelRow);
            String message = entry == null ? null : entry.message();
            if (message != null && message.toLowerCase(Locale.ROOT).contains(lowercaseText)) {
                table.setRowSelectionInterval(row, row);
                table.scrollRectToVisible(table.getCellRect(row, 0, true));
                return;
            }
        }
    }

    private String formatLogEntry(@Nullable LogEntry entry) {
        if (entry == null) return "";

        StringBuilder sb = new StringBuilder();
        try {
            pattern.formatLogEntry(sb, entry.time(), entry.logger(), entry.level(), entry.marker(), entry.mdc(), entry::location, entry::message, entry.throwable(), ConsoleCode.empty());
        } catch (IOException e) {
            sb.append("Error formatting log entry: ").append(e.getMessage());
        }
        return sb.toString();
    }

    private class LogEntryRenderer extends DefaultTableCellRenderer {
        private final LogPattern.LogPatternEntry patternEntry;

        LogEntryRenderer(LogPattern.LogPatternEntry patternEntry) {
            this.patternEntry = patternEntry;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (value instanceof LogEntry entry) {
                buffer.setLength(0);
                try {
                    patternEntry.format(buffer, entry.time(), entry.logger(), entry.level(), entry.marker(), entry.mdc(), entry.location(), entry::message, entry.throwable(), ConsoleCode.empty());
                    setText(buffer.toString());
                } catch (IOException e) {
                    setText("ERROR");
                }

                if (!isSelected) {
                    java.awt.Color fg = darkMode ? DARK_COLORS.get(entry.level()) : LIGHT_COLORS.get(entry.level());
                    if (fg != null) {
                        setForeground(fg);
                    }
                }
            }
            return this;
        }
    }

    @Override
    public void setDarkMode(boolean dark) {
        this.darkMode = dark;
        java.awt.Color bg = dark ? DARK_BG : LIGHT_BG;
        java.awt.Color fg = dark ? DARK_FG : LIGHT_FG;

        table.setBackground(bg);
        table.setForeground(fg);
        table.setGridColor(dark ? java.awt.Color.GRAY : java.awt.Color.LIGHT_GRAY);

        details.setBackground(bg);
        details.setForeground(fg);
        details.setCaretColor(fg);

        table.repaint();
    }

    private static LogBuffer createBuffer(int bufferSize) {
        LogBuffer buffer = new LogBuffer("Log Buffer", bufferSize);
        LoggerNamePrefixFilter filter = new LoggerNamePrefixFilter("filter");
        filter.setLevel("", LogLevel.TRACE);
        filter.setLevel("javafx", LogLevel.INFO);
        buffer.setFilter(filter);
        SLB4J.getDispatcher().addLogHandler(buffer);
        return buffer;
    }

    public LogBuffer getLogBuffer() {
        return logBuffer;
    }
}
