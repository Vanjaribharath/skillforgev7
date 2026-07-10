package com.executionos.repository;

import com.executionos.model.Task;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, UUID> {
    Page<Task> findByUserId(UUID userId, Pageable pageable);
    Page<Task> findByUserEmail(String email, Pageable pageable);
    Page<Task> findByUserEmailAndStatus(String email, com.executionos.model.Enums.TaskStatus status, Pageable pageable);
}
