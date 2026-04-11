package ai.javaclaw.security.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

class CookiePropertiesTest {

    @Test
    void defaults_are_correct() {
        var env = new MockEnvironment();
        var binder = Binder.get(env);
        var props = binder.bindOrCreate("javaclaw.security.cookie", CookieProperties.class);

        assertThat(props.name()).isEqualTo("JCLAW_SESSION");
        assertThat(props.maxAgeSeconds()).isEqualTo(86400);
        assertThat(props.secure()).isTrue();
        assertThat(props.sameSite()).isEqualTo("Lax");
        assertThat(props.path()).isEqualTo("/");
    }
}
