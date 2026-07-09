package com.deolho.queue;

import com.deolho.domain.enums.JobType;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Custom queue manager using Java 21 concurrent primitives.
 * <p>
 * Manages separate priority queues per JobType, dispatches jobs to registered workers
 * via virtual threads, and handles retry logic with a dead-letter queue.
 */
@Slf4j
@Component
public class QueueManager {

    private final Map<JobType, PriorityBlockingQueue<Job>> queues = new EnumMap<>(JobType.class);
    private final BlockingQueue<Job> deadLetterQueue = new LinkedBlockingQueue<>();
    private final Map<JobType, List<Worker>> workers = new EnumMap<>(JobType.class);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService executorService;

    // Metrics
    private final AtomicLong totalSubmitted = new AtomicLong(0);
    private final AtomicLong totalProcessed = new AtomicLong(0);
    private final AtomicLong totalFailed = new AtomicLong(0);

    @Value("${deolho.queue.max-retries:3}")
    private int maxRetries;

    @Value("${deolho.queue.retry-delay-ms:5000}")
    private long retryDelayMs;

    public QueueManager() {
        for (JobType type : JobType.values()) {
            queues.put(type, new PriorityBlockingQueue<>());
        }
    }

    /**
     * Register workers for a specific job type.
     */
    public void registerWorkers(JobType type, List<Worker> workerList) {
        workers.put(type, workerList);
        log.info("Registered {} worker(s) for job type: {}", workerList.size(), type);
    }

    /**
     * Submit a job to the appropriate queue.
     */
    public void submit(Job job) {
        queues.get(job.type()).offer(job);
        totalSubmitted.incrementAndGet();
        log.debug("Job submitted: type={}, priority={}", job.type(), job.priority());
    }

    /**
     * Submit multiple jobs atomically (e.g., one event → PERSIST + METRICS).
     */
    public void submitAll(List<Job> jobs) {
        jobs.forEach(this::submit);
    }

    /**
     * Start all worker consumer loops as virtual threads.
     * Called explicitly by WorkerConfig after all workers are registered.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            executorService = Executors.newVirtualThreadPerTaskExecutor();

            for (JobType type : JobType.values()) {
                List<Worker> typeWorkers = workers.getOrDefault(type, List.of());
                if (typeWorkers.isEmpty()) {
                    log.warn("No workers registered for job type: {}. Starting consumer anyway.", type);
                }
                // Start a consumer loop for each job type
                executorService.submit(() -> consumeLoop(type));
            }

            log.info("QueueManager started — consuming from {} queues", JobType.values().length);
        }
    }

    /**
     * Graceful shutdown — drain queues and wait for in-flight jobs.
     */
    @PreDestroy
    public void stop() {
        if (running.compareAndSet(true, false)) {
            log.info("QueueManager shutting down...");
            if (executorService != null) {
                executorService.shutdown();
                try {
                    if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                        executorService.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executorService.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
            log.info("QueueManager stopped. Submitted={}, Processed={}, Failed={}, DeadLetter={}",
                    totalSubmitted.get(), totalProcessed.get(), totalFailed.get(), deadLetterQueue.size());
        }
    }

    /**
     * Main consumer loop for a given job type.
     * Takes jobs from the queue and dispatches them to the first available worker.
     */
    private void consumeLoop(JobType type) {
        PriorityBlockingQueue<Job> queue = queues.get(type);
        Thread.currentThread().setName("queue-consumer-" + type.name().toLowerCase());

        while (running.get()) {
            try {
                Job job = queue.poll(1, TimeUnit.SECONDS);
                if (job == null) continue;

                processJob(type, job);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.debug("Consumer interrupted for type: {}", type);
                break;
            } catch (Exception e) {
                log.error("Unexpected error in consumer loop for type: {}", type, e);
            }
        }
    }

    /**
     * Process a single job — delegate to workers, handle retry on failure.
     */
    private void processJob(JobType type, Job job) {
        List<Worker> typeWorkers = workers.getOrDefault(type, List.of());
        if (typeWorkers.isEmpty()) {
            log.warn("No workers for job type: {}. Sending to dead letter queue.", type);
            deadLetterQueue.offer(job);
            totalFailed.incrementAndGet();
            return;
        }

        for (Worker worker : typeWorkers) {
            try {
                worker.process(job);
                totalProcessed.incrementAndGet();
            } catch (Exception e) {
                log.error("Worker {} failed processing job: {}", worker.getClass().getSimpleName(), e.getMessage(), e);

                if (job.retryCount() < maxRetries) {
                    Job retryJob = job.retry();
                    log.info("Retrying job (attempt {}/{}): type={}", retryJob.retryCount(), maxRetries, type);

                    // Schedule retry with delay using a virtual thread
                    executorService.submit(() -> {
                        try {
                            Thread.sleep(retryDelayMs);
                            submit(retryJob);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    });
                } else {
                    log.error("Job exhausted all retries ({}/{}). Sending to dead letter queue: type={}",
                            job.retryCount(), maxRetries, type);
                    deadLetterQueue.offer(job);
                    totalFailed.incrementAndGet();
                }
            }
        }
    }

    // --- Metrics / Health ---

    public int getQueueSize(JobType type) {
        return queues.get(type).size();
    }

    public int getDeadLetterQueueSize() {
        return deadLetterQueue.size();
    }

    public long getTotalSubmitted() {
        return totalSubmitted.get();
    }

    public long getTotalProcessed() {
        return totalProcessed.get();
    }

    public long getTotalFailed() {
        return totalFailed.get();
    }

    public Map<String, Object> getHealth() {
        Map<String, Object> health = new ConcurrentHashMap<>();
        health.put("running", running.get());
        health.put("totalSubmitted", totalSubmitted.get());
        health.put("totalProcessed", totalProcessed.get());
        health.put("totalFailed", totalFailed.get());
        health.put("deadLetterQueueSize", deadLetterQueue.size());

        Map<String, Integer> queueSizes = new ConcurrentHashMap<>();
        for (JobType type : JobType.values()) {
            queueSizes.put(type.name(), queues.get(type).size());
        }
        health.put("queueSizes", queueSizes);

        return health;
    }
}
