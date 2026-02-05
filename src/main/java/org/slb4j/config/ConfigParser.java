package org.slb4j.config;

import org.slb4j.LoggingConfiguration;
import java.util.Properties;

/**
 * Interface for parsing logging configuration from properties.
 */
public interface ConfigParser {
    /**
     * Parses the given {@code Properties} object to construct a {@link LoggingConfiguration}.
     *
     * @param properties the {@code Properties} object containing configuration details.
     * @return a fully configured {@link LoggingConfiguration} object.
     */
    LoggingConfiguration parse(Properties properties);
}
