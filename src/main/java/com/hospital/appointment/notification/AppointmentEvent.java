package com.hospital.appointment.notification;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Published by AppointmentService after something happens to an appointment.
 * EmailService listens and sends the email only AFTER the database transaction has committed.
 */
public record AppointmentEvent(
        Type type,
        String patientEmail,
        String patientName,
        String doctorName,
        LocalDate date,
        LocalTime startTime
) {
    public enum Type {
        BOOKED,
        CANCELLED,
        RESCHEDULED
    }
}
