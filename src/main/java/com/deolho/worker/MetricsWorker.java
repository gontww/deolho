package com.deolho.worker;

import com.deolho.api.dto.ErrorEventRequest;
import com.deolho.domain.enums.JobType;
import com.deolho.queue.Job;
import com.deolho.queue.Worker;
import com.deolho.service.MetricsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Updates aggregated metrics after each error event.
 * <p>
 * Tracks:
 * - Total error count
 * - Errors per application
 * - Errors per severity
 * - Frequency / time-based metrics
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsWorker implements Worker {

    private final MetricsService metricsService;
    private final ObjectMapper objectMapper;

    @Override
    public JobType getJobType() {
        return JobType.METRICS;
    }

    @Override
    public void process(Job job) throws Exception {
        ErrorEventRequest event = objectMapper.readValue(job.payload(), ErrorEventRequest.class);
        log.debug("Updating metrics for app={}", event.application());

        metricsService.recordEvent(event.application(), event.level());
    }
}
