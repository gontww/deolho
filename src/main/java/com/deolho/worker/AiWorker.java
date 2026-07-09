package com.deolho.worker;

import com.deolho.domain.entity.AiAnalysis;
import com.deolho.domain.entity.ErrorRecord;
import com.deolho.domain.enums.JobType;
import com.deolho.domain.repository.AiAnalysisRepository;
import com.deolho.domain.repository.ErrorRepository;
import com.deolho.integration.ai.AiAnalysisResult;
import com.deolho.integration.ai.AiService;
import com.deolho.queue.Job;
import com.deolho.queue.Worker;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Processes new errors through the AI analysis pipeline.
 * Only runs for first-time errors (not duplicates).
 * <p>
 * Responsibilities:
 * - Interpret the exception via AI
 * - Identify probable causes
 * - Suggest solutions
 * - Classify severity and category
 * - Store analysis in the knowledge base
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiWorker implements Worker {

    private final AiService aiService;
    private final ErrorRepository errorRepository;
    private final AiAnalysisRepository aiAnalysisRepository;
    private final ObjectMapper objectMapper;

    @Override
    public JobType getJobType() {
        return JobType.AI_ANALYZE;
    }

    @Override
    public void process(Job job) throws Exception {
        PersistWorker.ErrorPayload payload = objectMapper.readValue(
                job.payload(), PersistWorker.ErrorPayload.class);

        Long errorId = payload.errorId();
        log.debug("AI analysis started for error id={}", errorId);

        // Check if analysis already exists (idempotency)
        if (aiAnalysisRepository.existsByErrorRecordId(errorId)) {
            log.info("AI analysis already exists for error id={}. Skipping.", errorId);
            return;
        }

        ErrorRecord errorRecord = errorRepository.findById(errorId)
                .orElseThrow(() -> new IllegalStateException("Error not found: id=" + errorId));

        // Call AI service
        AiAnalysisResult result = aiService.analyze(errorRecord);

        // Persist analysis
        AiAnalysis analysis = AiAnalysis.builder()
                .errorRecord(errorRecord)
                .summary(result.summary())
                .cause(result.cause())
                .solution(result.solution())
                .category(result.category())
                .confidence(result.confidence())
                .build();

        aiAnalysisRepository.save(analysis);

        // Update error with AI-derived category and severity if available
        if (result.category() != null && !result.category().isBlank()) {
            errorRecord.setCategory(result.category());
        }
        if (result.severity() != null) {
            errorRecord.setSeverity(result.severity());
        }
        errorRepository.save(errorRecord);

        log.info("AI analysis complete for error id={}: category={}, severity={}, confidence={}",
                errorId, result.category(), result.severity(), result.confidence());
    }
}
