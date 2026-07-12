package com.aquagreen.model;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity @Table(name="awards") @Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Award {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private String title;
    private String organization;
    @Column(name="award_year") private String awardYear;
    @Column(length=500) private String description;
    private String imageUrl;
    @Builder.Default private Boolean active = true;
    @Builder.Default private Integer displayOrder = 0;
    @Column(updatable=false) private LocalDateTime createdAt;
    @PrePersist protected void onCreate(){ createdAt=LocalDateTime.now(); }
}
