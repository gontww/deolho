package com.deolho.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Stores the AI-generated analysis for a unique error.
 * One analysis per error — created when the error is first seen.
 */
@Entity
@Table(name = "ai_analyses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "error_id", nullable = false, unique = true)
    private ErrorRecord errorRecord;

    /** Brief human-readable summary of the error */
    @Column(nullable = false, length = 500)
    private String summary;

    /** Probable root cause identified by the AI */
    @Lob
    @Column(columnDefinition = "TEXT", nullable = false)
    private String cause;

    /** Suggested solution / remediation steps */
    @Lob
    @Column(columnDefinition = "TEXT", nullable = false)
    private String solution;

    /** AI-assigned category (e.g., "Database", "Network", "Authentication") */
    @Column
    private String category;

    /** Confidence score for the analysis (0.0 to 1.0) */
    @Column(nullable = false)
    @Builder.Default
    private double confidence = 0.0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
