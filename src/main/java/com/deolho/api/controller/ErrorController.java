package com.deolho.api.controller;

import com.deolho.api.dto.AiAnalysisResponse;
import com.deolho.api.dto.ErrorResponse;
import com.deolho.api.dto.UpdateStatusRequest;
import com.deolho.domain.entity.AiAnalysis;
import com.deolho.domain.entity.ErrorRecord;
import com.deolho.domain.enums.ErrorStatus;
import com.deolho.domain.enums.Severity;
import com.deolho.domain.repository.AiAnalysisRepository;
import com.deolho.domain.repository.ErrorRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for querying and managing errors.
 */
@RestController
@RequestMapping("/api/v1/errors")
@RequiredArgsConstructor
public class ErrorController {

    private final ErrorRepository errorRepository;
    private final AiAnalysisRepository aiAnalysisRepository;

    /**
     * List errors with pagination, filtering, and sorting.
     */
    @GetMapping
    public ResponseEntity<Page<ErrorResponse>> listErrors(
            @RequestParam(required = false) ErrorStatus status,
            @RequestParam(required = false) Severity severity,
            @RequestParam(required = false) Long applicationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "lastSeen") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        Sort sort = sortDir.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<ErrorRecord> errors;

        if (applicationId != null && status != null) {
            errors = errorRepository.findByApplicationIdAndStatus(applicationId, status, pageable);
        } else if (status != null) {
            errors = errorRepository.findByStatus(status, pageable);
        } else if (severity != null) {
            errors = errorRepository.findBySeverity(severity, pageable);
        } else if (applicationId != null) {
            errors = errorRepository.findByApplicationId(applicationId, pageable);
        } else {
            errors = errorRepository.findAll(pageable);
        }

        return ResponseEntity.ok(errors.map(ErrorResponse::from));
    }

    /**
     * Get error detail by ID (includes AI analysis if available).
     */
    @GetMapping("/{id}")
    public ResponseEntity<ErrorResponse> getError(@PathVariable Long id) {
        return errorRepository.findById(id)
                .map(ErrorResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get AI analysis for a specific error.
     */
    @GetMapping("/{id}/analysis")
    public ResponseEntity<AiAnalysisResponse> getAnalysis(@PathVariable Long id) {
        return aiAnalysisRepository.findByErrorRecordId(id)
                .map(AiAnalysisResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Update error status (ACKNOWLEDGED, RESOLVED, IGNORED).
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<ErrorResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStatusRequest request
    ) {
        return errorRepository.findById(id)
                .map(error -> {
                    error.setStatus(request.status());
                    ErrorRecord saved = errorRepository.save(error);
                    return ResponseEntity.ok(ErrorResponse.from(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Search errors by message or exception type.
     */
    @GetMapping("/search")
    public ResponseEntity<Page<ErrorResponse>> searchErrors(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("lastSeen").descending());
        Page<ErrorRecord> results = errorRepository.searchByMessageOrException(q, pageable);
        return ResponseEntity.ok(results.map(ErrorResponse::from));
    }

    /**
     * Top N most frequent errors.
     */
    @GetMapping("/top")
    public ResponseEntity<List<ErrorResponse>> topErrors(
            @RequestParam(defaultValue = "10") int limit
    ) {
        List<ErrorRecord> top = errorRepository.findTopByOccurrences(PageRequest.of(0, limit));
        return ResponseEntity.ok(top.stream().map(ErrorResponse::from).toList());
    }
}
