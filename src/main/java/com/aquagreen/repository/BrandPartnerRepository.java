package com.aquagreen.repository;
import com.aquagreen.model.BrandPartner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface BrandPartnerRepository extends JpaRepository<BrandPartner,Long> {
    List<BrandPartner> findByActiveTrueOrderByDisplayOrderAsc();
}
