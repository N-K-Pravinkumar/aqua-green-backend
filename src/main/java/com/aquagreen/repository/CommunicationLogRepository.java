package com.aquagreen.repository;
import com.aquagreen.model.CommunicationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CommunicationLogRepository extends JpaRepository<CommunicationLog, Long> {
    List<CommunicationLog> findAllByOrderByCreatedAtDesc();
    List<CommunicationLog> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    List<CommunicationLog> findByBatchIdOrderByCreatedAtDesc(String batchId);
    List<CommunicationLog> findByStatusOrderByCreatedAtDesc(String status);
    List<CommunicationLog> findByChannelOrderByCreatedAtDesc(String channel);
    long countByStatus(String status);
    long countByChannel(String channel);

    @Query("SELECT c FROM CommunicationLog c WHERE c.customer.id = :customerId ORDER BY c.createdAt DESC")
    List<CommunicationLog> findTimelineByCustomerId(Long customerId);
}
