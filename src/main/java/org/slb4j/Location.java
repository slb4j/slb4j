package org.slb4j;

import org.jspecify.annotations.Nullable;

/**
 * The Location interface provides information about the context
 * of a specific point within the code, such as the class name,
 * method name, file name, and line number.
 */
public interface Location {
    /**
     * Fully qualified class name of the caller, e.g. "com.example.OrderService", or null if unknown
     */
    @Nullable String getClassName();

    /**
     * Method name of the caller, e.g. "processOrder", or null if unknown
     */
    @Nullable String getMethodName();

    /**
     * Line number in the source file, or -1 if unknown
     */
    int getLineNumber();

    /**
     * File name of the caller, e.g. "OrderService.java", or null if unknown
     */
    @Nullable String getFileName();
}
