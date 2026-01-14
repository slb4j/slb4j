package org.slb4j.ext.swing;

import org.slb4j.ext.LogBuffer;
import org.slb4j.ext.LogWindow;
import org.slb4j.ext.LogPaneTexts;

import javax.swing.JFrame;
import javax.swing.WindowConstants;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.util.Objects;

/**
 * The SwingLogWindow class represents a Swing JFrame that displays log entries in a table view.
 */
public class SwingLogWindow extends JFrame implements LogWindow {

    private final LogBuffer logBuffer;

    /**
     * Create a new SwingLogWindow instance with a new {@link LogBuffer} using the default capacity.
     *
     * @param title the window title
     */
    public SwingLogWindow(String title) {
        this(title, LogBuffer.DEFAULT_CAPACITY);
    }

    /**
     * Constructs a new instance of {@code SwingLogWindow} with the specified maximum number of lines.
     *
     * @param title the window title
     * @param maxLines the maximum number of lines to display in the log window
     */
    public SwingLogWindow(String title, int maxLines) {
        this(title, createBuffer(maxLines));
    }

    /**
     * Constructs a new instance of {@code SwingLogWindow} using the provided {@link LogBuffer}.
     *
     * @param title     the window title
     * @param logBuffer the LogBuffer to use
     */
    public SwingLogWindow(String title, LogBuffer logBuffer) {
        this(title, logBuffer, SwingLogPane.DEFAULT_TEXTS);
    }

    private static LogBuffer createBuffer(int bufferSize) {
        LogBuffer buffer = new LogBuffer("Log Buffer", bufferSize);
        org.slb4j.SLB4J.getDispatcher().addLogHandler(buffer);
        return buffer;
    }

    /**
     * Constructs a new instance of {@code SwingLogWindow} using the provided {@link LogBuffer} and {@link LogPaneTexts}.
     *
     * @param title     the window title
     * @param logBuffer the LogBuffer to use
     * @param texts     the texts to use
     */
    public SwingLogWindow(String title, LogBuffer logBuffer, LogPaneTexts texts) {
        super(title);
        this.logBuffer = Objects.requireNonNull(logBuffer);

        SwingLogPane logPane = new SwingLogPane(this.logBuffer, texts);
        setContentPane(logPane);

        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int width = (int) (screenSize.width * 0.8);
        int height = (int) (screenSize.height * 0.5);
        setSize(width, height);
        setLocation((screenSize.width - width) / 2, screenSize.height - height);

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    }

    /**
     * Retrieves the LogBuffer associated with this SwingLogWindow.
     *
     * @return the LogBuffer instance used by this SwingLogWindow
     */
    public LogBuffer getLogBuffer() {
        return logBuffer;
    }

    /**
     * Sets the dark mode for the log window.
     *
     * @param darkMode true to enable dark mode, false to disable it
     */
    public void setDarkMode(boolean darkMode) {
        if (getContentPane() instanceof SwingLogPane pane) {
            pane.setDarkMode(darkMode);
        }
    }
}
