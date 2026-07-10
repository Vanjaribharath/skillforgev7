package com.executionos.repository;

import com.executionos.model.KnowledgeItem;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeItemRepository extends JpaRepository<KnowledgeItem, UUID> {
    Page<KnowledgeItem> findByUserId(UUID userId, Pageable pageable);
}
