package com.aquagreen.controller;

import com.aquagreen.dto.ApiResponse;
import com.aquagreen.model.*;
import com.aquagreen.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * One-time cleanup for the demo/seed data that DataSeeder and
 * MasterSeedConfig auto-generate on first boot (roughly 500 fake
 * customers, 200 fake leads, 500 fake sales, 1000 fake service requests,
 * 500 fake payments — all Tamil-name-pool synthetic data).
 *
 * This does NOT guess which records are fake. Every filter below matches
 * an exact, deterministic pattern that only the seeders produce and real
 * data effectively never would by coincidence:
 *   - Seed customers/leads: email is exactly "firstname.lastnameN@gmail.com"
 *     (no space, a trailing sequence number glued straight onto the local
 *     part) AND mobile falls in the synthetic-generation range.
 *   - Seed sales: invoiceNumber is exactly "AQG-INV-0001".."AQG-INV-0500"
 *     — real sales get "INV-<date>-<random4>" instead (different format).
 *   - Seed service requests: ticketNumber is exactly "SRV-00001".."SRV-01000"
 *     — real tickets get "TKT-<date>-<random>" instead.
 *   - Seed payments: paymentNumber starts with "AQG-PAY-".
 * Safe to preview first via GET before actually deleting via POST.
 */
@RestController @RequestMapping("/api/admin/seed-cleanup") @RequiredArgsConstructor @Slf4j
public class SeedDataCleanupController {

    private final CustomerRepository customerRepo;
    private final LeadRepository leadRepo;
    private final SaleRepository saleRepo;
    private final ServiceRequestRepository serviceRequestRepo;
    private final PaymentRepository paymentRepo;
    private final OperationHistoryRepository historyRepo;
    private final QuotationRepository quotationRepo;
    private final EnquiryRepository enquiryRepo;

    private static final Pattern FAKE_EMAIL = Pattern.compile("^[a-z]+\\.[a-z]+\\d+@gmail\\.com$");
    private static final Pattern FAKE_CUSTOMER_MOBILE = Pattern.compile("^9[78]\\d{8}$");
    private static final Pattern FAKE_LEAD_MOBILE = Pattern.compile("^8[78]\\d{8}$");
    private static final Pattern FAKE_SALE_INVOICE = Pattern.compile("^AQG-INV-\\d+$");
    private static final Pattern FAKE_TICKET = Pattern.compile("^SRV-\\d{5}$");
    private static final Pattern FAKE_QUOTATION = Pattern.compile("^AQG-QT-\\d+$");
    // DataSeeder's small fixed set of demo enquiries always used one of these exact numbers
    private static final Set<String> FAKE_ENQUIRY_MOBILES = Set.of("9876543210","9876500001","7654300003","9765432100");

    private List<Customer> findFakeCustomers() {
        return customerRepo.findAll().stream()
            .filter(c -> c.getEmail() != null && FAKE_EMAIL.matcher(c.getEmail()).matches()
                      && c.getMobile() != null && FAKE_CUSTOMER_MOBILE.matcher(c.getMobile()).matches())
            .collect(Collectors.toList());
    }
    private List<Lead> findFakeLeads() {
        return leadRepo.findAll().stream()
            .filter(l -> l.getEmail() != null && FAKE_EMAIL.matcher(l.getEmail()).matches()
                      && l.getMobile() != null && FAKE_LEAD_MOBILE.matcher(l.getMobile()).matches())
            .collect(Collectors.toList());
    }
    private List<Sale> findFakeSales() {
        return saleRepo.findAll().stream()
            .filter(s -> s.getInvoiceNumber() != null && FAKE_SALE_INVOICE.matcher(s.getInvoiceNumber()).matches())
            .collect(Collectors.toList());
    }
    private List<ServiceRequest> findFakeServices() {
        return serviceRequestRepo.findAll().stream()
            .filter(sr -> sr.getTicketNumber() != null && FAKE_TICKET.matcher(sr.getTicketNumber()).matches())
            .collect(Collectors.toList());
    }
    private List<Payment> findFakePayments() {
        return paymentRepo.findAllByOrderByCreatedAtDesc().stream()
            .filter(p -> p.getPaymentNumber() != null && p.getPaymentNumber().startsWith("AQG-PAY-"))
            .collect(Collectors.toList());
    }
    private List<Quotation> findFakeQuotations() {
        return quotationRepo.findAll().stream()
            .filter(q -> q.getQuotationNumber() != null && FAKE_QUOTATION.matcher(q.getQuotationNumber()).matches())
            .collect(Collectors.toList());
    }
    private List<Enquiry> findFakeEnquiries() {
        return enquiryRepo.findAll().stream()
            .filter(e -> e.getMobile() != null && FAKE_ENQUIRY_MOBILES.contains(e.getMobile()))
            .collect(Collectors.toList());
    }

    /** Preview only — shows exactly what would be deleted, deletes nothing. */
    @GetMapping("/preview")
    public ResponseEntity<ApiResponse<Map<String,Object>>> preview() {
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("fakeCustomers", findFakeCustomers().size());
        result.put("fakeLeads", findFakeLeads().size());
        result.put("fakeSales", findFakeSales().size());
        result.put("fakeServiceRequests", findFakeServices().size());
        result.put("fakePayments", findFakePayments().size());
        result.put("fakeQuotations", findFakeQuotations().size());
        result.put("fakeEnquiries", findFakeEnquiries().size());
        return ResponseEntity.ok(ApiResponse.success("Preview only — nothing deleted", result));
    }

    /** Actually deletes the identified seed/demo data. */
    @PostMapping("/execute")
    public ResponseEntity<ApiResponse<Map<String,Object>>> execute() {
        List<Payment> fakePayments = findFakePayments();
        paymentRepo.deleteAll(fakePayments);

        List<Quotation> fakeQuotations = findFakeQuotations();
        quotationRepo.deleteAll(fakeQuotations);

        List<Enquiry> fakeEnquiries = findFakeEnquiries();
        enquiryRepo.deleteAll(fakeEnquiries);

        List<Sale> fakeSales = findFakeSales();
        saleRepo.deleteAll(fakeSales);

        List<ServiceRequest> fakeServices = findFakeServices();
        serviceRequestRepo.deleteAll(fakeServices);

        List<Lead> fakeLeads = findFakeLeads();
        leadRepo.deleteAll(fakeLeads);

        List<Customer> fakeCustomers = findFakeCustomers();
        Set<Long> fakeCustomerIds = fakeCustomers.stream().map(Customer::getId).collect(Collectors.toSet());
        List<OperationHistory> fakeHistory = historyRepo.findAllByOrderByCreatedAtDesc().stream()
            .filter(h -> h.getCustomer() != null && fakeCustomerIds.contains(h.getCustomer().getId()))
            .collect(Collectors.toList());
        historyRepo.deleteAll(fakeHistory);
        customerRepo.deleteAll(fakeCustomers);

        Map<String,Object> result = new LinkedHashMap<>();
        result.put("customersDeleted", fakeCustomers.size());
        result.put("leadsDeleted", fakeLeads.size());
        result.put("salesDeleted", fakeSales.size());
        result.put("serviceRequestsDeleted", fakeServices.size());
        result.put("paymentsDeleted", fakePayments.size());
        result.put("quotationsDeleted", fakeQuotations.size());
        result.put("enquiriesDeleted", fakeEnquiries.size());
        result.put("historyDeleted", fakeHistory.size());

        log.info("Seed data cleanup: {}", result);
        return ResponseEntity.ok(ApiResponse.success("Seed/demo data removed", result));
    }
}
