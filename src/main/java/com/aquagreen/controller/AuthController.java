package com.aquagreen.controller;

import com.aquagreen.dto.ApiResponse;
import com.aquagreen.model.AppUser;
import com.aquagreen.model.Customer;
import com.aquagreen.repository.AppUserRepository;
import com.aquagreen.repository.CustomerRepository;
import com.aquagreen.service.EmailService;
import com.aquagreen.service.JwtUtil;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AppUserRepository userRepo;
    private final CustomerRepository customerRepo;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    // ── Login ───────────────────────────────────────────────────
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(@RequestBody LoginRequest req) {
        AppUser user = userRepo.findByEmail(req.getEmail())
                .or(() -> userRepo.findByUsername(req.getEmail()))
                .orElse(null);

        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            return ResponseEntity.status(401).body(ApiResponse.error("Invalid email or password"));
        }
        if (!Boolean.TRUE.equals(user.getActive())) {
            return ResponseEntity.status(403).body(ApiResponse.error("Account is deactivated. Contact admin."));
        }

        // Embed permissions in token
        String perms = resolvePermissions(user);
        String token = jwtUtil.generateToken(user.getUsername(), user.getRole(), perms);
        String refreshToken = jwtUtil.generateRefreshToken(user.getUsername());
        user.setRefreshToken(refreshToken);
        user.setLastLoginAt(LocalDateTime.now());
        userRepo.save(user);

        return ResponseEntity.ok(ApiResponse.success("Login successful", Map.of(
            "token", token,
            "refreshToken", refreshToken,
            "user", buildUserMap(user)
        )));
    }

    // ── Get current user (/me) ──────────────────────────────────
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> me(
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        String username = jwtUtil.extractUsername(token);
        AppUser user = userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return ResponseEntity.ok(ApiResponse.success("OK", buildUserMap(user)));
    }

    // ── Refresh token ───────────────────────────────────────────
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Map<String, String>>> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || !jwtUtil.isTokenValid(refreshToken)) {
            return ResponseEntity.status(401).body(ApiResponse.error("Invalid refresh token"));
        }
        String username = jwtUtil.extractUsername(refreshToken);
        AppUser user = userRepo.findByUsername(username).orElse(null);
        if (user == null || !refreshToken.equals(user.getRefreshToken())) {
            return ResponseEntity.status(401).body(ApiResponse.error("Refresh token mismatch"));
        }
        String perms = resolvePermissions(user);
        String newToken = jwtUtil.generateToken(username, user.getRole(), perms);
        return ResponseEntity.ok(ApiResponse.success("Token refreshed", Map.of("token", newToken)));
    }

    // ── Forgot password ─────────────────────────────────────────
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        AppUser user = userRepo.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.ok(ApiResponse.success("If this email exists, a reset link has been sent.", null));
        }
        String token = UUID.randomUUID().toString();
        user.setPasswordResetToken(token);
        user.setPasswordResetExpiry(LocalDateTime.now().plusHours(1));
        userRepo.save(user);
        emailService.sendPasswordResetEmail(email, user.getFullName() != null ? user.getFullName() : email, token);
        return ResponseEntity.ok(ApiResponse.success("Password reset link sent to your email.", null));
    }

    // ── Reset password ──────────────────────────────────────────
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@RequestBody ResetPasswordRequest req) {
        AppUser user = userRepo.findByPasswordResetToken(req.getToken()).orElse(null);
        if (user == null || user.getPasswordResetExpiry() == null
                || user.getPasswordResetExpiry().isBefore(LocalDateTime.now())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Invalid or expired reset token"));
        }
        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        user.setPasswordResetToken(null);
        user.setPasswordResetExpiry(null);
        userRepo.save(user);
        return ResponseEntity.ok(ApiResponse.success("Password reset successfully", null));
    }

    // ── Change password ─────────────────────────────────────────
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @RequestBody ChangePasswordRequest req,
            @RequestHeader("Authorization") String authHeader) {
        String username = jwtUtil.extractUsername(authHeader.substring(7));
        AppUser user = userRepo.findByUsername(username).orElseThrow(() -> new RuntimeException("User not found"));
        if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPassword())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Current password is incorrect"));
        }
        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userRepo.save(user);
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully", null));
    }

    // ── Helpers ─────────────────────────────────────────────────

    /** SUPER_ADMIN gets ALL, others get their stored permissions */
    private String resolvePermissions(AppUser user) {
        if ("SUPER_ADMIN".equals(user.getRole())) return "ALL";
        return user.getPermissions() != null ? user.getPermissions() : "";
    }

    private Map<String, Object> buildUserMap(AppUser user) {
        String permsStr = resolvePermissions(user);
        List<String> permList = permsStr.isEmpty() ? List.of() :
                "ALL".equals(permsStr) ? List.of("ALL") :
                Arrays.asList(permsStr.split(","));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", user.getId());
        m.put("username", user.getUsername());
        m.put("email", user.getEmail());
        m.put("fullName", user.getFullName() != null ? user.getFullName() : "");
        m.put("mobile", user.getMobile() != null ? user.getMobile() : "");
        m.put("role", user.getRole());
        m.put("permissions", permList);
        m.put("department", user.getDepartment());
        m.put("customerId", user.getCustomer() != null ? user.getCustomer().getId() : null);
        return m;
    }


    // ── Test email (SUPER_ADMIN only) ───────────────────────────
    @PostMapping("/test-email")
    public ResponseEntity<ApiResponse<String>> testEmail(
            @RequestBody Map<String, String> body,
            @RequestHeader("Authorization") String authHeader) {
        String username = jwtUtil.extractUsername(authHeader.substring(7));
        AppUser user = userRepo.findByUsername(username).orElseThrow(() -> new RuntimeException("Not found"));
        if (!"SUPER_ADMIN".equals(user.getRole()) && !"ADMIN".equals(user.getRole())) {
            return ResponseEntity.status(403).body(ApiResponse.error("Admin only"));
        }
        String target = body.getOrDefault("email", user.getEmail());
        boolean ok = emailService.sendTestEmail(target);
        if (ok) return ResponseEntity.ok(ApiResponse.success("Test email sent to " + target, "OK"));
        return ResponseEntity.status(500).body(ApiResponse.error("Email failed — check SMTP config in application.properties"));
    }

    @Data static class LoginRequest { private String email; private String password; }
    @Data static class ResetPasswordRequest { private String token; private String newPassword; }
    @Data static class ChangePasswordRequest { private String currentPassword; private String newPassword; }
}
