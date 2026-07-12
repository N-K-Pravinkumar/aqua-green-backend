package com.aquagreen.repository;
import com.aquagreen.model.GalleryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface GalleryRepository extends JpaRepository<GalleryItem,Long> {
    List<GalleryItem> findByActiveTrueOrderByDisplayOrderAsc();
    List<GalleryItem> findByCategoryAndActiveTrueOrderByDisplayOrderAsc(String category);
    List<GalleryItem> findTop6ByActiveTrueOrderByDisplayOrderAsc();
}
