package com.executionos.repository;

import com.executionos.model.Note;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoteRepository extends JpaRepository<Note, UUID> {
    Page<Note> findByUserId(UUID userId, Pageable pageable);
    Page<Note> findByUserEmail(String email, Pageable pageable);
}
