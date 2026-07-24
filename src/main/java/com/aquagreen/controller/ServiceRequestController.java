package com.aquagreen.controller;

import com.aquagreen.dto.ApiResponse;
import com.aquagreen.model.*;
import com.aquagreen.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/service-requests")
@RequiredArgsConstructor
@Slf4j
public class ServiceRequestController {

    private final ServiceRequestRepository repo;
    private final StockItemRepository stockRepo;
    private final SaleRepository saleRepo;
    private final ProductRepository productRepo;
    private final CustomerRepository customerRepo;
    private final ObjectMapper mapper;
    private final com.aquagreen.service.SmsService smsService;

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // ── List all — DB-filtered, paginated ─────────────────────────
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ServiceRequest>>> getAll(
            @RequestParam(required=false) String status,
            @RequestParam(required=false) String technician,
            @RequestParam(defaultValue="0")  int page,
            @RequestParam(defaultValue="20") int size) {
        Pageable p = PageRequest.of(page, size);
        boolean hasStatus = status != null && !status.isBlank();
        boolean hasTech   = technician != null && !technician.isBlank();
        Page<ServiceRequest> result;
        if (hasStatus && hasTech)
            result = repo.findByStatusAndAssignedTechnicianOrderByCreatedAtDesc(status, technician, p);
        else if (hasStatus)
            result = repo.findByStatusOrderByCreatedAtDesc(status, p);
        else if (hasTech)
            result = repo.findByAssignedTechnicianOrderByCreatedAtDesc(technician, p);
        else
            result = repo.findAllByOrderByCreatedAtDesc(p);
        return ResponseEntity.ok(ApiResponse.success("OK", result));
    }

    /** True counts across the whole table (not just the current page). */
    @GetMapping("/counts")
    public ResponseEntity<ApiResponse<Map<String,Long>>> counts() {
        Map<String,Long> result = new LinkedHashMap<>();
        for (String s : new String[]{"PENDING","ASSIGNED","IN_PROGRESS","COMPLETED","CANCELLED"}) {
            result.put(s, repo.countByStatus(s));
        }
        result.put("TOTAL", repo.count());
        return ResponseEntity.ok(ApiResponse.success("OK", result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ServiceRequest>> getById(@PathVariable Long id) {
        return repo.findById(id).map(s -> ResponseEntity.ok(ApiResponse.success("OK", s)))
            .orElse(ResponseEntity.notFound().build());
    }

    // ── Create ────────────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<ApiResponse<ServiceRequest>> create(@RequestBody ServiceRequest sr) {
        sr.setId(null);
        if (sr.getServiceCode() == null || sr.getServiceCode().isBlank())
            sr.setServiceCode(com.aquagreen.util.CodeGenerator.next("SERV", repo.findAllServiceCodes(), 3));
        sr.setTicketNumber(generateTicket());
        // Respect an invoice number the caller already generated (e.g. the
        // Billing wizard) — only blank it out if none was supplied.
        if (sr.getInvoiceNumber() != null && sr.getInvoiceNumber().isBlank()) sr.setInvoiceNumber(null);
        sr.setStockDeducted(false);

        // If this is being created already-COMPLETED (e.g. billed directly
        // through the Billing wizard, skipping the usual PENDING→COMPLETED
        // flow), set completedAt and the maintenance due-dates now — the
        // @PreUpdate hook that normally does this never fires on insert.
        if ("COMPLETED".equals(sr.getStatus())) {
            if (sr.getCompletedAt() == null) sr.setCompletedAt(LocalDateTime.now());
            if (sr.getNextFilterDueDate() == null)  sr.setNextFilterDueDate(sr.getCompletedAt().plusYears(1));
            if (sr.getNextServiceDueDate() == null) sr.setNextServiceDueDate(sr.getCompletedAt().plusMonths(6));
        }

        ServiceRequest saved = repo.save(sr);
        deductStockForParts(saved);

        // Notify customer that ticket has been booked
        smsService.sendServiceBooked(saved.getCustomerMobile(), saved.getCustomerName(),
            saved.getTicketNumber(), saved.getAssignedTechnician());
        return ResponseEntity.ok(ApiResponse.success("Ticket created", saved));
    }

    /**
     * Deduct stock for any spare parts logged with a stockItemId, and mark
     * the service as stockDeducted so this never happens twice. Safe to call
     * even if sparePartsJson is empty/blank.
     */
    private void deductStockForParts(ServiceRequest sr) {
        if (Boolean.TRUE.equals(sr.getStockDeducted())) return;
        if (sr.getSparePartsJson() == null || sr.getSparePartsJson().isBlank()) return;
        boolean any = false;
        try {
            List<Map<String,Object>> parts = mapper.readValue(sr.getSparePartsJson(), List.class);
            for (Map<String,Object> p : parts) {
                Object idObj = p.get("stockItemId");
                if (idObj == null) continue;
                Long stockItemId = Long.valueOf(idObj.toString());
                int qty = ((Number) p.getOrDefault("qty", 1)).intValue();
                final boolean[] found = {false};
                stockRepo.findById(stockItemId).ifPresent(stock -> {
                    int newQty = Math.max(0, stock.getCurrentStock() - qty);
                    stock.setCurrentStock(newQty);
                    stockRepo.save(stock);
                    found[0] = true;
                    log.info("Stock deducted (on create): {} x{} → remaining {}", stock.getName(), qty, newQty);
                });
                if (found[0]) any = true;
            }
        } catch (Exception e) {
            log.warn("Could not parse sparePartsJson for stock deduction: {}", e.getMessage());
        }
        if (any) { sr.setStockDeducted(true); repo.save(sr); }
    }

    // ── Update ────────────────────────────────────────────────────
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ServiceRequest>> update(@PathVariable Long id, @RequestBody ServiceRequest sr) {
        sr.setId(id);
        return ResponseEntity.ok(ApiResponse.success("Updated", repo.save(sr)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<ServiceRequest>> updateStatus(
            @PathVariable Long id, @RequestParam String status) {
        ServiceRequest sr = repo.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
        sr.setStatus(status);
        if ("COMPLETED".equals(status) && sr.getCompletedAt() == null)
            sr.setCompletedAt(LocalDateTime.now());
        ServiceRequest saved = repo.save(sr);
        // SMS triggers
        if ("ASSIGNED".equals(status)) {
            smsService.sendServiceBooked(saved.getCustomerMobile(), saved.getCustomerName(),
                saved.getTicketNumber(), saved.getAssignedTechnician());
        } else if ("COMPLETED".equals(status)) {
            String amt = saved.getTotalBillAmount() != null
                ? "₹" + saved.getTotalBillAmount().toPlainString() : null;
            smsService.sendServiceCompleted(saved.getCustomerMobile(), saved.getCustomerName(),
                saved.getTicketNumber(), amt);
        }
        return ResponseEntity.ok(ApiResponse.success("Status updated", saved));
    }

    // ── Add spare parts used by technician → deducts stock ───────
    @PostMapping("/{id}/spare-parts")
    public ResponseEntity<ApiResponse<ServiceRequest>> addSpareParts(
            @PathVariable Long id, @RequestBody SparePartsRequest req) {

        ServiceRequest sr = repo.findById(id).orElseThrow(() -> new RuntimeException("Not found"));

        BigDecimal total = BigDecimal.ZERO;
        List<Map<String,Object>> partsList = new ArrayList<>();

        for (SparePartsRequest.Part part : req.getParts()) {
            // Deduct from stock if stockItemId provided
            if (part.getStockItemId() != null) {
                stockRepo.findById(part.getStockItemId()).ifPresent(stock -> {
                    int newQty = Math.max(0, stock.getCurrentStock() - part.getQty());
                    stock.setCurrentStock(newQty);
                    stockRepo.save(stock);
                    log.info("Stock deducted: {} x{} → remaining {}", stock.getName(), part.getQty(), newQty);
                });
            }

            BigDecimal lineTotal = part.getUnitPrice().multiply(BigDecimal.valueOf(part.getQty()));
            total = total.add(lineTotal);

            Map<String,Object> m = new LinkedHashMap<>();
            m.put("name", part.getName());
            m.put("qty", part.getQty());
            m.put("unitPrice", part.getUnitPrice());
            m.put("lineTotal", lineTotal);
            m.put("stockItemId", part.getStockItemId());
            partsList.add(m);
        }

        try { sr.setSparePartsJson(mapper.writeValueAsString(partsList)); }
        catch (Exception e) { log.error("JSON error: {}", e.getMessage()); }

        sr.setSparePartsTotal(total);
        sr.setStockDeducted(true);

        // Recalculate total bill
        BigDecimal svc = sr.getServiceCharge() != null ? sr.getServiceCharge() : BigDecimal.ZERO;
        sr.setTotalBillAmount(svc.add(total));

        return ResponseEntity.ok(ApiResponse.success("Spare parts added and stock deducted", repo.save(sr)));
    }

    // ── Record product sold to customer during service visit ──────
    @PostMapping("/{id}/sell-product")
    public ResponseEntity<ApiResponse<Map<String,Object>>> sellProduct(
            @PathVariable Long id, @RequestBody SellProductRequest req) {

        ServiceRequest sr = repo.findById(id).orElseThrow(() -> new RuntimeException("Not found"));

        // Deduct product from stock
        if (req.getProductId() != null) {
            productRepo.findById(req.getProductId()).ifPresent(p -> {
                if (p.getStock() != null) {
                    p.setStock(Math.max(0, p.getStock() - req.getQty()));
                    productRepo.save(p);
                }
            });
        }

        // Create a Sale record
        BigDecimal lineTotal = req.getUnitPrice().multiply(BigDecimal.valueOf(req.getQty()));
        Sale sale = Sale.builder()
            .customerName(sr.getCustomerName())
            .customerMobile(sr.getCustomerMobile())
            .customer(sr.getCustomer())
            .productName(req.getProductName())
            .quantity(req.getQty())
            .unitPrice(req.getUnitPrice())
            .totalAmount(lineTotal)
            .salesPerson(sr.getAssignedTechnician())
            .invoiceNumber(generateInvoice())
            .paymentStatus(req.getPaymentStatus() != null ? req.getPaymentStatus() : "PAID")
            .paymentMethod(req.getPaymentMethod() != null ? req.getPaymentMethod() : "CASH")
            .serviceRequestId(sr.getId())
            .stockDeducted(true)
            .notes("Sold during service visit — Ticket: " + sr.getTicketNumber())
            .build();

        Sale savedSale = saleRepo.save(sale);

        // Update products sold on service request
        try {
            List<Map<String,Object>> sold = new ArrayList<>();
            if (sr.getProductsSoldJson() != null)
                sold = mapper.readValue(sr.getProductsSoldJson(), List.class);
            Map<String,Object> entry = new LinkedHashMap<>();
            entry.put("saleId", savedSale.getId());
            entry.put("productName", req.getProductName());
            entry.put("qty", req.getQty());
            entry.put("unitPrice", req.getUnitPrice());
            entry.put("lineTotal", lineTotal);
            entry.put("invoiceNumber", savedSale.getInvoiceNumber());
            sold.add(entry);
            sr.setProductsSoldJson(mapper.writeValueAsString(sold));
            BigDecimal soldTotal = sold.stream()
                .mapToDouble(e -> ((Number)e.getOrDefault("lineTotal", 0)).doubleValue())
                .mapToObj(BigDecimal::valueOf).reduce(BigDecimal.ZERO, BigDecimal::add);
            sr.setProductsSoldTotal(soldTotal);
            repo.save(sr);
        } catch (Exception e) { log.error("JSON error: {}", e.getMessage()); }

        return ResponseEntity.ok(ApiResponse.success("Product sold and stock deducted",
            Map.of("sale", savedSale, "saleId", savedSale.getId(),
                   "invoiceNumber", savedSale.getInvoiceNumber())));
    }

    // ── Complete billing — generate invoice, mark paid ────────────
    @PostMapping("/{id}/complete-billing")
    public ResponseEntity<ApiResponse<ServiceRequest>> completeBilling(
            @PathVariable Long id, @RequestBody BillingRequest req) {

        ServiceRequest sr = repo.findById(id).orElseThrow(() -> new RuntimeException("Not found"));

        if (req.getServiceCharge() != null) sr.setServiceCharge(req.getServiceCharge());

        BigDecimal svc   = sr.getServiceCharge() != null ? sr.getServiceCharge() : BigDecimal.ZERO;
        BigDecimal spare = sr.getSparePartsTotal() != null ? sr.getSparePartsTotal() : BigDecimal.ZERO;
        sr.setTotalBillAmount(svc.add(spare));
        sr.setPaymentStatus(req.getPaymentStatus() != null ? req.getPaymentStatus() : "PAID");
        sr.setPaymentMethod(req.getPaymentMethod() != null ? req.getPaymentMethod() : "CASH");

        if (sr.getInvoiceNumber() == null) sr.setInvoiceNumber(generateInvoice());
        sr.setStatus("COMPLETED");
        sr.setCompletedAt(LocalDateTime.now());

        return ResponseEntity.ok(ApiResponse.success("Billing completed. Invoice: " + sr.getInvoiceNumber(), repo.save(sr)));
    }

    // ── Generate Service Invoice PDF ──────────────────────────────
    @GetMapping("/{id}/invoice/pdf")
    public ResponseEntity<byte[]> generateInvoicePdf(@PathVariable Long id) {
        ServiceRequest sr = repo.findById(id).orElseThrow(() -> new RuntimeException("Not found"));

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 50, 50, 60, 50);
            PdfWriter.getInstance(doc, out);
            doc.open();

            Color green  = new Color(10, 79, 60);
            Color ltGreen= new Color(224, 245, 236);
            Color gray   = new Color(100, 100, 100);
            Color dark   = new Color(30, 30, 30);
            Color line   = new Color(220, 220, 220);

            Font compFont  = new Font(Font.HELVETICA, 18, Font.BOLD, green);
            Font h2Font    = new Font(Font.HELVETICA, 12, Font.BOLD, dark);
            Font normal    = new Font(Font.HELVETICA, 10, Font.NORMAL, dark);
            Font small     = new Font(Font.HELVETICA, 9, Font.NORMAL, gray);
            Font bold      = new Font(Font.HELVETICA, 10, Font.BOLD, dark);
            Font whiteBold = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
            Font redBold   = new Font(Font.HELVETICA, 11, Font.BOLD, new Color(220,38,38));
            Font greenBold = new Font(Font.HELVETICA, 12, Font.BOLD, green);

            // ── Company header ──
            PdfPTable hdr = new PdfPTable(2);
            hdr.setWidthPercentage(100); hdr.setSpacingAfter(10); hdr.setWidths(new float[]{2f,1f});
            PdfPCell lft = new PdfPCell();
            lft.setBorder(Rectangle.NO_BORDER); lft.setPadding(4);
            lft.addElement(new Paragraph("AQUA GREEN AGENCIES", compFont));
            lft.addElement(new Paragraph("Near ESI Hospital, Neelikonampalayam, Coimbatore — 641033", small));
            lft.addElement(new Paragraph("Phone: 09054617008  |  GST: 33XXXXXXXXXXXXXX", small));
            hdr.addCell(lft);

            PdfPCell rgt = new PdfPCell();
            rgt.setBorder(Rectangle.NO_BORDER); rgt.setHorizontalAlignment(Element.ALIGN_RIGHT); rgt.setPadding(4);
            rgt.addElement(new Paragraph("SERVICE INVOICE", new Font(Font.HELVETICA,14,Font.BOLD,green)));
            rgt.addElement(new Paragraph("Invoice #: " + (sr.getInvoiceNumber()!=null?sr.getInvoiceNumber():"DRAFT"), bold));
            rgt.addElement(new Paragraph("Ticket #: " + (sr.getTicketNumber()!=null?sr.getTicketNumber():"—"), small));
            rgt.addElement(new Paragraph("Date: " + LocalDateTime.now().format(D), small));
            hdr.addCell(rgt);
            doc.add(hdr);

            // ── Divider ──
            PdfPTable div = new PdfPTable(1); div.setWidthPercentage(100); div.setSpacingAfter(12);
            PdfPCell dc = new PdfPCell(); dc.setBackgroundColor(green); dc.setFixedHeight(3); dc.setBorder(Rectangle.NO_BORDER);
            div.addCell(dc); doc.add(div);

            // ── Bill to / Service info ──
            PdfPTable info = new PdfPTable(2); info.setWidthPercentage(100); info.setSpacingAfter(16); info.setWidths(new float[]{1f,1f});

            PdfPCell bt = new PdfPCell();
            bt.setBorder(Rectangle.BOX); bt.setBorderColor(line); bt.setPadding(10); bt.setBackgroundColor(ltGreen);
            bt.addElement(new Paragraph("BILL TO", new Font(Font.HELVETICA,8,Font.BOLD,gray)));
            bt.addElement(new Paragraph(sr.getCustomerName()!=null?sr.getCustomerName():"—", h2Font));
            bt.addElement(new Paragraph("Mobile: " + (sr.getCustomerMobile()!=null?sr.getCustomerMobile():"—"), normal));
            if (sr.getCustomerAddress()!=null)
                bt.addElement(new Paragraph(sr.getCustomerAddress(), small));
            info.addCell(bt);

            PdfPCell si = new PdfPCell();
            si.setBorder(Rectangle.BOX); si.setBorderColor(line); si.setPadding(10);
            si.addElement(new Paragraph("SERVICE DETAILS", new Font(Font.HELVETICA,8,Font.BOLD,gray)));
            si.addElement(new Paragraph("Product: " + (sr.getProductName()!=null?sr.getProductName():"—"), normal));
            si.addElement(new Paragraph("Issue: " + (sr.getIssueDescription()!=null?sr.getIssueDescription():"—"), normal));
            si.addElement(new Paragraph("Technician: " + (sr.getAssignedTechnician()!=null?sr.getAssignedTechnician():"—"), normal));
            si.addElement(new Paragraph("Completed: " + (sr.getCompletedAt()!=null?sr.getCompletedAt().format(D):"Pending"), normal));
            si.addElement(new Paragraph("Payment: " + (sr.getPaymentMethod()!=null?sr.getPaymentMethod():"—"), normal));
            info.addCell(si);
            doc.add(info);

            // ── Items table ──
            PdfPTable items = new PdfPTable(4);
            items.setWidthPercentage(100); items.setWidths(new float[]{3f,1f,1.5f,1.5f}); items.setSpacingAfter(12);

            for (String h : new String[]{"Description","Qty","Unit Price","Amount"}) {
                PdfPCell hc = new PdfPCell(new Phrase(h, whiteBold));
                hc.setBackgroundColor(green); hc.setPadding(8); hc.setBorder(Rectangle.NO_BORDER);
                items.addCell(hc);
            }

            // Service charge row
            BigDecimal svc = sr.getServiceCharge()!=null?sr.getServiceCharge():BigDecimal.ZERO;
            addItemRow(items, "Service Charge", 1, svc, svc, normal, line, false);

            // Spare parts rows
            if (sr.getSparePartsJson()!=null && !sr.getSparePartsJson().isBlank()) {
                try {
                    List<Map<String,Object>> parts = mapper.readValue(sr.getSparePartsJson(), List.class);
                    boolean alt = true;
                    for (Map<String,Object> p : parts) {
                        int qty = ((Number)p.getOrDefault("qty",1)).intValue();
                        BigDecimal up = BigDecimal.valueOf(((Number)p.getOrDefault("unitPrice",0)).doubleValue());
                        BigDecimal lt = BigDecimal.valueOf(((Number)p.getOrDefault("lineTotal",0)).doubleValue());
                        addItemRow(items, "Spare Part — " + p.get("name"), qty, up, lt, normal, line, alt);
                        alt = !alt;
                    }
                } catch (Exception e) { log.warn("Parse spare parts JSON: {}", e.getMessage()); }
            }

            // Products sold rows
            if (sr.getProductsSoldJson()!=null && !sr.getProductsSoldJson().isBlank()) {
                try {
                    List<Map<String,Object>> prods = mapper.readValue(sr.getProductsSoldJson(), List.class);
                    for (Map<String,Object> p : prods) {
                        int qty = ((Number)p.getOrDefault("qty",1)).intValue();
                        BigDecimal up = BigDecimal.valueOf(((Number)p.getOrDefault("unitPrice",0)).doubleValue());
                        BigDecimal lt = BigDecimal.valueOf(((Number)p.getOrDefault("lineTotal",0)).doubleValue());
                        addItemRow(items, "Product Sold — " + p.get("productName"), qty, up, lt, normal, line, false);
                    }
                } catch (Exception e) { log.warn("Parse products JSON: {}", e.getMessage()); }
            }

            doc.add(items);

            // ── Totals box ──
            PdfPTable totals = new PdfPTable(2); totals.setWidthPercentage(50); totals.setHorizontalAlignment(Element.ALIGN_RIGHT); totals.setSpacingAfter(24);
            BigDecimal spare = sr.getSparePartsTotal()!=null?sr.getSparePartsTotal():BigDecimal.ZERO;
            BigDecimal prodSold = sr.getProductsSoldTotal()!=null?sr.getProductsSoldTotal():BigDecimal.ZERO;
            BigDecimal subtotal = svc.add(spare).add(prodSold);

            addTotalRow(totals, "Service Charge", fmt(svc), normal, line, false);
            if (spare.compareTo(BigDecimal.ZERO)>0) addTotalRow(totals, "Spare Parts", fmt(spare), normal, line, false);
            if (prodSold.compareTo(BigDecimal.ZERO)>0) addTotalRow(totals, "Products Sold", fmt(prodSold), normal, line, false);

            PdfPCell tl = new PdfPCell(new Phrase("TOTAL", new Font(Font.HELVETICA,12,Font.BOLD,Color.WHITE)));
            tl.setBackgroundColor(green); tl.setPadding(10); tl.setBorder(Rectangle.NO_BORDER);
            PdfPCell tv = new PdfPCell(new Phrase("₹"+fmt(subtotal), new Font(Font.HELVETICA,14,Font.BOLD,Color.WHITE)));
            tv.setBackgroundColor(green); tv.setPadding(10); tv.setHorizontalAlignment(Element.ALIGN_RIGHT); tv.setBorder(Rectangle.NO_BORDER);
            totals.addCell(tl); totals.addCell(tv);
            doc.add(totals);

            // ── Payment status ──
            String pStatus = sr.getPaymentStatus()!=null?sr.getPaymentStatus():"PENDING";
            Paragraph ps = new Paragraph("Payment Status: " + pStatus, "PAID".equals(pStatus)?greenBold:redBold);
            ps.setSpacingAfter(18);
            doc.add(ps);

            // ── Technician notes ──
            if (sr.getTechnicianNotes()!=null && !sr.getTechnicianNotes().isBlank()) {
                PdfPTable notes = new PdfPTable(1); notes.setWidthPercentage(100); notes.setSpacingAfter(14);
                PdfPCell nc = new PdfPCell();
                nc.setBorder(Rectangle.BOX); nc.setBorderColor(line); nc.setPadding(10);
                nc.addElement(new Paragraph("Technician Notes", bold));
                nc.addElement(new Paragraph(sr.getTechnicianNotes(), normal));
                notes.addCell(nc); doc.add(notes);
            }

            // ── Footer ──
            PdfPTable footer = new PdfPTable(1); footer.setWidthPercentage(100);
            PdfPCell fc = new PdfPCell(new Phrase(
                "Thank you for choosing Aqua Green Agencies | Warranty: 3 months on parts | " + LocalDateTime.now().format(DT),
                new Font(Font.HELVETICA,8,Font.ITALIC,gray)));
            fc.setBorderWidthTop(1); fc.setBorderColorTop(line);
            fc.setBorderWidthBottom(0); fc.setBorderWidthLeft(0); fc.setBorderWidthRight(0);
            fc.setPadding(8); fc.setHorizontalAlignment(Element.ALIGN_CENTER);
            footer.addCell(fc);
            doc.add(footer);

            doc.close();

            String fname = "Service-Invoice-" + (sr.getInvoiceNumber()!=null?sr.getInvoiceNumber():"DRAFT") + ".pdf";
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\""+fname+"\"")
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .body(out.toByteArray());

        } catch (Exception e) {
            log.error("Invoice PDF failed: {}", e.getMessage(), e);
            throw new RuntimeException("Invoice generation failed: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        repo.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }

    // ── Helpers ───────────────────────────────────────────────────
    private String generateTicket() {
        return "TKT-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd")) + "-" + String.format("%04d", (int)(Math.random()*9000)+1000);
    }
    private String generateInvoice() {
        return "INV-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd")) + "-" + String.format("%04d", (int)(Math.random()*9000)+1000);
    }
    private String fmt(BigDecimal v) { return v!=null ? String.format("%,.2f", v) : "0.00"; }

    private void addItemRow(PdfPTable t, String desc, int qty, BigDecimal up, BigDecimal total, Font f, Color line, boolean alt) {
        Color bg = alt ? new Color(248,250,252) : Color.WHITE;
        for (String val : new String[]{desc, String.valueOf(qty), "₹"+fmt(up), "₹"+fmt(total)}) {
            PdfPCell c = new PdfPCell(new Phrase(val, f));
            c.setPadding(7); c.setBorderColor(line); c.setBorderWidth(0.4f); c.setBackgroundColor(bg);
            t.addCell(c);
        }
    }

    private void addTotalRow(PdfPTable t, String label, String val, Font f, Color line, boolean bold) {
        PdfPCell lc = new PdfPCell(new Phrase(label, f)); lc.setPadding(7); lc.setBorderColor(line); t.addCell(lc);
        PdfPCell vc = new PdfPCell(new Phrase("₹"+val, f)); vc.setPadding(7); vc.setHorizontalAlignment(Element.ALIGN_RIGHT); vc.setBorderColor(line); t.addCell(vc);
    }

    // ── DTOs ─────────────────────────────────────────────────────
    @Data static class SparePartsRequest {
        private List<Part> parts;
        @Data static class Part {
            private String name;
            private Integer qty;
            private BigDecimal unitPrice;
            private Long stockItemId;
        }
    }
    @Data static class SellProductRequest {
        private Long productId;
        private String productName;
        private Integer qty;
        private BigDecimal unitPrice;
        private String paymentStatus;
        private String paymentMethod;
    }
    @Data static class BillingRequest {
        private BigDecimal serviceCharge;
        private String paymentStatus;
        private String paymentMethod;
    }
}
