package com.aquagreen.repository;
import com.aquagreen.model.StockItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface StockItemRepository extends JpaRepository<StockItem,Long> {
    List<StockItem> findByActiveTrueOrderByNameAsc();
    @Query("SELECT s FROM StockItem s WHERE s.active=true AND s.currentStock <= s.minStock")
    List<StockItem> findLowStockItems();
}
