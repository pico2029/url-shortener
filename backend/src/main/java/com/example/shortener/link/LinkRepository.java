package com.example.shortener.link;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;
import java.util.Optional;

public interface LinkRepository extends JpaRepository<Link, Long> {
    @Modifying
    @Query("delete from Link l where l.shortCode = :shortCode and l.ownerId = :ownerId")
    int deleteOwnedLink(@Param("shortCode") String shortCode,
            @Param("ownerId") UUID ownerId);

    Optional<Link> findByShortCode(String shortCode);
    Page<Link> findByOwnerIdOrderByCreatedAtDescIdDesc(UUID ownerId, Pageable pageable);
}
