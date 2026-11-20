package com.hospital.appointment.controller;

import com.hospital.appointment.dto.MessageResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Small endpoints to prove login and role-based access work.
 * Real patient / doctor / admin APIs replace these from Week 2.
 */
@RestController
@RequestMapping("/api")
public class RoleCheckController {

    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        return Map.of(
                "email", authentication.getName(),
                "authorities", authentication.getAuthorities().toString()
        );
    }

    @GetMapping("/patient/ping")
    public MessageResponse patientPing() {
        return new MessageResponse("Hello PATIENT");
    }

    @GetMapping("/doctor/ping")
    public MessageResponse doctorPing() {
        return new MessageResponse("Hello DOCTOR");
    }

    @GetMapping("/admin/ping")
    public MessageResponse adminPing() {
        return new MessageResponse("Hello ADMIN");
    }
}
