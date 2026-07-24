package com.aquagreen.controller;

import com.aquagreen.dto.ApiResponse;
import com.aquagreen.model.Customer;
import com.aquagreen.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {
    private final CustomerRepository repo;
    private final LeadRepository leadRepo;
    private final EnquiryRepository enquiryRepo;
    private final ServiceRequestRepository serviceRequestRepo;
    private final SaleRepository saleRepo;
    private final QuotationRepository quotationRepo;
    private final CustomerPhoneRepository phoneRepo;
    private final OperationHistoryRepository historyRepo;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<Customer>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable p = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success("OK", repo.findByActiveTrueOrderByCreatedAtDesc(p)));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<Customer>>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable p = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success("OK",
                repo.findByNameContainingIgnoreCaseOrMobileContaining(q, q, p)));
    }

    @GetMapping("/lookup")
    public ResponseEntity<ApiResponse<Customer>> lookupByMobile(@RequestParam String mobile) {
        Customer c = repo.findByMobile(mobile.trim()).orElse(null);
        return ResponseEntity.ok(ApiResponse.success(c != null ? "Found" : "Not found", c));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Customer>> getById(@PathVariable Long id) {
        return repo.findById(id)
                .map(c -> ResponseEntity.ok(ApiResponse.success("OK", c)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Customer>> create(@RequestBody Customer c) {
        c.setId(null);
        String norm = com.aquagreen.util.MobileUtil.normalize(c.getMobile());
        if (norm != null && !norm.isEmpty()) {
            Optional<Customer> existing = repo.findByMobile(norm);
            if (existing.isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error(
                    "A customer with this mobile number already exists: " + existing.get().getName() +
                    " (ID " + existing.get().getId() + "). Edit that record instead of creating a duplicate."));
            }
        }
        if (c.getCustomerCode() == null || c.getCustomerCode().isBlank()) {
            c.setCustomerCode(com.aquagreen.util.CodeGenerator.next("AGA", repo.findAllCustomerCodes(), 3));
        }
        return ResponseEntity.ok(ApiResponse.success("Customer created", repo.save(c)));
    }

    /** Additional phone numbers for a customer (beyond the primary one on the record itself). */
    @GetMapping("/{id}/phones")
    public ResponseEntity<ApiResponse<List<com.aquagreen.model.CustomerPhone>>> getPhones(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("OK", phoneRepo.findByCustomerId(id)));
    }

    /**
     * Log that a WhatsApp message (bill, reminder, template, etc.) was sent
     * to a customer, so it shows up in their History timeline. Best-effort —
     * the actual send already happened client-side (opening wa.me); this
     * just records that it happened.
     */
    @PostMapping("/log-message")
    public ResponseEntity<ApiResponse<Void>> logMessage(@RequestBody Map<String,Object> body) {
        Long customerId = body.get("customerId") != null ? Long.valueOf(body.get("customerId").toString()) : null;
        String channel = String.valueOf(body.getOrDefault("channel", "WHATSAPP"));
        String context = String.valueOf(body.getOrDefault("context", ""));
        Customer c = customerId != null ? repo.findById(customerId).orElse(null) : null;

        com.aquagreen.model.OperationHistory h = com.aquagreen.model.OperationHistory.builder()
            .action(channel + "_MESSAGE_SENT")
            .entityType("Customer")
            .entityId(customerId)
            .entityName(c != null ? c.getName() : null)
            .customer(c)
            .remarks(context)
            .build();
        historyRepo.save(h);
        return ResponseEntity.ok(ApiResponse.success("Logged", null));
    }

    @PostMapping("/{id}/phones")
    public ResponseEntity<ApiResponse<com.aquagreen.model.CustomerPhone>> addPhone(
            @PathVariable Long id, @RequestBody Map<String,String> body) {
        String norm = com.aquagreen.util.MobileUtil.normalize(body.get("phone"));
        if (norm == null || norm.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Phone number required"));
        }
        String primaryNorm = repo.findById(id).map(c -> com.aquagreen.util.MobileUtil.normalize(c.getMobile())).orElse(null);
        if (norm.equals(primaryNorm) || phoneRepo.existsByPhone(norm) || repo.findByMobile(norm).isPresent()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("This phone number is already on file for a customer"));
        }
        Customer c = repo.findById(id).orElseThrow(() -> new RuntimeException("Customer not found"));
        com.aquagreen.model.CustomerPhone phone = com.aquagreen.model.CustomerPhone.builder()
            .customer(c).phone(norm).label(body.getOrDefault("label", "Alternate")).build();
        return ResponseEntity.ok(ApiResponse.success("Phone added", phoneRepo.save(phone)));
    }

    @DeleteMapping("/phones/{phoneId}")
    public ResponseEntity<ApiResponse<Void>> deletePhone(@PathVariable Long phoneId) {
        phoneRepo.deleteById(phoneId);
        return ResponseEntity.ok(ApiResponse.success("Removed", null));
    }


    /**
     * One-time backfill: assigns AGA###/SALE###/SERV### codes to every
     * existing customer, sale, and service request that doesn't have one
     * yet (in ID order, so the sequence reads oldest-to-newest). Safe to
     * call more than once — anything that already has a code is skipped.
     */
    @PostMapping("/assign-codes")
    public ResponseEntity<ApiResponse<Map<String,Object>>> assignCodes() {
        int customersCoded = 0, salesCoded = 0, servicesCoded = 0;

        List<Customer> customersMissing = repo.findByCustomerCodeIsNullOrderByIdAsc();
        List<String> existingCustomerCodes = new ArrayList<>(repo.findAllCustomerCodes());
        for (Customer c : customersMissing) {
            String code = com.aquagreen.util.CodeGenerator.next("AGA", existingCustomerCodes, 3);
            c.setCustomerCode(code);
            repo.save(c);
            existingCustomerCodes.add(code);
            customersCoded++;
        }

        List<com.aquagreen.model.Sale> salesMissing = saleRepo.findBySaleCodeIsNullOrderByIdAsc();
        List<String> existingSaleCodes = new ArrayList<>(saleRepo.findAllSaleCodes());
        for (com.aquagreen.model.Sale s : salesMissing) {
            String code = com.aquagreen.util.CodeGenerator.next("SALE", existingSaleCodes, 3);
            s.setSaleCode(code);
            saleRepo.save(s);
            existingSaleCodes.add(code);
            salesCoded++;
        }

        List<com.aquagreen.model.ServiceRequest> servicesMissing = serviceRequestRepo.findByServiceCodeIsNullOrderByIdAsc();
        List<String> existingServiceCodes = new ArrayList<>(serviceRequestRepo.findAllServiceCodes());
        for (com.aquagreen.model.ServiceRequest sr : servicesMissing) {
            String code = com.aquagreen.util.CodeGenerator.next("SERV", existingServiceCodes, 3);
            sr.setServiceCode(code);
            serviceRequestRepo.save(sr);
            existingServiceCodes.add(code);
            servicesCoded++;
        }

        Map<String,Object> result = new LinkedHashMap<>();
        result.put("customersCoded", customersCoded);
        result.put("salesCoded", salesCoded);
        result.put("servicesCoded", servicesCoded);
        return ResponseEntity.ok(ApiResponse.success(
            customersCoded + " customer(s), " + salesCoded + " sale(s), " + servicesCoded + " service(s) coded", result));
    }

    /**
     * One-time cleanup: find customers that share the same normalized mobile
     * number (duplicates created before this uniqueness check existed),
     * merge each group into a single customer, and reassign every Sale /
     * ServiceRequest / Quotation that pointed at a duplicate over to the
     * kept record. Safe to call more than once — a second run will find
     * nothing left to merge.
     */
    @PostMapping("/merge-duplicates")
    public ResponseEntity<ApiResponse<Map<String,Object>>> mergeDuplicates() {
        List<Customer> all = repo.findAll();
        Map<String, List<Customer>> byMobile = new LinkedHashMap<>();
        for (Customer c : all) {
            String norm = com.aquagreen.util.MobileUtil.normalize(c.getMobile());
            if (norm == null || norm.isEmpty()) continue;
            byMobile.computeIfAbsent(norm, k -> new ArrayList<>()).add(c);
        }

        int groupsMerged = 0, customersDeleted = 0, recordsReassigned = 0;
        List<String> details = new ArrayList<>();

        for (Map.Entry<String, List<Customer>> entry : byMobile.entrySet()) {
            List<Customer> group = entry.getValue();
            if (group.size() < 2) continue;

            // Keep the oldest record (earliest createdAt); fall back to lowest id if createdAt ties/nulls
            group.sort((a, b) -> {
                if (a.getCreatedAt() != null && b.getCreatedAt() != null) return a.getCreatedAt().compareTo(b.getCreatedAt());
                return Long.compare(a.getId(), b.getId());
            });
            Customer keep = group.get(0);
            List<Customer> dupes = group.subList(1, group.size());

            // Fill in anything the kept record is missing, from the duplicates
            for (Customer d : dupes) {
                if ((keep.getEmail()==null || keep.getEmail().isBlank()) && d.getEmail()!=null) keep.setEmail(d.getEmail());
                if ((keep.getAddress()==null || keep.getAddress().isBlank()) && d.getAddress()!=null) keep.setAddress(d.getAddress());
                if ((keep.getCity()==null || keep.getCity().isBlank()) && d.getCity()!=null) keep.setCity(d.getCity());
                if ((keep.getGstNumber()==null || keep.getGstNumber().isBlank()) && d.getGstNumber()!=null) keep.setGstNumber(d.getGstNumber());
            }
            repo.save(keep);

            for (Customer d : dupes) {
                int n = 0;
                for (var sr : serviceRequestRepo.findAll()) {
                    if (sr.getCustomer() != null && sr.getCustomer().getId().equals(d.getId())) { sr.setCustomer(keep); serviceRequestRepo.save(sr); n++; }
                }
                for (var s : saleRepo.findAll()) {
                    if (s.getCustomer() != null && s.getCustomer().getId().equals(d.getId())) { s.setCustomer(keep); saleRepo.save(s); n++; }
                }
                for (var q : quotationRepo.findAll()) {
                    if (q.getCustomer() != null && q.getCustomer().getId().equals(d.getId())) { q.setCustomer(keep); quotationRepo.save(q); n++; }
                }
                recordsReassigned += n;
                details.add("Merged customer #" + d.getId() + " (" + d.getName() + ") into #" + keep.getId() + " (" + keep.getName() + "), reassigned " + n + " record(s)");
                repo.delete(d);
                customersDeleted++;
            }
            groupsMerged++;
        }

        Map<String,Object> result = new LinkedHashMap<>();
        result.put("groupsMerged", groupsMerged);
        result.put("customersDeleted", customersDeleted);
        result.put("recordsReassigned", recordsReassigned);
        result.put("details", details);
        return ResponseEntity.ok(ApiResponse.success(
            groupsMerged + " duplicate group(s) merged, " + customersDeleted + " duplicate customer(s) removed", result));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Customer>> update(@PathVariable Long id, @RequestBody Customer c) {
        c.setId(id);
        return ResponseEntity.ok(ApiResponse.success("Updated", repo.save(c)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        repo.findById(id).ifPresent(c -> {
            c.setActive(false);
            repo.save(c);
        });
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }

    /**
     * Full customer timeline — leads, enquiries, service requests, sales,
     * quotations.
     *
     * IMPORTANT: this must filter with a WHERE clause at the database level,
     * not load entire tables and filter in Java. This endpoint previously did
     * findAllByOrderByCreatedAtDesc() (no filter) for all five tables, then
     * filtered in memory — which (a) pulls every row over the network from
     * Supabase on every single call, and (b) triggers Hibernate's eager
     * fetch of each row's linked Customer one-by-one (N+1), multiplying into
     * hundreds of extra round-trips. Combined with Supabase being in a
     * different region than the Railway app, that added up to 14+ second
     * responses and client-side timeouts. Targeted findByMobile queries
     * avoid both problems.
     */
    @GetMapping("/{id}/timeline")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTimeline(@PathVariable Long id) {
        Customer c = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        String mobileNorm = com.aquagreen.util.MobileUtil.normalize(c.getMobile());
        final String mobile = mobileNorm != null ? mobileNorm : "";

        Map<String, Object> timeline = new LinkedHashMap<>();
        timeline.put("leads",           mobile.isEmpty() ? List.of() : leadRepo.findByMobileOrderByCreatedAtDesc(mobile));
        timeline.put("enquiries",       mobile.isEmpty() ? List.of() : enquiryRepo.findByMobileOrderByCreatedAtDesc(mobile));
        timeline.put("messages",        historyRepo.findByCustomerIdOrderByCreatedAtDesc(id));
        timeline.put("serviceRequests", mobile.isEmpty() ? List.of() : serviceRequestRepo.findByCustomerMobileOrderByCreatedAtDesc(mobile));
        timeline.put("sales",           mobile.isEmpty() ? List.of() : saleRepo.findByCustomerMobileOrderByCreatedAtDesc(mobile));
        timeline.put("quotations",      mobile.isEmpty() ? List.of() : quotationRepo.findByCustomerMobileOrderByCreatedAtDesc(mobile));

        return ResponseEntity.ok(ApiResponse.success("OK", timeline));
    }
}
