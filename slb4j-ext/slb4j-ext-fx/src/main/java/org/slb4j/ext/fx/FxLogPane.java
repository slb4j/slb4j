package org.slb4j.ext.fx;

import org.slb4j.ConsoleCode;
import org.slb4j.LogFilter;
import org.slb4j.LogLevel;
import org.slb4j.LogPattern;
import org.slb4j.SLB4J;
import org.slb4j.ext.LogBuffer;
import org.slb4j.ext.LogEntry;
import org.slb4j.ext.LogPane;
import org.slb4j.ext.LogPaneTexts;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Text;
import org.jspecify.annotations.Nullable;
import org.slb4j.ext.LogEntryFilter;
import org.slb4j.filter.LogLevelFilter;
import org.slb4j.filter.LoggerNameFilter;
import org.slb4j.filter.LoggerNamePrefixFilter;
import org.slb4j.filter.MessageTextFilter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * FxLogPane is a custom JavaFX component that provides a log viewer with filtering
 * and search capabilities. It extends BorderPane and consists of a TableView for
 * displaying log entries and a TextArea for showing details of the selected log entry.
 * The log entries can be filtered by log level, logger name, and message content.
 * Additionally, it supports text search within the log messages.
 */
public class FxLogPane extends BorderPane implements LogPane {

    private static final double COLUMN_WIDTH_MAX = Double.MAX_VALUE;
    private static final double COLUMN_WIDTH_LARGE = 2000.0;

    /**
     * A CSS class constant representing the style applied to rows in the log table.
     * This class name is used to customize the appearance of log table rows within
     * the FxLogPane.
     */
    public static final String CSS_CLASS_LOGTABLE_ROW = "logtable-row";

    /**
     * A string constant representing the prefix "log-" used for CSS classes
     * associated with log level styling within the {@code FxLogPane} component.
     * This prefix can be appended to specific log level names to create dynamic
     * CSS class names (e.g., "log-info" or "log-error"), allowing customization
     * of the appearance of log entries based on their log level.
     */
    public static final String CSS_PREFIX_LOGLEVEL = "log-";

    /**
     * Predefined default texts used for various labels, buttons, and table headers
     * in the {@code FxLogPane} component. These texts act as standard default values
     * for visual elements such as search buttons, log level indicators, and column headers.
     * <ul>
     * <li>"Up": Label for the upward search button.
     * <li>"Down": Label for the downward search button.
     * <li>"Clear": Label for the button to clear logs.
     * <li>"Level": Label for the level filter.
     * <li>"Logger": Label for the logger filter.
     * <li>"Text": Label for the message text filter.
     * <li>"Search": Label for the search text field.
     * <li>"Time": Header text for the timestamp column.
     * <li>"Level": Header text for the second log level column.
     * <li>"Logger": Header text for the second logger column.
     * <li>"Message": Header text for the log message column.
     * </ul>
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

    /**
     * Represents the maximum buffer size for storing log entries in the `FxLogPane` class.
     * This constant defines the upper limit on the number of bytes that can be buffered
     * and is used to manage memory and performance effectively.
     * <p>
     * The value of `MAX_BUFFER_SIZE` is set to 4096, which is suitable for handling
     * a moderate amount of log data in typical use cases.
     */
    private static final int MAX_BUFFER_SIZE = 4096;

    /**
     * A {@code StringBuilder} instance used as a buffer for accumulating
     * log entry data before rendering or outputting. The initial capacity of
     * the buffer is defined by the constant {@code MAX_BUFFER_SIZE}.
     * <p>
     * As all log message formatting int this class must happen on the
     * FX Application thread, we use a single instance to avoid GC flooding.
     */
    @SuppressWarnings("StringBufferField")
    private final StringBuilder buffer = new StringBuilder(MAX_BUFFER_SIZE);

    private final LogBuffer logBuffer;
    private boolean dark = false;
    private final LogPattern pattern = LogPattern.DEFAULT_PATTERN;
    private final TextArea details;
    private final TableView<@Nullable LogEntry> tableView;

    private final AtomicReference<@Nullable LogEntry> selectedItem = new AtomicReference<>();

    private String darkCss;
    private String lightCss;

    private boolean autoScroll = true;

    private TableColumn<LogEntry, LogEntry> createColumn(String name, LogPattern.LogPatternEntry entry, boolean fixedWidth, String... sampleTexts) {
        TableColumn<LogEntry, LogEntry> column = new TableColumn<>(name);
        column.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue()));
        if (sampleTexts.length == 0) {
            column.setPrefWidth(COLUMN_WIDTH_LARGE);
            column.setMaxWidth(COLUMN_WIDTH_MAX);
        } else {
            double w = 24 + Stream.of(sampleTexts).mapToDouble(FxLogPane::getDisplayWidth).max().orElse(200);
            if (fixedWidth) {
                column.setMinWidth(w);
                column.setMaxWidth(w);
            } else {
                column.setMinWidth(w);
                column.setPrefWidth(w);
                column.setMaxWidth(COLUMN_WIDTH_MAX);
            }
        }
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(@Nullable LogEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle(null);
                } else {
                    try {
                        StringBuilder sb = getStringBuilder();
                        entry.format(
                                sb,
                                item.time(),
                                item.logger(),
                                item.level(),
                                item.marker(),
                                item.mdc(),
                                item.location(),
                                item::message,
                                item.throwable(),
                                ConsoleCode.empty()
                        );
                        setText(sb.toString());
                    } catch (IOException e) {
                        setText("ERROR");
                    }
                }
            }
        });
        return column;
    }

    private static double getDisplayWidth(String s) {
        return new Text(s).getLayoutBounds().getWidth();
    }

    private static URL getResourceURL(Class<?> clazz, String resource) {
        URL url = clazz.getResource(resource);
        if (url == null) {
            throw new MissingResourceException("Resource not found: " + resource, clazz.getName(), resource);
        }
        return url;
    }

    /**
     * Construct a new FxLogPane instance with default buffer capacity.
     */
    public FxLogPane() {
        this(LogBuffer.DEFAULT_CAPACITY);
    }

    /**
     * Construct a new FxLogPane instance with the given buffer capacity.
     *
     * @param bufferSize the buffer size
     */
    public FxLogPane(int bufferSize) {
        this(createBuffer(bufferSize));
    }

    /**
     * Construct a new FxLogPane instance with the given buffer.
     *
     * @param logBuffer the logBuffer to use
     * @throws NullPointerException if logBuffer is null
     */
    public FxLogPane(LogBuffer logBuffer) {
        this(logBuffer, DEFAULT_TEXTS);
    }

    /**
     * Construct a new FxLogPane instance with the given buffer and texts.
     *
     * @param logBuffer the logBuffer to use
     * @param texts the texts to use for labels and buttons
     * @throws NullPointerException if logBuffer or texts is null
     */
    public FxLogPane(LogBuffer logBuffer, LogPaneTexts texts) {
        FilteredList<LogEntry> entries = new FilteredList<>(new LogEntriesObservableList(logBuffer), p -> true);

        this.logBuffer = logBuffer;
        this.darkCss = getResourceURL(getClass(), "dark.css").toExternalForm();
        this.lightCss = getResourceURL(getClass(), "light.css").toExternalForm();
        ToolBar toolBar = new ToolBar();
        this.tableView = new TableView<>(entries);
        this.details = new TextArea();

        // force loading of the stylesheets
        setDarkMode(false);

        // colorize table rows via CSS classes
        tableView.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(@Nullable LogEntry item, boolean empty) {
                super.updateItem(item, empty);
                // clear previous style classes
                getStyleClass().removeIf(s -> s.startsWith(CSS_PREFIX_LOGLEVEL));
                if (!empty && item != null) {
                    getStyleClass().addAll(CSS_CLASS_LOGTABLE_ROW, cssClassForLogEntry(item.level()));
                }
            }
        });

        entries.addListener(this::onEntries);

        double tfWidth = getDisplayWidth("X".repeat(32));

        // filtering by log level, logger name, and message content
        ComboBox<LogLevel> cbLogLevel = new ComboBox<>(FXCollections.observableArrayList(LogLevel.values()));
        TextField tfLoggerName = new TextField();
        tfLoggerName.setPrefWidth(tfWidth);
        TextField tfMessageContent = new TextField();
        tfMessageContent.setPrefWidth(tfWidth);

        Runnable updateFilter = () -> updateFilter(cbLogLevel, tfLoggerName, tfMessageContent, entries);

        cbLogLevel.valueProperty().addListener((v, o, n) -> updateFilter.run());
        tfLoggerName.textProperty().addListener((v, o, n) -> updateFilter.run());
        tfMessageContent.textProperty().addListener((v, o, n) -> updateFilter.run());

        cbLogLevel.setValue(LogLevel.INFO);
        tfLoggerName.clear();
        tfMessageContent.clear();

        // search for text
        TextField tfSearchText = new TextField();
        tfSearchText.setPrefWidth(tfWidth);
        Button btnSearchUp = new Button(texts.textSearchUp());
        Button btnSearchDown = new Button(texts.textSearchDown());

        BiConsumer<String, Boolean> searchAction = (text, up) -> searchAction(text, up, entries);

        tfSearchText.setOnKeyReleased(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                searchAction.accept(tfSearchText.getText(), event.isShiftDown());
            }
        });

        btnSearchUp.setOnAction(evt -> searchAction.accept(tfSearchText.getText(), true));
        btnSearchDown.setOnAction(evt -> searchAction.accept(tfSearchText.getText(), false));

        Button btnClear = new Button(texts.textClear());
        btnClear.setOnAction(evt -> logBuffer.clear());

        // create toolbar
        toolBar.getItems().setAll(
                new Label(texts.labelFilterLogLevel()),
                cbLogLevel,
                new Label(texts.labelFilterLogger()),
                tfLoggerName,
                new Label(texts.labelFilterLogMessage()),
                tfMessageContent,
                new Separator(Orientation.HORIZONTAL),
                new Label(texts.labelSearchText()),
                tfSearchText,
                btnSearchUp,
                btnSearchDown,
                new Separator(Orientation.HORIZONTAL),
                btnClear
        );

        // define table columns
        tableView.setEditable(false);
        //noinspection unchecked
        tableView.getColumns().setAll(
                createColumn(texts.headerTimeColumn(), new LogPattern.DateEntry("HH:mm:ss,SSS"), true, "88:88:88,888"),
                createColumn(texts.headerLevelColumn(), new LogPattern.LevelEntry(0, 0, false), true, Arrays.stream(LogLevel.values()).map(Object::toString).toArray(String[]::new)),
                createColumn(texts.headerLoggerColumn(), new LogPattern.LoggerEntry(0, 0, true, 0), false, "X".repeat(40)),
                createColumn(texts.headerMessageColumn(), new LogPattern.MessageEntry(0, 0, false), false)
        );
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        // disable autoscroll if the selection is not empty, enable when selection is cleared while scrolled to bottom
        tableView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection == null) {
                details.clear();
                autoScroll = autoScroll || isScrolledToBottom();
            } else {
                selectedItem.set(newSelection);
                autoScroll = false;
                details.setText(formatLogEntry(newSelection));
            }
        });

        //  ESC clears the selection
        tableView.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                clearSelection();
                autoScroll = true;
                event.consume();
            }
        });

        // automatically enable/disable autoscroll when user scrolls
        tableView.addEventFilter(ScrollEvent.ANY, this::onScrollEvent);

        SplitPane splitPane = new SplitPane(tableView, details);
        splitPane.setOrientation(Orientation.VERTICAL);

        setTop(toolBar);
        setCenter(splitPane);
    }

    /**
     * Sets the stylesheets for light mode and dark mode for this pane.
     *
     * @param lightCss the path or URL to the stylesheet used for light mode
     * @param darkCss the path or URL to the stylesheet used for dark mode
     */
    public void setStyleSheets(String lightCss, String darkCss) {
        this.lightCss = lightCss;
        this.darkCss = darkCss;
        setDarkMode(dark); // force update of stylesheets
    }

    private String formatLogEntry(LogEntry entry) {
        try {
            StringBuilder sb = getStringBuilder();
            pattern.formatLogEntry(
                    sb,
                    entry.time(),
                    entry.logger(),
                    entry.level(),
                    entry.marker(),
                    entry.mdc(),
                    entry::location,
                    entry::message,
                    entry.throwable(),
                    ConsoleCode.empty()
            );
            return sb.toString();
        } catch (IOException e) {
            // this should never happen with a StringBuilder
            throw new UncheckedIOException(e);
        }
    }

    private StringBuilder getStringBuilder() {
        assert Platform.isFxApplicationThread();

        if (buffer.capacity() > MAX_BUFFER_SIZE) {
            buffer.trimToSize();
        }
        buffer.setLength(0);

        return buffer;
    }

    private static final Map<LogLevel, String> CSS_CLASS_BY_LOG_LEVEL = Arrays.stream(LogLevel.values())
            .collect(Collectors.toMap(
                    Function.identity(),
                    level -> CSS_PREFIX_LOGLEVEL + level.name().toLowerCase(Locale.ROOT)
            ));

    /**
     * Set dark mode stylesheet for this pane.
     * @param dark true to use dark.css, false to use light.css
     */
    @Override
    public void setDarkMode(boolean dark) {
        getStylesheets().setAll(dark ? darkCss : lightCss);
        this.dark = dark;
    }

    private static String cssClassForLogEntry(LogLevel level) {
        return CSS_CLASS_BY_LOG_LEVEL.get(level);
    }

    /**
     * Searches within the provided list of log entries for a log entry containing the specified text.
     * The search direction is determined by the `up` parameter and wraps around the list if necessary.
     *
     * @param text    the text to search for in the log entries
     * @param up      a boolean indicating the search direction; true for upward search, false for downward search
     * @param entries the list of log entries to search through; must be a FilteredList of LogEntry objects
     */
    private void searchAction(String text, boolean up, FilteredList<LogEntry> entries) {
        String lowercaseText = text.toLowerCase(Locale.ROOT);
        int step = up ? -1 : 1;
        LogEntry current = selectedItem.get();
        List<LogEntry> items = List.copyOf(entries);
        int n = items.size();
        int pos = current != null ? Math.max(0, items.indexOf(current)) : 0;
        for (int i = 0; i < n; i++) {
            int j = Math.floorMod(pos + step * i, n);
            LogEntry logEntry = items.get(j);
            String message = logEntry.message();
            if ((i != 0 || current == null) // skip the current entry if selected
                    && message != null
                    && message.toLowerCase(Locale.ROOT).contains(lowercaseText)) {
                selectLogEntry(logEntry);
                break;
            }
        }
    }

    /**
     * Updates the filter applied to a list of log entries based on the provided log level, logger name, and message content.
     *
     * @param cbLogLevel     the ComboBox component used to select the log level filter
     * @param tfLoggerName   the TextField used to input the logger name filter
     * @param tfMessageContent the TextField used to input the message content filter
     * @param entries        the FilteredList containing the log entries to be filtered
     */
    private static void updateFilter(ComboBox<LogLevel> cbLogLevel, TextField tfLoggerName, TextField tfMessageContent, FilteredList<LogEntry> entries) {
        LogLevel level = cbLogLevel.getSelectionModel().getSelectedItem();

        LogFilter filter = new LogLevelFilter("Filter level", level);

        String loggerText = tfLoggerName.getText().toLowerCase(Locale.ROOT).strip();
        if (!loggerText.isEmpty()) {
            filter = filter.andThen(new LoggerNameFilter("loggerName", name -> name.toLowerCase(Locale.ROOT).contains(loggerText)));
        }

        String messageContent = tfMessageContent.getText();
        if (!messageContent.isEmpty()) {
            filter = filter.andThen(new MessageTextFilter("messageContent", text -> text.contains(messageContent)));
        }

        entries.setPredicate(LogEntryFilter.forFilter(filter));
    }

    /**
     * Selects a specific log entry in the TableView, scrolls to it, and ensures
     * the selection is updated appropriately. This method must be executed on the
     * JavaFX Application thread.
     *
     * @param logEntry the {@link LogEntry} to select and scroll to
     */
    private void selectLogEntry(LogEntry logEntry) {
        checkApplicationThread();
        tableView.getSelectionModel().select(logEntry);
        tableView.scrollTo(logEntry);
    }

    /**
     * Ensures that the method invoking this function is being executed on the JavaFX Application Thread.
     * If the current thread is not the JavaFX Application Thread, an {@code IllegalStateException} is thrown.
     * <p>
     * This method is crucial for operations that manipulate UI components or require execution exclusively
     * on the JavaFX Application Thread to maintain thread safety.
     *
     * @throws IllegalStateException if the current thread is not the JavaFX Application Thread
     */
    private static void checkApplicationThread() {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("not on FX Application Thread");
        }
    }

    /**
     * Handles the scroll event for the log pane. Updates the {@code autoScroll}
     * property based on the current scroll position and selection state.
     *
     * @param evt the {@link ScrollEvent} representing the scroll action
     */
    private void onScrollEvent(ScrollEvent evt) {
        checkApplicationThread();
        if (autoScroll) {
            // disable autoscroll when manually scrolling
            autoScroll = false;
        } else {
            // enable autoscroll when scrolling ends at end of input and selection is empty
            autoScroll = isSelectionEmpty() && isScrolledToBottom();
        }
    }

    private Optional<ScrollBar> getScrollBar(Orientation orientation) {
        for (Node node : tableView.lookupAll(".scroll-bar")) {
            if (node instanceof ScrollBar sb && sb.getOrientation() == orientation) {
                return Optional.of(sb);
            }
        }
        return Optional.empty();
    }

    private boolean isSelectionEmpty() {
        checkApplicationThread();
        return selectedItem == null;
    }

    private void clearSelection() {
        checkApplicationThread();
        tableView.getSelectionModel().clearSelection();
        selectedItem.set(null);
    }

    private void onEntries(ListChangeListener.Change<? extends LogEntry> change) {
        checkApplicationThread();
        if (autoScroll) {
            // scroll to bottom
            Platform.runLater(() -> {
                tableView.scrollTo(tableView.getItems().size() - 1);
                autoScroll = true;
            });
        }
        if (!isSelectionEmpty()) {
            // update selection
            Platform.runLater(() -> tableView.getSelectionModel().select(selectedItem.get()));
        }
    }

    private boolean isScrolledToBottom() {
        checkApplicationThread();
        return getScrollBar(Orientation.VERTICAL).map(sb -> {
                    double max = sb.getMax();
                    double current = sb.getValue();
                    double step = max / (1.0 + tableView.getItems().size());
                    return current >= max - step;
                })
                .orElse(true);
    }

    /**
     * Creates a LogBuffer with the given buffer size and adds it to the global log entry handler.
     *
     * @param bufferSize the size of the buffer
     * @return the created LogBuffer
     */
    private static LogBuffer createBuffer(int bufferSize) {
        LogBuffer buffer = new LogBuffer("Log Buffer", bufferSize);
        LoggerNamePrefixFilter filter = new LoggerNamePrefixFilter("filter");
        filter.setLevel("", LogLevel.TRACE);
        filter.setLevel("javafx", LogLevel.INFO);
        buffer.setFilter(filter);
        SLB4J.getDispatcher().addLogHandler(buffer);
        return buffer;
    }


    /**
     * Retrieves the LogBuffer associated with this FxLogPane instance.
     *
     * @return the LogBuffer object
     */
    public LogBuffer getLogBuffer() {
        return logBuffer;
    }
}
