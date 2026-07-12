package com.aquagreen.repository;
import com.aquagreen.model.AutomationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AutomationRuleRepository extends JpaRepository<AutomationRule, Long> {
    List<AutomationRule> findByActiveTrueOrderByCreatedAtDesc();
    List<AutomationRule> findByTriggerTypeAndActiveTrue(String triggerType);
}
