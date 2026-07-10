package com.executionos.repository;

import com.executionos.model.FocusSession;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FocusSessionRepository extends JpaRepository<FocusSession, UUID> {
    Page<FocusSession> findByUserId(UUID userId, Pageable pageable);
    Page<FocusSession> findByUserEmail(String email, Pageable pageable);
}
