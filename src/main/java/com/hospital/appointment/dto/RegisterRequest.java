package com.hospital.appointment.dto;

import com.hospital.appointment.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * One request body for both roles.
 * Patient fields (age, gender, phone) are optional.
 * Doctor fields (specialization, experienceYears, fee) are checked in AuthService when role = DOCTOR.
 */
public record RegisterRequest(
        @NotBlank(message = "Name is required")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Email is not valid")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 6, message = "Password must be at least 6 characters")
        String password,

        @NotNull(message = "Role is required (PATIENT or DOCTOR)")
        Role role,

        // patient
        Integer age,
        String gender,
        String phone,

        // doctor
        String specialization,
        Integer experienceYears,
        BigDecimal fee
) {
}
