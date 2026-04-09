package ai.javaclaw.tasks;

import java.util.UUID;
import org.springframework.data.relational.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;

@Component
class ApprovalRequestIdGeneratorCallback implements BeforeConvertCallback<ApprovalRequest> {

    @Override
    public ApprovalRequest onBeforeConvert(final ApprovalRequest request) {
        if (request.getId() == null) {
            return new ApprovalRequest(
                    UUID.randomUUID().toString(),
                    request.getTaskId(),
                    request.getConversationId(),
                    request.getQuestion(),
                    request.getResponse(),
                    request.getStatus(),
                    request.getTimeoutAt(),
                    request.getCreatedAt(),
                    request.getRespondedAt());
        }
        return request;
    }
}
