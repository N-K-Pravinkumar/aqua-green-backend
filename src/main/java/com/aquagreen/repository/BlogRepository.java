package com.aquagreen.repository;
import com.aquagreen.model.Blog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
@Repository
public interface BlogRepository extends JpaRepository<Blog,Long> {
    List<Blog> findAllByOrderByCreatedAtDesc();
    List<Blog> findByStatusOrderByCreatedAtDesc(String status);
    Optional<Blog> findBySlug(String slug);
    List<Blog> findTop3ByStatusOrderByPublishedAtDesc(String status);
}
