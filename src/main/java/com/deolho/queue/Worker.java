package com.deolho.queue;

import com.deolho.domain.enums.JobType;

/**
 * Interface for all workers in the system.
 * Each worker handles one specific type of job.
 */
public interface Worker {

    /**
     * Process a job from the queue.
     *
     * @param job the job to process
     * @throws Exception if processing fails (triggers retry logic)
     */
    void process(Job job) throws Exception;

    /**
     * The type of job this worker handles.
     */
    JobType getJobType();
}
