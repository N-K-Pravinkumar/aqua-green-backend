package com.aquagreen.model;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity @Table(name="products") @Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Product {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private String name;
    @Column(length=2000) private String description;
    private BigDecimal price;
    private BigDecimal originalPrice;
    // pricingMode: SHOW_PRICE | HIDE_PRICE | CONTACT_FOR_PRICE | FREE
    @Builder.Default private String pricingMode = "SHOW_PRICE";
    @Builder.Default private Boolean showPrice = true;  // legacy - derived from pricingMode
    @Builder.Default private Boolean isFree = false;    // legacy - derived from pricingMode
    private String category;
    private String imageUrl;
    private String imageAlt;
    private Integer capacityLitres;
    @Column(length=1000) private String features;
    @Column(length=1000) private String specifications;
    private String seoTitle;
    private String seoDescription;
    @Builder.Default private Boolean active = true;
    @Builder.Default private Integer displayOrder = 0;
    @Builder.Default private Integer stock = 0;
    @Builder.Default private Integer minStock = 5;
    @Column(updatable=false) private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist protected void onCreate(){ createdAt=LocalDateTime.now(); updatedAt=LocalDateTime.now(); }
    @PreUpdate protected void onUpdate(){ updatedAt=LocalDateTime.now(); }
}
