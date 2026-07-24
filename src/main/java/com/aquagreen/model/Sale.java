package com.aquagreen.model;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity @Table(name="sales") @Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Sale {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(unique=true, length=20) private String saleCode; // e.g. SALE001
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="customer_id") private Customer customer;
    private String customerName;
    private String customerMobile;
    private String customerAddress;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="product_id") private Product product;
    private String productName;
    private String productModel;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal discountAmount;
    private BigDecimal gstAmount;
    private BigDecimal totalAmount;
    private String salesPerson;
    private String invoiceNumber;
    private String paymentStatus; // PAID,PENDING,OVERDUE
    private String paymentMethod; // CASH,UPI,CARD,BANK_TRANSFER
    private String notes;
    @Builder.Default private Boolean stockDeducted = false;
    // Linked to service request if product was sold during a service visit
    private Long serviceRequestId;
    @Column(updatable=false) private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist protected void onCreate() {
        if(createdAt==null) createdAt=LocalDateTime.now(); updatedAt=LocalDateTime.now();
        if (paymentStatus==null) paymentStatus="PAID";
        if (stockDeducted==null) stockDeducted=false;
        customerMobile=com.aquagreen.util.MobileUtil.normalize(customerMobile);
    }
    @PreUpdate protected void onUpdate() { updatedAt=LocalDateTime.now(); customerMobile=com.aquagreen.util.MobileUtil.normalize(customerMobile); }
}
