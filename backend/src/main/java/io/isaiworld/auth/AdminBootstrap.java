package io.isaiworld.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.util.Locale;

@Component
public class AdminBootstrap implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final String email;
    private final String password;

    public AdminBootstrap(JdbcTemplate jdbc, PasswordEncoder encoder,
                          @Value("${isai.admin.email}") String email,
                          @Value("${isai.admin.password}") String password) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(String... args) {
        if (email.isBlank() && password.isBlank()) return;
        if (email.isBlank() || password.length() < 10) {
            throw new IllegalStateException("Set both ADMIN_EMAIL and an ADMIN_PASSWORD of at least 10 characters.");
        }
        jdbc.update("""
            INSERT INTO app_users(email, password_hash, role) VALUES (?, ?, 'ADMIN')
            ON CONFLICT (email) DO NOTHING
            """, email.toLowerCase(Locale.ROOT), encoder.encode(password));
        log.info("Admin account bootstrap checked for configured email.");
    }
}
