package com.deolho.api.controller;

import com.deolho.api.dto.ErrorEventRequest;
import com.deolho.service.EventIngestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Main ingestion endpoint — applications send error events here.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final EventIngestionService ingestionService;

    /**
     * Receive a single error event.
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> ingestEvent(@Valid @RequestBody ErrorEventRequest event) {
        ingestionService.ingest(event);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("status", "accepted", "message", "Event queued for processing"));
    }

    /**
     * Receive a batch of error events.
     */
    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> ingestBatch(@Valid @RequestBody List<ErrorEventRequest> events) {
        ingestionService.ingestBatch(events);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of(
                        "status", "accepted",
                        "message", "Batch queued for processing",
                        "count", events.size()
                ));
    }
}
