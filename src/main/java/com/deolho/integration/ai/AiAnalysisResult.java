package com.deolho.integration.ai;

import com.deolho.domain.enums.Severity;

/**
 * Structured result from AI error analysis.
 */
public record AiAnalysisResult(
        String summary,
        String cause,
        String solution,
        String category,
        Severity severity,
        double confidence
) {}
