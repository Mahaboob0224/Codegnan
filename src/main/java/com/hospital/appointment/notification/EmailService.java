package com.hospital.appointment.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends appointment emails.
 *
 * - @TransactionalEventListener(AFTER_COMMIT): the email goes out only if the booking really got saved.
 * - @Async: sending an email is slow, so it runs in another thread and the user's request is not delayed.
 * - If mail is not configured (app.mail.enabled=false), the email is only written to the log.
 */
@Component
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final boolean enabled;
    private final String from;

    public EmailService(ObjectProvider<JavaMailSender> mailSenderProvider,
                        @Value("${app.mail.enabled:false}") boolean enabled,
                        @Value("${app.mail.from:noreply@hospital.com}") String from) {
        this.mailSenderProvider = mailSenderProvider;
        this.enabled = enabled;
        this.from = from;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentEvent(AppointmentEvent e) {
        String subject;
        String body;
        String when = e.date() + " at " + e.startTime();

        switch (e.type()) {
            case BOOKED -> {
                subject = "Appointment confirmed";
                body = "Hi " + e.patientName() + ",\n\nYour appointment with " + e.doctorName()
                        + " is confirmed for " + when + ".\n\nHospital Appointments";
            }
            case CANCELLED -> {
                subject = "Appointment cancelled";
                body = "Hi " + e.patientName() + ",\n\nYour appointment with " + e.doctorName()
                        + " on " + when + " has been cancelled.\n\nHospital Appointments";
            }
            default -> {
                subject = "Appointment rescheduled";
                body = "Hi " + e.patientName() + ",\n\nYour appointment with " + e.doctorName()
                        + " has been moved to " + when + ".\n\nHospital Appointments";
            }
        }

        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (!enabled || sender == null) {
            log.info("[EMAIL not sent, mail disabled] To: {} | Subject: {} | {}",
                    e.patientEmail(), subject, body.replace("\n", " "));
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(e.patientEmail());
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);
        } catch (Exception ex) {
            // A failed email must never break the booking.
            log.warn("Could not send email to {}: {}", e.patientEmail(), ex.getMessage());
        }
    }
}
