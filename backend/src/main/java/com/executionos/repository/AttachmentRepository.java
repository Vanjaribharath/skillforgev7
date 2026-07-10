package com.executionos.repository;

import com.executionos.model.Attachment;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {
    Page<Attachment> findByUserId(UUID userId, Pageable pageable);
}
