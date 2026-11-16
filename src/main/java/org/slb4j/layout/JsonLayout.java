package org.slb4j.layout;

import org.jspecify.annotations.Nullable;
import org.slb4j.ConsoleCode;
import org.slb4j.Location;
import org.slb4j.LogLevel;
import org.slb4j.LogLayout;
import org.slb4j.MDC;
import org.slb4j.support.Util;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

/**
 * The JsonLayout class implements the {@link LogLayout} interface and formats log entries into a JSON format.
 */
public final class JsonLayout implements LogLayout {

    /**
     * Hard upper limit for {@code maxStringLength}.
     */
    public static final int HARD_MAX_STRING_LENGTH = 1_048_576;
    /**
     * Hard lower limit for {@code maxStringLength}.
     */
    public static final int HARD_MIN_STRING_LENGTH = 0;

    /**
     * Maximum length for {@code truncatedStringSuffix}.
     */
    public static final int MAX_TRUNCATED_STRING_SUFFIX_LENGTH = 256;

    private final boolean propertiesEnabled;
    private final boolean locationInfoEnabled;
    private final boolean stacktraceEnabled;
    private final int maxStringLength;
    private final String truncatedStringSuffix;

    /**
     * Constructs an instance of the JsonLayout class with the specified configuration parameters.
     *
     * @param propertiesEnabled     whether context information (MDC) should be included in the JSON log output.
     * @param locationInfoEnabled   whether location information (source file and line number) should be included in
     *                              the JSON log output.
     * @param stacktraceEnabled     whether stack trace details should be included in the JSON log output if a
     *                              throwable is present.
     * @param maxStringLength       the maximum length for string fields in the JSON log output; strings exceeding
     *                              this length will be truncated. The value must be in the range
     *                              [{@link HARD_MIN_STRING_LENGTH}, {@link HARD_MAX_STRING_LENGTH}].
     *                              If {@code maxStringLength} is 0, only {@code truncatedStringSuffix will be
     *                              appended for non-empty values.
     * @param truncatedStringSuffix the suffix appended to truncated strings to indicate truncation. Must not be longer
     *                              than {@link MAX_TRUNCATED_STRING_SUFFIX_LENGTH} characters.
     */
    public JsonLayout(boolean propertiesEnabled, boolean locationInfoEnabled, boolean stacktraceEnabled, int maxStringLength, String truncatedStringSuffix) {
        this.propertiesEnabled = propertiesEnabled;
        this.locationInfoEnabled = locationInfoEnabled;
        this.stacktraceEnabled = stacktraceEnabled;
        this.maxStringLength = maxStringLength;
        this.truncatedStringSuffix = truncatedStringSuffix;

        if (maxStringLength < HARD_MIN_STRING_LENGTH || maxStringLength > HARD_MAX_STRING_LENGTH) {
            throw new IllegalArgumentException("maxStringLength must be in the range [" + HARD_MIN_STRING_LENGTH + ", " + HARD_MAX_STRING_LENGTH + "]");
        }
        if (truncatedStringSuffix.length() > MAX_TRUNCATED_STRING_SUFFIX_LENGTH) {
            throw new IllegalArgumentException("truncatedStringSuffix must be no longer than " + MAX_TRUNCATED_STRING_SUFFIX_LENGTH + " characters");
        }
    }

    @Override
    public String getType() {
        return "JsonLayout";
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JsonLayout that = (JsonLayout) o;
        return propertiesEnabled == that.propertiesEnabled && locationInfoEnabled == that.locationInfoEnabled && stacktraceEnabled == that.stacktraceEnabled && maxStringLength == that.maxStringLength && truncatedStringSuffix.equals(that.truncatedStringSuffix);
    }

    @Override
    public int hashCode() {
        return Objects.hash(propertiesEnabled, locationInfoEnabled, stacktraceEnabled, maxStringLength, truncatedStringSuffix);
    }

    @Override
    public void formatLogEntry(Appendable app, long timestamp, String loggerName, LogLevel lvl, @Nullable String mrk, @Nullable MDC mdc, @Nullable Location loc, @Nullable CharSequence msg, @Nullable Throwable t, ConsoleCode consoleCodes) throws IOException {
        app.append("{\"thread\":\"");
        appendJsonEscaped(app, Thread.currentThread().getName());
        app.append("\",\"level\":\"").append(lvl.name());
        app.append("\",\"loggerName\":\"");
        appendJsonEscaped(app, loggerName);
        app.append("\",\"message\":\"");
        appendJsonEscaped(app, msg);
        app.append("\", \"endOfBatch\":false");
        app.append(",\"instant\":{\"epochSecond\":").append(Long.toString(timestamp/1000));
        app.append(",\"nanoOfSecond\":\"").append(Long.toString(timestamp%1000)).append("000000");
        app.append("\"}");

        appendMarkerMap(app, mrk);
        appendContextMap(app, mdc);
        appendLocation(app, loc);
        appendThrowable(app, t);

        app.append("}\n");
    }

    /**
     * Appends the JSON marker field for the supplied marker value.
     * If the marker is null, nothing is appended. When present, the marker value is JSON-escaped
     * before being written.
     *
     * @param app the appendable to which the marker JSON fragment will be written
     * @param mrk the marker value to append; may be null
     * @throws IOException if an I/O error occurs while writing to the appendable
     */
    private void appendMarkerMap(Appendable app, @Nullable String mrk) throws IOException {
        if (mrk != null) {
            app.append(",\"marker\":\"");
            appendJsonEscaped(app, mrk);
            app.append("\"");
        }
    }

    /**
     * Appends the JSON context map field for the supplied mapping diagnostic context when property output is enabled.
     * If property output is disabled, the supplied context is null, or the context map is empty, nothing is appended.
     * Context map keys and values are JSON-escaped before being written.
     *
     * @param app the appendable to which the context map JSON fragment will be written
     * @param mdc the mapping diagnostic context to append; may be null
     * @throws IOException if an I/O error occurs while writing to the appendable
     */
    private void appendContextMap(Appendable app, @Nullable MDC mdc) throws IOException {
        if (propertiesEnabled && mdc != null) {
            Map<String, String> contextMap = mdc.get();
            if (!contextMap.isEmpty()) {
                app.append(",\"contextMap\":{");
                boolean first = true;
                for (Map.Entry<String, String> entry : contextMap.entrySet()) {
                    if (!first) {
                        app.append(",");
                    }
                    app.append("\"");
                    appendJsonEscaped(app, entry.getKey());
                    app.append("\":\"");
                    appendJsonEscaped(app, entry.getValue());
                    app.append("\"");
                    first = false;
                }
                app.append("}");
            }
        }
    }

    /**
     * Appends the JSON location field for the supplied source location when location output is enabled.
     * If location output is disabled or the supplied location is null, nothing is appended. The appended
     * location object includes the class name, method name, file name, and line number, with string values
     * JSON-escaped before being written.
     *
     * @param app the appendable to which the location JSON fragment will be written
     * @param loc the source location to append; may be null
     * @throws IOException if an I/O error occurs while writing to the appendable
     */
    private void appendLocation(Appendable app, @Nullable Location loc) throws IOException {
        if (locationInfoEnabled && loc != null) {
            app.append(",\"location\":{");
            app.append("\"class\":\"");
            appendJsonEscaped(app, loc.getClassName());
            app.append("\",\"method\":\"");
            appendJsonEscaped(app, loc.getMethodName());
            app.append("\",\"file\":\"");
            appendJsonEscaped(app, loc.getFileName());
            app.append("\",\"line\":");
            app.append(String.valueOf(loc.getLineNumber()));
            app.append("}");
        }
    }

    /**
     * Appends JSON fields describing the supplied throwable to the given appendable.
     * If the throwable is null, nothing is appended. When present, the throwable output includes
     * the localized message, message, and fully qualified class name, with values JSON-escaped.
     * If stack trace output is enabled, the throwable stack trace is also appended as an escaped
     * JSON string.
     *
     * @param app the appendable to which the throwable JSON fragment will be written
     * @param t the throwable to append; may be null
     * @throws IOException if an I/O error occurs while writing to the appendable
     */
    private void appendThrowable(Appendable app, @Nullable Throwable t) throws IOException {
        if (t != null) {
            app.append(",\"thrown\":{\"localizedMessage\":\"");
            appendJsonEscaped(app, t.getLocalizedMessage());
            app.append("\",\"message\":\"");
            appendJsonEscaped(app, t.getMessage());
            app.append("\",\"name\":\"");
            appendJsonEscaped(app, t.getClass().getName());
            app.append("\"");

            if (stacktraceEnabled) {
                app.append(",\"stacktrace\":\"");
                StringBuilder sb = new StringBuilder();
                Util.appendStackTrace(sb, t);
                appendJsonEscaped(app, sb.toString());
                app.append("\"");
            }

            app.append("}");
        }
    }

    /**
     * Appends a JSON-escaped representation of the given string to the specified {@link Appendable}.
     * Special characters in the string are escaped to ensure it is safe for inclusion in JSON.
     * If the input string is null, the text "null" is appended to the output.
     * Strings exceeding the maximum allowed length are truncated and suffixed accordingly.
     *
     * @param app the {@link Appendable} to which the JSON-escaped string will be appended
     * @param s the input string to be JSON-escaped; may be null
     * @throws IOException if an I/O error occurs while appending to the appendable
     */
    private void appendJsonEscaped(Appendable app, @Nullable CharSequence s) throws IOException {
        if (s == null) {
            app.append("null");
            return;
        }

        // determine length, truncating if necessary, and handle a possible trainling incomplete surrogate pair
        int length = Math.clamp(maxStringLength, 0, s.length());
        if (length > 0 && Character.isHighSurrogate(s.charAt(length - 1))) {
            length--;
        }

        appendJsonEscaped(app, s, length);

        if (length < s.length()) {
            appendJsonEscaped(app, truncatedStringSuffix, truncatedStringSuffix.length());
        }
    }

    /**
     * Appends a JSON-escaped representation of the supplied character sequence to the given appendable,
     * limiting the number of source characters processed to the specified maximum length. If the
     * character sequence is null, appends the literal {@code null}. Characters that require JSON
     * escaping, including quotation marks, backslashes, slashes, control characters, and standard
     * escape sequences, are written in escaped form.
     *
     * @param app the appendable to which the escaped JSON content is written
     * @param s the character sequence to escape; may be null
     * @param maxLength the maximum number of source characters to process, clamped to the range from zero to the input length
     * @throws IOException if an I/O error occurs while writing to the appendable
     */
    private void appendJsonEscaped(Appendable app, @Nullable CharSequence s, int maxLength) throws IOException {
        if (s == null) {
            app.append("null");
            return;
        }

        int length = Math.clamp(maxLength, 0, s.length());
        for (int i = 0; i < length; i++) {
            char c = s.charAt(i);

            if (Character.isHighSurrogate(c)) {
                if (i + 1 < length && Character.isLowSurrogate(s.charAt(i + 1))) {
                    // if c begins a valid surrogate pair and the next character is a low surrogate, both are appended unchanged.
                    app.append(c).append(s.charAt(++i));
                } else {
                    // if no matching low surrogate follows, it appends U+FFFD (�), the Unicode replacement character.
                    app.append('\uFFFD');
                }
            } else if (Character.isLowSurrogate(c)) {
                // if c is an unpaired low surrogate, the Unicode replacement character U+FFFD (�) is appended.
                app.append('\uFFFD');
            } else {
                // JSON-escape characters
                switch (c) {
                    case '"' -> app.append("\\\"");
                    case '\\' -> app.append("\\\\");
                    case '/' -> app.append("\\/");
                    case '\b' -> app.append("\\b");
                    case '\f' -> app.append("\\f");
                    case '\n' -> app.append("\\n");
                    case '\r' -> app.append("\\r");
                    case '\t' -> app.append("\\t");
                    default -> {
                        if (c < 32) {
                            // append control characters as \\uxxxx
                            app.append(String.format("\\u%04x", (int) c));
                        } else {
                            app.append(c);
                        }
                    }
                }
            }
        }
    }
}
