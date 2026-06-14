package com.jarvis.reminder;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReminderRepository extends JpaRepository<Reminder, String> {

    /** Reminders that are due and not yet fired, oldest first. */
    List<Reminder> findByFiredFalseAndFireAtLessThanEqualOrderByFireAtAsc(Instant now);

    List<Reminder> findByFiredFalseOrderByFireAtAsc();
}
