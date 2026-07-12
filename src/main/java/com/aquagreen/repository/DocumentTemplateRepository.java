package com.aquagreen.repository;
import com.aquagreen.model.DocumentTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentTemplateRepository extends JpaRepository<DocumentTemplate, Long> {
    List<DocumentTemplate> findByActiveTrueOrderByCreatedAtDesc();
    List<DocumentTemplate> findByTemplateTypeAndActiveTrueOrderByCreatedAtDesc(String templateType);
    List<DocumentTemplate> findByCategoryAndActiveTrueOrderByCreatedAtDesc(String category);
    Optional<DocumentTemplate> findByTemplateTypeAndIsDefaultTrue(String templateType);
    long countByTemplateType(String templateType);

    // SMS: find the active default template for a specific business event
    Optional<DocumentTemplate> findByTemplateTypeAndSmsEventAndIsDefaultTrueAndActiveTrue(
        String templateType, String smsEvent);

    // SMS: list all active templates for a specific event
    List<DocumentTemplate> findByTemplateTypeAndSmsEventAndActiveTrueOrderByCreatedAtDesc(
        String templateType, String smsEvent);
}
