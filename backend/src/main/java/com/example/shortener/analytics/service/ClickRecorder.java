package com.example.shortener.analytics.service;

import com.example.shortener.analytics.repository.ClickRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class ClickRecorder {
    private static final Logger LOG = LoggerFactory.getLogger(ClickRecorder.class);
    private final ClickRepository repository;
    private final TaskExecutor executor;
    private final AtomicLong lastWarning = new AtomicLong();

    public ClickRecorder(ClickRepository repository, @Qualifier("clickExecutor") TaskExecutor executor) {
        this.repository = repository;
        this.executor = executor;
    }

    public void record(long linkId, Instant occurredAt) {
        try {
            executor.execute(() -> {
                try {
                    repository.insert(linkId, occurredAt);
                } catch (DataAccessException exception) {
                    warn();
                }
            });
        } catch (TaskRejectedException exception) {
            warn();
        }
    }

    private void warn() {
        long now = System.nanoTime();
        long previous = lastWarning.get();
        if ((previous == 0 || now - previous >= Duration.ofSeconds(30).toNanos())
                && lastWarning.compareAndSet(previous, now)) {
            LOG.warn("Click recording unavailable; event discarded and redirect preserved");
        }
    }
}
