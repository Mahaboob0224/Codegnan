package com.hospital.appointment.repository;

import com.hospital.appointment.entity.Appointment;
import com.hospital.appointment.entity.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    List<Appointment> findByPatientUserEmailOrderByCreatedAtDesc(String email);

    long countByStatus(AppointmentStatus status);

    long countBySlotId(Long slotId);

    boolean existsBySlotId(Long slotId);

    @Query("""
            select a from Appointment a
            where a.slot.doctor.id = :doctorId and a.slot.slotDate = :date
            order by a.slot.startTime
            """)
    List<Appointment> findForDoctorOnDate(@Param("doctorId") Long doctorId, @Param("date") LocalDate date);

    @Query("""
            select a from Appointment a
            where a.slot.doctor.id = :doctorId and a.slot.slotDate >= :from
            order by a.slot.slotDate, a.slot.startTime
            """)
    List<Appointment> findForDoctorFrom(@Param("doctorId") Long doctorId, @Param("from") LocalDate from);
}
