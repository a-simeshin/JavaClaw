package ai.javaclaw.security.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class Argon2PasswordEncoderConfigTest {

    private PasswordEncoder buildEncoder() {
        Map<String, PasswordEncoder> encoders = new LinkedHashMap<>();
        encoders.put("argon2", new Argon2PasswordEncoder(16, 32, 1, 19456, 2));
        encoders.put("bcrypt", new BCryptPasswordEncoder());
        encoders.put("noop", NoOpPasswordEncoder.getInstance());
        return new DelegatingPasswordEncoder("argon2", encoders);
    }

    @Test
    void encode_producesArgon2PrefixedHash() {
        var encoder = buildEncoder();
        String encoded = encoder.encode("test");
        assertThat(encoded).startsWith("{argon2}");
    }

    @Test
    void matches_argon2Hash_returnsTrue() {
        var encoder = buildEncoder();
        String encoded = encoder.encode("test");
        assertThat(encoder.matches("test", encoded)).isTrue();
    }

    @Test
    void matches_wrongPassword_returnsFalse() {
        var encoder = buildEncoder();
        String encoded = encoder.encode("test");
        assertThat(encoder.matches("wrong", encoded)).isFalse();
    }

    @Test
    void matches_legacyBcryptHash_returnsTrue() {
        // Generate a bcrypt hash at runtime to ensure compatibility with whatever bcrypt version is used
        String rawBcrypt = new BCryptPasswordEncoder().encode("test");
        String bcryptHash = "{bcrypt}" + rawBcrypt;
        var encoder = buildEncoder();
        assertThat(encoder.matches("test", bcryptHash)).isTrue();
    }

    @Test
    void matches_noopHash_returnsTrue() {
        var encoder = buildEncoder();
        assertThat(encoder.matches("plaintext", "{noop}plaintext")).isTrue();
    }
}
