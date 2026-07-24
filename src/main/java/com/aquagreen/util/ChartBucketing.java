package com.aquagreen.util;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/**
 * Buckets a list of [dateTime, amount] rows into weekly or monthly totals
 * for bar-chart display. Shared between Sales and Service Requests so both
 * analytics charts behave identically.
 */
public final class ChartBucketing {
    private ChartBucketing() {}

    private static final DateTimeFormatter WEEK_LABEL = DateTimeFormatter.ofPattern("d MMM");
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yyyy");

    /** How far back to fetch rows for, given the requested bucket count. */
    public static LocalDateTime since(String granularity, int count) {
        LocalDate today = LocalDate.now();
        if ("month".equalsIgnoreCase(granularity)) {
            return today.withDayOfMonth(1).minusMonths(count - 1L).atStartOfDay();
        }
        LocalDate mondayThisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return mondayThisWeek.minusWeeks(count - 1L).atStartOfDay();
    }

    public static List<Map<String,Object>> bucket(List<Object[]> rows, String granularity, int count) {
        boolean monthly = "month".equalsIgnoreCase(granularity);
        LinkedHashMap<String, BigDecimal> totals = new LinkedHashMap<>();
        LinkedHashMap<String, String> labels = new LinkedHashMap<>();

        // Pre-seed every bucket with zero, so empty weeks/months still show on the chart
        LocalDate today = LocalDate.now();
        for (int i = count - 1; i >= 0; i--) {
            String key; String label;
            if (monthly) {
                LocalDate m = today.withDayOfMonth(1).minusMonths(i);
                key = m.toString().substring(0, 7);
                label = m.format(MONTH_LABEL);
            } else {
                LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(i);
                key = weekStart.toString();
                label = weekStart.format(WEEK_LABEL);
            }
            totals.put(key, BigDecimal.ZERO);
            labels.put(key, label);
        }

        for (Object[] row : rows) {
            LocalDateTime dt = (LocalDateTime) row[0];
            BigDecimal amount = row[1] != null ? (BigDecimal) row[1] : BigDecimal.ZERO;
            if (dt == null) continue;
            LocalDate d = dt.toLocalDate();
            String key = monthly
                ? d.toString().substring(0, 7)
                : d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();
            totals.merge(key, amount, BigDecimal::add);
        }

        List<Map<String,Object>> result = new ArrayList<>();
        for (String key : totals.keySet()) {
            Map<String,Object> point = new LinkedHashMap<>();
            point.put("label", labels.get(key));
            point.put("revenue", totals.get(key));
            result.add(point);
        }
        return result;
    }
}
