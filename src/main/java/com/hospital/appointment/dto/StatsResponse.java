package com.hospital.appointment.dto;

public record StatsResponse(
        long totalUsers,
        long patients,
        long doctors,
        long pendingDoctors,
        long totalAppointments,
        long booked,
        long cancelled,
        long completed
) {}
