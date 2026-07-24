package com.aquagreen.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.time.LocalDateTime;

/**
 * A customer can legitimately have more than one phone number on record
 * (e.g. a landline and a mobile, or an old number kept for reference).
 * The Customer entity's own `mobile` field stays the single *primary*
 * number used for matching History/Maintenance lookups everywhere else in
 * the app; this table holds any additional numbers, each of which is
 * globally unique so the same number can never get attached to two
 * different customers by mistake.
 */
@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity @Table(name="customer_phones", uniqueConstraints = @UniqueConstraint(columnNames = "phone"))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class CustomerPhone {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name="customer_id", nullable=false)
    private Customer customer;

    @Column(nullable=false, unique=true, length=15) private String phone;
    private String label; // optional — e.g. "Alternate", "Old number", "Home"

    @Column(updatable=false) private LocalDateTime createdAt;

    @PrePersist protected void onCreate() {
        createdAt = LocalDateTime.now();
        phone = com.aquagreen.util.MobileUtil.normalize(phone);
    }
}
