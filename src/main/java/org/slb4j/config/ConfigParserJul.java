package org.slb4j.config;

import org.slb4j.LoggingConfiguration;

import java.util.Properties;

/**
 * The ConfigParserJul class provides functionality for parsing
 * configuration properties from logging.properties into a
 * LoggingConfiguration object.
 */
public class ConfigParserJul implements ConfigParser {

    /**
     * Default construvtor.
     */
    public ConfigParserJul() {
        // nothing to do
    }

    /**
     * Parses the provided Properties object to create and return a LoggingConfiguration instance.
     *
     * @param props the Properties object containing logging configuration settings
     * @return a LoggingConfiguration instance initialized with the parsed properties
     */
    @Override
    public LoggingConfiguration parse(Properties props) {
        // FIXME
        throw new UnsupportedOperationException();
    }
}
