package com.aquagreen.model;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity @Table(name="stock_items") @Data @NoArgsConstructor @AllArgsConstructor @Builder
public class StockItem {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private String name;
    private String category;
    @Column(length=1000) private String description;
    private String brand;
    private String imageUrl;
    private java.math.BigDecimal price;
    @Builder.Default private Integer openingStock = 0;
    @Builder.Default private Integer currentStock = 0;
    @Builder.Default private Integer minStock = 5;
    private String unit;
    @Builder.Default private Boolean active = true;
    @Column(updatable=false) private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist protected void onCreate(){ createdAt=LocalDateTime.now(); updatedAt=LocalDateTime.now(); }
    @PreUpdate protected void onUpdate(){ updatedAt=LocalDateTime.now(); }
}
