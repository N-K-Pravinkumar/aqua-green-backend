package com.aquagreen.repository;
import com.aquagreen.model.FilterPreset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface FilterPresetRepository extends JpaRepository<FilterPreset, Long> {
    List<FilterPreset> findByActiveTrueOrderByUsageCountDesc();
}
