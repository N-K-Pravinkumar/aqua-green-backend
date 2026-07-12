package com.aquagreen.repository;
import com.aquagreen.model.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface CustomerRepository extends JpaRepository<Customer,Long> {
    Page<Customer> findByActiveTrueOrderByCreatedAtDesc(Pageable pageable);
    List<Customer> findByActiveTrueOrderByCreatedAtDesc();
    List<Customer> findAllByOrderByCreatedAtDesc();
    List<Customer> findByNameContainingIgnoreCaseOrMobileContaining(String name, String mobile);
    java.util.Optional<Customer> findByMobile(String mobile);
    long countByActiveTrue();
}
