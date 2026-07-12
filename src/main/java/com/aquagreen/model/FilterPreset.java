package com.aquagreen.model;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity @Table(name="filter_presets") @Data @NoArgsConstructor @AllArgsConstructor @Builder
public class FilterPreset {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private String name;
    private String description;
    @Column(columnDefinition="TEXT",nullable=false) private String filterConfig;
    private String icon;
    private String color;
    @Builder.Default private Boolean active = true;
    private String createdBy;
    @Builder.Default private Integer usageCount = 0;
    @Column(updatable=false) private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist protected void onCreate(){ createdAt=LocalDateTime.now(); updatedAt=LocalDateTime.now(); }
    @PreUpdate protected void onUpdate(){ updatedAt=LocalDateTime.now(); }
}
