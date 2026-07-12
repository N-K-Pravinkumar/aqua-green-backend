package com.aquagreen.repository;
import com.aquagreen.model.Quotation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface QuotationRepository extends JpaRepository<Quotation,Long> {
    Page<Quotation> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<Quotation> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);
    List<Quotation> findAllByOrderByCreatedAtDesc();
    List<Quotation> findByStatusOrderByCreatedAtDesc(String status);
    long countByStatus(String status);
}
