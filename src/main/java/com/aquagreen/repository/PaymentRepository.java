package com.aquagreen.repository;
import com.aquagreen.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;
@Repository
public interface PaymentRepository extends JpaRepository<Payment,Long> {
    List<Payment> findAllByOrderByCreatedAtDesc();
    List<Payment> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    @Query("SELECT COALESCE(SUM(p.amount),0) FROM Payment p WHERE p.paymentStatus='PAID'")
    BigDecimal sumTotalRevenue();
    @Query("SELECT COALESCE(SUM(p.amount),0) FROM Payment p WHERE p.paymentStatus='PAID' AND MONTH(p.createdAt)=MONTH(CURRENT_DATE) AND YEAR(p.createdAt)=YEAR(CURRENT_DATE)")
    BigDecimal sumMonthlyRevenue();
}
