package org.slb4j.config;

import org.slb4j.LoggingConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Interface for parsing logging configuration.
 */
public interface ConfigParser {
    /**
     * Parses the configuration from the given {@code InputStream} to construct a {@link LoggingConfiguration}.
     *
     * @param in the {@code InputStream} containing configuration details.
     * @return a fully configured {@link LoggingConfiguration} object.
     * @throws IOException if an error occurs while reading from the stream.
     */
    LoggingConfiguration parse(InputStream in) throws IOException;

    /**
     * Parses the given {@code Properties} object to construct a {@link LoggingConfiguration}.
     *
     * @param properties the {@code Properties} object containing configuration details.
     * @return a fully configured {@link LoggingConfiguration} object.
     */
    default LoggingConfiguration parse(Properties properties) {
        throw new UnsupportedOperationException("This parser does not support Properties objects.");
    }
}
