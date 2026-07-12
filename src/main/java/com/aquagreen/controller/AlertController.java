package com.aquagreen.controller;

import com.aquagreen.dto.ApiResponse;
import com.aquagreen.model.ServiceRequest;
import com.aquagreen.repository.ServiceRequestRepository;
import com.aquagreen.service.SmsService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AlertController — manages proactive filter/service due reminders.
 *
 * GET  /api/alerts/due          — list customers whose filter or service is due within N days
 * POST /api/alerts/send         — send SMS/WhatsApp reminder to one or many customers
 * POST /api/alerts/send-all-due — trigger reminders for everyone due in next 10 days
 */
@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
@Slf4j
public class AlertController {

    private final ServiceRequestRepository serviceRepo;
    private final SmsService smsService;

    public record AlertItem(
        Long serviceRequestId, String ticketNumber,
        String customerName, String customerMobile,
        String alertType,        // FILTER_DUE | SERVICE_DUE
        String dueDate,
        int daysUntilDue
    ) {}

    /** Returns customers with filter or service due within the next N days (default 30) */
    @GetMapping("/due")
    public ResponseEntity<ApiResponse<List<AlertItem>>> getDue(
            @RequestParam(defaultValue = "30") int days) {

        LocalDateTime now  = LocalDateTime.now();
        LocalDateTime to   = now.plusDays(days);

        List<AlertItem> alerts = new ArrayList<>();

        serviceRepo.findFiltersDueBetween(now, to).forEach(s -> {
            long d = java.time.Duration.between(now, s.getNextFilterDueDate()).toDays();
            alerts.add(new AlertItem(s.getId(), s.getTicketNumber(),
                s.getCustomerName(), s.getCustomerMobile(),
                "FILTER_DUE",
                s.getNextFilterDueDate().toLocalDate().toString(),
                (int) d));
        });

        serviceRepo.findServicesDueBetween(now, to).forEach(s -> {
            long d = java.time.Duration.between(now, s.getNextServiceDueDate()).toDays();
            alerts.add(new AlertItem(s.getId(), s.getTicketNumber(),
                s.getCustomerName(), s.getCustomerMobile(),
                "SERVICE_DUE",
                s.getNextServiceDueDate().toLocalDate().toString(),
                (int) d));
        });

        alerts.sort(Comparator.comparingInt(AlertItem::daysUntilDue));
        return ResponseEntity.ok(ApiResponse.success("OK", alerts));
    }

    @Data
    static class SendAlertRequest {
        private Long serviceRequestId;
        private String channel;     // SMS | WHATSAPP
        private String alertType;   // FILTER_DUE | SERVICE_DUE
        private String customMessage;
    }

    /** Send a reminder to a single customer */
    @PostMapping("/send")
    public ResponseEntity<ApiResponse<String>> sendAlert(@RequestBody SendAlertRequest req) {
        ServiceRequest sr = serviceRepo.findById(req.getServiceRequestId())
            .orElseThrow(() -> new RuntimeException("Service request not found"));

        String msg = buildMessage(sr, req.getAlertType(), req.getCustomMessage());
        String mobile = sr.getCustomerMobile();

        if ("SMS".equals(req.getChannel())) {
            smsService.sendLeadFollowup(mobile, sr.getCustomerName(), msg); // reuse as generic
            log.info("[Alert] SMS sent to {} for {}", mobile, req.getAlertType());
        } else {
            String waUrl = "https://wa.me/91" + mobile.replaceAll("[^0-9]","")
                + "?text=" + java.net.URLEncoder.encode(msg, java.nio.charset.StandardCharsets.UTF_8);
            log.info("[Alert] WhatsApp URL generated for {}: {}", mobile, waUrl);
            return ResponseEntity.ok(ApiResponse.success("OK", waUrl));
        }
        return ResponseEntity.ok(ApiResponse.success("Reminder sent to " + mobile, "OK"));
    }

    /** Send reminders to all customers due in next 10 days */
    @PostMapping("/send-all-due")
    public ResponseEntity<ApiResponse<Map<String,Object>>> sendAllDue(
            @RequestParam(defaultValue = "SMS") String channel,
            @RequestParam(defaultValue = "10")  int days) {

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime to  = now.plusDays(days);
        int sent = 0, failed = 0;

        for (ServiceRequest sr : serviceRepo.findFiltersDueBetween(now, to)) {
            try {
                String msg = buildMessage(sr, "FILTER_DUE", null);
                if ("SMS".equals(channel)) smsService.sendLeadFollowup(sr.getCustomerMobile(), sr.getCustomerName(), msg);
                sent++;
            } catch (Exception e) { failed++; }
        }
        for (ServiceRequest sr : serviceRepo.findServicesDueBetween(now, to)) {
            try {
                String msg = buildMessage(sr, "SERVICE_DUE", null);
                if ("SMS".equals(channel)) smsService.sendLeadFollowup(sr.getCustomerMobile(), sr.getCustomerName(), msg);
                sent++;
            } catch (Exception e) { failed++; }
        }
        return ResponseEntity.ok(ApiResponse.success("Done", Map.of("sent", sent, "failed", failed)));
    }

    private String buildMessage(ServiceRequest sr, String alertType, String custom) {
        if (custom != null && !custom.isBlank()) return custom;
        if ("FILTER_DUE".equals(alertType))
            return String.format(
                "Dear %s, your RO water purifier filter replacement is due. Please call us to schedule a service visit. - Aqua Green Agencies, Coimbatore. Ph: 09054617008",
                sr.getCustomerName());
        return String.format(
            "Dear %s, your RO water purifier is due for its annual service. Please call us to schedule a visit. - Aqua Green Agencies, Coimbatore. Ph: 09054617008",
            sr.getCustomerName());
    }
}
