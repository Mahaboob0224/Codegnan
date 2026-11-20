package com.hospital.appointment.controller;

import com.hospital.appointment.dto.*;
import com.hospital.appointment.service.DoctorService;
import com.hospital.appointment.exception.BadRequestException;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
public class DoctorController {
    private final DoctorService doctorService;
    public DoctorController(DoctorService doctorService) { this.doctorService = doctorService; }

    @GetMapping("/doctors")
    public Page<DoctorResponse> searchDoctors(
            @RequestParam(required = false) String specialization,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        if (page < 0 || size < 1 || size > 50) throw new BadRequestException("Page must be >= 0 and size must be between 1 and 50");
        return doctorService.searchDoctors(specialization, PageRequest.of(page, size, Sort.by("id").descending()));
    }

    @PostMapping("/doctor/slots")
    public SlotResponse createSlot(@Valid @RequestBody CreateSlotRequest request,
                                   @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {
        return doctorService.createSlot(principal.getUsername(), request);
    }

    @GetMapping("/doctors/{doctorId}/slots")
    public List<SlotResponse> getSlots(@PathVariable Long doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return doctorService.getAvailableSlots(doctorId, date);
    }

    @GetMapping("/admin/doctors/pending")
    public List<DoctorResponse> getPendingDoctors() {
        return doctorService.getPendingDoctors();
    }

    @PutMapping("/admin/doctors/{doctorId}/approve")
    public DoctorResponse approveDoctor(@PathVariable Long doctorId) {
        return doctorService.approveDoctor(doctorId);
    }
}
