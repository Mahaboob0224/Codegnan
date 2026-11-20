package com.hospital.appointment.dto;

import com.hospital.appointment.entity.AppointmentStatus;

import java.time.LocalDate;
import java.time.LocalTime;

/** An appointment as the doctor sees it (patient details instead of doctor details). */
public record DoctorAppointmentResponse(
        Long id,
        String patientName,
        String patientPhone,
        LocalDate slotDate,
        LocalTime startTime,
        LocalTime endTime,
        AppointmentStatus status,
        String reason
) {}
