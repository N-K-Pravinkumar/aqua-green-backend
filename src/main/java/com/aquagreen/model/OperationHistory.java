package com.aquagreen.model;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity @Table(name="operation_history") @Data @NoArgsConstructor @AllArgsConstructor @Builder
public class OperationHistory {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    // CUSTOMER_CREATED, LEAD_CREATED, PRODUCT_PURCHASED, QUOTATION_GENERATED,
    // INSTALLATION_COMPLETED, SERVICE_COMPLETED, FILTER_REPLACED, PAYMENT_RECEIVED,
    // EMPLOYEE_ASSIGNED, COMMUNICATION_SENT, etc.
    @Column(nullable=false) private String action;
    private String entityType; // Customer, Lead, Sale, Service, etc.
    private Long entityId;
    private String entityName;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="customer_id") private Customer customer;
    private String performedBy;
    @Column(length=2000) private String remarks;
    @Column(columnDefinition="TEXT") private String metaJson; // extra data
    @Column(updatable=false) private LocalDateTime createdAt;
    @PrePersist protected void onCreate(){ createdAt=LocalDateTime.now(); }
}
