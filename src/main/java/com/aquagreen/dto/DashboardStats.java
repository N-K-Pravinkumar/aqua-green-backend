package com.aquagreen.dto;
import lombok.*;
import java.math.BigDecimal;
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class DashboardStats {
    private long totalLeads;
    private long newLeads;
    private long totalCustomers;
    private long totalSales;
    private long pendingServices;
    private long completedServices;
    private long pendingQuotations;
    private long lowStockItems;
    private BigDecimal revenueToday;
    private BigDecimal revenueThisMonth;
    private BigDecimal totalRevenue;
}
