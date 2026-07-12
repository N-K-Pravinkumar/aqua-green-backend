package com.aquagreen.repository;
import com.aquagreen.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
@Repository
public interface AppUserRepository extends JpaRepository<AppUser,Long> {
    Optional<AppUser> findByUsername(String username);
    Optional<AppUser> findByEmail(String email);
    Optional<AppUser> findByPasswordResetToken(String token);
    Optional<AppUser> findByRefreshToken(String token);
    List<AppUser> findByRoleNotOrderByCreatedAtDesc(String role);
    List<AppUser> findByRoleOrderByCreatedAtDesc(String role);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
}
