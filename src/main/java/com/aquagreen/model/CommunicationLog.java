package com.aquagreen.model;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity @Table(name="communication_logs") @Data @NoArgsConstructor @AllArgsConstructor @Builder
public class CommunicationLog {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private String channel;
    private String status;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="customer_id") private Customer customer;
    private String customerName;
    private String customerMobile;
    private String customerEmail;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="template_id") private DocumentTemplate template;
    private String templateName;
    @Column(columnDefinition="TEXT") private String messageContent;
    @Column(columnDefinition="TEXT") private String subject;
    private String attachmentUrl;
    private String attachmentType;
    private String sentBy;
    private LocalDateTime scheduledAt;
    private LocalDateTime sentAt;
    private LocalDateTime deliveredAt;
    private String batchId;
    private String errorMessage;
    @Builder.Default private Integer retryCount = 0;
    @Column(updatable=false) private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist protected void onCreate(){ createdAt=LocalDateTime.now(); updatedAt=LocalDateTime.now(); if(status==null)status="QUEUED"; }
    @PreUpdate protected void onUpdate(){ updatedAt=LocalDateTime.now(); }
}
