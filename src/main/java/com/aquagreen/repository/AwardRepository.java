package com.aquagreen.repository;
import com.aquagreen.model.Award;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface AwardRepository extends JpaRepository<Award,Long> {
    List<Award> findByActiveTrueOrderByDisplayOrderAsc();
}
