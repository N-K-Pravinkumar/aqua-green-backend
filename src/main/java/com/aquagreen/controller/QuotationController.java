package com.aquagreen.controller;
import com.aquagreen.dto.ApiResponse;
import com.aquagreen.model.Quotation;
import com.aquagreen.repository.QuotationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.Map;

@RestController @RequestMapping("/api/quotations") @RequiredArgsConstructor
public class QuotationController {
    private final QuotationRepository repo;
    private final com.aquagreen.service.SmsService smsService;
    private final ObjectMapper mapper;

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    @GetMapping
    public ResponseEntity<ApiResponse<Page<Quotation>>> getAll(
            @RequestParam(required=false) String status,
            @RequestParam(defaultValue="0")  int page,
            @RequestParam(defaultValue="20") int size) {
        Pageable p = PageRequest.of(page, size);
        Page<Quotation> result = status!=null && !status.isBlank()
            ? repo.findByStatusOrderByCreatedAtDesc(status, p)
            : repo.findAllByOrderByCreatedAtDesc(p);
        return ResponseEntity.ok(ApiResponse.success("OK", result));
    }

    @GetMapping("/counts") public ResponseEntity<ApiResponse<Map<String,Long>>> counts() {
        return ResponseEntity.ok(ApiResponse.success("OK", Map.of(
            "DRAFT",    repo.countByStatus("DRAFT"),
            "SENT",     repo.countByStatus("SENT"),
            "ACCEPTED", repo.countByStatus("ACCEPTED"),
            "REJECTED", repo.countByStatus("REJECTED"))));
    }

    @GetMapping("/{id}") public ResponseEntity<ApiResponse<Quotation>> getById(@PathVariable Long id) {
        return repo.findById(id).map(q->ResponseEntity.ok(ApiResponse.success("OK",q))).orElse(ResponseEntity.notFound().build());
    }
    @PostMapping public ResponseEntity<ApiResponse<Quotation>> create(@RequestBody Quotation q) {
        q.setId(null); return ResponseEntity.ok(ApiResponse.success("Quotation created", repo.save(q)));
    }
    @PutMapping("/{id}") public ResponseEntity<ApiResponse<Quotation>> update(@PathVariable Long id, @RequestBody Quotation q) {
        q.setId(id); return ResponseEntity.ok(ApiResponse.success("Updated", repo.save(q)));
    }
    @PatchMapping("/{id}/status") public ResponseEntity<ApiResponse<Quotation>> updateStatus(@PathVariable Long id, @RequestParam String status) {
        Quotation q = repo.findById(id).orElseThrow(()->new RuntimeException("Not found"));
        q.setStatus(status);
        Quotation saved = repo.save(q);
        if ("SENT".equals(status)) {
            String amt = saved.getTotalAmount() != null ? "₹" + saved.getTotalAmount().toPlainString() : null;
            smsService.sendQuotationSent(saved.getCustomerMobile(), saved.getCustomerName(),
                saved.getQuotationNumber(), amt);
        }
        return ResponseEntity.ok(ApiResponse.success("Updated", saved));
    }
    @DeleteMapping("/{id}") public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        repo.deleteById(id); return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }

    // ── Quotation PDF ─────────────────────────────────────────
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> quotationPdf(@PathVariable Long id) {
        Quotation q = repo.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 50, 50, 60, 50);
            PdfWriter.getInstance(doc, out); doc.open();

            Color green = new Color(10,79,60);
            Color lt    = new Color(224,245,236);
            Color gray  = new Color(100,100,100);
            Color dark  = new Color(30,30,30);
            Color line  = new Color(220,220,220);
            Color amber = new Color(180,100,0);

            Font compFont = new Font(Font.HELVETICA,18,Font.BOLD,green);
            Font h2       = new Font(Font.HELVETICA,12,Font.BOLD,dark);
            Font norm     = new Font(Font.HELVETICA,10,Font.NORMAL,dark);
            Font sm       = new Font(Font.HELVETICA,9,Font.NORMAL,gray);
            Font bd       = new Font(Font.HELVETICA,10,Font.BOLD,dark);
            Font wh       = new Font(Font.HELVETICA,9,Font.BOLD,Color.WHITE);

            // ── Header ───────────────────────────────────────
            PdfPTable hdr = new PdfPTable(2);
            hdr.setWidthPercentage(100); hdr.setSpacingAfter(10); hdr.setWidths(new float[]{2f,1f});

            PdfPCell lc = new PdfPCell(); lc.setBorder(Rectangle.NO_BORDER); lc.setPadding(4);
            lc.addElement(new Paragraph("AQUA GREEN AGENCIES", compFont));
            lc.addElement(new Paragraph("Near ESI Hospital, Neelikonampalayam, Coimbatore — 641033", sm));
            lc.addElement(new Paragraph("Phone: 09054617008 | GST: 33XXXXXXXXXXXXXX", sm));
            hdr.addCell(lc);

            PdfPCell rc = new PdfPCell(); rc.setBorder(Rectangle.NO_BORDER);
            rc.setHorizontalAlignment(Element.ALIGN_RIGHT); rc.setPadding(4);
            rc.addElement(new Paragraph("QUOTATION", new Font(Font.HELVETICA,14,Font.BOLD,amber)));
            rc.addElement(new Paragraph("Quot #: " + safe(q.getQuotationNumber()), bd));
            rc.addElement(new Paragraph("Date: " + (q.getCreatedAt()!=null ? q.getCreatedAt().format(D) : LocalDate.now().format(D)), sm));
            rc.addElement(new Paragraph("Valid for: " + (q.getValidityDays()!=null ? q.getValidityDays() : 30) + " days", sm));
            hdr.addCell(rc);
            doc.add(hdr);

            // Divider
            PdfPTable dv = new PdfPTable(1); dv.setWidthPercentage(100); dv.setSpacingAfter(14);
            PdfPCell dc = new PdfPCell(); dc.setBackgroundColor(green); dc.setFixedHeight(3); dc.setBorder(Rectangle.NO_BORDER); dv.addCell(dc);
            doc.add(dv);

            // ── Bill To ───────────────────────────────────────
            PdfPTable info = new PdfPTable(2);
            info.setWidthPercentage(100); info.setSpacingAfter(16); info.setWidths(new float[]{1f,1f});

            PdfPCell bt = new PdfPCell(); bt.setBorder(Rectangle.BOX); bt.setBorderColor(line); bt.setPadding(10); bt.setBackgroundColor(lt);
            bt.addElement(new Paragraph("QUOTE TO", new Font(Font.HELVETICA,8,Font.BOLD,gray)));
            bt.addElement(new Paragraph(safe(q.getCustomerName()), h2));
            if (q.getCustomerMobile()!=null) bt.addElement(new Paragraph("Mobile: "+q.getCustomerMobile(), norm));
            if (q.getCustomerAddress()!=null) bt.addElement(new Paragraph(q.getCustomerAddress(), sm));
            info.addCell(bt);

            PdfPCell st = new PdfPCell(); st.setBorder(Rectangle.BOX); st.setBorderColor(line); st.setPadding(10);
            st.addElement(new Paragraph("STATUS", new Font(Font.HELVETICA,8,Font.BOLD,gray)));
            st.addElement(new Paragraph(safe(q.getStatus()), new Font(Font.HELVETICA,12,Font.BOLD,
                "ACCEPTED".equals(q.getStatus()) ? new Color(10,79,60) :
                "REJECTED".equals(q.getStatus()) ? new Color(180,30,30) : amber)));
            info.addCell(st);
            doc.add(info);

            // ── Line items ───────────────────────────────────
            List<Map<String,Object>> items = parseItems(q.getItemsJson());
            PdfPTable tbl = new PdfPTable(5);
            tbl.setWidthPercentage(100); tbl.setWidths(new float[]{3f,1f,1.5f,1f,1.5f}); tbl.setSpacingAfter(12);
            for (String h : new String[]{"Description","Qty","Unit Price","GST","Total"}) {
                PdfPCell hc = new PdfPCell(new Phrase(h, wh));
                hc.setBackgroundColor(green); hc.setPadding(8); hc.setBorder(Rectangle.NO_BORDER); tbl.addCell(hc);
            }
            if (items.isEmpty()) {
                PdfPCell empty = new PdfPCell(new Phrase("No items", norm));
                empty.setColspan(5); empty.setPadding(10); empty.setBorderColor(line); tbl.addCell(empty);
            } else {
                for (Map<String,Object> it : items) {
                    String desc  = str(it.get("description"), str(it.get("name"), "—"));
                    String qty   = str(it.get("quantity"),  "1");
                    String price = "₹" + str(it.get("unitPrice"), str(it.get("price"), "0"));
                    String gst   = str(it.get("gstPercent"), "0") + "%";
                    String total = "₹" + str(it.get("total"),  "0");
                    for (String v : new String[]{desc, qty, price, gst, total}) {
                        PdfPCell c = new PdfPCell(new Phrase(v, norm));
                        c.setPadding(8); c.setBorderColor(line); c.setBorderWidth(0.4f); tbl.addCell(c);
                    }
                }
            }
            doc.add(tbl);

            // ── Totals ────────────────────────────────────────
            PdfPTable tot = new PdfPTable(2);
            tot.setWidthPercentage(40); tot.setHorizontalAlignment(Element.ALIGN_RIGHT); tot.setSpacingAfter(20);

            if (q.getSubtotal()!=null && q.getSubtotal().compareTo(BigDecimal.ZERO)>0) {
                addTotRow(tot, "Subtotal", "₹"+fmt(q.getSubtotal()), norm, line);
            }
            if (q.getGstAmount()!=null && q.getGstAmount().compareTo(BigDecimal.ZERO)>0) {
                addTotRow(tot, "GST", "₹"+fmt(q.getGstAmount()), norm, line);
            }
            PdfPCell tl = new PdfPCell(new Phrase("TOTAL", new Font(Font.HELVETICA,12,Font.BOLD,Color.WHITE)));
            tl.setBackgroundColor(green); tl.setPadding(10); tl.setBorder(Rectangle.NO_BORDER); tot.addCell(tl);
            PdfPCell tv = new PdfPCell(new Phrase("₹"+fmt(q.getTotalAmount()), new Font(Font.HELVETICA,14,Font.BOLD,Color.WHITE)));
            tv.setBackgroundColor(green); tv.setPadding(10); tv.setHorizontalAlignment(Element.ALIGN_RIGHT); tv.setBorder(Rectangle.NO_BORDER); tot.addCell(tv);
            doc.add(tot);

            // Notes
            if (q.getNotes()!=null && !q.getNotes().isBlank()) {
                Paragraph np = new Paragraph("Notes: " + q.getNotes(), sm); np.setSpacingAfter(16); doc.add(np);
            }

            // Terms
            Paragraph terms = new Paragraph();
            terms.add(new Chunk("Terms & Conditions: ", new Font(Font.HELVETICA,8,Font.BOLD,gray)));
            terms.add(new Chunk("This quotation is valid for " + (q.getValidityDays()!=null?q.getValidityDays():30) +
                " days from the date of issue. Prices are subject to change after the validity period. " +
                "50% advance required at order confirmation. Balance due on delivery/installation.", sm));
            terms.setSpacingAfter(12);
            doc.add(terms);

            // Footer
            PdfPTable ft = new PdfPTable(1); ft.setWidthPercentage(100);
            PdfPCell fc = new PdfPCell(new Phrase(
                "Aqua Green Agencies | Near ESI Hospital, Neelikonampalayam, Coimbatore | Ph: 09054617008",
                new Font(Font.HELVETICA,8,Font.ITALIC,gray)));
            fc.setBorderWidthTop(1); fc.setBorderColorTop(line);
            fc.setBorderWidthBottom(0); fc.setBorderWidthLeft(0); fc.setBorderWidthRight(0);
            fc.setPadding(8); fc.setHorizontalAlignment(Element.ALIGN_CENTER); ft.addCell(fc);
            doc.add(ft);
            doc.close();

            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"Quotation-" + safe(q.getQuotationNumber()) + ".pdf\"")
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .body(out.toByteArray());

        } catch (Exception e) { throw new RuntimeException("PDF generation failed: " + e.getMessage()); }
    }

    // ── helpers ───────────────────────────────────────────────
    private void addTotRow(PdfPTable t, String label, String val, Font f, Color line) {
        PdfPCell l = new PdfPCell(new Phrase(label,f)); l.setPadding(7); l.setBorderColor(line); t.addCell(l);
        PdfPCell v = new PdfPCell(new Phrase(val,f)); v.setPadding(7); v.setHorizontalAlignment(Element.ALIGN_RIGHT); v.setBorderColor(line); t.addCell(v);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String,Object>> parseItems(String json) {
        if (json==null||json.isBlank()) return List.of();
        try { return mapper.readValue(json, List.class); } catch (Exception e) { return List.of(); }
    }

    private String safe(String s) { return s!=null?s:"—"; }
    private String str(Object o, String fallback) {
        if (o==null) return fallback;
        String s = o.toString();
        return s.isBlank() ? fallback : s;
    }
    private String fmt(BigDecimal v) { return v!=null?String.format("%,.2f",v):"0.00"; }
}
