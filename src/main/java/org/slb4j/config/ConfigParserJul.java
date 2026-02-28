package org.slb4j.config;

import org.slb4j.LogHandler;
import org.slb4j.LogLevel;
import org.slb4j.LoggingConfiguration;
import org.slb4j.SLB4J;
import org.slb4j.filter.LogLevelFilter;
import org.slb4j.filter.LoggerNamePrefixFilter;
import org.slb4j.handler.ConsoleHandler;
import org.slb4j.handler.FileHandler;
import org.slb4j.handler.RotatingFileHandler;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * The ConfigParserJul class provides functionality for parsing
 * configuration properties from logging.properties into a
 * LoggingConfiguration object.
 */
public class ConfigParserJul implements ConfigParser {

    /**
     * Default constructor.
     */
    public ConfigParserJul() {
        // nothing to do
    }

    /**
     * Parses the provided Properties object to create and return a LoggingConfiguration instance.
     *
     * Supported JUL options (best-effort):
     * <ul>
     * <li>handlers (ConsoleHandler, FileHandler)
     * <li>.level and {@literal <logger>.level}
     * <li>java.util.logging.ConsoleHandler.level
     * <li>java.util.logging.FileHandler.pattern, limit, count, append
     * </ul>
     * @param props the Properties object containing logging configuration settings
     * @return a LoggingConfiguration instance initialized with the parsed properties
     */
    @Override
    public LoggingConfiguration parse(Properties props) {
        LoggingConfiguration cfg = new LoggingConfiguration();

        // 1) Build and install a root + per-logger level filter
        LoggerNamePrefixFilter nameFilter = new LoggerNamePrefixFilter("jul-levels");

        // Root level: key ".level"
        String rootLevel = props.getProperty(".level");
        if (rootLevel != null) {
            nameFilter.setLevel(mapJulLevel(rootLevel));
        } else {
            nameFilter.setLevel(LogLevel.INFO); // JUL default
        }

        // Per-logger levels: keys like "com.example.level=FINE"
        for (Map.Entry<Object, Object> e : props.entrySet()) {
            String key = String.valueOf(e.getKey());
            if (key.endsWith(".level") && !".level".equals(key)) {
                String loggerName = key.substring(0, key.length() - ".level".length());
                String lvl = String.valueOf(e.getValue());
                nameFilter.setLevel(loggerName, mapJulLevel(lvl));
            }
        }
        cfg.setRootFilter(nameFilter);

        // 2) Parse handlers list
        String handlersProp = props.getProperty("handlers", "");
        List<String> handlerClasses = new ArrayList<>();
        for (String token : handlersProp.split(",")) {
            String t = token.trim();
            if (!t.isEmpty()) {
                handlerClasses.add(t);
            }
        }

        int consoleCount = 0;
        int fileCount = 0;

        for (String hc : handlerClasses) {
            switch (hc) {
                case "java.util.logging.ConsoleHandler" -> {
                    String name = consoleCount++ == 0 ? "console" : ("console" + consoleCount);
                    LogHandler handler = buildConsoleHandler(name, props);
                    cfg.addHandler(name, handler);
                }
                case "java.util.logging.FileHandler" -> {
                    String name = fileCount++ == 0 ? "file" : ("file" + fileCount);
                    LogHandler handler = buildFileHandler(name, props);
                    if (handler != null) {
                        cfg.addHandler(name, handler);
                    }
                }
                default -> {
                    SLB4J.logInternal(LogLevel.WARN, "JUL: Unsupported handler class %s — ignoring.", hc);
                }
            }
        }

        return cfg;
    }

    private static LogHandler buildConsoleHandler(String name, Properties props) {
        // JUL ConsoleHandler writes to System.err by default; we default to System.out here
        PrintStream out = System.out;
        ConsoleHandler ch = new ConsoleHandler(name, out, true);

        // Optional level filter for handler
        String lvl = props.getProperty("java.util.logging.ConsoleHandler.level");
        if (lvl != null && !lvl.isBlank()) {
            LogLevel mapped = mapJulLevel(lvl);
            ch.setFilter(LogLevelFilter.pass(mapped));
        }

        // JUL formatter not mapped; keep default layout
        return ch;
    }

    private static LogHandler buildFileHandler(String name, Properties props) {
        String prefix = "java.util.logging.FileHandler.";
        String fileNamePattern = props.getProperty(prefix + "pattern");
        if (fileNamePattern == null || fileNamePattern.isBlank()) {
            // No pattern set — JUL would use a default in user home; here we ignore
            SLB4J.logInternal(LogLevel.WARN, "JUL: FileHandler.pattern not set — skipping file handler creation");
            return null;
        }

        long limit = parseLong(props.getProperty(prefix + "limit"), 0L);
        int count = (int) parseLong(props.getProperty(prefix + "count"), 1L);
        boolean append = Boolean.parseBoolean(props.getProperty(prefix + "append", "false"));

        try {
            if (limit > 0 || count > 1) {
                RotatingFileHandler rfh = new RotatingFileHandler(name, "", fileNamePattern, append, RotatingFileHandler.IndexStrategy.USE_MAX);
                if (limit > 0) {
                    rfh.setMaxFileSize(limit);
                }
                if (count > 0) {
                    // JUL count means total files, including active one; backups = count-1
                    rfh.setMaxBackupIndex(Math.max(0, count - 1));
                }
                // No direct time-based rotation in JUL default props; keep size-based only
                return rfh;
            } else {
                return new FileHandler(name, fileNamePattern, append);
            }
        } catch (java.io.IOException e) {
            SLB4J.logInternal(LogLevel.WARN, "JUL: Failed creating FileHandler for %s: %s", fileNamePattern, e);
            return null;
        }
    }

    private static long parseLong(String s, long def) {
        if (s == null) return def;
        try { return Long.parseLong(s.trim()); } catch (NumberFormatException e) { return def; }
    }

    private static LogLevel mapJulLevel(String julLevel) {
        String v = Objects.toString(julLevel, "").trim().toUpperCase(Locale.ROOT);
        return switch (v) {
            case "SEVERE" -> LogLevel.ERROR;
            case "WARNING" -> LogLevel.WARN;
            case "INFO" -> LogLevel.INFO;
            case "CONFIG" -> LogLevel.DEBUG; // map CONFIG to DEBUG
            case "FINE", "FINER" -> LogLevel.DEBUG;
            case "FINEST", "ALL" -> LogLevel.TRACE;
            case "OFF" -> LogLevel.ERROR; // closest; effectively means suppress, but we can't model OFF exactly here
            default -> LogLevel.INFO;
        };
    }
}
