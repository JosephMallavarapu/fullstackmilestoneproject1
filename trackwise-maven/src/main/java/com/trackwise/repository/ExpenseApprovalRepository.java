package com.trackwise.repository;

import com.trackwise.entity.ExpenseApproval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExpenseApprovalRepository extends JpaRepository<ExpenseApproval, Long> {
        List<ExpenseApproval> findByApprover_IdAndActionOrderByCreatedAtDesc(
                        Long approverId, ExpenseApproval.ApprovalAction action);

        Optional<ExpenseApproval> findByExpense_IdAndLevelAndAction(
                        Long expenseId, Short level, ExpenseApproval.ApprovalAction action);
}
