package com.deolho.api.dto;

import com.deolho.domain.entity.Application;

import java.time.LocalDateTime;

/**
 * Response DTO for monitored applications.
 */
public record ApplicationResponse(
        Long id,
        String name,
        String environment,
        LocalDateTime createdAt,
        long errorCount
) {
    public static ApplicationResponse from(Application app, long errorCount) {
        return new ApplicationResponse(
                app.getId(),
                app.getName(),
                app.getEnvironment(),
                app.getCreatedAt(),
                errorCount
        );
    }

    public static ApplicationResponse from(Application app) {
        return from(app, 0);
    }
}
