package com.deolho.integration.ai;

import com.deolho.domain.entity.ErrorRecord;

/**
 * Contract for AI-powered error analysis.
 * Implementations can use OpenAI, Gemini, Claude, or a local model.
 */
public interface AiService {

    /**
     * Analyze an error and produce a structured result with
     * cause, solution, category, severity, and confidence.
     */
    AiAnalysisResult analyze(ErrorRecord errorRecord);
}
