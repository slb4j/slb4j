package org.slb4j.config;

import org.slb4j.LayoutConfigurable;
import org.slb4j.LogFilter;
import org.slb4j.LogHandler;
import org.slb4j.LogLayout;
import org.slb4j.LogLevel;
import org.slb4j.LoggingConfiguration;
import org.slb4j.SLB4J;
import org.slb4j.filter.LogLevelFilter;
import org.slb4j.filter.LoggerNamePrefixFilter;
import org.slb4j.filter.MarkerFilter;
import org.slb4j.filter.MessageTextFilter;
import org.slb4j.handler.ConsoleHandler;
import org.slb4j.handler.RotatingFileHandler;
import org.slb4j.layout.LayoutBuilder;
import org.slb4j.layout.Layouts;
import org.slb4j.support.Util;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Predicate;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The ConfigParserLog4j class provides methods to parse Log4j configuration details
 * from property definitions.
 * <p>
 * This implementation uses the Log4J2 properties syntax.
 */
public class ConfigParserLog4j implements ConfigParser {

    private static final String THRESHOLD_FILTER = "ThresholdFilter";
    private static final String MARKER_FILTER = "MarkerFilter";
    private static final String REGEX_FILTER = "RegexFilter";
    private static final String SYSTEM_OUT = "SYSTEM_OUT";
    private static final String SYSTEM_ERR = "SYSTEM_ERR";
    private static final String APPENDER_REF = "appenderRef";
    private static final String ROOT_LOGGER = "rootLogger";
    private static final String LEVEL = "level";
    private static final String LAYOUT = "layout";
    private static final String POLICIES = "policies";
    private static final String STRATEGY = "strategy";
    private static final String FILTER = "filter";
    private static final String ON_MATCH = "onMatch";
    private static final String ON_MISMATCH = "onMismatch";
    private static final String NEUTRAL = "NEUTRAL";
    private static final String DENY = "DENY";
    private static final String STATUS_CONFIG = "statusConfig";
    private static final String NAME = "name";
    private static final String DEST = "dest";
    private static final String MAX_ENTRIES = "maxEntries";
    private static final String FILE_INDEX = "fileIndex";
    private static final String MIN = "min";
    private static final String MAX = "max";

    /**
     * Default constructor.
     */
    public ConfigParserLog4j() {
        // nothing to do
    }

    /**
     * Pattern to extract layout configuration from log4j2.properties.
     * <p>
     * The pattern extracts group as follows
     * <pre>{@literal
     * [appender.<name>.]<item>.<key>
     *
     * appender.file.layout.type
     * |        |    |      |
     * |        |    |      option
     * |        |    item
     * |        appender name
     * literal "appender"
     *
     * appender.console.type
     * |        |       |      (no entry)
     * |        |       option
     * |        appender name
     * literal "appender"
     *
     * layout.type
     * |      |                (no literal "appender", no appender name)
     * |      option
     * item
     * }</pre>
     */
    public static final Pattern PATTERN_LOG4J2_CONFIG = Pattern.compile(
            "^(" +
                    "((?<appender>appender|logger)\\.(?<appenderName>[^\\.]*)\\.)?" +
                    "((?<root>rootLogger)\\.)?" +
                    "(((?<entry>[^.]*)\\.)((?<entryName>[^.]*)\\.)?)" +
                    "?(?<option>.*)" +
                    ")$"
    );

    @Override
    public LoggingConfiguration parse(InputStream in) throws IOException {
        Properties props = new Properties();
        props.load(in);
        return parse(props);
    }

    /**
     * Parses the given {@code Properties} object to construct a {@link LoggingConfiguration}
     * based on log4j-like configuration definitions. This method processes appenders, filters,
     * layouts, and handler options to create a complete logging configuration.
     *
     * @param properties the {@code Properties} object containing configuration details.
     *                   Keys and values should conform to the expected log4j-like patterns
     *                   for defining appenders, filters, layouts, and their respective options.
     * @return a fully configured {@link LoggingConfiguration} object constructed from the
     *         provided {@code properties}.
     */
    @Override
    public LoggingConfiguration parse(Properties properties) {
        // collect all definitions
        LogLevel[] statusLevel = {LogLevel.WARN}; // level for internal backend logging
        String[] statusName = {""};
        String[] statusDest = {"err"};
        Map<String, Map<String, String>> appenderConfig = new HashMap<>();
        Map<String, Map<String, String>> loggerConfig = new HashMap<>();
        Map<String, Map<String, Map<String, String>>> entryConfig = new HashMap<>();
        Map<String, Map<String, Map<String, Map<String, String>>>> compoundEntryConfig = new HashMap<>();
        properties.forEach((keyObject, valueObject) -> {
           String key = String.valueOf(keyObject);
           String value = String.valueOf(valueObject);

            Matcher matcher = PATTERN_LOG4J2_CONFIG.matcher(key);
            // Populates definitions with appender and layout details
            if (matcher.matches()) {
                MatchResult mr = matcher.toMatchResult();

                String appenderName = Objects.requireNonNullElse(mr.group("appenderName"), "");
                String appenderOrLogger = Objects.requireNonNullElse(mr.group("appender"), "");
                String isRoot = mr.group("root");
                String entry = Objects.requireNonNullElse(mr.group("entry"), "");
                String entryName = Objects.requireNonNullElse(mr.group("entryName"), "");
                String option = Objects.requireNonNullElse(mr.group("option"), "");

                if (ROOT_LOGGER.equals(key)) {
                    String[] parts = value.split(",");
                    if (parts.length > 0) {
                        appenderConfig.computeIfAbsent(ROOT_LOGGER, k -> new HashMap<>()).put(LEVEL, parts[0].trim());
                        for (int i = 1; i < parts.length; i++) {
                            entryConfig.computeIfAbsent(ROOT_LOGGER, k -> new HashMap<>())
                                    .computeIfAbsent(APPENDER_REF, k -> new HashMap<>())
                                    .put(String.valueOf(i - 1), parts[i].trim());
                        }
                    }
                    return;
                }

                if ("status".equals(key)) {
                    if (entry.isEmpty()) {
                        try {
                            statusLevel[0] = LogLevel.valueOf(value.toUpperCase());
                        } catch (IllegalArgumentException e) {
                            SLB4J.logInternal(LogLevel.WARN, "Ignoring unknown status level %s", value);
                        }
                    } else {
                        SLB4J.logInternal(LogLevel.WARN, "Ignoring status option for entry %s", entry);
                    }
                    return;
                }

                if (STATUS_CONFIG.equals(key) || STATUS_CONFIG.equals(entry)) {
                    // Handle both statusConfig = value and statusConfig.option = value
                    // but the regex usually puts "statusConfig" in entry if it's statusConfig.dest
                    String actualOption = entry.equals(STATUS_CONFIG) ? option : key.substring("statusConfig.".length());

                    switch (actualOption) {
                        case NAME -> statusName[0] = value;
                        case DEST -> statusDest[0] = value;
                        case MAX_ENTRIES -> { /* ignore as per requirement */ }
                        default -> SLB4J.logInternal(LogLevel.WARN, "Ignoring unknown statusConfig option %s", actualOption);
                    }
                    return;
                }

                if (isRoot != null) {
                    appenderName = ROOT_LOGGER;
                    if (entry.isEmpty() && !option.isEmpty() && !LEVEL.equals(option) && !APPENDER_REF.equals(option)) {
                        entry = option;
                        option = "";
                    }
                }

                if ("logger".equals(appenderOrLogger)) {
                    if (entry.isEmpty()) {
                        loggerConfig.computeIfAbsent(appenderName, k -> new HashMap<>()).put(option, value);
                    } else if (entryName.isEmpty()) {
                        entryConfig.computeIfAbsent("logger." + appenderName, k -> new HashMap<>())
                                .computeIfAbsent(entry, k -> new HashMap<>())
                                .put(option, value);
                    } else {
                        compoundEntryConfig.computeIfAbsent("logger." + appenderName, k -> new HashMap<>())
                                .computeIfAbsent(entry, k -> new HashMap<>())
                                .computeIfAbsent(entryName, k -> new HashMap<>())
                                .put(option, value);
                    }
                } else {
                    if (entry.isEmpty()) {
                        // configure the appender itself
                        if  (!appenderName.isEmpty()) {
                            appenderConfig.computeIfAbsent(appenderName, k -> new HashMap<>()).put(option, value);
                        }
                    } else if (entryName.isEmpty()) {
                        // configure the entry
                        entryConfig.computeIfAbsent(appenderName, k -> new HashMap<>())
                                .computeIfAbsent(entry, k -> new HashMap<>())
                                .put(option, value);
                    } else {
                        // configure the entry
                        compoundEntryConfig.computeIfAbsent(appenderName, k -> new HashMap<>())
                                .computeIfAbsent(entry, k -> new HashMap<>())
                                .computeIfAbsent(entryName, k -> new HashMap<>())
                                .put(option, value);
                    }
                }
            }
        });

        // create compound filters
        Map<String, LogFilter> filters = new HashMap<>();
        compoundEntryConfig.forEach((appenderName, config) -> {
            List<LogFilter> list = new ArrayList<>();
            config.forEach((name, defs) -> {
                if (FILTER.equals(name)) {
                    defs.forEach((entryName, options) ->
                        list.add(parseFilterDefinition(appenderName, options))
                    );
                }
            });
            if (!list.isEmpty()) {
                LogFilter filter = LogFilter.combine(list.toArray(LogFilter[]::new));
                LogFilter old = filters.put(appenderName, filter);
                if (old != null) {
                    SLB4J.logInternal(LogLevel.WARN, "Duplicate compound filter definition for appender %s!", appenderName);
                }
            }
        });

        // create layouts and filters and extract handler options
        Map<String, Map<String, Map<String, String>>> handlerOptions = new HashMap<>();
        Map<String, LogLayout> layouts = new HashMap<>();
        entryConfig.forEach((appenderName, entryDefs) -> {
            entryDefs.forEach((entryName, options) -> {
                switch (entryName) {
                    case "" -> {
                        Map<String, Map<String, String>> appenderHandlerOptions = handlerOptions.computeIfAbsent(appenderName, k -> new HashMap<>());
                        appenderHandlerOptions.put(appenderName, options);
                    }
                    case LAYOUT ->
                        layouts.put(appenderName, parseLayoutDefinition(appenderName, options));
                    case APPENDER_REF -> {
                        // handled during appender creation or rootLogger setup
                    }
                    case POLICIES, STRATEGY -> {
                        // handled during appender creation
                    }
                    case FILTER -> {
                        LogFilter old = filters.put(appenderName, parseFilterDefinition(appenderName, options));
                        if (old != null) {
                            SLB4J.logInternal(LogLevel.WARN, "Duplicate compound filter definition for appender %s!", appenderName);
                        }
                    }
                    default -> SLB4J.logInternal(LogLevel.WARN, "Ignoring unknown entry %s in appender definition for appender %s!", entryName, appenderName);
                }
            });
        });

        LoggingConfiguration configuration = new LoggingConfiguration();
        configuration.setStatusLevel(statusLevel[0]);
        configuration.setStatusName(statusName[0]);
        configuration.setStatusDest(statusDest[0]);

        // create appenders
        appenderConfig.forEach((appenderIdentifier, options) -> {
            if (ROOT_LOGGER.equals(appenderIdentifier)) {
                return;
            }
            LogHandler handler = null;
            String appenderName = options.getOrDefault(NAME, appenderIdentifier);
            String type = options.getOrDefault("type", "Console");
            switch (type) {
                case "Console" -> {
                    String target = options.getOrDefault("target", SYSTEM_OUT);
                    PrintStream out = switch (target) {
                        case SYSTEM_OUT -> System.out;
                        case SYSTEM_ERR -> System.err;
                        default -> {
                            SLB4J.logInternal(LogLevel.WARN, "Appender %s: Ignoring unknown target %s, using %s instead", appenderName, target, SYSTEM_OUT);
                            yield System.out;
                        }
                    };
                    handler = new ConsoleHandler(appenderName, out, true);
                }
                case "File", "RollingFile" -> {
                    String fileName = options.getOrDefault("fileName", "");
                    String fileNamePattern = options.getOrDefault("filePattern", "");
                    boolean append = Boolean.parseBoolean(options.getOrDefault("append", "true"));

                    try {
                        Map<String, Map<String, String>> appenderEntries = entryConfig.getOrDefault(appenderIdentifier, Map.of());
                        Map<String, String> policies = appenderEntries.getOrDefault(POLICIES, Map.of());
                        String size = policies.get("size.size");
                        if (size == null) {
                            // check for appender.NAME.policies.size.size in compound entry config
                            size = compoundEntryConfig.getOrDefault(appenderIdentifier, Map.of())
                                    .getOrDefault(POLICIES, Map.of())
                                    .getOrDefault("size", Map.of())
                                    .get("size");
                        }

                        if ("RollingFile".equals(type) || compoundEntryConfig.containsKey(appenderIdentifier) || appenderEntries.containsKey(POLICIES) || size != null) {
                            Map<String, String> strategy = appenderEntries.getOrDefault(STRATEGY, Map.of());
                            String fileIndex = strategy.get(FILE_INDEX);
                            if (fileIndex == null) {
                                fileIndex = compoundEntryConfig.getOrDefault(appenderIdentifier, Map.of())
                                        .getOrDefault(STRATEGY, Map.of())
                                        .getOrDefault(FILE_INDEX, Map.of())
                                        .get(FILE_INDEX);
                            }
                            RotatingFileHandler.IndexStrategy indexStrategy = RotatingFileHandler.IndexStrategy.USE_MAX;
                            if (MIN.equalsIgnoreCase(fileIndex)) {
                                indexStrategy = RotatingFileHandler.IndexStrategy.USE_MIN;
                            } else if (MAX.equalsIgnoreCase(fileIndex)) {
                                indexStrategy = RotatingFileHandler.IndexStrategy.USE_MAX;
                            } else if (fileIndex != null) {
                                SLB4J.logInternal(LogLevel.WARN, "Appender %s: Unknown fileIndex '%s', using 'max'", appenderName, fileIndex);
                            }

                            org.slb4j.handler.RotatingFileHandler rfh = new org.slb4j.handler.RotatingFileHandler(appenderName, fileName, fileNamePattern, append, indexStrategy);
                            if (size != null) {
                                rfh.setMaxFileSize(Util.parseSize(size));
                            }
                            String max = strategy.get(MAX);
                            if (max == null) {
                                max = compoundEntryConfig.getOrDefault(appenderIdentifier, Map.of())
                                        .getOrDefault(STRATEGY, Map.of())
                                        .getOrDefault("max", Map.of())
                                        .get("max");
                            }
                            if (max != null) {
                                rfh.setMaxBackupIndex(Integer.parseInt(max));
                            }
                            handler = rfh;
                        } else {
                            handler = new org.slb4j.handler.FileHandler(appenderName, fileName, append);
                        }
                    } catch (java.io.IOException e) {
                        SLB4J.logInternal(LogLevel.WARN, "Appender %s: Failed to create file handler for %s: %s", appenderName, fileName, e);
                    }
                }
                default ->
                    SLB4J.logInternal(LogLevel.WARN, "Ignoring unknown appender type %s!", type);
            }

            if (handler != null) {
                configuration.addHandler(appenderIdentifier, handler);
            }
        });

        // add layouts
        layouts.forEach((appenderIdentifier, layout) -> {
            LogHandler handler = configuration.getHandlers().get(appenderIdentifier);
            if (handler instanceof LayoutConfigurable lc) {
                lc.setLayout(layout);
            } else {
                SLB4J.logInternal(LogLevel.WARN, "Ignoring layout definition for appender %s: no handler found", appenderIdentifier);
            }
        });

        // add filters
        filters.forEach((appenderIdentifier, filter) -> {
            if (ROOT_LOGGER.equals(appenderIdentifier)) {
                configuration.setRootFilter((LoggerNamePrefixFilter) filter);
                return;
            }
            LogHandler handler = configuration.getHandlers().get(appenderIdentifier);
            if (handler != null) {
                handler.setFilter(filter);
            } else {
                SLB4J.logInternal(LogLevel.WARN, "Ignoring filter definition for appender %s: handler not found", appenderIdentifier);
            }
        });

        // process logger entries
        if (!loggerConfig.isEmpty()) {
            loggerConfig.forEach((loggerIdentifier, options) -> {
                String name = options.getOrDefault(NAME, loggerIdentifier);
                String levelStr = options.get(LEVEL);
                if (levelStr != null) {
                    try {
                        LogLevel level = LogLevel.valueOf(levelStr.toUpperCase(Locale.ROOT));
                        configuration.getRootFilter().setLevel(name, level);
                    } catch (IllegalArgumentException e) {
                        SLB4J.logInternal(LogLevel.WARN, "Logger %s: Ignoring unknown level %s", name, levelStr);
                    }
                }
            });
         }

        // process rootLogger level if filter was not set
        Map<String, String> rootOptions = appenderConfig.get(ROOT_LOGGER);
        if (rootOptions != null) {
            String levelStr = rootOptions.get(LEVEL);
            if (levelStr != null) {
                try {
                    LogLevel level = LogLevel.valueOf(levelStr.toUpperCase(Locale.ROOT));
                    configuration.setRootLevel(level);
                } catch (IllegalArgumentException e) {
                    SLB4J.logInternal(LogLevel.WARN, "rootLogger: Ignoring unknown level %s", levelStr);
                }
            }
        }

        return configuration;
    }

    private static LogLayout parseLayoutDefinition(String appenderName, Map<String, String> defs) {
        String type = defs.getOrDefault("type", "PatternLayout");
        LayoutBuilder builder = Layouts.getBuilder(type)
                .orElseThrow(() -> new IllegalStateException("unsupported Layout: " + type))
                .apply(appenderName);
        builder.applyDefinitions(defs);
        return builder.build();
    }

    private static LogFilter parseFilterDefinition(String appenderName, Map<String, String> defs) {
        String type = defs.getOrDefault("type", THRESHOLD_FILTER);
        return switch (type) {
            case THRESHOLD_FILTER -> parseThresholFilter(defs);
            case MARKER_FILTER -> parseMarkerFilter(defs);
            case REGEX_FILTER -> parseRegexFilter(defs);
            default -> {
                SLB4J.logInternal(LogLevel.WARN, "Ignoring unknown filter type %s in definition of appender %s!", type, appenderName);
                yield  LogLevelFilter.allPass();
            }
        };
    }

    private static LogFilter parseThresholFilter(Map<String, String> options) {
        // 1. Resolve the Threshold Level (default to ALL/TRACE if missing)
        String levelStr = options.getOrDefault(LEVEL, "TRACE").toUpperCase(Locale.ROOT).trim();
        LogLevel threshold;
        try {
            threshold = LogLevel.valueOf(levelStr);
        } catch (IllegalArgumentException e) {
            SLB4J.logInternal(LogLevel.WARN, "Ignoring unknown threshold level %s in filter definition!", levelStr);
            return LogFilter.allPass();
        }

        // 2. Resolve Log4j2-style Actions
        // Log4j2 Defaults: onMatch=NEUTRAL (treated as pass), onMismatch=DENY (treated as fail)
        String onMatch = options.getOrDefault(ON_MATCH, NEUTRAL).toUpperCase(Locale.ROOT).trim();
        String onMismatch = options.getOrDefault(ON_MISMATCH, DENY).toUpperCase(Locale.ROOT).trim();

        // 3. Map every LogLevel to a pass/fail boolean
        LogLevel[] allLevels = LogLevel.values();
        boolean[] passMap = new boolean[allLevels.length];

        for (int i = 0; i < allLevels.length; i++) {
            LogLevel current = allLevels[i];

            // Log4j2 Threshold Logic: Match if current level is equal or more severe than threshold
            boolean isMatch = current.ordinal() >= threshold.ordinal();

            String action = isMatch ? onMatch : onMismatch;

            // In a binary boolean filter:
            // ACCEPT/NEUTRAL = true (let the log through)
            // DENY = false (stop the log)
            passMap[i] = !action.equals(DENY);
        }

        String name = String.format("LogLevelFilter[threshold=%s, match=%s, mismatch=%s]", threshold, onMatch, onMismatch);

        return new LogLevelFilter(name, passMap);
    }

    private static LogFilter parseMarkerFilter(Map<String, String> options) {
        String target = options.getOrDefault("marker", "");
        String onMatch = options.getOrDefault(ON_MATCH, NEUTRAL).toUpperCase().trim();
        String onMismatch = options.getOrDefault(ON_MISMATCH, DENY).toUpperCase().trim();

        boolean matchPasses = !onMatch.equals(DENY);
        boolean mismatchPasses = !onMismatch.equals(DENY);

        Predicate<? super String> predicate;

        if (matchPasses && mismatchPasses) {
            // Case: Both pass (Bypass)
            predicate = s -> true;
        } else if (!matchPasses && !mismatchPasses) {
            // Case: Both deny (Kill switch)
            predicate = s -> false;
        } else if (matchPasses) {
            // Case: Match passes, Mismatch fails (Standard filter)
            // This is exactly: marker::equals
            predicate = target::equals;
        } else {
            // Case: Match fails, Mismatch passes (Inverted filter)
            // This is exactly: !marker::equals
            predicate = s -> !target.equals(s);
        }

        String name = String.format("MarkerFilter[%s, match=%b, mismatch=%b]",
                target, matchPasses, mismatchPasses);

        return new MarkerFilter(name, predicate);
    }

    private static LogFilter parseRegexFilter(Map<String, String> options) {
        String regex = options.getOrDefault("regex", ".*");
        Pattern pattern = Pattern.compile(regex);

        String onMatch = options.getOrDefault(ON_MATCH, NEUTRAL).toUpperCase().trim();
        String onMismatch = options.getOrDefault(ON_MISMATCH, DENY).toUpperCase().trim();

        boolean matchPasses = !onMatch.equals(DENY);
        boolean mismatchPasses = !onMismatch.equals(DENY);

        // Get the base predicate (uses .find() logic)
        Predicate<CharSequence> basePredicate = s -> pattern.matcher(s).find();

        Predicate<? super CharSequence> textFilter;

        if (matchPasses && mismatchPasses) {
            textFilter = s -> true;
        } else if (!matchPasses && !mismatchPasses) {
            textFilter = s -> false;
        } else if (matchPasses) {
            // Match -> Pass, Mismatch -> Fail
            textFilter = basePredicate;
        } else {
            // Match -> Fail, Mismatch -> Pass (Inverted)
            textFilter = basePredicate.negate();
        }

        return new MessageTextFilter("RegexFilter[" + regex + "]", textFilter);
    }
}
