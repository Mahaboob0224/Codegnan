package com.hospital.appointment.dto;

import jakarta.validation.constraints.NotNull;

public record RescheduleRequest(
        @NotNull(message = "newSlotId is required") Long newSlotId
) {}
