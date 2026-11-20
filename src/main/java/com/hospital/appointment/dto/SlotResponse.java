package com.hospital.appointment.dto;

import com.hospital.appointment.entity.SlotStatus;
import java.time.LocalDate;
import java.time.LocalTime;

public record SlotResponse(
        Long id,
        Long doctorId,
        LocalDate slotDate,
        LocalTime startTime,
        LocalTime endTime,
        SlotStatus status
) {}
