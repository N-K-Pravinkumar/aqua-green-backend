package com.aquagreen.controller;

import com.aquagreen.dto.ApiResponse;
import com.aquagreen.model.Employee;
import com.aquagreen.model.ServiceRequest;
import com.aquagreen.repository.EmployeeRepository;
import com.aquagreen.repository.ServiceRequestRepository;
import com.aquagreen.repository.SaleRepository;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
@Slf4j
public class EmployeeReportController {

    private final EmployeeRepository employeeRepo;
    private final ServiceRequestRepository serviceRequestRepo;
    private final SaleRepository saleRepo;

    // ── Employee stats ──────────────────────────────────────────
    @GetMapping("/{id}/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats(@PathVariable Long id) {
        Employee emp = employeeRepo.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
        String name = emp.getName();

        List<ServiceRequest> allServices = serviceRequestRepo.findAllByOrderByCreatedAtDesc();
        List<ServiceRequest> mine = allServices.stream()
            .filter(s -> name.equals(s.getAssignedTechnician()))
            .collect(Collectors.toList());

        long total = mine.size();
        long completed = mine.stream().filter(s -> "COMPLETED".equals(s.getStatus())).count();
        long pending = mine.stream().filter(s -> "PENDING".equals(s.getStatus())).count();
        long inProgress = mine.stream().filter(s -> "IN_PROGRESS".equals(s.getStatus())).count();
        long assigned = mine.stream().filter(s -> "ASSIGNED".equals(s.getStatus())).count();

        // This month
        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0);
        long thisMonth = mine.stream().filter(s -> s.getCreatedAt() != null && s.getCreatedAt().isAfter(monthStart)).count();

        java.math.BigDecimal revenue = mine.stream()
            .filter(s -> "COMPLETED".equals(s.getStatus()) && s.getServiceCharge() != null)
            .map(ServiceRequest::getServiceCharge)
            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("employeeId", id);
        stats.put("employeeName", name);
        stats.put("totalServices", total);
        stats.put("completed", completed);
        stats.put("pending", pending);
        stats.put("inProgress", inProgress);
        stats.put("assigned", assigned);
        stats.put("thisMonth", thisMonth);
        stats.put("completionRate", total > 0 ? Math.round((completed * 100.0) / total) : 0);
        stats.put("totalRevenue", revenue);
        stats.put("recentServices", mine.stream().limit(10).collect(Collectors.toList()));
        return ResponseEntity.ok(ApiResponse.success("OK", stats));
    }

    // ── Employee service history with date filter ───────────────
    @GetMapping("/{id}/services")
    public ResponseEntity<ApiResponse<List<ServiceRequest>>> getServices(
            @PathVariable Long id,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        Employee emp = employeeRepo.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
        String name = emp.getName();

        List<ServiceRequest> all = serviceRequestRepo.findAllByOrderByCreatedAtDesc();
        List<ServiceRequest> filtered = all.stream()
            .filter(s -> name.equals(s.getAssignedTechnician()))
            .filter(s -> from == null || (s.getCreatedAt() != null && !s.getCreatedAt().isBefore(from)))
            .filter(s -> to == null || (s.getCreatedAt() != null && !s.getCreatedAt().isAfter(to)))
            .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("OK", filtered));
    }

    // ── Generate Employee Report PDF (bank statement style) ─────
    @GetMapping("/{id}/report/pdf")
    public ResponseEntity<byte[]> generateReportPdf(
            @PathVariable Long id,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        Employee emp = employeeRepo.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
        String name = emp.getName();
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd-MM-yyyy");
        DateTimeFormatter sdtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        List<ServiceRequest> all = serviceRequestRepo.findAllByOrderByCreatedAtDesc();
        List<ServiceRequest> services = all.stream()
            .filter(s -> name.equals(s.getAssignedTechnician()))
            .filter(s -> from == null || (s.getCreatedAt() != null && !s.getCreatedAt().isBefore(from)))
            .filter(s -> to == null || (s.getCreatedAt() != null && !s.getCreatedAt().isAfter(to)))
            .collect(Collectors.toList());

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            com.lowagie.text.Document doc = new com.lowagie.text.Document(PageSize.A4, 50, 50, 60, 50);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            doc.open();

            Color brandGreen = new Color(10, 79, 60);
            Color lightGreen = new Color(224, 245, 236);
            Color darkGray = new Color(51, 51, 51);
            Color midGray = new Color(120, 120, 120);
            Color lineGray = new Color(220, 220, 220);

            // ── Header ──
            Font co = new Font(Font.HELVETICA, 22, Font.BOLD, brandGreen);
            Paragraph comp = new Paragraph("AQUA GREEN AGENCIES", co);
            comp.setAlignment(Element.ALIGN_CENTER);
            doc.add(comp);

            Font sf = new Font(Font.HELVETICA, 9, Font.NORMAL, midGray);
            Paragraph info = new Paragraph("Near ESI Hospital, Neelikonampalayam, Coimbatore — 641033 | Ph: 09054617008", sf);
            info.setAlignment(Element.ALIGN_CENTER);
            doc.add(info);
            doc.add(new Paragraph(" "));

            // ── Title bar ──
            PdfPTable titleTable = new PdfPTable(1);
            titleTable.setWidthPercentage(100);
            titleTable.setSpacingAfter(16);
            PdfPCell titleCell = new PdfPCell(new Phrase("EMPLOYEE SERVICE REPORT",
                new Font(Font.HELVETICA, 13, Font.BOLD, Color.WHITE)));
            titleCell.setBackgroundColor(brandGreen);
            titleCell.setPadding(10);
            titleCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            titleCell.setBorder(Rectangle.NO_BORDER);
            titleTable.addCell(titleCell);
            doc.add(titleTable);

            // ── Employee info box ──
            PdfPTable empTable = new PdfPTable(2);
            empTable.setWidthPercentage(100);
            empTable.setSpacingAfter(16);
            empTable.setWidths(new float[]{1, 1});

            Font lf = new Font(Font.HELVETICA, 10, Font.BOLD, darkGray);
            Font vf = new Font(Font.HELVETICA, 10, Font.NORMAL, darkGray);

            String fromStr = from != null ? from.format(dtf) : "All time";
            String toStr = to != null ? to.format(dtf) : "Present";

            String[][] empInfo = {
                {"Employee Name", emp.getName()},
                {"Employee ID", "EMP-" + String.format("%03d", emp.getId())},
                {"Role / Department", emp.getRole() != null ? emp.getRole() : "Technician"},
                {"Mobile", emp.getMobile() != null ? emp.getMobile() : "—"},
                {"Report Period", fromStr + " to " + toStr},
                {"Generated On", LocalDateTime.now().format(sdtf)},
            };

            for (String[] row : empInfo) {
                addInfoCell(empTable, row[0], row[1], lf, vf);
            }
            doc.add(empTable);

            // ── Summary cards ──
            long totalSvcs = services.size();
            long completedSvcs = services.stream().filter(s->"COMPLETED".equals(s.getStatus())).count();
            long pendingSvcs = services.stream().filter(s->"PENDING".equals(s.getStatus())).count();
            java.math.BigDecimal totalRev = services.stream()
                .filter(s->"COMPLETED".equals(s.getStatus()) && s.getServiceCharge()!=null)
                .map(ServiceRequest::getServiceCharge)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

            PdfPTable summaryTable = new PdfPTable(4);
            summaryTable.setWidthPercentage(100);
            summaryTable.setSpacingAfter(20);

            String[][] summary = {
                {"Total Services", String.valueOf(totalSvcs)},
                {"Completed", String.valueOf(completedSvcs)},
                {"Pending", String.valueOf(pendingSvcs)},
                {"Total Revenue", "₹" + totalRev.toPlainString()},
            };
            for (String[] s : summary) {
                PdfPCell c = new PdfPCell();
                c.setBorder(Rectangle.BOX);
                c.setBorderColor(lineGray);
                c.setPadding(12);
                c.setBackgroundColor(lightGreen);
                Paragraph label = new Paragraph(s[0], new Font(Font.HELVETICA, 9, Font.NORMAL, midGray));
                Paragraph val = new Paragraph(s[1], new Font(Font.HELVETICA, 16, Font.BOLD, brandGreen));
                c.addElement(label);
                c.addElement(val);
                summaryTable.addCell(c);
            }
            doc.add(summaryTable);

            // ── Service Records Table ──
            Font tableHead = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
            Font tableBody = new Font(Font.HELVETICA, 8, Font.NORMAL, darkGray);

            PdfPTable srvTable = new PdfPTable(7);
            srvTable.setWidthPercentage(100);
            srvTable.setWidths(new float[]{1.2f, 2.2f, 2.5f, 1.5f, 1.5f, 1.5f, 1.5f});
            srvTable.setSpacingAfter(16);

            for (String h : new String[]{"Ticket", "Customer", "Issue", "Product", "Status", "Charge", "Date"}) {
                PdfPCell hc = new PdfPCell(new Phrase(h, tableHead));
                hc.setBackgroundColor(brandGreen);
                hc.setPadding(7);
                hc.setBorder(Rectangle.NO_BORDER);
                srvTable.addCell(hc);
            }

            boolean alt = false;
            for (ServiceRequest s : services) {
                Color rowBg = alt ? new Color(245, 245, 245) : Color.WHITE;
                String[] row = {
                    s.getTicketNumber() != null ? s.getTicketNumber() : "—",
                    s.getCustomerName() != null ? s.getCustomerName() : "—",
                    s.getIssueDescription() != null ? s.getIssueDescription().substring(0, Math.min(35, s.getIssueDescription().length())) : "—",
                    s.getProductName() != null ? s.getProductName() : "—",
                    s.getStatus() != null ? s.getStatus() : "—",
                    s.getServiceCharge() != null ? "₹" + s.getServiceCharge().toPlainString() : "₹0",
                    s.getCreatedAt() != null ? s.getCreatedAt().format(dtf) : "—",
                };
                for (String cell : row) {
                    PdfPCell tc = new PdfPCell(new Phrase(cell, tableBody));
                    tc.setPadding(6);
                    tc.setBorderColor(lineGray);
                    tc.setBackgroundColor(rowBg);
                    srvTable.addCell(tc);
                }
                alt = !alt;
            }
            doc.add(srvTable);

            if (services.isEmpty()) {
                Paragraph noData = new Paragraph("No service records found for this period.",
                    new Font(Font.HELVETICA, 11, Font.ITALIC, midGray));
                noData.setAlignment(Element.ALIGN_CENTER);
                noData.setSpacingBefore(20);
                doc.add(noData);
            }

            // ── Footer line ──
            PdfPTable footerLine = new PdfPTable(1);
            footerLine.setWidthPercentage(100);
            footerLine.setSpacingBefore(10);
            PdfPCell fc = new PdfPCell(new Phrase(
                "Report generated by Aqua Green Agencies CRM | " + LocalDateTime.now().format(sdtf) +
                " | This is a computer-generated report.",
                new Font(Font.HELVETICA, 8, Font.ITALIC, midGray)));
            fc.setBorderWidthTop(1f); fc.setBorderColorTop(lineGray);
            fc.setBorderWidthBottom(0); fc.setBorderWidthLeft(0); fc.setBorderWidthRight(0);
            fc.setPadding(8); fc.setHorizontalAlignment(Element.ALIGN_CENTER);
            footerLine.addCell(fc);
            doc.add(footerLine);

            doc.close();

            String filename = "Employee-Report-" + name.replace(" ", "-") + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".pdf";
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(out.toByteArray());

        } catch (Exception e) {
            log.error("Employee report PDF failed: {}", e.getMessage(), e);
            throw new RuntimeException("PDF generation failed: " + e.getMessage());
        }
    }

    private void addInfoCell(PdfPTable t, String label, String value, Font lf, Font vf) {
        PdfPCell lc = new PdfPCell(new Phrase(label + ":", lf));
        lc.setBorder(Rectangle.NO_BORDER); lc.setPadding(5); t.addCell(lc);
        PdfPCell vc = new PdfPCell(new Phrase(value, vf));
        vc.setBorder(Rectangle.NO_BORDER); vc.setPadding(5); t.addCell(vc);
    }
}
