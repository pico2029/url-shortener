package com.example.shortener.link;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;
import java.util.Optional;

public interface LinkRepository extends JpaRepository<Link, Long> {
    Optional<Link> findByShortCode(String shortCode);
    Page<Link> findByOwnerIdOrderByCreatedAtDescIdDesc(UUID ownerId, Pageable pageable);
}
