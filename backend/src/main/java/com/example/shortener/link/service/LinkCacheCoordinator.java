package com.example.shortener.link.service;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.springframework.stereotype.Component;

/** Coordinates cache writes and deletion within the single backend process. */
@Component
public class LinkCacheCoordinator {
    private final Group[] groups = new Group[256];

    public LinkCacheCoordinator() {
        String process = UUID.randomUUID().toString();
        for (int i = 0; i < groups.length; i++) {
            groups[i] = new Group("shortener:redirect:v1:" + process + ":" + i + ":");
        }
    }

    Group group(String shortCode) {
        return groups[Math.floorMod(shortCode.hashCode(), groups.length)];
    }

    static final class Group {
        final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
        private final String prefix;
        private final AtomicReference<String> generation = new AtomicReference<>(UUID.randomUUID().toString());

        Group(String prefix) {
            this.prefix = prefix;
        }

        String generation() { return generation.get(); }
        String key(String code, String capturedGeneration) { return prefix + capturedGeneration + ":" + code; }
        boolean isCurrent(String capturedGeneration) { return generation.get().equals(capturedGeneration); }

        void abandon(String capturedGeneration) {
            generation.compareAndSet(capturedGeneration, UUID.randomUUID().toString());
        }
    }
}
