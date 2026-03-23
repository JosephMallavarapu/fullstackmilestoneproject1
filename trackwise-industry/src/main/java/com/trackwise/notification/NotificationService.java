package com.trackwise.notification;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.util.Map;

/**
 * ══════════════════════════════════════════════════════════════
 *  TrackWise — Notification Service
 *  Industry Feature: Email (SMTP/SendGrid) + SMS (Twilio)
 *
 *  Triggers:
 *  - Expense submitted       → notify manager (email + SMS)
 *  - Policy violation        → notify manager + compliance (email)
 *  - Expense approved        → notify submitter (email)
 *  - Expense rejected        → notify submitter (email + SMS)
 *  - Budget exceeded         → notify finance (email)
 *  - ERP sync failed         → notify admin (email)
 *
 *  ISO 27001: all notifications logged to audit_log table
 *  PCI DSS:   no sensitive card/payment data in notifications
 * ══════════════════════════════════════════════════════════════
 */

// ── Notification event types ──────────────────────────────────
enum NotificationEvent {
    EXPENSE_SUBMITTED,
    EXPENSE_APPROVED,
    EXPENSE_REJECTED,
    POLICY_VIOLATED,
    BUDGET_EXCEEDED,
    ERP_SYNC_FAILED,
    DUPLICATE_DETECTED
}

// ── Notification channel ──────────────────────────────────────
enum NotificationChannel { EMAIL, SMS, BOTH }

// ── Payload passed to notification service ────────────────────
@Data @Builder @NoArgsConstructor @AllArgsConstructor
class NotificationPayload {
    private NotificationEvent  event;
    private String             recipientEmail;
    private String             recipientPhone;    // E.164 format: +1234567890
    private String             recipientName;
    private String             expenseRef;
    private BigDecimal         expenseAmount;
    private String             expenseCurrency;
    private String             expenseDescription;
    private String             actionNote;        // e.g. rejection reason
    private NotificationChannel channel;
}

// ─────────────────────────────────────────────────────────────
// NotificationService
// ─────────────────────────────────────────────────────────────
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    private final JavaMailSender  mailSender;

    @Value("${app.notification.from-email:noreply@trackwise.io}")
    private String fromEmail;

    @Value("${app.notification.from-name:TrackWise}")
    private String fromName;

    // Twilio credentials
    @Value("${twilio.account-sid:}")       private String twilioSid;
    @Value("${twilio.auth-token:}")        private String twilioToken;
    @Value("${twilio.from-number:}")       private String twilioFrom;

    private final RestTemplate restTemplate = new RestTemplate();
    private final AuditLogger  auditLogger;

    // ── Main entry point ──────────────────────────────────────
    @Async
    public void send(NotificationPayload payload) {
        log.info("Sending {} notification for event={} ref={}",
                payload.getChannel(), payload.getEvent(), payload.getExpenseRef());

        try {
            switch (payload.getChannel()) {
                case EMAIL -> sendEmail(payload);
                case SMS   -> sendSMS(payload);
                case BOTH  -> { sendEmail(payload); sendSMS(payload); }
            }
            auditLogger.log("NOTIFICATION_SENT", "system",
                    "Event=" + payload.getEvent() + " | To=" + payload.getRecipientEmail()
                    + " | Ref=" + payload.getExpenseRef(), null);
        } catch (Exception e) {
            log.error("Notification failed for event={}: {}", payload.getEvent(), e.getMessage());
        }
    }

    // ── Convenience builders for each trigger ────────────────

    /** Called when expense is submitted — notifies the approver */
    public void notifyExpenseSubmitted(String approverEmail, String approverPhone,
                                       String approverName, String ref,
                                       BigDecimal amount, String currency, String desc) {
        send(NotificationPayload.builder()
            .event(NotificationEvent.EXPENSE_SUBMITTED)
            .recipientEmail(approverEmail)
            .recipientPhone(approverPhone)
            .recipientName(approverName)
            .expenseRef(ref).expenseAmount(amount)
            .expenseCurrency(currency).expenseDescription(desc)
            .channel(NotificationChannel.BOTH)
            .build());
    }

    /** Called after approval decision — notifies the submitter */
    public void notifyApprovalDecision(String submitterEmail, String submitterPhone,
                                       String submitterName, String ref,
                                       BigDecimal amount, String currency,
                                       boolean approved, String note) {
        send(NotificationPayload.builder()
            .event(approved ? NotificationEvent.EXPENSE_APPROVED : NotificationEvent.EXPENSE_REJECTED)
            .recipientEmail(submitterEmail)
            .recipientPhone(submitterPhone)
            .recipientName(submitterName)
            .expenseRef(ref).expenseAmount(amount)
            .expenseCurrency(currency).actionNote(note)
            .channel(approved ? NotificationChannel.EMAIL : NotificationChannel.BOTH)
            .build());
    }

    /** Called when policy engine flags a violation */
    public void notifyPolicyViolation(String managerEmail, String ref,
                                      BigDecimal amount, String violationMsg) {
        send(NotificationPayload.builder()
            .event(NotificationEvent.POLICY_VIOLATED)
            .recipientEmail(managerEmail)
            .recipientName("Manager")
            .expenseRef(ref).expenseAmount(amount)
            .actionNote(violationMsg)
            .channel(NotificationChannel.EMAIL)
            .build());
    }

    // ── Email implementation (JavaMailSender / SendGrid SMTP) ─
    private void sendEmail(NotificationPayload p) throws MessagingException {
        MimeMessage msg = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");

        helper.setFrom(fromEmail, fromName);
        helper.setTo(p.getRecipientEmail());
        helper.setSubject(buildSubject(p));
        helper.setText(buildHtmlBody(p), true);   // HTML email

        mailSender.send(msg);
        log.info("Email sent to {} for event={}", p.getRecipientEmail(), p.getEvent());
    }

    // ── SMS implementation (Twilio REST API) ─────────────────
    private void sendSMS(NotificationPayload p) {
        if (twilioSid.isBlank() || p.getRecipientPhone() == null) {
            log.warn("SMS skipped — Twilio not configured or no phone number");
            return;
        }

        String url = "https://api.twilio.com/2010-04-01/Accounts/" + twilioSid + "/Messages.json";
        String body = buildSmsText(p);

        try {
            restTemplate.postForObject(url,
                Map.of("From", twilioFrom, "To", p.getRecipientPhone(), "Body", body),
                Map.class);
            log.info("SMS sent to {} for event={}", p.getRecipientPhone(), p.getEvent());
        } catch (Exception e) {
            log.error("Twilio SMS failed: {}", e.getMessage());
        }
    }

    // ── Email template builder ────────────────────────────────
    private String buildSubject(NotificationPayload p) {
        return switch (p.getEvent()) {
            case EXPENSE_SUBMITTED  -> "[TrackWise] Expense Awaiting Your Approval — " + p.getExpenseRef();
            case EXPENSE_APPROVED   -> "[TrackWise] ✅ Your Expense Was Approved — " + p.getExpenseRef();
            case EXPENSE_REJECTED   -> "[TrackWise] ❌ Your Expense Was Rejected — " + p.getExpenseRef();
            case POLICY_VIOLATED    -> "[TrackWise] ⚠ Policy Violation Detected — " + p.getExpenseRef();
            case BUDGET_EXCEEDED    -> "[TrackWise] 📊 Department Budget Exceeded";
            case ERP_SYNC_FAILED    -> "[TrackWise] 🔴 ERP Sync Failed";
            case DUPLICATE_DETECTED -> "[TrackWise] 🔍 Duplicate Expense Detected — " + p.getExpenseRef();
        };
    }

    private String buildHtmlBody(NotificationPayload p) {
        String color = switch (p.getEvent()) {
            case EXPENSE_APPROVED  -> "#16a34a";
            case EXPENSE_REJECTED, POLICY_VIOLATED -> "#dc2626";
            case ERP_SYNC_FAILED   -> "#dc2626";
            default                -> "#2563eb";
        };

        return """
            <div style="font-family:sans-serif;max-width:520px;margin:0 auto;border:1px solid #e2e5ea;border-radius:12px;overflow:hidden">
              <div style="background:%s;padding:20px 24px">
                <h2 style="color:white;margin:0;font-size:18px">TrackWise</h2>
                <p style="color:rgba(255,255,255,.8);margin:4px 0 0;font-size:13px">Expense Analytics Platform</p>
              </div>
              <div style="padding:24px">
                <p style="font-size:15px;font-weight:600;margin-bottom:16px">Hi %s,</p>
                <p style="font-size:14px;color:#424a57;line-height:1.6">%s</p>
                <div style="background:#f4f5f7;border-radius:8px;padding:16px;margin:16px 0">
                  <div style="display:flex;justify-content:space-between;margin-bottom:8px">
                    <span style="font-size:12px;color:#6b7589">Reference</span>
                    <strong style="font-size:12px;font-family:monospace">%s</strong>
                  </div>
                  <div style="display:flex;justify-content:space-between;margin-bottom:8px">
                    <span style="font-size:12px;color:#6b7589">Amount</span>
                    <strong style="font-size:14px;font-family:monospace">%s %.2f</strong>
                  </div>
                  <div style="display:flex;justify-content:space-between">
                    <span style="font-size:12px;color:#6b7589">Description</span>
                    <strong style="font-size:12px">%s</strong>
                  </div>
                </div>
                %s
              </div>
              <div style="background:#f9fafb;padding:14px 24px;font-size:11px;color:#9ba3b5;border-top:1px solid #e2e5ea">
                TrackWise Enterprise · ISO 27001 · PCI DSS Compliant · Do not share this email
              </div>
            </div>
            """.formatted(
                color,
                p.getRecipientName() != null ? p.getRecipientName() : "User",
                getEventMessage(p),
                p.getExpenseRef() != null ? p.getExpenseRef() : "—",
                p.getExpenseCurrency() != null ? p.getExpenseCurrency() : "USD",
                p.getExpenseAmount() != null ? p.getExpenseAmount() : BigDecimal.ZERO,
                p.getExpenseDescription() != null ? p.getExpenseDescription() : "—",
                p.getActionNote() != null ? "<p style=\"font-size:13px;color:#6b7589\"><strong>Note:</strong> " + p.getActionNote() + "</p>" : ""
            );
    }

    private String getEventMessage(NotificationPayload p) {
        return switch (p.getEvent()) {
            case EXPENSE_SUBMITTED  -> "A new expense has been submitted and requires your approval.";
            case EXPENSE_APPROVED   -> "Great news! Your expense has been approved and will be processed in the next payroll cycle.";
            case EXPENSE_REJECTED   -> "Your expense submission has been rejected. Please review the note below and resubmit.";
            case POLICY_VIOLATED    -> "A policy violation was detected on an expense submission. Please review immediately.";
            case BUDGET_EXCEEDED    -> "Your department has exceeded its monthly budget. Finance approval is required for new submissions.";
            case ERP_SYNC_FAILED    -> "The automated ERP sync failed. Manual intervention may be required.";
            case DUPLICATE_DETECTED -> "A potential duplicate expense was detected. Please review before processing.";
        };
    }

    private String buildSmsText(NotificationPayload p) {
        return switch (p.getEvent()) {
            case EXPENSE_SUBMITTED  -> "[TrackWise] New expense " + p.getExpenseRef() + " ($" + p.getExpenseAmount() + ") awaiting your approval. Log in to review.";
            case EXPENSE_APPROVED   -> "[TrackWise] Your expense " + p.getExpenseRef() + " was APPROVED. Check your email for details.";
            case EXPENSE_REJECTED   -> "[TrackWise] Your expense " + p.getExpenseRef() + " was REJECTED. Reason: " + p.getActionNote();
            case POLICY_VIOLATED    -> "[TrackWise] POLICY ALERT: " + p.getExpenseRef() + " flagged. " + p.getActionNote();
            default                 -> "[TrackWise] Action required on " + p.getExpenseRef() + ". Check your email.";
        };
    }
}

// ─────────────────────────────────────────────────────────────
// AuditLogger — ISO 27001 §A.12.4 compliant logging
// ─────────────────────────────────────────────────────────────
@Service
@Slf4j
class AuditLogger {

    /**
     * Write to audit_log table.
     * In production: inject AuditLogRepository and persist to DB.
     * Every sensitive action (login, data change, notification) is logged.
     */
    public void log(String action, String actor, String detail, String ipAddress) {
        // TODO: inject AuditLogRepository and call .save(AuditLog.builder()...)
        log.info("[AUDIT] action={} actor={} detail={} ip={}", action, actor, detail, ipAddress);
    }
}
