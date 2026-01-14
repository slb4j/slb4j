package org.slb4j.ext;

/**
 * The LogPaneTexts interface defines a set of methods for retrieving localized or customized
 * text values used in implementations of the {@link LogPane} interface.
 * <p>
 * The interface provides a convenient way to provided localized strings for the user interface.
 */
public interface LogPaneTexts {
    /**
     * Retrieves the localized or customized text for the "Search Up" action
     * used in the context of a log pane interface.
     *
     * @return a string representing the text for the "Search Up" action.
     */
    String textSearchUp();

    /**
     * Retrieves the text representing the "Search Down" action in the log pane interface.
     * This text is typically used as a label or tooltip for the corresponding UI component.
     *
     * @return a localized or customized string representing the "Search Down" action.
     */
    String textSearchDown();

    /**
     * Retrieves the text used to represent the action of clearing
     * the log buffer shown in a log pane..
     *
     * @return a string representing the "clear" action text.
     */
    String textClear();

    /**
     * Retrieves the label for the user interface component that represents the
     * log level filter in a log pane.
     *
     * @return a string containing the label text for the log level filter.
     */
    String labelFilterLogLevel();

    /**
     * Retrieves the label text used for the logger filter in the user interface.
     *
     * @return a string representing the label for the logger filter.
     */
    String labelFilterLogger();

    /**
     * Retrieves the label text used for the log message filter in the user interface.
     *
     * @return a string representing the label for the log message filter.
     */
    String labelFilterLogMessage();

    /**
     * Retrieves the label text for the search input field in the log pane.
     *
     * @return a string representing the label text for the search input field.
     */
    String labelSearchText();

    /**
     * Retrieves the header text for the time column of the log display.
     *
     * @return the header text for the time column as a string
     */
    String headerTimeColumn();

    /**
     * Retrieves the localized or customized text used as the header label
     * for the log level column in the log pane.
     *
     * @return a string representing the header label for the log level column.
     */
    String headerLevelColumn();

    /**
     * Retrieves the text for the logger column in a log display.
     *
     * @return a string representing the header text for the logger column.
     */
    String headerLoggerColumn();

    /**
     * Retrieves the text for the log message column in the log pane's user interface.
     *
     * @return the header text for the message column as a String.
     */
    String headerMessageColumn();

    /**
     * Creates an instance of LogPaneTexts with the specified text values.
     *
     * @param textSearchUp the text associated with the "Search Up" action.
     * @param textSearchDown the text associated with the "Search Down" action.
     * @param textClear the text associated with the "Clear" action for clearing the log view.
     * @param textLogLevel the label text for the log level filter in the user interface.
     * @param textLogger the label text for the logger filter in the user interface.
     * @param textLogText the label text for the log message filter in the user interface.
     * @param textSearch the label text for the search input field in the log pane.
     * @param textColumnTime the text used as the header for the time column in the log display.
     * @param textColumnLevel the text used as the header for the log level column in the log display.
     * @param textColumnLogger the text used as the header for the logger column in the log display.
     * @param textColumnMessage the text used as the header for the log message column in the log display.
     * @return a new instance of LogPaneTexts containing the provided text values.
     */
    static LogPaneTexts of(
            String textSearchUp,
            String textSearchDown,
            String textClear,
            String textLogLevel,
            String textLogger,
            String textLogText,
            String textSearch,
            String textColumnTime,
            String textColumnLevel,
            String textColumnLogger,
            String textColumnMessage
    ) {
        return new LogPaneTextsRecord(textSearchUp, textSearchDown, textClear, textLogLevel, textLogger, textLogText, textSearch, textColumnTime, textColumnLevel, textColumnLogger, textColumnMessage);
    }
}

record LogPaneTextsRecord(
        String textSearchUp,
        String textSearchDown,
        String textClear,
        String labelFilterLogLevel,
        String labelFilterLogger,
        String labelFilterLogMessage,
        String labelSearchText,
        String headerTimeColumn,
        String headerLevelColumn,
        String headerLoggerColumn,
        String headerMessageColumn
) implements LogPaneTexts {
}
