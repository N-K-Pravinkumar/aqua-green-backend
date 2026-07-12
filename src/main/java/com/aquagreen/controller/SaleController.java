package com.aquagreen.controller;

import com.aquagreen.dto.ApiResponse;
import com.aquagreen.model.*;
import com.aquagreen.repository.*;
import com.lowagie.text.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import com.lowagie.text.pdf.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController @RequestMapping("/api/sales") @RequiredArgsConstructor @Slf4j
public class SaleController {

    private final SaleRepository repo;
    private final ProductRepository productRepo;

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @GetMapping public ResponseEntity<ApiResponse<Page<Sale>>> getAll(
            @RequestParam(defaultValue="0")  int page,
            @RequestParam(defaultValue="20") int size) {
        return ResponseEntity.ok(ApiResponse.success("OK", repo.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size))));
    }
    @GetMapping("/stats") public ResponseEntity<ApiResponse<Map<String,Object>>> stats() {
        return ResponseEntity.ok(ApiResponse.success("OK",
            Map.of("totalRevenue", repo.sumTotalRevenue(),
                   "monthlyRevenue", repo.sumMonthlyRevenue(),
                   "totalSales", repo.count())));
    }
    @GetMapping("/{id}") public ResponseEntity<ApiResponse<Sale>> getById(@PathVariable Long id) {
        return repo.findById(id).map(s->ResponseEntity.ok(ApiResponse.success("OK",s))).orElse(ResponseEntity.notFound().build());
    }

    /** Create sale — automatically deducts product stock */
    @PostMapping
    public ResponseEntity<ApiResponse<Sale>> create(@RequestBody Sale s) {
        s.setId(null);
        if (s.getInvoiceNumber() == null)
            s.setInvoiceNumber("INV-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"))
                + "-" + String.format("%04d",(int)(Math.random()*9000)+1000));

        // Deduct stock from product
        if (s.getProduct() != null && s.getProduct().getId() != null) {
            productRepo.findById(s.getProduct().getId()).ifPresent(p -> {
                if (p.getStock() != null) {
                    int qty = s.getQuantity() != null ? s.getQuantity() : 1;
                    p.setStock(Math.max(0, p.getStock() - qty));
                    productRepo.save(p);
                    s.setStockDeducted(true);
                    log.info("Stock deducted: {} product '{}' → {} remaining", qty, p.getName(), p.getStock());
                }
            });
        }

        Sale saved = repo.save(s);
        return ResponseEntity.ok(ApiResponse.success("Sale created. Invoice: " + saved.getInvoiceNumber(), saved));
    }

    @PutMapping("/{id}") public ResponseEntity<ApiResponse<Sale>> update(@PathVariable Long id, @RequestBody Sale s) {
        s.setId(id); return ResponseEntity.ok(ApiResponse.success("Updated", repo.save(s)));
    }
    @DeleteMapping("/{id}") public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        repo.deleteById(id); return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }

    /** Generate sales invoice PDF */
    @GetMapping("/{id}/invoice/pdf")
    public ResponseEntity<byte[]> invoicePdf(@PathVariable Long id) {
        Sale s = repo.findById(id).orElseThrow(() -> new RuntimeException("Sale not found"));
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 50, 50, 60, 50);
            PdfWriter.getInstance(doc, out); doc.open();

            Color green = new Color(10,79,60); Color lt = new Color(224,245,236);
            Color gray = new Color(100,100,100); Color dark = new Color(30,30,30); Color line = new Color(220,220,220);

            Font comp = new Font(Font.HELVETICA,18,Font.BOLD,green);
            Font h2   = new Font(Font.HELVETICA,12,Font.BOLD,dark);
            Font norm = new Font(Font.HELVETICA,10,Font.NORMAL,dark);
            Font sm   = new Font(Font.HELVETICA,9,Font.NORMAL,gray);
            Font bd   = new Font(Font.HELVETICA,10,Font.BOLD,dark);
            Font wh   = new Font(Font.HELVETICA,9,Font.BOLD,Color.WHITE);

            // Header
            PdfPTable hdr = new PdfPTable(2); hdr.setWidthPercentage(100); hdr.setSpacingAfter(10); hdr.setWidths(new float[]{2f,1f});
            PdfPCell lc = new PdfPCell(); lc.setBorder(Rectangle.NO_BORDER); lc.setPadding(4);
            lc.addElement(new Paragraph("AQUA GREEN AGENCIES", comp));
            lc.addElement(new Paragraph("Near ESI Hospital, Neelikonampalayam, Coimbatore — 641033", sm));
            lc.addElement(new Paragraph("Phone: 09054617008 | GST: 33XXXXXXXXXXXXXX", sm));
            hdr.addCell(lc);
            PdfPCell rc = new PdfPCell(); rc.setBorder(Rectangle.NO_BORDER); rc.setHorizontalAlignment(Element.ALIGN_RIGHT); rc.setPadding(4);
            rc.addElement(new Paragraph("SALES INVOICE", new Font(Font.HELVETICA,14,Font.BOLD,green)));
            rc.addElement(new Paragraph("Invoice #: " + (s.getInvoiceNumber()!=null?s.getInvoiceNumber():"—"), bd));
            rc.addElement(new Paragraph("Date: " + (s.getCreatedAt()!=null?s.getCreatedAt().format(D):LocalDate.now().format(D)), sm));
            hdr.addCell(rc); doc.add(hdr);

            // Divider
            PdfPTable dv = new PdfPTable(1); dv.setWidthPercentage(100); dv.setSpacingAfter(12);
            PdfPCell dc = new PdfPCell(); dc.setBackgroundColor(green); dc.setFixedHeight(3); dc.setBorder(Rectangle.NO_BORDER); dv.addCell(dc); doc.add(dv);

            // Bill to
            PdfPTable info = new PdfPTable(2); info.setWidthPercentage(100); info.setSpacingAfter(16); info.setWidths(new float[]{1f,1f});
            PdfPCell bt = new PdfPCell(); bt.setBorder(Rectangle.BOX); bt.setBorderColor(line); bt.setPadding(10); bt.setBackgroundColor(lt);
            bt.addElement(new Paragraph("BILL TO", new Font(Font.HELVETICA,8,Font.BOLD,gray)));
            bt.addElement(new Paragraph(s.getCustomerName()!=null?s.getCustomerName():"—", h2));
            if (s.getCustomerMobile()!=null) bt.addElement(new Paragraph("Mobile: "+s.getCustomerMobile(), norm));
            if (s.getCustomerAddress()!=null) bt.addElement(new Paragraph(s.getCustomerAddress(), sm));
            info.addCell(bt);
            PdfPCell pd = new PdfPCell(); pd.setBorder(Rectangle.BOX); pd.setBorderColor(line); pd.setPadding(10);
            pd.addElement(new Paragraph("SOLD BY", new Font(Font.HELVETICA,8,Font.BOLD,gray)));
            pd.addElement(new Paragraph(s.getSalesPerson()!=null?s.getSalesPerson():"—", norm));
            pd.addElement(new Paragraph("Payment: "+(s.getPaymentMethod()!=null?s.getPaymentMethod():"—"), norm));
            pd.addElement(new Paragraph("Status: "+(s.getPaymentStatus()!=null?s.getPaymentStatus():"—"), norm));
            info.addCell(pd); doc.add(info);

            // Items
            PdfPTable items = new PdfPTable(4); items.setWidthPercentage(100); items.setWidths(new float[]{3f,1f,1.5f,1.5f}); items.setSpacingAfter(12);
            for (String h : new String[]{"Product","Qty","Unit Price","Total"}) {
                PdfPCell hc = new PdfPCell(new Phrase(h,wh)); hc.setBackgroundColor(green); hc.setPadding(8); hc.setBorder(Rectangle.NO_BORDER); items.addCell(hc);
            }
            BigDecimal total = s.getTotalAmount()!=null?s.getTotalAmount():BigDecimal.ZERO;
            BigDecimal unit  = s.getUnitPrice()!=null?s.getUnitPrice():total;
            int qty = s.getQuantity()!=null?s.getQuantity():1;
            for (String v : new String[]{s.getProductName()!=null?s.getProductName():"—", String.valueOf(qty), "₹"+fmt(unit), "₹"+fmt(total)}) {
                PdfPCell c = new PdfPCell(new Phrase(v,norm)); c.setPadding(8); c.setBorderColor(line); c.setBorderWidth(0.4f); items.addCell(c);
            }
            doc.add(items);

            // Total box
            PdfPTable tot = new PdfPTable(2); tot.setWidthPercentage(40); tot.setHorizontalAlignment(Element.ALIGN_RIGHT); tot.setSpacingAfter(24);
            if (s.getDiscountAmount()!=null && s.getDiscountAmount().compareTo(BigDecimal.ZERO)>0) {
                PdfPCell dl=new PdfPCell(new Phrase("Discount",norm)); dl.setPadding(7); dl.setBorderColor(line); tot.addCell(dl);
                PdfPCell dv2=new PdfPCell(new Phrase("-₹"+fmt(s.getDiscountAmount()),norm)); dv2.setPadding(7); dv2.setHorizontalAlignment(Element.ALIGN_RIGHT); dv2.setBorderColor(line); tot.addCell(dv2);
            }
            if (s.getGstAmount()!=null && s.getGstAmount().compareTo(BigDecimal.ZERO)>0) {
                PdfPCell gl=new PdfPCell(new Phrase("GST",norm)); gl.setPadding(7); gl.setBorderColor(line); tot.addCell(gl);
                PdfPCell gv=new PdfPCell(new Phrase("₹"+fmt(s.getGstAmount()),norm)); gv.setPadding(7); gv.setHorizontalAlignment(Element.ALIGN_RIGHT); gv.setBorderColor(line); tot.addCell(gv);
            }
            PdfPCell tl=new PdfPCell(new Phrase("TOTAL",new Font(Font.HELVETICA,12,Font.BOLD,Color.WHITE))); tl.setBackgroundColor(green); tl.setPadding(10); tl.setBorder(Rectangle.NO_BORDER); tot.addCell(tl);
            PdfPCell tv=new PdfPCell(new Phrase("₹"+fmt(total),new Font(Font.HELVETICA,14,Font.BOLD,Color.WHITE))); tv.setBackgroundColor(green); tv.setPadding(10); tv.setHorizontalAlignment(Element.ALIGN_RIGHT); tv.setBorder(Rectangle.NO_BORDER); tot.addCell(tv);
            doc.add(tot);

            if (s.getNotes()!=null && !s.getNotes().isBlank()) {
                doc.add(new Paragraph("Notes: " + s.getNotes(), sm));
            }
            PdfPTable ft=new PdfPTable(1); ft.setWidthPercentage(100);
            PdfPCell fc=new PdfPCell(new Phrase("Thank you for your purchase | Aqua Green Agencies | "+LocalDateTime.now().format(DT), new Font(Font.HELVETICA,8,Font.ITALIC,gray)));
            fc.setBorderWidthTop(1); fc.setBorderColorTop(line); fc.setBorderWidthBottom(0); fc.setBorderWidthLeft(0); fc.setBorderWidthRight(0); fc.setPadding(8); fc.setHorizontalAlignment(Element.ALIGN_CENTER); ft.addCell(fc); doc.add(ft);
            doc.close();

            return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"Invoice-"+s.getInvoiceNumber()+".pdf\"")
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .body(out.toByteArray());
        } catch (Exception e) { throw new RuntimeException("PDF failed: "+e.getMessage()); }
    }

    private String fmt(BigDecimal v) { return v!=null?String.format("%,.2f",v):"0.00"; }
}
