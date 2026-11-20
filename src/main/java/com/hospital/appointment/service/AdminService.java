package com.hospital.appointment.service;

import com.hospital.appointment.dto.StatsResponse;
import com.hospital.appointment.entity.AppointmentStatus;
import com.hospital.appointment.repository.AppointmentRepository;
import com.hospital.appointment.repository.DoctorRepository;
import com.hospital.appointment.repository.PatientRepository;
import com.hospital.appointment.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin dashboard numbers. (Doctor approval lives in DoctorService.) */
@Service
public class AdminService {

    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;

    public AdminService(DoctorRepository doctorRepository,
                        PatientRepository patientRepository,
                        UserRepository userRepository,
                        AppointmentRepository appointmentRepository) {
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.userRepository = userRepository;
        this.appointmentRepository = appointmentRepository;
    }

    @Transactional(readOnly = true)
    public StatsResponse stats() {
        return new StatsResponse(
                userRepository.count(),
                patientRepository.count(),
                doctorRepository.count(),
                doctorRepository.countByApprovedFalse(),
                appointmentRepository.count(),
                appointmentRepository.countByStatus(AppointmentStatus.BOOKED),
                appointmentRepository.countByStatus(AppointmentStatus.CANCELLED),
                appointmentRepository.countByStatus(AppointmentStatus.COMPLETED));
    }
}
