package com.aquagreen.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@aquagreen.com}")
    private String fromEmail;

    @Value("${app.url:http://localhost:3000}")
    private String appUrl;

    // ── Password Reset Email ──────────────────────────────────────
    public void sendPasswordResetEmail(String toEmail, String name, String resetToken) {
        try {
            var msg = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom("Aqua Green Agencies <" + fromEmail + ">");
            helper.setTo(toEmail);
            helper.setSubject("🔐 Reset Your Password — Aqua Green Agencies");

            String resetUrl = appUrl + "/reset-password?token=" + resetToken;
            String displayName = (name != null && !name.isBlank()) ? name : toEmail;

            String html = """
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8"></head>
                <body style="margin:0;padding:0;background:#f4f4f4;font-family:Arial,sans-serif;">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f4f4;padding:40px 20px;">
                    <tr><td align="center">
                      <table width="600" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.08);">

                        <!-- Header -->
                        <tr>
                          <td style="background:linear-gradient(135deg,#0a4f3c,#1a7a5c);padding:32px;text-align:center;">
                            <h1 style="color:#ffffff;margin:0;font-size:22px;font-weight:800;">Aqua Green Agencies</h1>
                            <p style="color:#7fe8c5;margin:6px 0 0;font-size:13px;">RO Water Purifier · Coimbatore</p>
                          </td>
                        </tr>

                        <!-- Body -->
                        <tr>
                          <td style="padding:40px 36px;">
                            <h2 style="color:#111827;font-size:20px;margin:0 0 8px;">Password Reset Request</h2>
                            <p style="color:#6b7280;font-size:14px;margin:0 0 20px;">We received a request to reset your password.</p>
                            <p style="color:#374151;font-size:15px;margin:0 0 28px;">Hello <strong>%s</strong>,</p>

                            <p style="color:#374151;font-size:14px;line-height:1.6;margin:0 0 28px;">
                              Click the button below to set a new password for your account.
                              This link is valid for <strong>1 hour</strong>.
                            </p>

                            <!-- Button -->
                            <table cellpadding="0" cellspacing="0" width="100%%">
                              <tr>
                                <td align="center" style="padding:8px 0 32px;">
                                  <a href="%s"
                                     style="background:#0a4f3c;color:#ffffff;padding:15px 40px;border-radius:10px;text-decoration:none;font-weight:700;font-size:16px;display:inline-block;letter-spacing:0.3px;">
                                    🔐 Reset My Password
                                  </a>
                                </td>
                              </tr>
                            </table>

                            <!-- Copy link -->
                            <div style="background:#f9fafb;border:1px solid #e5e7eb;border-radius:8px;padding:14px;margin-bottom:24px;">
                              <p style="color:#6b7280;font-size:12px;margin:0 0 6px;">Or copy this link into your browser:</p>
                              <p style="color:#0a4f3c;font-size:11px;word-break:break-all;margin:0;font-family:monospace;">%s</p>
                            </div>

                            <p style="color:#9ca3af;font-size:12px;line-height:1.6;margin:0;">
                              If you didn't request a password reset, you can safely ignore this email.
                              Your password will remain unchanged.
                            </p>
                          </td>
                        </tr>

                        <!-- Footer -->
                        <tr>
                          <td style="background:#f9fafb;padding:20px 36px;border-top:1px solid #e5e7eb;text-align:center;">
                            <p style="color:#9ca3af;font-size:11px;margin:0;">
                              Aqua Green Agencies · Near ESI Hospital, Neelikonampalayam, Coimbatore — 641033
                            </p>
                            <p style="color:#9ca3af;font-size:11px;margin:4px 0 0;">
                              📞 09054617008 · This is an automated email, please do not reply.
                            </p>
                          </td>
                        </tr>

                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(displayName, resetUrl, resetUrl);

            helper.setText(html, true);
            mailSender.send(msg);
            log.info("✅ Password reset email sent to: {}", toEmail);

        } catch (Exception e) {
            log.error("❌ Failed to send password reset email to {}: {}", toEmail, e.getMessage());
            // Don't throw — app works even if email fails
            // The token is still saved in DB, admin can share it manually
        }
    }

    // ── Test email (call from controller to verify SMTP) ──────────
    public boolean sendTestEmail(String toEmail) {
        try {
            var msg = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom("Aqua Green Agencies <" + fromEmail + ">");
            helper.setTo(toEmail);
            helper.setSubject("✅ Email Test — Aqua Green Agencies CRM");
            helper.setText("""
                <div style="font-family:Arial;padding:24px;max-width:500px;">
                  <h2 style="color:#0a4f3c;">✅ Email is Working!</h2>
                  <p>Your SMTP configuration is correct.</p>
                  <p>Password reset emails will be delivered successfully.</p>
                  <p style="color:#6b7280;font-size:12px;">— Aqua Green Agencies CRM</p>
                </div>
                """, true);
            mailSender.send(msg);
            log.info("✅ Test email sent to: {}", toEmail);
            return true;
        } catch (Exception e) {
            log.error("❌ Test email failed: {}", e.getMessage());
            return false;
        }
    }

    // ── Welcome email ─────────────────────────────────────────────
    public void sendWelcomeEmail(String toEmail, String name) {
        try {
            var msg = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom("Aqua Green Agencies <" + fromEmail + ">");
            helper.setTo(toEmail);
            helper.setSubject("Welcome to Aqua Green Agencies!");
            helper.setText("""
                <div style="font-family:Arial;max-width:560px;margin:0 auto;">
                  <div style="background:#0a4f3c;padding:24px;text-align:center;">
                    <h1 style="color:#fff;margin:0;">Welcome to Aqua Green Agencies!</h1>
                  </div>
                  <div style="padding:28px;">
                    <p>Hello <strong>%s</strong>! 💧</p>
                    <p>Your staff account has been created successfully. You can now log in to the admin portal.</p>
                    <p>For support, call <strong>09054617008</strong>.</p>
                    <p style="color:#9ca3af;font-size:12px;">Near ESI Hospital, Neelikonampalayam, Coimbatore — 641033</p>
                  </div>
                </div>
                """.formatted(name != null ? name : toEmail), true);
            mailSender.send(msg);
        } catch (Exception e) {
            log.warn("Welcome email failed for {}: {}", toEmail, e.getMessage());
        }
    }
}
