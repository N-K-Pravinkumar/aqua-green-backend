package com.aquagreen.model;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity @Table(name="app_users") @Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AppUser {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(unique=true,nullable=false) private String username;
    @Column(unique=true,nullable=false) private String email;
    @JsonIgnore
    @Column(nullable=false) private String password;
    private String fullName;
    private String mobile;
    // Roles: SUPER_ADMIN | ADMIN | MANAGER | EMPLOYEE | CUSTOMER
    @Column(nullable=false) private String role;

    // Granular permissions (JSON array of permission strings)
    // e.g. ["VIEW_LEADS","EDIT_LEADS","VIEW_CUSTOMERS","EDIT_CUSTOMERS"]
    // SUPER_ADMIN has all permissions, ADMIN has most, EMPLOYEE is restricted
    @Column(columnDefinition="TEXT") private String permissions;

    @Builder.Default private Boolean active = true;
    @JsonIgnore
    private String passwordResetToken;
    @JsonIgnore
    private LocalDateTime passwordResetExpiry;
    private String refreshToken;

    // Link to Customer (for CUSTOMER role)
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="customer_id")
    private Customer customer;

    // Link to Employee (for EMPLOYEE role)
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="employee_id")
    private Employee employee;

    private String avatarUrl;
    private String department;
    private LocalDateTime lastLoginAt;

    @Column(updatable=false) private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist protected void onCreate(){ createdAt=LocalDateTime.now(); updatedAt=LocalDateTime.now(); }
    @PreUpdate protected void onUpdate(){ updatedAt=LocalDateTime.now(); }
}
