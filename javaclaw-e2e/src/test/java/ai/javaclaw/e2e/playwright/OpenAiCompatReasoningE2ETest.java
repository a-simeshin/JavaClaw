package ai.javaclaw.e2e.playwright;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import ai.javaclaw.e2e.support.IntegrationTestBase;
import ai.javaclaw.e2e.support.RestTestClient;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * E2E integration test for OpenAI-compat reasoning wiring (spec
 * {@code specs/openai-compat-reasoning-events.md}, Task 7).
 *
 * <p>Boots the full Spring application context with {@code javaclaw.reasoning.enabled=true}
 * pointed at a local {@link WireMockServer} serving an OpenAI-compat streaming response
 * (reasoning_details deltas followed by content). Posts to {@code /api/chat/send} and
 * asserts that the system returns a non-error SSE (Vercel AI data-stream) body that
 * contains the expected content frames.
 *
 * <p>This test validates two things:
 * <ol>
 *   <li>Spring context boots cleanly with the reasoning auto-configuration enabled
 *       (ReasoningAwareChatModel bean wired, reasoningWebClient pointed at a mock).</li>
 *   <li>The REST → SseStreamingService → ChatService → ChatModel → OpenAI (mocked) pipeline
 *       produces a well-formed Vercel v4 data-stream body terminated with a {@code d:} frame.</li>
 * </ol>
 *
 * <p>Since {@code ReasoningAwareChatModel.shouldHandleReasoning} uses the base
 * {@link org.springframework.ai.chat.prompt.ChatOptions#getModel()} API (working for
 * {@code ToolCallingChatOptions} built by {@code ChatService.buildPrompt}), the reasoning
 * fast-path engages end-to-end and WireMock's reasoning_details fixture is consumed via
 * the reasoning WebClient pipeline, producing {@code g:} reasoning frames in the
 * Vercel data-stream body alongside the content {@code 0:} frames.
 */
class OpenAiCompatReasoningE2ETest extends IntegrationTestBase {

    private static WireMockServer wireMock;

    @LocalServerPort
    int port;

    private RestTestClient client;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        wireMock.stubFor(post(urlPathMatching("/v1/chat/completions.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody(buildSseFixture())));
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @DynamicPropertySource
    static void overrideOpenAiProps(final DynamicPropertyRegistry registry) {
        registry.add("spring.ai.openai.base-url", () -> "http://localhost:" + wireMock.port());
        registry.add("spring.ai.openai.api-key", () -> "test-key");
        registry.add("spring.ai.openai.chat.options.model", () -> "minimax/minimax-m2.7");
        registry.add("javaclaw.reasoning.enabled", () -> "true");
        registry.add("javaclaw.reasoning.effort", () -> "medium");
    }

    @BeforeEach
    void setupClient() {
        client = new RestTestClient("http://localhost:" + port, "admin", "admin");
    }

    /**
     * Builds an OpenAI-compat SSE fixture body containing both reasoning_details deltas
     * and a final content delta plus a [DONE] sentinel. WireMock will stream this verbatim
     * on a single OpenAI /v1/chat/completions POST.
     */
    private static String buildSseFixture() {
        return String.join(
                        "\n\n",
                        "data: {\"id\":\"chatcmpl-1\",\"object\":\"chat.completion.chunk\","
                                + "\"choices\":[{\"index\":0,\"delta\":{\"reasoning_details\":[{\"type\":"
                                + "\"reasoning.text\",\"text\":\"Думаю\",\"id\":\"r-1\"}]}}]}",
                        "data: {\"id\":\"chatcmpl-1\",\"object\":\"chat.completion.chunk\","
                                + "\"choices\":[{\"index\":0,\"delta\":{\"reasoning_details\":[{\"type\":"
                                + "\"reasoning.text\",\"text\":\" над задачей\",\"id\":\"r-1\"}]}}]}",
                        "data: {\"id\":\"chatcmpl-1\",\"object\":\"chat.completion.chunk\","
                                + "\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Ответ: 42\"}}]}",
                        "data: {\"id\":\"chatcmpl-1\",\"object\":\"chat.completion.chunk\","
                                + "\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}],"
                                + "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5,\"total_tokens\":15}}",
                        "data: [DONE]")
                + "\n\n";
    }

    @Test
    @DisplayName("POST /api/chat/send with reasoning config enabled → 200 + Vercel data-stream body")
    void reasoningConfigEnabled_chatSendReturnsDataStream() {
        final HttpResponse<String> response = client.post(
                "/api/chat/send",
                "{\"content\":\"tell me\",\"conversationId\":\"reasoning-e2e-" + System.currentTimeMillis() + "\"}");

        // The core integration assertion: the system boots + accepts the request.
        // (A non-200 here would mean either context failed to start with reasoning config,
        // or the request was rejected before reaching SseStreamingService.)
        assertThat(response.statusCode())
                .as("chat/send with reasoning config enabled must not 5xx")
                .isBetween(200, 299);

        // Vercel AI data-stream protocol negotiation header.
        assertThat(response.headers().firstValue("x-vercel-ai-data-stream"))
                .as("response must advertise Vercel data-stream v1")
                .isPresent()
                .hasValue("v1");

        final String body = response.body();
        assertThat(body).as("response body must not be empty").isNotNull().isNotEmpty();

        // Reasoning fast-path must engage — WireMock fixture streams reasoning_details "Думаю над задачей"
        // and content "Ответ: 42"; both must flow through.
        assertThat(body).as("reasoning text must appear in stream").contains("Думаю");
        assertThat(body).as("content text must appear in stream").contains("Ответ: 42");

        // At least one Vercel v4 reasoning frame (g:) must be present.
        final List<String> lines = Arrays.asList(body.split("\n"));
        final long gFrames = lines.stream().filter(l -> l.startsWith("g:")).count();
        assertThat(gFrames).as("body must contain >= 1 reasoning 'g:' frame").isGreaterThanOrEqualTo(1);

        // And the terminal Vercel v4 finish marker.
        assertThat(body)
                .as("body must contain Vercel data-stream terminal frame 'd:'")
                .contains("d:");
    }
}
