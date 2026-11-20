package com.hospital.appointment.service;

import com.hospital.appointment.dto.AuthResponse;
import com.hospital.appointment.dto.LoginRequest;
import com.hospital.appointment.dto.MessageResponse;
import com.hospital.appointment.dto.RegisterRequest;
import com.hospital.appointment.entity.Doctor;
import com.hospital.appointment.entity.Patient;
import com.hospital.appointment.entity.Role;
import com.hospital.appointment.entity.User;
import com.hospital.appointment.exception.BadRequestException;
import com.hospital.appointment.exception.DuplicateResourceException;
import com.hospital.appointment.repository.DoctorRepository;
import com.hospital.appointment.repository.PatientRepository;
import com.hospital.appointment.repository.UserRepository;
import com.hospital.appointment.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       DoctorRepository doctorRepository,
                       PatientRepository patientRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    // @Transactional: if saving the doctor/patient profile fails, the user row is rolled back too.
    @Transactional
    public MessageResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();

        if (request.role() == Role.ADMIN) {
            throw new BadRequestException("Only PATIENT or DOCTOR can self-register");
        }
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Email is already registered");
        }
        if (request.role() == Role.DOCTOR) {
            validateDoctorFields(request);
        }

        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        userRepository.save(user);

        if (request.role() == Role.DOCTOR) {
            Doctor doctor = new Doctor();
            doctor.setUser(user);
            doctor.setSpecialization(request.specialization().trim());
            doctor.setExperienceYears(request.experienceYears());
            doctor.setFee(request.fee());
            doctor.setApproved(false); // admin approves later
            doctorRepository.save(doctor);
            return new MessageResponse("Doctor registered. Your account will be visible to patients after admin approval.");
        }

        Patient patient = new Patient();
        patient.setUser(user);
        patient.setAge(request.age());
        patient.setGender(request.gender());
        patient.setPhone(request.phone());
        patientRepository.save(patient);
        return new MessageResponse("Patient registered successfully");
    }

    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();

        // Checks the password against the stored BCrypt hash.
        // Throws BadCredentialsException on failure (handled in GlobalExceptionHandler -> 401).
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password()));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("User not found"));

        String token = jwtService.generateToken(user.getEmail(), user.getRole().name());
        return new AuthResponse(token, user.getName(), user.getEmail(), user.getRole().name());
    }

    private void validateDoctorFields(RegisterRequest r) {
        if (r.specialization() == null || r.specialization().isBlank()) {
            throw new BadRequestException("Specialization is required for doctors");
        }
        if (r.experienceYears() == null || r.experienceYears() < 0) {
            throw new BadRequestException("Experience (years) is required for doctors");
        }
        if (r.fee() == null || r.fee().signum() < 0) {
            throw new BadRequestException("Consultation fee is required for doctors");
        }
    }
}
