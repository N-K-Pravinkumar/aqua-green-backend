package com.aquagreen.model;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity @Table(name="blogs") @Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Blog {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private String title;
    @Column(unique=true) private String slug;
    @Column(columnDefinition="TEXT") private String content;
    private String excerpt;
    private String featuredImageUrl;
    private String seoTitle;
    private String seoDescription;
    private String status; // DRAFT,PUBLISHED
    private String author;
    @Column(updatable=false) private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime publishedAt;
    @PrePersist protected void onCreate(){ createdAt=LocalDateTime.now(); updatedAt=LocalDateTime.now(); if(status==null)status="DRAFT"; }
    @PreUpdate protected void onUpdate(){ updatedAt=LocalDateTime.now(); }
}
