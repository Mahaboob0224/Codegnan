package com.hospital.appointment.service;

import com.hospital.appointment.dto.DoctorAppointmentResponse;
import com.hospital.appointment.entity.Appointment;
import com.hospital.appointment.entity.AppointmentStatus;
import com.hospital.appointment.entity.Doctor;
import com.hospital.appointment.exception.BadRequestException;
import com.hospital.appointment.exception.ForbiddenException;
import com.hospital.appointment.exception.ResourceNotFoundException;
import com.hospital.appointment.repository.AppointmentRepository;
import com.hospital.appointment.repository.DoctorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** Doctor-side view of appointments. */
@Service
public class DoctorAppointmentService {

    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;

    public DoctorAppointmentService(DoctorRepository doctorRepository, AppointmentRepository appointmentRepository) {
        this.doctorRepository = doctorRepository;
        this.appointmentRepository = appointmentRepository;
    }

    /** One day if a date is given, otherwise everything from today onwards. */
    @Transactional(readOnly = true)
    public List<DoctorAppointmentResponse> list(String doctorEmail, LocalDate date) {
        Doctor doctor = findDoctor(doctorEmail);

        List<Appointment> appointments = (date != null)
                ? appointmentRepository.findForDoctorOnDate(doctor.getId(), date)
                : appointmentRepository.findForDoctorFrom(doctor.getId(), LocalDate.now());

        return appointments.stream().map(this::toResponse).toList();
    }

    @Transactional
    public DoctorAppointmentResponse complete(String doctorEmail, Long appointmentId) {
        Doctor doctor = findDoctor(doctorEmail);
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment was not found"));

        if (!appointment.getSlot().getDoctor().getId().equals(doctor.getId()))
            throw new ForbiddenException("This appointment belongs to another doctor");
        if (appointment.getStatus() != AppointmentStatus.BOOKED)
            throw new BadRequestException("Only booked appointments can be marked as completed");

        appointment.setStatus(AppointmentStatus.COMPLETED);
        return toResponse(appointment);
    }

    private Doctor findDoctor(String email) {
        return doctorRepository.findByUserEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor profile was not found"));
    }

    private DoctorAppointmentResponse toResponse(Appointment a) {
        return new DoctorAppointmentResponse(
                a.getId(),
                a.getPatient().getUser().getName(),
                a.getPatient().getPhone(),
                a.getSlot().getSlotDate(),
                a.getSlot().getStartTime(),
                a.getSlot().getEndTime(),
                a.getStatus(),
                a.getReason());
    }
}
