package org.slb4j.layout;

import org.slb4j.LogLayout;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * A builder class for creating instances of {@link PatternLayout} using Log4J2 options.
 */
public class PatternLayoutBuilder extends LayoutBuilder {

    private static final String TYPE = "type";
    private static final String CHARSET = "charset";
    private static final String PATTERN = "pattern";
    private static final String ALWAYS_WRITE_EXCEPTIONS = "alwaysWriteExceptions";
    private static final String DISABLE_ANSI = "disableAnsi";
    private static final String NO_CONSOLE_NO_ANSI = "noConsoleNoAnsi";

    private static final Map<String, LayoutAtribute> ATTRIBUTES = Map.of(
            TYPE, new LayoutAtribute(TYPE, true, null, null),
            CHARSET, new LayoutAtribute(CHARSET, false, "UTF-8", Charset::forName),
            PATTERN, new LayoutAtribute(PATTERN, true, PatternLayout.DEFAULT_PATTERN_STRING, String::valueOf),
            ALWAYS_WRITE_EXCEPTIONS, new LayoutAtribute(ALWAYS_WRITE_EXCEPTIONS, true, "true", Boolean::parseBoolean),
            DISABLE_ANSI, new LayoutAtribute(DISABLE_ANSI, true, "false", Boolean::parseBoolean),
            NO_CONSOLE_NO_ANSI, new LayoutAtribute(NO_CONSOLE_NO_ANSI, false, "false", Boolean::parseBoolean)
    );

    /**
     * Constructs a new instance of {@code PatternLayoutBuilder}.
     *
     * @param name the name of the layout to be built
     */
    PatternLayoutBuilder(String name) {
        super(name, ATTRIBUTES);
    }

    public LogLayout build() {
        String pattern = (String) getValue(PATTERN);
        boolean alwaysWriteExceptions = (boolean) getValue(ALWAYS_WRITE_EXCEPTIONS);
        boolean disableAnsi = (boolean) getValue(DISABLE_ANSI);

        List<PatternLayout.LogPatternEntry> logPatternEntries = new ArrayList<>(Arrays.asList(PatternLayout.parseLog4jPatternString(pattern)));

        if (disableAnsi) {
            logPatternEntries.removeIf(entry -> entry instanceof PatternLayout.ColorStartEntry || entry instanceof PatternLayout.ColorEndEntry);
        }

        if (alwaysWriteExceptions && logPatternEntries.stream().noneMatch(entry -> entry instanceof PatternLayout.ExceptionEntry)) {
            PatternLayout.ExceptionEntry exceptionEntry = new PatternLayout.ExceptionEntry(0, Integer.MAX_VALUE, true);
            int insertOffset =  !logPatternEntries.isEmpty() && logPatternEntries.getLast() instanceof PatternLayout.NewlineEntry ? -1 : 0;
            logPatternEntries.add(logPatternEntries.size() + insertOffset, exceptionEntry);
        }

        return new PatternLayout(pattern, logPatternEntries.toArray(PatternLayout.LogPatternEntry[]::new));
    }
}
