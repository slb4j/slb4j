package org.slb4j.ext.fx;

import org.slb4j.ext.LogBuffer;
import org.slb4j.ext.LogWindow;
import org.slb4j.ext.LogPaneTexts;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 * The FxLogWindow class represents a JavaFX window that displays log entries in a table view.
 * It extends the Stage class.
 */
public class FxLogWindow extends Stage implements LogWindow {

    private final LogBuffer logBuffer;

    /**
     * Create a new FxLogWindow instance with a new {@link LogBuffer} using the default capacity;
     *
     * @param title the window title
     */
    public FxLogWindow(String title) {
        this(title, new LogBuffer());
    }

    /**
     * Constructs a new instance of {@code FxLogWindow} with the specified maximum number of lines.
     *
     * @param title the window title
     * @param maxLines the maximum number of lines to display in the log window
     */
    public FxLogWindow(String title, int maxLines) {
        this(title, new LogBuffer(FxLogWindow.class.getSimpleName() + "Log Buffer", maxLines));
    }

    /**
     * Constructs a new instance of {@code FxLogWindow} using the provided {@link LogBuffer}.
     *
     * @param title the window title
     * @param logBuffer the LogBuffer to use
     */
    public FxLogWindow(String title, LogBuffer logBuffer) {
        this(title, logBuffer, FxLogPane.DEFAULT_TEXTS);
    }

    /**
     * Constructs a new instance of {@code FxLogWindow} using the provided {@link LogBuffer} and {@link LogPaneTexts}.
     *
     * @param title     the window title
     * @param logBuffer the LogBuffer to use
     * @param texts     the texts to use
     */
    public FxLogWindow(String title, LogBuffer logBuffer, LogPaneTexts texts) {
        this.logBuffer = logBuffer;
        FxLogPane logPane = new FxLogPane(this.logBuffer, texts);
        Scene scene = new Scene(logPane);
        setScene(scene);
        setTitle(title);

        Rectangle2D primaryScreenBounds = Screen.getPrimary().getVisualBounds();
        double width = primaryScreenBounds.getWidth() * 0.8;
        double height = primaryScreenBounds.getHeight() * 0.5;
        setWidth(width);
        setHeight(height);
        setX((primaryScreenBounds.getWidth() - width) / 2 + primaryScreenBounds.getMinX());
        setY(primaryScreenBounds.getMaxY() - height);
    }

    /**
     * Retrieves the LogBuffer associated with this FxLogWindow.
     *
     * @return the LogBuffer instance used by this FxLogWindow
     */
    public LogBuffer getLogBuffer() {
        return logBuffer;
    }
}
