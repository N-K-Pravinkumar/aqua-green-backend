package com.aquagreen.config;

import com.aquagreen.model.*;
import com.aquagreen.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class TemplateSeedConfig implements CommandLineRunner {

    private final DocumentTemplateRepository templateRepo;
    private final FilterPresetRepository filterPresetRepo;
    private final AutomationRuleRepository automationRepo;

    @Override
    public void run(String... args) {
        seedTemplates();
        seedFilterPresets();
        seedAutomationRules();
    }

    void seedTemplates() {
        if (templateRepo.count() > 0) return;

        templateRepo.saveAll(List.of(
            // SMS Templates
            DocumentTemplate.builder()
                .name("Service Reminder SMS").templateType("SMS").category("COMMUNICATION")
                .messageContent("Dear {{customerName}}, your RO service is due. Book now: Call {{companyPhone}} — Aqua Green Agencies")
                .placeholders("customerName,companyPhone")
                .isDefault(true).active(true).version(1).createdBy("System").build(),

            DocumentTemplate.builder()
                .name("Annual Service Expiry Reminder SMS").templateType("SMS").category("COMMUNICATION")
                .messageContent("Dear {{customerName}}, your Annual Service expires on {{amcExpiryDate}}. Renew now! Call {{companyPhone}} — Aqua Green Agencies")
                .placeholders("customerName,amcExpiryDate,companyPhone")
                .active(true).version(1).createdBy("System").build(),

            DocumentTemplate.builder()
                .name("Payment Reminder SMS").templateType("SMS").category("COMMUNICATION")
                .messageContent("Dear {{customerName}}, payment of Rs.{{grandTotal}} is pending for Invoice {{invoiceNumber}}. Pay now. — Aqua Green Agencies {{companyPhone}}")
                .placeholders("customerName,grandTotal,invoiceNumber,companyPhone")
                .active(true).version(1).createdBy("System").build(),

            DocumentTemplate.builder()
                .name("Installation Confirmation SMS").templateType("SMS").category("COMMUNICATION")
                .messageContent("Dear {{customerName}}, your RO purifier ({{productName}}) installation is confirmed for {{installationDate}}. Technician: {{technician}}. — Aqua Green Agencies")
                .placeholders("customerName,productName,installationDate,technician")
                .active(true).version(1).createdBy("System").build(),

            // WhatsApp Templates
            DocumentTemplate.builder()
                .name("Welcome WhatsApp").templateType("WHATSAPP").category("COMMUNICATION")
                .messageContent("Hello {{customerName}}! 👋\n\nThank you for choosing *Aqua Green Agencies* for your water purification needs.\n\n✅ Product: {{productName}}\n📅 Installation: {{installationDate}}\n🔧 Next Service: {{nextServiceDate}}\n\nFor any assistance, call us at {{companyPhone}}.\n\n_Aqua Green Agencies — Pure Water, Healthy Life_ 💧")
                .placeholders("customerName,productName,installationDate,nextServiceDate,companyPhone")
                .isDefault(true).active(true).version(1).createdBy("System").build(),

            DocumentTemplate.builder()
                .name("Service Complete WhatsApp").templateType("WHATSAPP").category("COMMUNICATION")
                .messageContent("Hello {{customerName}},\n\n✅ *Service Completed Successfully!*\n\n🔧 Technician: {{technician}}\n📋 Service: Filter replacement + sanitisation\n🗓 Next Due: {{nextServiceDate}}\n\nThank you for trusting Aqua Green Agencies! 💧\n\nFor queries: {{companyPhone}}")
                .placeholders("customerName,technician,nextServiceDate,companyPhone")
                .active(true).version(1).createdBy("System").build(),

            DocumentTemplate.builder()
                .name("Quotation WhatsApp").templateType("WHATSAPP").category("COMMUNICATION")
                .messageContent("Hello {{customerName}},\n\n📋 *Your Quotation is Ready!*\n\nQuotation No: {{quotationNumber}}\n💧 Product: {{productName}}\n💰 Amount: ₹{{grandTotal}}\n📅 Valid for 30 days\n\nPlease find the PDF quotation attached.\n\nFor queries: {{companyPhone}}\n— Aqua Green Agencies")
                .placeholders("customerName,quotationNumber,productName,grandTotal,companyPhone")
                .active(true).version(1).createdBy("System").build(),

            DocumentTemplate.builder()
                .name("Filter Due WhatsApp").templateType("WHATSAPP").category("COMMUNICATION")
                .messageContent("Hello {{customerName}},\n\n⚠️ *Filter Change Due*\n\nYour RO filter for *{{productName}}* is due for replacement.\n\nSchedule now at just ₹199!\n\nCall: {{companyPhone}}\nWhatsApp: {{companyPhone}}\n\n— Aqua Green Agencies 💧")
                .placeholders("customerName,productName,companyPhone")
                .active(true).version(1).createdBy("System").build(),

            // Email Templates
            DocumentTemplate.builder()
                .name("Welcome Email").templateType("EMAIL").category("COMMUNICATION")
                .subject("Welcome to Aqua Green Agencies — Your Pure Water Partner!")
                .messageContent("Dear {{customerName}},\n\nThank you for choosing Aqua Green Agencies!\n\nYour RO water purifier ({{productName}}) has been successfully installed on {{installationDate}}.\n\nProduct Details:\n- Model: {{model}}\n- Serial No: {{serialNumber}}\n- Next Service Date: {{nextServiceDate}}\n- Warranty Valid Until: {{amcExpiryDate}}\n\nFor service or support, please contact us:\nPhone: {{companyPhone}}\nEmail: {{companyEmail}}\nWebsite: {{companyWebsite}}\n\nWarm Regards,\nAqua Green Agencies\nCoimbatore — 641033")
                .placeholders("customerName,productName,installationDate,model,serialNumber,nextServiceDate,amcExpiryDate,companyPhone,companyEmail,companyWebsite")
                .isDefault(true).active(true).version(1).createdBy("System").build(),

            DocumentTemplate.builder()
                .name("Invoice Email").templateType("EMAIL").category("COMMUNICATION")
                .subject("Invoice {{invoiceNumber}} — Aqua Green Agencies")
                .messageContent("Dear {{customerName}},\n\nPlease find your invoice attached.\n\nInvoice No: {{invoiceNumber}}\nDate: {{date}}\nAmount: ₹{{grandTotal}}\nPayment Mode: {{paymentMode}}\n\nThank you for your payment!\n\nAqua Green Agencies | {{companyPhone}}")
                .placeholders("customerName,invoiceNumber,date,grandTotal,paymentMode,companyPhone")
                .active(true).version(1).createdBy("System").build(),

            // Quotation Template (Document)
            DocumentTemplate.builder()
                .name("Standard Quotation").templateType("QUOTATION").category("DOCUMENT")
                .htmlContent("<div style='font-family:Calibri,sans-serif;'><h2 style='color:#009B00;text-align:center;'>AQUA GREEN AGENCIES</h2><p style='text-align:center;color:#666;font-size:12px;'>Near ESI Hospital, Neelikonampalayam, Coimbatore — 641033 | Ph: {{companyPhone}}</p><hr style='border-color:#009B00;'/><h3 style='text-align:center;'>QUOTATION</h3><table style='width:100%;'><tr><td><strong>Quotation No:</strong> {{quotationNumber}}</td><td><strong>Date:</strong> {{date}}</td></tr><tr><td><strong>Customer:</strong> {{customerName}}</td><td><strong>Mobile:</strong> {{mobile}}</td></tr></table><br/><p>Dear {{customerName}},</p><p>Thank you for your enquiry. We are pleased to provide the following quotation:</p><table style='width:100%;border-collapse:collapse;'><tr style='background:#009B00;color:white;'><th style='padding:8px;'>Description</th><th style='padding:8px;'>Qty</th><th style='padding:8px;'>Rate</th><th style='padding:8px;'>Total</th></tr><tr><td style='padding:8px;border:1px solid #ddd;'>{{productName}}</td><td style='padding:8px;border:1px solid #ddd;text-align:center;'>1</td><td style='padding:8px;border:1px solid #ddd;text-align:right;'>₹{{subtotal}}</td><td style='padding:8px;border:1px solid #ddd;text-align:right;'>₹{{subtotal}}</td></tr></table><br/><div style='text-align:right;'><p><strong>Subtotal:</strong> ₹{{subtotal}}</p><p><strong>GST (18%):</strong> ₹{{gst}}</p><p style='font-size:16px;color:#009B00;'><strong>TOTAL: ₹{{grandTotal}}</strong></p></div><p style='font-size:11px;color:#666;'>This quotation is valid for 30 days. GST applicable as per government norms.</p><p style='text-align:right;margin-top:40px;'>Authorised Signatory<br/><strong>Aqua Green Agencies</strong></p></div>")
                .placeholders("quotationNumber,date,customerName,mobile,productName,subtotal,gst,grandTotal,companyPhone")
                .headerConfig("{\"showLogo\":true,\"companyName\":true,\"address\":true,\"phone\":true}")
                .footerConfig("{\"left\":\"GST: 33XXXXX1234Z1Z5\",\"center\":\"Terms: Valid 30 days\",\"right\":\"Page {{pageNumber}}\"}")
                .watermarkConfig("{\"text\":\"QUOTATION\",\"opacity\":0.08,\"rotation\":45,\"position\":\"center\"}")
                .pageConfig("{\"size\":\"A4\",\"orientation\":\"PORTRAIT\",\"marginTop\":72,\"marginBottom\":72}")
                .isDefault(true).active(true).version(1).createdBy("System").build(),

            // Invoice Template
            DocumentTemplate.builder()
                .name("Tax Invoice").templateType("INVOICE").category("DOCUMENT")
                .htmlContent("<div style='font-family:Calibri,sans-serif;'><h2 style='color:#009B00;text-align:center;'>AQUA GREEN AGENCIES</h2><p style='text-align:center;color:#666;font-size:12px;'>Near ESI Hospital, Neelikonampalayam, Coimbatore — 641033 | GST: 33XXXXX1234Z1Z5</p><hr style='border-color:#009B00;'/><h3 style='text-align:center;'>TAX INVOICE</h3><table style='width:100%;'><tr><td><strong>Invoice No:</strong> {{invoiceNumber}}</td><td><strong>Date:</strong> {{date}}</td></tr><tr><td><strong>Customer:</strong> {{customerName}}</td><td><strong>Mobile:</strong> {{mobile}}</td></tr><tr><td><strong>Address:</strong> {{address}}</td><td><strong>Payment:</strong> {{paymentMode}}</td></tr></table><br/><table style='width:100%;border-collapse:collapse;'><tr style='background:#009B00;color:white;'><th style='padding:8px;'>Item</th><th>Qty</th><th>Rate</th><th>Amount</th></tr><tr><td style='padding:8px;border:1px solid #ddd;'>{{productName}}</td><td style='padding:8px;border:1px solid #ddd;text-align:center;'>1</td><td style='padding:8px;border:1px solid #ddd;text-align:right;'>₹{{subtotal}}</td><td style='padding:8px;border:1px solid #ddd;text-align:right;'>₹{{subtotal}}</td></tr></table><div style='text-align:right;margin-top:12px;'><p><strong>Subtotal:</strong> ₹{{subtotal}}</p><p><strong>CGST (9%):</strong> ₹{{gst}}</p><p><strong>SGST (9%):</strong> ₹{{gst}}</p><p style='font-size:16px;color:#009B00;'><strong>TOTAL: ₹{{grandTotal}}</strong></p></div><p style='font-size:10px;color:#666;'>E.&amp;O.E. Goods once sold will not be taken back.</p><p style='text-align:right;margin-top:40px;'>Authorised Signatory<br/><strong>Aqua Green Agencies</strong></p></div>")
                .placeholders("invoiceNumber,date,customerName,mobile,address,paymentMode,productName,subtotal,gst,grandTotal")
                .watermarkConfig("{\"text\":\"PAID\",\"opacity\":0.06,\"rotation\":45}")
                .isDefault(true).active(true).version(1).createdBy("System").build(),

            // Service Report Template
            DocumentTemplate.builder()
                .name("Service Completion Report").templateType("SERVICE_REPORT").category("DOCUMENT")
                .htmlContent("<div style='font-family:Calibri,sans-serif;'><h2 style='color:#009B00;text-align:center;'>SERVICE COMPLETION REPORT</h2><p style='text-align:center;color:#666;'>Aqua Green Agencies | {{companyPhone}}</p><hr/><table style='width:100%;'><tr><td><strong>Customer:</strong> {{customerName}}</td><td><strong>Date:</strong> {{date}}</td></tr><tr><td><strong>Mobile:</strong> {{mobile}}</td><td><strong>Technician:</strong> {{technician}}</td></tr><tr><td><strong>Product:</strong> {{productName}}</td><td><strong>Model:</strong> {{model}}</td></tr><tr><td><strong>Serial No:</strong> {{serialNumber}}</td><td><strong>Next Service:</strong> {{nextServiceDate}}</td></tr></table><hr/><h4>Service Done:</h4><ul><li>Filter replacement (Sediment, Carbon, Post-Carbon)</li><li>Tank cleaning and sanitisation</li><li>TDS level check and adjustment</li><li>UV lamp check</li><li>Water flow pressure test</li></ul><p><strong>Remarks:</strong> System working perfectly. Output TDS within safe limits.</p><p style='text-align:right;margin-top:40px;'>Customer Signature<br/><br/>_______________</p></div>")
                .placeholders("customerName,date,mobile,technician,productName,model,serialNumber,nextServiceDate,companyPhone")
                .isDefault(true).active(true).version(1).createdBy("System").build(),

            // Warranty Certificate
            DocumentTemplate.builder()
                .name("Warranty Certificate").templateType("WARRANTY_CERT").category("DOCUMENT")
                .htmlContent("<div style='font-family:Calibri,sans-serif;border:4px solid #009B00;padding:30px;text-align:center;'><h1 style='color:#009B00;'>WARRANTY CERTIFICATE</h1><p style='color:#666;'>AQUA GREEN AGENCIES, COIMBATORE</p><hr style='border-color:#009B00;'/><p style='font-size:14px;margin:20px 0;'>This is to certify that the following product is covered under warranty:</p><table style='margin:0 auto;text-align:left;'><tr><td style='padding:6px 20px;'><strong>Customer:</strong></td><td>{{customerName}}</td></tr><tr><td style='padding:6px 20px;'><strong>Product:</strong></td><td>{{productName}}</td></tr><tr><td style='padding:6px 20px;'><strong>Model:</strong></td><td>{{model}}</td></tr><tr><td style='padding:6px 20px;'><strong>Serial No:</strong></td><td>{{serialNumber}}</td></tr><tr><td style='padding:6px 20px;'><strong>Purchase Date:</strong></td><td>{{installationDate}}</td></tr><tr><td style='padding:6px 20px;'><strong>Warranty Expiry:</strong></td><td>{{amcExpiryDate}}</td></tr></table><p style='margin:24px 0;font-size:13px;color:#555;'>This warranty covers manufacturing defects. Physical damage is excluded.</p><div style='text-align:right;margin-top:40px;'><p>Authorised Signatory<br/><strong>Aqua Green Agencies</strong><br/>{{companyPhone}}</p></div></div>")
                .placeholders("customerName,productName,model,serialNumber,installationDate,amcExpiryDate,companyPhone")
                .isDefault(true).active(true).version(1).createdBy("System").build(),

            // Customer Welcome Letter
            DocumentTemplate.builder()
                .name("Customer Welcome Letter").templateType("WELCOME_LETTER").category("DOCUMENT")
                .htmlContent("<div style='font-family:Calibri,sans-serif;'><h2 style='color:#009B00;'>AQUA GREEN AGENCIES</h2><p style='color:#666;font-size:12px;'>Near ESI Hospital, Neelikonampalayam, Coimbatore — 641033</p><hr/><p>{{date}}</p><p><strong>{{customerName}}</strong><br/>{{address}}</p><br/><p>Dear {{customerName}},</p><p>We are delighted to welcome you to the Aqua Green Agencies family! 💧</p><p>Your {{productName}} has been successfully installed. Here are your details:</p><ul><li>Installation Date: {{installationDate}}</li><li>Next Service Date: {{nextServiceDate}}</li><li>Your Technician: {{technician}}</li></ul><p>For any assistance, our team is available 7 days a week. Call us at {{companyPhone}} or WhatsApp us anytime.</p><p>Thank you for choosing pure water with Aqua Green Agencies!</p><br/><p>Warm Regards,<br/><strong>Arun Kumar</strong><br/>Aqua Green Agencies<br/>{{companyPhone}}</p></div>")
                .placeholders("date,customerName,address,productName,installationDate,nextServiceDate,technician,companyPhone")
                .isDefault(true).active(true).version(1).createdBy("System").build()
        ));
        log.info("Seeded document templates");
    }

    void seedFilterPresets() {
        if (filterPresetRepo.count() > 0) return;
        filterPresetRepo.saveAll(List.of(
            FilterPreset.builder().name("Service Due This Week").description("Customers with service due within 7 days").filterConfig("{\"serviceDueDays\":7,\"direction\":\"BEFORE\"}").icon("🔧").color("#e8a020").active(true).usageCount(0).createdBy("System").build(),
            FilterPreset.builder().name("Annual Service Expiring in 30 Days").description("Customers whose Annual Service expires within 30 days").filterConfig("{\"amcExpiryDays\":30,\"direction\":\"BEFORE\"}").icon("📅").color("#185FA5").active(true).usageCount(0).createdBy("System").build(),
            FilterPreset.builder().name("Payment Pending").description("Customers with pending payments").filterConfig("{\"paymentStatus\":\"PENDING\"}").icon("💳").color("#A32D2D").active(true).usageCount(0).createdBy("System").build(),
            FilterPreset.builder().name("No Service 6 Months").description("Customers without service in last 6 months").filterConfig("{\"lastServiceMonths\":6,\"direction\":\"AFTER\"}").icon("⚠️").color("#854F0B").active(true).usageCount(0).createdBy("System").build(),
            FilterPreset.builder().name("Commercial RO Customers").description("All commercial RO customers").filterConfig("{\"customerType\":\"COMMERCIAL\"}").icon("🏢").color("#009B00").active(true).usageCount(0).createdBy("System").build(),
            FilterPreset.builder().name("Coimbatore Customers").description("All customers from Coimbatore").filterConfig("{\"city\":\"Coimbatore\"}").icon("📍").color("#007A00").active(true).usageCount(0).createdBy("System").build()
        ));
        log.info("Seeded filter presets");
    }

    void seedAutomationRules() {
        if (automationRepo.count() > 0) return;
        automationRepo.saveAll(List.of(
            AutomationRule.builder().name("Service Due Reminder").description("Send WhatsApp 3 days before service is due").triggerType("SERVICE_DUE").dayOffset(-3).scheduleType("DAILY").scheduleTime("09:00").actionChannel("WHATSAPP").filterConfig("{\"active\":true}").active(true).totalSent(0).createdBy("System").build(),
            AutomationRule.builder().name("Annual Service Expiry Alert").description("Send SMS 7 days before Annual Service expires").triggerType("Annual Service_EXPIRING").dayOffset(-7).scheduleType("DAILY").scheduleTime("10:00").actionChannel("SMS").filterConfig("{\"active\":true}").active(true).totalSent(0).createdBy("System").build(),
            AutomationRule.builder().name("Welcome on Installation").description("Send welcome WhatsApp immediately after installation").triggerType("INSTALLATION_COMPLETE").dayOffset(0).scheduleType("IMMEDIATELY").actionChannel("WHATSAPP").filterConfig("{\"active\":true}").active(true).totalSent(0).createdBy("System").build(),
            AutomationRule.builder().name("Payment Reminder").description("Send SMS 5 days after payment is pending").triggerType("PAYMENT_PENDING").dayOffset(5).scheduleType("DAILY").scheduleTime("11:00").actionChannel("SMS").filterConfig("{\"active\":true}").active(false).totalSent(0).createdBy("System").build()
        ));
        log.info("Seeded automation rules");
    }
}
