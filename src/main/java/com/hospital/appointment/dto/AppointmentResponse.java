package com.hospital.appointment.dto;

import com.hospital.appointment.entity.AppointmentStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record AppointmentResponse(
        Long id,
        Long doctorId,
        String doctorName,
        String specialization,
        LocalDate slotDate,
        LocalTime startTime,
        LocalTime endTime,
        AppointmentStatus status,
        String reason,
        LocalDateTime createdAt
) {}
