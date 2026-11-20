package com.hospital.appointment.service;

import com.hospital.appointment.dto.BookAppointmentRequest;
import com.hospital.appointment.entity.*;
import com.hospital.appointment.exception.DuplicateResourceException;
import com.hospital.appointment.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The most important test in the project: 10 patients try to book the SAME slot at the same moment.
 * Exactly one must win. Runs against an in-memory H2 database (see application-test.properties).
 */
@SpringBootTest
@ActiveProfiles("test")
class BookingConcurrencyTest {

    @Autowired AppointmentService appointmentService;
    @Autowired UserRepository userRepository;
    @Autowired PatientRepository patientRepository;
    @Autowired DoctorRepository doctorRepository;
    @Autowired AvailabilitySlotRepository slotRepository;
    @Autowired AppointmentRepository appointmentRepository;

    @Test
    void onlyOnePatientCanBookTheSameSlot() throws Exception {
        int patients = 10;

        // One approved doctor with one free slot tomorrow.
        Doctor doctor = new Doctor();
        doctor.setUser(saveUser("doctor-" + UUID.randomUUID() + "@test.com", Role.DOCTOR));
        doctor.setSpecialization("Cardiology");
        doctor.setApproved(true);
        doctor = doctorRepository.save(doctor);

        AvailabilitySlot slot = new AvailabilitySlot();
        slot.setDoctor(doctor);
        slot.setSlotDate(LocalDate.now().plusDays(1));
        slot.setStartTime(LocalTime.of(10, 0));
        slot.setEndTime(LocalTime.of(10, 30));
        slot.setStatus(SlotStatus.AVAILABLE);
        Long slotId = slotRepository.save(slot).getId();

        List<String> emails = new ArrayList<>();
        for (int i = 0; i < patients; i++) {
            User user = saveUser("patient-" + i + "-" + UUID.randomUUID() + "@test.com", Role.PATIENT);
            Patient patient = new Patient();
            patient.setUser(user);
            patientRepository.save(patient);
            emails.add(user.getEmail());
        }

        // All threads wait at the starting line, then book at the same moment.
        ExecutorService pool = Executors.newFixedThreadPool(patients);
        CountDownLatch ready = new CountDownLatch(patients);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger booked = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();

        for (String email : emails) {
            pool.submit(() -> {
                try {
                    ready.countDown();
                    go.await();
                    appointmentService.book(email, new BookAppointmentRequest(slotId, "race"));
                    booked.incrementAndGet();
                } catch (DuplicateResourceException e) {
                    rejected.incrementAndGet();   // "slot already booked / just booked by someone else"
                } catch (Exception e) {
                    unexpected.incrementAndGet();
                    e.printStackTrace();
                }
            });
        }

        ready.await();
        go.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "booking threads did not finish in time");

        assertEquals(1, booked.get(), "exactly one patient must win");
        assertEquals(patients - 1, rejected.get() + unexpected.get(), "everyone else must be rejected");
        assertEquals(1, appointmentRepository.countBySlotId(slotId), "only one appointment may exist for the slot");
        assertEquals(SlotStatus.BOOKED, slotRepository.findById(slotId).orElseThrow().getStatus());
    }

    private User saveUser(String email, Role role) {
        User user = new User();
        user.setName("Test " + role);
        user.setEmail(email);
        user.setPassword("not-a-real-hash");
        user.setRole(role);
        return userRepository.save(user);
    }
}
