package com.deolho.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for registering a new application to monitor.
 */
public record ApplicationRequest(
        @NotBlank(message = "name is required")
        String name,

        @NotBlank(message = "environment is required")
        String environment
) {}
