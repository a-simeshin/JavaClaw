package ai.javaclaw.api.chat.controller;

import ai.javaclaw.api.chat.controller.dto.ApprovalRespondRequest;
import ai.javaclaw.tasks.ApprovalRequest;
import ai.javaclaw.tasks.ApprovalRequestRepository;
import ai.javaclaw.tasks.ApprovalService;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
@RequestMapping("/api/chat/approval")
public class ApprovalController {

    private final ApprovalService approvalService;
    private final ApprovalRequestRepository approvalRequestRepository;

    @GetMapping("/pending")
    public List<ApprovalRequest> pending(@RequestParam final String conversationId) {
        return approvalRequestRepository.findByConversationIdAndStatus(conversationId, ApprovalRequest.Status.pending);
    }

    @PostMapping("/{approvalId}/respond")
    public ResponseEntity<Void> respond(
            @PathVariable final String approvalId, @RequestBody final ApprovalRespondRequest request) {
        final ApprovalRequest approval = approvalRequestRepository
                .findById(approvalId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Approval not found: " + approvalId));
        approvalService.submitApproval(approval.getConversationId(), request.response());
        return ResponseEntity.ok().build();
    }
}
