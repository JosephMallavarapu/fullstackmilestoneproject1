package com.trackwise.repository;

import com.trackwise.entity.BudgetGoal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetGoalRepository extends JpaRepository<BudgetGoal, Long> {
    Optional<BudgetGoal> findByDepartment_IdAndCategory_IdAndFiscalYearAndFiscalQuarter(
            Long deptId, Long catId, Integer year, Integer quarter);

    @Query("""
            SELECT bg FROM BudgetGoal bg
            WHERE bg.department.id = :deptId
              AND bg.fiscalYear = :year
              AND bg.fiscalQuarter = :quarter
            """)
    List<BudgetGoal> findByDepartmentAndPeriod(
            @Param("deptId") Long deptId,
            @Param("year") Integer year,
            @Param("quarter") Integer quarter);
}
