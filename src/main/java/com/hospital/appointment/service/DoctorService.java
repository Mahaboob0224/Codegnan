package com.hospital.appointment.service;

import com.hospital.appointment.dto.BulkSlotRequest;
import com.hospital.appointment.dto.CreateSlotRequest;
import com.hospital.appointment.dto.DoctorResponse;
import com.hospital.appointment.dto.SlotResponse;
import com.hospital.appointment.entity.*;
import com.hospital.appointment.exception.BadRequestException;
import com.hospital.appointment.exception.DuplicateResourceException;
import com.hospital.appointment.exception.ForbiddenException;
import com.hospital.appointment.exception.ResourceNotFoundException;
import com.hospital.appointment.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class DoctorService {
    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;
    private final AvailabilitySlotRepository slotRepository;
    private final AppointmentRepository appointmentRepository;

    public DoctorService(DoctorRepository doctorRepository, UserRepository userRepository,
                         AvailabilitySlotRepository slotRepository, AppointmentRepository appointmentRepository) {
        this.doctorRepository = doctorRepository;
        this.userRepository = userRepository;
        this.slotRepository = slotRepository;
        this.appointmentRepository = appointmentRepository;
    }

    @Transactional(readOnly = true)
    public Page<DoctorResponse> searchDoctors(String specialization, Pageable pageable) {
        Page<Doctor> doctors = (specialization == null || specialization.isBlank())
                ? doctorRepository.findByApprovedTrue(pageable)
                : doctorRepository.findByApprovedTrueAndSpecializationContainingIgnoreCase(specialization.trim(), pageable);
        return doctors.map(this::toDoctorResponse);
    }

    @Transactional
    public SlotResponse createSlot(String email, CreateSlotRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("Logged-in user was not found"));
        Doctor doctor = doctorRepository.findByUserId(user.getId())
                .orElseThrow(() -> new BadRequestException("Doctor profile was not found"));
        if (!doctor.isApproved()) throw new BadRequestException("Your doctor profile must be approved before adding slots");
        if (request.slotDate().isBefore(LocalDate.now())) throw new BadRequestException("Slot date cannot be in the past");
        if (!request.endTime().isAfter(request.startTime())) throw new BadRequestException("End time must be after start time");
        if (slotRepository.existsByDoctorIdAndSlotDateAndStartTimeLessThanAndEndTimeGreaterThan(
                doctor.getId(), request.slotDate(), request.endTime(), request.startTime()))
            throw new BadRequestException("This slot overlaps with an existing slot");

        AvailabilitySlot slot = new AvailabilitySlot();
        slot.setDoctor(doctor);
        slot.setSlotDate(request.slotDate());
        slot.setStartTime(request.startTime());
        slot.setEndTime(request.endTime());
        slot.setStatus(SlotStatus.AVAILABLE);
        return toSlotResponse(slotRepository.save(slot));
    }

    @Transactional(readOnly = true)
    public List<SlotResponse> getAvailableSlots(Long doctorId, LocalDate date) {
        if (!doctorRepository.existsById(doctorId)) throw new BadRequestException("Doctor was not found");
        return slotRepository.findByDoctorIdAndSlotDateAndStatusOrderByStartTimeAsc(doctorId, date, SlotStatus.AVAILABLE)
                .stream().map(this::toSlotResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<DoctorResponse> getPendingDoctors() {
        return doctorRepository.findByApprovedFalseOrderByIdAsc().stream()
                .map(this::toDoctorResponse).toList();
    }

    @Transactional
    public DoctorResponse approveDoctor(Long doctorId) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new BadRequestException("Doctor was not found"));
        doctor.setApproved(true);
        return toDoctorResponse(doctorRepository.save(doctor));
    }

    // ---------------------------------------------------------------- Week 3: doctor's own workspace

    @Transactional(readOnly = true)
    public DoctorResponse profile(String email) {
        return toDoctorResponse(findDoctor(email));
    }

    /** Doctor gives a window (09:00-13:00) and a slot length (30 min); we create 8 slots. */
    @Transactional
    public List<SlotResponse> createSlotsBulk(String email, BulkSlotRequest request) {
        Doctor doctor = findDoctor(email);
        if (!doctor.isApproved()) throw new BadRequestException("Your doctor profile must be approved before adding slots");
        if (!request.endTime().isAfter(request.startTime())) throw new BadRequestException("End time must be after start time");
        if (LocalDateTime.of(request.slotDate(), request.startTime()).isBefore(LocalDateTime.now()))
            throw new BadRequestException("Start time is in the past");

        int minutes = request.slotMinutes();
        // Whole slots only: a 09:00-10:45 window with 30-minute slots gives 3 slots, the last 15 minutes are dropped.
        int count = (int) (Duration.between(request.startTime(), request.endTime()).toMinutes() / minutes);
        if (count < 1) throw new BadRequestException("The time window is shorter than one slot");
        if (count > 48) throw new BadRequestException("Too many slots at once (maximum 48)");

        if (slotRepository.existsByDoctorIdAndSlotDateAndStartTimeLessThanAndEndTimeGreaterThan(
                doctor.getId(), request.slotDate(), request.endTime(), request.startTime()))
            throw new DuplicateResourceException("You already have slots overlapping this time window");

        List<AvailabilitySlot> slots = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            LocalTime start = request.startTime().plusMinutes((long) i * minutes);
            AvailabilitySlot slot = new AvailabilitySlot();
            slot.setDoctor(doctor);
            slot.setSlotDate(request.slotDate());
            slot.setStartTime(start);
            slot.setEndTime(start.plusMinutes(minutes));
            slot.setStatus(SlotStatus.AVAILABLE);
            slots.add(slot);
        }
        return slotRepository.saveAll(slots).stream().map(this::toSlotResponse).toList();
    }

    /** Doctor's own slots (free and booked): one day if a date is given, otherwise everything from today. */
    @Transactional(readOnly = true)
    public List<SlotResponse> mySlots(String email, LocalDate date) {
        Doctor doctor = findDoctor(email);
        List<AvailabilitySlot> slots = (date != null)
                ? slotRepository.findByDoctorIdAndSlotDateOrderByStartTimeAsc(doctor.getId(), date)
                : slotRepository.findByDoctorIdAndSlotDateGreaterThanEqualOrderBySlotDateAscStartTimeAsc(
                        doctor.getId(), LocalDate.now());
        return slots.stream().map(this::toSlotResponse).toList();
    }

    @Transactional
    public void deleteSlot(String email, Long slotId) {
        Doctor doctor = findDoctor(email);
        AvailabilitySlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("Slot was not found"));

        if (!slot.getDoctor().getId().equals(doctor.getId()))
            throw new ForbiddenException("This slot belongs to another doctor");
        if (slot.getStatus() != SlotStatus.AVAILABLE)
            throw new BadRequestException("A booked slot cannot be deleted");
        if (appointmentRepository.existsBySlotId(slotId))
            // Cancelled appointments still point to this slot (booking history), so the slot is kept.
            throw new BadRequestException("This slot has booking history and cannot be deleted");

        slotRepository.delete(slot);
    }

    private Doctor findDoctor(String email) {
        return doctorRepository.findByUserEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor profile was not found"));
    }

    private DoctorResponse toDoctorResponse(Doctor d) {
        return new DoctorResponse(d.getId(), d.getUser().getName(), d.getSpecialization(),
                d.getExperienceYears(), d.getFee(), d.isApproved());
    }

    private SlotResponse toSlotResponse(AvailabilitySlot s) {
        return new SlotResponse(s.getId(), s.getDoctor().getId(), s.getSlotDate(),
                s.getStartTime(), s.getEndTime(), s.getStatus());
    }
}
