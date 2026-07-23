package com.aquagreen.repository;
import com.aquagreen.model.Lead;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface LeadRepository extends JpaRepository<Lead,Long> {
    // Paginated (preferred)
    Page<Lead> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<Lead> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);
    // Unpaginated — kept for counts/dashboard
    List<Lead> findAllByOrderByCreatedAtDesc();
    List<Lead> findByStatusOrderByCreatedAtDesc(String status);
    long countByStatus(String status);
    // Customer 360 history — match by mobile since Lead has no customer_id FK
    List<Lead> findByMobileOrderByCreatedAtDesc(String mobile);
}
