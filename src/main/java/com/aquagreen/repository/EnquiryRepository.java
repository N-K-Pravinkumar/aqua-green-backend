package com.aquagreen.repository;
import com.aquagreen.model.Enquiry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface EnquiryRepository extends JpaRepository<Enquiry,Long> {
    Page<Enquiry> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<Enquiry> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);
    List<Enquiry> findAllByOrderByCreatedAtDesc();
    List<Enquiry> findByStatusOrderByCreatedAtDesc(String status);
    long countByStatus(String status);
    // Customer 360 history — match by mobile since Enquiry has no customer_id FK
    List<Enquiry> findByMobileOrderByCreatedAtDesc(String mobile);
}
