package com.deolho.config;

import com.deolho.domain.enums.JobType;
import com.deolho.queue.QueueManager;
import com.deolho.queue.Worker;
import com.deolho.worker.AiWorker;
import com.deolho.worker.MetricsWorker;
import com.deolho.worker.NotificationWorker;
import com.deolho.worker.PersistWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

import java.util.List;

/**
 * Registers all workers with the QueueManager on application startup.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class WorkerConfig {

    private final QueueManager queueManager;
    private final PersistWorker persistWorker;
    private final AiWorker aiWorker;
    private final NotificationWorker notificationWorker;
    private final MetricsWorker metricsWorker;

    @EventListener(ContextRefreshedEvent.class)
    public void registerWorkers() {
        queueManager.registerWorkers(JobType.PERSIST, List.of(persistWorker));
        queueManager.registerWorkers(JobType.AI_ANALYZE, List.of(aiWorker));
        queueManager.registerWorkers(JobType.NOTIFY, List.of(notificationWorker));
        queueManager.registerWorkers(JobType.METRICS, List.of(metricsWorker));

        log.info("All workers registered with QueueManager");

        // Start consuming after all workers are registered
        queueManager.start();
    }
}
