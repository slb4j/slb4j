package org.slb4j.layout;

import org.slb4j.LogLayout;

import java.nio.charset.Charset;
import java.util.Map;

/**
 * A builder class for creating instances of {@link JsonLayout} using Log4J2 options.
 */
public class JsonLayoutBuilder extends LayoutBuilder {

    private static final String CHARSET = "charset";
    private static final String PROPERTIES = "properties";
    private static final String LOCATION_INFO_ENABLED = "locationInfoEnabled";
    private static final String STACKTRACE_ENABLED = "stackTraceEnabled";
    private static final String EVENT_TEMPLATE = "eventTemplate";
    private static final String EVENT_TEMPLATEURI = "eventTemplateUri";
    private static final String EVENT_TEMPLATE_ROOT_OBJECT_KEY = "eventTemplateRootObjectKey";
    private static final String STACKTRACE_ELEMENT_TEMPLATE = "stackTraceElementTemplate";
    private static final String STACKTRACE_ELEMENT_TEMPLATE_URI = "stackTraceElementTemplateUri";
    private static final String EVENT_DELIMITER = "eventDelimiter";
    private static final String NULL_EVENT_DELIMITER_ENABLED = "nullEventDelimiterEnabled";
    private static final String MAX_STRING_LENGTH = "maxStringLength";
    private static final String TRUNCATED_STRING_SUFFIX = "truncatedStringSuffix";
    private static final String RECYCLER_FACTORY = "recyclerFactory";

    private static final Map<String, LayoutAtribute> ATTRIBUTES = Map.ofEntries(
            Map.entry(CHARSET, new LayoutAtribute(CHARSET, false, "UTF-8", Charset::forName)),
            Map.entry(PROPERTIES, new LayoutAtribute(PROPERTIES, true, "true", Boolean::parseBoolean)),
            Map.entry(LOCATION_INFO_ENABLED, new LayoutAtribute(LOCATION_INFO_ENABLED, true, "false", Boolean::parseBoolean)),
            Map.entry(STACKTRACE_ENABLED, new LayoutAtribute(STACKTRACE_ENABLED, true, "true", Boolean::parseBoolean)),
            Map.entry(EVENT_TEMPLATE, new LayoutAtribute(EVENT_TEMPLATE, false, null, Boolean::parseBoolean)),
            Map.entry(EVENT_TEMPLATEURI, new LayoutAtribute(EVENT_TEMPLATEURI, false, null, Boolean::parseBoolean)),
            Map.entry(EVENT_TEMPLATE_ROOT_OBJECT_KEY, new LayoutAtribute(EVENT_TEMPLATE_ROOT_OBJECT_KEY, false, null, Boolean::parseBoolean)),
            Map.entry(STACKTRACE_ELEMENT_TEMPLATE, new LayoutAtribute(STACKTRACE_ELEMENT_TEMPLATE, false, null, Boolean::parseBoolean)),
            Map.entry(STACKTRACE_ELEMENT_TEMPLATE_URI, new LayoutAtribute(STACKTRACE_ELEMENT_TEMPLATE_URI, false, null, Boolean::parseBoolean)),
            Map.entry(EVENT_DELIMITER, new LayoutAtribute(EVENT_DELIMITER, false, null, Boolean::parseBoolean)),
            Map.entry(NULL_EVENT_DELIMITER_ENABLED, new LayoutAtribute(NULL_EVENT_DELIMITER_ENABLED, false, null, Boolean::parseBoolean)),
            Map.entry(MAX_STRING_LENGTH, new LayoutAtribute(MAX_STRING_LENGTH, true, "16384", Integer::parseInt)),
            Map.entry(TRUNCATED_STRING_SUFFIX, new LayoutAtribute(TRUNCATED_STRING_SUFFIX, true, "…", String::valueOf)),
            Map.entry(RECYCLER_FACTORY, new LayoutAtribute(RECYCLER_FACTORY, false, null, Boolean::parseBoolean))
    );

    /**
     * Constructs a new instance of {@code JsonLayoutBuilder}.
     *
     * @param name the name of the layout to be built
     */
    JsonLayoutBuilder(String name) {
        super(name, ATTRIBUTES);
    }

    public LogLayout build() {
        boolean properties = (boolean) getValue(PROPERTIES);
        boolean locationInfoEnabled = (boolean) getValue(LOCATION_INFO_ENABLED);
        boolean stacktraceEnabled = (boolean) getValue(STACKTRACE_ENABLED);
        int maxStringLength = (int) getValue(MAX_STRING_LENGTH);
        String truncatedStringSuffix = (String) getValue(TRUNCATED_STRING_SUFFIX);
        return new JsonLayout(properties, locationInfoEnabled, stacktraceEnabled, maxStringLength, truncatedStringSuffix);
    }
}
