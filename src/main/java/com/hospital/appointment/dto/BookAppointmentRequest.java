package com.hospital.appointment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BookAppointmentRequest(
        @NotNull(message = "Slot ID is required") Long slotId,
        @Size(max = 500, message = "Reason cannot exceed 500 characters") String reason
) {}
