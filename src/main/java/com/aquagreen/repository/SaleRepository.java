package com.aquagreen.repository;
import com.aquagreen.model.Sale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;
@Repository
public interface SaleRepository extends JpaRepository<Sale,Long> {
    Page<Sale> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<Sale> findAllByOrderByCreatedAtDesc();
    @Query("SELECT COALESCE(SUM(s.totalAmount),0) FROM Sale s")
    BigDecimal sumTotalRevenue();
    @Query("SELECT COALESCE(SUM(s.totalAmount),0) FROM Sale s WHERE MONTH(s.createdAt)=MONTH(CURRENT_DATE) AND YEAR(s.createdAt)=YEAR(CURRENT_DATE)")
    BigDecimal sumMonthlyRevenue();
    // For dashboard monthly chart — group sales by month
    @Query("SELECT MONTH(s.createdAt) as month, COALESCE(SUM(s.totalAmount),0) as total FROM Sale s WHERE YEAR(s.createdAt)=YEAR(CURRENT_DATE) GROUP BY MONTH(s.createdAt) ORDER BY MONTH(s.createdAt)")
    List<Object[]> monthlySaleTotals();
    long count();
    // Customer 360 history
    List<Sale> findByCustomerMobileOrderByCreatedAtDesc(String customerMobile);
}
