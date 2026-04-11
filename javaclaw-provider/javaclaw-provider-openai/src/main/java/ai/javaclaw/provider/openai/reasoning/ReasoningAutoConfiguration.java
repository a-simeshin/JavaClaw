package ai.javaclaw.provider.openai.reasoning;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

@AutoConfiguration(after = OpenAiChatAutoConfiguration.class)
@EnableConfigurationProperties(ReasoningProperties.class)
@ConditionalOnClass(OpenAiChatModel.class)
@ConditionalOnProperty(prefix = "javaclaw.reasoning", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ReasoningAutoConfiguration {

    /**
     * Создаём собственный Jackson 2 ObjectMapper, не полагаясь на autowiring —
     * в приложении основной ObjectMapper — Jackson 3 (tools.jackson), и autoconfiguration
     * Jackson 2 бина может отсутствовать. Jackson 2 databind — транзитивная зависимость
     * Spring AI OpenAI (OpenAiApi) и WebFlux, класс гарантированно в classpath.
     */
    @Bean
    public ReasoningChunkParser reasoningChunkParser() {
        return new ReasoningChunkParser(new ObjectMapper());
    }

    @Bean
    public ReasoningRequestBuilder reasoningRequestBuilder() {
        return new ReasoningRequestBuilder(new ObjectMapper());
    }

    @Bean("reasoningWebClient")
    public WebClient reasoningWebClient(
            @Value("${spring.ai.openai.base-url:https://api.openai.com}") final String baseUrl,
            @Value("${spring.ai.openai.api-key:}") final String apiKey) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    @Bean
    @ConditionalOnBean(OpenAiChatModel.class)
    public ReasoningAwareChatModel reasoningAwareChatModel(
            final ObjectProvider<OpenAiChatModel> openAiChatModelProvider,
            @Qualifier("reasoningWebClient") final WebClient webClient,
            final ReasoningProperties props,
            final ReasoningRequestBuilder requestBuilder,
            final ReasoningChunkParser parser) {
        final OpenAiChatModel delegate = openAiChatModelProvider.getIfUnique();
        if (delegate == null) {
            return null;
        }
        return new ReasoningAwareChatModel(delegate, webClient, props, requestBuilder, parser);
    }
}
