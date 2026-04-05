package ai.javaclaw.config;

import ai.javaclaw.mcp.converter.JsonbToMapConverter;
import ai.javaclaw.mcp.converter.MapToJsonbConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;

@Configuration
public class JdbcConfig {

    @Bean
    @Primary
    public JdbcCustomConversions jdbcCustomConversions() {
        final ObjectMapper objectMapper = new ObjectMapper();
        return new JdbcCustomConversions(
                List.of(new MapToJsonbConverter(objectMapper), new JsonbToMapConverter(objectMapper)));
    }
}
