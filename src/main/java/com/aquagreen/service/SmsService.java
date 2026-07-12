package com.aquagreen.service;

import com.aquagreen.model.DocumentTemplate;
import com.aquagreen.repository.DocumentTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.regex.*;

/**
 * MSG91 SMS Service.
 *
 * Templates are stored in the document_templates table (templateType = 'SMS').
 * Each template has:
 *   - messageContent  : the message body with {{variable}} placeholders
 *   - msg91TemplateId : the Flow ID from the MSG91 panel
 *   - smsEvent        : the business event key (ENQUIRY_RECEIVED, etc.)
 *   - isDefault       : only the default template fires automatically
 *
 * How variables work:
 *   Template content: "Dear {{name}}, your ticket {{ticket}} is assigned to {{technician}}."
 *   Variables map:   { "name": "Ravi", "ticket": "TKT-001", "technician": "Kumar" }
 *   → Resolved text is sent as context to MSG91 alongside the flow_id.
 *   → MSG91 receives the variables map keyed by variable name.
 *
 * If msg91.enabled=false, the service logs a dry-run and does nothing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SmsService {

    // ── SMS event keys ───────────────────────────────────────────
    public static final String EVT_ENQUIRY_RECEIVED   = "ENQUIRY_RECEIVED";
    public static final String EVT_SERVICE_BOOKED     = "SERVICE_BOOKED";
    public static final String EVT_SERVICE_COMPLETED  = "SERVICE_COMPLETED";
    public static final String EVT_QUOTATION_SENT     = "QUOTATION_SENT";
    public static final String EVT_LEAD_FOLLOWUP      = "LEAD_FOLLOWUP";
    public static final String EVT_PAYMENT_RECEIVED   = "PAYMENT_RECEIVED";

    private static final String API_URL = "https://api.msg91.com/api/v5/flow/";

    @Value("${msg91.enabled:false}")    private boolean enabled;
    @Value("${msg91.auth-key:}")        private String  authKey;
    @Value("${msg91.sender-id:TESTER}") private String  senderId;
    @Value("${msg91.country:91}")       private String  country;

    private final DocumentTemplateRepository templateRepo;
    private final RestTemplate restTemplate = new RestTemplate();

    // ── Public send methods — one per business event ─────────────

    public void sendEnquiryReceived(String mobile, String customerName, String productName) {
        sendByEvent(EVT_ENQUIRY_RECEIVED, mobile, Map.of(
            "name",    safe(customerName),
            "product", safe(productName, "our products")
        ));
    }

    public void sendServiceBooked(String mobile, String customerName,
                                  String ticketNumber, String technicianName) {
        sendByEvent(EVT_SERVICE_BOOKED, mobile, Map.of(
            "name",       safe(customerName),
            "ticket",     safe(ticketNumber),
            "technician", safe(technicianName, "our team")
        ));
    }

    public void sendServiceCompleted(String mobile, String customerName,
                                     String ticketNumber, String amount) {
        sendByEvent(EVT_SERVICE_COMPLETED, mobile, Map.of(
            "name",   safe(customerName),
            "ticket", safe(ticketNumber),
            "amount", safe(amount, "0")
        ));
    }

    public void sendQuotationSent(String mobile, String customerName,
                                  String quotationNumber, String totalAmount) {
        sendByEvent(EVT_QUOTATION_SENT, mobile, Map.of(
            "name",   safe(customerName),
            "quot",   safe(quotationNumber),
            "amount", safe(totalAmount, "0")
        ));
    }

    public void sendLeadFollowup(String mobile, String leadName, String requirement) {
        sendByEvent(EVT_LEAD_FOLLOWUP, mobile, Map.of(
            "name",        safe(leadName),
            "requirement", safe(requirement, "your requirement")
        ));
    }

    public void sendPaymentReceived(String mobile, String customerName,
                                    String invoiceNumber, String amount) {
        sendByEvent(EVT_PAYMENT_RECEIVED, mobile, Map.of(
            "name",    safe(customerName),
            "invoice", safe(invoiceNumber),
            "amount",  safe(amount, "0")
        ));
    }

    // ── Send by event key — looks up default DB template ─────────

    public void sendByEvent(String smsEvent, String mobile, Map<String, String> variables) {
        Optional<DocumentTemplate> tplOpt = templateRepo
            .findByTemplateTypeAndSmsEventAndIsDefaultTrueAndActiveTrue("SMS", smsEvent);

        if (tplOpt.isEmpty()) {
            log.warn("[SMS] No active default template for event '{}'. " +
                "Create one in Admin → Templates → SMS.", smsEvent);
            return;
        }
        DocumentTemplate tpl = tplOpt.get();
        if (tpl.getMsg91TemplateId() == null || tpl.getMsg91TemplateId().isBlank()) {
            log.warn("[SMS] Template '{}' (event={}) has no MSG91 Template ID set. " +
                "Edit the template and paste the ID from your MSG91 panel.", tpl.getName(), smsEvent);
            return;
        }
        sendRaw(tpl.getMsg91TemplateId(), mobile, variables, smsEvent + " [" + tpl.getName() + "]");
    }

    /**
     * Send directly with a known MSG91 template ID — used by the admin test endpoint
     * and manual send from the SMS controller.
     */
    public void sendRaw(String msg91TemplateId, String mobile,
                        Map<String, String> variables, String eventLabel) {
        if (!enabled) {
            log.info("[SMS dry-run] event='{}' mobile='{}' template='{}' vars={}",
                eventLabel, mobile, msg91TemplateId, variables);
            return;
        }
        if (mobile == null || mobile.isBlank()) {
            log.warn("[SMS] No mobile number for event '{}', skipping.", eventLabel);
            return;
        }
        try {
            String normalised = mobile.replaceAll("[^0-9]", "");
            if (!normalised.startsWith(country)) normalised = country + normalised;

            Map<String, String> recipient = new LinkedHashMap<>(variables);
            recipient.put("mobiles", normalised);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("flow_id",    msg91TemplateId);
            body.put("sender",     senderId);
            body.put("recipients", List.of(recipient));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("authkey", authKey);

            ResponseEntity<String> resp = restTemplate.postForEntity(
                API_URL, new HttpEntity<>(body, headers), String.class);

            if (resp.getStatusCode().is2xxSuccessful()) {
                log.info("[SMS] Sent event='{}' to {} → {}", eventLabel, normalised, resp.getBody());
            } else {
                log.warn("[SMS] Non-2xx for event='{}': {} {}",
                    eventLabel, resp.getStatusCode(), resp.getBody());
            }
        } catch (Exception ex) {
            log.error("[SMS] Failed for event='{}' mobile='{}': {}",
                eventLabel, mobile, ex.getMessage());
        }
    }

    /**
     * Preview: resolve {{variable}} placeholders in the template message content
     * using the provided variables map. Returns the resolved string.
     */
    public String resolveContent(String content, Map<String, String> variables) {
        if (content == null) return "";
        StringBuffer sb = new StringBuffer();
        Matcher m = Pattern.compile("\\{\\{(\\w+)\\}\\}").matcher(content);
        while (m.find()) {
            String val = variables.getOrDefault(m.group(1), m.group(0));
            m.appendReplacement(sb, Matcher.quoteReplacement(val));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String safe(String v) { return v != null ? v : ""; }
    private String safe(String v, String fallback) { return (v != null && !v.isBlank()) ? v : fallback; }
}
