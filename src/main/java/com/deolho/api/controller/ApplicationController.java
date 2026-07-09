package com.deolho.api.controller;

import com.deolho.api.dto.ApplicationRequest;
import com.deolho.api.dto.ApplicationResponse;
import com.deolho.api.dto.ErrorResponse;
import com.deolho.domain.entity.Application;
import com.deolho.domain.entity.ErrorRecord;
import com.deolho.domain.repository.ApplicationRepository;
import com.deolho.domain.repository.ErrorRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for managing monitored applications.
 */
@RestController
@RequestMapping("/api/v1/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationRepository applicationRepository;
    private final ErrorRepository errorRepository;

    /**
     * List all monitored applications.
     */
    @GetMapping
    public ResponseEntity<List<ApplicationResponse>> listApplications() {
        List<Application> apps = applicationRepository.findAll(Sort.by("name"));
        List<ApplicationResponse> response = apps.stream()
                .map(app -> {
                    long errorCount = errorRepository.countByApplicationId(app.getId());
                    return ApplicationResponse.from(app, errorCount);
                })
                .toList();
        return ResponseEntity.ok(response);
    }

    /**
     * Register a new application.
     */
    @PostMapping
    public ResponseEntity<ApplicationResponse> createApplication(@Valid @RequestBody ApplicationRequest request) {
        if (applicationRepository.existsByName(request.name())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        Application app = Application.builder()
                .name(request.name())
                .environment(request.environment())
                .build();

        Application saved = applicationRepository.save(app);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApplicationResponse.from(saved));
    }

    /**
     * Get application detail.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApplicationResponse> getApplication(@PathVariable Long id) {
        return applicationRepository.findById(id)
                .map(app -> {
                    long errorCount = errorRepository.countByApplicationId(app.getId());
                    return ResponseEntity.ok(ApplicationResponse.from(app, errorCount));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get errors for a specific application.
     */
    @GetMapping("/{id}/errors")
    public ResponseEntity<Page<ErrorResponse>> getApplicationErrors(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (!applicationRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        Page<ErrorRecord> errors = errorRepository.findByApplicationId(
                id, PageRequest.of(page, size, Sort.by("lastSeen").descending()));
        return ResponseEntity.ok(errors.map(ErrorResponse::from));
    }

    /**
     * Delete an application and all its errors.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteApplication(@PathVariable Long id) {
        if (!applicationRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        applicationRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
