package com.deolho.api.dto;

import com.deolho.domain.entity.AiAnalysis;

import java.time.LocalDateTime;

/**
 * Response DTO for AI analysis results.
 */
public record AiAnalysisResponse(
        Long id,
        String summary,
        String cause,
        String solution,
        String category,
        double confidence,
        LocalDateTime createdAt
) {
    public static AiAnalysisResponse from(AiAnalysis analysis) {
        return new AiAnalysisResponse(
                analysis.getId(),
                analysis.getSummary(),
                analysis.getCause(),
                analysis.getSolution(),
                analysis.getCategory(),
                analysis.getConfidence(),
                analysis.getCreatedAt()
        );
    }
}
