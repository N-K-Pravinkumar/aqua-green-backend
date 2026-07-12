package com.aquagreen.controller;

import com.aquagreen.dto.ApiResponse;
import com.aquagreen.model.AppUser;
import com.aquagreen.model.Employee;
import com.aquagreen.repository.AppUserRepository;
import com.aquagreen.repository.EmployeeRepository;
import com.aquagreen.service.JwtUtil;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Slf4j
public class UserManagementController {

    private final AppUserRepository userRepo;
    private final EmployeeRepository employeeRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    // Default permissions per role
    private static final Map<String, List<String>> ROLE_PERMISSIONS = Map.of(
        "SUPER_ADMIN", List.of("ALL"),
        "ADMIN", List.of(
            "VIEW_LEADS","EDIT_LEADS","DELETE_LEADS",
            "VIEW_CUSTOMERS","EDIT_CUSTOMERS","DELETE_CUSTOMERS",
            "VIEW_PRODUCTS","EDIT_PRODUCTS","DELETE_PRODUCTS",
            "VIEW_SERVICES","EDIT_SERVICES",
            "VIEW_SALES","EDIT_SALES","DELETE_SALES",
            "VIEW_SERVICE_REQUESTS","EDIT_SERVICE_REQUESTS",
            "VIEW_QUOTATIONS","EDIT_QUOTATIONS",
            "VIEW_STOCK","EDIT_STOCK",
            "VIEW_EMPLOYEES","EDIT_EMPLOYEES",
            "VIEW_REPORTS","EXPORT_REPORTS",
            "VIEW_COMMUNICATION","SEND_COMMUNICATION",
            "VIEW_GALLERY","EDIT_GALLERY",
            "VIEW_BLOGS","EDIT_BLOGS",
            "VIEW_TEMPLATES","EDIT_TEMPLATES",
            "VIEW_ENQUIRIES","EDIT_ENQUIRIES",
            "MANAGE_USERS"
        ),
        "MANAGER", List.of(
            "VIEW_LEADS","EDIT_LEADS",
            "VIEW_CUSTOMERS","EDIT_CUSTOMERS",
            "VIEW_PRODUCTS",
            "VIEW_SERVICES","EDIT_SERVICES",
            "VIEW_SALES","EDIT_SALES",
            "VIEW_SERVICE_REQUESTS","EDIT_SERVICE_REQUESTS",
            "VIEW_QUOTATIONS","EDIT_QUOTATIONS",
            "VIEW_STOCK","EDIT_STOCK",
            "VIEW_EMPLOYEES",
            "VIEW_REPORTS","EXPORT_REPORTS",
            "VIEW_COMMUNICATION","SEND_COMMUNICATION",
            "VIEW_ENQUIRIES","EDIT_ENQUIRIES"
        ),
        "EMPLOYEE", List.of(
            "VIEW_LEADS",
            "VIEW_CUSTOMERS",
            "VIEW_SERVICE_REQUESTS","EDIT_SERVICE_REQUESTS",
            "VIEW_ENQUIRIES"
        )
    );

    // ── List all staff users ───────────────────────────────────
    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAllUsers() {
        List<AppUser> users = userRepo.findByRoleNotOrderByCreatedAtDesc("CUSTOMER");
        List<Map<String, Object>> result = users.stream().map(this::toMap).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("OK", result));
    }

    // ── Get user by ID ─────────────────────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getById(@PathVariable Long id) {
        return userRepo.findById(id)
            .map(u -> ResponseEntity.ok(ApiResponse.success("OK", toMap(u))))
            .orElse(ResponseEntity.notFound().build());
    }

    // ── Create staff account (admin/manager/employee) ──────────
    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> createUser(
            @RequestBody CreateUserRequest req) {

        if (userRepo.existsByEmail(req.getEmail())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Email already registered"));
        }
        if (req.getUsername() != null && userRepo.existsByUsername(req.getUsername())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Username already taken"));
        }

        // Determine permissions
        List<String> perms = req.getPermissions() != null && !req.getPermissions().isEmpty()
            ? req.getPermissions()
            : ROLE_PERMISSIONS.getOrDefault(req.getRole(), List.of());

        Employee linkedEmployee = null;
        if (req.getEmployeeId() != null) {
            linkedEmployee = employeeRepo.findById(req.getEmployeeId()).orElse(null);
        }

        AppUser user = AppUser.builder()
            .username(req.getUsername() != null ? req.getUsername() : req.getEmail())
            .email(req.getEmail())
            .password(passwordEncoder.encode(req.getPassword()))
            .fullName(req.getFullName())
            .mobile(req.getMobile())
            .role(req.getRole())
            .permissions(String.join(",", perms))
            .department(req.getDepartment())
            .employee(linkedEmployee)
            .active(true)
            .build();

        user = userRepo.save(user);
        log.info("Created staff user: {} role={}", user.getEmail(), user.getRole());
        return ResponseEntity.ok(ApiResponse.success("Account created", toMap(user)));
    }

    // ── Update user ────────────────────────────────────────────
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateUser(
            @PathVariable Long id, @RequestBody UpdateUserRequest req) {
        AppUser user = userRepo.findById(id).orElseThrow(() -> new RuntimeException("User not found"));

        if (req.getFullName() != null) user.setFullName(req.getFullName());
        if (req.getMobile() != null) user.setMobile(req.getMobile());
        if (req.getRole() != null) user.setRole(req.getRole());
        if (req.getDepartment() != null) user.setDepartment(req.getDepartment());
        if (req.getActive() != null) user.setActive(req.getActive());
        if (req.getPermissions() != null) user.setPermissions(String.join(",", req.getPermissions()));
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(req.getPassword()));
        }
        if (req.getEmployeeId() != null) {
            employeeRepo.findById(req.getEmployeeId()).ifPresent(user::setEmployee);
        }

        return ResponseEntity.ok(ApiResponse.success("Updated", toMap(userRepo.save(user))));
    }

    // ── Toggle active status ───────────────────────────────────
    @PatchMapping("/{id}/toggle-active")
    public ResponseEntity<ApiResponse<Map<String, Object>>> toggleActive(@PathVariable Long id) {
        AppUser user = userRepo.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
        user.setActive(!Boolean.TRUE.equals(user.getActive()));
        return ResponseEntity.ok(ApiResponse.success(
            user.getActive() ? "Account activated" : "Account deactivated",
            toMap(userRepo.save(user))));
    }

    // ── Reset password by admin ────────────────────────────────
    @PatchMapping("/{id}/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @PathVariable Long id, @RequestBody Map<String, String> body) {
        AppUser user = userRepo.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
        user.setPassword(passwordEncoder.encode(body.get("password")));
        userRepo.save(user);
        return ResponseEntity.ok(ApiResponse.success("Password reset", null));
    }

    // ── Delete user account ────────────────────────────────────
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Long id) {
        AppUser user = userRepo.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
        user.setActive(false);
        userRepo.save(user);
        return ResponseEntity.ok(ApiResponse.success("Account deactivated", null));
    }

    // ── Get default permissions for a role ─────────────────────
    @GetMapping("/permissions/{role}")
    public ResponseEntity<ApiResponse<List<String>>> getDefaultPermissions(@PathVariable String role) {
        return ResponseEntity.ok(ApiResponse.success("OK",
            ROLE_PERMISSIONS.getOrDefault(role.toUpperCase(), List.of())));
    }

    // ── All available permissions ──────────────────────────────
    @GetMapping("/permissions")
    public ResponseEntity<ApiResponse<Map<String, List<String>>>> getAllPermissions() {
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        grouped.put("Leads", List.of("VIEW_LEADS","EDIT_LEADS","DELETE_LEADS"));
        grouped.put("Customers", List.of("VIEW_CUSTOMERS","EDIT_CUSTOMERS","DELETE_CUSTOMERS"));
        grouped.put("Products", List.of("VIEW_PRODUCTS","EDIT_PRODUCTS","DELETE_PRODUCTS"));
        grouped.put("Services", List.of("VIEW_SERVICES","EDIT_SERVICES","DELETE_SERVICES"));
        grouped.put("Sales", List.of("VIEW_SALES","EDIT_SALES","DELETE_SALES"));
        grouped.put("Service Requests", List.of("VIEW_SERVICE_REQUESTS","EDIT_SERVICE_REQUESTS","DELETE_SERVICE_REQUESTS"));
        grouped.put("Quotations", List.of("VIEW_QUOTATIONS","EDIT_QUOTATIONS","DELETE_QUOTATIONS"));
        grouped.put("Stock", List.of("VIEW_STOCK","EDIT_STOCK"));
        grouped.put("Employees", List.of("VIEW_EMPLOYEES","EDIT_EMPLOYEES"));
        grouped.put("Reports", List.of("VIEW_REPORTS","EXPORT_REPORTS"));
        grouped.put("Communication", List.of("VIEW_COMMUNICATION","SEND_COMMUNICATION"));
        grouped.put("Gallery", List.of("VIEW_GALLERY","EDIT_GALLERY","DELETE_GALLERY"));
        grouped.put("Blogs", List.of("VIEW_BLOGS","EDIT_BLOGS","DELETE_BLOGS"));
        grouped.put("Templates", List.of("VIEW_TEMPLATES","EDIT_TEMPLATES"));
        grouped.put("Enquiries", List.of("VIEW_ENQUIRIES","EDIT_ENQUIRIES"));
        grouped.put("Users", List.of("MANAGE_USERS"));
        return ResponseEntity.ok(ApiResponse.success("OK", grouped));
    }

    // ── Helper: convert user to safe map ──────────────────────
    private Map<String, Object> toMap(AppUser u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("email", u.getEmail());
        m.put("fullName", u.getFullName());
        m.put("mobile", u.getMobile());
        m.put("role", u.getRole());
        m.put("permissions", u.getPermissions() != null
            ? Arrays.asList(u.getPermissions().split(",")) : List.of());
        m.put("department", u.getDepartment());
        m.put("active", u.getActive());
        m.put("createdAt", u.getCreatedAt());
        m.put("employeeId", u.getEmployee() != null ? u.getEmployee().getId() : null);
        m.put("employeeName", u.getEmployee() != null ? u.getEmployee().getName() : null);
        return m;
    }

    // DTOs
    @Data static class CreateUserRequest {
        private String username; private String email; private String password;
        private String fullName; private String mobile; private String role;
        private String department; private Long employeeId;
        private List<String> permissions;
    }
    @Data static class UpdateUserRequest {
        private String fullName; private String mobile; private String role;
        private String department; private String password; private Boolean active;
        private Long employeeId; private List<String> permissions;
    }
}
