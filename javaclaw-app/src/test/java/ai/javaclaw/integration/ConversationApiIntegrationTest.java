package ai.javaclaw.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration test for the Conversation REST API — full CRUD lifecycle with JDBC memory.
 *
 * <p>Verifies create, list, get-messages, and delete operations against a real
 * PostgreSQL database via Testcontainers.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConversationApiIntegrationTest extends IntegrationTestBase {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static String createdConversationId;

    @Test
    @Order(1)
    void createConversation_withTitle_returns200() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Test Conversation\",\"id\":\"integ-conv-1\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value("integ-conv-1"))
                .andExpect(jsonPath("$.title").value("Test Conversation"))
                .andExpect(jsonPath("$.messageCount").isNumber())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        createdConversationId = body.path("id").asText(null);
        assertThat(createdConversationId).isNotBlank();
    }

    @Test
    @Order(2)
    void listConversations_returnsPageWithContent() throws Exception {
        mockMvc.perform(get("/api/conversations")
                        .param("page", "0")
                        .param("size", "50")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").isNumber())
                .andExpect(jsonPath("$.size").isNumber())
                .andExpect(jsonPath("$.total").isNumber());
    }

    @Test
    @Order(3)
    void getMessages_forConversation_returnsPage() throws Exception {
        assertThat(createdConversationId)
                .as("conversation must be created first")
                .isNotNull();

        mockMvc.perform(get("/api/conversations/{id}/messages", createdConversationId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.total").isNumber());
    }

    @Test
    @Order(4)
    void deleteConversation_returns204() throws Exception {
        assertThat(createdConversationId)
                .as("conversation must be created first")
                .isNotNull();

        mockMvc.perform(delete("/api/conversations/{id}", createdConversationId))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(5)
    void afterDeletion_messagesEndpointReturnsEmptyPage() throws Exception {
        assertThat(createdConversationId)
                .as("conversation must be created first")
                .isNotNull();

        mockMvc.perform(get("/api/conversations/{id}/messages", createdConversationId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }
}
