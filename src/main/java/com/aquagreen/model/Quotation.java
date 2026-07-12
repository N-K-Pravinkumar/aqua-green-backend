package com.aquagreen.model;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity @Table(name="quotations") @Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Quotation {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private String quotationNumber;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="customer_id") private Customer customer;
    private String customerName;
    private String customerMobile;
    private String customerAddress;
    private String itemsJson;
    private BigDecimal subtotal;
    private BigDecimal gstAmount;
    private BigDecimal totalAmount;
    private String notes;
    private String status;
    @Builder.Default private Integer validityDays = 30;
    @Column(updatable=false) private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist protected void onCreate(){ createdAt=LocalDateTime.now(); updatedAt=LocalDateTime.now(); if(status==null)status="DRAFT"; }
    @PreUpdate protected void onUpdate(){ updatedAt=LocalDateTime.now(); }
}
