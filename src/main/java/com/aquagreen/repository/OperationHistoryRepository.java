package com.aquagreen.repository;
import com.aquagreen.model.OperationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface OperationHistoryRepository extends JpaRepository<OperationHistory,Long> {
    List<OperationHistory> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    List<OperationHistory> findAllByOrderByCreatedAtDesc();
    List<OperationHistory> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String type, Long id);
    List<OperationHistory> findTop50ByOrderByCreatedAtDesc();
}
