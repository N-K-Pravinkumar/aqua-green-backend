package com.aquagreen.util;

/**
 * Single source of truth for normalizing Indian mobile numbers before they're
 * stored or compared. Without this, the same customer can end up matched
 * inconsistently across Leads, Enquiries, Sales, Service Requests and
 * Quotations just because one form stored "+91 95244 56346" and another
 * stored "9524456346" — they're the same number but wouldn't match on a
 * naive string comparison.
 *
 * Rule: strip everything but digits, then keep only the last 10 digits
 * (drops a leading 0 or a 91/+91 country code, however it was typed).
 */
public final class MobileUtil {
    private MobileUtil() {}

    public static String normalize(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return "";
        return digits.length() > 10 ? digits.substring(digits.length() - 10) : digits;
    }
}
