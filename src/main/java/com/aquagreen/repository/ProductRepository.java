package com.aquagreen.repository;
import com.aquagreen.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface ProductRepository extends JpaRepository<Product,Long> {
    List<Product> findByActiveTrueOrderByDisplayOrderAsc();
    List<Product> findByCategoryAndActiveTrueOrderByDisplayOrderAsc(String category);
    List<Product> findTop5ByActiveTrueOrderByDisplayOrderAsc();
}
