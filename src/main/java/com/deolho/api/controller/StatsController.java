package com.deolho.api.controller;

import com.deolho.domain.repository.ErrorRepository;
import com.deolho.queue.QueueManager;
import com.deolho.service.MetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST API for statistics and dashboard data.
 */
@RestController
@RequestMapping("/api/v1/stats")
@RequiredArgsConstructor
public class StatsController {

    private final ErrorRepository errorRepository;
    private final MetricsService metricsService;
    private final QueueManager queueManager;

    /**
     * Global overview statistics.
     */
    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> overview() {
        Map<String, Object> stats = new LinkedHashMap<>();

        stats.put("totalErrors", errorRepository.count());
        stats.put("errorsByStatus", errorRepository.countGroupedByStatus());
        stats.put("errorsBySeverity", errorRepository.countGroupedBySeverity());
        stats.put("errorsLast24h", errorRepository.countByLastSeenBetween(
                LocalDateTime.now().minusHours(24), LocalDateTime.now()));
        stats.put("errorsLast7d", errorRepository.countByLastSeenBetween(
                LocalDateTime.now().minusDays(7), LocalDateTime.now()));

        // In-memory real-time metrics
        stats.put("realtime", metricsService.getOverview());

        // Queue health
        stats.put("queue", queueManager.getHealth());

        return ResponseEntity.ok(stats);
    }

    /**
     * Top N most frequent errors.
     */
    @GetMapping("/top-errors")
    public ResponseEntity<?> topErrors(@RequestParam(defaultValue = "10") int limit) {
        var top = errorRepository.findTopByOccurrences(PageRequest.of(0, limit));
        return ResponseEntity.ok(top.stream().map(e -> Map.of(
                "id", e.getId(),
                "exception", e.getException(),
                "message", e.getMessage(),
                "occurrences", e.getOccurrences(),
                "severity", e.getSeverity(),
                "application", e.getApplication() != null ? e.getApplication().getName() : "unknown",
                "lastSeen", e.getLastSeen()
        )).toList());
    }

    /**
     * Timeline: errors per hour for the last 24h.
     */
    @GetMapping("/timeline")
    public ResponseEntity<Map<String, Long>> timeline() {
        return ResponseEntity.ok(metricsService.getTimeline());
    }
}
