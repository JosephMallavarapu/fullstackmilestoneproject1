package com.trackwise.repository;

import com.trackwise.entity.PolicyRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PolicyRuleRepository extends JpaRepository<PolicyRule, Long> {
    List<PolicyRule> findByIsActiveTrueOrderByCreatedAtDesc();

    Optional<PolicyRule> findByRuleType(PolicyRule.RuleType ruleType);
}
