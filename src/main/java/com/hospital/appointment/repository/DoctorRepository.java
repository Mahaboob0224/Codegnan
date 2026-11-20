package com.hospital.appointment.repository;

import com.hospital.appointment.entity.Doctor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {
    Optional<Doctor> findByUserId(Long userId);
    Optional<Doctor> findByUserEmail(String email);
    Page<Doctor> findByApprovedTrue(Pageable pageable);
    java.util.List<Doctor> findByApprovedFalseOrderByIdAsc();
    long countByApprovedFalse();
    Page<Doctor> findByApprovedTrueAndSpecializationContainingIgnoreCase(String specialization, Pageable pageable);
}
