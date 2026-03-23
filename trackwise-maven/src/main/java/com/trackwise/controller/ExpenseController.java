package com.trackwise.controller;

import com.trackwise.dto.request.*;
import com.trackwise.dto.response.*;
import com.trackwise.entity.Expense;
import com.trackwise.service.impl.ExpenseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * TrackWise — Expense REST Controller
 *
 * Base URL: /api/v1/expenses
 * Auth: Bearer JWT token required on all endpoints
 */
@RestController
@RequestMapping("/api/v1/expenses")
@RequiredArgsConstructor
@Tag(name = "Expenses", description = "CRUD, approval workflow, and analytics for expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    // ── POST /api/v1/expenses ──────────────────────────────
    @PostMapping
    @Operation(summary = "Submit a new expense claim")
    public ResponseEntity<ApiResponse<Expense>> create(
            @RequestBody ExpenseCreateRequest req) {
        Expense expense = expenseService.createExpense(
                req.getUserId(), req.getDepartmentId(), req.getCategoryId(),
                req.getTitle(), req.getDescription(), req.getVendor(),
                req.getAmount(), req.getCurrency(), req.getExpenseDate(),
                req.getReceiptUrl());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Expense submitted", expense));
    }

    // ── GET /api/v1/expenses/{id} ──────────────────────────
    @GetMapping("/{id}")
    @Operation(summary = "Get a single expense by ID")
    public ResponseEntity<ApiResponse<Expense>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(expenseService.getById(id)));
    }

    // ── POST /api/v1/expenses/{id}/approve ─────────────────
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Operation(summary = "Approve an expense")
    public ResponseEntity<ApiResponse<Expense>> approve(
            @PathVariable Long id,
            @RequestBody ApprovalRequest req) {
        Expense expense = expenseService.approveExpense(id, req.getApproverId(), req.getRemarks());
        return ResponseEntity.ok(ApiResponse.ok("Expense approved", expense));
    }

    // ── POST /api/v1/expenses/{id}/reject ──────────────────
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Operation(summary = "Reject an expense")
    public ResponseEntity<ApiResponse<Expense>> reject(
            @PathVariable Long id,
            @RequestBody RejectRequest req) {
        Expense expense = expenseService.rejectExpense(id, req.getApproverId(), req.getReason());
        return ResponseEntity.ok(ApiResponse.ok("Expense rejected", expense));
    }

    // ── GET /api/v1/expenses/user/{userId} ──────────────
    @GetMapping("/user/{userId}")
    @Operation(summary = "Get expenses for a specific user")
    public ResponseEntity<ApiResponse<List<Expense>>> getUserExpenses(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(expenseService.getUserExpenses(userId)));
    }

    // ── GET /api/v1/expenses/all ──────────────────────────
    @GetMapping("/all")
    @Operation(summary = "Get all expenses (admin/manager)")
    public ResponseEntity<ApiResponse<List<Expense>>> allExpenses() {
        return ResponseEntity.ok(ApiResponse.ok(expenseService.getAllExpenses()));
    }

    // ── GET /api/v1/expenses/pending ───────────────────────
    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Operation(summary = "Get all pending expenses")
    public ResponseEntity<ApiResponse<List<Expense>>> pending() {
        return ResponseEntity.ok(ApiResponse.ok(expenseService.getPendingExpenses()));
    }

    // ── GET /api/v1/expenses/analytics ─────────────────────
    @GetMapping("/analytics")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','AUDITOR')")
    @Operation(summary = "Dashboard summary analytics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> analytics() {
        return ResponseEntity.ok(ApiResponse.ok(expenseService.getAnalyticsSummary()));
    }
}
