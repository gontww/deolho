package com.deolho.worker;

import com.deolho.api.dto.ErrorEventRequest;
import com.deolho.domain.entity.Application;
import com.deolho.domain.entity.ErrorRecord;
import com.deolho.domain.enums.ErrorStatus;
import com.deolho.domain.enums.JobType;
import com.deolho.domain.enums.Severity;
import com.deolho.domain.repository.ApplicationRepository;
import com.deolho.domain.repository.ErrorRepository;
import com.deolho.queue.Job;
import com.deolho.queue.QueueManager;
import com.deolho.queue.Worker;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Responsible for:
 * - Validating incoming error events
 * - Computing SHA-256 hash for deduplication
 * - Persisting new errors or incrementing occurrence counter
 * - Enqueuing follow-up jobs (AI_ANALYZE, NOTIFY) for new errors
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PersistWorker implements Worker {

    private final ErrorRepository errorRepository;
    private final ApplicationRepository applicationRepository;
    private final QueueManager queueManager;
    private final ObjectMapper objectMapper;

    @Override
    public JobType getJobType() {
        return JobType.PERSIST;
    }

    @Override
    public void process(Job job) throws Exception {
        ErrorEventRequest event = objectMapper.readValue(job.payload(), ErrorEventRequest.class);
        log.debug("Processing error event: app={}, exception={}", event.application(), event.exception());

        // 1. Resolve or create the application
        Application application = resolveApplication(event);

        // 2. Compute dedup hash
        String hash = computeHash(event);

        // 3. Check for existing error
        Optional<ErrorRecord> existing = errorRepository.findByHash(hash);

        if (existing.isPresent()) {
            // Duplicate — increment counter
            ErrorRecord error = existing.get();
            error.recordNewOccurrence();
            errorRepository.save(error);
            log.info("Duplicate error detected: hash={}, occurrences={}", hash, error.getOccurrences());

            // Always enqueue metrics update
            queueManager.submit(new Job(JobType.METRICS, job.payload()));
        } else {
            // New error — save and trigger AI + notification pipeline
            ErrorRecord error = ErrorRecord.builder()
                    .application(application)
                    .hash(hash)
                    .exception(event.exception() != null ? event.exception() : "Unknown")
                    .message(event.message())
                    .stacktrace(event.stacktrace())
                    .occurrences(1)
                    .firstSeen(LocalDateTime.now())
                    .lastSeen(LocalDateTime.now())
                    .severity(mapLevelToSeverity(event.level()))
                    .status(ErrorStatus.NEW)
                    .host(event.host())
                    .build();

            ErrorRecord saved = errorRepository.save(error);
            log.info("New error persisted: id={}, hash={}, exception={}", saved.getId(), hash, saved.getException());

            // Enqueue follow-up jobs for new errors
            String payload = objectMapper.writeValueAsString(new ErrorPayload(saved.getId(), job.payload()));
            queueManager.submitAll(List.of(
                    new Job(JobType.AI_ANALYZE, payload, 3),
                    new Job(JobType.NOTIFY, payload, 4),
                    new Job(JobType.METRICS, job.payload())
            ));
        }
    }

    /**
     * Find existing application or create a new one.
     */
    private Application resolveApplication(ErrorEventRequest event) {
        return applicationRepository.findByName(event.application())
                .orElseGet(() -> {
                    Application app = Application.builder()
                            .name(event.application())
                            .environment(event.environment() != null ? event.environment() : "unknown")
                            .build();
                    Application saved = applicationRepository.save(app);
                    log.info("Auto-registered new application: name={}, id={}", saved.getName(), saved.getId());
                    return saved;
                });
    }

    /**
     * Compute SHA-256 hash based on exception type + message + first line of stacktrace.
     * This groups identical errors while allowing variations in deeper stack frames.
     */
    private String computeHash(ErrorEventRequest event) {
        String input = normalizeForHash(event.exception())
                + "|" + normalizeForHash(event.message())
                + "|" + extractFirstStackLine(event.stacktrace());

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private String normalizeForHash(String value) {
        return value != null ? value.trim().toLowerCase() : "";
    }

    private String extractFirstStackLine(String stacktrace) {
        if (stacktrace == null || stacktrace.isBlank()) return "";
        String[] lines = stacktrace.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("at ")) {
                return trimmed;
            }
        }
        return lines[0].trim();
    }

    private Severity mapLevelToSeverity(String level) {
        if (level == null) return Severity.MEDIUM;
        return switch (level.toUpperCase()) {
            case "FATAL", "CRITICAL" -> Severity.CRITICAL;
            case "ERROR" -> Severity.HIGH;
            case "WARN", "WARNING" -> Severity.MEDIUM;
            default -> Severity.LOW;
        };
    }

    /**
     * Internal record to pass error ID + original payload to downstream workers.
     */
    public record ErrorPayload(Long errorId, String originalEvent) {}
}
