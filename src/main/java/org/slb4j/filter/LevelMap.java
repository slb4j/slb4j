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
package org.slb4j.filter;

import org.slb4j.LogLevel;
import org.slb4j.support.SharableString;
import org.slb4j.support.SharedString;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LevelMap is a hierarchical mapping structure used for associating log levels
 * with logger names. It supports efficient lookups and updates of log levels at
 * various levels in the logger hierarchy.
 * <p>
 * Each logger name is represented as a hierarchical structure, where parts of
 * the name separated by dots ('.') are treated as levels.
 * This allows for retrieval of the most specific log level associated with a given logger name.
 */
final class LevelMap {
    public static final String ROOT_LEVEL_SHOULD_NEVER_BE_NULL = "internal error! Root level should never be null.";

    // The root level handles the "empty" or "root" logger
    private final Node root;

    /**
     * Constructs a new instance of {@code LevelMap} with the specified root log level.
     * This initializes the root node of the map with the given {@code rootLevel}.
     * The class is used to manage log levels hierarchically across various loggers.
     *
     * @param rootLevel the {@code LogLevel} to be assigned to the root node of the map.
     */
    LevelMap(LogLevel rootLevel) {
        this.root = new Node();
        this.root.level = rootLevel;
    }

    private LevelMap(Node root) {
        this.root = root;
    }

    /**
     * Creates a deep copy of this {@code LevelMap}.
     *
     * @return a new {@code LevelMap} instance that is a deep copy of this one.
     */
    public LevelMap copy() {
        return new LevelMap(root.copy());
    }

    /**
     * Retrieves the root node of the hierarchical structure managed by this instance of {@code LevelMap}.
     *
     * @return the root {@code Node} of the {@code LevelMap}.
     */
    public Node getRoot() {
        return root;
    }

    /**
     * Retrieves a map of logger names/name prefixes to their associated log levels.
     * <p>
     * This method collects all log level rules defined hierarchically within the
     * underlying structure, starting from the root node. The logger names are
     * recorded as keys in the map, and their respective log levels are the values.
     *
     * @return a map containing logger name to log level mappings, where the keys are logger
     *         names and the values are their corresponding {@code LogLevel}.
     */
    public Map<String, LogLevel> rules() {
        Map<String, LogLevel> rules = new java.util.LinkedHashMap<>();

        LogLevel rootLevel = root.level;
        assert rootLevel != null : ROOT_LEVEL_SHOULD_NEVER_BE_NULL;
        rules.put("", rootLevel);

        // traverse the complete tree and add all rules to the map
        traverseAndCollectRules(rules, "", root);
        return rules;
    }

    /**
     * Recursively traverses the tree and collects all logger name to level mappings.
     *
     * @param rules  the map to collect the rules into
     * @param prefix the accumulated logger name prefix up to this node
     * @param node   the current node being traversed
     */
    private static void traverseAndCollectRules(Map<String, LogLevel> rules, String prefix, Node node) {
        for (Map.Entry<SharedString, Node> entry : node.children.entrySet()) {
            String loggerName = prefix.isEmpty() ? entry.getKey().toString() : prefix + "." + entry.getKey();
            Node childNode = entry.getValue();

            LogLevel lvl = childNode.level; // store volatile variable locally
            if (lvl != null) {
                rules.put(loggerName, lvl);
            }

            traverseAndCollectRules(rules, loggerName, childNode);
        }
    }

    /**
     * Represents a single node in a hierarchical data structure, useful for storing relationships
     * between keys and associated log levels. Each node may have child nodes and an optional log level.
     * <p>
     * This class is designed to model a tree structure where each node can house multiple children
     * identified by a {@link SharedString} key. Log levels can be dynamically assigned to nodes for
     * structured logging purposes.
     */
    public static final class Node {
        // We use SharedString as the key to allow lookups with your window-view
        final Map<SharedString, Node> children = new ConcurrentHashMap<>();
        volatile @Nullable LogLevel level = null;

        /**
         * Creates a deep copy of this node and all its children.
         *
         * @return a new {@code Node} instance that is a deep copy of this one.
         */
        public Node copy() {
            Node newNode = new Node();
            newNode.level = this.level;
            for (Map.Entry<SharedString, Node> entry : children.entrySet()) {
                newNode.children.put(entry.getKey(), entry.getValue().copy());
            }
            return newNode;
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (!(o instanceof Node other)) return false;
            return Objects.equals(children, other.children) && level == other.level;
        }

        @Override
        public int hashCode() {
            return Objects.hash(children, level);
        }

        @Override
        public String toString() {
            return appendTo(new StringBuilder()).toString();
        }

        /**
         * Appends the string representation of the current node and its children to the provided StringBuilder.
         * The method includes the node's log level and recursively appends all children in the format:
         * "level , {key -> child, ...}".
         *
         * @param sb the {@code StringBuilder} to which the string representation will be appended
         * @return the same {@code StringBuilder} instance, appended with the string representation of the node
         */
        public StringBuilder appendTo(StringBuilder sb) {
            LogLevel lvl = level; // store volatile field locally
            sb.append(lvl == null ? "null" : lvl.name());
            if (!children.isEmpty()) {
                for (Map.Entry<SharedString, Node> e : children.entrySet()) {
                    sb.append(" , {").append(e.getKey()).append(" -> ");
                    e.getValue().appendTo(sb);
                    sb.append("}");
                }
            }
            return sb;
        }
    }

    /**
     * Adds or updates the log level for the specified logger name in the hierarchical structure.
     * This method ensures that the logger name is correctly mapped to the given log level, creating
     * intermediate nodes as necessary within the logger hierarchy.
     *
     * @param loggerName the hierarchical name of the logger for which the log level is being set;
     *                   must not end with a '.' character
     * @param level      the {@code LogLevel} to associate with the specified logger name
     * @throws IllegalArgumentException if the {@code loggerName} ends with a '.' character
     */
    public void put(String loggerName, LogLevel level) {
        if (loggerName.isEmpty()) {
            root.level = level;
            return;
        }
        if (loggerName.endsWith(".")) {
            throw new IllegalArgumentException("loggerName must not end with '.'");
        }

        Node current = root;
        int start = 0;
        int dot;

        // Populate using SharedString segments
        SharableString name = new SharableString(loggerName);
        while ((dot = name.indexOf('.', start)) != -1) {
            SharedString segment = name.subSequence(start, dot);
            current = current.children.computeIfAbsent(segment, k -> new Node());
            start = dot + 1;
        }
        current.children.computeIfAbsent(name.subSequence(start, name.length()), k -> new Node()).level = level;
    }

    /**
     * Retrieves the {@link LogLevel} associated with the specified class name by traversing
     * the hierarchical structure. If no specific log level is found for the given class name,
     * the closest ancestor's log level is returned. The method starts at the root node
     * and iteratively progresses through each segment of the class name.
     *
     * @param className the fully qualified name of the class whose log level is being queried
     * @return the {@link LogLevel} associated with the class name or its nearest ancestor;
     *         defaults to the root level if no specific level is found
     */
    public LogLevel level(String className) {
        Node current = root;
        LogLevel level = root.level;

        assert level != null : ROOT_LEVEL_SHOULD_NEVER_BE_NULL;

        int start = 0;
        int dot;

        SharableString name = new SharableString(className);
        while ((dot = name.indexOf('.', start)) != -1) {
            // No new underlying char[] is created here!
            SharedString segment = name.subSequence(start, dot);

            current = current.children.get(segment);
            if (current == null) {
                assert level != null : ROOT_LEVEL_SHOULD_NEVER_BE_NULL;
                return level;
            }

            if (current.level != null) {
                level = current.level;
            }
            start = dot + 1;
        }

        // Final segment check
        SharedString lastSegment = name.subSequence(start, className.length());
        current = current.children.get(lastSegment);
        if (current != null && current.level != null) {
            level = current.level;
        }

        assert level != null : ROOT_LEVEL_SHOULD_NEVER_BE_NULL;
        return level;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (!(o instanceof LevelMap other)) return false;
        return Objects.equals(root, other.root);
    }

    @Override
    public int hashCode() {
        return Objects.hash(root);
    }

    @Override
    public String toString() {
        return root.toString();
    }
}