package com.hospital.appointment.service;

import com.hospital.appointment.dto.AppointmentResponse;
import com.hospital.appointment.dto.BookAppointmentRequest;
import com.hospital.appointment.dto.RescheduleRequest;
import com.hospital.appointment.entity.*;
import com.hospital.appointment.exception.BadRequestException;
import com.hospital.appointment.exception.DuplicateResourceException;
import com.hospital.appointment.exception.ForbiddenException;
import com.hospital.appointment.exception.ResourceNotFoundException;
import com.hospital.appointment.notification.AppointmentEvent;
import com.hospital.appointment.repository.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Patient-side logic: book, cancel, reschedule, list.
 *
 * Double-booking protection: AvailabilitySlot has a @Version column. Two patients may both read the slot
 * as AVAILABLE, but only the first UPDATE ... WHERE version = ? succeeds. The second one updates 0 rows,
 * Hibernate throws an optimistic-locking exception, and we turn it into a 409 "slot just taken".
 */
@Service
public class AppointmentService {
    private final PatientRepository patientRepository;
    private final AvailabilitySlotRepository slotRepository;
    private final AppointmentRepository appointmentRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AppointmentService(PatientRepository patientRepository,
                              AvailabilitySlotRepository slotRepository,
                              AppointmentRepository appointmentRepository,
                              ApplicationEventPublisher eventPublisher) {
        this.patientRepository = patientRepository;
        this.slotRepository = slotRepository;
        this.appointmentRepository = appointmentRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public AppointmentResponse book(String email, BookAppointmentRequest request) {
        Patient patient = findPatient(email);
        AvailabilitySlot slot = reserveSlot(request.slotId());

        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setSlot(slot);
        appointment.setReason(request.reason());
        appointment.setStatus(AppointmentStatus.BOOKED);
        Appointment saved = appointmentRepository.save(appointment);

        publish(AppointmentEvent.Type.BOOKED, saved);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> myAppointments(String email) {
        return appointmentRepository.findByPatientUserEmailOrderByCreatedAtDesc(email)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public AppointmentResponse cancel(String email, Long appointmentId) {
        Patient patient = findPatient(email);
        Appointment appointment = findOwned(patient, appointmentId);

        if (appointment.getStatus() != AppointmentStatus.BOOKED)
            throw new BadRequestException("Only booked appointments can be cancelled");
        if (isInPast(appointment.getSlot()))
            throw new BadRequestException("Past appointments cannot be cancelled");

        releaseSlot(appointment.getSlot());
        appointment.setStatus(AppointmentStatus.CANCELLED);

        publish(AppointmentEvent.Type.CANCELLED, appointment);
        return toResponse(appointment);
    }

    @Transactional
    public AppointmentResponse reschedule(String email, Long appointmentId, RescheduleRequest request) {
        Patient patient = findPatient(email);
        Appointment appointment = findOwned(patient, appointmentId);

        if (appointment.getStatus() != AppointmentStatus.BOOKED)
            throw new BadRequestException("Only booked appointments can be rescheduled");
        if (isInPast(appointment.getSlot()))
            throw new BadRequestException("Past appointments cannot be rescheduled");
        if (appointment.getSlot().getId().equals(request.newSlotId()))
            throw new BadRequestException("Please choose a different slot");

        // Book the new slot first. If someone else got it, we fail here and the old booking stays untouched.
        AvailabilitySlot newSlot = reserveSlot(request.newSlotId());
        releaseSlot(appointment.getSlot());
        appointment.setSlot(newSlot);

        publish(AppointmentEvent.Type.RESCHEDULED, appointment);
        return toResponse(appointment);
    }

    // ------------------------------------------------------------------ helpers

    /** Marks a free slot as BOOKED. The flush is where the optimistic-lock check happens. */
    private AvailabilitySlot reserveSlot(Long slotId) {
        AvailabilitySlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment slot was not found"));

        if (!slot.getDoctor().isApproved()) throw new BadRequestException("This doctor is not approved");
        if (slot.getStatus() != SlotStatus.AVAILABLE) throw new DuplicateResourceException("This slot is already booked");
        if (isInPast(slot)) throw new BadRequestException("Cannot book a slot in the past");

        slot.setStatus(SlotStatus.BOOKED);
        try {
            // Flush now so a concurrent update is detected before we create the appointment.
            slotRepository.saveAndFlush(slot);
        } catch (OptimisticLockingFailureException ex) {
            throw new DuplicateResourceException("This slot was just booked by another patient. Please choose another slot.");
        }
        return slot;
    }

    private void releaseSlot(AvailabilitySlot slot) {
        slot.setStatus(SlotStatus.AVAILABLE);
        try {
            slotRepository.saveAndFlush(slot);
        } catch (OptimisticLockingFailureException ex) {
            throw new DuplicateResourceException("The slot was changed by someone else. Please try again.");
        }
    }

    private Patient findPatient(String email) {
        return patientRepository.findByUserEmail(email)
                .orElseThrow(() -> new BadRequestException("Patient profile was not found"));
    }

    private Appointment findOwned(Patient patient, Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment was not found"));
        if (!appointment.getPatient().getId().equals(patient.getId()))
            throw new ForbiddenException("This appointment belongs to another patient");
        return appointment;
    }

    private boolean isInPast(AvailabilitySlot slot) {
        return LocalDateTime.of(slot.getSlotDate(), slot.getStartTime()).isBefore(LocalDateTime.now());
    }

    private void publish(AppointmentEvent.Type type, Appointment a) {
        AvailabilitySlot slot = a.getSlot();
        eventPublisher.publishEvent(new AppointmentEvent(
                type,
                a.getPatient().getUser().getEmail(),
                a.getPatient().getUser().getName(),
                slot.getDoctor().getUser().getName(),
                slot.getSlotDate(),
                slot.getStartTime()));
    }

    private AppointmentResponse toResponse(Appointment a) {
        Doctor doctor = a.getSlot().getDoctor();
        return new AppointmentResponse(a.getId(), doctor.getId(), doctor.getUser().getName(),
                doctor.getSpecialization(), a.getSlot().getSlotDate(), a.getSlot().getStartTime(),
                a.getSlot().getEndTime(), a.getStatus(), a.getReason(), a.getCreatedAt());
    }
}
