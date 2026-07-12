package com.aquagreen.model;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity @Table(name="brand_partners") @Data @NoArgsConstructor @AllArgsConstructor @Builder
public class BrandPartner {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private String name;
    private String logoUrl;
    private String websiteUrl;
    @Builder.Default private Boolean active = true;
    @Builder.Default private Integer displayOrder = 0;
    @Column(updatable=false) private LocalDateTime createdAt;
    @PrePersist protected void onCreate(){ createdAt=LocalDateTime.now(); }
}
