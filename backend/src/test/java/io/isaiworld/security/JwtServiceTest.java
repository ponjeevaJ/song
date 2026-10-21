package io.isaiworld.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

class JwtServiceTest {
    private final JwtService jwtService = new JwtService("test-secret-with-more-than-32-characters", 24);

    @Test
    void roundTripsAccountIdentityAndRole() {
        AccountPrincipal user = new AccountPrincipal(42L, "music@example.com", "ADMIN");

        AccountPrincipal restored = jwtService.parse(jwtService.issue(user));

        assertEquals(user.id(), restored.id());
        assertEquals(user.getUsername(), restored.getUsername());
        assertEquals(user.role(), restored.role());
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwtService.issue(new AccountPrincipal(7L, "listener@example.com", "USER"));
        String[] parts = token.split("\\.");
        String changedSignature = (parts[2].charAt(0) == 'A' ? "B" : "A") + parts[2].substring(1);
        String tampered = parts[0] + "." + parts[1] + "." + changedSignature;

        assertThrows(JwtException.class, () -> jwtService.parse(tampered));
    }
}
