package com.aquagreen.controller;

import com.aquagreen.dto.ApiResponse;
import com.aquagreen.model.DocumentTemplate;
import com.aquagreen.repository.DocumentTemplateRepository;
import com.aquagreen.repository.LeadRepository;
import com.aquagreen.service.SmsService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/sms")
@RequiredArgsConstructor
@Slf4j
public class SmsController {

    private final SmsService smsService;
    private final LeadRepository leadRepo;
    private final DocumentTemplateRepository templateRepo;

    // ── List all SMS templates ────────────────────────────────────

    @GetMapping("/templates")
    public ResponseEntity<ApiResponse<List<DocumentTemplate>>> listTemplates(
            @RequestParam(required = false) String event) {
        List<DocumentTemplate> list = event != null
            ? templateRepo.findByTemplateTypeAndSmsEventAndActiveTrueOrderByCreatedAtDesc("SMS", event)
            : templateRepo.findByTemplateTypeAndActiveTrueOrderByCreatedAtDesc("SMS");
        return ResponseEntity.ok(ApiResponse.success("OK", list));
    }

    // ── Preview: resolve placeholders and show what will be sent ──

    @Data static class PreviewRequest {
        private Long templateId;
        private Map<String, String> variables;
    }

    @PostMapping("/preview")
    public ResponseEntity<ApiResponse<Map<String, String>>> preview(@RequestBody PreviewRequest req) {
        Optional<DocumentTemplate> tpl = templateRepo.findById(req.getTemplateId());
        if (tpl.isEmpty()) return ResponseEntity.notFound().<ApiResponse<Map<String, String>>>build();
        String resolved = smsService.resolveContent(tpl.get().getMessageContent(), req.getVariables());
        return ResponseEntity.ok(ApiResponse.success("OK", Map.of(
            "resolved",     resolved,
            "charCount",    String.valueOf(resolved.length()),
            "smsParts",     String.valueOf((int) Math.ceil(resolved.length() / 160.0)),
            "msg91Id",      tpl.get().getMsg91TemplateId() != null ? tpl.get().getMsg91TemplateId() : ""
        )));
    }

    // ── Test: send to your own number with a specific template ────

    @Data static class TestRequest {
        private String mobile;
        private Long templateId;
        private Map<String, String> variables;
    }

    @PostMapping("/test")
    public ResponseEntity<ApiResponse<String>> test(@RequestBody TestRequest req) {
        DocumentTemplate tpl = templateRepo.findById(req.getTemplateId())
            .orElseThrow(() -> new RuntimeException("Template not found"));
        if (tpl.getMsg91TemplateId() == null || tpl.getMsg91TemplateId().isBlank())
            return ResponseEntity.badRequest().body(
                ApiResponse.error("This template has no MSG91 Template ID. Edit the template and add it first."));
        log.info("[SMS] Manual test — template='{}' event='{}' to '{}'",
            tpl.getName(), tpl.getSmsEvent(), req.getMobile());
        smsService.sendRaw(tpl.getMsg91TemplateId(), req.getMobile(),
            req.getVariables() != null ? req.getVariables() : Map.of(), "manual-test");
        return ResponseEntity.ok(ApiResponse.<String>success(
            "SMS triggered to " + req.getMobile() + ". Check your phone and MSG91 Logs.", null));
    }

    // ── Send by event — fires the default template for that event ─

    @Data static class SendByEventRequest {
        private String event;   // e.g. ENQUIRY_RECEIVED
        private String mobile;
        private Map<String, String> variables;
    }

    @PostMapping("/send")
    public ResponseEntity<ApiResponse<String>> sendByEvent(@RequestBody SendByEventRequest req) {
        smsService.sendByEvent(req.getEvent(), req.getMobile(), req.getVariables());
        return ResponseEntity.ok(ApiResponse.<String>success("SMS dispatched.", null));
    }

    // ── Lead follow-up — fires LEAD_FOLLOWUP template for a lead ─

    @PostMapping("/lead-followup/{id}")
    public ResponseEntity<ApiResponse<String>> leadFollowup(@PathVariable Long id) {
        return leadRepo.findById(id).map(lead -> {
            smsService.sendLeadFollowup(lead.getMobile(), lead.getName(), lead.getRequirement());
            return ResponseEntity.ok(ApiResponse.<String>success(
                "Follow-up SMS queued for " + lead.getMobile(), null));
        }).orElseGet(() -> ResponseEntity.notFound().<ApiResponse<String>>build());
    }
}
