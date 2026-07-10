package com.executionos.repository;

import com.executionos.model.JournalEntry;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {
    Optional<JournalEntry> findByUserIdAndEntryDate(UUID userId, LocalDate entryDate);
    Page<JournalEntry> findByUserId(UUID userId, Pageable pageable);
}
