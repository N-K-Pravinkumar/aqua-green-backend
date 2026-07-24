package com.aquagreen.controller;

import com.aquagreen.dto.ApiResponse;
import com.aquagreen.model.ServiceRequest;
import com.aquagreen.repository.ServiceRequestRepository;
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
 */
@RestController @RequestMapping("/api/maintenance") @RequiredArgsConstructor
public class MaintenanceController {

    private final ServiceRequestRepository repo;
    private final ObjectMapper mapper;

    /** Distinct part names ever logged, so the UI can offer a suggestion dropdown. */
    @GetMapping("/parts")
    public ResponseEntity<ApiResponse<List<String>>> partsUsed() {
        List<ServiceRequest> completed = repo.findByStatusAndSparePartsJsonIsNotNullOrderByCompletedAtDesc("COMPLETED");
        TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (ServiceRequest sr : completed) {
            for (Map<String,Object> p : parseParts(sr.getSparePartsJson())) {
                Object n = p.get("name");
                if (n != null && !n.toString().isBlank()) names.add(n.toString().trim());
            }
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
        // Falls back to `months` for anything still calling the old way.
        LocalDateTime cutoff = (days != null) ? LocalDateTime.now().minusDays(days) : LocalDateTime.now().minusMonths(months);
        String needle = partName.trim().toLowerCase();

        List<ServiceRequest> completed = repo.findByStatusAndSparePartsJsonIsNotNullOrderByCompletedAtDesc("COMPLETED");

        // customerMobile (digits only) -> most recent ServiceRequest that replaced this part
        Map<String, ServiceRequest> latestByCustomer = new LinkedHashMap<>();
        for (ServiceRequest sr : completed) {
            if (sr.getCompletedAt() == null || sr.getCustomerMobile() == null) continue;
            boolean matches = parseParts(sr.getSparePartsJson()).stream()
                .anyMatch(p -> p.get("name") != null && p.get("name").toString().toLowerCase().contains(needle));
            if (!matches) continue;
            String key = sr.getCustomerMobile().replaceAll("[^0-9]", "");
            if (key.isEmpty()) continue;
            ServiceRequest existing = latestByCustomer.get(key);
            if (existing == null || sr.getCompletedAt().isAfter(existing.getCompletedAt())) {
                latestByCustomer.put(key, sr);
            }
        }

        List<Map<String,Object>> result = new ArrayList<>();
        for (ServiceRequest sr : latestByCustomer.values()) {
            if (sr.getCompletedAt().isBefore(cutoff)) {
                Map<String,Object> row = new LinkedHashMap<>();
                row.put("customerId", sr.getCustomer() != null ? sr.getCustomer().getId() : null);
                row.put("customerName", sr.getCustomerName());
                row.put("customerMobile", sr.getCustomerMobile());
                row.put("customerAddress", sr.getCustomerAddress());
                row.put("productName", sr.getProductName());
                row.put("lastReplacedDate", sr.getCompletedAt());
                row.put("daysSinceReplaced", ChronoUnit.DAYS.between(sr.getCompletedAt(), LocalDateTime.now()));
                row.put("ticketNumber", sr.getTicketNumber());
                result.add(row);
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
