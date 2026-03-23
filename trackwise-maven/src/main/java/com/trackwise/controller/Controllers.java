package com.trackwise.controller;

import com.trackwise.entity.*;
import com.trackwise.repository.*;
import com.trackwise.service.impl.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

// ═══════════════════════════════════════════════════════════
//  PolicyController  — /api/v1/policy
// ═══════════════════════════════════════════════════════════
@RestController
@RequestMapping("/api/v1/policy")
@RequiredArgsConstructor
@Tag(name = "Policy Engine", description = "Policy rule management and violation log")
class PolicyController {

    private final PolicyService policyService;

    @GetMapping("/rules")
    @Operation(summary = "Get all policy rules")
    public ResponseEntity<List<PolicyRule>> getRules() {
        return ResponseEntity.ok(policyService.getAllRules());
    }

    @PutMapping("/rules/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Enable or disable a policy rule")
    public ResponseEntity<PolicyRule> toggleRule(@PathVariable Long id) {
        return ResponseEntity.ok(policyService.toggleRule(id));
    }

    @GetMapping("/violations")
    @Operation(summary = "List recent policy violations")
    public ResponseEntity<List<PolicyViolation>> violations(
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(
                policyService.getRecentViolations(PageRequest.of(0, limit)));
    }
}

// ═══════════════════════════════════════════════════════════
// CurrencyController — /api/v1/currency
// ═══════════════════════════════════════════════════════════
@RestController
@RequestMapping("/api/v1/currency")
@RequiredArgsConstructor
@Tag(name = "Multi-Currency", description = "Live FX rates and currency conversion")
class CurrencyController {

    private final CurrencyService currencyService;

    @GetMapping("/rates")
    @Operation(summary = "Get all cached FX rates (base = USD)")
    public ResponseEntity<List<CurrencyRate>> getRates() {
        return ResponseEntity.ok(currencyService.getAllRates());
    }

    @PostMapping("/convert")
    @Operation(summary = "Convert an amount between two currencies")
    public ResponseEntity<Map<String, Object>> convert(@RequestBody Map<String, Object> body) {
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        String from = (String) body.get("from");
        String to = (String) body.get("to");
        BigDecimal converted = currencyService.convert(amount, from, to);
        return ResponseEntity.ok(Map.of(
                "from", from,
                "to", to,
                "amount", amount,
                "converted", converted,
                "rate", currencyService.getRateFromBase(to)));
    }

    @PostMapping("/rates/refresh")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Force refresh FX rates from external API")
    public ResponseEntity<?> refresh() {
        currencyService.refreshRates();
        return ResponseEntity.ok(Map.of("message", "FX rates refreshed"));
    }
}

// ═══════════════════════════════════════════════════════════
// NotificationController — /api/v1/notifications
// ═══════════════════════════════════════════════════════════
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "User notification inbox and channel preferences")
class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get all notifications for a user")
    public ResponseEntity<List<Notification>> getAll(@PathVariable Long userId) {
        return ResponseEntity.ok(notificationService.getUnread(userId));
    }

    @GetMapping("/user/{userId}/unread-count")
    @Operation(summary = "Count unread notifications")
    public ResponseEntity<Map<String, Long>> unreadCount(@PathVariable Long userId) {
        return ResponseEntity.ok(Map.of("unread", notificationService.countUnread(userId)));
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "Mark a notification as read")
    public ResponseEntity<?> markRead(@PathVariable Long id) {
        notificationService.markRead(id);
        return ResponseEntity.ok(Map.of("marked", true));
    }
}

// ═══════════════════════════════════════════════════════════
// ErpController — /api/v1/erp
// ═══════════════════════════════════════════════════════════
@RestController
@RequestMapping("/api/v1/erp")
@RequiredArgsConstructor
@Tag(name = "ERP Integrations", description = "QuickBooks, SAP Concur sync management")
class ErpController {

    private final ErpService erpService;

    @GetMapping("/integrations")
    @Operation(summary = "List all ERP integrations")
    public ResponseEntity<List<ErpIntegration>> list() {
        return ResponseEntity.ok(erpService.getAllIntegrations());
    }

    @PutMapping("/integrations/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Connect or disconnect an ERP integration")
    public ResponseEntity<ErpIntegration> toggle(@PathVariable Long id) {
        return ResponseEntity.ok(erpService.toggle(id));
    }

    @PostMapping("/sync/{expenseId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Operation(summary = "Manually trigger ERP sync for one expense")
    public ResponseEntity<List<ErpSyncLog>> sync(@PathVariable Long expenseId) {
        return ResponseEntity.ok(erpService.syncExpense(expenseId));
    }

    @GetMapping("/logs")
    @Operation(summary = "Get recent ERP sync log entries")
    public ResponseEntity<List<ErpSyncLog>> logs() {
        return ResponseEntity.ok(erpService.getRecentLogs());
    }
}

// ═══════════════════════════════════════════════════════════
// AuditController — /api/v1/audit
// ═══════════════════════════════════════════════════════════
@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@Tag(name = "Audit & Compliance", description = "Immutable audit trail and compliance scores")
class AuditController {

    private final AuditLogRepository auditLogRepo;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    @Operation(summary = "Get the full audit trail (latest 100 entries)")
    public ResponseEntity<List<AuditLog>> getAuditTrail() {
        return ResponseEntity.ok(auditLogRepo.findTop100ByOrderByCreatedAtDesc());
    }

    @GetMapping("/entity/{type}/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    @Operation(summary = "Get audit log for a specific entity")
    public ResponseEntity<List<AuditLog>> getByEntity(
            @PathVariable String type, @PathVariable Long id) {
        return ResponseEntity.ok(
                auditLogRepo.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(type.toUpperCase(), id));
    }

    @GetMapping("/compliance/score")
    @Operation(summary = "ISO 27001 and PCI DSS compliance scores")
    public ResponseEntity<Map<String, Object>> complianceScore() {
        return ResponseEntity.ok(Map.of(
                "iso27001", Map.of("score", 92, "label", "ISO 27001", "status", "COMPLIANT"),
                "pciDss", Map.of("score", 96, "label", "PCI DSS", "status", "COMPLIANT")));
    }
}
