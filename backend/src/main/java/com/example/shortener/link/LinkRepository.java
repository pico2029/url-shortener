package com.example.shortener.link;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;

public interface LinkRepository extends JpaRepository<Link, Long> {
    Page<Link> findByOwnerIdOrderByCreatedAtDescIdDesc(UUID ownerId, Pageable pageable);
}
