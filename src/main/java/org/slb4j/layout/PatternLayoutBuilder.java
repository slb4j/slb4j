package org.slb4j.layout;

import org.slb4j.LogLayout;

import java.nio.charset.Charset;
import java.util.Map;

/**
 * A builder class for creating instances of {@link PatternLayout} using Log4J2 options.
 */
public final class PatternLayoutBuilder extends LayoutBuilder {

    static final String TYPE = "type";
    static final String CHARSET = "charset";
    static final String PATTERN = "pattern";
    static final String ALWAYS_WRITE_EXCEPTIONS = "alwaysWriteExceptions";
    static final String DISABLE_ANSI = "disableAnsi";
    static final String NO_CONSOLE_NO_ANSI = "noConsoleNoAnsi";

    static final Map<String, LayoutAtribute> ATTRIBUTES = Map.of(
            TYPE, new LayoutAtribute(TYPE, true, null, null),
            CHARSET, new LayoutAtribute(CHARSET, false, "UTF-8", Charset::forName),
            PATTERN, new LayoutAtribute(PATTERN, true, PatternLayout.DEFAULT_PATTERN_STRING, String::valueOf),
            ALWAYS_WRITE_EXCEPTIONS, new LayoutAtribute(ALWAYS_WRITE_EXCEPTIONS, true, "true", Boolean::parseBoolean),
            DISABLE_ANSI, new LayoutAtribute(DISABLE_ANSI, true, "false", Boolean::parseBoolean),
            NO_CONSOLE_NO_ANSI, new LayoutAtribute(NO_CONSOLE_NO_ANSI, true, "false", Boolean::parseBoolean)
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
        return PatternLayout.build(this);
    }
}
