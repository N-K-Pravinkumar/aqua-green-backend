package com.aquagreen.model;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity @Table(name="payments") @Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Payment {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private String paymentNumber;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="customer_id") private Customer customer;
    private String customerName;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="sale_id") private Sale sale;
    private String invoiceNumber;
    @Column(nullable=false) private BigDecimal amount;
    private String paymentMethod; // CASH, UPI, CARD, BANK_TRANSFER, CHEQUE
    private String paymentStatus; // PAID, PENDING, FAILED, REFUNDED
    private String transactionId;
    private String remarks;
    private String receivedBy;
    @Column(updatable=false) private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist protected void onCreate(){ createdAt=LocalDateTime.now(); updatedAt=LocalDateTime.now(); if(paymentStatus==null)paymentStatus="PAID"; }
    @PreUpdate protected void onUpdate(){ updatedAt=LocalDateTime.now(); }
}
