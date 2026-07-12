package com.aquagreen.controller;

import com.aquagreen.model.*;
import com.aquagreen.repository.*;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import org.apache.poi.ss.usermodel.Font;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Slf4j
public class ReportController {

    private final SaleRepository saleRepo;
    private final ServiceRequestRepository serviceRequestRepo;
    private final LeadRepository leadRepo;
    private final CustomerRepository customerRepo;
    private final PaymentRepository paymentRepo;
    private final EnquiryRepository enquiryRepo;
    private final QuotationRepository quotationRepo;
    private final EmployeeRepository employeeRepo;

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
    private static final DateTimeFormatter D  = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter FILE = DateTimeFormatter.ofPattern("yyyyMMdd");

    // ── SALES REPORT ──────────────────────────────────────────────
    @GetMapping("/sales/pdf")
    public ResponseEntity<byte[]> salesPdf(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        List<Sale> data = filterByDate(saleRepo.findAllByOrderByCreatedAtDesc(), from, to);
        BigDecimal total = data.stream().map(s -> s.getTotalAmount() != null ? s.getTotalAmount() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paid = data.stream().filter(s -> "PAID".equals(s.getPaymentStatus())).map(s -> s.getTotalAmount() != null ? s.getTotalAmount() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pending = total.subtract(paid);

        byte[] pdf = buildPdf("SALES REPORT", from, to,
            new String[]{"Invoice No","Customer","Product","Qty","Amount","Status","Method","Date"},
            new float[]{1.4f,1.8f,2f,0.6f,1.2f,1f,1f,1.2f},
            data.stream().map(s -> new String[]{
                safe(s.getInvoiceNumber()), safe(s.getCustomerName()), safe(s.getProductName()),
                String.valueOf(s.getQuantity() != null ? s.getQuantity() : 1),
                "₹" + fmt(s.getTotalAmount()), safe(s.getPaymentStatus()),
                safe(s.getPaymentMethod()), fmtDate(s.getCreatedAt())
            }).collect(Collectors.toList()),
            Map.of("Total Sales", "₹"+fmt(total), "Paid", "₹"+fmt(paid), "Pending", "₹"+fmt(pending), "Records", String.valueOf(data.size()))
        );
        return pdfResponse(pdf, "Sales-Report-" + fileDateRange(from, to));
    }

    @GetMapping("/sales/excel")
    public ResponseEntity<byte[]> salesExcel(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        List<Sale> data = filterByDate(saleRepo.findAllByOrderByCreatedAtDesc(), from, to);
        BigDecimal total = data.stream().map(s -> s.getTotalAmount() != null ? s.getTotalAmount() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);

        byte[] excel = buildExcel("Sales Report", from, to,
            new String[]{"Invoice No","Customer","Product","Qty","Unit Price","Total Amount","Payment Status","Payment Method","Sales Person","Date"},
            data.stream().map(s -> new Object[]{
                safe(s.getInvoiceNumber()), safe(s.getCustomerName()), safe(s.getProductName()),
                s.getQuantity(), numVal(s.getUnitPrice()), numVal(s.getTotalAmount()),
                safe(s.getPaymentStatus()), safe(s.getPaymentMethod()),
                safe(s.getSalesPerson()), fmtDate(s.getCreatedAt())
            }).collect(Collectors.toList()),
            new int[]{4,5}, // currency column indices
            Map.of("Total Revenue", total, "Total Records", (double)data.size())
        );
        return excelResponse(excel, "Sales-Report-" + fileDateRange(from, to));
    }

    // ── SERVICE REQUEST REPORT ────────────────────────────────────
    @GetMapping("/services/pdf")
    public ResponseEntity<byte[]> servicesPdf(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        List<ServiceRequest> data = filterByDate(serviceRequestRepo.findAllByOrderByCreatedAtDesc(), from, to);
        long completed = data.stream().filter(s -> "COMPLETED".equals(s.getStatus())).count();
        long pending   = data.stream().filter(s -> "PENDING".equals(s.getStatus())).count();
        BigDecimal revenue = data.stream().filter(s -> "COMPLETED".equals(s.getStatus()))
            .map(s -> s.getServiceCharge() != null ? s.getServiceCharge() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        byte[] pdf = buildPdf("SERVICE REQUEST REPORT", from, to,
            new String[]{"Ticket","Customer","Mobile","Product","Issue","Technician","Charge","Status","Date"},
            new float[]{1.2f,1.6f,1.2f,1.6f,2f,1.4f,1f,1f,1.2f},
            data.stream().map(s -> new String[]{
                safe(s.getTicketNumber()), safe(s.getCustomerName()), safe(s.getCustomerMobile()),
                safe(s.getProductName()), truncate(s.getIssueDescription(), 30),
                safe(s.getAssignedTechnician()), "₹"+fmt(s.getServiceCharge()),
                safe(s.getStatus()), fmtDate(s.getCreatedAt())
            }).collect(Collectors.toList()),
            Map.of("Total", String.valueOf(data.size()), "Completed", String.valueOf(completed),
                   "Pending", String.valueOf(pending), "Revenue", "₹"+fmt(revenue))
        );
        return pdfResponse(pdf, "Service-Report-" + fileDateRange(from, to));
    }

    @GetMapping("/services/excel")
    public ResponseEntity<byte[]> servicesExcel(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        List<ServiceRequest> data = filterByDate(serviceRequestRepo.findAllByOrderByCreatedAtDesc(), from, to);
        byte[] excel = buildExcel("Service Report", from, to,
            new String[]{"Ticket No","Customer","Mobile","Product","Issue Description","Assigned Technician","Service Charge","Status","Priority","Date","Completed Date"},
            data.stream().map(s -> new Object[]{
                safe(s.getTicketNumber()), safe(s.getCustomerName()), safe(s.getCustomerMobile()),
                safe(s.getProductName()), safe(s.getIssueDescription()),
                safe(s.getAssignedTechnician()), numVal(s.getServiceCharge()),
                safe(s.getStatus()), safe(s.getPriority()),
                fmtDate(s.getCreatedAt()), fmtDate(s.getCompletedAt())
            }).collect(Collectors.toList()),
            new int[]{6}, Map.of()
        );
        return excelResponse(excel, "Service-Report-" + fileDateRange(from, to));
    }

    // ── LEADS REPORT ──────────────────────────────────────────────
    @GetMapping("/leads/pdf")
    public ResponseEntity<byte[]> leadsPdf(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        List<Lead> data = filterByDate(leadRepo.findAllByOrderByCreatedAtDesc(), from, to);
        long converted = data.stream().filter(l -> "CONVERTED".equals(l.getStatus())).count();
        long lost      = data.stream().filter(l -> "LOST".equals(l.getStatus())).count();

        byte[] pdf = buildPdf("LEADS REPORT", from, to,
            new String[]{"Name","Mobile","City","Requirement","Source","Assigned To","Status","Date"},
            new float[]{1.6f,1.3f,1.2f,2.2f,1f,1.3f,1f,1.2f},
            data.stream().map(l -> new String[]{
                safe(l.getName()), safe(l.getMobile()), safe(l.getCity()),
                truncate(l.getRequirement(), 30), safe(l.getSource()),
                safe(l.getAssignedEmployee()), safe(l.getStatus()), fmtDate(l.getCreatedAt())
            }).collect(Collectors.toList()),
            Map.of("Total Leads", String.valueOf(data.size()), "Converted", String.valueOf(converted),
                   "Lost", String.valueOf(lost), "Conversion Rate",
                   data.isEmpty() ? "0%" : Math.round(converted*100.0/data.size())+"%")
        );
        return pdfResponse(pdf, "Leads-Report-" + fileDateRange(from, to));
    }

    @GetMapping("/leads/excel")
    public ResponseEntity<byte[]> leadsExcel(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        List<Lead> data = filterByDate(leadRepo.findAllByOrderByCreatedAtDesc(), from, to);
        byte[] excel = buildExcel("Leads Report", from, to,
            new String[]{"Name","Email","Mobile","City","Requirement","Source","Assigned Employee","Status","Notes","Created Date","Updated Date"},
            data.stream().map(l -> new Object[]{
                safe(l.getName()), safe(l.getEmail()), safe(l.getMobile()), safe(l.getCity()),
                safe(l.getRequirement()), safe(l.getSource()), safe(l.getAssignedEmployee()),
                safe(l.getStatus()), safe(l.getNotes()),
                fmtDate(l.getCreatedAt()), fmtDate(l.getUpdatedAt())
            }).collect(Collectors.toList()),
            new int[]{}, Map.of()
        );
        return excelResponse(excel, "Leads-Report-" + fileDateRange(from, to));
    }

    // ── CUSTOMER REPORT ───────────────────────────────────────────
    @GetMapping("/customers/pdf")
    public ResponseEntity<byte[]> customersPdf(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        List<Customer> data = filterByDate(customerRepo.findAllByOrderByCreatedAtDesc(), from, to);
        long residential = data.stream().filter(c -> "RESIDENTIAL".equals(c.getCustomerType())).count();
        long commercial  = data.stream().filter(c -> "COMMERCIAL".equals(c.getCustomerType())).count();

        byte[] pdf = buildPdf("CUSTOMER REPORT", from, to,
            new String[]{"Name","Mobile","Email","City","Type","Address","Date Joined"},
            new float[]{1.8f,1.3f,2f,1.2f,1.1f,2.2f,1.2f},
            data.stream().map(c -> new String[]{
                safe(c.getName()), safe(c.getMobile()), safe(c.getEmail()),
                safe(c.getCity()), safe(c.getCustomerType()),
                truncate(c.getAddress(), 35), fmtDate(c.getCreatedAt())
            }).collect(Collectors.toList()),
            Map.of("Total Customers", String.valueOf(data.size()), "Residential", String.valueOf(residential), "Commercial", String.valueOf(commercial))
        );
        return pdfResponse(pdf, "Customer-Report-" + fileDateRange(from, to));
    }

    @GetMapping("/customers/excel")
    public ResponseEntity<byte[]> customersExcel(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        List<Customer> data = filterByDate(customerRepo.findAllByOrderByCreatedAtDesc(), from, to);
        byte[] excel = buildExcel("Customer Report", from, to,
            new String[]{"ID","Full Name","Mobile","Email","Address","City","Customer Type","GST Number","Date Joined"},
            data.stream().map(c -> new Object[]{
                c.getId(), safe(c.getName()), safe(c.getMobile()), safe(c.getEmail()),
                safe(c.getAddress()), safe(c.getCity()), safe(c.getCustomerType()),
                safe(c.getGstNumber()), fmtDate(c.getCreatedAt())
            }).collect(Collectors.toList()),
            new int[]{}, Map.of()
        );
        return excelResponse(excel, "Customer-Report-" + fileDateRange(from, to));
    }

    // ── PAYMENT / REVENUE REPORT ──────────────────────────────────
    @GetMapping("/payments/pdf")
    public ResponseEntity<byte[]> paymentsPdf(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        List<Payment> data = filterByDate(paymentRepo.findAllByOrderByCreatedAtDesc(), from, to);
        BigDecimal total = data.stream().map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cash  = data.stream().filter(p -> "CASH".equals(p.getPaymentMethod())).map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal upi   = data.stream().filter(p -> "UPI".equals(p.getPaymentMethod())).map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);

        byte[] pdf = buildPdf("PAYMENT / REVENUE REPORT", from, to,
            new String[]{"Payment No","Customer","Invoice","Amount","Method","Status","Received By","Date"},
            new float[]{1.5f,1.8f,1.4f,1.2f,1.1f,1f,1.3f,1.2f},
            data.stream().map(p -> new String[]{
                safe(p.getPaymentNumber()), safe(p.getCustomerName()),
                safe(p.getInvoiceNumber()), "₹"+fmt(p.getAmount()),
                safe(p.getPaymentMethod()), safe(p.getPaymentStatus()),
                safe(p.getReceivedBy()), fmtDate(p.getCreatedAt())
            }).collect(Collectors.toList()),
            Map.of("Total Collected", "₹"+fmt(total), "Cash", "₹"+fmt(cash), "UPI / Online", "₹"+fmt(upi), "Transactions", String.valueOf(data.size()))
        );
        return pdfResponse(pdf, "Payment-Report-" + fileDateRange(from, to));
    }

    @GetMapping("/payments/excel")
    public ResponseEntity<byte[]> paymentsExcel(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        List<Payment> data = filterByDate(paymentRepo.findAllByOrderByCreatedAtDesc(), from, to);
        BigDecimal total = data.stream().map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
        byte[] excel = buildExcel("Payment Report", from, to,
            new String[]{"Payment No","Customer","Invoice No","Amount","Payment Method","Payment Status","Transaction ID","Received By","Remarks","Date"},
            data.stream().map(p -> new Object[]{
                safe(p.getPaymentNumber()), safe(p.getCustomerName()),
                safe(p.getInvoiceNumber()), numVal(p.getAmount()),
                safe(p.getPaymentMethod()), safe(p.getPaymentStatus()),
                safe(p.getTransactionId()), safe(p.getReceivedBy()),
                safe(p.getRemarks()), fmtDate(p.getCreatedAt())
            }).collect(Collectors.toList()),
            new int[]{3}, Map.of("Total Revenue", total)
        );
        return excelResponse(excel, "Payment-Report-" + fileDateRange(from, to));
    }

    // ── ENQUIRY REPORT ─────────────────────────────────────────────
    @GetMapping("/enquiries/pdf")
    public ResponseEntity<byte[]> enquiriesPdf(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        List<Enquiry> data = filterByDate(enquiryRepo.findAllByOrderByCreatedAtDesc(), from, to);
        long converted = data.stream().filter(e -> "CONVERTED".equals(e.getStatus())).count();
        byte[] pdf = buildPdf("ENQUIRY REPORT", from, to,
            new String[]{"Customer","Mobile","Service","Product","Source","Status","Date"},
            new float[]{1.8f,1.3f,1.8f,1.8f,1f,1f,1.2f},
            data.stream().map(e -> new String[]{
                safe(e.getCustomerName()), safe(e.getMobile()),
                safe(e.getServiceRequired()), safe(e.getProductName()),
                safe(e.getSource()), safe(e.getStatus()), fmtDate(e.getCreatedAt())
            }).collect(Collectors.toList()),
            Map.of("Total", String.valueOf(data.size()), "Converted", String.valueOf(converted))
        );
        return pdfResponse(pdf, "Enquiry-Report-" + fileDateRange(from, to));
    }

    @GetMapping("/enquiries/excel")
    public ResponseEntity<byte[]> enquiriesExcel(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        List<Enquiry> data = filterByDate(enquiryRepo.findAllByOrderByCreatedAtDesc(), from, to);
        byte[] excel = buildExcel("Enquiry Report", from, to,
            new String[]{"Customer Name","Mobile","Email","Address","Service Required","Product","Source","Status","Message","Date"},
            data.stream().map(e -> new Object[]{
                safe(e.getCustomerName()), safe(e.getMobile()), safe(e.getEmail()),
                safe(e.getAddress()), safe(e.getServiceRequired()),
                safe(e.getProductName()), safe(e.getSource()), safe(e.getStatus()),
                safe(e.getMessage()), fmtDate(e.getCreatedAt())
            }).collect(Collectors.toList()),
            new int[]{}, Map.of()
        );
        return excelResponse(excel, "Enquiry-Report-" + fileDateRange(from, to));
    }

    // ── QUOTATION REPORT ───────────────────────────────────────────
    @GetMapping("/quotations/pdf")
    public ResponseEntity<byte[]> quotationsPdf(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        List<Quotation> data = filterByDate(quotationRepo.findAllByOrderByCreatedAtDesc(), from, to);
        BigDecimal total = data.stream().map(q -> q.getTotalAmount() != null ? q.getTotalAmount() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
        byte[] pdf = buildPdf("QUOTATION REPORT", from, to,
            new String[]{"Quot. No","Customer","Mobile","Subtotal","GST","Total","Status","Date"},
            new float[]{1.4f,1.8f,1.3f,1.2f,1f,1.3f,1f,1.2f},
            data.stream().map(q -> new String[]{
                safe(q.getQuotationNumber()), safe(q.getCustomerName()), safe(q.getCustomerMobile()),
                "₹"+fmt(q.getSubtotal()), "₹"+fmt(q.getGstAmount()),
                "₹"+fmt(q.getTotalAmount()), safe(q.getStatus()), fmtDate(q.getCreatedAt())
            }).collect(Collectors.toList()),
            Map.of("Total Quotations", String.valueOf(data.size()), "Total Value", "₹"+fmt(total))
        );
        return pdfResponse(pdf, "Quotation-Report-" + fileDateRange(from, to));
    }

    @GetMapping("/quotations/excel")
    public ResponseEntity<byte[]> quotationsExcel(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        List<Quotation> data = filterByDate(quotationRepo.findAllByOrderByCreatedAtDesc(), from, to);
        byte[] excel = buildExcel("Quotation Report", from, to,
            new String[]{"Quotation No","Customer","Mobile","Customer Address","Subtotal","GST Amount","Total Amount","Status","Notes","Date"},
            data.stream().map(q -> new Object[]{
                safe(q.getQuotationNumber()), safe(q.getCustomerName()), safe(q.getCustomerMobile()),
                safe(q.getCustomerAddress()), numVal(q.getSubtotal()),
                numVal(q.getGstAmount()), numVal(q.getTotalAmount()),
                safe(q.getStatus()), safe(q.getNotes()), fmtDate(q.getCreatedAt())
            }).collect(Collectors.toList()),
            new int[]{4,5,6}, Map.of()
        );
        return excelResponse(excel, "Quotation-Report-" + fileDateRange(from, to));
    }

    // ════════════════════════════════════════════════════════════════
    // PDF BUILDER — Bank Statement Style
    // ════════════════════════════════════════════════════════════════
    private byte[] buildPdf(String title, LocalDateTime from, LocalDateTime to,
                             String[] headers, float[] colWidths,
                             List<String[]> rows, Map<String, String> summary) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            com.lowagie.text.Document doc = new com.lowagie.text.Document(PageSize.A4.rotate(), 36, 36, 54, 40);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            doc.open();

            // Colors
            Color brandGreen = new Color(10, 79, 60);
            Color lightGreen = new Color(224, 245, 236);
            Color darkGray   = new Color(40, 40, 40);
            Color midGray    = new Color(110, 110, 110);
            Color rowAlt     = new Color(248, 250, 252);
            Color lineGray   = new Color(218, 218, 218);

            // ── Company Header ──────────────────────────────────────
            PdfPTable header = new PdfPTable(2);
            header.setWidthPercentage(100);
            header.setSpacingAfter(10);
            header.setWidths(new float[]{2f, 1f});

            // Left: company info
            PdfPCell left = new PdfPCell();
            left.setBorder(Rectangle.NO_BORDER);
            left.setPadding(6);
            left.addElement(new Paragraph("AQUA GREEN AGENCIES",
                new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 18, com.lowagie.text.Font.BOLD, brandGreen)));
            left.addElement(new Paragraph("Near ESI Hospital, Neelikonampalayam, Coimbatore — 641033",
                new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 9, com.lowagie.text.Font.NORMAL, midGray)));
            left.addElement(new Paragraph("Ph: 09054617008  |  www.aquagreenagencies.com",
                new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 9, com.lowagie.text.Font.NORMAL, midGray)));
            header.addCell(left);

            // Right: report meta
            PdfPCell right = new PdfPCell();
            right.setBorder(Rectangle.NO_BORDER);
            right.setPaddingTop(6);
            right.setHorizontalAlignment(Element.ALIGN_RIGHT);
            com.lowagie.text.Font metaFont = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 9, com.lowagie.text.Font.NORMAL, midGray);
            com.lowagie.text.Font metaBold = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 9, com.lowagie.text.Font.BOLD, darkGray);
            right.addElement(new Paragraph("Period:  " + (from != null ? from.format(D) : "All time") + "  to  " + (to != null ? to.format(D) : "Present"), metaFont));
            right.addElement(new Paragraph("Generated:  " + LocalDateTime.now().format(DT), metaFont));
            right.addElement(new Paragraph("Total Records:  " + rows.size(), metaBold));
            header.addCell(right);
            doc.add(header);

            // ── Title bar ───────────────────────────────────────────
            PdfPTable titleBar = new PdfPTable(1);
            titleBar.setWidthPercentage(100);
            titleBar.setSpacingAfter(10);
            PdfPCell titleCell = new PdfPCell(new Phrase(title,
                new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 12, com.lowagie.text.Font.BOLD, Color.WHITE)));
            titleCell.setBackgroundColor(brandGreen);
            titleCell.setPadding(8);
            titleCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            titleCell.setBorder(Rectangle.NO_BORDER);
            titleBar.addCell(titleCell);
            doc.add(titleBar);

            // ── Summary cards ────────────────────────────────────────
            if (!summary.isEmpty()) {
                PdfPTable summaryTable = new PdfPTable(summary.size());
                summaryTable.setWidthPercentage(100);
                summaryTable.setSpacingAfter(12);
                for (Map.Entry<String, String> e : summary.entrySet()) {
                    PdfPCell sc = new PdfPCell();
                    sc.setBorder(Rectangle.BOX);
                    sc.setBorderColor(new Color(192, 220, 208));
                    sc.setPadding(8);
                    sc.setBackgroundColor(lightGreen);
                    sc.addElement(new Paragraph(e.getKey(),
                        new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 8, com.lowagie.text.Font.NORMAL, midGray)));
                    sc.addElement(new Paragraph(e.getValue(),
                        new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 13, com.lowagie.text.Font.BOLD, brandGreen)));
                    summaryTable.addCell(sc);
                }
                doc.add(summaryTable);
            }

            // ── Data table ───────────────────────────────────────────
            PdfPTable table = new PdfPTable(headers.length);
            table.setWidthPercentage(100);
            table.setWidths(colWidths);
            table.setSpacingAfter(10);

            // Header row
            com.lowagie.text.Font hf = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 8, com.lowagie.text.Font.BOLD, Color.WHITE);
            for (String h : headers) {
                PdfPCell hc = new PdfPCell(new Phrase(h, hf));
                hc.setBackgroundColor(brandGreen);
                hc.setPadding(6);
                hc.setBorder(Rectangle.NO_BORDER);
                table.addCell(hc);
            }

            // Data rows
            com.lowagie.text.Font bf = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 8, com.lowagie.text.Font.NORMAL, darkGray);
            boolean alt = false;
            int rowNum = 1;
            for (String[] row : rows) {
                Color rowBg = alt ? rowAlt : Color.WHITE;
                for (String cell : row) {
                    PdfPCell tc = new PdfPCell(new Phrase(cell != null ? cell : "—", bf));
                    tc.setPadding(5);
                    tc.setBorderColor(lineGray);
                    tc.setBorderWidth(0.4f);
                    tc.setBackgroundColor(rowBg);
                    table.addCell(tc);
                }
                alt = !alt;
                rowNum++;
            }
            doc.add(table);

            if (rows.isEmpty()) {
                Paragraph noData = new Paragraph("No records found for the selected period.",
                    new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 11, com.lowagie.text.Font.ITALIC, midGray));
                noData.setAlignment(Element.ALIGN_CENTER);
                doc.add(noData);
            }

            // ── Footer ──────────────────────────────────────────────
            PdfPTable footer = new PdfPTable(1);
            footer.setWidthPercentage(100);
            footer.setSpacingBefore(8);
            PdfPCell fc = new PdfPCell(new Phrase(
                "Report generated by Aqua Green Agencies CRM  |  " + LocalDateTime.now().format(DT) +
                "  |  This is a computer-generated report — no signature required.",
                new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 7, com.lowagie.text.Font.ITALIC, midGray)));
            fc.setBorderWidthTop(0.5f);
            fc.setBorderColorTop(lineGray);
            fc.setBorderWidthBottom(0); fc.setBorderWidthLeft(0); fc.setBorderWidthRight(0);
            fc.setPadding(6);
            fc.setHorizontalAlignment(Element.ALIGN_CENTER);
            footer.addCell(fc);
            doc.add(footer);

            // Page numbers
            PdfContentByte cb = writer.getDirectContent();
            int pages = writer.getPageNumber();
            for (int i = 1; i <= pages; i++) {
                cb.beginText();
                cb.setFontAndSize(BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, false), 8);
                cb.setColorFill(midGray);
                cb.showTextAligned(Element.ALIGN_RIGHT, "Page " + i + " of " + pages,
                    doc.getPageSize().getWidth() - 36, 20, 0);
                cb.endText();
            }

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("PDF build failed: {}", e.getMessage(), e);
            throw new RuntimeException("PDF build failed: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════
    // EXCEL BUILDER — Professional with formatting
    // ════════════════════════════════════════════════════════════════
    private byte[] buildExcel(String sheetTitle, LocalDateTime from, LocalDateTime to,
                               String[] headers, List<Object[]> rows,
                               int[] currencyCols, Map<String, ? extends Number> totals) {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            XSSFSheet sheet = wb.createSheet(sheetTitle);

            // ── Styles ───────────────────────────────────────────────
            // Brand green fill
            XSSFCellStyle brandStyle = wb.createCellStyle();
            XSSFColor green = new XSSFColor(new byte[]{10, 79, 60}, null);
            brandStyle.setFillForegroundColor(green);
            brandStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font brandFont = wb.createFont();
            brandFont.setColor(IndexedColors.WHITE.getIndex());
            brandFont.setBold(true); brandFont.setFontHeightInPoints((short)18);
            brandStyle.setFont(brandFont);

            // Meta style
            XSSFCellStyle metaStyle = wb.createCellStyle();
            Font metaFont = wb.createFont();
            metaFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            metaFont.setFontHeightInPoints((short)9);
            metaStyle.setFont(metaFont);

            // Header style
            XSSFCellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFillForegroundColor(green);
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBottomBorderColor(IndexedColors.WHITE.getIndex());
            Font headerFont = wb.createFont();
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setBold(true); headerFont.setFontHeightInPoints((short)10);
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setWrapText(true);

            // Data style (odd)
            XSSFCellStyle dataStyle = wb.createCellStyle();
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            dataStyle.setFont(wb.createFont());

            // Data style (even - alt row)
            XSSFCellStyle altStyle = wb.createCellStyle();
            XSSFColor altColor = new XSSFColor(new byte[]{(byte)240,(byte)249,(byte)244}, null);
            altStyle.setFillForegroundColor(altColor);
            altStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            altStyle.setBorderBottom(BorderStyle.THIN);
            altStyle.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());

            // Currency style
            XSSFCellStyle currStyle = wb.createCellStyle();
            DataFormat df = wb.createDataFormat();
            currStyle.setDataFormat(df.getFormat("₹#,##0.00"));
            currStyle.setBorderBottom(BorderStyle.THIN);
            currStyle.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());

            XSSFCellStyle currAltStyle = wb.createCellStyle();
            currAltStyle.cloneStyleFrom(currStyle);
            currAltStyle.setFillForegroundColor(altColor);
            currAltStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Total style
            XSSFCellStyle totalStyle = wb.createCellStyle();
            XSSFColor totalBg = new XSSFColor(new byte[]{(byte)224,(byte)245,(byte)236}, null);
            totalStyle.setFillForegroundColor(totalBg);
            totalStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            totalStyle.setBorderTop(BorderStyle.MEDIUM);
            totalStyle.setTopBorderColor(new XSSFColor(new byte[]{10, 79, 60}, null));
            Font totalFont = wb.createFont();
            totalFont.setBold(true); totalFont.setFontHeightInPoints((short)10);
            totalStyle.setFont(totalFont);
            totalStyle.setDataFormat(df.getFormat("₹#,##0.00"));

            XSSFCellStyle totalLabelStyle = wb.createCellStyle();
            totalLabelStyle.cloneStyleFrom(totalStyle);
            totalLabelStyle.setDataFormat((short)0);

            int rowIdx = 0;

            // ── Row 1: Company name ──────────────────────────────────
            Row r0 = sheet.createRow(rowIdx++);
            r0.setHeightInPoints(28);
            Cell c0 = r0.createCell(0);
            c0.setCellValue("AQUA GREEN AGENCIES");
            c0.setCellStyle(brandStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowIdx-1, rowIdx-1, 0, headers.length-1));

            // ── Row 2: Report title ──────────────────────────────────
            Row r1 = sheet.createRow(rowIdx++);
            r1.setHeightInPoints(20);
            XSSFCellStyle titleStyle = wb.createCellStyle();
            titleStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte)236,(byte)247,(byte)242}, null));
            titleStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            org.apache.poi.xssf.usermodel.XSSFFont titleFont = (org.apache.poi.xssf.usermodel.XSSFFont) wb.createFont();
            titleFont.setBold(true); titleFont.setFontHeightInPoints((short)12);
            titleFont.setColor(new XSSFColor(new byte[]{10, 79, 60}, null));
            titleStyle.setFont(titleFont); titleStyle.setAlignment(HorizontalAlignment.CENTER);
            Cell tc = r1.createCell(0);
            tc.setCellValue(sheetTitle.toUpperCase());
            tc.setCellStyle(titleStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowIdx-1, rowIdx-1, 0, headers.length-1));

            // ── Row 3: Period & generated ─────────────────────────────
            Row r2 = sheet.createRow(rowIdx++);
            Cell mc1 = r2.createCell(0);
            mc1.setCellValue("Period: " + (from != null ? from.format(D) : "All time") + " to " + (to != null ? to.format(D) : "Present"));
            mc1.setCellStyle(metaStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowIdx-1, rowIdx-1, 0, headers.length/2-1));
            Cell mc2 = r2.createCell(headers.length/2);
            mc2.setCellValue("Generated: " + LocalDateTime.now().format(DT) + "   Records: " + rows.size());
            mc2.setCellStyle(metaStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowIdx-1, rowIdx-1, headers.length/2, headers.length-1));

            // Blank row
            rowIdx++;

            // ── Header row ────────────────────────────────────────────
            Row headerRow = sheet.createRow(rowIdx++);
            headerRow.setHeightInPoints(22);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // ── Data rows ─────────────────────────────────────────────
            Set<Integer> currSet = new HashSet<>();
            for (int ci : currencyCols) currSet.add(ci);

            boolean altRow = false;
            for (Object[] row : rows) {
                Row dataRow = sheet.createRow(rowIdx++);
                dataRow.setHeightInPoints(16);
                for (int i = 0; i < row.length; i++) {
                    Cell cell = dataRow.createCell(i);
                    if (currSet.contains(i) && row[i] instanceof Number) {
                        cell.setCellValue(((Number)row[i]).doubleValue());
                        cell.setCellStyle(altRow ? currAltStyle : currStyle);
                    } else if (row[i] instanceof Number) {
                        cell.setCellValue(((Number)row[i]).doubleValue());
                        cell.setCellStyle(altRow ? altStyle : dataStyle);
                    } else {
                        cell.setCellValue(row[i] != null ? row[i].toString() : "");
                        cell.setCellStyle(altRow ? altStyle : dataStyle);
                    }
                }
                altRow = !altRow;
            }

            // Blank row before totals
            rowIdx++;

            // ── Totals row ────────────────────────────────────────────
            if (!totals.isEmpty()) {
                Row totalRow = sheet.createRow(rowIdx++);
                totalRow.setHeightInPoints(20);
                int col = 0;
                for (Map.Entry<String, ? extends Number> e : totals.entrySet()) {
                    Cell lc = totalRow.createCell(col);
                    lc.setCellValue(e.getKey());
                    lc.setCellStyle(totalLabelStyle);
                    Cell vc = totalRow.createCell(col+1);
                    vc.setCellValue(e.getValue().doubleValue());
                    vc.setCellStyle(totalStyle);
                    col += 2;
                }
            }

            // Blank + footer
            rowIdx++;
            Row footerRow = sheet.createRow(rowIdx);
            Cell footerCell = footerRow.createCell(0);
            footerCell.setCellValue("Report generated by Aqua Green Agencies CRM | " + LocalDateTime.now().format(DT));
            XSSFCellStyle footerStyle = wb.createCellStyle();
            Font footerFont = wb.createFont();
            footerFont.setItalic(true); footerFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            footerFont.setFontHeightInPoints((short)8);
            footerStyle.setFont(footerFont);
            footerCell.setCellStyle(footerStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowIdx, rowIdx, 0, headers.length-1));

            // ── Column widths ─────────────────────────────────────────
            for (int i = 0; i < headers.length; i++) {
                sheet.setColumnWidth(i, 4800);
            }
            sheet.setColumnWidth(0, 4200);

            // Freeze header row
            sheet.createFreezePane(0, 5);

            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Excel build failed: {}", e.getMessage(), e);
            throw new RuntimeException("Excel build failed: " + e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────
    private <T> List<T> filterByDate(List<T> list, LocalDateTime from, LocalDateTime to) {
        return list.stream().filter(item -> {
            try {
                LocalDateTime dt = (LocalDateTime) item.getClass().getMethod("getCreatedAt").invoke(item);
                if (dt == null) return from == null && to == null;
                if (from != null && dt.isBefore(from)) return false;
                if (to != null && dt.isAfter(to)) return false;
                return true;
            } catch (Exception e) { return true; }
        }).collect(Collectors.toList());
    }

    private ResponseEntity<byte[]> pdfResponse(byte[] data, String filename) {
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + ".pdf\"")
            .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
            .body(data);
    }

    private ResponseEntity<byte[]> excelResponse(byte[] data, String filename) {
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + ".xlsx\"")
            .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
            .body(data);
    }

    private String safe(String s) { return s != null ? s : ""; }
    private String fmtDate(LocalDateTime dt) { return dt != null ? dt.format(D) : ""; }
    private String fmt(BigDecimal v) { return v != null ? String.format("%,.2f", v) : "0.00"; }
    private double numVal(BigDecimal v) { return v != null ? v.doubleValue() : 0.0; }
    private String truncate(String s, int max) { return s != null ? (s.length() > max ? s.substring(0, max) + "…" : s) : ""; }
    private String fileDateRange(LocalDateTime from, LocalDateTime to) {
        String f = from != null ? from.format(FILE) : "all";
        String t = to != null ? to.format(FILE) : LocalDateTime.now().format(FILE);
        return f + "_to_" + t;
    }
}
