package com.aquagreen.model;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity @Table(name="leads") @Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Lead {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private String name;
    @Column(nullable=false,length=15) private String mobile;
    private String email;
    private String city;
    private String requirement;
    private String source; // WEBSITE,GOOGLE_ADS,FACEBOOK,INSTAGRAM,WHATSAPP,REFERRAL
    private String assignedEmployee;
    private String status; // NEW,CONTACTED,FOLLOW_UP,QUOTATION_SENT,CONVERTED,LOST
    @Column(length=2000) private String notes;
    @Column(updatable=false) private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist protected void onCreate(){ createdAt=LocalDateTime.now(); updatedAt=LocalDateTime.now(); if(status==null)status="NEW"; if(source==null)source="WEBSITE"; }
    @PreUpdate protected void onUpdate(){ updatedAt=LocalDateTime.now(); }
}
