package com.deolho.domain.entity;

import com.deolho.domain.enums.ErrorStatus;
import com.deolho.domain.enums.Severity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Represents a unique error occurrence tracked by the system.
 * Named "ErrorRecord" to avoid conflict with java.lang.Error.
 */
@Entity
@Table(name = "errors", indexes = {
        @Index(name = "idx_errors_hash", columnList = "hash"),
        @Index(name = "idx_errors_application_id", columnList = "application_id"),
        @Index(name = "idx_errors_status", columnList = "status"),
        @Index(name = "idx_errors_severity", columnList = "severity"),
        @Index(name = "idx_errors_last_seen", columnList = "last_seen")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    /** SHA-256 hash of exception + message + first stacktrace line for deduplication */
    @Column(nullable = false, length = 64)
    private String hash;

    /** Fully qualified exception class name (e.g., java.sql.SQLTransientConnectionException) */
    @Column(nullable = false)
    private String exception;

    @Column(nullable = false, length = 1000)
    private String message;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String stacktrace;

    /** Number of times this exact error has been seen */
    @Column(nullable = false)
    @Builder.Default
    private int occurrences = 1;

    @Column(name = "first_seen", nullable = false, updatable = false)
    private LocalDateTime firstSeen;

    @Column(name = "last_seen", nullable = false)
    private LocalDateTime lastSeen;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Severity severity = Severity.MEDIUM;

    /** AI-determined or user-assigned category (e.g., "Database", "Network", "NullPointer") */
    @Column
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ErrorStatus status = ErrorStatus.NEW;

    /** Host/server where the error originated */
    @Column
    private String host;

    @OneToOne(mappedBy = "errorRecord", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private AiAnalysis aiAnalysis;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (firstSeen == null) firstSeen = now;
        if (lastSeen == null) lastSeen = now;
    }

    /**
     * Increment occurrence counter and update the last-seen timestamp.
     */
    public void recordNewOccurrence() {
        this.occurrences++;
        this.lastSeen = LocalDateTime.now();
    }
}
