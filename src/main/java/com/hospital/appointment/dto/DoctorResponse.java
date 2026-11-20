package com.hospital.appointment.dto;

import java.math.BigDecimal;

public record DoctorResponse(
        Long id,
        String name,
        String specialization,
        Integer experienceYears,
        BigDecimal fee,
        boolean approved
) {}
