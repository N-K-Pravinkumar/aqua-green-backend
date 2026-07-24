package com.aquagreen.controller;

import com.aquagreen.dto.ApiResponse;
import com.aquagreen.repository.SaleRepository;
import com.aquagreen.repository.ServiceRequestRepository;
import com.aquagreen.repository.StockItemRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Part-replacement maintenance reminders.
 *
 * Rather than a separate table that technicians have to remember to fill in,
 * this reuses the spare-parts already logged against each completed
 * ServiceRequest (sparePartsJson, e.g. [{"name":"Carbon Filter","qty":1,...}]).
 * For any part name, we can work out the most recent replacement date per
 * customer and flag anyone whose last replacement is older than a chosen
 * interval (3 / 6 / 12 months, or any custom number).
 *
 * IMPORTANT: both endpoints here use lightweight column projections
 * (findCompletedWithPartsProjection / findDistinctProductNames) instead of
 * loading full entities. ServiceRequest.customer and Sale.customer are both
 * EAGER-fetched relations — loading full entities via findAll() would
 * trigger one extra DB round-trip PER ROW just to fetch each customer,
 * which is exactly what caused this to time out once the tables grew past
 * a few hundred rows.
 */
@RestController @RequestMapping("/api/maintenance") @RequiredArgsConstructor
public class MaintenanceController {

    private final ServiceRequestRepository repo;
    private final SaleRepository saleRepo;
    private final StockItemRepository stockRepo;
    private final ObjectMapper mapper;

    /**
     * Suggestion list for the filter dropdown — merges part names actually
     * logged against completed services with your real catalog (sold
     * products + spares/stock). The filter box still accepts free typing
     * beyond this list too.
     */
    @GetMapping("/parts")
    public ResponseEntity<ApiResponse<List<String>>> partsUsed() {
        TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        for (Object[] row : repo.findCompletedWithPartsProjection()) {
            String sparePartsJson = (String) row[4];
            for (Map<String,Object> p : parseParts(sparePartsJson)) {
                Object n = p.get("name");
                if (n != null && !n.toString().isBlank()) names.add(n.toString().trim());
            }
        }
        for (String productName : saleRepo.findDistinctProductNames()) {
            names.add(productName.trim());
        }
        for (var stock : stockRepo.findAll()) {
            if (stock.getName() != null && !stock.getName().isBlank()) names.add(stock.getName().trim());
        }

        return ResponseEntity.ok(ApiResponse.success("OK", new ArrayList<>(names)));
    }

    /**
     * Customers overdue for a given part replacement.
     * "Overdue" = their most recent logged replacement of a part matching
     * partName (case-insensitive, partial match) is older than `months`.
     * Customers who have never had that part replaced are intentionally
     * excluded — a reminder only makes sense once we know they have the part.
     */
    @GetMapping("/overdue")
    public ResponseEntity<ApiResponse<List<Map<String,Object>>>> overdue(
            @RequestParam String partName,
            @RequestParam(required = false) Integer days,
            @RequestParam(defaultValue = "6") int months) {

        // `days` is more precise (e.g. exactly 90 days) — if given, it wins.
        LocalDateTime cutoff = (days != null) ? LocalDateTime.now().minusDays(days) : LocalDateTime.now().minusMonths(months);
        String needle = partName.trim().toLowerCase();

        List<Object[]> rows = repo.findCompletedWithPartsProjection();

        // customerMobile (digits only) -> most recent matching row
        Map<String, Object[]> latestByCustomer = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String customerMobile = (String) row[0];
            String sparePartsJson = (String) row[4];
            LocalDateTime completedAt = (LocalDateTime) row[5];
            if (completedAt == null || customerMobile == null) continue;

            boolean matches = parseParts(sparePartsJson).stream()
                .anyMatch(p -> p.get("name") != null && p.get("name").toString().toLowerCase().contains(needle));
            if (!matches) continue;

            String key = customerMobile.replaceAll("[^0-9]", "");
            if (key.isEmpty()) continue;
            Object[] existing = latestByCustomer.get(key);
            if (existing == null || completedAt.isAfter((LocalDateTime) existing[5])) {
                latestByCustomer.put(key, row);
            }
        }

        List<Map<String,Object>> result = new ArrayList<>();
        for (Object[] row : latestByCustomer.values()) {
            LocalDateTime completedAt = (LocalDateTime) row[5];
            if (completedAt.isBefore(cutoff)) {
                Map<String,Object> out = new LinkedHashMap<>();
                out.put("customerMobile", row[0]);
                out.put("customerName", row[1]);
                out.put("customerAddress", row[2]);
                out.put("productName", row[3]);
                out.put("lastReplacedDate", completedAt);
                out.put("daysSinceReplaced", ChronoUnit.DAYS.between(completedAt, LocalDateTime.now()));
                out.put("ticketNumber", row[6]);
                out.put("customerId", row[7]);
                result.add(out);
            }
        }
        result.sort((a, b) -> ((LocalDateTime) a.get("lastReplacedDate")).compareTo((LocalDateTime) b.get("lastReplacedDate")));

        return ResponseEntity.ok(ApiResponse.success("Found " + result.size() + " overdue", result));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String,Object>> parseParts(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try { return mapper.readValue(json, List.class); }
        catch (Exception e) { return Collections.emptyList(); }
    }
}
