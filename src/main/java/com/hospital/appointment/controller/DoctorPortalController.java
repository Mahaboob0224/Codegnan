package com.hospital.appointment.controller;

import com.hospital.appointment.dto.BulkSlotRequest;
import com.hospital.appointment.dto.DoctorAppointmentResponse;
import com.hospital.appointment.dto.DoctorResponse;
import com.hospital.appointment.dto.MessageResponse;
import com.hospital.appointment.dto.SlotResponse;
import com.hospital.appointment.service.DoctorAppointmentService;
import com.hospital.appointment.service.DoctorService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Everything a logged-in DOCTOR does: own profile, own slots, own appointments. */
@RestController
@RequestMapping("/api/doctor")
public class DoctorPortalController {

    private final DoctorService doctorService;
    private final DoctorAppointmentService doctorAppointmentService;

    public DoctorPortalController(DoctorService doctorService, DoctorAppointmentService doctorAppointmentService) {
        this.doctorService = doctorService;
        this.doctorAppointmentService = doctorAppointmentService;
    }

    @GetMapping("/profile")
    public DoctorResponse profile(@AuthenticationPrincipal User principal) {
        return doctorService.profile(principal.getUsername());
    }

    // POST /api/doctor/slots/bulk  body: { slotDate, startTime, endTime, slotMinutes }
    @PostMapping("/slots/bulk")
    public List<SlotResponse> createSlots(@Valid @RequestBody BulkSlotRequest request,
                                          @AuthenticationPrincipal User principal) {
        return doctorService.createSlotsBulk(principal.getUsername(), request);
    }

    // GET /api/doctor/slots            -> all upcoming slots
    // GET /api/doctor/slots?date=...   -> one day
    @GetMapping("/slots")
    public List<SlotResponse> mySlots(@RequestParam(required = false)
                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                      @AuthenticationPrincipal User principal) {
        return doctorService.mySlots(principal.getUsername(), date);
    }

    @DeleteMapping("/slots/{id}")
    public MessageResponse deleteSlot(@PathVariable Long id, @AuthenticationPrincipal User principal) {
        doctorService.deleteSlot(principal.getUsername(), id);
        return new MessageResponse("Slot deleted");
    }

    @GetMapping("/appointments")
    public List<DoctorAppointmentResponse> appointments(@RequestParam(required = false)
                                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                        @AuthenticationPrincipal User principal) {
        return doctorAppointmentService.list(principal.getUsername(), date);
    }

    @PutMapping("/appointments/{id}/complete")
    public DoctorAppointmentResponse complete(@PathVariable Long id, @AuthenticationPrincipal User principal) {
        return doctorAppointmentService.complete(principal.getUsername(), id);
    }
}
