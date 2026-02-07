package org.slb4j;

/**
 * Plugin interface to be implemented by classes providing extended functionalities.
 *
 * This interface requires implementing classes to define their name and provide
 * an initialization mechanism.
 */
public interface Plugin {
    /**
     * Exception thrown to indicate an error occurred during the initialization process.
     *
     * This exception is typically used to signal failures in setup or configuration steps
     * that are required to prepare a component, such as a plugin, for normal operation.
     */
    class InitializationException extends RuntimeException {
        /**
         * Constructs a new {@code InitializationException} with the specified detail message.
         *
         * @param message the detail message providing additional information about the exception.
         */
        public InitializationException(String message) {
            super(message);
        }

        /**
         * Constructs a new {@code InitializationException} with the specified detail message and cause.
         *
         * @param message the detail message that provides information about the error.
         * @param cause   the underlying cause of the error, or {@code null} if the cause is not available.
         */
        public InitializationException(String message, Throwable cause) {
            super(message, cause);
        }

        /**
         * Constructs a new InitializationException with the specified cause.
         *
         * This constructor is used to create an exception that solely wraps
         * another throwable. The message of this exception will be set to the
         * message of the provided cause.
         *
         * @param cause the underlying cause of this exception. May be null.
         */
        public InitializationException(Throwable cause) {
            super(cause);
        }
    }

    /**
     * Retrieves the name of the plugin.
     *
     * @return the name of the plugin as a non-null, human-readable string.
     */
    String name();

    /**
     * Initializes the plugin.
     *
     * This method is called to perform any necessary setup or configuration required
     * for the plugin to function correctly. Implementations should handle all required
     * initialization procedures, such as resource allocation or dependency preparation.
     *
     * @throws InitializationException if any error occurs during the initialization process.
     */
    void init() throws InitializationException;
}
