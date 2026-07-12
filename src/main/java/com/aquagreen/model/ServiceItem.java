package com.aquagreen.model;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity @Table(name="service_items") @Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ServiceItem {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private String name;
    @Column(length=1000) private String description;
    private BigDecimal price;
    // pricingMode: SHOW_PRICE | HIDE_PRICE | CONTACT_FOR_PRICE | FREE
    @Builder.Default private String pricingMode = "SHOW_PRICE";
    private String imageUrl;
    private String seoTitle;
    private String seoDescription;
    @Builder.Default private Boolean active = true;
    @Builder.Default private Integer displayOrder = 0;
    @Column(updatable=false) private LocalDateTime createdAt;
    @PrePersist protected void onCreate(){ createdAt=LocalDateTime.now(); }
}
