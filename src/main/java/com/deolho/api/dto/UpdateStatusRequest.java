package com.deolho.api.dto;

import com.deolho.domain.enums.ErrorStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for updating an error's status.
 */
public record UpdateStatusRequest(
        @NotNull(message = "status is required")
        ErrorStatus status
) {}
