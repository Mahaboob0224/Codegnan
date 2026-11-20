package com.hospital.appointment.repository;

import com.hospital.appointment.entity.AvailabilitySlot;
import com.hospital.appointment.entity.SlotStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface AvailabilitySlotRepository extends JpaRepository<AvailabilitySlot, Long> {
    List<AvailabilitySlot> findByDoctorIdAndSlotDateAndStatusOrderByStartTimeAsc(Long doctorId, LocalDate slotDate, SlotStatus status);
    boolean existsByDoctorIdAndSlotDateAndStartTime(Long doctorId, LocalDate slotDate, LocalTime startTime);
    boolean existsByDoctorIdAndSlotDateAndStartTimeLessThanAndEndTimeGreaterThan(
            Long doctorId, LocalDate slotDate, LocalTime endTime, LocalTime startTime);

    // Doctor's own view: every slot on one day, or every slot from a date onwards.
    List<AvailabilitySlot> findByDoctorIdAndSlotDateOrderByStartTimeAsc(Long doctorId, LocalDate slotDate);
    List<AvailabilitySlot> findByDoctorIdAndSlotDateGreaterThanEqualOrderBySlotDateAscStartTimeAsc(Long doctorId, LocalDate from);
}
