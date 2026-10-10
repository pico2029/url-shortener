package com.example.shortener.analytics.config;

import com.example.shortener.analytics.repository.ClickRepository;
import com.example.shortener.analytics.service.ClickRecorder;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnalyticsConfigurationTest {
    @Test
    void fullQueueDropsTheEventWithoutBlockingTheCaller() throws Exception {
        var executor = new AnalyticsConfiguration().clickExecutor(new AnalyticsProperties(1));
        executor.initialize();
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var repository = mock(ClickRepository.class);
        doAnswer(invocation -> {
            started.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return null;
        }).when(repository).insert(1L, Instant.EPOCH);
        var recorder = new ClickRecorder(repository, executor);
        try {
            recorder.record(1L, Instant.EPOCH);
            assertTrue(started.await(5, TimeUnit.SECONDS));
            recorder.record(2L, Instant.EPOCH);
            assertDoesNotThrow(() -> recorder.record(3L, Instant.EPOCH));
            verify(repository, never()).insert(3L, Instant.EPOCH);
            release.countDown();
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> verify(repository).insert(2L, Instant.EPOCH));
            verify(repository, never()).insert(3L, Instant.EPOCH);
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }
}
