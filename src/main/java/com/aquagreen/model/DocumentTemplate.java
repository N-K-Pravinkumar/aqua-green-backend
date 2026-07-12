package com.aquagreen.model;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity @Table(name="document_templates") @Data @NoArgsConstructor @AllArgsConstructor @Builder
public class DocumentTemplate {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private String name;
    @Column(nullable=false) private String templateType;
    @Column(columnDefinition="TEXT") private String messageContent;
    @Column(columnDefinition="TEXT") private String htmlContent;
    @Column(columnDefinition="TEXT") private String documentJson;
    @Column(columnDefinition="TEXT") private String headerConfig;
    @Column(columnDefinition="TEXT") private String footerConfig;
    @Column(columnDefinition="TEXT") private String watermarkConfig;
    @Column(columnDefinition="TEXT") private String pageConfig;
    @Column(columnDefinition="TEXT") private String brandingConfig;
    @Column(columnDefinition="TEXT") private String placeholders;
    private String category;
    private String description;
    private String subject;
    @Builder.Default private Boolean isDefault = false;
    @Builder.Default private Boolean active = true;
    private String createdBy;
    @Builder.Default private Integer version = 1;
    @Column(columnDefinition="TEXT") private String versionHistory;

    // ── SMS-specific fields ──────────────────────────────────────
    /** MSG91 Template/Flow ID — paste from MSG91 panel after DLT approval */
    private String msg91TemplateId;

    /**
     * Business event this template handles.
     * One of: ENQUIRY_RECEIVED, SERVICE_BOOKED, SERVICE_COMPLETED,
     *         QUOTATION_SENT, LEAD_FOLLOWUP, PAYMENT_RECEIVED
     * The SmsService looks up the active default template for each event.
     */
    private String smsEvent;

    @Column(updatable=false) private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist protected void onCreate(){ createdAt=LocalDateTime.now(); updatedAt=LocalDateTime.now(); }
    @PreUpdate protected void onUpdate(){ updatedAt=LocalDateTime.now(); if(version==null)version=1; version++; }
}
