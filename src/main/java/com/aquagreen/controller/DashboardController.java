package com.aquagreen.controller;
import com.aquagreen.dto.ApiResponse;
import com.aquagreen.dto.DashboardStats;
import com.aquagreen.model.Lead;
import com.aquagreen.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.*;

@RestController @RequestMapping("/api/dashboard") @RequiredArgsConstructor
public class DashboardController {
    private final LeadRepository leadRepo;
    private final CustomerRepository customerRepo;
    private final SaleRepository saleRepo;
    private final ServiceRequestRepository serviceRequestRepo;
    private final QuotationRepository quotationRepo;
    private final StockItemRepository stockRepo;
    private final EnquiryRepository enquiryRepo;

    private static final String[] MONTHS = {"Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"};

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<DashboardStats>> getStats() {
        DashboardStats stats = DashboardStats.builder()
            .totalLeads(leadRepo.count())
            .newLeads(leadRepo.countByStatus("NEW"))
            .totalCustomers(customerRepo.countByActiveTrue())
            .totalSales(saleRepo.count())
            .pendingServices(serviceRequestRepo.countByStatus("PENDING") + serviceRequestRepo.countByStatus("ASSIGNED"))
            .completedServices(serviceRequestRepo.countByStatus("COMPLETED"))
            .pendingQuotations(quotationRepo.countByStatus("SENT") + quotationRepo.countByStatus("DRAFT"))
            .lowStockItems(stockRepo.findLowStockItems().size())
            .revenueToday(BigDecimal.ZERO)
            .revenueThisMonth(nvl(saleRepo.sumMonthlyRevenue()))
            .totalRevenue(nvl(saleRepo.sumTotalRevenue()))
            .build();
        return ResponseEntity.ok(ApiResponse.success("OK", stats));
    }

    /**
     * Real chart data derived from the database.
     * Returns:
     *   monthlySales  — [{month, sales}] for current year
     *   leadSources   — [{name, value}] percentage breakdown
     *   serviceStatus — [{name, value}] percentage breakdown
     */
    @GetMapping("/charts")
    public ResponseEntity<ApiResponse<Map<String,Object>>> getCharts() {

        // ── Monthly sales from real Sale records ─────────────────
        List<Object[]> rawSales = saleRepo.monthlySaleTotals();
        // Build a full 12-month array filled with 0 by default
        long[] salesByMonth = new long[12];
        for (Object[] row : rawSales) {
            int month = ((Number) row[0]).intValue();   // 1-based
            long total = ((Number) row[1]).longValue();
            if (month >= 1 && month <= 12) salesByMonth[month - 1] = total;
        }
        List<Map<String,Object>> monthlySales = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            monthlySales.add(Map.of("month", MONTHS[i], "sales", salesByMonth[i]));
        }

        // ── Lead source breakdown ────────────────────────────────
        List<Lead> allLeads = leadRepo.findAllByOrderByCreatedAtDesc();
        Map<String,Long> sourceCounts = new LinkedHashMap<>();
        for (Lead l : allLeads) {
            String src = l.getSource() != null ? l.getSource() : "OTHER";
            sourceCounts.merge(src, 1L, Long::sum);
        }
        long totalLeads = allLeads.size();
        List<Map<String,Object>> leadSources = new ArrayList<>();
        sourceCounts.forEach((src, cnt) ->
            leadSources.add(Map.of("name", src.replace("_"," "), "value",
                totalLeads > 0 ? Math.round(cnt * 100.0 / totalLeads) : 0)));

        // ── Service request status breakdown ─────────────────────
        long completed  = serviceRequestRepo.countByStatus("COMPLETED");
        long pending    = serviceRequestRepo.countByStatus("PENDING") + serviceRequestRepo.countByStatus("ASSIGNED") + serviceRequestRepo.countByStatus("IN_PROGRESS");
        long cancelled  = serviceRequestRepo.countByStatus("CANCELLED");
        long totalSvc   = completed + pending + cancelled;
        List<Map<String,Object>> serviceStatus = List.of(
            Map.of("name","Completed", "value", totalSvc>0 ? Math.round(completed*100.0/totalSvc)  : 0),
            Map.of("name","Pending",   "value", totalSvc>0 ? Math.round(pending*100.0/totalSvc)    : 0),
            Map.of("name","Cancelled", "value", totalSvc>0 ? Math.round(cancelled*100.0/totalSvc)  : 0)
        );

        Map<String,Object> data = Map.of(
            "monthlySales",  monthlySales,
            "leadSources",   leadSources,
            "serviceStatus", serviceStatus
        );
        return ResponseEntity.ok(ApiResponse.success("OK", data));
    }

    private BigDecimal nvl(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }
}
