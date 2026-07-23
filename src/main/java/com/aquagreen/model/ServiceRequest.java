package com.aquagreen.model;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity @Table(name="service_requests") @Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ServiceRequest {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private String ticketNumber;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="customer_id")
    private Customer customer;
    private String customerName;
    private String customerMobile;
    private String customerAddress;
    private String productName;
    private String productModel;
    private String issueDescription;
    private String assignedTechnician;
    private BigDecimal serviceCharge;
    private String status;   // PENDING,ASSIGNED,IN_PROGRESS,COMPLETED,CANCELLED
    private String priority; // LOW,MEDIUM,HIGH,URGENT
    @Column(length=2000) private String technicianNotes;

    // ── Spare parts used by technician ─────────────────────────
    // JSON array: [{"name":"Filter","qty":2,"unitPrice":150,"stockItemId":5}]
    @Column(columnDefinition="TEXT") private String sparePartsJson;
    private BigDecimal sparePartsTotal;

    // ── Billing ─────────────────────────────────────────────────
    private BigDecimal totalBillAmount;  // serviceCharge + sparePartsTotal
    private String paymentStatus;  // PENDING,PAID,WAIVED
    private String paymentMethod;  // CASH,UPI,CARD,BANK_TRANSFER
    private String invoiceNumber;
    private Boolean stockDeducted;

    // ── Products sold to customer during visit ──────────────────
    // JSON array: [{"productId":1,"name":"Kent Ace","qty":1,"price":7499}]
    @Column(columnDefinition="TEXT") private String productsSoldJson;
    private BigDecimal productsSoldTotal;

    @Column(updatable=false) private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
    /** When filter should next be replaced — set by technician on completion (default: completedAt + 1 year) */
    private LocalDateTime nextFilterDueDate;
    /** When next service visit is due (default: completedAt + 6 months) */
    private LocalDateTime nextServiceDueDate;

    @PrePersist protected void onCreate() {
        if(createdAt==null) createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now();
        if (status == null) status = "PENDING";
        if (priority == null) priority = "MEDIUM";
        if (paymentStatus == null) paymentStatus = "PENDING";
        if (stockDeducted == null) stockDeducted = false;
        customerMobile = com.aquagreen.util.MobileUtil.normalize(customerMobile);
    }
    @PreUpdate protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        customerMobile = com.aquagreen.util.MobileUtil.normalize(customerMobile);
        // Auto-set due dates when completing a service
        if ("COMPLETED".equals(status) && completedAt != null) {
            if (nextFilterDueDate == null)  nextFilterDueDate  = completedAt.plusYears(1);
            if (nextServiceDueDate == null) nextServiceDueDate = completedAt.plusMonths(6);
        }
    }
}
