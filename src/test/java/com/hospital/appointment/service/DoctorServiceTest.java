package com.hospital.appointment.service;

import com.hospital.appointment.dto.BulkSlotRequest;
import com.hospital.appointment.dto.SlotResponse;
import com.hospital.appointment.entity.AvailabilitySlot;
import com.hospital.appointment.entity.Doctor;
import com.hospital.appointment.entity.SlotStatus;
import com.hospital.appointment.entity.User;
import com.hospital.appointment.exception.BadRequestException;
import com.hospital.appointment.exception.DuplicateResourceException;
import com.hospital.appointment.exception.ForbiddenException;
import com.hospital.appointment.repository.AppointmentRepository;
import com.hospital.appointment.repository.AvailabilitySlotRepository;
import com.hospital.appointment.repository.DoctorRepository;
import com.hospital.appointment.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoctorServiceTest {

    private static final String EMAIL = "anita@test.com";

    @Mock DoctorRepository doctorRepository;
    @Mock UserRepository userRepository;
    @Mock AvailabilitySlotRepository slotRepository;
    @Mock AppointmentRepository appointmentRepository;

    @InjectMocks DoctorService service;

    private final LocalDate tomorrow = LocalDate.now().plusDays(1);

    @Test
    void bulkCreate_splitsTheWindowIntoSlots() {
        when(doctorRepository.findByUserEmail(EMAIL)).thenReturn(Optional.of(doctor(10L, true)));
        when(slotRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        List<SlotResponse> slots = service.createSlotsBulk(EMAIL,
                new BulkSlotRequest(tomorrow, LocalTime.of(9, 0), LocalTime.of(11, 0), 30));

        assertEquals(4, slots.size());
        assertEquals(LocalTime.of(9, 0), slots.get(0).startTime());
        assertEquals(LocalTime.of(9, 30), slots.get(0).endTime());
        assertEquals(LocalTime.of(10, 30), slots.get(3).startTime());
        assertEquals(LocalTime.of(11, 0), slots.get(3).endTime());
        assertTrue(slots.stream().allMatch(s -> s.status() == SlotStatus.AVAILABLE));
    }

    @Test
    void bulkCreate_dropsAPartialLastSlot() {
        when(doctorRepository.findByUserEmail(EMAIL)).thenReturn(Optional.of(doctor(10L, true)));
        when(slotRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        // 09:00 to 10:45 is 105 minutes: three full 30-minute slots, the last 15 minutes are left out.
        List<SlotResponse> slots = service.createSlotsBulk(EMAIL,
                new BulkSlotRequest(tomorrow, LocalTime.of(9, 0), LocalTime.of(10, 45), 30));

        assertEquals(3, slots.size());
    }

    @Test
    void bulkCreate_rejectsEndBeforeStart() {
        when(doctorRepository.findByUserEmail(EMAIL)).thenReturn(Optional.of(doctor(10L, true)));

        assertThrows(BadRequestException.class, () -> service.createSlotsBulk(EMAIL,
                new BulkSlotRequest(tomorrow, LocalTime.of(11, 0), LocalTime.of(9, 0), 30)));

        verify(slotRepository, never()).saveAll(any());
    }

    @Test
    void bulkCreate_rejectsUnapprovedDoctor() {
        when(doctorRepository.findByUserEmail(EMAIL)).thenReturn(Optional.of(doctor(10L, false)));

        assertThrows(BadRequestException.class, () -> service.createSlotsBulk(EMAIL,
                new BulkSlotRequest(tomorrow, LocalTime.of(9, 0), LocalTime.of(11, 0), 30)));
    }

    @Test
    void bulkCreate_rejectsOverlappingWindow() {
        when(doctorRepository.findByUserEmail(EMAIL)).thenReturn(Optional.of(doctor(10L, true)));
        when(slotRepository.existsByDoctorIdAndSlotDateAndStartTimeLessThanAndEndTimeGreaterThan(
                eq(10L), any(LocalDate.class), any(LocalTime.class), any(LocalTime.class))).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> service.createSlotsBulk(EMAIL,
                new BulkSlotRequest(tomorrow, LocalTime.of(9, 0), LocalTime.of(11, 0), 30)));

        verify(slotRepository, never()).saveAll(any());
    }

    @Test
    void deleteSlotOfAnotherDoctor_isForbidden() {
        when(doctorRepository.findByUserEmail(EMAIL)).thenReturn(Optional.of(doctor(10L, true)));
        when(slotRepository.findById(5L)).thenReturn(Optional.of(slot(5L, doctor(99L, true), SlotStatus.AVAILABLE)));

        assertThrows(ForbiddenException.class, () -> service.deleteSlot(EMAIL, 5L));
    }

    @Test
    void deleteBookedSlot_isRejected() {
        Doctor me = doctor(10L, true);
        when(doctorRepository.findByUserEmail(EMAIL)).thenReturn(Optional.of(me));
        when(slotRepository.findById(5L)).thenReturn(Optional.of(slot(5L, me, SlotStatus.BOOKED)));

        assertThrows(BadRequestException.class, () -> service.deleteSlot(EMAIL, 5L));
    }

    @Test
    void deleteFreeSlotWithoutHistory_deletesIt() {
        Doctor me = doctor(10L, true);
        AvailabilitySlot slot = slot(5L, me, SlotStatus.AVAILABLE);
        when(doctorRepository.findByUserEmail(EMAIL)).thenReturn(Optional.of(me));
        when(slotRepository.findById(5L)).thenReturn(Optional.of(slot));

        service.deleteSlot(EMAIL, 5L);

        verify(slotRepository).delete(slot);
    }

    // ------------------------------------------------------------------ test data helpers

    private Doctor doctor(long id, boolean approved) {
        User user = new User();
        user.setName("Dr. Anita");
        user.setEmail(EMAIL);
        Doctor doctor = new Doctor();
        doctor.setId(id);
        doctor.setUser(user);
        doctor.setSpecialization("Cardiology");
        doctor.setApproved(approved);
        return doctor;
    }

    private AvailabilitySlot slot(long id, Doctor doctor, SlotStatus status) {
        AvailabilitySlot slot = new AvailabilitySlot();
        slot.setId(id);
        slot.setDoctor(doctor);
        slot.setSlotDate(tomorrow);
        slot.setStartTime(LocalTime.of(10, 0));
        slot.setEndTime(LocalTime.of(10, 30));
        slot.setStatus(status);
        return slot;
    }
}
