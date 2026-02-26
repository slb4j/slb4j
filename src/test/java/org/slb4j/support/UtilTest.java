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
    void testSplitOnCharWithMultipleOccurrences() {
        String input = "a,b,c";
        char delimiter = ',';
        String[] expected = {"a", "b", "c"};

        assertArrayEquals(expected, Util.splitOnChar(input, delimiter));
    }

    /**
     * Test that a string without the split character returns the original string wrapped in an array.
     */
    @Test
    void testSplitOnCharWithoutDelimiter() {
        String input = "abc";
        char delimiter = ',';
        String[] expected = {"abc"};

        assertArrayEquals(expected, Util.splitOnChar(input, delimiter));
    }

    /**
     * Test that an empty string returns an empty array.
     */
    @Test
    void testSplitOnCharWithEmptyString() {
        String input = "";
        char delimiter = ',';
        String[] expected = Util.EMPTY_STRING_ARRAY;

        assertArrayEquals(expected, Util.splitOnChar(input, delimiter));
    }

    /**
     * Test a string containing leading and trailing delimiters.
     */
    @Test
    void testSplitOnCharWithLeadingAndTrailingDelimiters() {
        String input = ",a,b,c,";
        char delimiter = ',';
        String[] expected = {"", "a", "b", "c", ""};

        assertArrayEquals(expected, Util.splitOnChar(input, delimiter));
    }

    /**
     * Test a string containing only delimiters.
     */
    @Test
    void testSplitOnCharWithOnlyDelimiters() {
        String input = ",,,";
        char delimiter = ',';
        String[] expected = {"", "", "", ""};

        assertArrayEquals(expected, Util.splitOnChar(input, delimiter));
    }
}