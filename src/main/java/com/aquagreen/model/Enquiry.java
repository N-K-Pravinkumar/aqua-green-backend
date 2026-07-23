package com.aquagreen.model;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity @Table(name="enquiries") @Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Enquiry {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private String customerName;
    @Column(nullable=false,length=15) private String mobile;
    private String email;
    private String address;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="product_id") private Product product;
    private String productName;
    private String serviceRequired;
    @Column(length=1000) private String message;
    private String source;
    private String status;
    @Column(updatable=false) private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist protected void onCreate(){ createdAt=LocalDateTime.now(); updatedAt=LocalDateTime.now(); if(status==null)status="NEW"; if(source==null)source="WEBSITE"; mobile=com.aquagreen.util.MobileUtil.normalize(mobile); }
    @PreUpdate protected void onUpdate(){ updatedAt=LocalDateTime.now(); mobile=com.aquagreen.util.MobileUtil.normalize(mobile); }
}
