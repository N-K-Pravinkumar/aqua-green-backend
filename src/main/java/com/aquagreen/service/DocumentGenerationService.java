package com.aquagreen.service;

import com.aquagreen.model.Customer;
import com.aquagreen.model.DocumentTemplate;

// OpenPDF — fully qualified to avoid ambiguity with Apache POI's Document interface
import com.lowagie.text.Chunk;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

// Apache POI — DOCX generation
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class DocumentGenerationService {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");

    /** Render all {{placeholder}} variables in a template string */
    public String renderTemplate(String template, Map<String, String> variables) {
        if (template == null) return "";
        Matcher m = PLACEHOLDER_PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String key = m.group(1).trim();
            String value = variables.getOrDefault(key, "{{" + key + "}}");
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Extract comma-separated list of placeholder names from content */
    public String extractPlaceholders(String content) {
        if (content == null) return "";
        Matcher m = PLACEHOLDER_PATTERN.matcher(content);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            if (sb.length() > 0) sb.append(",");
            sb.append(m.group(1).trim());
        }
        return sb.toString();
    }

    /** Build variable map pre-loaded with customer data + company defaults */
    public Map<String, String> buildVariables(Customer customer, Map<String, String> extra) {
        Map<String, String> vars = new HashMap<>();
        if (customer != null) {
            vars.put("customerName",  safe(customer.getName()));
            vars.put("customerId",    String.valueOf(customer.getId()));
            vars.put("mobile",        safe(customer.getMobile()));
            vars.put("email",         safe(customer.getEmail()));
            vars.put("address",       safe(customer.getAddress()));
            vars.put("city",          safe(customer.getCity()));
        }
        vars.put("companyName",    "Aqua Green Agencies");
        vars.put("companyPhone",   "09054617008");
        vars.put("companyEmail",   "info@aquagreenagencies.com");
        vars.put("companyWebsite", "www.aquagreenagencies.com");
        vars.put("date",           java.time.LocalDate.now().toString());
        vars.put("time",           java.time.LocalTime.now().toString().substring(0, 5));
        if (extra != null) vars.putAll(extra);
        return vars;
    }

    // ── Generate DOCX ────────────────────────────────────────────
    public byte[] generateDocx(DocumentTemplate template, Map<String, String> variables) {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // Company header
            XWPFParagraph h = doc.createParagraph();
            h.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun hr = h.createRun();
            hr.setText("AQUA GREEN AGENCIES");
            hr.setBold(true); hr.setFontSize(18);
            hr.setColor("0A4F3C"); hr.setFontFamily("Calibri");
            hr.addBreak();
            hr.setText("Near ESI Hospital, Neelikonampalayam, Coimbatore — 641033");
            hr.setFontSize(10); hr.setColor("666666");
            hr.addBreak();

            // Document title
            XWPFParagraph tp = doc.createParagraph();
            tp.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun tr = tp.createRun();
            tr.setText(renderTemplate(template.getName(), variables));
            tr.setBold(true); tr.setFontSize(14); tr.setFontFamily("Calibri");
            tr.addBreak();

            // Body content
            String raw = template.getHtmlContent() != null
                    ? template.getHtmlContent() : template.getMessageContent();
            String plain = raw != null
                    ? renderTemplate(raw, variables)
                        .replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim()
                    : "";

            for (String line : plain.split("\\n")) {
                if (!line.trim().isEmpty()) {
                    XWPFParagraph p = doc.createParagraph();
                    XWPFRun r = p.createRun();
                    r.setText(line.trim());
                    r.setFontFamily("Calibri"); r.setFontSize(11);
                }
            }

            // Footer
            XWPFParagraph fp = doc.createParagraph();
            fp.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun fr = fp.createRun();
            fr.addBreak();
            fr.setText("Aqua Green Agencies | Ph: 09054617008 | www.aquagreenagencies.com");
            fr.setFontSize(9); fr.setColor("888888"); fr.setFontFamily("Calibri");

            doc.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("DOCX generation failed: {}", e.getMessage(), e);
            throw new RuntimeException("DOCX generation failed: " + e.getMessage());
        }
    }

    // ── Generate PDF ─────────────────────────────────────────────
    public byte[] generatePdf(DocumentTemplate template, Map<String, String> variables) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            com.lowagie.text.Document doc =
                    new com.lowagie.text.Document(PageSize.A4, 72, 72, 72, 72);
            PdfWriter writer = PdfWriter.getInstance(doc, out);

            if (template.getWatermarkConfig() != null
                    && !template.getWatermarkConfig().isEmpty()
                    && !template.getWatermarkConfig().equals("{}")) {
                applyWatermark(writer, template);
            }

            doc.open();

            // Header
            Font hf = new Font(Font.HELVETICA, 18, Font.BOLD, new Color(10, 79, 60));
            Paragraph ph = new Paragraph("AQUA GREEN AGENCIES", hf);
            ph.setAlignment(Element.ALIGN_CENTER);
            doc.add(ph);

            Font sf = new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(100, 100, 100));
            Paragraph ps = new Paragraph(
                    "Near ESI Hospital, Neelikonampalayam, Coimbatore — 641033 | Ph: 09054617008", sf);
            ps.setAlignment(Element.ALIGN_CENTER);
            ps.setSpacingAfter(4);
            doc.add(ps);

            // Divider (table-based line — avoids LineSeparator import issue)
            doc.add(hrLine());
            doc.add(spacer());

            // Title
            Font tf = new Font(Font.HELVETICA, 14, Font.BOLD, new Color(33, 37, 41));
            Paragraph pt = new Paragraph(renderTemplate(template.getName(), variables), tf);
            pt.setAlignment(Element.ALIGN_CENTER);
            pt.setSpacingAfter(16);
            doc.add(pt);

            // Body
            String raw = template.getHtmlContent() != null
                    ? template.getHtmlContent() : template.getMessageContent();
            String plain = raw != null
                    ? renderTemplate(raw, variables)
                        .replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim()
                    : "";
            Font bf = new Font(Font.HELVETICA, 11, Font.NORMAL, new Color(33, 37, 41));
            for (String line : plain.split("\\n")) {
                if (!line.trim().isEmpty()) {
                    Paragraph p = new Paragraph(line.trim(), bf);
                    p.setSpacingAfter(8);
                    doc.add(p);
                }
            }

            // Footer
            doc.add(hrLine());
            Font ff = new Font(Font.HELVETICA, 8, Font.ITALIC, new Color(120, 120, 120));
            Paragraph pf = new Paragraph(
                    "Generated by Aqua Green Agencies CRM | "
                            + java.time.LocalDateTime.now().toString().substring(0, 16), ff);
            pf.setAlignment(Element.ALIGN_CENTER);
            pf.setSpacingBefore(6);
            doc.add(pf);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("PDF generation failed: {}", e.getMessage(), e);
            throw new RuntimeException("PDF generation failed: " + e.getMessage());
        }
    }

    // ── Generate Quotation PDF ───────────────────────────────────
    public byte[] generateQuotationPdf(Map<String, String> variables, String itemsJson) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            com.lowagie.text.Document doc =
                    new com.lowagie.text.Document(PageSize.A4, 60, 60, 60, 60);
            PdfWriter.getInstance(doc, out);
            doc.open();

            Font headFont  = new Font(Font.HELVETICA, 20, Font.BOLD, new Color(10, 79, 60));
            Font subFont   = new Font(Font.HELVETICA, 10, Font.NORMAL, new Color(100, 100, 100));
            Font labelFont = new Font(Font.HELVETICA, 10, Font.BOLD);
            Font valueFont = new Font(Font.HELVETICA, 10, Font.NORMAL);
            Font totalFont = new Font(Font.HELVETICA, 12, Font.BOLD, new Color(10, 79, 60));

            Paragraph cn = new Paragraph("AQUA GREEN AGENCIES", headFont);
            cn.setAlignment(Element.ALIGN_CENTER); doc.add(cn);

            Paragraph info = new Paragraph(
                    "Shop No 3/2, Near ESI Hospital, Neelikonampalayam, Coimbatore — 641033\n"
                    + "Ph: 09054617008 | www.aquagreenagencies.com", subFont);
            info.setAlignment(Element.ALIGN_CENTER); info.setSpacingAfter(6); doc.add(info);

            doc.add(hrLine()); doc.add(spacer());

            Paragraph title = new Paragraph("QUOTATION",
                    new Font(Font.HELVETICA, 14, Font.BOLD));
            title.setAlignment(Element.ALIGN_CENTER); title.setSpacingAfter(12); doc.add(title);

            PdfPTable infoTable = new PdfPTable(2);
            infoTable.setWidthPercentage(100); infoTable.setSpacingAfter(16);
            addCell(infoTable, "Quotation No:", v(variables,"quotationNumber","—"), labelFont, valueFont);
            addCell(infoTable, "Date:", v(variables,"date", today()), labelFont, valueFont);
            addCell(infoTable, "Customer:", v(variables,"customerName","—"), labelFont, valueFont);
            addCell(infoTable, "Mobile:", v(variables,"mobile","—"), labelFont, valueFont);
            addCell(infoTable, "Address:", v(variables,"address","—"), labelFont, valueFont);
            addCell(infoTable, "Valid Until:", "30 days from date", labelFont, valueFont);
            doc.add(infoTable);

            // Items table
            PdfPTable items = new PdfPTable(4);
            items.setWidthPercentage(100);
            items.setWidths(new float[]{3, 1, 1.5f, 1.5f});
            items.setSpacingAfter(16);
            Color greenBg = new Color(10, 79, 60);
            Font whBold = new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE);
            for (String h : new String[]{"Description","Qty","Unit Price","Total"}) {
                PdfPCell c = new PdfPCell(new com.lowagie.text.Phrase(h, whBold));
                c.setBackgroundColor(greenBg); c.setPadding(8); items.addCell(c);
            }
            String sub = v(variables,"subtotal","0");
            addItemRow(items, v(variables,"productName","RO Water Purifier"),
                    v(variables,"quantity","1"), "₹"+sub, "₹"+sub, valueFont);
            doc.add(items);

            // Totals
            PdfPTable totals = new PdfPTable(2);
            totals.setWidthPercentage(40);
            totals.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totals.setSpacingAfter(16);
            addTotalRow(totals,"Subtotal:","₹"+sub, labelFont, valueFont);
            addTotalRow(totals,"GST (18%):","₹"+v(variables,"gstAmount","0"), labelFont, valueFont);

            PdfPCell tc = new PdfPCell(new com.lowagie.text.Phrase("Grand Total:", totalFont));
            tc.setBorder(Rectangle.TOP); tc.setPadding(8); totals.addCell(tc);
            PdfPCell tv = new PdfPCell(new com.lowagie.text.Phrase(
                    "₹"+v(variables,"totalAmount",sub), totalFont));
            tv.setBorder(Rectangle.TOP); tv.setPadding(8);
            tv.setHorizontalAlignment(Element.ALIGN_RIGHT); totals.addCell(tv);
            doc.add(totals);

            doc.add(hrLine());
            Font termsFont = new Font(Font.HELVETICA, 9, Font.ITALIC, new Color(120,120,120));
            doc.add(new Paragraph(
                    "Terms & Conditions: This quotation is valid for 30 days. "
                    + "GST applicable as per government norms. "
                    + "Installation free for purchases above ₹5000.", termsFont));
            doc.add(new Paragraph("\n\nAuthorised Signatory\nAqua Green Agencies",
                    new Font(Font.HELVETICA, 10)));
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Quotation PDF failed: {}", e.getMessage(), e);
            throw new RuntimeException("Quotation PDF failed: " + e.getMessage());
        }
    }

    // ── Generate Invoice PDF ─────────────────────────────────────
    public byte[] generateInvoicePdf(Map<String, String> variables) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            com.lowagie.text.Document doc =
                    new com.lowagie.text.Document(PageSize.A4, 60, 60, 60, 60);
            PdfWriter.getInstance(doc, out);
            doc.open();

            Font headFont  = new Font(Font.HELVETICA, 20, Font.BOLD, new Color(10, 79, 60));
            Font subFont   = new Font(Font.HELVETICA, 10, Font.NORMAL, new Color(100, 100, 100));
            Font labelFont = new Font(Font.HELVETICA, 10, Font.BOLD);
            Font valueFont = new Font(Font.HELVETICA, 10, Font.NORMAL);
            Font totalFont = new Font(Font.HELVETICA, 12, Font.BOLD, new Color(10, 79, 60));

            Paragraph cn = new Paragraph("AQUA GREEN AGENCIES", headFont);
            cn.setAlignment(Element.ALIGN_CENTER); doc.add(cn);

            Paragraph info = new Paragraph(
                    "Shop No 3/2, Near ESI Hospital, Neelikonampalayam, Coimbatore — 641033\n"
                    + "Ph: 09054617008 | GST: 33XXXXX1234Z1Z5", subFont);
            info.setAlignment(Element.ALIGN_CENTER); info.setSpacingAfter(6); doc.add(info);
            doc.add(hrLine());

            Paragraph title = new Paragraph("\nTAX INVOICE\n",
                    new Font(Font.HELVETICA, 14, Font.BOLD));
            title.setAlignment(Element.ALIGN_CENTER); doc.add(title);

            PdfPTable infoTable = new PdfPTable(2);
            infoTable.setWidthPercentage(100); infoTable.setSpacingAfter(12);
            addCell(infoTable,"Invoice No:", v(variables,"invoiceNumber","AQG-INV-001"), labelFont, valueFont);
            addCell(infoTable,"Date:", v(variables,"date", today()), labelFont, valueFont);
            addCell(infoTable,"Customer:", v(variables,"customerName","—"), labelFont, valueFont);
            addCell(infoTable,"Mobile:", v(variables,"mobile","—"), labelFont, valueFont);
            addCell(infoTable,"Address:", v(variables,"address","—"), labelFont, valueFont);
            addCell(infoTable,"Payment:", v(variables,"paymentMode","—"), labelFont, valueFont);
            doc.add(infoTable);

            PdfPTable items = new PdfPTable(4);
            items.setWidthPercentage(100);
            items.setWidths(new float[]{3, 1, 1.5f, 1.5f});
            items.setSpacingAfter(12);
            Font whBold = new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE);
            Color greenBg = new Color(10, 79, 60);
            for (String h : new String[]{"Item","Qty","Rate","Amount"}) {
                PdfPCell c = new PdfPCell(new com.lowagie.text.Phrase(h, whBold));
                c.setBackgroundColor(greenBg); c.setPadding(8); items.addCell(c);
            }
            String sub = v(variables,"subtotal","0");
            addItemRow(items, v(variables,"productName","RO Water Purifier"),
                    v(variables,"quantity","1"),
                    "₹"+v(variables,"unitPrice","0"), "₹"+sub, valueFont);
            doc.add(items);

            PdfPTable totals = new PdfPTable(2);
            totals.setWidthPercentage(35);
            totals.setHorizontalAlignment(Element.ALIGN_RIGHT);
            addTotalRow(totals,"Subtotal:","₹"+sub, labelFont, valueFont);
            addTotalRow(totals,"CGST (9%):","₹"+v(variables,"cgst","0"), labelFont, valueFont);
            addTotalRow(totals,"SGST (9%):","₹"+v(variables,"sgst","0"), labelFont, valueFont);

            PdfPCell tc = new PdfPCell(new com.lowagie.text.Phrase("Total:", totalFont));
            tc.setBorder(Rectangle.TOP); tc.setPadding(8); totals.addCell(tc);
            PdfPCell tv = new PdfPCell(new com.lowagie.text.Phrase(
                    "₹"+v(variables,"totalAmount",sub), totalFont));
            tv.setBorder(Rectangle.TOP); tv.setPadding(8);
            tv.setHorizontalAlignment(Element.ALIGN_RIGHT); totals.addCell(tv);
            doc.add(totals);

            doc.add(hrLine());
            doc.add(new Paragraph("\nE.&O.E. Goods once sold will not be taken back.",
                    new Font(Font.HELVETICA, 9, Font.ITALIC, new Color(120,120,120))));
            doc.add(new Paragraph("\n\nAuthorised Signatory\nAqua Green Agencies",
                    new Font(Font.HELVETICA, 10)));
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Invoice PDF failed: " + e.getMessage());
        }
    }

    // ── Helpers ──────────────────────────────────────────────────

    /** Horizontal rule using a 1-row PdfPTable — no LineSeparator needed */
    private PdfPTable hrLine() {
        PdfPTable line = new PdfPTable(1);
        line.setWidthPercentage(100);
        line.setSpacingBefore(4);
        line.setSpacingAfter(4);
        PdfPCell cell = new PdfPCell();
        cell.setBorderWidthTop(1.5f);
        cell.setBorderWidthBottom(0);
        cell.setBorderWidthLeft(0);
        cell.setBorderWidthRight(0);
        cell.setBorderColorTop(new Color(10, 79, 60));
        cell.setMinimumHeight(0);
        cell.setPaddingTop(0);
        cell.setPaddingBottom(0);
        line.addCell(cell);
        return line;
    }

    private Paragraph spacer() {
        Paragraph p = new Paragraph(" ");
        p.setSpacingAfter(4);
        return p;
    }

    private void applyWatermark(PdfWriter writer, DocumentTemplate template) {
        writer.setPageEvent(new PdfPageEventHelper() {
            @Override
            public void onEndPage(PdfWriter w, com.lowagie.text.Document d) {
                try {
                    String config = template.getWatermarkConfig();
                    String wmText = "AQUA GREEN";
                    if (config != null && config.contains("\"text\"")) {
                        int start = config.indexOf("\"text\"") + 8;
                        int end = config.indexOf("\"", start);
                        if (end > start) wmText = config.substring(start, end);
                    }
                    PdfContentByte cb = w.getDirectContentUnder();
                    cb.saveState();
                    BaseFont bf = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, false);
                    cb.setColorFill(new Color(10, 79, 60, 25));
                    cb.beginText();
                    cb.setFontAndSize(bf, 52);
                    cb.showTextAligned(Element.ALIGN_CENTER, wmText,
                            d.getPageSize().getWidth() / 2,
                            d.getPageSize().getHeight() / 2, 45);
                    cb.endText();
                    cb.restoreState();
                } catch (Exception e) {
                    log.warn("Watermark failed: {}", e.getMessage());
                }
            }
        });
    }

    private void addCell(PdfPTable t, String label, String value, Font lf, Font vf) {
        PdfPCell lc = new PdfPCell(new com.lowagie.text.Phrase(label, lf));
        lc.setBorder(Rectangle.NO_BORDER); lc.setPadding(4); t.addCell(lc);
        PdfPCell vc = new PdfPCell(new com.lowagie.text.Phrase(value, vf));
        vc.setBorder(Rectangle.NO_BORDER); vc.setPadding(4); t.addCell(vc);
    }

    private void addItemRow(PdfPTable t, String desc, String qty, String price, String total, Font f) {
        t.addCell(new PdfPCell(new com.lowagie.text.Phrase(desc, f)));
        PdfPCell qc = new PdfPCell(new com.lowagie.text.Phrase(qty, f));
        qc.setHorizontalAlignment(Element.ALIGN_CENTER); t.addCell(qc);
        PdfPCell pc = new PdfPCell(new com.lowagie.text.Phrase(price, f));
        pc.setHorizontalAlignment(Element.ALIGN_RIGHT); t.addCell(pc);
        PdfPCell tc = new PdfPCell(new com.lowagie.text.Phrase(total, f));
        tc.setHorizontalAlignment(Element.ALIGN_RIGHT); t.addCell(tc);
    }

    private void addTotalRow(PdfPTable t, String label, String value, Font lf, Font vf) {
        PdfPCell lc = new PdfPCell(new com.lowagie.text.Phrase(label, lf));
        lc.setBorder(Rectangle.NO_BORDER); lc.setPadding(4); t.addCell(lc);
        PdfPCell vc = new PdfPCell(new com.lowagie.text.Phrase(value, vf));
        vc.setBorder(Rectangle.NO_BORDER); vc.setPadding(4);
        vc.setHorizontalAlignment(Element.ALIGN_RIGHT); t.addCell(vc);
    }

    private String v(Map<String,String> m, String key, String def) {
        return m.getOrDefault(key, def);
    }

    private String today() { return java.time.LocalDate.now().toString(); }
    private String safe(String s) { return s != null ? s : ""; }
}
