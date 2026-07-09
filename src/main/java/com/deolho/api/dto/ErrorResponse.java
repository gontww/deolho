package com.deolho.api.dto;

import com.deolho.domain.entity.ErrorRecord;
import com.deolho.domain.enums.ErrorStatus;
import com.deolho.domain.enums.Severity;

import java.time.LocalDateTime;

/**
 * Response DTO for error details.
 */
public record ErrorResponse(
        Long id,
        String application,
        String environment,
        String hash,
        String exception,
        String message,
        String stacktrace,
        int occurrences,
        LocalDateTime firstSeen,
        LocalDateTime lastSeen,
        Severity severity,
        String category,
        ErrorStatus status,
        String host,
        AiAnalysisResponse aiAnalysis
) {
    public static ErrorResponse from(ErrorRecord error) {
        AiAnalysisResponse analysis = null;
        if (error.getAiAnalysis() != null) {
            analysis = AiAnalysisResponse.from(error.getAiAnalysis());
        }

        return new ErrorResponse(
                error.getId(),
                error.getApplication() != null ? error.getApplication().getName() : null,
                error.getApplication() != null ? error.getApplication().getEnvironment() : null,
                error.getHash(),
                error.getException(),
                error.getMessage(),
                error.getStacktrace(),
                error.getOccurrences(),
                error.getFirstSeen(),
                error.getLastSeen(),
                error.getSeverity(),
                error.getCategory(),
                error.getStatus(),
                error.getHost(),
                analysis
        );
    }
}
