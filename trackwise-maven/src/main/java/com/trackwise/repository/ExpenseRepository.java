package com.trackwise.repository;

import com.trackwise.entity.Expense;
import com.trackwise.enums.ExpenseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    Optional<Expense> findByReferenceCode(String referenceCode);

    Page<Expense> findBySubmittedBy_Id(Long userId, Pageable pageable);

    Page<Expense> findByStatus(ExpenseStatus status, Pageable pageable);

    List<Expense> findByStatus(ExpenseStatus status);

    @Query("""
            SELECT e FROM Expense e
            WHERE e.submittedBy.id = :userId
              AND e.status = :status
            ORDER BY e.createdAt DESC
            """)
    List<Expense> findByUserAndStatus(
            @Param("userId") Long userId,
            @Param("status") ExpenseStatus status);

    @Query("""
            SELECT COALESCE(SUM(e.amountUsd), 0)
            FROM Expense e
            WHERE e.submittedBy.id = :userId
              AND e.expenseDate = :date
              AND e.status NOT IN ('DRAFT', 'REJECTED')
            """)
    BigDecimal sumDailySpend(@Param("userId") Long userId, @Param("date") LocalDate date);

    @Query("""
            SELECT COUNT(e) FROM Expense e
            WHERE e.submittedBy.id = :userId
              AND e.vendor = :vendor
              AND e.amountUsd = :amount
              AND e.expenseDate >= :since
            """)
    Long countDuplicates(
            @Param("userId") Long userId,
            @Param("vendor") String vendor,
            @Param("amount") BigDecimal amount,
            @Param("since") LocalDate since);

    @Query("""
            SELECT COALESCE(SUM(e.amountUsd), 0)
            FROM Expense e
            WHERE e.department.id = :deptId
              AND e.category.id   = :catId
              AND YEAR(e.expenseDate)  = :year
              AND MONTH(e.expenseDate) = :month
              AND e.status IN ('APPROVED', 'REIMBURSED')
            """)
    BigDecimal sumByCategoryDeptMonth(
            @Param("deptId") Long deptId,
            @Param("catId") Long catId,
            @Param("year") Integer year,
            @Param("month") Integer month);

    /* Dashboard analytics */
    @Query("SELECT COALESCE(SUM(e.amountUsd),0) FROM Expense e WHERE e.status IN ('APPROVED','REIMBURSED')")
    BigDecimal sumTotalApproved();

    @Query("SELECT COALESCE(SUM(e.amountUsd),0) FROM Expense e WHERE e.status = 'PENDING'")
    BigDecimal sumTotalPending();
}
