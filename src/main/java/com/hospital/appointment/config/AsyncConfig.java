package com.hospital.appointment.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Turns on @Async (used by EmailService). */
@Configuration
@EnableAsync
public class AsyncConfig {
}
