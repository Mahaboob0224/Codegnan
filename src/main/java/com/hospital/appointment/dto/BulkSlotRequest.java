package com.hospital.appointment.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * A doctor gives a time window (for example 09:00 to 13:00) and the length of one slot (for example 30 minutes).
 * The server splits the window into slots: 09:00-09:30, 09:30-10:00, and so on.
 */
public record BulkSlotRequest(
        @NotNull(message = "Slot date is required") LocalDate slotDate,
        @NotNull(message = "Start time is required") LocalTime startTime,
        @NotNull(message = "End time is required") LocalTime endTime,
        @NotNull(message = "Slot length in minutes is required")
        @Min(value = 10, message = "Slot length must be at least 10 minutes")
        @Max(value = 240, message = "Slot length can be at most 240 minutes")
        Integer slotMinutes
) {}
