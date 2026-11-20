package com.hospital.appointment.service;

import com.hospital.appointment.dto.AppointmentResponse;
import com.hospital.appointment.dto.BookAppointmentRequest;
import com.hospital.appointment.dto.RescheduleRequest;
import com.hospital.appointment.entity.*;
import com.hospital.appointment.exception.BadRequestException;
import com.hospital.appointment.exception.DuplicateResourceException;
import com.hospital.appointment.exception.ForbiddenException;
import com.hospital.appointment.notification.AppointmentEvent;
import com.hospital.appointment.repository.AppointmentRepository;
import com.hospital.appointment.repository.AvailabilitySlotRepository;
import com.hospital.appointment.repository.PatientRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests: the repositories are mocks, so no database is needed. */
@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    private static final String EMAIL = "ravi@test.com";

    @Mock PatientRepository patientRepository;
    @Mock AvailabilitySlotRepository slotRepository;
    @Mock AppointmentRepository appointmentRepository;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks AppointmentService service;

    // ------------------------------------------------------------------ book

    @Test
    void bookSuccess_marksSlotBookedAndPublishesEvent() {
        Patient patient = patient(1L);
        AvailabilitySlot slot = slot(5L, doctor(true), SlotStatus.AVAILABLE, 1);
        when(patientRepository.findByUserEmail(EMAIL)).thenReturn(Optional.of(patient));
        when(slotRepository.findById(5L)).thenReturn(Optional.of(slot));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(i -> i.getArgument(0));

        AppointmentResponse response = service.book(EMAIL, new BookAppointmentRequest(5L, "Checkup"));

        assertEquals(SlotStatus.BOOKED, slot.getStatus());
        assertEquals(AppointmentStatus.BOOKED, response.status());
        assertEquals("Checkup", response.reason());
        verify(slotRepository).saveAndFlush(slot);
        verify(eventPublisher).publishEvent(any(AppointmentEvent.class));
    }

    @Test
    void bookAlreadyBookedSlot_isRejectedWithoutSavingAnything() {
        AvailabilitySlot slot = slot(5L, doctor(true), SlotStatus.BOOKED, 1);
        when(patientRepository.findByUserEmail(EMAIL)).thenReturn(Optional.of(patient(1L)));
        when(slotRepository.findById(5L)).thenReturn(Optional.of(slot));

        assertThrows(DuplicateResourceException.class,
                () -> service.book(EMAIL, new BookAppointmentRequest(5L, null)));

        verify(slotRepository, never()).saveAndFlush(any());
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void bookWhenAnotherPatientWinsTheRace_isRejectedAndNoAppointmentIsCreated() {
        AvailabilitySlot slot = slot(5L, doctor(true), SlotStatus.AVAILABLE, 1);
        when(patientRepository.findByUserEmail(EMAIL)).thenReturn(Optional.of(patient(1L)));
        when(slotRepository.findById(5L)).thenReturn(Optional.of(slot));
        // This is what Hibernate throws when the @Version in the database has changed in the meantime.
        when(slotRepository.saveAndFlush(slot))
                .thenThrow(new ObjectOptimisticLockingFailureException(AvailabilitySlot.class, 5L));

        assertThrows(DuplicateResourceException.class,
                () -> service.book(EMAIL, new BookAppointmentRequest(5L, null)));

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void bookWithUnapprovedDoctor_isRejected() {
        AvailabilitySlot slot = slot(5L, doctor(false), SlotStatus.AVAILABLE, 1);
        when(patientRepository.findByUserEmail(EMAIL)).thenReturn(Optional.of(patient(1L)));
        when(slotRepository.findById(5L)).thenReturn(Optional.of(slot));

        assertThrows(BadRequestException.class,
                () -> service.book(EMAIL, new BookAppointmentRequest(5L, null)));
    }

    @Test
    void bookSlotInThePast_isRejected() {
        AvailabilitySlot slot = slot(5L, doctor(true), SlotStatus.AVAILABLE, -1);
        when(patientRepository.findByUserEmail(EMAIL)).thenReturn(Optional.of(patient(1L)));
        when(slotRepository.findById(5L)).thenReturn(Optional.of(slot));

        assertThrows(BadRequestException.class,
                () -> service.book(EMAIL, new BookAppointmentRequest(5L, null)));
    }

    // ------------------------------------------------------------------ cancel

    @Test
    void cancel_freesTheSlotAndMarksAppointmentCancelled() {
        Patient patient = patient(1L);
        AvailabilitySlot slot = slot(5L, doctor(true), SlotStatus.BOOKED, 1);
        Appointment appointment = appointment(100L, patient, slot);
        when(patientRepository.findByUserEmail(EMAIL)).thenReturn(Optional.of(patient));
        when(appointmentRepository.findById(100L)).thenReturn(Optional.of(appointment));

        AppointmentResponse response = service.cancel(EMAIL, 100L);

        assertEquals(AppointmentStatus.CANCELLED, response.status());
        assertEquals(SlotStatus.AVAILABLE, slot.getStatus());
        verify(eventPublisher).publishEvent(any(AppointmentEvent.class));
    }

    @Test
    void cancelSomeoneElsesAppointment_isForbidden() {
        Patient me = patient(1L);
        Patient other = patient(2L);
        Appointment appointment = appointment(100L, other, slot(5L, doctor(true), SlotStatus.BOOKED, 1));
        when(patientRepository.findByUserEmail(EMAIL)).thenReturn(Optional.of(me));
        when(appointmentRepository.findById(100L)).thenReturn(Optional.of(appointment));

        assertThrows(ForbiddenException.class, () -> service.cancel(EMAIL, 100L));
    }

    @Test
    void cancelAnAlreadyCancelledAppointment_isRejected() {
        Patient patient = patient(1L);
        Appointment appointment = appointment(100L, patient, slot(5L, doctor(true), SlotStatus.AVAILABLE, 1));
        appointment.setStatus(AppointmentStatus.CANCELLED);
        when(patientRepository.findByUserEmail(EMAIL)).thenReturn(Optional.of(patient));
        when(appointmentRepository.findById(100L)).thenReturn(Optional.of(appointment));

        assertThrows(BadRequestException.class, () -> service.cancel(EMAIL, 100L));
    }

    // ------------------------------------------------------------------ reschedule

    @Test
    void reschedule_movesBookingToNewSlotAndFreesTheOldOne() {
        Patient patient = patient(1L);
        Doctor doctor = doctor(true);
        AvailabilitySlot oldSlot = slot(5L, doctor, SlotStatus.BOOKED, 1);
        AvailabilitySlot newSlot = slot(6L, doctor, SlotStatus.AVAILABLE, 2);
        Appointment appointment = appointment(100L, patient, oldSlot);
        when(patientRepository.findByUserEmail(EMAIL)).thenReturn(Optional.of(patient));
        when(appointmentRepository.findById(100L)).thenReturn(Optional.of(appointment));
        when(slotRepository.findById(6L)).thenReturn(Optional.of(newSlot));

        service.reschedule(EMAIL, 100L, new RescheduleRequest(6L));

        assertSame(newSlot, appointment.getSlot());
        assertEquals(SlotStatus.BOOKED, newSlot.getStatus());
        assertEquals(SlotStatus.AVAILABLE, oldSlot.getStatus());
        assertEquals(AppointmentStatus.BOOKED, appointment.getStatus());
        verify(eventPublisher).publishEvent(any(AppointmentEvent.class));
    }

    @Test
    void rescheduleToATakenSlot_keepsTheOldBooking() {
        Patient patient = patient(1L);
        Doctor doctor = doctor(true);
        AvailabilitySlot oldSlot = slot(5L, doctor, SlotStatus.BOOKED, 1);
        AvailabilitySlot takenSlot = slot(6L, doctor, SlotStatus.BOOKED, 2);
        Appointment appointment = appointment(100L, patient, oldSlot);
        when(patientRepository.findByUserEmail(EMAIL)).thenReturn(Optional.of(patient));
        when(appointmentRepository.findById(100L)).thenReturn(Optional.of(appointment));
        when(slotRepository.findById(6L)).thenReturn(Optional.of(takenSlot));

        assertThrows(DuplicateResourceException.class,
                () -> service.reschedule(EMAIL, 100L, new RescheduleRequest(6L)));

        assertSame(oldSlot, appointment.getSlot());
        assertEquals(SlotStatus.BOOKED, oldSlot.getStatus());
    }

    // ------------------------------------------------------------------ test data helpers

    private Patient patient(long id) {
        User user = new User();
        user.setName("Ravi");
        user.setEmail(EMAIL);
        Patient patient = new Patient();
        patient.setId(id);
        patient.setUser(user);
        return patient;
    }

    private Doctor doctor(boolean approved) {
        User user = new User();
        user.setName("Dr. Anita");
        user.setEmail("anita@test.com");
        Doctor doctor = new Doctor();
        doctor.setId(10L);
        doctor.setUser(user);
        doctor.setSpecialization("Cardiology");
        doctor.setApproved(approved);
        return doctor;
    }

    private AvailabilitySlot slot(long id, Doctor doctor, SlotStatus status, int daysFromToday) {
        AvailabilitySlot slot = new AvailabilitySlot();
        slot.setId(id);
        slot.setDoctor(doctor);
        slot.setSlotDate(LocalDate.now().plusDays(daysFromToday));
        slot.setStartTime(LocalTime.of(10, 0));
        slot.setEndTime(LocalTime.of(10, 30));
        slot.setStatus(status);
        return slot;
    }

    private Appointment appointment(long id, Patient patient, AvailabilitySlot slot) {
        Appointment appointment = new Appointment();
        appointment.setId(id);
        appointment.setPatient(patient);
        appointment.setSlot(slot);
        appointment.setStatus(AppointmentStatus.BOOKED);
        return appointment;
    }
}
