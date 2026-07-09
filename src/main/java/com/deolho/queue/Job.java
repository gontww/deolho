package com.deolho.queue;

import com.deolho.domain.enums.JobType;

import java.time.LocalDateTime;

/**
 * Represents a unit of work to be processed by a worker.
 * Implements Comparable for priority queue ordering (lower priority number = higher priority).
 */
public record Job(
        JobType type,
        String payload,
        int priority,
        int retryCount,
        LocalDateTime createdAt
) implements Comparable<Job> {

    public Job(JobType type, String payload) {
        this(type, payload, 5, 0, LocalDateTime.now());
    }

    public Job(JobType type, String payload, int priority) {
        this(type, payload, priority, 0, LocalDateTime.now());
    }

    /**
     * Create a retry copy with incremented counter.
     */
    public Job retry() {
        return new Job(type, payload, priority, retryCount + 1, createdAt);
    }

    @Override
    public int compareTo(Job other) {
        return Integer.compare(this.priority, other.priority);
    }
}
