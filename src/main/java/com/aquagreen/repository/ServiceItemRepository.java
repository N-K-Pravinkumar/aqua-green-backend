package com.aquagreen.repository;
import com.aquagreen.model.ServiceItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface ServiceItemRepository extends JpaRepository<ServiceItem,Long> {
    List<ServiceItem> findByActiveTrueOrderByDisplayOrderAsc();
    List<ServiceItem> findTop5ByActiveTrueOrderByDisplayOrderAsc();
}
