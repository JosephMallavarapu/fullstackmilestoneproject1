package com.trackwise.service.impl;

import com.trackwise.entity.*;
import com.trackwise.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.util.List;

// ═══════════════════════════════════════════════════════════
//  NotificationService
//  - Persists notification records to DB
//  - Sends Email async via JavaMailSender (SendGrid SMTP)
//  - SMS send stub (wire Twilio SDK to replace log line)
// ═══════════════════════════════════════════════════════════
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class NotificationService {

    private final NotificationRepository           notificationRepo;
    private final NotificationPreferenceRepository prefRepo;
    private final UserRepository                   userRepo;
    private final JavaMailSender                   mailSender;

    @Value("${trackwise.mail.from:noreply@trackwise.io}") private String fromEmail;

    /** Persist + dispatch notification for a given user and event. */
    public void notify(User user, Notification.NotificationType type,
                       String title, String message, Long relatedId) {

        // IN-APP (always stored)
        Notification inApp = buildNotification(user, type, title, message,
            relatedId, Notification.NotificationChannel.IN_APP);
        notificationRepo.save(inApp);

        // Check user preferences and dispatch per channel
        NotificationPreference pref = prefRepo
            .findByUser_IdAndEventType(user.getId(), type.name())
            .orElse(defaultPreference(user, type));

        if (Boolean.TRUE.equals(pref.getEmail())) {
            sendEmail(user, title, message);
            Notification emailRecord = buildNotification(user, type, title, message,
                relatedId, Notification.NotificationChannel.EMAIL);
            emailRecord.setIsSent(true);
            emailRecord.setSentAt(LocalDateTime.now());
            notificationRepo.save(emailRecord);
        }
        if (Boolean.TRUE.equals(pref.getSms())) {
            sendSms(user.getEmail(), title + ": " + message); // email acts as ID for demo
            Notification smsRecord = buildNotification(user, type, title, message,
                relatedId, Notification.NotificationChannel.SMS);
            smsRecord.setIsSent(true);
            smsRecord.setSentAt(LocalDateTime.now());
            notificationRepo.save(smsRecord);
        }
    }

    /** Convenience: notify all managers about a new pending expense. */
    public void notifyManagersForApproval(Expense expense) {
        User manager = expense.getSubmittedBy().getManager();
        if (manager != null) {
            notify(manager,
                Notification.NotificationType.APPROVAL_REQUIRED,
                "Approval Required — " + expense.getReferenceCode(),
                String.format("Expense '%s' ($%.2f) submitted by %s %s requires your approval.",
                    expense.getTitle(), expense.getAmountUsd(),
                    expense.getSubmittedBy().getFirstName(),
                    expense.getSubmittedBy().getLastName()),
                expense.getId()
            );
        }
    }

    public void notifyPolicyViolation(User user, Expense expense, List<PolicyViolation> violations) {
        String detail = violations.isEmpty() ? "" :
            violations.get(0).getDetail();
        notify(user,
            Notification.NotificationType.POLICY_VIOLATION,
            "Policy Violation — " + expense.getReferenceCode(),
            "Rule triggered: " + detail,
            expense.getId()
        );
    }

    public void notifyExpenseApproved(User submitter, Expense expense) {
        notify(submitter,
            Notification.NotificationType.EXPENSE_APPROVED,
            "Expense Approved — " + expense.getReferenceCode(),
            String.format("Your expense '%s' ($%.2f) has been approved.", expense.getTitle(), expense.getAmountUsd()),
            expense.getId()
        );
    }

    public void notifyExpenseRejected(User submitter, Expense expense, String reason) {
        notify(submitter,
            Notification.NotificationType.EXPENSE_REJECTED,
            "Expense Rejected — " + expense.getReferenceCode(),
            String.format("Your expense '%s' was rejected. Reason: %s", expense.getTitle(), reason),
            expense.getId()
        );
    }

    public void notifyErpSyncDone(User admin, String message) {
        notify(admin,
            Notification.NotificationType.ERP_SYNC_DONE,
            "ERP Sync Complete",
            message,
            null
        );
    }

    /** Mark a notification as read. */
    public void markRead(Long notificationId) {
        notificationRepo.findById(notificationId).ifPresent(n -> {
            n.setIsRead(true);
            notificationRepo.save(n);
        });
    }

    public List<Notification> getUnread(Long userId) {
        return notificationRepo.findByUser_IdAndIsReadFalseOrderByCreatedAtDesc(userId);
    }

    public long countUnread(Long userId) {
        return notificationRepo.countByUser_IdAndIsReadFalse(userId);
    }

    // ───── Internal helpers ─────

    @Async
    protected void sendEmail(User user, String subject, String body) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, false, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(user.getEmail());
            helper.setSubject("[TrackWise] " + subject);
            helper.setText(buildHtmlEmail(user.getFirstName(), subject, body), true);
            mailSender.send(msg);
            log.info("Email sent to {}: {}", user.getEmail(), subject);
        } catch (Exception e) {
            log.error("Email send failed to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    @Async
    protected void sendSms(String recipient, String message) {
        // Inject Twilio RestClient here when credentials are available
        log.info("SMS stub — to: {} | msg: {}", recipient, message);
        // Example Twilio call:
        // Message.creator(new PhoneNumber(userPhone), new PhoneNumber(twilioFrom), message).create();
    }

    private Notification buildNotification(User user, Notification.NotificationType type,
                                           String title, String message, Long relatedId,
                                           Notification.NotificationChannel channel) {
        return Notification.builder()
            .user(user).type(type).title(title).message(message)
            .relatedId(relatedId).channel(channel)
            .isRead(false).isSent(false).createdAt(LocalDateTime.now())
            .build();
    }

    private NotificationPreference defaultPreference(User user, Notification.NotificationType type) {
        return NotificationPreference.builder()
            .user(user).eventType(type.name())
            .email(true).sms(false).push(false).inApp(true)
            .build();
    }

    private String buildHtmlEmail(String name, String subject, String body) {
        return """
            <!DOCTYPE html><html><body style="font-family:sans-serif;background:#f4f5f7;padding:30px">
            <div style="max-width:500px;margin:auto;background:white;border-radius:8px;padding:28px;border:1px solid #e2e5ea">
              <div style="font-size:18px;font-weight:700;margin-bottom:4px;color:#0d1117">TrackWise</div>
              <hr style="border:none;border-top:1px solid #e2e5ea;margin:14px 0"/>
              <p style="color:#424a57">Hi %s,</p>
              <p style="color:#0d1117;font-weight:600">%s</p>
              <p style="color:#6b7589">%s</p>
              <div style="margin-top:20px;font-size:12px;color:#9ba3b5">
                This is an automated message from TrackWise. Do not reply to this email.
              </div>
            </div>
            </body></html>
            """.formatted(name, subject, body);
    }
}
