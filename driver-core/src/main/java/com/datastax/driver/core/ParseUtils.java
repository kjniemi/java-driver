/*
 *      Copyright (C) 2012-2015 DataStax Inc.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.datastax.driver.core;

/**
 * Simple utility method used to help parsing CQL values (mainly UDT and collection ones).
 */
abstract class ParseUtils {

    private ParseUtils() {
    }

    /**
     * Returns the index of the first character in toParse from idx that is not a "space".
     *
     * @param toParse the string to skip space on.
     * @param idx     the index to start skipping space from.
     * @return the index of the first character in toParse from idx that is not a "space.
     */
    public static int skipSpaces(String toParse, int idx) {
        while (isBlank(toParse.charAt(idx)) && idx < toParse.length())
            ++idx;
        return idx;
    }

    /**
     * Assuming that idx points to the beginning of a CQL value in toParse, returns the
     * index of the first character after this value.
     *
     * @param toParse the string to skip a value form.
     * @param idx     the index to start parsing a value from.
     * @return the index ending the CQL value starting at {@code idx}.
     * @throws IllegalArgumentException if idx doesn't point to the start of a valid CQL
     *                                  value.
     */
    public static int skipCQLValue(String toParse, int idx) {
        if (idx >= toParse.length())
            throw new IllegalArgumentException();

        if (isBlank(toParse.charAt(idx)))
            throw new IllegalArgumentException();

        int cbrackets = 0;
        int sbrackets = 0;
        int parens = 0;
        boolean inString = false;

        do {
            char c = toParse.charAt(idx);
            if (inString) {
                if (c == '\'') {
                    if (idx + 1 < toParse.length() && toParse.charAt(idx + 1) == '\'') {
                        ++idx; // this is an escaped quote, skip it
                    } else {
                        inString = false;
                        if (cbrackets == 0 && sbrackets == 0 && parens == 0)
                            return idx + 1;
                    }
                }
                // Skip any other character
            } else if (c == '\'') {
                inString = true;
            } else if (c == '{') {
                ++cbrackets;
            } else if (c == '[') {
                ++sbrackets;
            } else if (c == '(') {
                ++parens;
            } else if (c == '}') {
                if (cbrackets == 0)
                    return idx;

                --cbrackets;
                if (cbrackets == 0 && sbrackets == 0 && parens == 0)
                    return idx + 1;
            } else if (c == ']') {
                if (sbrackets == 0)
                    return idx;

                --sbrackets;
                if (cbrackets == 0 && sbrackets == 0 && parens == 0)
                    return idx + 1;
            } else if (c == ')') {
                if (parens == 0)
                    return idx;

                --parens;
                if (cbrackets == 0 && sbrackets == 0 && parens == 0)
                    return idx + 1;
            } else if (isBlank(c) || !isIdentifierChar(c)) {
                if (cbrackets == 0 && sbrackets == 0 && parens == 0)
                    return idx;
            }
        } while (++idx < toParse.length());

        if (inString || cbrackets != 0 || sbrackets != 0 || parens != 0)
            throw new IllegalArgumentException();
        return idx;
    }

    /**
     * Assuming that idx points to the beginning of a CQL identifier in toParse, returns the
     * index of the first character after this identifier.
     *
     * @param toParse the string to skip an identifier from.
     * @param idx     the index to start parsing an identifier from.
     * @return the index ending the CQL identifier starting at {@code idx}.
     * @throws IllegalArgumentException if idx doesn't point to the start of a valid CQL
     *                                  identifier.
     */
    public static int skipCQLId(String toParse, int idx) {
        if (idx >= toParse.length())
            throw new IllegalArgumentException();

        char c = toParse.charAt(idx);
        if (isIdentifierChar(c)) {
            while (idx < toParse.length() && isIdentifierChar(toParse.charAt(idx)))
                idx++;
            return idx;
        }

        if (c != '"')
            throw new IllegalArgumentException();

        while (++idx < toParse.length()) {
            c = toParse.charAt(idx);
            if (c != '"')
                continue;

            if (idx + 1 < toParse.length() && toParse.charAt(idx + 1) == '\"')
                ++idx; // this is an escaped double quote, skip it
            else
                return idx + 1;
        }
        throw new IllegalArgumentException();
    }

    // [0..9a..zA..Z-+._&]
    public static boolean isIdentifierChar(int c) {
        return (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                || c == '-' || c == '+' || c == '.' || c == '_' || c == '&';
    }

    // [ \t\n]
    public static boolean isBlank(int c) {
        return c == ' ' || c == '\t' || c == '\n';
    }

    /**
     * Return {@code true} if the given string is surrounded
     * by single quotes, and {@code false} otherwise.
     *
     * @param value The string to inspect.
     * @return {@code true} if the given string is surrounded
     * by single quotes, and {@code false} otherwise.
     */
    public static boolean isQuoted(String value) {
        return value != null && value.length() > 1 && value.charAt(0) == '\'' && value.charAt(value.length() - 1) == '\'';
    }

    /**
     * Quote the given string; single quotes are escaped.
     * If the given string is null, this method returns a quoted empty string ({@code ''}).
     *
     * @param value The value to quote.
     * @return The quoted string.
     */
    public static String quote(String value) {
        return '\'' + replaceChar(value, '\'', "''") + '\'';
    }

    /**
     * Unquote the given string if it is quoted; single quotes are unescaped.
     * If the given string is not quoted, it is returned without any modification.
     *
     * @param value The string to unquote.
     * @return The unquoted string.
     */
    public static String unquote(String value) {
        if (!isQuoted(value))
            return value;
        return value.substring(1, value.length() - 1).replace("''", "'");
    }

    // Simple method to replace a single character. String.replace is a bit too
    // inefficient (see JAVA-67)
    static String replaceChar(String text, char search, String replacement) {
        if (text == null || text.isEmpty())
            return text;

        int nbMatch = 0;
        int start = -1;
        do {
            start = text.indexOf(search, start + 1);
            if (start != -1)
                ++nbMatch;
        } while (start != -1);

        if (nbMatch == 0)
            return text;

        int newLength = text.length() + nbMatch * (replacement.length() - 1);
        char[] result = new char[newLength];
        int newIdx = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == search) {
                for (int r = 0; r < replacement.length(); r++)
                    result[newIdx++] = replacement.charAt(r);
            } else {
                result[newIdx++] = c;
            }
        }
        return new String(result);
    }
}
