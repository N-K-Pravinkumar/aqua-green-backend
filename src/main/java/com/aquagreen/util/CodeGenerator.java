package com.aquagreen.util;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates sequential, human-readable codes like AGA001, SALE001, SERV001.
 *
 * Deliberately does NOT find "the current highest code" via a SQL
 * `ORDER BY code DESC LIMIT 1` — string sorting breaks once codes cross a
 * digit-width boundary (e.g. "AGA999" sorts *after* "AGA1000" as plain
 * text, because '9' > '1'). Instead, callers pass in every existing code
 * for the prefix and this parses each one numerically to find the true max.
 * For a few thousand rows that's a trivial in-memory scan.
 */
public final class CodeGenerator {
    private CodeGenerator() {}

    /**
     * @param prefix   e.g. "AGA", "SALE", "SERV"
     * @param existing every existing code string for this prefix (nulls ignored)
     * @param minDigits minimum zero-padded width (numbers beyond it just grow naturally, never truncated)
     */
    public static String next(String prefix, List<String> existing, int minDigits) {
        int max = 0;
        Pattern p = Pattern.compile("^" + Pattern.quote(prefix) + "(\\d+)$");
        if (existing != null) {
            for (String code : existing) {
                if (code == null) continue;
                Matcher m = p.matcher(code.trim());
                if (m.matches()) {
                    try {
                        int n = Integer.parseInt(m.group(1));
                        if (n > max) max = n;
                    } catch (NumberFormatException ignore) { /* skip malformed */ }
                }
            }
        }
        int next = max + 1;
        return prefix + String.format("%0" + minDigits + "d", next);
    }
}
