package com.aquagreen.controller;

import com.aquagreen.dto.ApiResponse;
import com.aquagreen.model.*;
import com.aquagreen.repository.*;
import com.aquagreen.service.DocumentGenerationService;
import com.aquagreen.service.EmailService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/communications")
@RequiredArgsConstructor
@Slf4j
public class CommunicationController {

    private final CommunicationLogRepository logRepo;
    private final CustomerRepository customerRepo;
    private final DocumentTemplateRepository templateRepo;
    private final AutomationRuleRepository automationRepo;
    private final FilterPresetRepository filterPresetRepo;
    private final DocumentGenerationService docService;
    private final EmailService emailService;
    private final JavaMailSender mailSender;

    // ── GET all logs ──────────────────────────────────────────────
    @GetMapping
    public ResponseEntity<ApiResponse<List<CommunicationLog>>> getAll(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String channel) {
        List<CommunicationLog> list;
        if (status != null)  list = logRepo.findByStatusOrderByCreatedAtDesc(status);
        else if (channel != null) list = logRepo.findByChannelOrderByCreatedAtDesc(channel);
        else list = logRepo.findAllByOrderByCreatedAtDesc();
        return ResponseEntity.ok(ApiResponse.success("OK", list));
    }

    // ── Customer timeline ─────────────────────────────────────────
    @GetMapping("/customer/{customerId}/timeline")
    public ResponseEntity<ApiResponse<List<CommunicationLog>>> getTimeline(@PathVariable Long customerId) {
        return ResponseEntity.ok(ApiResponse.success("OK", logRepo.findTimelineByCustomerId(customerId)));
    }

    // ── Stats ─────────────────────────────────────────────────────
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", logRepo.count());
        stats.put("queued", logRepo.countByStatus("QUEUED"));
        stats.put("sent", logRepo.countByStatus("SENT"));
        stats.put("delivered", logRepo.countByStatus("DELIVERED"));
        stats.put("failed", logRepo.countByStatus("FAILED"));
        stats.put("sms", logRepo.countByChannel("SMS"));
        stats.put("whatsapp", logRepo.countByChannel("WHATSAPP"));
        stats.put("email", logRepo.countByChannel("EMAIL"));
        return ResponseEntity.ok(ApiResponse.success("OK", stats));
    }

    // ── Send individual message ───────────────────────────────────
    @PostMapping("/send")
    public ResponseEntity<ApiResponse<CommunicationLog>> send(@RequestBody SendRequest req) {

        DocumentTemplate template = req.getTemplateId() != null
            ? templateRepo.findById(req.getTemplateId()).orElse(null) : null;

        Customer customer = req.getCustomerId() != null
            ? customerRepo.findById(req.getCustomerId()).orElse(null) : null;

        Map<String, String> vars = docService.buildVariables(customer, req.getVariables());

        String rendered = renderContent(req.getContent(), template, vars);
        String subject  = req.getSubject() != null ? req.getSubject() : "Message from Aqua Green Agencies";
        String status   = req.getScheduledAt() != null ? "SCHEDULED" : "QUEUED";

        // ── Actually send the message ─────────────────────────────
        boolean deliverySuccess = false;
        String failReason = null;

        if (req.getScheduledAt() == null) {
            try {
                switch (req.getChannel() != null ? req.getChannel().toUpperCase() : "") {
                    case "EMAIL" -> {
                        String toEmail = req.getCustomerEmail();
                        if (customer != null && (toEmail == null || toEmail.isBlank()))
                            toEmail = customer.getEmail();
                        if (toEmail != null && !toEmail.isBlank()) {
                            deliverySuccess = sendEmail(toEmail, subject, rendered,
                                req.getCustomerName() != null ? req.getCustomerName() : (customer != null ? customer.getName() : "Customer"));
                        } else {
                            failReason = "No email address available for this customer";
                        }
                    }
                    case "WHATSAPP" -> {
                        // WhatsApp — log only (needs WhatsApp Business API integration)
                        String mobile = req.getCustomerMobile();
                        if (customer != null && (mobile == null || mobile.isBlank()))
                            mobile = customer.getMobile();
                        log.info("📱 WhatsApp to {}: {}", mobile, rendered.substring(0, Math.min(80, rendered.length())));
                        deliverySuccess = true; // logged
                    }
                    case "SMS" -> {
                        // SMS — log only (needs SMS gateway integration)
                        String mobile = req.getCustomerMobile();
                        if (customer != null && (mobile == null || mobile.isBlank()))
                            mobile = customer.getMobile();
                        log.info("💬 SMS to {}: {}", mobile, rendered.substring(0, Math.min(80, rendered.length())));
                        deliverySuccess = true; // logged
                    }
                    default -> {
                        log.warn("Unknown channel: {}", req.getChannel());
                        failReason = "Unknown channel: " + req.getChannel();
                    }
                }
                status = deliverySuccess ? "SENT" : "FAILED";
            } catch (Exception e) {
                status = "FAILED";
                failReason = e.getMessage();
                log.error("Send failed: {}", e.getMessage());
            }
        }

        CommunicationLog commLog = CommunicationLog.builder()
            .channel(req.getChannel())
            .customer(customer)
            .customerName(req.getCustomerName())
            .customerMobile(req.getCustomerMobile())
            .customerEmail(req.getCustomerEmail())
            .template(template)
            .templateName(template != null ? template.getName() : null)
            .messageContent(rendered)
            .subject(subject)
            .status(status)
            .sentBy("Admin")
            .scheduledAt(req.getScheduledAt())
            .sentAt(req.getScheduledAt() == null && deliverySuccess ? LocalDateTime.now() : null)
            .build();

        CommunicationLog saved = logRepo.save(commLog);

        if ("FAILED".equals(status)) {
            return ResponseEntity.status(500).body(
                ApiResponse.error("Send failed: " + (failReason != null ? failReason : "Unknown error")));
        }

        return ResponseEntity.ok(ApiResponse.success(
            req.getScheduledAt() != null ? "Message scheduled" : "Message sent via " + req.getChannel(), saved));
    }

    // ── Bulk send ─────────────────────────────────────────────────
    @PostMapping("/bulk-send")
    public ResponseEntity<ApiResponse<Map<String, Object>>> bulkSend(@RequestBody BulkSendRequest req) {
        String batchId = "BATCH-" + System.currentTimeMillis();
        AtomicInteger sent = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        List<String> failedNames = new ArrayList<>();

        DocumentTemplate template = req.getTemplateId() != null
            ? templateRepo.findById(req.getTemplateId()).orElse(null) : null;

        String subject = req.getSubject() != null ? req.getSubject() : "Message from Aqua Green Agencies";
        String channel = req.getChannel() != null ? req.getChannel().toUpperCase() : "EMAIL";

        for (Long customerId : req.getCustomerIds()) {
            try {
                Customer customer = customerRepo.findById(customerId).orElse(null);
                if (customer == null) { failed.incrementAndGet(); continue; }

                Map<String, String> vars = docService.buildVariables(customer, req.getVariables());
                String rendered = renderContent(req.getContent(), template, vars);

                boolean deliveryOk = false;
                String status = "FAILED";

                switch (channel) {
                    case "EMAIL" -> {
                        String email = customer.getEmail();
                        if (email != null && !email.isBlank()) {
                            deliveryOk = sendEmail(email, subject, rendered, customer.getName());
                            status = deliveryOk ? "SENT" : "FAILED";
                        } else {
                            status = "FAILED";
                        }
                    }
                    case "WHATSAPP", "SMS" -> {
                        log.info("📱 {} to {} ({}): {}",
                            channel, customer.getName(), customer.getMobile(),
                            rendered != null ? rendered.substring(0, Math.min(60, rendered.length())) : "");
                        deliveryOk = true;
                        status = "SENT";
                    }
                }

                CommunicationLog cLog = CommunicationLog.builder()
                    .channel(req.getChannel())
                    .customer(customer)
                    .customerName(customer.getName())
                    .customerMobile(customer.getMobile())
                    .customerEmail(customer.getEmail())
                    .template(template)
                    .templateName(template != null ? template.getName() : null)
                    .messageContent(rendered)
                    .subject(subject)
                    .status(status)
                    .sentBy("Admin")
                    .sentAt(deliveryOk ? LocalDateTime.now() : null)
                    .batchId(batchId)
                    .build();

                logRepo.save(cLog);
                if (deliveryOk) sent.incrementAndGet();
                else { failed.incrementAndGet(); failedNames.add(customer.getName()); }

            } catch (Exception e) {
                log.warn("Bulk send failed for customerId {}: {}", customerId, e.getMessage());
                failed.incrementAndGet();
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchId", batchId);
        result.put("channel", channel);
        result.put("totalRequested", req.getCustomerIds().size());
        result.put("sent", sent.get());
        result.put("failed", failed.get());
        result.put("failedCustomers", failedNames);

        String msg = String.format("Bulk send complete: %d sent, %d failed", sent.get(), failed.get());
        return ResponseEntity.ok(ApiResponse.success(msg, result));
    }

    // ── Test email endpoint ───────────────────────────────────────
    @PostMapping("/test-email")
    public ResponseEntity<ApiResponse<String>> testEmail(@RequestBody Map<String, String> body) {
        String toEmail = body.getOrDefault("email", "pravinkathirneels24@gmail.com");
        boolean ok = emailService.sendTestEmail(toEmail);
        if (ok) return ResponseEntity.ok(ApiResponse.success("✅ Test email sent to " + toEmail, "OK"));
        return ResponseEntity.status(500).body(ApiResponse.error(
            "❌ Email failed — check SMTP config in application.properties. Make sure you are using a Gmail App Password (not your real password)."
        ));
    }

    // ── Update log status ─────────────────────────────────────────
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<CommunicationLog>> updateStatus(
            @PathVariable Long id, @RequestParam String status) {
        CommunicationLog cl = logRepo.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
        cl.setStatus(status);
        if ("DELIVERED".equals(status)) cl.setDeliveredAt(LocalDateTime.now());
        return ResponseEntity.ok(ApiResponse.success("Updated", logRepo.save(cl)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        logRepo.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }

    // ── Automation Rules ──────────────────────────────────────────
    @GetMapping("/automation")
    public ResponseEntity<ApiResponse<List<AutomationRule>>> getAutomation() {
        return ResponseEntity.ok(ApiResponse.success("OK", automationRepo.findByActiveTrueOrderByCreatedAtDesc()));
    }

    @PostMapping("/automation")
    public ResponseEntity<ApiResponse<AutomationRule>> createAutomation(@RequestBody AutomationRule rule) {
        rule.setId(null);
        return ResponseEntity.ok(ApiResponse.success("Rule created", automationRepo.save(rule)));
    }

    @PutMapping("/automation/{id}")
    public ResponseEntity<ApiResponse<AutomationRule>> updateAutomation(@PathVariable Long id, @RequestBody AutomationRule rule) {
        rule.setId(id);
        return ResponseEntity.ok(ApiResponse.success("Updated", automationRepo.save(rule)));
    }

    @DeleteMapping("/automation/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAutomation(@PathVariable Long id) {
        automationRepo.findById(id).ifPresent(r -> { r.setActive(false); automationRepo.save(r); });
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }

    // ── Filter Presets ────────────────────────────────────────────
    @GetMapping("/filter-presets")
    public ResponseEntity<ApiResponse<List<FilterPreset>>> getPresets() {
        return ResponseEntity.ok(ApiResponse.success("OK", filterPresetRepo.findByActiveTrueOrderByUsageCountDesc()));
    }

    @PostMapping("/filter-presets")
    public ResponseEntity<ApiResponse<FilterPreset>> createPreset(@RequestBody FilterPreset preset) {
        preset.setId(null);
        return ResponseEntity.ok(ApiResponse.success("Preset saved", filterPresetRepo.save(preset)));
    }

    @DeleteMapping("/filter-presets/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePreset(@PathVariable Long id) {
        filterPresetRepo.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }

    // ── Customer filter ───────────────────────────────────────────
    @PostMapping("/filter-customers")
    public ResponseEntity<ApiResponse<List<Customer>>> filterCustomers(@RequestBody CustomerFilterRequest req) {
        List<Customer> all = customerRepo.findByActiveTrueOrderByCreatedAtDesc();
        List<Customer> filtered = new ArrayList<>(all);
        if (req.getCustomerType() != null && !req.getCustomerType().isBlank())
            filtered.removeIf(c -> !req.getCustomerType().equals(c.getCustomerType()));
        if (req.getCity() != null && !req.getCity().isBlank())
            filtered.removeIf(c -> c.getCity() == null || !c.getCity().toLowerCase().contains(req.getCity().toLowerCase()));
        if (req.getSearch() != null && !req.getSearch().isBlank()) {
            String q = req.getSearch().toLowerCase();
            filtered.removeIf(c -> !(
                (c.getName() != null && c.getName().toLowerCase().contains(q)) ||
                (c.getMobile() != null && c.getMobile().contains(q)) ||
                (c.getEmail() != null && c.getEmail().toLowerCase().contains(q))
            ));
        }
        return ResponseEntity.ok(ApiResponse.success("Filtered: " + filtered.size() + " customers", filtered));
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════

    /** Render message content — prefers custom content, falls back to template */
    private String renderContent(String customContent, DocumentTemplate template, Map<String, String> vars) {
        String raw = customContent;
        if (raw == null || raw.isBlank()) {
            if (template != null) {
                raw = template.getMessageContent() != null ? template.getMessageContent() : template.getHtmlContent();
            }
        }
        return raw != null ? docService.renderTemplate(raw, vars) : "";
    }

    /** Send an HTML email via Gmail SMTP */
    private boolean sendEmail(String toEmail, String subject, String body, String recipientName) {
        try {
            var msg = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom("Aqua Green Agencies <pravinkathirneels24@gmail.com>");
            helper.setTo(toEmail);
            helper.setSubject(subject);

            // If body looks like HTML, send as HTML; otherwise wrap it
            boolean isHtml = body != null && (body.contains("<") && body.contains(">"));
            String htmlBody = isHtml ? body : buildPlainEmailHtml(recipientName, body, subject);

            helper.setText(htmlBody, true);
            mailSender.send(msg);
            log.info("✅ Email sent to {} <{}>", recipientName, toEmail);
            return true;
        } catch (Exception e) {
            log.error("❌ Email send failed to {}: {}", toEmail, e.getMessage());
            return false;
        }
    }

    /** Wrap plain text content in a nice HTML email template */
    private String buildPlainEmailHtml(String name, String message, String subject) {
        return """
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"></head>
            <body style="margin:0;padding:0;background:#f4f4f4;font-family:Arial,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f4f4;padding:32px 16px;">
                <tr><td align="center">
                  <table width="580" cellpadding="0" cellspacing="0" style="background:#fff;border-radius:14px;overflow:hidden;box-shadow:0 4px 20px rgba(0,0,0,.08);">
                    <tr>
                      <td style="background:linear-gradient(135deg,#0a4f3c,#1a7a5c);padding:28px 32px;text-align:center;">
                        <h1 style="color:#fff;margin:0;font-size:20px;font-weight:800;">Aqua Green Agencies</h1>
                        <p style="color:#7fe8c5;margin:5px 0 0;font-size:12px;">RO Water Purifier · Coimbatore</p>
                      </td>
                    </tr>
                    <tr>
                      <td style="padding:32px 32px 24px;">
                        <p style="color:#374151;font-size:15px;margin:0 0 18px;">Dear <strong>%s</strong>,</p>
                        <div style="color:#374151;font-size:14px;line-height:1.8;white-space:pre-wrap;">%s</div>
                      </td>
                    </tr>
                    <tr>
                      <td style="background:#f9fafb;padding:18px 32px;border-top:1px solid #e5e7eb;text-align:center;">
                        <p style="color:#9ca3af;font-size:11px;margin:0;">Aqua Green Agencies · Near ESI Hospital, Neelikonampalayam, Coimbatore — 641033</p>
                        <p style="color:#9ca3af;font-size:11px;margin:4px 0 0;">📞 09054617008 · This is an automated message.</p>
                      </td>
                    </tr>
                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(name != null ? name : "Customer", message != null ? message : "");
    }

    // ─── DTOs ────────────────────────────────────────────────────
    @Data static class SendRequest {
        private String channel;
        private Long customerId;
        private String customerName;
        private String customerMobile;
        private String customerEmail;
        private Long templateId;
        private String content;
        private String subject;
        private Map<String, String> variables;
        private LocalDateTime scheduledAt;
    }

    @Data static class BulkSendRequest {
        private String channel;
        private List<Long> customerIds;
        private Long templateId;
        private String content;
        private String subject;
        private Map<String, String> variables;
    }

    @Data static class CustomerFilterRequest {
        private String customerType;
        private String city;
        private String search;
    }
}
