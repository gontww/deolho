package com.deolho.service;

import com.deolho.api.dto.ErrorEventRequest;
import com.deolho.domain.enums.JobType;
import com.deolho.queue.Job;
import com.deolho.queue.QueueManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for ingesting error events into the processing pipeline.
 * Serializes events and submits them to the queue as PERSIST jobs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventIngestionService {

    private final QueueManager queueManager;
    private final ObjectMapper objectMapper;

    /**
     * Ingest a single error event.
     */
    public void ingest(ErrorEventRequest event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            Job job = new Job(JobType.PERSIST, payload, mapPriority(event.level()));
            queueManager.submit(job);
            log.info("Event ingested: app={}, exception={}", event.application(), event.exception());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize error event: {}", e.getMessage());
            throw new RuntimeException("Failed to process event", e);
        }
    }

    /**
     * Ingest a batch of error events.
     */
    public void ingestBatch(List<ErrorEventRequest> events) {
        List<Job> jobs = events.stream()
                .map(event -> {
                    try {
                        String payload = objectMapper.writeValueAsString(event);
                        return new Job(JobType.PERSIST, payload, mapPriority(event.level()));
                    } catch (JsonProcessingException e) {
                        log.error("Failed to serialize event: {}", e.getMessage());
                        return null;
                    }
                })
                .filter(j -> j != null)
                .toList();

        queueManager.submitAll(jobs);
        log.info("Batch ingested: {} events", jobs.size());
    }

    private int mapPriority(String level) {
        if (level == null) return 5;
        return switch (level.toUpperCase()) {
            case "FATAL", "CRITICAL" -> 1;
            case "ERROR" -> 2;
            case "WARN", "WARNING" -> 4;
            default -> 5;
        };
    }
}
