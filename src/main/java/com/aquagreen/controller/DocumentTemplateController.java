package com.aquagreen.controller;

import com.aquagreen.dto.ApiResponse;
import com.aquagreen.model.*;
import com.aquagreen.repository.*;
import com.aquagreen.service.DocumentGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
@Slf4j
public class DocumentTemplateController {

    private final DocumentTemplateRepository templateRepo;
    private final CustomerRepository customerRepo;
    private final QuotationRepository quotationRepo;
    private final DocumentGenerationService docService;

    // ── CRUD ─────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<ApiResponse<List<DocumentTemplate>>> getAll(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String category) {
        List<DocumentTemplate> list;
        if (type != null) list = templateRepo.findByTemplateTypeAndActiveTrueOrderByCreatedAtDesc(type);
        else if (category != null) list = templateRepo.findByCategoryAndActiveTrueOrderByCreatedAtDesc(category);
        else list = templateRepo.findByActiveTrueOrderByCreatedAtDesc();
        return ResponseEntity.ok(ApiResponse.success("OK", list));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DocumentTemplate>> getById(@PathVariable Long id) {
        return templateRepo.findById(id)
                .map(t -> ResponseEntity.ok(ApiResponse.success("OK", t)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DocumentTemplate>> create(@RequestBody DocumentTemplate template) {
        template.setId(null);
        // Auto-extract placeholders
        String content = template.getHtmlContent() != null ? template.getHtmlContent() : template.getMessageContent();
        template.setPlaceholders(docService.extractPlaceholders(content));
        return ResponseEntity.ok(ApiResponse.success("Template created", templateRepo.save(template)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DocumentTemplate>> update(@PathVariable Long id, @RequestBody DocumentTemplate template) {
        template.setId(id);
        String content = template.getHtmlContent() != null ? template.getHtmlContent() : template.getMessageContent();
        template.setPlaceholders(docService.extractPlaceholders(content));
        return ResponseEntity.ok(ApiResponse.success("Updated", templateRepo.save(template)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        templateRepo.findById(id).ifPresent(t -> { t.setActive(false); templateRepo.save(t); });
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }

    @PatchMapping("/{id}/default")
    public ResponseEntity<ApiResponse<DocumentTemplate>> setDefault(@PathVariable Long id) {
        DocumentTemplate t = templateRepo.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
        // Unset other defaults for same type
        templateRepo.findByTemplateTypeAndIsDefaultTrue(t.getTemplateType())
                .ifPresent(existing -> { existing.setIsDefault(false); templateRepo.save(existing); });
        t.setIsDefault(true);
        return ResponseEntity.ok(ApiResponse.success("Set as default", templateRepo.save(t)));
    }

    // ── PREVIEW — render with sample variables ────────────────────

    @PostMapping("/{id}/preview")
    public ResponseEntity<ApiResponse<Map<String, String>>> preview(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> variables) {
        DocumentTemplate t = templateRepo.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
        Map<String, String> vars = buildSampleVariables();
        if (variables != null) vars.putAll(variables);
        String rendered = docService.renderTemplate(
            t.getHtmlContent() != null ? t.getHtmlContent() : t.getMessageContent(), vars);
        return ResponseEntity.ok(ApiResponse.success("Preview", Map.of("rendered", rendered, "templateName", t.getName())));
    }

    // ── GENERATE DOCX ─────────────────────────────────────────────

    @PostMapping("/{id}/generate-docx")
    public ResponseEntity<byte[]> generateDocx(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> variables) {
        DocumentTemplate t = templateRepo.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
        Map<String, String> vars = buildSampleVariables();
        if (variables != null) vars.putAll(variables);
        byte[] docxBytes = docService.generateDocx(t, vars);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + sanitize(t.getName()) + ".docx\"")
                .body(docxBytes);
    }

    // ── GENERATE PDF ──────────────────────────────────────────────

    @PostMapping("/{id}/generate-pdf")
    public ResponseEntity<byte[]> generatePdf(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> variables) {
        DocumentTemplate t = templateRepo.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
        Map<String, String> vars = buildSampleVariables();
        if (variables != null) vars.putAll(variables);
        byte[] pdfBytes = docService.generatePdf(t, vars);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + sanitize(t.getName()) + ".pdf\"")
                .body(pdfBytes);
    }

    // ── GENERATE QUOTATION PDF ─────────────────────────────────────

    @PostMapping("/quotation/{quotationId}/pdf")
    public ResponseEntity<byte[]> generateQuotationPdf(@PathVariable Long quotationId) {
        Quotation q = quotationRepo.findById(quotationId).orElseThrow(() -> new RuntimeException("Quotation not found"));
        Map<String, String> vars = new HashMap<>();
        vars.put("quotationNumber", q.getQuotationNumber());
        vars.put("customerName", q.getCustomerName());
        vars.put("mobile", q.getCustomerMobile() != null ? q.getCustomerMobile() : "");
        vars.put("address", q.getCustomerAddress() != null ? q.getCustomerAddress() : "");
        vars.put("subtotal", q.getSubtotal() != null ? q.getSubtotal().toPlainString() : "0");
        vars.put("gstAmount", q.getGstAmount() != null ? q.getGstAmount().toPlainString() : "0");
        vars.put("totalAmount", q.getTotalAmount() != null ? q.getTotalAmount().toPlainString() : "0");
        vars.put("notes", q.getNotes() != null ? q.getNotes() : "");
        vars.put("date", q.getCreatedAt() != null ? q.getCreatedAt().toLocalDate().toString() : java.time.LocalDate.now().toString());

        byte[] pdf = docService.generateQuotationPdf(vars, q.getItemsJson());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Quotation-" + q.getQuotationNumber() + ".pdf\"")
                .body(pdf);
    }

    // ── EXTRACT PLACEHOLDERS from content ─────────────────────────

    @PostMapping("/extract-placeholders")
    public ResponseEntity<ApiResponse<Map<String, Object>>> extractPlaceholders(@RequestBody Map<String, String> body) {
        String content = body.getOrDefault("content", "");
        String placeholders = docService.extractPlaceholders(content);
        List<String> list = placeholders.isEmpty() ? List.of() : Arrays.asList(placeholders.split(","));
        return ResponseEntity.ok(ApiResponse.success("OK", Map.of("placeholders", list, "count", list.size())));
    }

    // ── COUNTS per type ───────────────────────────────────────────

    @GetMapping("/counts")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getCounts() {
        Map<String, Long> counts = Map.of(
            "SMS", templateRepo.countByTemplateType("SMS"),
            "WHATSAPP", templateRepo.countByTemplateType("WHATSAPP"),
            "EMAIL", templateRepo.countByTemplateType("EMAIL"),
            "QUOTATION", templateRepo.countByTemplateType("QUOTATION"),
            "INVOICE", templateRepo.countByTemplateType("INVOICE"),
            "SERVICE_REPORT", templateRepo.countByTemplateType("SERVICE_REPORT"),
            "WARRANTY_CERT", templateRepo.countByTemplateType("WARRANTY_CERT"),
            "TOTAL", templateRepo.count()
        );
        return ResponseEntity.ok(ApiResponse.success("OK", counts));
    }

    // ── AVAILABLE PLACEHOLDERS reference ─────────────────────────

    @GetMapping("/placeholders")
    public ResponseEntity<ApiResponse<Map<String, List<Map<String, String>>>>> getPlaceholders() {
        Map<String, List<Map<String, String>>> groups = new LinkedHashMap<>();

        groups.put("Customer", List.of(
            ph("customerName","Customer full name"), ph("customerId","Customer ID"),
            ph("mobile","Mobile number"), ph("email","Email address"),
            ph("address","Full address"), ph("city","City"), ph("district","District"),
            ph("state","State"), ph("pincode","PIN code")));

        groups.put("Product & Service", List.of(
            ph("productName","Product name"), ph("model","Model number"),
            ph("serialNumber","Serial number"), ph("filterType","Filter type"),
            ph("technician","Technician name"), ph("salesPerson","Sales person")));

        groups.put("Dates", List.of(
            ph("installationDate","Installation date"), ph("nextServiceDate","Next service date"),
            ph("amcExpiryDate","Annual Service expiry date"), ph("lastServiceDate","Last service date"),
            ph("warrantyExpiry","Warranty expiry date")));

        groups.put("Financial", List.of(
            ph("quotationNumber","Quotation number"), ph("invoiceNumber","Invoice number"),
            ph("paymentStatus","Payment status"), ph("paymentMode","Payment mode"),
            ph("gst","GST amount"), ph("discount","Discount"),
            ph("subtotal","Subtotal"), ph("grandTotal","Grand total"),
            ph("upi","UPI ID"), ph("bankDetails","Bank details")));

        groups.put("Company", List.of(
            ph("companyName","Company name"), ph("companyPhone","Company phone"),
            ph("companyEmail","Company email"), ph("companyWebsite","Company website"),
            ph("date","Current date"), ph("time","Current time")));

        return ResponseEntity.ok(ApiResponse.success("OK", groups));
    }

    // ─── Helpers ─────────────────────────────────────────────────

    private Map<String, String> ph(String key, String desc) {
        return Map.of("key", key, "placeholder", "{{" + key + "}}", "description", desc);
    }

    private Map<String, String> buildSampleVariables() {
        Map<String, String> vars = new HashMap<>();
        vars.put("customerName", "Rajesh Kumar");
        vars.put("customerId", "C-001");
        vars.put("mobile", "9876543210");
        vars.put("email", "rajesh@gmail.com");
        vars.put("address", "12, Gandhi Nagar, Saravanampatti, Coimbatore");
        vars.put("city", "Coimbatore");
        vars.put("productName", "Aqua Green RO 12L");
        vars.put("model", "AGA-RO-12L");
        vars.put("serialNumber", "SN2024001");
        vars.put("technician", "Murugan K");
        vars.put("salesPerson", "Senthil K");
        vars.put("installationDate", "2024-06-10");
        vars.put("nextServiceDate", "2025-06-10");
        vars.put("amcExpiryDate", "2025-06-10");
        vars.put("quotationNumber", "AQG-QT-001");
        vars.put("invoiceNumber", "AQG-INV-001");
        vars.put("paymentStatus", "PAID");
        vars.put("paymentMode", "UPI");
        vars.put("subtotal", "7,499");
        vars.put("gst", "1,350");
        vars.put("grandTotal", "8,849");
        vars.put("companyName", "Aqua Green Agencies");
        vars.put("companyPhone", "09054617008");
        vars.put("companyEmail", "info@aquagreenagencies.com");
        vars.put("companyWebsite", "www.aquagreenagencies.com");
        vars.put("date", java.time.LocalDate.now().toString());
        vars.put("time", java.time.LocalTime.now().toString().substring(0, 5));
        return vars;
    }

    private String sanitize(String name) {
        return name != null ? name.replaceAll("[^a-zA-Z0-9_\\-]", "_") : "document";
    }
}
