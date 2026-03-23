package com.trackwise.repository;

import com.trackwise.entity.PolicyViolation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PolicyViolationRepository extends JpaRepository<PolicyViolation, Long> {
    List<PolicyViolation> findByExpense_IdOrderByEvaluatedAtDesc(Long expenseId);

    List<PolicyViolation> findTop20ByOrderByEvaluatedAtDesc();

    @Query("""
            SELECT pv FROM PolicyViolation pv
            WHERE pv.result IN ('FLAGGED','REJECTED')
            ORDER BY pv.evaluatedAt DESC
            """)
    List<PolicyViolation> findRecentViolations(Pageable pageable);
}
