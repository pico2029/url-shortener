package com.example.shortener.analytics.service;

import com.example.shortener.analytics.repository.ClickRepository;
import com.example.shortener.analytics.config.AnalyticsProperties;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DataAccessResourceFailureException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClickRecorderTest {
    @Test
    void queuesTheOriginalTimestampWithoutWritingOnTheRequestThread() {
        var repository = mock(ClickRepository.class);
        var pending = new AtomicReference<Runnable>();
        var recorder = new ClickRecorder(repository, pending::set);
        var occurredAt = Instant.parse("2026-10-10T23:59:59Z");
        recorder.record(42L, occurredAt);
        verifyNoInteractions(repository);
        pending.get().run();
        verify(repository).insert(42L, occurredAt);
    }

    @Test
    void rejectionAndPersistenceFailureDoNotEscapeOrRetry() {
        var repository = mock(ClickRepository.class);
        TaskExecutor full = task -> { throw new TaskRejectedException("full"); };
        assertDoesNotThrow(() -> new ClickRecorder(repository, full).record(1L, Instant.EPOCH));
        verifyNoInteractions(repository);
        doThrow(new DataAccessResourceFailureException("private details")).when(repository).insert(1L, Instant.EPOCH);
        assertDoesNotThrow(() -> new ClickRecorder(repository, Runnable::run).record(1L, Instant.EPOCH));
        verify(repository, times(1)).insert(1L, Instant.EPOCH);
    }

    @Test
    void queueCapacityMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new AnalyticsProperties(0));
        assertThrows(IllegalArgumentException.class, () -> new AnalyticsProperties(-1));
    }
}
