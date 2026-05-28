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
import org.jspecify.annotations.Nullable;
import org.slb4j.support.Util;

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
    public static final String ROOT_LEVEL_NOT_INITIALIZED = "internal error! Root level should alwazs be initialized.";

    // The minimum log level that is configured for anz node
    private volatile int minLevel;

    // The root level handles the "empty" or "root" logger
    private final Node root;

    private static final Map<String, String[]> loggerNameCache = new ConcurrentHashMap<>();

    /**
     * Constructs a new instance of {@code LevelMap} with the specified root log level.
     * This initializes the root node of the map with the given {@code rootLevel}.
     * The class is used to manage log levels hierarchically across various loggers.
     *
     * @param rootLevel the level to be assigned to the root node of the map.
     */
    LevelMap(int rootLevel) {
        this(new Node(rootLevel));
    }

    /**
     * Private constructor for the {@code LevelMap} class.
     * Initializes the hierarchical structure with the specified root node.
     * This constructor enforces that the root node must have a non-null level.
     *
     * @param root the root {@code Node} of the hierarchical structure;
     *             must have a non-negative log level
     * @throws AssertionError if the root node's level is {@code null}
     */
    private LevelMap(Node root) {
        this.root = root;
        this.minLevel = calculateMinLevel(root);
    }

    /**
     * Recursively calculates the minimum log level from the given node and its children.
     * The method traverses the hierarchical structure of nodes and determines the smallest
     * level among the provided node and all its descendants.
     *
     * @param node the {@code Node} from which to start the calculation; must not be {@code null}.
     * @return the minimum log level found, or {@code Integer.MAX_VALUE} if the node has no children.
     */
    private int calculateMinLevel(Node node) {
        int level = node.getLevel() >= 0 ? node.getLevel() : Integer.MAX_VALUE;
        for (Node child : node.children.values()) {
            int childLevel = calculateMinLevel(child);
            if (childLevel >= 0 && childLevel < level) {
                level = childLevel;
            }
        }
        return level;
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
    Node getRoot() {
        return root;
    }

    /**
     * Updates the root node of the hierarchical structure to use the specified log level.
     * This method is typically used to set the default log level for all loggers in the hierarchy.
     *
     * @param level the {@code LogLevel} to assign to the root node; can be {@code null} to clear the level
     */
    public void setRootLevel(int level) {
        setNodeLevel(root, level);
    }

    /**
     * Retrieves the log level of the root node in the hierarchical structure managed by this instance of {@code LevelMap}.
     *
     * @return the {@code LogLevel} of the root node, or {@code null} if the root node does not have a defined level.
     */
    public int getRootLevel() {
        return root.getLevel();
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
    public Map<String, Integer> rules() {
        Map<String, Integer> rules = new java.util.LinkedHashMap<>();

        int rootLevel = root.getLevel();
        assert rootLevel >= 0 : ROOT_LEVEL_NOT_INITIALIZED;
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
    private static void traverseAndCollectRules(Map<String, Integer> rules, String prefix, Node node) {
        for (Map.Entry<String, Node> entry : node.children.entrySet()) {
            String loggerName = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Node childNode = entry.getValue();

            int lvl = childNode.getLevel();
            if (lvl >= 0) {
                rules.put(loggerName, lvl);
            }

            traverseAndCollectRules(rules, loggerName, childNode);
        }
    }

    public int getMinLevel() {
        return minLevel;
    }

    /**
     * Represents a single node in a hierarchical data structure, useful for storing relationships
     * between keys and associated log levels. Each node may have child nodes and an optional log level.
     * <p>
     * This class is designed to model a tree structure where each node can house multiple children
     * identified by a {@link String} key. Log levels can be dynamically assigned to nodes for
     * structured logging purposes.
     */
    public static final class Node {
        final Map<String, Node> children = new ConcurrentHashMap<>();
        volatile int level = -1;

        private Node(int level) {
            this.level = level;
        }

        /**
         * Creates a deep copy of this node and all its children.
         *
         * @return a new {@code Node} instance that is a deep copy of this one.
         */
        public Node copy() {
            Node newNode = new Node(getLevel());
            for (Map.Entry<String, Node> entry : children.entrySet()) {
                newNode.children.put(entry.getKey(), entry.getValue().copy());
            }
            return newNode;
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (!(o instanceof Node other)) return false;
            return getLevel() == other.getLevel() && children.equals(other.children);
        }

        @Override
        public int hashCode() {
            return Objects.hash(children, getLevel());
        }

        private int getLevel() {
            return level;
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
            int lvl = getLevel();
            sb.append(lvl);
            if (!children.isEmpty()) {
                for (Map.Entry<String, Node> e : children.entrySet()) {
                    sb.append(" , {").append(e.getKey()).append(" -> ");
                    e.getValue().appendTo(sb);
                    sb.append("}");
                }
            }
            return sb;
        }

        public void setLevel(int level) {
            this.level = level;
        }
    }

    /**
     * Adds or updates the log level for the specified logger name in the hierarchical structure.
     * This method ensures that the logger name is correctly mapped to the given log level, creating
     * intermediate nodes as necessary within the logger hierarchy.
     *
     * @param loggerName the hierarchical name of the logger for which the log level is being set;
     *                   must not end with a '.' character, must not be blank
     * @param level      the {@code LogLevel} to associate with the specified logger name
     * @throws IllegalArgumentException if the {@code loggerName} ends with a '.' character
     */
    public void put(String loggerName, int level) {
        if (loggerName.isBlank()) {
            throw new IllegalArgumentException("loggerName must not be blank");
        }
        if (loggerName.endsWith(".")) {
            throw new IllegalArgumentException("loggerName must not end with '.'");
        }

        Node current = root;
        String[] segments = getSegments(loggerName);
        for (String segment : segments) {
            current = current.children.computeIfAbsent(segment, k -> new Node(-1));
        }
        setNodeLevel(current, level);
    }

    /**
     * Updates the level of the specified node and adjusts the minimum level of the hierarchy if needed.
     * This method synchronizes on the root node, ensuring thread-safe updates to the node's level
     * and the `minLevel` property of the `LevelMap` hierarchy.
     *
     * @param node  the {@code Node} whose level is being updated; must not be {@code null}.
     * @param level the new log level to assign to the specified node.
     */
    private void setNodeLevel(Node node, int level) {
        synchronized (root) {
            int oldLevel = node.level;
            node.level = level;

            if (level != oldLevel) {
                if (level >= 0 && level < minLevel) {
                    minLevel = level;
                } else if (level > minLevel && oldLevel == minLevel) {
                    minLevel = calculateMinLevel(root);
                }
            }

            // consistencz check
            assert minLevel == calculateMinLevel(root);
        }
    }

    /**
     * Determines if logging is enabled for the specified logger name and log level.
     * <p>
     * The method checks whether the provided level is greater than or equal to both
     * the minimum log level of the hierarchy and the specific log level associated
     * with the given logger name.
     *
     * @param loggerName the name of the logger whose logging status is being checked;
     *                   must not be null or empty.
     * @param level the log level to compare against the thresholds; must be a non-negative integer.
     * @return true if logging is enabled for the specified logger name and level,
     *         false otherwise.
     */
    public boolean isEnabled(String loggerName, int level) {
        return level >= minLevel && level >= level(loggerName);
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
    public int level(String className) {
        Node current = root;
        int level = root.getLevel();

        String[] parts = getSegments(className);
        for (int i = 0; i < parts.length; i++) {
            String segment = parts[i];

            current = current.children.get(segment);
            if (current == null) {
                return level;
            }

            int currentLevel = current.getLevel();
            if (currentLevel >= 0) {
                level = currentLevel;
            }
        }

        return level;
    }

    /**
     * Splits the provided class name into its constituent segments based on the dot (`.`) character.
     * The method caches the result for performance optimization if the same class name is called multiple times.
     *
     * @param className the fully qualified class name to be split into segments; must not be {@code null}.
     * @return an array of strings representing the segments of the class name, split by the dot (`.`) character.
     */
    private static String[] getSegments(String className) {
        // do not use computeIfAbsent here, this is a hot path!
        String[] strings = loggerNameCache.get(className);
        if (strings != null) {
            return strings;
        }

        strings = Util.splitOnDot(className);
        loggerNameCache.put(className, strings);
        return strings;
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