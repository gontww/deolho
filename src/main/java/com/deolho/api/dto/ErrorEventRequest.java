package com.deolho.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO for incoming error events.
 * This is the main contract for applications pushing errors to DeOlho.
 */
public record ErrorEventRequest(
        @NotBlank(message = "application is required")
        String application,

        @NotBlank(message = "level is required")
        String level,

        @NotBlank(message = "message is required")
        String message,

        String exception,

        String stacktrace,

        String host,

        String environment
) {}
