package com.executionos.repository;

import com.executionos.model.Schedule;
import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleRepository extends JpaRepository<Schedule, UUID> {
    List<Schedule> findByUserEmailOrderByCreatedAtDesc(String email);
}
