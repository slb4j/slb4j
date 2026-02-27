package org.slb4j.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Tests for the Util class's method splitOnCahr.
 */
class UtilTest {

    /**
     * Test that a string with multiple occurrences of the split character is split correctly.
     */
    @Test
    void testSplitOnDotWithMultipleOccurrences() {
        String input = "a.b.c";
        String[] expected = {"a", "b", "c"};

        assertArrayEquals(expected, Util.splitOnDot(input));
    }

    /**
     * Test that a string without the split character returns the original string wrapped in an array.
     */
    @Test
    void testSplitOnDotWithoutDelimiter() {
        String input = "abc";
        String[] expected = {"abc"};

        assertArrayEquals(expected, Util.splitOnDot(input));
    }

    /**
     * Test that an empty string returns an empty array.
     */
    @Test
    void testSplitOnDotWithEmptyString() {
        String input = "";
        String[] expected = Util.EMPTY_STRING_ARRAY;

        assertArrayEquals(expected, Util.splitOnDot(input));
    }

    /**
     * Test a string containing leading and trailing delimiters.
     */
    @Test
    void testSplitOnDotWithLeadingAndTrailingDelimiters() {
        String input = ".a.b.c.";
        String[] expected = {"", "a", "b", "c", ""};

        assertArrayEquals(expected, Util.splitOnDot(input));
    }

    /**
     * Test a string containing only delimiters.
     */
    @Test
    void testSplitOnDotWithOnlyDelimiters() {
        String input = "...";
        String[] expected = {"", "", "", ""};

        assertArrayEquals(expected, Util.splitOnDot(input));
    }
}