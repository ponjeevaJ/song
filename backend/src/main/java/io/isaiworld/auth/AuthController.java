package io.isaiworld.auth;

import io.isaiworld.security.AccountPrincipal;
import io.isaiworld.security.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;

    public AuthController(JdbcTemplate jdbc, PasswordEncoder encoder, JwtService jwtService) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody Credentials credentials) {
        String email = credentials.email().toLowerCase(Locale.ROOT);
        if (jdbc.queryForObject("SELECT count(*) FROM app_users WHERE email = ?", Integer.class, email) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists.");
        }
        Long id = jdbc.queryForObject(
            "INSERT INTO app_users(email, password_hash) VALUES (?, ?) RETURNING id",
            Long.class, email, encoder.encode(credentials.password()));
        return response(new AccountPrincipal(id, email, "USER"));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody Credentials credentials) {
        var rows = jdbc.query(
            "SELECT id, email, password_hash, role FROM app_users WHERE email = ?",
            (rs, row) -> new LoginRow(rs.getLong("id"), rs.getString("email"), rs.getString("password_hash"), rs.getString("role")),
            credentials.email().toLowerCase(Locale.ROOT));
        if (rows.isEmpty() || !encoder.matches(credentials.password(), rows.getFirst().passwordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect.");
        }
        LoginRow row = rows.getFirst();
        return response(new AccountPrincipal(row.id(), row.email(), row.role()));
    }

    private AuthResponse response(AccountPrincipal principal) {
        return new AuthResponse(jwtService.issue(principal), principal.getUsername(), principal.role());
    }

    public record Credentials(@Email @NotBlank @Size(max = 254) String email,
                              @NotBlank @Size(min = 10, max = 72) String password) {}
    public record AuthResponse(String token, String email, String role) {}
    private record LoginRow(long id, String email, String passwordHash, String role) {}
}
