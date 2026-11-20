package com.hospital.appointment.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private static final String SECRET =
            "SG9zcGl0YWxBcHBvaW50bWVudFN5c3RlbVNlY3JldEtleUZvckp3dFNpZ25pbmcyMDI2";

    @Test
    void generatedTokenIsValidAndContainsEmail() {
        JwtService jwtService = new JwtService(SECRET, 60_000);

        String token = jwtService.generateToken("patient@test.com", "PATIENT");

        assertTrue(jwtService.isTokenValid(token));
        assertEquals("patient@test.com", jwtService.extractEmail(token));
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtService jwtService = new JwtService(SECRET, 60_000);
        String token = jwtService.generateToken("patient@test.com", "PATIENT");

        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertFalse(jwtService.isTokenValid(tampered));
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService jwtService = new JwtService(SECRET, -1_000); // already expired

        String token = jwtService.generateToken("patient@test.com", "PATIENT");

        assertFalse(jwtService.isTokenValid(token));
    }
}
