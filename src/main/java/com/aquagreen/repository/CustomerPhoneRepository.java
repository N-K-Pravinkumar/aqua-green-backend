package com.aquagreen.repository;

import com.aquagreen.model.CustomerPhone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerPhoneRepository extends JpaRepository<CustomerPhone, Long> {
    List<CustomerPhone> findByCustomerId(Long customerId);
    Optional<CustomerPhone> findByPhone(String phone);
    boolean existsByPhone(String phone);
}
