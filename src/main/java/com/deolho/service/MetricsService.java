package com.deolho.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory metrics aggregator.
 * Tracks error counts, rates, and distributions for real-time dashboard access.
 */
@Slf4j
@Service
public class MetricsService {

    private final AtomicLong totalErrors = new AtomicLong(0);
    private final Map<String, AtomicLong> errorsByApplication = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> errorsByLevel = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> errorsByHour = new ConcurrentHashMap<>();

    /**
     * Record a new error event for metrics.
     */
    public void recordEvent(String application, String level) {
        totalErrors.incrementAndGet();

        errorsByApplication
                .computeIfAbsent(application, k -> new AtomicLong(0))
                .incrementAndGet();

        if (level != null) {
            errorsByLevel
                    .computeIfAbsent(level.toUpperCase(), k -> new AtomicLong(0))
                    .incrementAndGet();
        }

        // Track by hour bucket for timeline
        String hourKey = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0).toString();
        errorsByHour
                .computeIfAbsent(hourKey, k -> new AtomicLong(0))
                .incrementAndGet();

        log.debug("Metrics updated: total={}, app={}", totalErrors.get(), application);
    }

    public long getTotalErrors() {
        return totalErrors.get();
    }

    public Map<String, Long> getErrorsByApplication() {
        Map<String, Long> result = new ConcurrentHashMap<>();
        errorsByApplication.forEach((k, v) -> result.put(k, v.get()));
        return result;
    }

    public Map<String, Long> getErrorsByLevel() {
        Map<String, Long> result = new ConcurrentHashMap<>();
        errorsByLevel.forEach((k, v) -> result.put(k, v.get()));
        return result;
    }

    public Map<String, Long> getTimeline() {
        Map<String, Long> result = new ConcurrentHashMap<>();
        errorsByHour.forEach((k, v) -> result.put(k, v.get()));
        return result;
    }

    /**
     * Get a full snapshot of all metrics for the dashboard.
     */
    public Map<String, Object> getOverview() {
        return Map.of(
                "totalErrors", totalErrors.get(),
                "errorsByApplication", getErrorsByApplication(),
                "errorsByLevel", getErrorsByLevel(),
                "timeline", getTimeline()
        );
    }
}
