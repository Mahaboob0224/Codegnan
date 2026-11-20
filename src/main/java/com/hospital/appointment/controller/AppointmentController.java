package com.hospital.appointment.controller;

import com.hospital.appointment.dto.AppointmentResponse;
import com.hospital.appointment.dto.BookAppointmentRequest;
import com.hospital.appointment.dto.RescheduleRequest;
import com.hospital.appointment.service.AppointmentService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/patient/appointments")
public class AppointmentController {
    private final AppointmentService appointmentService;
    public AppointmentController(AppointmentService appointmentService) { this.appointmentService = appointmentService; }

    @PostMapping
    public AppointmentResponse book(@Valid @RequestBody BookAppointmentRequest request,
                                    @AuthenticationPrincipal User principal) {
        return appointmentService.book(principal.getUsername(), request);
    }

    @GetMapping("/my")
    public List<AppointmentResponse> myAppointments(@AuthenticationPrincipal User principal) {
        return appointmentService.myAppointments(principal.getUsername());
    }

    // PUT /api/patient/appointments/12/cancel
    @PutMapping("/{id}/cancel")
    public AppointmentResponse cancel(@PathVariable Long id, @AuthenticationPrincipal User principal) {
        return appointmentService.cancel(principal.getUsername(), id);
    }

    // PUT /api/patient/appointments/12/reschedule   body: { "newSlotId": 7 }
    @PutMapping("/{id}/reschedule")
    public AppointmentResponse reschedule(@PathVariable Long id,
                                          @Valid @RequestBody RescheduleRequest request,
                                          @AuthenticationPrincipal User principal) {
        return appointmentService.reschedule(principal.getUsername(), id, request);
    }
}
