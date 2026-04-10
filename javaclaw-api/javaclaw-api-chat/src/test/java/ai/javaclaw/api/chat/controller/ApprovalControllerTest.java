package ai.javaclaw.api.chat.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import ai.javaclaw.api.chat.error.ChatApiExceptionHandler;
import ai.javaclaw.tasks.ApprovalRequest;
import ai.javaclaw.tasks.ApprovalRequestRepository;
import ai.javaclaw.tasks.ApprovalService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class ApprovalControllerTest {

    private ApprovalService approvalService;
    private ApprovalRequestRepository approvalRequestRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        approvalService = mock(ApprovalService.class);
        approvalRequestRepository = mock(ApprovalRequestRepository.class);
        mockMvc = standaloneSetup(new ApprovalController(approvalService, approvalRequestRepository))
                .setControllerAdvice(new ChatApiExceptionHandler())
                .build();
    }

    @Test
    void pendingReturnsPendingApprovals() throws Exception {
        ApprovalRequest req = ApprovalRequest.create(
                "task-1", "conv-1", "Buy ticket?", Instant.now().plusSeconds(60));
        when(approvalRequestRepository.findByConversationIdAndStatus("conv-1", ApprovalRequest.Status.pending))
                .thenReturn(List.of(req));

        mockMvc.perform(get("/api/chat/approval/pending?conversationId=conv-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].taskId").value("task-1"))
                .andExpect(jsonPath("$[0].question").value("Buy ticket?"))
                .andExpect(jsonPath("$[0].status").value("pending"));
    }

    @Test
    void pendingReturnsEmptyWhenNone() throws Exception {
        when(approvalRequestRepository.findByConversationIdAndStatus("conv-2", ApprovalRequest.Status.pending))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/chat/approval/pending?conversationId=conv-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void respondCallsSubmitApproval() throws Exception {
        ApprovalRequest req =
                ApprovalRequest.create("task-1", "conv-1", "Buy?", Instant.now().plusSeconds(60));
        when(approvalRequestRepository.findById("approval-1")).thenReturn(Optional.of(req));

        mockMvc.perform(post("/api/chat/approval/approval-1/respond")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"response\":\"yes\"}"))
                .andExpect(status().isOk());

        verify(approvalService).submitApproval("conv-1", "yes");
    }

    @Test
    void respondReturns404WhenApprovalNotFound() throws Exception {
        when(approvalRequestRepository.findById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/chat/approval/missing/respond")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"response\":\"yes\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
    }
}
