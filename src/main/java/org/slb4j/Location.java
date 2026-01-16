/*
 * Copyright 2026 Axel Howind - axh@dua3.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
